package com.magic.mvicore.runtime

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.StoreError

fun main() {
    dispatchShouldRespectLifecycle()
    storeShouldEmitStateAndEffect()
    pluginShouldReceiveCoreCallbacks()
}

private fun dispatchShouldRespectLifecycle() {
    val store = DefaultStore(
        initialState = CounterState(0),
        reducer = CounterReducer(),
        autoStart = false,
    )

    val notStarted = store.dispatch(CounterIntent.Increment)
    check(notStarted == DispatchResult.Rejected(StoreError.StoreNotStarted))

    store.start()
    check(store.dispatch(CounterIntent.Increment) == DispatchResult.Accepted)
    check(store.currentState == CounterState(1))

    store.stop()
    val stopped = store.dispatch(CounterIntent.Increment)
    check(stopped == DispatchResult.Rejected(StoreError.StoreNotStarted))

    store.close()
    val closed = store.dispatch(CounterIntent.Increment)
    check(closed == DispatchResult.Rejected(StoreError.StoreClosed))
}

private fun storeShouldEmitStateAndEffect() {
    val store = DefaultStore(
        initialState = CounterState(0),
        reducer = CounterReducer(),
    )
    val states = mutableListOf<CounterState>()
    val effects = mutableListOf<CounterEffect>()

    val stateSub = store.observeState(states::add)
    val effectSub = store.observeEffect(effects::add)

    store.dispatch(CounterIntent.Increment)
    store.dispatch(CounterIntent.Increment)
    store.dispatch(CounterIntent.Reset)

    stateSub.cancel()
    effectSub.cancel()

    check(states == listOf(CounterState(0), CounterState(1), CounterState(2), CounterState(0)))
    check(effects == listOf(CounterEffect.WasReset))
}

private fun pluginShouldReceiveCoreCallbacks() {
    val events = mutableListOf<String>()
    val plugin = object : StorePlugin<CounterState, CounterIntent, CounterEffect> {
        override fun onStart(initialState: CounterState) {
            events += "start:${initialState.count}"
        }

        override fun onIntent(intent: CounterIntent, stateBeforeReduce: CounterState) {
            events += "intent:${stateBeforeReduce.count}"
        }

        override fun onState(state: CounterState) {
            events += "state:${state.count}"
        }

        override fun onEffect(effect: CounterEffect) {
            events += "effect"
        }

        override fun onStop(lastState: CounterState) {
            events += "stop:${lastState.count}"
        }

        override fun onClose(lastState: CounterState) {
            events += "close:${lastState.count}"
        }
    }

    val store = DefaultStore(
        initialState = CounterState(0),
        reducer = CounterReducer(),
        plugins = listOf(plugin),
    )

    store.dispatch(CounterIntent.Increment)
    store.dispatch(CounterIntent.Reset)
    store.stop()
    store.close()

    check(events == listOf("start:0", "intent:0", "state:1", "intent:1", "state:0", "effect", "stop:0", "close:0"))
}

private data class CounterState(val count: Int) : MviState

private sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
    data object Reset : CounterIntent
}

private sealed interface CounterEffect : MviEffect {
    data object WasReset : CounterEffect
}

private class CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increment -> Next.just(previous.copy(count = previous.count + 1))
            CounterIntent.Reset -> Next.withEffect(CounterState(0), CounterEffect.WasReset)
        }
    }
}
