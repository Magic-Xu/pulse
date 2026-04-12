package com.magic.mvicore.runtime

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.StoreError

/**
 * Runtime extension point.
 * Keep core dispatch/reduce logic stable, move non-essential concerns here.
 */
interface StorePlugin<S : MviState, I : MviIntent, E : MviEffect> {
    fun onStart(initialState: S) {}

    fun onStop(lastState: S) {}

    fun onClose(lastState: S) {}

    fun onIntent(intent: I, stateBeforeReduce: S) {}

    fun onState(state: S) {}

    fun onEffect(effect: E) {}

    fun onRejected(result: DispatchResult.Rejected) {}

    fun onError(error: StoreError.ReducerFailure) {}
}
