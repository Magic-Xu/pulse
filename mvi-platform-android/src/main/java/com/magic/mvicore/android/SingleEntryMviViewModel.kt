package com.magic.mvicore.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.Store
import com.magic.mvicore.contract.Subscription
import com.magic.mvicore.runtime.DefaultStore
import com.magic.mvicore.runtime.StorePlugin

/**
 * Single-entry MVI ViewModel:
 * - final class (no subclass API expansion)
 * - only exposes dispatch as intent entry
 * - side effects can be handled via IntentExecutor
 */
class SingleEntryMviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    reducer: Reducer<S, I, E>,
    private val intentExecutor: IntentExecutor<S, I, E> = IntentExecutor.noop(),
    plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : ViewModel(), Store<S, I, E> {

    private val store = DefaultStore(
        initialState = initialState,
        reducer = reducer,
        plugins = plugins,
        autoStart = autoStart,
    )

    private val executionScope = IntentExecutionScope(
        store = store,
        coroutineScope = viewModelScope,
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

    override fun dispatch(intent: I): DispatchResult {
        val result = store.dispatch(intent)
        if (result is DispatchResult.Accepted) {
            runCatching {
                intentExecutor.execute(intent, executionScope)
            }
        }
        return result
    }

    override fun observeState(observer: (S) -> Unit): Subscription = store.observeState(observer)

    override fun observeEffect(observer: (E) -> Unit): Subscription = store.observeEffect(observer)

    override fun onCleared() {
        store.close()
    }
}
