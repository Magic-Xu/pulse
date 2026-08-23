package com.magic.mvicore.android

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSplitAdmissionTest {
    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `split capacity covers executor backlog and preserves mutation progress`() =
        runTest(mainDispatcherRule.dispatcher) {
            val releaseFirst = CompletableDeferred<Unit>()
            val failures = mutableListOf<PulseFailure>()
            val executed = mutableListOf<Int>()
            val viewModel = viewModel(
                capacity = 2,
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    intent as TestUi.Add
                    executed += intent.amount
                    if (intent.amount == 1) releaseFirst.await()
                    assertTrue(context.mutate(TestMutation.Add(intent.amount)))
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(1)))
            runCurrent()
            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(2)))
            runCurrent()

            // The Core mailbox is empty here, but both Split executor slots are still in flight.
            // The third callback must see end-to-end pressure instead of entering a capacity cycle.
            assertEquals(EnqueueResult.Full, viewModel.trySend(TestUi.Add(3)))
            runCurrent()
            val overflow = assertIs<PulseFailure.SplitAdmissionOverflow>(failures.single())
            assertEquals(2, overflow.capacity)

            releaseFirst.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(1, 2), executed)
            assertEquals(TestState(3), viewModel.state.value)

            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(3)))
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), executed)
            assertEquals(TestState(6), viewModel.state.value)

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
        }

    @Test
    fun `callback ingress always reports bounded rejection and keeps accepted order`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val rejected = mutableListOf<Pair<TestUi, EnqueueResult>>()
            val executed = mutableListOf<Int>()
            val viewModel = viewModel(
                capacity = 1,
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    intent as TestUi.Add
                    executed += intent.amount
                    context.mutate(TestMutation.Add(intent.amount))
                    PulseIntentExecutionDecision.Completed
                },
            )
            val ingress = viewModel.callbackIngress { intent, result ->
                rejected += intent to result
            }

            assertIs<EnqueueResult.Enqueued>(ingress.submit(TestUi.Add(1)))
            assertEquals(EnqueueResult.Full, ingress.submit(TestUi.Add(2)))
            assertEquals(
                listOf<Pair<TestUi, EnqueueResult>>(TestUi.Add(2) to EnqueueResult.Full),
                rejected,
            )

            advanceUntilIdle()
            assertIs<EnqueueResult.Enqueued>(ingress.submit(TestUi.Add(2)))
            advanceUntilIdle()

            assertEquals(listOf(1, 2), executed)
            assertEquals(TestState(3), viewModel.state.value)

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
        }

    @Test
    fun `suspending admission processes ten thousand intents without loss or capacity cycle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            var executed = 0
            val viewModel = viewModel(
                capacity = 2,
                failures = failures,
                executor = PulseUiIntentExecutor { _, context ->
                    executed += 1
                    assertTrue(context.mutate(TestMutation.Add(1)))
                    PulseIntentExecutionDecision.Completed
                },
            )

            List(PRODUCER_COUNT) {
                async {
                    repeat(INTENTS_PER_PRODUCER) {
                        assertEquals(
                            PulseIntentExecutionResult.Completed,
                            viewModel.send(TestUi.Add(1)),
                        )
                    }
                }
            }.awaitAll()

            assertEquals(TOTAL_INTENTS, executed)
            assertEquals(TestState(TOTAL_INTENTS), viewModel.state.value)
            assertTrue(failures.isEmpty())

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
        }

    @Test
    fun `caller cancellation does not release an admission still owned by executor`() =
        runTest(mainDispatcherRule.dispatcher) {
            val releaseExecutor = CompletableDeferred<Unit>()
            val executorStarted = CompletableDeferred<Unit>()
            val failures = mutableListOf<PulseFailure>()
            val viewModel = viewModel(
                capacity = 1,
                failures = failures,
                executor = PulseUiIntentExecutor { intent, context ->
                    intent as TestUi.Add
                    executorStarted.complete(Unit)
                    releaseExecutor.await()
                    assertTrue(context.mutate(TestMutation.Add(intent.amount)))
                    PulseIntentExecutionDecision.Completed
                },
            )

            val caller = async { viewModel.send(TestUi.Add(1)) }
            runCurrent()
            assertTrue(executorStarted.isCompleted)
            caller.cancelAndJoin()

            assertEquals(EnqueueResult.Full, viewModel.trySend(TestUi.Add(2)))

            releaseExecutor.complete(Unit)
            advanceUntilIdle()
            assertEquals(TestState(1), viewModel.state.value)

            assertIs<EnqueueResult.Enqueued>(viewModel.trySend(TestUi.Add(2)))
            advanceUntilIdle()
            assertEquals(TestState(3), viewModel.state.value)

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
        }

    @Test
    fun `cancellation before Core starts suppresses executor and reclaims Split capacity`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val executed = mutableListOf<Int>()
            val viewModel = PulseSplitStoreViewModel<
                TestState,
                TestUi,
                TestMutation,
                TestEffect,
                >(
                initialState = TestState(0),
                mutationReducer = PulseMutationReducer { state, mutation ->
                    mutation as TestMutation.Add
                    ReduceOutcome.Changed(state.copy(value = state.value + mutation.amount))
                },
                uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                    intent as TestUi.Add
                    executed += intent.amount
                    assertTrue(context.mutate(TestMutation.Add(intent.amount)))
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = PulseRuntimeConfig(
                    mailboxCapacity = 1,
                    storeDispatcher = mainDispatcherRule.dispatcher,
                    consumerDispatcher = UnconfinedTestDispatcher(testScheduler),
                    errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                    storeId = "split-cancel-before-transfer",
                ),
            )

            val cancelled = async(
                context = UnconfinedTestDispatcher(testScheduler),
                start = CoroutineStart.UNDISPATCHED,
            ) {
                viewModel.send(TestUi.Add(1))
            }
            assertFalse(cancelled.isCompleted)
            cancelled.cancelAndJoin()
            advanceUntilIdle()

            assertTrue(executed.isEmpty())
            assertEquals(TestState(0), viewModel.state.value)
            assertEquals(
                PulseIntentExecutionResult.Completed,
                viewModel.send(TestUi.Add(2)),
            )
            assertEquals(listOf(2), executed)
            assertEquals(TestState(2), viewModel.state.value)
            assertTrue(failures.isEmpty())

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
        }

    @Test
    fun `close releases every sender queued on the Split permit`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val executorStarted = CompletableDeferred<Unit>()
            val viewModel = viewModel(
                capacity = 1,
                failures = failures,
                executor = PulseUiIntentExecutor { _, _ ->
                    executorStarted.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    PulseIntentExecutionDecision.Completed
                },
            )
            val active = async { viewModel.send(TestUi.Add(1)) }
            runCurrent()
            assertTrue(executorStarted.isCompleted)
            val waiting = (2..5).map { value ->
                async { viewModel.send(TestUi.Add(value)) }
            }
            runCurrent()

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()

            assertEquals(PulseIntentExecutionResult.Cancelled, active.await())
            waiting.forEach { sender ->
                val rejection = assertIs<PulseIntentExecutionResult.Rejected>(sender.await())
                assertTrue(
                    rejection.reason == com.magic.mvicore.contract.RejectionReason.Closing ||
                        rejection.reason == com.magic.mvicore.contract.RejectionReason.Closed
                )
            }
            assertEquals(TestState(0), viewModel.state.value)
            assertTrue(failures.isEmpty())
        }

    private fun viewModel(
        capacity: Int,
        failures: MutableList<PulseFailure>,
        executor: PulseUiIntentExecutor<TestState, TestUi, TestMutation>,
    ): PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect> {
        return PulseSplitStoreViewModel(
            initialState = TestState(0),
            mutationReducer = PulseMutationReducer { state, mutation ->
                mutation as TestMutation.Add
                ReduceOutcome.Changed(state.copy(value = state.value + mutation.amount))
            },
            uiIntentExecutor = executor,
            runtimeConfig = PulseRuntimeConfig(
                mailboxCapacity = capacity,
                storeDispatcher = mainDispatcherRule.dispatcher,
                consumerDispatcher = mainDispatcherRule.dispatcher,
                errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                storeId = "split-admission-test",
            ),
        )
    }

    private data class TestState(val value: Int) : MviState

    private sealed interface TestUi : MviUiIntent {
        data class Add(val amount: Int) : TestUi
    }

    private sealed interface TestMutation : MviMutation {
        data class Add(val amount: Int) : TestMutation
    }

    private sealed interface TestEffect : UiEffect

    private companion object {
        const val PRODUCER_COUNT = 8
        const val INTENTS_PER_PRODUCER = 1_250
        const val TOTAL_INTENTS = PRODUCER_COUNT * INTENTS_PER_PRODUCER
    }
}
