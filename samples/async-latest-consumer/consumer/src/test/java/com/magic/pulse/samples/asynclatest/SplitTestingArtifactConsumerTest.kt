package com.magic.pulse.samples.asynclatest

import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseIntentExecutionResult
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.android.testing.runPulseSplitTest
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTestingArtifactConsumerTest {
    @Test
    fun androidSplitTestingArtifactIsConsumable() = runPulseSplitTest {
        val host = splitHost(
            initialState = ConsumerState(0),
            mutationReducer = PulseMutationReducer<
                ConsumerState,
                ConsumerMutation,
                ConsumerEffect
            > { state, mutation ->
                when (mutation) {
                    ConsumerMutation.Increment -> ReduceOutcome.Changed(
                        state.copy(value = state.value + 1)
                    )
                }
            },
            uiIntentExecutor = PulseUiIntentExecutor { _, context ->
                context.mutate(ConsumerMutation.Increment)
                PulseIntentExecutionDecision.Completed
            },
        )

        assertEquals(
            PulseIntentExecutionResult.Completed,
            host.sendAndDrain(ConsumerIntent.Increment),
        )
        assertEquals(ConsumerState(1), host.stateProbe.latest())
        assertEquals(2, host.transitionProbe.snapshot().size)
        host.failureProbe.assertEmpty()
    }

    private data class ConsumerState(val value: Int) : MviState

    private sealed interface ConsumerIntent : MviUiIntent {
        data object Increment : ConsumerIntent
    }

    private sealed interface ConsumerMutation : MviMutation {
        data object Increment : ConsumerMutation
    }

    private sealed interface ConsumerEffect : UiEffect
}
