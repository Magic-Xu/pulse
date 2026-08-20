package com.magic.mvicore.runtime

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException

/**
 * Delivers immutable plugin observations at the engine's ordered publication boundary.
 *
 * A separate fire-and-forget consumer cannot preserve fail-stop semantics: once that consumer
 * terminates, the input that caused the terminal plugin failure may already have completed and a
 * later close barrier can wait forever. Synchronous delivery keeps the failure attached to the
 * originating engine operation. The monitor only prevents failures reported from auxiliary task
 * contexts from invoking plugins concurrently with transition publication.
 */
internal class PluginDeliveryHub<S : MviState, I : MviIntent, E : UiEffect>(
    private val plugins: List<PulseStorePlugin<S, I, E>>,
    private val config: PulseRuntimeConfig,
) {
    private val deliveryLock = Any()
    private var closed = false

    fun publishTransition(frame: TransitionFrame<S, I, E>) {
        synchronized(deliveryLock) {
            if (closed) return
            deliverTransition(frame)
        }
    }

    fun publishFailure(failure: PulseFailure) {
        synchronized(deliveryLock) {
            if (closed) return
            deliverFailure(failure)
        }
    }

    suspend fun closeAndAwait() {
        synchronized(deliveryLock) {
            closed = true
        }
    }

    private fun deliverTransition(frame: TransitionFrame<S, I, E>) {
        plugins.forEach { plugin ->
            guardPlugin(plugin.pluginId, frame) {
                plugin.onTransition(frame)
            }
        }
    }

    private fun deliverFailure(failure: PulseFailure) {
        plugins.forEach { plugin ->
            guardPlugin(plugin.pluginId, frame = null) {
                plugin.onFailure(failure)
            }
        }
    }

    private inline fun guardPlugin(
        pluginId: String,
        frame: TransitionFrame<S, I, E>?,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            config.reportFailure(
                PulseFailure.PluginFailure(
                    context = FailureContext(
                        requestId = frame?.requestId,
                        sequenceId = frame?.sequenceId,
                        stateRevision = frame?.stateRevision,
                        component = pluginId,
                    ),
                    cause = failure,
                )
            )
        }
    }

}
