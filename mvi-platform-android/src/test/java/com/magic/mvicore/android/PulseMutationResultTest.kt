package com.magic.mvicore.android

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseMutationResultTest {
    @get:Rule internal val main = MainDispatcherRule()

    @Test
    fun `detailed results distinguish committed ignored failed and closed while preserving Boolean callers`() =
        runTest(main.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val reducerFailure = IllegalArgumentException("fixture")
            val results = mutableListOf<PulseMutationResult>()
            lateinit var retainedContext: PulseIntentContext<State, Mutation>
            val viewModel = PulseSplitStoreViewModel<State, Intent, Mutation, Effect>(
                initialState = State(),
                mutationReducer = PulseMutationReducer { previous, mutation ->
                    when (mutation) {
                        Mutation.Change -> ReduceOutcome.Changed(State(previous.count + 1))
                        Mutation.Keep -> ReduceOutcome.Unchanged()
                        Mutation.Ignore -> ReduceOutcome.Ignored("business-rule")
                        Mutation.ReservedReason -> ReduceOutcome.Ignored("late-task-mutation")
                        Mutation.Fail -> throw reducerFailure
                    }
                },
                uiIntentExecutor = PulseUiIntentExecutor { _, context ->
                    retainedContext = context
                    Mutation.entries.forEach { results += context.mutateResult(it) }
                    assertTrue(context.mutate(Mutation.Ignore))
                    assertTrue(context.mutate(Mutation.ReservedReason))
                    PulseIntentExecutionDecision.Completed
                },
                runtimeConfig = config(failures),
                executionOwner = PulseAndroidExecutionOwner.from(backgroundScope),
            )
            viewModel.send(Intent.First)
            assertEquals(PulseMutationResult.Changed, results[0])
            assertEquals(PulseMutationResult.Unchanged, results[1])
            assertEquals(PulseMutationResult.Ignored("business-rule"), results[2])
            assertEquals(PulseMutationResult.Ignored("late-task-mutation"), results[3])
            assertSame(reducerFailure, assertIs<PulseMutationResult.Failed>(results[4]).failure.cause)
            assertEquals(State(1), viewModel.state.value)
            assertIs<PulseFailure.ReducerFailure>(failures.single())

            viewModel.close()
            viewModel.awaitClosed()
            assertEquals(
                PulseMutationResult.Rejected(RejectionReason.Closing),
                retainedContext.mutateResult(Mutation.Change),
            )
            assertFalse(retainedContext.mutate(Mutation.Change))
        }

    @Test
    fun `replaced task retains its origin and cannot commit a late mutation`() = runTest(main.dispatcher) {
        val failures = mutableListOf<PulseFailure>()
        val oldContext = CompletableDeferred<PulseTaskContext<State, Mutation>>()
        val newContext = CompletableDeferred<PulseTaskContext<State, Mutation>>()
        val intentIds = mutableListOf<Long>()
        val viewModel = PulseSplitStoreViewModel<State, Intent, Mutation, Effect>(
            initialState = State(),
            mutationReducer = PulseMutationReducer { previous, _ -> ReduceOutcome.Changed(State(previous.count + 1)) },
            uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                intentIds += context.intentId
                context.launchTask(TaskKey("replacement"), TaskPolicy.Latest) {
                    if (intent == Intent.First) oldContext.complete(this) else newContext.complete(this)
                    awaitCancellation()
                }
                PulseIntentExecutionDecision.Completed
            },
            runtimeConfig = config(failures),
            executionOwner = PulseAndroidExecutionOwner.from(backgroundScope),
        )
        viewModel.send(Intent.First)
        runCurrent()
        viewModel.send(Intent.Replace)
        runCurrent()
        assertEquals(intentIds[0], oldContext.await().originIntentId)
        assertEquals(intentIds[1], newContext.await().originIntentId)
        assertEquals(PulseMutationResult.StaleTask, oldContext.await().mutateResult(Mutation.Change))
        assertFalse(oldContext.await().mutate(Mutation.Change))
        assertEquals(PulseMutationResult.Changed, newContext.await().mutateResult(Mutation.Change))
        assertEquals(State(1), viewModel.state.value)
        assertTrue(failures.all { it is PulseFailure.LateMutation })
        viewModel.close()
        viewModel.awaitClosed()
    }

    private fun config(failures: MutableList<PulseFailure>) = PulseRuntimeConfig(
        storeDispatcher = main.dispatcher,
        consumerDispatcher = main.dispatcher,
        errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
    )

    private data class State(val count: Int = 0) : MviState
    private enum class Intent : MviUiIntent { First, Replace }
    private enum class Mutation : MviMutation { Change, Keep, Ignore, ReservedReason, Fail }
    private sealed interface Effect : UiEffect
}
