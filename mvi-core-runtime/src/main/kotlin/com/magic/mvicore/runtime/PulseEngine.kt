package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The single ordering owner used by both the v0.3 API and the v0.2 compatibility facade.
 *
 * The command channel is unbounded because input capacity is enforced by [inputPermits]. Control
 * commands must always be admitted so close cannot be starved by a full input mailbox.
 */
internal class PulseEngine<S : MviState, I : MviIntent, E : UiEffect>(
    initialState: S,
    private val reducer: PulseReducer<S, I, E>,
    private val config: PulseRuntimeConfig,
    plugins: List<PulseStorePlugin<S, I, E>>,
    initiallyStarted: Boolean,
    private val publishUiEffects: Boolean = true,
    private val frameObserver: suspend (TransitionFrame<S, I, E>) -> Unit = {},
) {
    private val engineJob: Job = SupervisorJob()
    private val engineScope = CoroutineScope(
        engineJob + config.storeDispatcher + CoroutineName("${config.storeId}-processor")
    )
    private val inputPermits = Semaphore(config.mailboxCapacity)
    private val commands = Channel<Command<S, I, E>>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState)
    private val mutableTransitions = MutableSharedFlow<TransitionFrame<S, I, E>>(
        replay = 0,
        extraBufferCapacity = 0,
    )
    private val effectStream = SingleCoordinatorUiEffectStream<E>(config, ::reportFailure)
    private val pluginHub = PluginDeliveryHub(plugins.toList(), config)
    private val taskRegistry = TaskRegistry(
        scope = engineScope,
        config = config,
        failureReporter = { failure -> reportFailure(failure, closeOnTerminal = false) },
        terminalFailureHandler = { close() },
    )
    private val closedSignal = CompletableDeferred<Unit>()
    private val admissionLock = Any()
    private val nextRequestId = AtomicLong(0L)
    private val publishedSequence = AtomicLong(0L)
    private val publishedStateRevision = AtomicLong(0L)
    private val overflowDiagnosticPending = AtomicBoolean(false)

    private var admissionState: AdmissionState = AdmissionState.OPEN
    private var started: Boolean = initiallyStarted
    private var sequenceId: Long = 0L
    private var stateRevision: Long = 0L
    private var nextEffectId: Long = 0L

    val state: StateFlow<S> = mutableState
    val transitions: SharedFlow<TransitionFrame<S, I, E>> = mutableTransitions
    val effects: UiEffectStream<E> = effectStream
    val tasks: PulseTasks = taskRegistry

    init {
        engineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            processCommands()
        }
    }

    val currentState: S
        get() = mutableState.value

    val currentSequence: Long
        get() = publishedSequence.get()

    val currentStateRevision: Long
        get() = publishedStateRevision.get()

    val isStarted: Boolean
        get() = synchronized(admissionLock) { started }

    val isClosed: Boolean
        get() = synchronized(admissionLock) { admissionState != AdmissionState.OPEN }

    suspend fun send(input: I): TransitionResult<S, I, E> {
        currentAdmissionRejection()?.let { reason ->
            return TransitionResult.Rejected(reason)
        }
        inputPermits.acquire()
        val pending = PendingInput<S, I, E>(input = input, completion = CompletableDeferred())
        val rejection = admitInput(pending)
        if (rejection != null) {
            inputPermits.release()
            return TransitionResult.Rejected(rejection)
        }

        return try {
            pending.completion!!.await()
        } catch (cancelled: CancellationException) {
            pending.cancelBeforeStart()
            throw cancelled
        }
    }

    fun trySend(input: I): EnqueueResult {
        val pending = PendingInput<S, I, E>(input = input, completion = null)
        return synchronized(admissionLock) {
            when (admissionState) {
                AdmissionState.CLOSING -> EnqueueResult.Rejected(RejectionReason.Closing)
                AdmissionState.CLOSED -> EnqueueResult.Rejected(RejectionReason.Closed)
                AdmissionState.OPEN -> {
                    if (!started) {
                        EnqueueResult.Rejected(RejectionReason.NotStarted)
                    } else if (!inputPermits.tryAcquire()) {
                        when (config.overflowPolicy) {
                            MailboxOverflowPolicy.REJECT -> EnqueueResult.Full
                            MailboxOverflowPolicy.REJECT_AND_REPORT -> {
                                if (enqueueOverflowDiagnostic(input)) {
                                    EnqueueResult.Full
                                } else {
                                    admissionState = AdmissionState.CLOSED
                                    EnqueueResult.Rejected(RejectionReason.Closed)
                                }
                            }
                        }
                    } else {
                        pending.requestId = nextRequestId.incrementAndGet()
                        if (commands.trySend(Command.Input(pending)).isSuccess) {
                            EnqueueResult.Enqueued(pending.requestId)
                        } else {
                            inputPermits.release()
                            admissionState = AdmissionState.CLOSED
                            EnqueueResult.Rejected(RejectionReason.Closed)
                        }
                    }
                }
            }
        }
    }

    suspend fun startAndAwait(): Boolean {
        val completion = CompletableDeferred<Unit>()
        val admitted = synchronized(admissionLock) {
            if (admissionState != AdmissionState.OPEN || started) {
                false
            } else {
                started = true
                commands.trySend(Command.Start(completion)).isSuccess
            }
        }
        if (admitted) completion.await()
        return admitted
    }

    suspend fun stopAndAwait(): Boolean {
        val completion = CompletableDeferred<Unit>()
        val admitted = synchronized(admissionLock) {
            if (admissionState != AdmissionState.OPEN || !started) {
                false
            } else {
                started = false
                commands.trySend(Command.Stop(completion)).isSuccess
            }
        }
        if (admitted) completion.await()
        return admitted
    }

    fun close() {
        synchronized(admissionLock) {
            if (admissionState != AdmissionState.OPEN) return
            admissionState = AdmissionState.CLOSING
            started = false
            if (commands.trySend(Command.Close).isFailure) {
                admissionState = AdmissionState.CLOSED
                closedSignal.complete(Unit)
            }
        }
        taskRegistry.close()
    }

    suspend fun awaitClosed() {
        closedSignal.await()
    }

    private fun admitInput(pending: PendingInput<S, I, E>): RejectionReason? {
        return synchronized(admissionLock) {
            when (admissionState) {
                AdmissionState.CLOSING -> RejectionReason.Closing
                AdmissionState.CLOSED -> RejectionReason.Closed
                AdmissionState.OPEN -> {
                    if (!started) {
                        RejectionReason.NotStarted
                    } else {
                        pending.requestId = nextRequestId.incrementAndGet()
                        if (commands.trySend(Command.Input(pending)).isSuccess) {
                            null
                        } else {
                            admissionState = AdmissionState.CLOSED
                            RejectionReason.Closed
                        }
                    }
                }
            }
        }
    }

    private fun currentAdmissionRejection(): RejectionReason? {
        return synchronized(admissionLock) {
            when (admissionState) {
                AdmissionState.CLOSING -> RejectionReason.Closing
                AdmissionState.CLOSED -> RejectionReason.Closed
                AdmissionState.OPEN -> if (started) null else RejectionReason.NotStarted
            }
        }
    }

    private suspend fun processCommands() {
        var terminalFailure: Throwable? = null
        try {
            for (command in commands) {
                when (command) {
                    is Command.Input -> processInput(command.pending)
                    is Command.Diagnostic -> {
                        overflowDiagnosticPending.set(false)
                        reportFailure(command.failure)
                    }
                    is Command.Start -> command.completion.complete(Unit)
                    is Command.Stop -> command.completion.complete(Unit)
                    Command.Close -> break
                }
            }
        } catch (failure: Throwable) {
            terminalFailure = failure
            throw failure
        } finally {
            withContext(NonCancellable) {
                finishEngine(terminalFailure)
            }
        }
    }

    private suspend fun processInput(pending: PendingInput<S, I, E>) {
        inputPermits.release()
        if (!pending.markStarted()) return

        try {
            processStartedInput(pending)
        } catch (failure: Throwable) {
            // Once an input has left the mailbox it is no longer visible to drainPendingCommands.
            // Every terminal failure after that point must complete its waiter before the engine
            // terminates, otherwise send() can suspend forever.
            pending.completion?.completeExceptionally(failure)
            throw failure
        }
    }

    private suspend fun processStartedInput(pending: PendingInput<S, I, E>) {
        sequenceId += 1L
        val frameSequence = sequenceId
        val stateBefore = mutableState.value
        val startedAt = config.clock.nanoTime()
        val dispatcher = Thread.currentThread().name

        val outcome = try {
            reducer.reduce(stateBefore, pending.input)
        } catch (cancelled: CancellationException) {
            pending.completion?.completeExceptionally(cancelled)
            throw cancelled
        } catch (failure: Exception) {
            processReducerFailure(
                pending = pending,
                sequence = frameSequence,
                stateBefore = stateBefore,
                startedAt = startedAt,
                dispatcher = dispatcher,
                cause = failure,
            )
            return
        } catch (fatal: Throwable) {
            pending.completion?.completeExceptionally(fatal)
            throw fatal
        }

        val normalized = normalize(stateBefore, outcome)
        if (normalized.stateChanged) {
            stateRevision += 1L
            mutableState.value = normalized.stateAfter
        }

        val envelopes = normalized.effects.mapIndexed { index, effect ->
            EffectEnvelope(
                effectId = ++nextEffectId,
                requestId = pending.requestId,
                sequenceId = frameSequence,
                stateRevision = stateRevision,
                index = index,
                payload = effect,
            )
        }
        val frame = TransitionFrame(
            requestId = pending.requestId,
            sequenceId = frameSequence,
            stateRevision = stateRevision,
            input = pending.input,
            stateBefore = stateBefore,
            stateAfter = normalized.stateAfter,
            outcome = normalized.outcome,
            uiEffects = envelopes,
            startedAtNanos = startedAt,
            completedAtNanos = config.clock.nanoTime(),
            dispatcher = dispatcher,
        )
        publishFrame(frame)
        pending.completion?.complete(TransitionResult.Completed(frame))
    }

    private suspend fun processReducerFailure(
        pending: PendingInput<S, I, E>,
        sequence: Long,
        stateBefore: S,
        startedAt: Long,
        dispatcher: String,
        cause: Exception,
    ) {
        val failure = PulseFailure.ReducerFailure(
            context = FailureContext(
                requestId = pending.requestId,
                sequenceId = sequence,
                stateRevision = stateRevision,
                component = COMPONENT_REDUCER,
                inputType = pending.input.typeName(),
            ),
            cause = cause,
        )
        val frame = TransitionFrame<S, I, E>(
            requestId = pending.requestId,
            sequenceId = sequence,
            stateRevision = stateRevision,
            input = pending.input,
            stateBefore = stateBefore,
            stateAfter = stateBefore,
            outcome = TransitionOutcome.ReducerFailed,
            startedAtNanos = startedAt,
            completedAtNanos = config.clock.nanoTime(),
            dispatcher = dispatcher,
            reducerFailure = failure,
        )
        publishFrame(frame)
        reportFailure(failure)
        pending.completion?.complete(TransitionResult.Failed(frame, failure))
    }

    private suspend fun publishFrame(frame: TransitionFrame<S, I, E>) {
        publishedStateRevision.set(frame.stateRevision)
        publishedSequence.set(frame.sequenceId)
        mutableTransitions.emit(frame)
        pluginHub.publishTransition(frame)
        if (publishUiEffects) {
            frame.uiEffects.forEach { envelope -> effectStream.emit(envelope) }
        }
        frameObserver(frame)
    }

    private fun normalize(
        stateBefore: S,
        outcome: ReduceOutcome<S, E>,
    ): NormalizedOutcome<S, E> {
        return when (outcome) {
            is ReduceOutcome.Changed -> {
                if (outcome.state == stateBefore) {
                    NormalizedOutcome(
                        stateAfter = stateBefore,
                        outcome = TransitionOutcome.Unchanged,
                        effects = outcome.uiEffects,
                        stateChanged = false,
                    )
                } else {
                    NormalizedOutcome(
                        stateAfter = outcome.state,
                        outcome = TransitionOutcome.Changed,
                        effects = outcome.uiEffects,
                        stateChanged = true,
                    )
                }
            }

            is ReduceOutcome.Unchanged -> NormalizedOutcome(
                stateAfter = stateBefore,
                outcome = TransitionOutcome.Unchanged,
                effects = outcome.uiEffects,
                stateChanged = false,
            )

            is ReduceOutcome.Ignored -> NormalizedOutcome(
                stateAfter = stateBefore,
                outcome = TransitionOutcome.Ignored(outcome.reason),
                effects = emptyList(),
                stateChanged = false,
            )
        }
    }

    private fun reportFailure(
        failure: PulseFailure,
        closeOnTerminal: Boolean = true,
    ) {
        try {
            config.reportFailure(failure)
            pluginHub.publishFailure(failure)
        } catch (terminalFailure: Throwable) {
            // Failure callbacks can also originate outside the processor (for example mailbox
            // overflow or a task diagnostic). A terminal callback failure must still establish the
            // same admission cutoff as one raised while publishing a transition.
            if (closeOnTerminal) close()
            throw terminalFailure
        }
    }

    /** Keeps caller-side overflow reporting bounded while preserving an immediate Full result. */
    private fun enqueueOverflowDiagnostic(input: I): Boolean {
        if (!overflowDiagnosticPending.compareAndSet(false, true)) return true
        val failure = PulseFailure.MailboxOverflow(
            context = FailureContext(
                component = COMPONENT_MAILBOX,
                inputType = input.typeName(),
            ),
            capacity = config.mailboxCapacity,
        )
        if (commands.trySend(Command.Diagnostic(failure)).isSuccess) return true
        overflowDiagnosticPending.set(false)
        return false
    }

    private fun Any.typeName(): String = this::class.qualifiedName ?: javaClass.name

    private suspend fun finishEngine(terminalFailure: Throwable?) {
        var cleanupFailure: Throwable? = null
        synchronized(admissionLock) {
            admissionState = AdmissionState.CLOSED
            started = false
        }
        try {
            commands.close()
            drainPendingCommands(terminalFailure)
        } catch (failure: Throwable) {
            cleanupFailure = failure
        }
        try {
            taskRegistry.close()
            taskRegistry.awaitClosed()
        } catch (failure: Throwable) {
            cleanupFailure = cleanupFailure.recordSuppressed(failure)
        }
        try {
            effectStream.close()
        } catch (failure: Throwable) {
            cleanupFailure = cleanupFailure.recordSuppressed(failure)
        }
        try {
            pluginHub.closeAndAwait()
        } catch (failure: Throwable) {
            cleanupFailure = cleanupFailure.recordSuppressed(failure)
        } finally {
            val cleanup = cleanupFailure
            if (terminalFailure != null && cleanup != null && terminalFailure !== cleanup) {
                terminalFailure.addSuppressed(cleanup)
            }
            if (cleanup == null) {
                closedSignal.complete(Unit)
            } else {
                closedSignal.completeExceptionally(cleanup)
            }
            engineJob.cancel()
        }

        if (terminalFailure == null) {
            cleanupFailure?.let { throw it }
        }
    }

    private fun Throwable?.recordSuppressed(next: Throwable): Throwable {
        val current = this ?: return next
        if (current !== next) current.addSuppressed(next)
        return current
    }

    private fun drainPendingCommands(terminalFailure: Throwable?) {
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            when (command) {
                is Command.Input -> {
                    inputPermits.release()
                    val completion = command.pending.completion
                    if (terminalFailure == null) {
                        completion?.complete(TransitionResult.Rejected(RejectionReason.Closed))
                    } else {
                        completion?.completeExceptionally(terminalFailure)
                    }
                }

                is Command.Start -> command.completion.complete(Unit)
                is Command.Stop -> command.completion.complete(Unit)
                is Command.Diagnostic -> overflowDiagnosticPending.set(false)
                Command.Close -> Unit
            }
        }
    }

    private data class NormalizedOutcome<S : MviState, E : UiEffect>(
        val stateAfter: S,
        val outcome: TransitionOutcome,
        val effects: List<E>,
        val stateChanged: Boolean,
    )

    private class PendingInput<S : MviState, I : MviIntent, E : UiEffect>(
        val input: I,
        val completion: CompletableDeferred<TransitionResult<S, I, E>>?,
    ) {
        private val processingState = AtomicInteger(STATE_PENDING)
        var requestId: Long = 0L

        fun markStarted(): Boolean {
            return processingState.compareAndSet(STATE_PENDING, STATE_STARTED)
        }

        fun cancelBeforeStart(): Boolean {
            return processingState.compareAndSet(STATE_PENDING, STATE_CANCELLED)
        }

        private companion object {
            const val STATE_PENDING = 0
            const val STATE_STARTED = 1
            const val STATE_CANCELLED = 2
        }
    }

    private sealed interface Command<out S : MviState, out I : MviIntent, out E : UiEffect> {
        data class Input<S : MviState, I : MviIntent, E : UiEffect>(
            val pending: PendingInput<S, I, E>,
        ) : Command<S, I, E>

        data class Start(
            val completion: CompletableDeferred<Unit>,
        ) : Command<Nothing, Nothing, Nothing>

        data class Stop(
            val completion: CompletableDeferred<Unit>,
        ) : Command<Nothing, Nothing, Nothing>

        data class Diagnostic(
            val failure: PulseFailure,
        ) : Command<Nothing, Nothing, Nothing>

        data object Close : Command<Nothing, Nothing, Nothing>
    }

    private enum class AdmissionState {
        OPEN,
        CLOSING,
        CLOSED,
    }

    private companion object {
        const val COMPONENT_MAILBOX = "mailbox"
        const val COMPONENT_REDUCER = "reducer"
    }
}
