package com.magic.mvicore.android

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseClock
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSplitDiagnosticsTest {
    @get:Rule internal val main = MainDispatcherRule()

    @Test
    fun `timing separates admission executor wait and execution and correlates task mutations`() =
        runTest(main.dispatcher) {
            val events = mutableListOf<PulseSplitDiagnosticEvent>()
            val frames = mutableListOf<TransitionFrame<State, PulseSplitInput<Intent, Mutation>, Effect>>()
            val viewModel = PulseSplitStoreViewModel<State, Intent, Mutation, Effect>(
                initialState = State(),
                mutationReducer = reducer,
                uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                    if (intent.number == 1) {
                        context.launchTask(TaskKey("private-task-key"), TaskPolicy.Latest) {
                            delay(200)
                            mutateResult(Mutation("private-mutation"))
                        }
                    }
                    delay(if (intent.number == 1) 100 else 20)
                    context.mutateResult(Mutation("private-mutation"))
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config(2, PulseClock { testScheduler.currentTime * 1_000_000 }),
                executionOwner = PulseAndroidExecutionOwner.from(backgroundScope),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.diagnostics.collect { events += it }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.transitions.collect { frames += it }
            }
            val first = async { viewModel.send(Intent(1, "private-input")) }
            runCurrent()
            advanceTimeBy(10)
            val second = async { viewModel.send(Intent(2, "private-input")) }
            val third = async { viewModel.send(Intent(3, "private-input")) }
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            listOf(first, second, third).forEach { assertEquals(PulseIntentExecutionResult.Completed, it.await()) }

            val executions = events.filterIsInstance<PulseSplitDiagnosticEvent.ExecutionCompleted>()
            assertEquals(3, executions.size)
            assertEquals(100_000_000, executions[0].executionDurationNanos)
            assertEquals(90_000_000, executions[1].executorWaitNanos)
            assertEquals(90_000_000, executions[2].admissionWaitNanos)
            assertEquals(20_000_000, executions[2].executorWaitNanos)
            assertEquals(20_000_000, executions[2].executionDurationNanos)
            assertEquals(130_000_000, executions[2].totalDurationNanos)
            assertTrue(executions.all { it.outcome == PulseIntentExecutionStatus.COMPLETED })

            val mutations = events.filterIsInstance<PulseSplitDiagnosticEvent.MutationCompleted>()
            assertEquals(4, mutations.size)
            assertEquals(executions[0].originIntentId, mutations.single { it.taskToken != null }.originIntentId)
            mutations.forEach { mutation ->
                val frame = frames.single { it.requestId == mutation.mutationRequestId }
                assertIs<PulseSplitInput.Mutation<Mutation>>(frame.input)
                assertEquals(PulseMutationStatus.CHANGED, mutation.outcome)
            }
            assertEquals(State(4), viewModel.state.value)
            assertFalse(events.toString().contains("private-"))
            assertEquals(events.map { it.sequenceId }.sorted(), events.map { it.sequenceId })
            viewModel.close()
            viewModel.awaitClosed()
        }

    @Test
    fun `slow diagnostics collector cannot backpressure Store work and sequence gaps expose loss`() =
        runTest(main.dispatcher) {
            val events = mutableListOf<PulseSplitDiagnosticEvent>()
            val release = CompletableDeferred<Unit>()
            val viewModel = PulseSplitStoreViewModel<State, Intent, Mutation, Effect>(
                initialState = State(), mutationReducer = reducer,
                uiIntentExecutor = PulseUiIntentExecutor { _, context ->
                    context.mutateResult(Mutation("private"))
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config(2),
                executionOwner = PulseAndroidExecutionOwner.from(backgroundScope),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.diagnostics.collect {
                    events += it
                    if (events.size == 1) release.await()
                }
            }
            repeat(100) { viewModel.send(Intent(it, "private")) }
            assertEquals(State(100), viewModel.state.value)
            assertEquals(1, events.size)
            release.complete(Unit)
            runCurrent()
            assertEquals(3, events.size)
            assertTrue(events[1].sequenceId > events[0].sequenceId + 1)
            assertEquals(200, events.last().sequenceId)
            viewModel.close()
            viewModel.awaitClosed()
        }

    @Test
    fun `close reports cancellation for active and queued executors`() = runTest(main.dispatcher) {
        val events = mutableListOf<PulseSplitDiagnosticEvent>()
        val viewModel = PulseSplitStoreViewModel<State, Intent, Mutation, Effect>(
            initialState = State(), mutationReducer = reducer,
            uiIntentExecutor = PulseUiIntentExecutor { _, _ -> awaitCancellation() },
            runtimeConfig = config(2),
            executionOwner = PulseAndroidExecutionOwner.from(backgroundScope),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.diagnostics.collect { events += it }
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { viewModel.send(Intent(1, "private")) }
        runCurrent()
        val second = async(start = CoroutineStart.UNDISPATCHED) { viewModel.send(Intent(2, "private")) }
        runCurrent()
        viewModel.close()
        viewModel.awaitClosed()
        assertEquals(PulseIntentExecutionResult.Cancelled, first.await())
        assertEquals(PulseIntentExecutionResult.Cancelled, second.await())
        val executions = events.filterIsInstance<PulseSplitDiagnosticEvent.ExecutionCompleted>()
        assertEquals(2, executions.size)
        assertTrue(executions.all { it.outcome == PulseIntentExecutionStatus.CANCELLED })
        assertEquals(1, executions.count { it.executorStartedAtNanos == null })
    }

    private fun config(capacity: Int, clock: PulseClock = PulseClock { 0 }) = PulseRuntimeConfig(
        mailboxCapacity = capacity, storeDispatcher = main.dispatcher,
        consumerDispatcher = main.dispatcher, clock = clock, storeId = "diagnostic-fixture",
    )
    private data class State(val count: Int = 0) : MviState
    private data class Intent(val number: Int, val payload: String) : MviUiIntent
    private data class Mutation(val payload: String) : MviMutation
    private sealed interface Effect : UiEffect
    private val reducer = PulseMutationReducer<State, Mutation, Effect> { previous, _ ->
        ReduceOutcome.Changed(State(previous.count + 1))
    }
}
