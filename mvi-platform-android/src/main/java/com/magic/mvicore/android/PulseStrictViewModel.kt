package com.magic.mvicore.android

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.runtime.StorePlugin

/**
 * Strict flavor of PulseViewModel:
 * - reducer remains the only state transition path
 * - accepted intents are delegated to IntentExecutor
 */
class PulseStrictViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    reducer: Reducer<S, I, E>,
    private val intentExecutor: IntentExecutor<S, I, E> = IntentExecutor.noop(),
    plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : PulseViewModel<S, I, E>(
    initialState = initialState,
    reducer = reducer,
    plugins = plugins,
    autoStart = autoStart,
) {

    override fun onIntentAccepted(
        intent: I,
        scope: IntentExecutionScope<S, I, E>,
    ) {
        runCatching {
            intentExecutor.execute(intent, scope)
        }
    }
}
