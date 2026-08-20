package com.magic.mvicore.contract

/** Pure state transition used by the ordered Pulse runtime. */
fun interface PulseReducer<S : MviState, I : MviIntent, E : UiEffect> {
    fun reduce(previous: S, input: I): ReduceOutcome<S, E>
}
