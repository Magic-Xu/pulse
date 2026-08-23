package com.magic.pulse.compat.v03

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.extensions.pulseMutationReducer
import com.magic.mvicore.extensions.selectDistinct
import com.magic.mvicore.extensions.stateLens
import com.magic.mvicore.extensions.subStateWithEffect
import com.magic.mvicore.extensions.updateSubState
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.MailboxOverflowPolicy
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.PulseStore
import com.magic.mvicore.runtime.PulseStorePlugin
import com.magic.mvicore.testing.FailureProbe
import com.magic.mvicore.testing.runPulseTest
import kotlinx.coroutines.flow.Flow

/** Frozen 0.3 consumer compiled and executed against both public baseline and staged candidate. */
fun main() = runPulseTest {
    val failureProbe = FailureProbe()
    val config = runtimeConfig(
        mailboxCapacity = 8,
        overflowPolicy = MailboxOverflowPolicy.REJECT_AND_REPORT,
        effectBufferCapacity = 4,
        failureProbe = failureProbe,
        storeId = "compat-v03",
    )
    val store = testStore(
        initialState = LegacyState(0),
        reducer = LegacyReducer,
        config = config,
        plugins = listOf(LegacyPlugin),
    )

    val result = store.send(LegacyIntent.Increment)
    check(result is TransitionResult.Completed)
    check(result.frame.outcome == TransitionOutcome.Changed)
    check(result.frame.stateAfter == LegacyState(1))
    runCurrent()

    store.stateProbe.assertValues(LegacyState(0), LegacyState(1))
    store.transitionProbe.assertSequence(1L)
    store.transitionProbe.assertOutcomes(TransitionOutcome.Changed)
    store.effectProbe.assertPayloads(LegacyEffect.Incremented)
    failureProbe.assertEmpty()

    val selected: Flow<Int> = store.state.selectDistinct(LegacyState::value)
    check(selected !== store.state)
    check(
        legacyMutationResult() == ReduceOutcome.Changed<LegacyState, LegacyEffect>(
            state = LegacyState(1),
            uiEffects = listOf(LegacyEffect.Incremented),
        )
    )
}

/** Keeps constructor names and the complete 0.3 runtime contract in the source gate. */
fun legacyRuntimeStore(config: PulseRuntimeConfig = PulseRuntimeConfig()): PulseStore<
    LegacyState,
    LegacyIntent,
    LegacyEffect,
> = DefaultPulseStore(
    initialState = LegacyState(0),
    reducer = LegacyReducer,
    config = config,
    plugins = listOf(LegacyPlugin),
)

private val valueLens = stateLens<LegacyState, Int>(
    get = LegacyState::value,
    set = { state, value -> state.copy(value = value) },
)

private val LegacyMutationReducer: PulseMutationReducer<
    LegacyState,
    LegacyMutation,
    LegacyEffect,
> = pulseMutationReducer {
    onSub<Int, LegacyMutation.Incremented>(valueLens) { value, _ ->
        subStateWithEffect(value + 1, LegacyEffect.Incremented)
    }
}

private fun legacyMutationResult(): ReduceOutcome<LegacyState, LegacyEffect> {
    val updated = LegacyState(0).updateSubState(valueLens) { it + 1 }
    check(updated == LegacyState(1))
    return LegacyMutationReducer.reduce(LegacyState(0), LegacyMutation.Incremented)
}

data class LegacyState(val value: Int) : MviState

sealed interface LegacyIntent : MviIntent {
    data object Increment : LegacyIntent
}

sealed interface LegacyMutation : MviMutation {
    data object Incremented : LegacyMutation
}

sealed interface LegacyEffect : UiEffect {
    data object Incremented : LegacyEffect
}

private val LegacyReducer = PulseReducer<LegacyState, LegacyIntent, LegacyEffect> { previous, input ->
    when (input) {
        LegacyIntent.Increment -> ReduceOutcome.Changed(
            state = previous.copy(value = previous.value + 1),
            uiEffects = listOf(LegacyEffect.Incremented),
        )
    }
}

private object LegacyPlugin : PulseStorePlugin<LegacyState, LegacyIntent, LegacyEffect> {
    override val pluginId: String = "compat-v03-plugin"
}
