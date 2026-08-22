package com.magic.mvicore.runtime

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.Subscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes delivery to the callback-based v0.2 compatibility surface. */
internal class LegacyCallbackHub<S : MviState, E : MviEffect>(
    dispatcher: CoroutineDispatcher,
    private val failureReporter: (PulseFailure) -> Unit,
    storeId: String,
) : AutoCloseable {
    private val admissionLock = Any()
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val stateCallbacks = mutableListOf<StateCallback<S>>()
    private val effectCallbacks = mutableListOf<Callback<E>>()

    private var accepting = true
    private var latestState: SequencedState<S>? = null
    private var latestPublishedSequence: Long? = null
    private val delivering = ThreadLocal<Boolean>()
    private val deliveryCutoff = AtomicBoolean(false)

    private val consumer: Deferred<Unit>

    init {
        require(storeId.isNotBlank()) { "storeId must not be blank." }

        consumer = CoroutineScope(
            dispatcher + CoroutineName("$storeId-legacy-callbacks")
        ).async(start = CoroutineStart.UNDISPATCHED) {
            var drainedNormally = false
            try {
                for (command in commands) {
                    delivering.set(true)
                    try {
                        command.run()
                    } finally {
                        delivering.remove()
                    }
                }
                drainedNormally = true
            } finally {
                stopAccepting()
                if (!drainedNormally) commands.cancel()
                stateCallbacks.forEach(StateCallback<S>::cancel)
                effectCallbacks.forEach(Callback<E>::cancel)
                stateCallbacks.clear()
                effectCallbacks.clear()
            }
        }
    }

    /**
     * Registers a state callback and schedules exactly one non-regressing initial snapshot.
     *
     * The supplied snapshot may race with a committed update already accepted by this hub. In
     * that case the newer hub-owned snapshot wins.
     */
    fun registerState(
        snapshotSequence: Long,
        state: S,
        callback: (S) -> Unit,
    ): Subscription {
        val registered = StateCallback(callback)
        val accepted = offer(
            Command {
                if (deliveryCutoff.get() || !registered.isActive()) return@Command

                val supplied = SequencedState(snapshotSequence, state)
                val initial = latestState
                    ?.takeIf { it.sequence >= snapshotSequence }
                    ?: supplied.also { latestState = it }

                stateCallbacks += registered
                deliverState(registered, initial)
            }
        )

        if (!accepted) registered.cancel()
        return Subscription {
            registered.cancel()
            offer(Command { stateCallbacks.remove(registered) })
        }
    }

    /** Publishes a committed state frame; unchanged frames only advance the latest snapshot. */
    fun publishState(
        sequence: Long,
        state: S,
        stateChanged: Boolean,
    ) {
        offer(
            Command {
                val publishedSequence = latestPublishedSequence
                if (publishedSequence != null && sequence <= publishedSequence) return@Command

                latestPublishedSequence = sequence
                val published = SequencedState(sequence, state)
                val currentLatest = latestState
                if (currentLatest == null || sequence >= currentLatest.sequence) {
                    latestState = published
                }

                if (!stateChanged || deliveryCutoff.get()) return@Command

                stateCallbacks.removeAll { !it.isActive() }
                stateCallbacks.forEach { deliverState(it, published) }
            }
        )
    }

    fun registerEffect(callback: (E) -> Unit): Subscription {
        val registered = Callback(callback)
        val accepted = offer(
            Command {
                if (!deliveryCutoff.get() && registered.isActive()) effectCallbacks += registered
            }
        )

        if (!accepted) registered.cancel()
        return Subscription {
            registered.cancel()
            offer(Command { effectCallbacks.remove(registered) })
        }
    }

    /** Broadcasts an effect to every callback active at its serialized delivery point. */
    fun publishEffect(
        sequence: Long,
        effect: E,
    ) {
        offer(
            Command {
                if (deliveryCutoff.get()) return@Command
                effectCallbacks.removeAll { !it.isActive() }
                effectCallbacks.forEach { registered ->
                    try {
                        registered.deliver(effect)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        failureReporter(
                            PulseFailure.UiEffectConsumerFailure(
                                context = FailureContext(
                                    sequenceId = sequence,
                                    component = EFFECT_COMPONENT,
                                ),
                                cause = failure,
                            )
                        )
                    }
                }
            }
        )
    }

    /** Queues compatibility work in the same serial order as state and effect callbacks. */
    fun publishAction(action: () -> Unit) {
        offer(Command { if (!deliveryCutoff.get()) action() })
    }

    /** Queues the final lifecycle callback even after ordinary deliveries have been cut off. */
    fun publishTerminalAction(action: () -> Unit) {
        offer(Command(action))
    }

    /**
     * Prevents any new callback from beginning after this method returns.
     *
     * Call it either from this hub's delivery thread or after [flush] to avoid racing list edits.
     */
    fun cutoffDeliveries() {
        if (!deliveryCutoff.compareAndSet(false, true)) return
        stateCallbacks.forEach(StateCallback<S>::cancel)
        effectCallbacks.forEach(Callback<E>::cancel)
    }

    /** Waits until all work accepted before this barrier has completed. */
    suspend fun flush() {
        if (isDeliveringOnCurrentThread()) return
        val completion = CompletableDeferred<Unit>()
        if (!offer(Command { completion.complete(Unit) })) {
            consumer.await()
            return
        }

        select<Unit> {
            completion.onAwait { Unit }
            consumer.onAwait { Unit }
        }
    }

    /** True only while the current thread is executing one of this hub's callbacks. */
    fun isDeliveringOnCurrentThread(): Boolean = delivering.get() == true

    /** Establishes the admission cutoff. Commands accepted before it are drained in order. */
    override fun close() {
        synchronized(admissionLock) {
            if (!accepting) return
            accepting = false
            commands.close()
        }
    }

    /** Waits for accepted commands to drain and callback references to be cleared. */
    suspend fun awaitClosed() {
        consumer.await()
    }

    private fun deliverState(
        registered: StateCallback<S>,
        state: SequencedState<S>,
    ) {
        try {
            registered.deliver(state.sequence, state.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter(
                PulseFailure.StateConsumerFailure(
                    context = FailureContext(
                        sequenceId = state.sequence,
                        component = STATE_COMPONENT,
                    ),
                    cause = failure,
                )
            )
        }
    }

    private fun offer(command: Command): Boolean {
        return synchronized(admissionLock) {
            accepting && commands.trySend(command).isSuccess
        }
    }

    private fun stopAccepting() {
        synchronized(admissionLock) {
            accepting = false
            commands.close()
        }
    }

    private fun interface Command {
        fun run()
    }

    /**
     * The monitor linearizes cancellation with callback start. A cancelling caller waits for an
     * already-running callback, and once cancel returns no later callback can begin.
     */
    private open class Callback<T>(
        private val callback: (T) -> Unit,
    ) {
        private val lock = Any()
        private var active = true

        fun isActive(): Boolean = synchronized(lock) { active }

        open fun deliver(value: T) {
            synchronized(lock) {
                if (active) callback(value)
            }
        }

        fun cancel() {
            synchronized(lock) {
                active = false
            }
        }
    }

    private class StateCallback<S : MviState>(
        callback: (S) -> Unit,
    ) : Callback<S>(callback) {
        private var lastDeliveredSequence: Long? = null

        fun deliver(
            sequence: Long,
            state: S,
        ) {
            val previous = lastDeliveredSequence
            if (previous != null && sequence <= previous) return
            lastDeliveredSequence = sequence
            deliver(state)
        }
    }

    private data class SequencedState<S : MviState>(
        val sequence: Long,
        val value: S,
    )

    private companion object {
        const val STATE_COMPONENT = "legacy-state-callback"
        const val EFFECT_COMPONENT = "legacy-effect-callback"
    }
}
