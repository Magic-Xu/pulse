package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Read-only, replay-zero UI-effect delivery surface. */
fun interface UiEffectStream<E : UiEffect> {
    suspend fun collect(consumer: suspend (EffectEnvelope<E>) -> Unit)
}

/**
 * Runtime-owned UI-effect stream with exactly one active coordinator.
 *
 * Producers and session lifecycle are internal so callers cannot obtain the backing channel or
 * runtime scope.
 */
internal class SingleCoordinatorUiEffectStream<E : UiEffect>(
    private val config: PulseRuntimeConfig,
    private val failureReporter: (PulseFailure) -> Unit = config::reportFailure,
) : UiEffectStream<E> {
    private val sessionMutex = Mutex()

    private var activeSession: Session<E>? = null
    private var closed: Boolean = false

    internal suspend fun emit(envelope: EffectEnvelope<E>) {
        val session = sessionMutex.withLock { activeSession }
        if (session == null) {
            reportUndelivered(
                envelope = envelope,
                reason = if (closed) REASON_STREAM_CLOSED else REASON_NO_COORDINATOR,
            )
            return
        }

        try {
            session.channel.send(envelope)
        } catch (cancellation: CancellationException) {
            // Cancelling a lifecycle coordinator also cancels its session channel. That must drop
            // the undelivered envelope without cancelling the store processor that called emit.
            // A real processor cancellation still propagates through ensureActive().
            currentCoroutineContext().ensureActive()
            return
        } catch (_: ClosedSendChannelException) {
            // Channel.onUndeliveredElement reports the envelope with the session-ended reason.
        } catch (failure: Throwable) {
            // Channel wraps exceptions raised by onUndeliveredElement. Pulse preserves the
            // reporter's terminal cancellation/fatal failure instead of exposing that transport
            // implementation detail to the engine.
            throw failure.cause ?: failure
        }
    }

    override suspend fun collect(consumer: suspend (EffectEnvelope<E>) -> Unit) {
        val session = sessionMutex.withLock {
            check(!closed) { "UiEffectStream is closed." }
            val conflict = activeSession != null
            if (conflict) null else Session(createChannel()).also { activeSession = it }
        }
        if (session == null) {
            val rejection = IllegalStateException(
                "UiEffectStream already has an active coordinator."
            )
            withContext(config.consumerDispatcher) {
                failureReporter(
                    PulseFailure.UiEffectConsumerFailure(
                        context = FailureContext(component = COMPONENT),
                        cause = rejection,
                    )
                )
            }
            throw rejection
        }

        try {
            for (envelope in session.channel) {
                deliver(envelope, consumer)
            }
        } finally {
            withContext(NonCancellable + config.consumerDispatcher) {
                val ownedSession = sessionMutex.withLock {
                    if (activeSession === session) {
                        activeSession = null
                        true
                    } else {
                        false
                    }
                }
                if (ownedSession) {
                    cancelSession(session, "UiEffect coordinator session ended.")
                }
            }
        }
    }

    internal suspend fun close() {
        withContext(NonCancellable + config.consumerDispatcher) {
            val session = sessionMutex.withLock {
                if (closed) return@withLock null
                closed = true
                activeSession.also { activeSession = null }
            }
            if (session != null) {
                cancelSession(session, "UiEffectStream closed.")
            }
        }
    }

    private fun cancelSession(
        session: Session<E>,
        message: String,
    ) {
        try {
            session.channel.cancel(CancellationException(message))
        } catch (failure: Throwable) {
            // kotlinx.coroutines deliberately wraps undelivered-element callback exceptions.
            // Unwrap at the channel boundary so Pulse's terminal-failure contract stays stable.
            throw failure.cause ?: failure
        }
    }

    private fun createChannel(): Channel<EffectEnvelope<E>> {
        return Channel(
            capacity = config.effectBufferCapacity,
            onUndeliveredElement = { envelope ->
                reportUndelivered(envelope, REASON_SESSION_ENDED)
            },
        )
    }

    private suspend fun deliver(
        envelope: EffectEnvelope<E>,
        consumer: suspend (EffectEnvelope<E>) -> Unit,
    ) {
        withContext(config.consumerDispatcher) {
            try {
                consumer(envelope)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failureReporter(
                    PulseFailure.UiEffectConsumerFailure(
                        context = envelope.failureContext(),
                        cause = error,
                    )
                )
            }
        }
    }

    private fun reportUndelivered(
        envelope: EffectEnvelope<E>,
        reason: String,
    ) {
        failureReporter(
            PulseFailure.UndeliveredUiEffect(
                context = envelope.failureContext(),
                envelope = envelope,
                reason = reason,
            )
        )
    }

    private fun EffectEnvelope<E>.failureContext(): FailureContext {
        return FailureContext(
            requestId = requestId,
            sequenceId = sequenceId,
            stateRevision = stateRevision,
            component = COMPONENT,
        )
    }

    private data class Session<E : UiEffect>(
        val channel: Channel<EffectEnvelope<E>>,
    )

    private companion object {
        const val COMPONENT = "ui-effect-stream"
        const val REASON_NO_COORDINATOR = "no-active-coordinator"
        const val REASON_SESSION_ENDED = "coordinator-session-ended"
        const val REASON_STREAM_CLOSED = "stream-closed"
    }
}
