package com.magic.mvicore.contract

/**
 * Pure state transition function:
 * previous State + Intent -> next State (+ optional Effect)
 */
fun interface Reducer<S : MviState, I : MviIntent, E : MviEffect> {
    fun reduce(previous: S, intent: I): Next<S, E>
}
