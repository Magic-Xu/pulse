package com.magic.mvicore.runtime

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.Store
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.contract.Subscription
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binary-compatible v0.2 facade over the ordered v0.3 engine.
 *
 * Reducer execution, lifecycle cutoff, and frame ordering have exactly one owner: [PulseEngine].
 * Callback delivery is serialized separately so a callback can dispatch without entering the
 * reducer stack recursively.
 */
@Deprecated(
    message = "Use DefaultPulseStore for ordered suspending input and StateFlow state.",
    replaceWith = ReplaceWith("DefaultPulseStore(initialState, reducer)"),
)
class DefaultStore<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    private val reducer: Reducer<S, I, E>,
    plugins: List<StorePlugin<S, I, E>> = emptyList(),
    autoStart: Boolean = true,
) : Store<S, I, E> {
    private val plugins: List<StorePlugin<S, I, E>> = plugins.toList()
    private val config = PulseRuntimeConfig()
    private val callbackHub = LegacyCallbackHub<S, E>(
        dispatcher = config.consumerDispatcher,
        failureReporter = config::reportFailure,
        storeId = config.storeId,
    )
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + config.consumerDispatcher + CoroutineName("${config.storeId}-legacy-close")
    )
    private val cleanupStarted = AtomicBoolean(false)
    private val engine = PulseEngine(
        initialState = initialState,
        reducer = PulseReducer<S, I, LegacyUiEffect<E>> { previous, input ->
            val next = reducer.reduce(previous, input)
            ReduceOutcome.Changed(
                state = next.state,
                uiEffects = next.effects.map(::LegacyUiEffect),
            )
        },
        config = config,
        plugins = emptyList(),
        initiallyStarted = autoStart,
        publishUiEffects = false,
        frameObserver = ::publishLegacyFrame,
    )

    override val currentState: S
        get() = engine.currentState

    override val isStarted: Boolean
        get() = engine.isStarted

    override val isClosed: Boolean
        get() = engine.isClosed

    init {
        if (autoStart) {
            callbackHub.publishAction { callPlugins { it.onStart(initialState) } }
            flushCallbacks()
        }
    }

    override fun start() {
        val started = runBlocking { engine.startAndAwait() }
        if (!started) return
        callbackHub.publishAction { callPlugins { it.onStart(engine.currentState) } }
        flushCallbacks()
    }

    override fun stop() {
        val stopped = runBlocking { engine.stopAndAwait() }
        if (!stopped) return
        callbackHub.publishAction { callPlugins { it.onStop(engine.currentState) } }
        flushCallbacks()
    }

    override fun close() {
        if (!cleanupStarted.compareAndSet(false, true)) return
        engine.close()

        if (callbackHub.isDeliveringOnCurrentThread()) {
            callbackHub.cutoffDeliveries()
            callPlugins { it.onClose(engine.currentState) }
            cleanupScope.launch { finishClose(closePluginDelivered = true) }
        } else {
            runBlocking { finishClose(closePluginDelivered = false) }
        }
    }

    override fun dispatch(intent: I): DispatchResult {
        val result = runBlocking { engine.send(intent) }
        flushCallbacks()
        return when (result) {
            is TransitionResult.Completed -> DispatchResult.Accepted
            is TransitionResult.Failed -> DispatchResult.Rejected(
                StoreError.ReducerFailure(result.failure.cause)
            )

            is TransitionResult.Rejected -> rejectWithoutFrame(result.reason)
        }
    }

    override fun observeState(observer: (S) -> Unit): Subscription {
        val subscription = callbackHub.registerState(
            snapshotSequence = engine.currentSequence,
            state = engine.currentState,
            callback = observer,
        )
        flushCallbacks()
        return subscription
    }

    override fun observeEffect(observer: (E) -> Unit): Subscription {
        val subscription = callbackHub.registerEffect(observer)
        flushCallbacks()
        return subscription
    }

    private fun publishLegacyFrame(frame: TransitionFrame<S, I, LegacyUiEffect<E>>) {
        val reducerFailure = frame.reducerFailure
        if (reducerFailure != null || frame.outcome == TransitionOutcome.ReducerFailed) {
            val error = StoreError.ReducerFailure(
                requireNotNull(reducerFailure) {
                    "ReducerFailed frame must carry its typed reducer failure."
                }.cause
            )
            val rejected = DispatchResult.Rejected(error)
            callbackHub.publishAction {
                callPlugins { it.onError(error) }
                callPlugins { it.onRejected(rejected) }
            }
            return
        }

        callbackHub.publishAction {
            callPlugins { it.onIntent(frame.input, frame.stateBefore) }
        }
        val stateChanged = frame.outcome == TransitionOutcome.Changed
        callbackHub.publishState(
            sequence = frame.sequenceId,
            state = frame.stateAfter,
            stateChanged = stateChanged,
        )
        if (stateChanged) {
            callbackHub.publishAction {
                callPlugins { it.onState(frame.stateAfter) }
            }
        }
        frame.uiEffects.forEach { envelope ->
            val effect = envelope.payload.value
            callbackHub.publishEffect(frame.sequenceId, effect)
            callbackHub.publishAction { callPlugins { it.onEffect(effect) } }
        }
    }

    private fun rejectWithoutFrame(reason: RejectionReason): DispatchResult.Rejected {
        val error = when (reason) {
            RejectionReason.NotStarted -> StoreError.StoreNotStarted
            RejectionReason.Closing,
            RejectionReason.Closed,
            -> StoreError.StoreClosed
        }
        val rejected = DispatchResult.Rejected(error)
        callbackHub.publishAction { callPlugins { it.onRejected(rejected) } }
        flushCallbacks()
        return rejected
    }

    private suspend fun finishClose(closePluginDelivered: Boolean) {
        try {
            engine.awaitClosed()
            if (!closePluginDelivered) {
                callbackHub.flush()
                callbackHub.publishTerminalAction { callPlugins { it.onClose(engine.currentState) } }
                callbackHub.flush()
                callbackHub.cutoffDeliveries()
            }
            callbackHub.close()
            callbackHub.awaitClosed()
        } finally {
            cleanupScope.cancel()
        }
    }

    private fun flushCallbacks() {
        if (callbackHub.isDeliveringOnCurrentThread()) return
        try {
            runBlocking { callbackHub.flush() }
        } catch (terminalFailure: Throwable) {
            // The compatibility facade has no asynchronous terminal-failure surface. Establish the
            // engine and callback cutoffs before propagating so no later dispatch can commit state
            // after callback delivery has irrecoverably terminated.
            engine.close()
            callbackHub.close()
            cleanupScope.cancel()
            throw terminalFailure
        }
    }

    private inline fun callPlugins(block: (StorePlugin<S, I, E>) -> Unit) {
        plugins.forEachIndexed { index, plugin ->
            try {
                block(plugin)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                config.reportFailure(
                    PulseFailure.PluginFailure(
                        context = FailureContext(
                            sequenceId = engine.currentSequence.takeIf { it > 0L },
                            stateRevision = engine.currentStateRevision,
                            component = "legacy-plugin-$index:${plugin.javaClass.name}",
                        ),
                        cause = failure,
                    )
                )
            }
        }
    }
}

private data class LegacyUiEffect<E : MviEffect>(
    val value: E,
) : UiEffect
