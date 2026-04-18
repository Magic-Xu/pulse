package com.magic.mvicore.android

import androidx.lifecycle.ViewModel
import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.Store
import com.magic.mvicore.contract.Subscription
import com.magic.mvicore.runtime.DefaultStore
import com.magic.mvicore.runtime.StorePlugin
import kotlinx.coroutines.cancel

/**
 * Open, inheritance-friendly Pulse ViewModel base.
 *
 * Child classes can add their own APIs, but state transition is still constrained
 * to reducer-driven dispatch.
 */
open class PulseViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    reducer: Reducer<S, I, E>,
    plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : ViewModel(), Store<S, I, E> {

    private val pulseScope = createPulseCoroutineScope()

    private val store = DefaultStore(
        initialState = initialState,
        reducer = reducer,
        plugins = plugins,
        autoStart = autoStart,
    )

    protected val intentExecutionScope = IntentExecutionScope(
        store = store,
        coroutineScope = pulseScope,
    )

    override val currentState: S
        get() = store.currentState

    override val isStarted: Boolean
        get() = store.isStarted

    override val isClosed: Boolean
        get() = store.isClosed

    override fun start() = store.start()

    override fun stop() = store.stop()

    override fun close() = store.close()

    final override fun dispatch(intent: I): DispatchResult {
        val result = store.dispatch(intent)
        if (result is DispatchResult.Accepted) {
            onIntentAccepted(intent, intentExecutionScope)
        }
        return result
    }

    override fun observeState(observer: (S) -> Unit): Subscription = store.observeState(observer)

    override fun observeEffect(observer: (E) -> Unit): Subscription = store.observeEffect(observer)

    /**
     * Extension hook for side effects after accepted dispatch.
     */
    protected open fun onIntentAccepted(
        intent: I,
        scope: IntentExecutionScope<S, I, E>,
    ) = Unit

    override fun onCleared() {
        pulseScope.cancel()
        store.close()
    }
}
