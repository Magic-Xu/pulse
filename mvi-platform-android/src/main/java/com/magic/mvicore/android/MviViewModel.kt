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

/**
 * Android adapter for Store lifecycle.
 * - Uses ViewModel for ownership.
 * - Delegates MVI runtime work to DefaultStore.
 */
open class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    reducer: Reducer<S, I, E>,
    plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : ViewModel(), Store<S, I, E> {

    private val store = DefaultStore(
        initialState = initialState,
        reducer = reducer,
        plugins = plugins,
        autoStart = autoStart,
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

    override fun dispatch(intent: I): DispatchResult = store.dispatch(intent)

    override fun observeState(observer: (S) -> Unit): Subscription = store.observeState(observer)

    override fun observeEffect(observer: (E) -> Unit): Subscription = store.observeEffect(observer)

    override fun onCleared() {
        store.close()
    }
}
