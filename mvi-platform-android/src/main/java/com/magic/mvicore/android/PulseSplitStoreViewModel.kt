package com.magic.mvicore.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.FailureContext
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
import com.magic.mvicore.contract.TransitionFrame
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.ContinuationInterceptor

/**
 * Split Intent ViewModel with one ordered admission boundary.
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
    private val pendingExecutionResults = Collections.newSetFromMap(
        ConcurrentHashMap<CompletableDeferred<PulseIntentExecutionResult>, Boolean>()
    )
    private val splitAdmissionPermits = Semaphore(runtimeConfig.mailboxCapacity)
    private val activeAdmissions = Collections.newSetFromMap(
        ConcurrentHashMap<SplitAdmissionLease, Boolean>()
    )
    private val splitOverflowDiagnosticPending = AtomicBoolean(false)
    private val executorInputs = Channel<ExecutorInput<S, UI>>(
        capacity = runtimeConfig.mailboxCapacity,
        onUndeliveredElement = { input ->
            input.completion?.complete(PulseIntentExecutionResult.Cancelled)
            input.admission.release()
        },
    )
    private val store = DefaultPulseStore(
        initialState = restoredInitialState,
        reducer = PulseReducer<S, SplitStoreInput<UI, M>, E> { previous, input ->
            when (input) {
                is SplitStoreInput.Ui -> {
                    input.admission.claim()
                    ReduceOutcome.Unchanged()
                }
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
    private val executorJob: Job
    private val transitionJob: Job
    private val savedStateJob: Job?
    private val storeClosureObserverJob: Job
    private val cleanupStarted = AtomicBoolean(false)
    private val pulseClearedHookInvoked = AtomicBoolean(false)
    private val platformClosed = CompletableDeferred<Unit>()
    private var parentCompletionHandle: DisposableHandle? = null
    private val cleanupScope = CoroutineScope(
        SupervisorJob() +
            runtimeConfig.consumerDispatcher +
            CoroutineName("${runtimeConfig.storeId}-android-close")
    )

    final override val state: StateFlow<S> = store.state
    final override val uiEffects: UiEffectStream<E> = store.effects
    /** Read-only Split frames for diagnostics and tests; this exposes no Store or mutation sink. */
    val transitions: Flow<TransitionFrame<S, PulseSplitInput<UI, M>, E>> =
        store.transitions.map { frame -> frame.toObservedFrame() }

    init {
        taskAccess = store.tasks
        executorJob = ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            for (input in executorInputs) {
                val context = PulseIntentContext(
                    intentId = input.intentId,
                    stateAtStart = input.stateAtStart,
                    inputType = input.intent.typeName(),
                    stateProvider = { store.state.value },
                    mutationDispatcher = mutationDispatcher,
                    tasks = store.tasks,
                    lifecycleActive = ::isExecutionActive,
                    failureReporter = { failure -> reportPlatformFailure(failure) },
                )
                try {
                    val result = when (val decision = uiIntentExecutor.execute(input.intent, context)) {
                        PulseIntentExecutionDecision.Completed -> PulseIntentExecutionResult.Completed
                        is PulseIntentExecutionDecision.Ignored -> {
                            PulseIntentExecutionResult.Ignored(decision.reason)
                        }
                    }
                    input.completion?.complete(result)
                } catch (cancelled: CancellationException) {
                    // A feature may cancel one intent without cancelling the serial executor lane.
                    // Owner/job cancellation still propagates and terminates the lane.
                    input.completion?.complete(PulseIntentExecutionResult.Cancelled)
                    currentCoroutineContext().ensureActive()
                } catch (failure: Exception) {
                    try {
                        reportPlatformFailure(
                            PulseFailure.ExecutorFailure(
                                context = FailureContext(
                                    requestId = input.intentId,
                                    component = COMPONENT_UI_INTENT_EXECUTOR,
                                    inputType = input.intent.typeName(),
                                ),
                                cause = failure,
                            ),
                            beforeCloseOnTerminal = { terminal ->
                                input.completion?.completeExceptionally(terminal)
                            },
                        )
                        input.completion?.complete(PulseIntentExecutionResult.Failed(failure))
                    } catch (terminal: Throwable) {
                        input.completion?.completeExceptionally(terminal)
                        throw terminal
                    }
                } catch (fatal: Throwable) {
                    input.completion?.completeExceptionally(fatal)
                    close()
                    throw fatal
                } finally {
                    input.admission.release()
                }
            }
        }
        transitionJob = ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.transitions.collect { frame ->
                val input = frame.input
                if (input is SplitStoreInput.Ui && input.admission.transferToExecutor()) {
                    executorInputs.send(
                        ExecutorInput(
                            intentId = frame.requestId,
                            intent = input.value,
                            stateAtStart = frame.stateBefore,
                            completion = input.completion,
                            admission = input.admission,
                        )
                    )
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
        storeClosureObserverJob = cleanupScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                store.awaitClosed()
            } catch (_: Throwable) {
                // close() awaits the Store again and preserves its terminal cleanup failure in
                // platformClosed. This observer only establishes adapter cleanup.
            } finally {
                close()
            }
        }
        parentCompletionHandle = registerParentCancellation()
    }

    /**
     * Suspends through ordered admission and the serial executor decision for this UI intent.
     *
     * Cancellation while waiting for a Split permit prevents admission. After admission, caller
     * cancellation can suppress executor work that has not yet been transferred; transferred work
     * remains runtime-owned and releases its permit during executor cleanup.
     */
    suspend fun send(intent: UI): PulseIntentExecutionResult {
        if (!isExecutionActive()) {
            return PulseIntentExecutionResult.Rejected(RejectionReason.Closing)
        }
        splitAdmissionPermits.acquire()
        if (!isExecutionActive()) {
            splitAdmissionPermits.release()
            return PulseIntentExecutionResult.Rejected(RejectionReason.Closing)
        }
        val admission = newAdmissionLease()
        val completion = CompletableDeferred<PulseIntentExecutionResult>()
        pendingExecutionResults += completion
        completion.invokeOnCompletion { pendingExecutionResults -= completion }
        return try {
            when (val result = store.send(SplitStoreInput.Ui(intent, completion, admission))) {
                is TransitionResult.Completed -> completion.await()
                is TransitionResult.Failed -> {
                    PulseIntentExecutionResult.Failed(result.failure.cause as Exception)
                }
                is TransitionResult.Rejected -> {
                    admission.release()
                    completion.complete(PulseIntentExecutionResult.Rejected(result.reason))
                    PulseIntentExecutionResult.Rejected(result.reason)
                }
            }
        } catch (cancelled: CancellationException) {
            admission.cancelBeforeExecutor()
            completion.complete(PulseIntentExecutionResult.Cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            try {
                admission.release()
                completion.completeExceptionally(failure)
            } finally {
                close()
            }
            throw failure
        }
    }

    /** Non-suspending enqueue-only UI input path. */
    fun trySend(intent: UI): EnqueueResult {
        if (!isExecutionActive()) {
            return EnqueueResult.Rejected(RejectionReason.Closing)
        }
        if (!splitAdmissionPermits.tryAcquire()) {
            reportSplitAdmissionOverflow(intent)
            return EnqueueResult.Full
        }
        if (!isExecutionActive()) {
            splitAdmissionPermits.release()
            return EnqueueResult.Rejected(RejectionReason.Closing)
        }
        val admission = newAdmissionLease()
        return store.trySend(SplitStoreInput.Ui(intent, completion = null, admission)).also { result ->
            if (result !is EnqueueResult.Enqueued) admission.release()
        }
    }

    /**
     * Adapts a non-suspending listener to the bounded Split admission path.
     *
     * Every accepted callback is processed in admission order. A bounded non-suspending path cannot
     * guarantee acceptance under overload, so [onRejected] is mandatory and observes both Full and
     * lifecycle rejection instead of allowing a silent drop.
     */
    fun callbackIngress(
        onRejected: (intent: UI, result: EnqueueResult) -> Unit,
    ): PulseCallbackIngress<UI> = PulseCallbackIngress(::trySend, onRejected)

    final override fun close() {
        if (!cleanupStarted.compareAndSet(false, true)) return

        // The store establishes its ordered admission cutoff first. Platform work is lifecycle
        // owned, so it is then cancelled instead of being allowed to outlive the ViewModel.
        store.close()
        pendingExecutionResults.forEach { result ->
            result.complete(PulseIntentExecutionResult.Cancelled)
        }
        activeAdmissions.toList().forEach(SplitAdmissionLease::release)
        executorInputs.cancel(CancellationException("PulseSplitStoreViewModel closed."))
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
                storeClosureObserverJob.join()
            } catch (failure: Throwable) {
                terminalFailure = terminalFailure.combine(failure)
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
        try {
            close()
        } finally {
            if (pulseClearedHookInvoked.compareAndSet(false, true)) {
                onPulseCleared()
            }
        }
    }

    /** Optional subclass cleanup hook invoked after the final framework-owned close request. */
    protected open fun onPulseCleared() = Unit

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
            when (binding.restoreFailurePolicy) {
                PulseRestoreFailurePolicy.FALLBACK_TO_INITIAL_STATE -> fallback
                PulseRestoreFailurePolicy.FAIL_CREATION -> throw failure
            }
        }
    }

    private suspend fun reportPlatformFailure(
        failure: PulseFailure,
        beforeCloseOnTerminal: (Throwable) -> Unit = {},
    ) {
        withContext(runtimeConfig.consumerDispatcher) {
            try {
                runtimeConfig.reportFailure(failure)
            } catch (terminal: Throwable) {
                beforeCloseOnTerminal(terminal)
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

    private fun newAdmissionLease(): SplitAdmissionLease {
        lateinit var admission: SplitAdmissionLease
        admission = SplitAdmissionLease {
            activeAdmissions.remove(admission)
            splitAdmissionPermits.release()
        }
        activeAdmissions += admission
        return admission
    }

    private fun reportSplitAdmissionOverflow(intent: UI) {
        if (runtimeConfig.overflowPolicy != com.magic.mvicore.runtime.MailboxOverflowPolicy.REJECT_AND_REPORT) {
            return
        }
        if (!splitOverflowDiagnosticPending.compareAndSet(false, true)) return
        ownerScope.launch {
            try {
                reportPlatformFailure(
                    PulseFailure.SplitAdmissionOverflow(
                        context = FailureContext(
                            component = COMPONENT_SPLIT_ADMISSION,
                            inputType = intent.typeName(),
                        ),
                        capacity = runtimeConfig.mailboxCapacity,
                    )
                )
            } finally {
                splitOverflowDiagnosticPending.set(false)
            }
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

    private fun Any.typeName(): String = this::class.qualifiedName ?: javaClass.name

    private fun TransitionFrame<S, SplitStoreInput<UI, M>, E>.toObservedFrame():
        TransitionFrame<S, PulseSplitInput<UI, M>, E> {
        val observedInput = when (val runtimeInput = input) {
            is SplitStoreInput.Ui -> PulseSplitInput.Ui(runtimeInput.value)
            is SplitStoreInput.Mutation -> PulseSplitInput.Mutation(runtimeInput.value)
        }
        return TransitionFrame(
            requestId = requestId,
            sequenceId = sequenceId,
            stateRevision = stateRevision,
            input = observedInput,
            stateBefore = stateBefore,
            stateAfter = stateAfter,
            outcome = outcome,
            uiEffects = uiEffects,
            startedAtNanos = startedAtNanos,
            completedAtNanos = completedAtNanos,
            dispatcher = dispatcher,
            reducerFailure = reducerFailure,
            mailboxDepthAtStart = mailboxDepthAtStart,
            mailboxHighWater = mailboxHighWater,
        )
    }

    private companion object {
        val COMPONENT_UI_INTENT_EXECUTOR = "ui-intent-executor"
        val COMPONENT_SAVED_STATE = "saved-state"
        val COMPONENT_SPLIT_ADMISSION = "split-admission"
        val REASON_LATE_TASK_MUTATION = "late-task-mutation"
    }
}

/** Android defaults keep reducer, transition, plugin, and coordinator work on Main.immediate. */
fun androidPulseRuntimeConfig(): PulseRuntimeConfig = androidPulseRuntimeConfig(PulseRuntimeConfig())

/** Applies Android Main dispatchers while preserving every non-dispatcher option from [base]. */
fun androidPulseRuntimeConfig(base: PulseRuntimeConfig): PulseRuntimeConfig {
    return base.copy(
        storeDispatcher = Dispatchers.Main.immediate,
        consumerDispatcher = Dispatchers.Main.immediate,
    )
}

private data class ExecutorInput<S : MviState, UI : MviUiIntent>(
    val intentId: Long,
    val intent: UI,
    val stateAtStart: S,
    val completion: CompletableDeferred<PulseIntentExecutionResult>?,
    val admission: SplitAdmissionLease,
)
