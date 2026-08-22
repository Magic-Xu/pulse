package com.magic.mvicore.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskReplacementReason
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseClock
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSplitStoreViewModelTest {
    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `UI send and trySend invoke executor while only executor mutation changes state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val executed = mutableListOf<TestUi>()
            val reduced = mutableListOf<TestMutation>()
            val viewModel = viewModel(
                failures = failures,
                reducer = reducer { reduced += it },
                executor = PulseUiIntentExecutor { intent, context ->
                    executed += intent
                    if (intent is TestUi.Add) {
                        assertTrue(context.mutate(TestMutation.Add(intent.amount)))
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertEquals(PulseIntentExecutionResult.Completed, viewModel.send(TestUi.Add(2)))
            advanceUntilIdle()
            assertEquals(TestState(2), viewModel.state.value)

            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(3)))
            advanceUntilIdle()

            assertEquals(TestState(5), viewModel.state.value)
            assertEquals(listOf<TestUi>(TestUi.Add(2), TestUi.Add(3)), executed)
            assertEquals(
                listOf<TestMutation>(TestMutation.Add(2), TestMutation.Add(3)),
                reduced,
            )
            assertTrue(failures.isEmpty())

            close(viewModel)
        }

    @Test
    fun `send returns executor decision with monotonic id and stable start state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val intentIds = mutableListOf<Long>()
            val startStates = mutableListOf<TestState>()
            val latestStates = mutableListOf<TestState>()
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    intentIds += context.intentId
                    startStates += context.stateAtStart
                    latestStates += context.currentState
                    when (intent) {
                        is TestUi.Add -> {
                            context.mutate(TestMutation.Add(intent.amount))
                            PulseIntentExecutionDecision.Completed
                        }

                        TestUi.Ignore -> PulseIntentExecutionDecision.Ignored("not-applicable")
                        else -> PulseIntentExecutionDecision.Completed
                    }
                },
            )

            assertEquals(PulseIntentExecutionResult.Completed, viewModel.send(TestUi.Add(2)))
            assertEquals(
                PulseIntentExecutionResult.Ignored("not-applicable"),
                viewModel.send(TestUi.Ignore),
            )

            assertEquals(listOf(TestState(0), TestState(2)), startStates)
            assertEquals(listOf(TestState(0), TestState(2)), latestStates)
            assertTrue(intentIds[1] > intentIds[0])
            assertTrue(failures.isEmpty())
            close(viewModel)
        }

    @Test
    fun `Latest replacement ignores old token mutation and reports typed late mutation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val failureLanes = mutableListOf<String?>()
            val lane = ThreadLocal<String?>()
            val consumerDispatcher = MarkingDispatcher(
                delegate = mainDispatcherRule.dispatcher,
                marker = lane,
                value = "consumer",
            )
            val key = TaskKey("refresh")
            val oldStarted = CompletableDeferred<TaskToken>()
            val oldMutationResult = CompletableDeferred<Boolean>()
            val replacementMutationResult = CompletableDeferred<Boolean>()
            val launchResults = mutableListOf<TaskLaunchResult>()
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    when (intent) {
                        TestUi.StartLatest -> {
                            launchResults += context.launchTask(key, TaskPolicy.Latest) {
                                oldStarted.complete(token)
                                try {
                                    awaitCancellation()
                                } finally {
                                    withContext(NonCancellable + Dispatchers.IO) {
                                        oldMutationResult.complete(mutate(TestMutation.Add(100)))
                                    }
                                }
                            }
                        }

                        TestUi.ReplaceLatest -> {
                            launchResults += context.launchTask(key, TaskPolicy.Latest) {
                                replacementMutationResult.complete(mutate(TestMutation.Add(1)))
                            }
                        }

                        else -> Unit
                    }
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = PulseRuntimeConfig(
                    storeDispatcher = mainDispatcherRule.dispatcher,
                    consumerDispatcher = consumerDispatcher,
                    errorHandler = PulseErrorHandler { _, failure, _ ->
                        failureLanes += lane.get()
                        failures += failure
                    },
                    storeId = "background-late-mutation",
                ),
            )

            viewModel.send(TestUi.StartLatest)
            advanceUntilIdle()
            val oldToken = oldStarted.await()

            viewModel.send(TestUi.ReplaceLatest)
            advanceUntilIdle()

            val accepted = launchResults.map { result ->
                assertIs<TaskLaunchResult.Accepted>(result).handle
            }
            assertEquals(2, accepted.size)
            assertEquals(
                TaskOutcome.Replaced(TaskReplacementReason.LATEST),
                accepted[0].awaitOutcome(),
            )
            assertEquals(TaskOutcome.Completed, accepted[1].awaitOutcome())
            assertFalse(oldMutationResult.await())
            assertTrue(replacementMutationResult.await())
            assertEquals(TestState(1), viewModel.state.value)
            val late = assertIs<PulseFailure.LateMutation>(failures.single())
            assertEquals(key.value, late.taskKey)
            assertEquals(oldToken.value, late.token)
            assertEquals(listOf<String?>("consumer"), failureLanes)
            assertTrue(failures.none { it is PulseFailure.ExecutorFailure })

            close(viewModel)
        }

    @Test
    fun `one executor cancellation is not a failure and later intents still execute`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val expected = CancellationException("executor cancelled")
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    when (intent) {
                        TestUi.CancelExecutor -> throw expected
                        is TestUi.Add -> context.mutate(TestMutation.Add(intent.amount))
                        else -> Unit
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertEquals(
                PulseIntentExecutionResult.Cancelled,
                viewModel.send(TestUi.CancelExecutor),
            )
            advanceUntilIdle()
            viewModel.send(TestUi.Add(2))
            advanceUntilIdle()

            assertTrue(failures.none { it is PulseFailure.ExecutorFailure })
            assertEquals(TestState(2), viewModel.state.value)

            close(viewModel)
        }

    @Test
    fun `ordinary executor failure is typed and does not stop later intents`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val expected = IllegalStateException("executor failed")
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    when (intent) {
                        TestUi.CrashExecutor -> throw expected
                        is TestUi.Add -> context.mutate(TestMutation.Add(intent.amount))
                        else -> Unit
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            val failed = assertIs<PulseIntentExecutionResult.Failed>(
                viewModel.send(TestUi.CrashExecutor)
            )
            assertSame(expected, failed.cause)
            advanceUntilIdle()
            viewModel.send(TestUi.Add(4))
            advanceUntilIdle()

            val failure = assertIs<PulseFailure.ExecutorFailure>(failures.single())
            assertEquals("ui-intent-executor", failure.context.component)
            assertSame(expected, failure.cause)
            assertEquals(TestState(4), viewModel.state.value)

            close(viewModel)
        }

    @Test
    fun `terminal executor diagnostic closes ViewModel instead of leaving a zombie lane`() =
        runTest(mainDispatcherRule.dispatcher) {
            val parentJob = SupervisorJob()
            val terminalFailure = CompletableDeferred<Throwable>()
            val ownerScope = CoroutineScope(
                parentJob +
                    mainDispatcherRule.dispatcher +
                    CoroutineExceptionHandler { _, failure ->
                        terminalFailure.complete(failure)
                    }
            )
            val expected = LinkageError("terminal reporter")
            val config = PulseRuntimeConfig(
                storeDispatcher = mainDispatcherRule.dispatcher,
                consumerDispatcher = mainDispatcherRule.dispatcher,
                errorHandler = PulseErrorHandler { _, _, _ -> throw expected },
                storeId = "terminal-executor-store",
            )
            val viewModel = PulseSplitStoreViewModel<
                TestState,
                TestUi,
                TestMutation,
                TestEffect,
                >(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { intent, _ ->
                    if (intent == TestUi.CrashExecutor) {
                        throw IllegalStateException("ordinary executor failure")
                    }
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config,
                executionOwner = PulseAndroidExecutionOwner.from(ownerScope),
            )

            val sendFailure = async {
                try {
                    viewModel.send(TestUi.CrashExecutor)
                    null
                } catch (failure: Throwable) {
                    failure
                }
            }
            advanceUntilIdle()

            val actual = assertIs<LinkageError>(terminalFailure.await())
            assertEquals(expected.message, actual.message)
            assertEquals(expected.message, assertIs<LinkageError>(sendFailure.await()).message)
            viewModel.awaitClosed()
            assertIs<EnqueueResult.Rejected>(viewModel.trySend(TestUi.Add(1)))
            assertTrue(parentJob.isActive)
            parentJob.cancel()
        }

    @Test
    fun `background send and trySend marshal reducer and effect delivery to Main`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val lane = ThreadLocal<String?>()
            val storeDispatcher = MarkingDispatcher(
                delegate = mainDispatcherRule.dispatcher,
                marker = lane,
                value = "store",
            )
            val reducerLanes = Collections.synchronizedList(mutableListOf<String?>())
            val effectLanes = Collections.synchronizedList(
                mutableListOf<ContinuationInterceptor?>()
            )
            val effects = Collections.synchronizedList(mutableListOf<TestEffect>())
            val config = runtimeConfig(
                failures = failures,
                storeDispatcher = storeDispatcher,
                consumerDispatcher = Dispatchers.Main.immediate,
            )
            val viewModel = viewModel(
                failures = failures,
                runtimeConfig = config,
                reducer = PulseMutationReducer { previous, mutation ->
                    reducerLanes += lane.get()
                    val add = mutation as TestMutation.Add
                    val next = previous.copy(value = previous.value + add.amount)
                    ReduceOutcome.Changed(next, listOf(TestEffect.Changed(next.value)))
                },
                executor = PulseUiIntentExecutor { intent, context ->
                    if (intent is TestUi.Add) context.mutate(TestMutation.Add(intent.amount))
                    PulseIntentExecutionDecision.Completed
                },
            )
            val coordinator = backgroundScope.launch(
                context = Dispatchers.Main.immediate,
                start = CoroutineStart.UNDISPATCHED,
            ) {
                viewModel.uiEffects.collect { envelope ->
                    effectLanes += currentCoroutineContext()[ContinuationInterceptor]
                    effects += envelope.payload
                }
            }

            withContext(Dispatchers.IO) {
                viewModel.send(TestUi.Add(7))
            }
            advanceUntilIdle()
            val enqueued = withContext(Dispatchers.IO) {
                viewModel.trySend(TestUi.Add(1))
            }
            assertIs<EnqueueResult.Enqueued>(enqueued)
            advanceUntilIdle()

            assertEquals(listOf("store", "store"), synchronized(reducerLanes) { reducerLanes.toList() })
            assertTrue(
                synchronized(effectLanes) { effectLanes.toList() }
                    .all { it === Dispatchers.Main }
            )
            assertEquals(
                listOf(TestEffect.Changed(7), TestEffect.Changed(8)),
                synchronized(effects) { effects.toList() },
            )
            assertEquals(TestState(8), viewModel.state.value)
            assertSame(Dispatchers.Main.immediate, androidPulseRuntimeConfig().storeDispatcher)
            assertSame(Dispatchers.Main.immediate, androidPulseRuntimeConfig().consumerDispatcher)
            assertTrue(failures.isEmpty())

            coordinator.cancelAndJoin()
            close(viewModel)
        }

    @Test
    fun `injected execution owner controls lifetime while configured dispatcher owns adapter work`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val lane = ThreadLocal<String?>()
            val parentJob = SupervisorJob()
            val injectedScope = CoroutineScope(
                parentJob + MarkingDispatcher(
                    delegate = mainDispatcherRule.dispatcher,
                    marker = lane,
                    value = "injected",
                ),
            )
            val executorLane = CompletableDeferred<String?>()
            val configuredDispatcher = MarkingDispatcher(
                delegate = mainDispatcherRule.dispatcher,
                marker = lane,
                value = "configured",
            )
            val config = runtimeConfig(
                failures = failures,
                consumerDispatcher = configuredDispatcher,
            )
            val viewModel = PulseSplitStoreViewModel(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                    executorLane.complete(lane.get())
                    if (intent is TestUi.Add) {
                        context.mutate(TestMutation.Add(intent.amount))
                    }
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config,
                savedState = null,
                executionOwner = PulseAndroidExecutionOwner.from(injectedScope),
            )

            viewModel.send(TestUi.Add(3))
            advanceUntilIdle()

            assertEquals("configured", executorLane.await())
            assertEquals(TestState(3), viewModel.state.value)
            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()

            assertTrue(parentJob.isActive)
            assertTrue(failures.isEmpty())
            parentJob.cancel()
        }

    @Test
    fun `ViewModelStore clear closes tasks and engine idempotently without late writeback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val taskStarted = CompletableDeferred<TaskToken>()
            val lateMutation = CompletableDeferred<Boolean>()
            val executor = PulseUiIntentExecutor<TestState, TestUi, TestMutation> { intent, context ->
                if (intent == TestUi.StartLatest) {
                    context.launchTask(TaskKey("owned-task"), TaskPolicy.Latest) {
                        taskStarted.complete(token)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                lateMutation.complete(mutate(TestMutation.Add(50)))
                            }
                        }
                    }
                }
                PulseIntentExecutionDecision.Completed
            }
            val owned = OwnedTestViewModel(
                reducer = reducer(),
                executor = executor,
                runtimeConfig = runtimeConfig(failures),
            )
            val owner = TestOwner()
            val provider = ViewModelProvider(owner, ExistingViewModelFactory(owned))
            val viewModel = provider[OwnedTestViewModel::class.java]

            assertSame(owned, viewModel)
            viewModel.send(TestUi.StartLatest)
            advanceUntilIdle()
            taskStarted.await()

            owner.viewModelStore.clear()
            advanceUntilIdle()
            viewModel.awaitClosed()

            assertFalse(lateMutation.await())
            assertEquals(TestState(0), viewModel.state.value)
            viewModel.send(TestUi.Add(1))
            advanceUntilIdle()
            assertEquals(TestState(0), viewModel.state.value)
            assertIs<EnqueueResult.Rejected>(viewModel.trySend(TestUi.Add(1)))
            viewModel.close()
            viewModel.close()
            viewModel.awaitClosed()
            assertTrue(failures.none { it is PulseFailure.ExecutorFailure })
            val rejectedLateMutation = assertIs<PulseFailure.LateMutation>(failures.single())
            assertEquals("owned-task", rejectedLateMutation.taskKey)
            assertEquals(1, owned.pulseClearedCount)

            val onCleared = PulseSplitStoreViewModel::class.java.getDeclaredMethod("onCleared")
            assertTrue(Modifier.isFinal(onCleared.modifiers))
        }

    @Test
    fun `awaitClosed includes executor cancellation cleanup`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val executorStarted = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val executorCleaned = CompletableDeferred<Unit>()
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, _ ->
                    if (intent == TestUi.BlockExecutor) {
                        executorStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                releaseCleanup.await()
                                executorCleaned.complete(Unit)
                            }
                        }
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            val sendResult = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.send(TestUi.BlockExecutor)
            }
            advanceUntilIdle()
            executorStarted.await()

            viewModel.close()
            val closed = async { viewModel.awaitClosed() }
            runCurrent()

            assertFalse(executorCleaned.isCompleted)
            assertFalse(closed.isCompleted)

            releaseCleanup.complete(Unit)
            advanceUntilIdle()

            closed.await()
            assertEquals(PulseIntentExecutionResult.Cancelled, sendResult.await())
            assertTrue(executorCleaned.isCompleted)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `parent cancellation establishes cutoff before child cleanup finishes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val parentJob = SupervisorJob()
            val executorStarted = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val executorCleaned = CompletableDeferred<Unit>()
            val viewModel = PulseSplitStoreViewModel(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { intent, _ ->
                    if (intent == TestUi.BlockExecutor) {
                        executorStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                releaseCleanup.await()
                                executorCleaned.complete(Unit)
                            }
                        }
                    }
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = runtimeConfig(failures),
                executionOwner = PulseAndroidExecutionOwner.from(
                    CoroutineScope(parentJob + mainDispatcherRule.dispatcher)
                ),
            )

            val sendResult = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.send(TestUi.BlockExecutor)
            }
            advanceUntilIdle()
            executorStarted.await()

            parentJob.cancel()
            assertIs<EnqueueResult.Rejected>(viewModel.trySend(TestUi.Add(1)))
            val closed = async { viewModel.awaitClosed() }
            runCurrent()
            assertFalse(closed.isCompleted)
            assertFalse(executorCleaned.isCompleted)

            releaseCleanup.complete(Unit)
            advanceUntilIdle()

            closed.await()
            assertEquals(PulseIntentExecutionResult.Cancelled, sendResult.await())
            assertTrue(executorCleaned.isCompleted)
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `close drains an admitted UI frame but cancels owner executor work`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val executed = mutableListOf<TestUi>()
            val viewModel = viewModel(
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    executed += intent
                    if (intent is TestUi.Add) {
                        context.mutate(TestMutation.Add(intent.amount))
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(9)))
            viewModel.close()
            viewModel.awaitClosed()

            assertTrue(executed.isEmpty())
            assertEquals(TestState(0), viewModel.state.value)
            assertIs<EnqueueResult.Rejected>(viewModel.trySend(TestUi.Add(1)))
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `send frame admitted before close drains even though its owner Job is cancelled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val clockCalls = AtomicInteger(0)
            val executed = mutableListOf<TestUi>()
            val config = PulseRuntimeConfig(
                storeDispatcher = mainDispatcherRule.dispatcher,
                consumerDispatcher = UnconfinedTestDispatcher(testScheduler),
                clock = PulseClock { clockCalls.incrementAndGet().toLong() },
                errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                storeId = "send-drain-store",
            )
            val viewModel = PulseSplitStoreViewModel<
                TestState,
                TestUi,
                TestMutation,
                TestEffect,
                >(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { intent, _ ->
                    executed += intent
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config,
            )

            // Unconfined owner execution admits the frame immediately, while the Store processor
            // remains queued on the StandardTestDispatcher until the scheduler advances.
            val sendResult = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.send(TestUi.Add(5))
            }
            assertEquals(0, clockCalls.get())
            viewModel.close()

            advanceUntilIdle()
            viewModel.awaitClosed()

            assertEquals(PulseIntentExecutionResult.Cancelled, sendResult.await())
            assertEquals(2, clockCalls.get())
            assertTrue(executed.isEmpty())
            assertTrue(failures.isEmpty())
        }

    private fun viewModel(
        failures: MutableList<PulseFailure>,
        reducer: PulseMutationReducer<TestState, TestMutation, TestEffect> = reducer(),
        executor: PulseUiIntentExecutor<TestState, TestUi, TestMutation>,
        runtimeConfig: PulseRuntimeConfig = runtimeConfig(failures),
    ): PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect> {
        return PulseSplitStoreViewModel(
            initialState = TestState(0),
            mutationReducer = reducer,
            uiIntentExecutor = executor,
            runtimeConfig = runtimeConfig,
        )
    }

    private fun runtimeConfig(
        failures: MutableList<PulseFailure>,
        storeDispatcher: CoroutineDispatcher = mainDispatcherRule.dispatcher,
        consumerDispatcher: CoroutineDispatcher = mainDispatcherRule.dispatcher,
    ): PulseRuntimeConfig {
        return PulseRuntimeConfig(
            storeDispatcher = storeDispatcher,
            consumerDispatcher = consumerDispatcher,
            errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
            storeId = "android-test-store",
        )
    }

    private suspend fun close(
        viewModel: PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>,
    ) {
        viewModel.close()
        viewModel.awaitClosed()
    }

    private class MarkingDispatcher(
        private val delegate: CoroutineDispatcher,
        private val marker: ThreadLocal<String?>,
        private val value: String,
    ) : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            delegate.dispatch(context) {
                val previous = marker.get()
                marker.set(value)
                try {
                    block.run()
                } finally {
                    marker.set(previous)
                }
            }
        }
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private class ExistingViewModelFactory(
        private val viewModel: ViewModel,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
    }

    private class OwnedTestViewModel(
        reducer: PulseMutationReducer<TestState, TestMutation, TestEffect>,
        executor: PulseUiIntentExecutor<TestState, TestUi, TestMutation>,
        runtimeConfig: PulseRuntimeConfig,
    ) : PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>(
        initialState = TestState(0),
        mutationReducer = reducer,
        uiIntentExecutor = executor,
        runtimeConfig = runtimeConfig,
    ) {
        var pulseClearedCount: Int = 0
            private set

        override fun onPulseCleared() {
            pulseClearedCount += 1
        }
    }

    private data class TestState(val value: Int) : MviState

    private sealed interface TestUi : MviUiIntent {
        data class Add(val amount: Int) : TestUi
        data object StartLatest : TestUi
        data object ReplaceLatest : TestUi
        data object CancelExecutor : TestUi
        data object CrashExecutor : TestUi
        data object BlockExecutor : TestUi
        data object Ignore : TestUi
    }

    private sealed interface TestMutation : MviMutation {
        data class Add(val amount: Int) : TestMutation
    }

    private sealed interface TestEffect : UiEffect {
        data class Changed(val value: Int) : TestEffect
    }

    private companion object {
        fun reducer(
            observer: (TestMutation) -> Unit = {},
        ): PulseMutationReducer<TestState, TestMutation, TestEffect> {
            return PulseMutationReducer { previous, mutation ->
                observer(mutation)
                when (mutation) {
                    is TestMutation.Add -> ReduceOutcome.Changed(
                        previous.copy(value = previous.value + mutation.amount)
                    )
                }
            }
        }
    }
}
