package com.magic.mvicore.testing

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseStore
import com.magic.mvicore.runtime.PulseRedactor
import com.magic.mvicore.runtime.PulseTasks
import com.magic.mvicore.runtime.UiEffectStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Probe-enabled wrapper around a Pulse store created by [PulseTestScope].
 *
 * Internal collector jobs and scopes are intentionally not part of this public surface.
 */
class TestPulseStore<S : MviState, I : MviIntent, E : UiEffect> internal constructor(
    private val delegate: PulseStore<S, I, E>,
    collectorScope: CoroutineScope,
    val failureProbe: FailureProbe,
    redactor: PulseRedactor,
) : PulseStore<S, I, E> {
    val transitionProbe: TransitionProbe<S, I, E> = TransitionProbe()
    val stateProbe: StateProbe<S> = StateProbe(redactor) {
        transitionProbe.snapshot().lastOrNull()?.sequenceId
    }
    val effectProbe: EffectProbe<E> = EffectProbe(redactor)

    private val stopped = AtomicBoolean(false)
    private val stateCollector: Job = collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
        delegate.state.collect(stateProbe::record)
    }
    private val transitionCollector: Job = collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
        delegate.transitions.collect(transitionProbe::record)
    }
    private val effectCollector: Job = collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
        delegate.effects.collect(effectProbe::record)
    }

    override val state: StateFlow<S> = delegate.state
    override val transitions: Flow<TransitionFrame<S, I, E>> = delegate.transitions
    override val effects: UiEffectStream<E> = delegate.effects
    override val tasks: PulseTasks = delegate.tasks

    override suspend fun send(input: I): TransitionResult<S, I, E> = delegate.send(input)

    override fun trySend(input: I): EnqueueResult = delegate.trySend(input)

    override fun close() = delegate.close()

    override suspend fun awaitClosed() {
        delegate.awaitClosed()
        stopCollectors()
    }

    suspend fun closeAndAwait() {
        close()
        awaitClosed()
    }

    private suspend fun stopCollectors() {
        if (!stopped.compareAndSet(false, true)) return
        stateCollector.cancelAndJoin()
        transitionCollector.cancelAndJoin()
        effectCollector.cancelAndJoin()
    }
}
