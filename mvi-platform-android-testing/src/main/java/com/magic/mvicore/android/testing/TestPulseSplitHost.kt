package com.magic.mvicore.android.testing

import com.magic.mvicore.android.PulseIntentExecutionResult
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.testing.FailureProbe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Probe-enabled owner of one real Android Split ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestPulseSplitHost<
    S : MviState,
    UI : MviUiIntent,
    M : MviMutation,
    E : UiEffect,
    VM : PulseSplitStoreViewModel<S, UI, M, E>,
> internal constructor(
    val viewModel: VM,
    val failureProbe: FailureProbe,
    private val testScope: TestScope,
    private val executionScope: CoroutineScope,
) {
    val stateProbe = PulseSplitStateProbe<S>()
    val transitionProbe = PulseSplitTransitionProbe<S, UI, M, E>()
    val effectProbe = PulseSplitEffectProbe<E>()

    private val closeStarted = AtomicBoolean(false)
    private val cleanupStarted = AtomicBoolean(false)
    private val cleanupCompleted = CompletableDeferred<Unit>()
    private val stateCollector: Job = testScope.backgroundScope.launch(
        start = CoroutineStart.UNDISPATCHED,
    ) {
        viewModel.state.collect(stateProbe::record)
    }
    private val effectCollector: Job = testScope.backgroundScope.launch(
        start = CoroutineStart.UNDISPATCHED,
    ) {
        viewModel.uiEffects.collect(effectProbe::record)
    }
    private val transitionCollector: Job = testScope.backgroundScope.launch(
        start = CoroutineStart.UNDISPATCHED,
    ) {
        viewModel.transitions.collect(transitionProbe::record)
    }

    /**
     * Sends one UI intent and runs collectors scheduled at the resulting virtual time.
     *
     * Completion covers that intent's serial executor decision and mutations directly awaited by
     * the executor. It deliberately does not await keyed tasks, tickers, or infinite sources started
     * by the executor; await their task handle or probe explicitly.
     */
    suspend fun sendAndDrain(intent: UI): PulseIntentExecutionResult {
        val result = viewModel.send(intent)
        testScope.runCurrent()
        return result
    }

    /** Establishes the close cutoff, drains finite cleanup, and stops test-owned collectors. */
    suspend fun closeAndDrain() {
        startClose()
        awaitCloseAndStopCollectors()
    }

    internal fun startClose() {
        if (closeStarted.compareAndSet(false, true)) viewModel.close()
    }

    internal suspend fun awaitCloseAndStopCollectors() {
        startClose()
        if (!cleanupStarted.compareAndSet(false, true)) {
            cleanupCompleted.await()
            return
        }

        withContext(NonCancellable) {
            var failure: Throwable? = null
            try {
                viewModel.awaitClosed()
            } catch (closeFailure: Throwable) {
                failure = closeFailure
            }
            try {
                stateCollector.cancelAndJoin()
            } catch (collectorFailure: Throwable) {
                failure = failure.combine(collectorFailure)
            }
            try {
                effectCollector.cancelAndJoin()
            } catch (collectorFailure: Throwable) {
                failure = failure.combine(collectorFailure)
            }
            try {
                transitionCollector.cancelAndJoin()
            } catch (collectorFailure: Throwable) {
                failure = failure.combine(collectorFailure)
            }
            executionScope.cancel()
            val terminal = failure
            if (terminal == null) {
                cleanupCompleted.complete(Unit)
            } else {
                cleanupCompleted.completeExceptionally(terminal)
                throw terminal
            }
        }
    }
}

private fun Throwable?.combine(next: Throwable): Throwable {
    val current = this ?: return next
    if (current !== next) current.addSuppressed(next)
    return current
}
