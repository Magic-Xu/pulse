package com.magic.mvicore.testing

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TestingToolsTest {
    @Test
    fun `runtime config shares dispatcher clock and failure recorder`() = runPulseTest {
        val config = runtimeConfig(storeId = "testing-tools")
        val runtime = config.toPulseRuntimeConfig()

        assertSame(dispatcher, runtime.storeDispatcher)
        assertSame(dispatcher, runtime.consumerDispatcher)
        val first = runtime.clock.nanoTime()
        val second = runtime.clock.nanoTime()
        advanceTimeBy(3)
        val afterAdvance = runtime.clock.nanoTime()

        assertTrue(second > first)
        assertTrue(afterAdvance >= 3_000_000L)

        val failure = PulseFailure.MailboxOverflow(
            context = FailureContext(component = "test"),
            capacity = 1,
        )
        runtime.reportFailure(failure)
        assertSame(failure, config.failureProbe.awaitCount(1).single())
    }

    @Test
    fun `test store captures state transitions effects and failures`() = runPulseTest {
        val store = testStore(
            initialState = ToolState(0),
            reducer = PulseReducer<ToolState, ToolIntent, ToolEffect> { previous, input ->
                when (input) {
                    ToolIntent.Advance -> ReduceOutcome.Changed(
                        state = previous.copy(value = previous.value + 1),
                        uiEffects = listOf(ToolEffect.Notice),
                    )
                    ToolIntent.Fail -> throw IllegalArgumentException("expected")
                }
            },
        )

        store.send(ToolIntent.Advance)
        store.send(ToolIntent.Fail)

        store.stateProbe.assertValues(ToolState(0), ToolState(1))
        store.transitionProbe.assertSequence(1L, 2L)
        store.transitionProbe.assertOutcomes(
            TransitionOutcome.Changed,
            TransitionOutcome.ReducerFailed,
        )
        store.effectProbe.assertPayloads(ToolEffect.Notice)
        assertEquals(1, store.failureProbe.snapshot().filterIsInstance<PulseFailure.ReducerFailure>().size)
    }

    private data class ToolState(val value: Int) : MviState

    private sealed interface ToolIntent : MviIntent {
        data object Advance : ToolIntent
        data object Fail : ToolIntent
    }

    private sealed interface ToolEffect : UiEffect {
        data object Notice : ToolEffect
    }
}
