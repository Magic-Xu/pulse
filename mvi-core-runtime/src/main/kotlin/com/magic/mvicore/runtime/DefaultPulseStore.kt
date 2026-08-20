package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Default implementation of the ordered Pulse v0.3 store contract. */
class DefaultPulseStore<S : MviState, I : MviIntent, E : UiEffect>(
    initialState: S,
    reducer: PulseReducer<S, I, E>,
    config: PulseRuntimeConfig = PulseRuntimeConfig(),
    plugins: List<PulseStorePlugin<S, I, E>> = emptyList(),
) : PulseStore<S, I, E> {
    private val engine = PulseEngine(
        initialState = initialState,
        reducer = reducer,
        config = config,
        plugins = plugins,
        initiallyStarted = true,
    )

    override val state: StateFlow<S> = engine.state
    override val transitions: Flow<TransitionFrame<S, I, E>> = engine.transitions
    override val effects: UiEffectStream<E> = engine.effects
    override val tasks: PulseTasks = engine.tasks

    override suspend fun send(input: I): TransitionResult<S, I, E> = engine.send(input)

    override fun trySend(input: I): EnqueueResult = engine.trySend(input)

    override suspend fun awaitClosed() = engine.awaitClosed()

    override fun close() = engine.close()
}
