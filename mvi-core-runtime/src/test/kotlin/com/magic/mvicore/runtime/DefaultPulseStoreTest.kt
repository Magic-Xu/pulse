package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultPulseStoreTest {
    @Test
    fun `sequential sends publish frames in commit order`() = runBlocking {
        val store = store(
            reducer = PulseReducer { previous, input ->
                ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
            },
        )
        val published = async(start = CoroutineStart.UNDISPATCHED) {
            store.transitions.take(3).toList()
        }

        val results = listOf(1, 2, 3).map { amount ->
            assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
                store.send(TestInput.Add(amount))
            )
        }
        val frames = withTimeout(TIMEOUT_MILLIS) { published.await() }

        assertEquals(listOf(1L, 2L, 3L), frames.map { it.sequenceId })
        assertEquals(listOf(0, 1, 3), frames.map { it.stateBefore.value })
        assertEquals(listOf(1, 3, 6), frames.map { it.stateAfter.value })
        assertEquals(listOf(1L, 2L, 3L), frames.map { it.stateRevision })
        assertEquals(frames, results.map { it.frame })
        close(store)
    }

    @Test
    fun `concurrent sends have one contiguous total order`() = runBlocking {
        val count = 64
        val store = store(
            reducer = PulseReducer { previous, _: TestInput ->
                ReduceOutcome.Changed(previous.copy(value = previous.value + 1))
            },
            mailboxCapacity = count,
        )
        val published = async(start = CoroutineStart.UNDISPATCHED) {
            store.transitions.take(count).toList()
        }
        val start = CompletableDeferred<Unit>()
        val sends = List(count) {
            async(Dispatchers.Default) {
                start.await()
                store.send(TestInput.Add(1))
            }
        }

        start.complete(Unit)
        val results = withTimeout(TIMEOUT_MILLIS) { sends.awaitAll() }
        val frames = withTimeout(TIMEOUT_MILLIS) { published.await() }

        assertTrue(results.all { it is TransitionResult.Completed })
        assertEquals((1L..count.toLong()).toList(), frames.map { it.sequenceId })
        assertEquals((0 until count).toList(), frames.map { it.stateBefore.value })
        assertEquals((1..count).toList(), frames.map { it.stateAfter.value })
        assertEquals(count, frames.map { it.requestId }.toSet().size)
        assertEquals(TestState(count), store.state.value)
        close(store)
    }

    @Test
    fun `equal changed state normalizes to unchanged and envelopes carry frame identity`() = runBlocking {
        val recorder = FailureRecorder()
        val store = store(
            reducer = PulseReducer { previous, _: TestInput ->
                ReduceOutcome.Changed(
                    state = previous.copy(),
                    uiEffects = listOf(TestEffect.Notice("same")),
                )
            },
            recorder = recorder,
        )
        val delivered = CompletableDeferred<EffectEnvelope<TestEffect>>()
        val coordinator = launch(start = CoroutineStart.UNDISPATCHED) {
            store.effects.collect { envelope -> delivered.complete(envelope) }
        }

        val result = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(0))
        )
        val frame = result.frame
        val envelope = withTimeout(TIMEOUT_MILLIS) { delivered.await() }

        assertEquals(TransitionOutcome.Unchanged, frame.outcome)
        assertEquals(0L, frame.stateRevision)
        assertEquals(frame.stateBefore, frame.stateAfter)
        assertEquals(1L, envelope.effectId)
        assertEquals(frame.requestId, envelope.requestId)
        assertEquals(frame.sequenceId, envelope.sequenceId)
        assertEquals(frame.stateRevision, envelope.stateRevision)
        assertEquals(0, envelope.index)
        assertEquals(TestEffect.Notice("same"), envelope.payload)
        assertTrue(recorder.snapshot().isEmpty())
        coordinator.cancelAndJoin()
        close(store)
    }

    @Test
    fun `ignored input keeps state revision and emits no effects`() = runBlocking {
        val store = store(
            reducer = PulseReducer { _, _: TestInput -> ReduceOutcome.Ignored("not-applicable") },
        )

        val result = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(1))
        )

        assertEquals(TransitionOutcome.Ignored("not-applicable"), result.frame.outcome)
        assertEquals(0L, result.frame.stateRevision)
        assertEquals(result.frame.stateBefore, result.frame.stateAfter)
        assertTrue(result.frame.uiEffects.isEmpty())
        close(store)
    }

    @Test
    fun `ordinary reducer failure produces failed frame and processor continues`() = runBlocking {
        val expected = IllegalStateException("reduce failed")
        val recorder = FailureRecorder()
        val store = store(
            reducer = PulseReducer { previous, input ->
                if (input is TestInput.Fail) throw expected
                ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
            },
            recorder = recorder,
        )

        val failed = assertIs<TransitionResult.Failed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Fail)
        )
        val recovered = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(1))
        )

        assertSame(expected, failed.failure.cause)
        assertEquals(TransitionOutcome.ReducerFailed, failed.frame.outcome)
        assertEquals(0L, failed.frame.stateRevision)
        assertEquals(1L, failed.frame.sequenceId)
        assertEquals(2L, recovered.frame.sequenceId)
        assertEquals(TestState(1), recovered.frame.stateAfter)
        val reported = assertIs<PulseFailure.ReducerFailure>(recorder.snapshot().single())
        assertSame(expected, reported.cause)
        close(store)
    }

    @Test
    fun `reducer cancellation is propagated and never converted to Pulse failure`() = runBlocking {
        val expected = CancellationException("cancel reducer")
        val recorder = FailureRecorder()
        val store = store(
            reducer = PulseReducer { _, _: TestInput -> throw expected },
            recorder = recorder,
        )

        val thrown = assertFailsWith<CancellationException> { store.send(TestInput.Cancel) }

        assertEquals(expected.message, thrown.message)
        assertTrue(recorder.snapshot().isEmpty())
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
    }

    @Test
    fun `fatal reducer error is propagated and never converted to Pulse failure`() = runBlocking {
        val expected = TestFatalError()
        val recorder = FailureRecorder()
        val store = store(
            reducer = PulseReducer { _, _: TestInput -> throw expected },
            recorder = recorder,
        )

        val thrown = assertFailsWith<TestFatalError> { store.send(TestInput.Fatal) }

        assertSame(expected, thrown)
        assertTrue(recorder.snapshot().isEmpty())
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
    }

    @Test
    fun `caller cancelled after enqueue is skipped without consuming sequence`() = runBlocking {
        val gate = ReducerGate()
        val recorder = FailureRecorder()
        val store = store(
            reducer = gatedReducer(gate),
            mailboxCapacity = 2,
            recorder = recorder,
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Block)
        }

        gate.awaitEntered()
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Add(100))
        }
        cancelled.cancel()
        assertFailsWith<CancellationException> { cancelled.await() }
        gate.release()

        val firstFrame = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { first.await() }
        ).frame
        val nextFrame = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(1))
        ).frame

        assertEquals(1L, firstFrame.sequenceId)
        assertEquals(2L, nextFrame.sequenceId)
        assertEquals(TestState(2), nextFrame.stateAfter)
        assertTrue(recorder.snapshot().isEmpty())
        close(store)
    }

    @Test
    fun `repeated full trySend stays bounded without dropping admitted input`() = runBlocking {
        val gate = ReducerGate()
        val recorder = FailureRecorder()
        val store = store(
            reducer = gatedReducer(gate),
            mailboxCapacity = 1,
            recorder = recorder,
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Block)
        }

        gate.awaitEntered()
        assertIs<EnqueueResult.Enqueued>(store.trySend(TestInput.Add(1)))
        repeat(1_000) {
            assertEquals(EnqueueResult.Full, store.trySend(TestInput.Add(1)))
        }
        gate.release()

        withTimeout(TIMEOUT_MILLIS) { first.await() }
        withTimeout(TIMEOUT_MILLIS) { store.state.first { it == TestState(2) } }
        assertIs<PulseFailure.MailboxOverflow>(recorder.snapshot().single())
        close(store)
    }

    @Test
    fun `background trySend overflow diagnostic runs on store dispatcher`() = runBlocking {
        val gate = ReducerGate()
        val marker = ThreadLocal<Boolean>()
        val failureOnStoreDispatcher = CompletableDeferred<Boolean>()
        val store = DefaultPulseStore(
            initialState = TestState(0),
            reducer = gatedReducer(gate),
            config = PulseRuntimeConfig(
                mailboxCapacity = 1,
                storeDispatcher = MarkingDispatcher(Dispatchers.Default, marker),
                consumerDispatcher = Dispatchers.Default,
                errorHandler = PulseErrorHandler { _, failure, _ ->
                    if (failure is PulseFailure.MailboxOverflow) {
                        failureOnStoreDispatcher.complete(marker.get() == true)
                    }
                },
                storeId = "overflow-dispatcher-test",
            ),
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Block)
        }

        gate.awaitEntered()
        assertIs<EnqueueResult.Enqueued>(store.trySend(TestInput.Add(1)))
        val overflow = withContext(Dispatchers.IO) {
            store.trySend(TestInput.Add(1))
        }
        assertEquals(EnqueueResult.Full, overflow)
        gate.release()

        withTimeout(TIMEOUT_MILLIS) { first.await() }
        assertTrue(withTimeout(TIMEOUT_MILLIS) { failureOnStoreDispatcher.await() })
        close(store)
    }

    @Test
    fun `close establishes cutoff drains admitted work and is idempotent`() = runBlocking {
        val gate = ReducerGate()
        val store = store(
            reducer = gatedReducer(gate),
            mailboxCapacity = 4,
        )
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Block)
        }

        gate.awaitEntered()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            store.send(TestInput.Add(1))
        }
        store.close()
        store.close()
        val closingRejection = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
        assertEquals(RejectionReason.Closing, closingRejection.reason)
        val closed = async { store.awaitClosed() }
        assertFalse(closed.isCompleted)
        gate.release()

        val firstFrame = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { first.await() }
        ).frame
        val secondFrame = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { second.await() }
        ).frame
        withTimeout(TIMEOUT_MILLIS) { closed.await() }

        assertEquals(listOf(1L, 2L), listOf(firstFrame.sequenceId, secondFrame.sequenceId))
        assertEquals(TestState(2), store.state.value)
        val closedRejection = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
        assertEquals(RejectionReason.Closed, closedRejection.reason)
        store.close()
    }

    @Test
    fun `reentrant send from state consumer runs after current frame`() = runBlocking {
        val store = store(
            reducer = PulseReducer { previous, input ->
                ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
            },
        )
        val reentrantResult = CompletableDeferred<TransitionResult<TestState, TestInput, TestEffect>>()
        val consumer = launch(start = CoroutineStart.UNDISPATCHED) {
            store.state.drop(1).take(1).collect {
                reentrantResult.complete(store.send(TestInput.Add(10)))
            }
        }

        val first = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(1))
        )
        val second = assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { reentrantResult.await() }
        )
        consumer.join()

        assertEquals(1L, first.frame.sequenceId)
        assertEquals(2L, second.frame.sequenceId)
        assertEquals(first.frame.stateAfter, second.frame.stateBefore)
        assertEquals(TestState(11), second.frame.stateAfter)
        close(store)
    }

    @Test
    fun `cancelling a full effect session never cancels the processor or hangs send`() = runBlocking {
        val recorder = FailureRecorder()
        val store = DefaultPulseStore(
            initialState = TestState(0),
            reducer = PulseReducer<TestState, TestInput, TestEffect> { previous, input ->
                val effects = if (input.amount == 1) {
                    listOf(
                        TestEffect.Notice("first"),
                        TestEffect.Notice("buffered"),
                        TestEffect.Notice("blocked-producer"),
                    )
                } else {
                    emptyList()
                }
                ReduceOutcome.Changed(
                    state = previous.copy(value = previous.value + input.amount),
                    uiEffects = effects,
                )
            },
            config = PulseRuntimeConfig(
                effectBufferCapacity = 1,
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = recorder.handler,
                storeId = "effect-session-cutoff-test",
            ),
        )
        val firstDeliveryStarted = CompletableDeferred<Unit>()
        val keepFirstDeliveryBlocked = CompletableDeferred<Unit>()
        val coordinator = launch(start = CoroutineStart.UNDISPATCHED) {
            store.effects.collect { envelope ->
                if (envelope.index == 0) {
                    firstDeliveryStarted.complete(Unit)
                    keepFirstDeliveryBlocked.await()
                }
            }
        }
        val firstSend = async { store.send(TestInput.Add(1)) }

        withTimeout(TIMEOUT_MILLIS) { firstDeliveryStarted.await() }
        coordinator.cancelAndJoin()

        assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { firstSend.await() }
        )
        assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            withTimeout(TIMEOUT_MILLIS) { store.send(TestInput.Add(2)) }
        )
        assertEquals(TestState(3), store.state.value)

        val undelivered = recorder.snapshot().filterIsInstance<PulseFailure.UndeliveredUiEffect>()
        assertEquals(setOf(1, 2), undelivered.map { it.envelope.index }.toSet())
        assertEquals(2, undelivered.size)
        close(store)
    }

    @Test
    fun `terminal undelivered diagnostic during close completes close barrier exceptionally`() =
        runBlocking {
            val expected = CloseDiagnosticFatal()
            val store = DefaultPulseStore(
                initialState = TestState(0),
                reducer = PulseReducer<TestState, TestInput, TestEffect> { previous, input ->
                    ReduceOutcome.Changed(
                        state = previous.copy(value = previous.value + input.amount),
                        uiEffects = listOf(
                            TestEffect.Notice("delivering"),
                            TestEffect.Notice("pending"),
                        ),
                    )
                },
                config = PulseRuntimeConfig(
                    effectBufferCapacity = 1,
                    storeDispatcher = Dispatchers.Default,
                    consumerDispatcher = Dispatchers.Default,
                    errorHandler = PulseErrorHandler { _, failure, _ ->
                        if (failure is PulseFailure.UndeliveredUiEffect) throw expected
                    },
                    storeId = "terminal-close-diagnostic-test",
                ),
            )
            val firstDeliveryStarted = CompletableDeferred<Unit>()
            val keepDeliveryBlocked = CompletableDeferred<Unit>()
            val coordinator = launch(start = CoroutineStart.UNDISPATCHED) {
                store.effects.collect { envelope ->
                    if (envelope.index == 0) {
                        firstDeliveryStarted.complete(Unit)
                        keepDeliveryBlocked.await()
                    }
                }
            }

            val sent = async { store.send(TestInput.Add(1)) }
            withTimeout(TIMEOUT_MILLIS) { firstDeliveryStarted.await() }
            assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
                withTimeout(TIMEOUT_MILLIS) { sent.await() }
            )

            store.close()
            val thrown = withTimeout(TIMEOUT_MILLIS) {
                assertFailsWith<CloseDiagnosticFatal> { store.awaitClosed() }
            }

            assertEquals(expected.message, thrown.message)
            coordinator.cancelAndJoin()
            val rejected = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
            assertEquals(RejectionReason.Closed, rejected.reason)
        }

    @Test
    fun `terminal failure after reduce completes waiter exceptionally instead of hanging`() = runBlocking {
        val expected = IllegalStateException("strict failure handler")
        val store = DefaultPulseStore<TestState, TestInput, TestEffect>(
            initialState = TestState(0),
            reducer = PulseReducer { _, _ -> throw IllegalArgumentException("reducer failed") },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = PulseErrorHandler { _, _, _ -> throw expected },
                strictMode = true,
                storeId = "terminal-frame-test",
            ),
        )

        val thrown = withTimeout(TIMEOUT_MILLIS) {
            assertFailsWith<IllegalStateException> { store.send(TestInput.Fail) }
        }

        assertEquals(expected.message, thrown.message)
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
    }

    @Test
    fun `terminal task diagnostic closes store after preserving handle failure`() = runBlocking {
        val expected = TaskDiagnosticFatal()
        val store = DefaultPulseStore(
            initialState = TestState(0),
            reducer = PulseReducer<TestState, TestInput, TestEffect> { previous, _ ->
                ReduceOutcome.Unchanged()
            },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = PulseErrorHandler { _, failure, _ ->
                    if (failure is PulseFailure.ExecutorFailure) throw expected
                },
                storeId = "terminal-task-diagnostic-test",
            ),
        )
        val launch = assertIs<TaskLaunchResult.Accepted>(
            store.tasks.launch(TaskKey("terminal-task"), TaskPolicy.Latest) {
                throw IllegalStateException("ordinary task failure")
            }
        )

        val thrown = withTimeout(TIMEOUT_MILLIS) {
            assertFailsWith<TaskDiagnosticFatal> { launch.handle.awaitOutcome() }
        }

        assertSame(expected, thrown)
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
        val rejected = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
        assertEquals(RejectionReason.Closed, rejected.reason)
    }

    @Test
    fun `reducer failure plugin observes transition before typed diagnostic`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val failureObserved = CompletableDeferred<Unit>()
        val plugin = object : PulseStorePlugin<TestState, TestInput, TestEffect> {
            override val pluginId: String = "order-recorder"

            override fun onTransition(frame: TransitionFrame<TestState, TestInput, TestEffect>) {
                events += "transition:${frame.outcome}"
            }

            override fun onFailure(failure: PulseFailure) {
                events += "failure:${failure.phase}"
                failureObserved.complete(Unit)
            }
        }
        val store = DefaultPulseStore<TestState, TestInput, TestEffect>(
            initialState = TestState(0),
            reducer = PulseReducer { _, _ -> throw IllegalStateException("reducer failed") },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = PulseErrorHandler { _, _, _ -> },
                storeId = "failure-order-test",
            ),
            plugins = listOf(plugin),
        )

        assertIs<TransitionResult.Failed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Fail)
        )
        withTimeout(TIMEOUT_MILLIS) { failureObserved.await() }

        assertEquals(
            listOf("transition:ReducerFailed", "failure:REDUCER"),
            synchronized(events) { events.toList() },
        )
        close(store)
    }

    @Test
    fun `ordinary plugin failure is typed and isolated from later plugins and inputs`() = runBlocking {
        val recorder = FailureRecorder()
        val observedSequences = mutableListOf<Long>()
        val throwing = object : PulseStorePlugin<TestState, TestInput, TestEffect> {
            override val pluginId: String = "ordinary-throwing-plugin"

            override fun onTransition(frame: TransitionFrame<TestState, TestInput, TestEffect>) {
                error("broken plugin")
            }
        }
        val recording = object : PulseStorePlugin<TestState, TestInput, TestEffect> {
            override val pluginId: String = "recording-plugin"

            override fun onTransition(frame: TransitionFrame<TestState, TestInput, TestEffect>) {
                observedSequences += frame.sequenceId
            }
        }
        val store = DefaultPulseStore(
            initialState = TestState(0),
            reducer = PulseReducer<TestState, TestInput, TestEffect> { previous, input ->
                ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
            },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = recorder.handler,
                storeId = "ordinary-plugin-failure-test",
            ),
            plugins = listOf(throwing, recording),
        )

        assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(1))
        )
        assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
            store.send(TestInput.Add(2))
        )

        assertEquals(listOf(1L, 2L), observedSequences)
        val failures = recorder.snapshot().filterIsInstance<PulseFailure.PluginFailure>()
        assertEquals(2, failures.size)
        assertTrue(failures.all { it.context.component == "ordinary-throwing-plugin" })
        assertEquals(TestState(3), store.state.value)
        close(store)
    }

    @Test
    fun `plugin cancellation propagates and closes engine without hanging waiter`() = runBlocking {
        val expected = CancellationException("cancel plugin")
        val recorder = FailureRecorder()
        val plugin = object : PulseStorePlugin<TestState, TestInput, TestEffect> {
            override val pluginId: String = "cancelling-plugin"

            override fun onTransition(frame: TransitionFrame<TestState, TestInput, TestEffect>) {
                throw expected
            }
        }
        val store = DefaultPulseStore(
            initialState = TestState(0),
            reducer = PulseReducer<TestState, TestInput, TestEffect> { previous, input ->
                ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
            },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = recorder.handler,
                storeId = "plugin-cancellation-test",
            ),
            plugins = listOf(plugin),
        )

        val thrown = withTimeout(TIMEOUT_MILLIS) {
            assertFailsWith<CancellationException> { store.send(TestInput.Add(1)) }
        }

        assertEquals(expected.message, thrown.message)
        assertTrue(recorder.snapshot().isEmpty())
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
        val rejection = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
        assertEquals(RejectionReason.Closed, rejection.reason)
    }

    @Test
    fun `fatal plugin failure hook propagates and closes engine without hanging waiter`() = runBlocking {
        val expected = PluginFatalError()
        val recorder = FailureRecorder()
        val plugin = object : PulseStorePlugin<TestState, TestInput, TestEffect> {
            override val pluginId: String = "fatal-plugin"

            override fun onFailure(failure: PulseFailure) {
                throw expected
            }
        }
        val store = DefaultPulseStore<TestState, TestInput, TestEffect>(
            initialState = TestState(0),
            reducer = PulseReducer { _, _ -> error("reducer failed") },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                errorHandler = recorder.handler,
                storeId = "fatal-plugin-test",
            ),
            plugins = listOf(plugin),
        )

        val thrown = withTimeout(TIMEOUT_MILLIS) {
            assertFailsWith<PluginFatalError> { store.send(TestInput.Fail) }
        }

        assertSame(expected, thrown)
        assertTrue(recorder.snapshot().none { it is PulseFailure.PluginFailure })
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
        val rejection = assertIs<EnqueueResult.Rejected>(store.trySend(TestInput.Add(1)))
        assertEquals(RejectionReason.Closed, rejection.reason)
    }

    private fun store(
        reducer: PulseReducer<TestState, TestInput, TestEffect>,
        mailboxCapacity: Int = 128,
        recorder: FailureRecorder = FailureRecorder(),
    ): DefaultPulseStore<TestState, TestInput, TestEffect> {
        return DefaultPulseStore(
            initialState = TestState(0),
            reducer = reducer,
            config = PulseRuntimeConfig(
                mailboxCapacity = mailboxCapacity,
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = Dispatchers.Default,
                clock = IncrementingClock(),
                errorHandler = recorder.handler,
                storeId = "default-pulse-store-test",
            ),
        )
    }

    private fun gatedReducer(gate: ReducerGate): PulseReducer<TestState, TestInput, TestEffect> {
        return PulseReducer { previous, input ->
            if (input is TestInput.Block) gate.block()
            ReduceOutcome.Changed(previous.copy(value = previous.value + input.amount))
        }
    }

    private suspend fun close(store: DefaultPulseStore<TestState, TestInput, TestEffect>) {
        store.close()
        withTimeout(TIMEOUT_MILLIS) { store.awaitClosed() }
    }

    private class FailureRecorder {
        private val failures = Collections.synchronizedList(mutableListOf<PulseFailure>())
        val handler = PulseErrorHandler { _, failure, _ -> failures += failure }

        fun snapshot(): List<PulseFailure> = synchronized(failures) { failures.toList() }
    }

    private class ReducerGate {
        private val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun block() {
            entered.countDown()
            check(released.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "Timed out waiting to release reducer."
            }
        }

        suspend fun awaitEntered() {
            val didEnter = withContext(Dispatchers.IO) {
                entered.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
            assertTrue(didEnter, "Reducer did not start before timeout.")
        }

        fun release() {
            released.countDown()
        }
    }

    private class IncrementingClock : PulseClock {
        private val value = AtomicLong(0L)

        override fun nanoTime(): Long = value.incrementAndGet()
    }

    private class MarkingDispatcher(
        private val delegate: CoroutineDispatcher,
        private val marker: ThreadLocal<Boolean>,
    ) : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            delegate.dispatch(context) {
                marker.set(true)
                try {
                    block.run()
                } finally {
                    marker.remove()
                }
            }
        }
    }

    private data class TestState(val value: Int) : MviState

    private sealed interface TestInput : MviIntent {
        val amount: Int

        data class Add(override val amount: Int) : TestInput
        data object Block : TestInput { override val amount: Int = 1 }
        data object Fail : TestInput { override val amount: Int = 0 }
        data object Cancel : TestInput { override val amount: Int = 0 }
        data object Fatal : TestInput { override val amount: Int = 0 }
    }

    private sealed interface TestEffect : UiEffect {
        data class Notice(val message: String) : TestEffect
    }

    private class TestFatalError : LinkageError("fatal reducer")

    private class PluginFatalError : LinkageError("fatal plugin")

    private class CloseDiagnosticFatal : LinkageError("fatal close diagnostic")

    private class TaskDiagnosticFatal : LinkageError("fatal task diagnostic")

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
