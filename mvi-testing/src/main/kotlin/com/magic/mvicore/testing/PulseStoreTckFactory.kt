package com.magic.mvicore.testing

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.PulseStore
import com.magic.mvicore.runtime.PulseStorePlugin

/** Factory boundary used to run the same Pulse store contract against another implementation. */
interface PulseStoreTckFactory {
    fun <S : MviState, I : MviIntent, E : UiEffect> create(
        initialState: S,
        reducer: PulseReducer<S, I, E>,
        config: TestRuntimeConfig,
        plugins: List<PulseStorePlugin<S, I, E>> = emptyList(),
    ): PulseStore<S, I, E>
}

/** TCK factory for the production [DefaultPulseStore]. */
object DefaultPulseStoreTckFactory : PulseStoreTckFactory {
    override fun <S : MviState, I : MviIntent, E : UiEffect> create(
        initialState: S,
        reducer: PulseReducer<S, I, E>,
        config: TestRuntimeConfig,
        plugins: List<PulseStorePlugin<S, I, E>>,
    ): PulseStore<S, I, E> {
        return DefaultPulseStore(
            initialState = initialState,
            reducer = reducer,
            config = config.toPulseRuntimeConfig(),
            plugins = plugins,
        )
    }
}
