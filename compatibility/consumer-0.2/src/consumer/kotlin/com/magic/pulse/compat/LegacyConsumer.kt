package com.magic.pulse.compat

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.runtime.DefaultStore

fun main() {
    val states = mutableListOf<LegacyState>()
    val effects = mutableListOf<LegacyEffect>()
    val store = DefaultStore(
        initialState = LegacyState(0),
        reducer = LegacyReducer,
    )
    val stateSubscription = store.observeState(states::add)
    val effectSubscription = store.observeEffect(effects::add)

    check(store.dispatch(LegacyIntent.Increment) == DispatchResult.Accepted)
    check(store.dispatch(LegacyIntent.Reset) == DispatchResult.Accepted)
    check(store.currentState == LegacyState(0))
    check(states == listOf(LegacyState(0), LegacyState(1), LegacyState(0)))
    check(effects == listOf(LegacyEffect.ResetDone))

    stateSubscription.cancel()
    effectSubscription.cancel()
    store.close()
    check(store.isClosed)
}

private data class LegacyState(val value: Int) : MviState

private sealed interface LegacyIntent : MviIntent {
    data object Increment : LegacyIntent
    data object Reset : LegacyIntent
}

private sealed interface LegacyEffect : MviEffect {
    data object ResetDone : LegacyEffect
}

private object LegacyReducer : Reducer<LegacyState, LegacyIntent, LegacyEffect> {
    override fun reduce(
        previous: LegacyState,
        intent: LegacyIntent,
    ): Next<LegacyState, LegacyEffect> {
        return when (intent) {
            LegacyIntent.Increment -> Next.just(previous.copy(value = previous.value + 1))
            LegacyIntent.Reset -> Next.withEffect(LegacyState(0), LegacyEffect.ResetDone)
        }
    }
}
