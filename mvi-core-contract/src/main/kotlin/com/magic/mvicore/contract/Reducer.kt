package com.magic.mvicore.contract

/**
 * Pure state transition function:
 * previous State + Intent -> next State (+ optional Effect)
 */
@Deprecated(
    message = "Use PulseReducer and return an explicit ReduceOutcome.",
    replaceWith = ReplaceWith("PulseReducer<S, I, E>"),
)
fun interface Reducer<S : MviState, I : MviIntent, E : MviEffect> {
    fun reduce(previous: S, intent: I): Next<S, E>
}
