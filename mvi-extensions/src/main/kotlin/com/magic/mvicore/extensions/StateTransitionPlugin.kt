package com.magic.mvicore.extensions

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.runtime.StorePlugin

data class StateTransition<S : MviState, I : MviIntent>(
    val previous: S,
    val intent: I,
    val next: S,
)

/**
 * Emits complete state transition tuples:
 * previous state + intent + next state.
 */
class StateTransitionPlugin<S : MviState, I : MviIntent, E : MviEffect>(
    private val onTransition: (StateTransition<S, I>) -> Unit,
) : StorePlugin<S, I, E> {

    private var pending: Pending<S, I>? = null

    override fun onIntent(intent: I, stateBeforeReduce: S) {
        pending = Pending(previous = stateBeforeReduce, intent = intent)
    }

    override fun onState(state: S) {
        val current = pending ?: return
        pending = null
        onTransition(StateTransition(previous = current.previous, intent = current.intent, next = state))
    }

    override fun onRejected(result: DispatchResult.Rejected) {
        pending = null
    }

    override fun onError(error: StoreError.ReducerFailure) {
        pending = null
    }

    override fun onClose(lastState: S) {
        pending = null
    }

    private data class Pending<S : MviState, I : MviIntent>(
        val previous: S,
        val intent: I,
    )
}
