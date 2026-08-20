package com.magic.mvicore.testing

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseStorePlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Reusable behavioral contract for [com.magic.mvicore.runtime.PulseStore] implementations.
 *
 * Test frameworks can expose each method as an individual test for precise failure reporting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseStoreTck(
    private val factory: PulseStoreTckFactory = DefaultPulseStoreTckFactory,
) {
    fun sequentialOrdering() = runPulseTest {
        val store = counterStore()

        listOf(1, 2, 3).forEach { amount -> store.send(CounterIntent.Add(amount)) }
        val frames = store.transitionProbe.awaitCount(3)

        assertEquals(listOf(1L, 2L, 3L), frames.map { it.sequenceId })
        assertEquals(listOf(0, 1, 3), frames.map { it.stateBefore.value })
        assertEquals(listOf(1, 3, 6), frames.map { it.stateAfter.value })
    }

    fun concurrentTotalOrder() = runPulseTest {
        val count = 64
        val store = counterStore(runtimeConfig(mailboxCapacity = count))

        val results = sendConcurrently(store, List(count) { CounterIntent.Add(1) })
        val frames = store.transitionProbe.awaitCount(count)

        assertTrue(results.all { it is TransitionResult.Completed })
        assertEquals((1L..count.toLong()).toList(), frames.map { it.sequenceId })
        assertEquals(count, frames.map { it.requestId }.toSet().size)
        assertEquals(CounterState(count), store.state.value)
    }

    fun stateSubscriptionStartsWithAtomicSnapshot() = runPulseTest {
        val store = counterStore()

        assertEquals(listOf(CounterState(0)), store.stateProbe.awaitCount(1))
        store.send(CounterIntent.Add(2))
        store.stateProbe.awaitValue(CounterState(2))

        store.stateProbe.assertValues(CounterState(0), CounterState(2))
        assertEquals(CounterState(2), store.state.value)
    }

    fun equalChangedNormalizesToUnchanged() = runPulseTest {
        val store = counterStore()

        val result = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Same)
        )

        assertEquals(TransitionOutcome.Unchanged, result.frame.outcome)
        assertEquals(0L, result.frame.stateRevision)
        assertSame(result.frame.stateBefore, result.frame.stateAfter)
    }

    fun ignoredInputIsObservable() = runPulseTest {
        val store = counterStore()

        val result = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Ignore)
        )

        assertEquals(TransitionOutcome.Ignored("not-applicable"), result.frame.outcome)
        assertEquals(0L, result.frame.stateRevision)
        assertTrue(result.frame.uiEffects.isEmpty())
    }

    fun reducerFailureIsTypedAndProcessorContinues() = runPulseTest {
        val store = counterStore()

        val failed = assertIs<TransitionResult.Failed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Fail)
        )
        val recovered = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Add(1))
        )
        val reported = store.failureProbe.awaitFailure<PulseFailure.ReducerFailure>()

        assertEquals(TransitionOutcome.ReducerFailed, failed.frame.outcome)
        assertSame(failed.failure, failed.frame.reducerFailure)
        assertSame(failed.failure, reported)
        assertEquals(2L, recovered.frame.sequenceId)
        assertEquals(CounterState(1), recovered.frame.stateAfter)
    }

    fun effectCardinalityAndEnvelopeOrder() = runPulseTest {
        val store = counterStore()

        store.send(CounterIntent.Emit(emptyList()))
        store.send(CounterIntent.Emit(listOf(7)))
        store.send(CounterIntent.Emit(listOf(8, 9, 10)))
        val effects = store.effectProbe.awaitCount(4)

        assertEquals(listOf(1L, 2L, 3L, 4L), effects.map { it.effectId })
        assertEquals(listOf(2L, 3L, 3L, 3L), effects.map { it.sequenceId })
        assertEquals(listOf(0, 0, 1, 2), effects.map { it.index })
        assertEquals(listOf(7, 8, 9, 10), effects.map { it.payload.value })
    }

    fun effectConsumerFailureIsIsolatedAndReportedOnce() = runPulseTest {
        val config = runtimeConfig()
        val store = factory.create(
            initialState = CounterState(0),
            reducer = COUNTER_REDUCER,
            config = config,
        )
        val observed = mutableListOf<Int>()
        val secondObserved = CompletableDeferred<Unit>()
        val coordinator = testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.effects.collect { envelope ->
                observed += envelope.payload.value
                if (envelope.payload.value == 1) throw IllegalStateException("consumer failed")
                if (envelope.payload.value == 2) secondObserved.complete(Unit)
            }
        }

        try {
            store.send(CounterIntent.Emit(listOf(1, 2)))
            secondObserved.await()

            assertEquals(listOf(1, 2), observed)
            config.failureProbe.awaitFailure<PulseFailure.UiEffectConsumerFailure>()
            assertEquals(
                1,
                config.failureProbe.snapshot()
                    .filterIsInstance<PulseFailure.UiEffectConsumerFailure>()
                    .size,
            )
        } finally {
            store.close()
            advanceUntilIdle()
            store.awaitClosed()
            coordinator.cancelAndJoin()
        }
    }

    fun overflowIsExplicitAndReported() = runPulseTest {
        val store = counterStore(runtimeConfig(mailboxCapacity = 1))

        assertIs<EnqueueResult.Enqueued>(store.trySend(CounterIntent.Add(1)))
        assertEquals(EnqueueResult.Full, store.trySend(CounterIntent.Add(1)))

        store.failureProbe.awaitFailure<PulseFailure.MailboxOverflow>()
        assertIs<PulseFailure.MailboxOverflow>(store.failureProbe.snapshot().single())
        assertEquals(CounterState(1), store.state.value)
    }

    fun closeEstablishesCutoffAndDrains() = runPulseTest {
        val store = counterStore(runtimeConfig(mailboxCapacity = 2))
        assertIs<EnqueueResult.Enqueued>(store.trySend(CounterIntent.Add(1)))
        assertIs<EnqueueResult.Enqueued>(store.trySend(CounterIntent.Add(2)))

        store.close()
        store.close()
        val closing = assertIs<EnqueueResult.Rejected>(store.trySend(CounterIntent.Add(10)))
        assertEquals(RejectionReason.Closing, closing.reason)
        advanceUntilIdle()
        store.awaitClosed()

        assertEquals(CounterState(3), store.state.value)
        store.transitionProbe.assertSequence(1L, 2L)
        val closed = assertIs<EnqueueResult.Rejected>(store.trySend(CounterIntent.Add(10)))
        assertEquals(RejectionReason.Closed, closed.reason)
    }

    fun cancelledWaitingSenderDoesNotConsumeSequence() = runPulseTest {
        val store = counterStore(runtimeConfig(mailboxCapacity = 1))
        assertIs<EnqueueResult.Enqueued>(store.trySend(CounterIntent.Add(1)))
        val cancelled = testScope.async(start = CoroutineStart.UNDISPATCHED) {
            store.send(CounterIntent.Add(100))
        }

        cancelled.cancelAndJoin()
        advanceUntilIdle()
        val next = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Add(1))
        )

        store.transitionProbe.assertSequence(1L, 2L)
        assertEquals(2L, next.frame.sequenceId)
        assertEquals(CounterState(2), store.state.value)
    }

    fun reentrantSendRunsAfterCurrentFrame() = runPulseTest {
        val store = counterStore()
        val reentrant = CompletableDeferred<TransitionResult<CounterState, CounterIntent, CounterEffect>>()
        val consumer = testScope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.state.drop(1).take(1).collect {
                reentrant.complete(store.send(CounterIntent.Add(10)))
            }
        }

        val first = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            store.send(CounterIntent.Add(1))
        )
        val second = assertIs<TransitionResult.Completed<CounterState, CounterIntent, CounterEffect>>(
            reentrant.await()
        )
        consumer.cancelAndJoin()

        assertEquals(1L, first.frame.sequenceId)
        assertEquals(2L, second.frame.sequenceId)
        assertEquals(first.frame.stateAfter, second.frame.stateBefore)
        assertEquals(CounterState(11), second.frame.stateAfter)
    }

    fun pluginFailureIsIsolatedAndReportedOnce() = runPulseTest {
        val observed = mutableListOf<Long>()
        val throwing = object : PulseStorePlugin<CounterState, CounterIntent, CounterEffect> {
            override val pluginId: String = "throwing-plugin"
            override fun onTransition(
                frame: com.magic.mvicore.contract.TransitionFrame<CounterState, CounterIntent, CounterEffect>,
            ) {
                throw IllegalStateException("plugin failed")
            }
        }
        val recording = object : PulseStorePlugin<CounterState, CounterIntent, CounterEffect> {
            override val pluginId: String = "recording-plugin"
            override fun onTransition(
                frame: com.magic.mvicore.contract.TransitionFrame<CounterState, CounterIntent, CounterEffect>,
            ) {
                observed += frame.sequenceId
            }
        }
        val store = counterStore(plugins = listOf(throwing, recording))

        store.send(CounterIntent.Add(1))
        advanceUntilIdle()

        assertEquals(listOf(1L), observed)
        assertEquals(1, store.failureProbe.snapshot().filterIsInstance<PulseFailure.PluginFailure>().size)
    }

    fun tenThousandInputStress() = runPulseTest {
        val count = 10_000
        val store = counterStore(runtimeConfig(mailboxCapacity = 128))

        repeat(count) { store.send(CounterIntent.Add(1)) }
        val frames = store.transitionProbe.awaitCount(count)

        assertEquals(count, frames.size)
        assertEquals(1L, frames.first().sequenceId)
        assertEquals(count.toLong(), frames.last().sequenceId)
        assertEquals(CounterState(count), store.state.value)
        store.failureProbe.assertEmpty()
    }

    private fun PulseTestScope.counterStore(
        config: TestRuntimeConfig = runtimeConfig(),
        plugins: List<PulseStorePlugin<CounterState, CounterIntent, CounterEffect>> = emptyList(),
    ): TestPulseStore<CounterState, CounterIntent, CounterEffect> {
        return testStore(
            initialState = CounterState(0),
            reducer = COUNTER_REDUCER,
            config = config,
            plugins = plugins,
            factory = factory,
        )
    }

    private data class CounterState(val value: Int) : MviState

    private sealed interface CounterIntent : MviIntent {
        data class Add(val amount: Int) : CounterIntent
        data object Same : CounterIntent
        data object Ignore : CounterIntent
        data object Fail : CounterIntent
        data class Emit(val values: List<Int>) : CounterIntent
    }

    private data class CounterEffect(val value: Int) : UiEffect

    private companion object {
        val COUNTER_REDUCER = PulseReducer<CounterState, CounterIntent, CounterEffect> { previous, input ->
            when (input) {
                is CounterIntent.Add -> ReduceOutcome.Changed(
                    previous.copy(value = previous.value + input.amount)
                )
                CounterIntent.Same -> ReduceOutcome.Changed(previous.copy())
                CounterIntent.Ignore -> ReduceOutcome.Ignored("not-applicable")
                CounterIntent.Fail -> throw IllegalStateException("reduce failed")
                is CounterIntent.Emit -> ReduceOutcome.Unchanged(
                    input.values.map(::CounterEffect)
                )
            }
        }
    }
}
