package com.magic.mvicore.android

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Handles side effects triggered by incoming intents.
 *
 * PulseViewModel-based classes call this only for intents accepted by reducer dispatch.
 */
fun interface IntentExecutor<S : MviState, I : MviIntent, E : MviEffect> {
    fun execute(intent: I, scope: IntentExecutionScope<S, I, E>)

    companion object {
        fun <S : MviState, I : MviIntent, E : MviEffect> noop(): IntentExecutor<S, I, E> =
            IntentExecutor { _, _ -> }
    }
}

/**
 * Execution scope for intent side-effect handling.
 *
 * Dispatch here goes directly to Store and does not trigger IntentExecutor recursively.
 */
class IntentExecutionScope<S : MviState, I : MviIntent, E : MviEffect> internal constructor(
    private val store: Store<S, I, E>,
    private val coroutineScope: CoroutineScope,
) {
    val currentState: S
        get() = store.currentState

    fun dispatch(intent: I): DispatchResult = store.dispatch(intent)

    fun launch(block: suspend IntentExecutionScope<S, I, E>.() -> Unit): Job {
        return coroutineScope.launch {
            block()
        }
    }
}
