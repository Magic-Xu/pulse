package com.magic.mvicore.contract

/** Pure mutation-only reducer used by the v0.3 Split Intent runtime. */
fun interface PulseMutationReducer<S : MviState, M : MviMutation, E : UiEffect> {
    fun reduce(previous: S, mutation: M): ReduceOutcome<S, E>
}
