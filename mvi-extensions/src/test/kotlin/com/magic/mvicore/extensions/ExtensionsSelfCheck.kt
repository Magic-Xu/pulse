package com.magic.mvicore.extensions

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.runtime.DefaultStore

fun main() {
    loggingPluginShouldCaptureLifecycleAndDispatch()
    stateTransitionPluginShouldEmitTransitionTuples()
}

private fun loggingPluginShouldCaptureLifecycleAndDispatch() {
    val lines = mutableListOf<String>()
    val store = DefaultStore(
        initialState = CounterState(0),
        reducer = CounterReducer(),
        plugins = listOf(LoggingPlugin(tag = "TEST", sink = LogSink(lines::add))),
    )

    store.dispatch(CounterIntent.Increase)
    store.dispatch(CounterIntent.Reset)
    store.stop()
    store.close()

    check(lines == listOf(
        "[TEST] start state=CounterState(count=0)",
        "[TEST] intent=Increase stateBefore=CounterState(count=0)",
        "[TEST] state=CounterState(count=1)",
        "[TEST] intent=Reset stateBefore=CounterState(count=1)",
        "[TEST] state=CounterState(count=0)",
        "[TEST] effect=ResetDone",
        "[TEST] stop state=CounterState(count=0)",
        "[TEST] close state=CounterState(count=0)",
    ))
}

private fun stateTransitionPluginShouldEmitTransitionTuples() {
    val transitions = mutableListOf<StateTransition<CounterState, CounterIntent>>()
    val store = DefaultStore(
        initialState = CounterState(0),
        reducer = CounterReducer(),
        plugins = listOf(StateTransitionPlugin(onTransition = transitions::add)),
    )

    store.dispatch(CounterIntent.Increase)
    store.dispatch(CounterIntent.Increase)
    store.dispatch(CounterIntent.Reset)

    check(transitions == listOf(
        StateTransition(previous = CounterState(0), intent = CounterIntent.Increase, next = CounterState(1)),
        StateTransition(previous = CounterState(1), intent = CounterIntent.Increase, next = CounterState(2)),
        StateTransition(previous = CounterState(2), intent = CounterIntent.Reset, next = CounterState(0)),
    ))
}

private data class CounterState(val count: Int) : MviState

private sealed interface CounterIntent : MviIntent {
    data object Increase : CounterIntent
    data object Reset : CounterIntent
}

private sealed interface CounterEffect : MviEffect {
    data object ResetDone : CounterEffect
}

private class CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> Next.just(previous.copy(count = previous.count + 1))
            CounterIntent.Reset -> Next.withEffect(CounterState(0), CounterEffect.ResetDone)
        }
    }
}
