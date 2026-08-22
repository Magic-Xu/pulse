package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ordered Pulse v0.3 store surface.
 *
 * [send] completes after reduce, commit, transition publication, and effect publication. It does
 * not wait for external Flow collectors to finish handling an item.
 */
interface PulseStore<S : MviState, IN : MviIntent, E : UiEffect> : AutoCloseable {
    val state: StateFlow<S>

    val transitions: Flow<TransitionFrame<S, IN, E>>

    val effects: UiEffectStream<E>

    val tasks: PulseTasks

    suspend fun send(input: IN): TransitionResult<S, IN, E>

    fun trySend(input: IN): EnqueueResult

    /** Waits until every input admitted before [close] has reached the close cutoff. */
    suspend fun awaitClosed()

    /** Establishes the admission cutoff without blocking the calling thread. */
    override fun close()
}
