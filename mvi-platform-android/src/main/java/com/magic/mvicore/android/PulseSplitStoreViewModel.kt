package com.magic.mvicore.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.PulseTasks
import com.magic.mvicore.runtime.UiEffectStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor

/**
 * v0.3 Split Intent ViewModel.
 *
 * Its public input surface accepts [UI] only. Mutations can be emitted solely from the
 * executor-owned [PulseIntentContext]. The store drains UI frames admitted before [close], while
 * owner-bound executor work is cancelled by close; an [EnqueueResult.Enqueued] value therefore
 * reports mailbox admission rather than executor completion.
 */
open class PulseSplitStoreViewModel<
    S : MviState,
    UI : MviUiIntent,
    M : MviMutation,
    E : UiEffect,
>(
    initialState: S,
    mutationReducer: PulseMutationReducer<S, M, E>,
    private val uiIntentExecutor: PulseUiIntentExecutor<S, UI, M> = PulseUiIntentExecutor.noop(),
    private val runtimeConfig: PulseRuntimeConfig = androidPulseRuntimeConfig(),
    private val savedState: PulseSavedState<S>? = null,
    executionOwner: PulseAndroidExecutionOwner? = null,
) : ViewModel(), PulseStateHost<S, E>, AutoCloseable {
    private val restoredInitialState = restoreInitialState(initialState)
    private val executionParentContext =
        executionOwner?.coroutineContext() ?: viewModelScope.coroutineContext
    private val executionParentJob = requireNotNull(executionParentContext[Job])
    private val ownerJob = SupervisorJob(executionParentJob)
    private val cancellationSentinel = Job(ownerJob)
    private val ownerScope = CoroutineScope(
        executionParentContext
            .minusKey(Job)
            .minusKey(ContinuationInterceptor) +
            ownerJob +
            runtimeConfig.consumerDispatcher +
            CoroutineName("${runtimeConfig.storeId}-android-owner")
    )
    private lateinit var taskAccess: PulseTasks
    private val executorInputs = Channel<UI>(runtimeConfig.mailboxCapacity)
    private val store = DefaultPulseStore(
        initialState = restoredInitialState,
        reducer = PulseReducer<S, SplitStoreInput<UI, M>, E> { previous, input ->
            when (input) {
                is SplitStoreInput.Ui -> ReduceOutcome.Unchanged()
                is SplitStoreInput.Mutation -> {
                    val token = input.token
                    if (token != null && !taskAccess.validate(token)) {
                        ReduceOutcome.Ignored(REASON_LATE_TASK_MUTATION)
                    } else {
                        mutationReducer.reduce(previous, input.value)
                    }
                }
            }
        },
        config = runtimeConfig,
    )
    private val mutationDispatcher = MutationDispatcher<M> { mutation, token ->
        if (!isExecutionActive()) {
            if (token != null && validateTaskToken(token)) {
                reportLateMutation(token)
            }
            return@MutationDispatcher false
        }
        if (token != null && !validateTaskToken(token)) {
            return@MutationDispatcher false
        }
        when (val result = store.send(SplitStoreInput.Mutation(mutation, token))) {
            is TransitionResult.Completed -> {
                val outcome = result.frame.outcome
                outcome !is TransitionOutcome.Ignored || outcome.reason != REASON_LATE_TASK_MUTATION
            }

            is TransitionResult.Failed -> false
            is TransitionResult.Rejected -> {
                if (token != null && validateTaskToken(token)) {
                    // The close cutoff and task invalidation are adjacent operations. If this
                    // coroutine observed the cutoff in their tiny interleaving window, report the
                    // rejected attempt explicitly instead of losing the late-mutation diagnostic.
                    reportLateMutation(token)
                }
                false
            }
        }
    }
    private val intentContext: PulseIntentContext<S, M>
    private val executorJob: Job
    private val transitionJob: Job
    private val savedStateJob: Job?
    private val cleanupStarted = AtomicBoolean(false)
    private val platformClosed = CompletableDeferred<Unit>()
    private var parentCompletionHandle: DisposableHandle? = null
    private val cleanupScope = CoroutineScope(
        SupervisorJob() +
            runtimeConfig.consumerDispatcher +
            CoroutineName("${runtimeConfig.storeId}-android-close")
    )

    final override val state: StateFlow<S> = store.state
    final override val uiEffects: UiEffectStream<E> = store.effects

    init {
        taskAccess = store.tasks
        intentContext = PulseIntentContext(
            stateProvider = { store.state.value },
            mutationDispatcher = mutationDispatcher,
            tasks = store.tasks,
            lifecycleActive = ::isExecutionActive,
        )
        executorJob = ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (intent in executorInputs) {
                try {
                    uiIntentExecutor.execute(intent, intentContext)
                } catch (cancelled: CancellationException) {
                    // A feature may cancel one intent without cancelling the serial executor lane.
                    // Owner/job cancellation still propagates and terminates the lane.
                    currentCoroutineContext().ensureActive()
                } catch (failure: Exception) {
                    reportPlatformFailure(
                        PulseFailure.ExecutorFailure(
                            context = FailureContext(component = COMPONENT_UI_INTENT_EXECUTOR),
                            cause = failure,
                        )
                    )
                } catch (fatal: Throwable) {
                    close()
                    throw fatal
                }
            }
        }
        transitionJob = ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.transitions.collect { frame ->
                val input = frame.input
                if (input is SplitStoreInput.Ui) {
                    executorInputs.send(input.value)
                }
            }
        }
        savedStateJob = savedState?.let { binding ->
            ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    state.collect { current ->
                        saveStateOrReport(binding, current)
                    }
                } catch (terminal: Throwable) {
                    close()
                    throw terminal
                }
            }
        }
        parentCompletionHandle = registerParentCancellation()
    }

    /** UI-friendly path that applies mailbox backpressure inside the ViewModel-owned scope. */
    fun send(intent: UI) {
        if (!isExecutionActive()) return
        ownerScope.launch {
            // Once admitted, the ordered Store frame belongs to the Store cutoff, not to the
            // caller's lifecycle Job. Platform cancellation may stop the later executor work.
            withContext(NonCancellable) {
                store.send(SplitStoreInput.Ui(intent))
            }
        }
    }

    /** Suspends until the UI input frame completes, useful for non-UI coordinators and tests. */
    internal suspend fun sendAndAwait(intent: UI): Boolean {
        if (!isExecutionActive()) return false
        return store.send(SplitStoreInput.Ui(intent)) is TransitionResult.Completed
    }

    /** Non-suspending enqueue-only UI input path. */
    fun trySend(intent: UI): EnqueueResult {
        if (!isExecutionActive()) {
            return EnqueueResult.Rejected(RejectionReason.Closing)
        }
        return store.trySend(SplitStoreInput.Ui(intent))
    }

    final override fun close() {
        if (!cleanupStarted.compareAndSet(false, true)) return

        // The store establishes its ordered admission cutoff first. Platform work is lifecycle
        // owned, so it is then cancelled instead of being allowed to outlive the ViewModel.
        store.close()
        executorInputs.close()
        parentCompletionHandle?.dispose()
        parentCompletionHandle = null
        ownerJob.cancel()

        cleanupScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var terminalFailure: Throwable? = null
            try {
                store.awaitClosed()
            } catch (failure: Throwable) {
                terminalFailure = failure
            }
            try {
                transitionJob.join()
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
            }
            try {
                executorJob.join()
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
            }
            try {
                savedStateJob?.join()
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
            }
            try {
                ownerJob.join()
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
            }
            try {
                savedState?.let { binding -> saveStateOrReport(binding, state.value) }
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
            }
            try {
                val terminal = terminalFailure
                if (terminal == null) {
                    platformClosed.complete(Unit)
                } else {
                    platformClosed.completeExceptionally(terminal)
                }
            } finally {
                cleanupScope.cancel()
            }
        }
    }

    /** Waits for the runtime cutoff and every ViewModel-owned adapter job to finish cleanup. */
    suspend fun awaitClosed() = platformClosed.await()

    final override fun onCleared() {
        close()
    }

    private fun restoreInitialState(fallback: S): S {
        val binding = savedState ?: return fallback
        return try {
            binding.restore() ?: fallback
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            runtimeConfig.reportFailure(
                PulseFailure.StateRestoreFailure(
                    context = FailureContext(component = COMPONENT_SAVED_STATE),
                    cause = failure,
                )
            )
            fallback
        }
    }

    private suspend fun reportPlatformFailure(failure: PulseFailure) {
        withContext(runtimeConfig.consumerDispatcher) {
            try {
                runtimeConfig.reportFailure(failure)
            } catch (terminal: Throwable) {
                close()
                throw terminal
            }
        }
    }

    private fun isExecutionActive(): Boolean {
        return executionParentJob.isActive &&
            ownerJob.isActive &&
            !cleanupStarted.get()
    }

    private suspend fun validateTaskToken(token: TaskToken): Boolean {
        return withContext(runtimeConfig.consumerDispatcher) {
            taskAccess.validate(token)
        }
    }

    private suspend fun reportLateMutation(token: TaskToken) {
        reportPlatformFailure(
            PulseFailure.LateMutation(
                context = FailureContext(component = token.key.value),
                taskKey = token.key.value,
                token = token.value,
            )
        )
    }

    private suspend fun saveStateOrReport(
        binding: PulseSavedState<S>,
        current: S,
    ) {
        try {
            binding.save(current)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            reportPlatformFailure(
                PulseFailure.StateSaveFailure(
                    context = FailureContext(component = COMPONENT_SAVED_STATE),
                    cause = failure,
                )
            )
        }
    }

    private fun registerParentCancellation(): DisposableHandle {
        return cancellationSentinel.invokeOnCompletion {
            // The sentinel has no child work, so parent cancellation completes it without waiting
            // for adapter cleanup. close() is non-blocking and establishes the Store cutoff.
            close()
        }
    }

    private fun Throwable?.combine(next: Throwable): Throwable {
        val current = this ?: return next
        if (current !== next) current.addSuppressed(next)
        return current
    }

    private companion object {
        val COMPONENT_UI_INTENT_EXECUTOR = "ui-intent-executor"
        val COMPONENT_SAVED_STATE = "saved-state"
        val REASON_LATE_TASK_MUTATION = "late-task-mutation"
    }
}

/** Android defaults keep reducer, transition, plugin, and coordinator work on Main.immediate. */
fun androidPulseRuntimeConfig(): PulseRuntimeConfig {
    return PulseRuntimeConfig(
        storeDispatcher = Dispatchers.Main.immediate,
        consumerDispatcher = Dispatchers.Main.immediate,
    )
}

private sealed interface SplitStoreInput<out UI : MviUiIntent, out M : MviMutation> : MviIntent {
    data class Ui<UI : MviUiIntent>(
        val value: UI,
    ) : SplitStoreInput<UI, Nothing>

    data class Mutation<M : MviMutation>(
        val value: M,
        val token: TaskToken?,
    ) : SplitStoreInput<Nothing, M>
}
