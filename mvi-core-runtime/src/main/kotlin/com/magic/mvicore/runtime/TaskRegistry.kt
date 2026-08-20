package com.magic.mvicore.runtime

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TaskHandle
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskReplacementReason
import com.magic.mvicore.contract.TaskToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.ArrayDeque

/** Runtime-owned keyed task coordinator. It never exposes its jobs or scope. */
internal class TaskRegistry(
    scope: CoroutineScope,
    private val config: PulseRuntimeConfig,
    private val failureReporter: (PulseFailure) -> Unit = config::reportFailure,
    private val terminalFailureHandler: (Throwable) -> Unit = {},
) : PulseTasks {
    private val lock = Any()
    private val registryJob = SupervisorJob(scope.coroutineContext[Job])
    private val registryScope = CoroutineScope(
        scope.coroutineContext.minusKey(Job) +
            registryJob +
            CoroutineName("${config.storeId}-tasks")
    )
    private val states = mutableMapOf<TaskKey, KeyState>()

    private var closed: Boolean = false
    private var nextRequestId: Long = 0L

    override val isClosed: Boolean
        get() = synchronized(lock) { closed || !registryJob.isActive }

    override fun launch(
        key: TaskKey,
        policy: TaskPolicy,
        block: suspend (TaskToken) -> Unit,
    ): TaskLaunchResult {
        val jobsToCancel = mutableListOf<Job>()
        val terminalUpdates = mutableListOf<TerminalUpdate>()
        var jobToStart: Job? = null
        lateinit var admittedRequest: TaskRequest

        synchronized(lock) {
            if (closed || !registryJob.isActive) return TaskLaunchResult.Closed

            var state = states[key]
            if (state != null && state.policy != policy) {
                state.detachAll(
                    outcome = TaskOutcome.Replaced(TaskReplacementReason.POLICY_CHANGED),
                    jobsToCancel = jobsToCancel,
                    terminalUpdates = terminalUpdates,
                )
                states.remove(key)
                state = null
            }

            if (
                state != null &&
                policy == TaskPolicy.DropWhileRunning &&
                state.activeTasks.isNotEmpty()
            ) {
                return TaskLaunchResult.DroppedWhileRunning
            }

            admittedRequest = TaskRequest(
                requestId = ++nextRequestId,
                block = block,
            )
            if (state == null) {
                state = KeyState(policy)
                states[key] = state
                jobToStart = createActiveTaskLocked(key, state, admittedRequest)
            } else {
                when (policy) {
                    TaskPolicy.Latest -> {
                        state.detachAll(
                            outcome = TaskOutcome.Replaced(TaskReplacementReason.LATEST),
                            jobsToCancel = jobsToCancel,
                            terminalUpdates = terminalUpdates,
                        )
                        jobToStart = createActiveTaskLocked(key, state, admittedRequest)
                    }

                    TaskPolicy.DropWhileRunning -> {
                        jobToStart = createActiveTaskLocked(key, state, admittedRequest)
                    }

                    TaskPolicy.Queue -> {
                        if (state.activeTasks.isEmpty()) {
                            jobToStart = createActiveTaskLocked(key, state, admittedRequest)
                        } else {
                            state.queued.addLast(admittedRequest)
                        }
                    }

                    TaskPolicy.Parallel -> {
                        jobToStart = createActiveTaskLocked(key, state, admittedRequest)
                    }

                    TaskPolicy.Conflate -> {
                        if (state.activeTasks.isEmpty()) {
                            jobToStart = createActiveTaskLocked(key, state, admittedRequest)
                        } else {
                            state.conflated?.let { pending ->
                                terminalUpdates += TerminalUpdate(
                                    request = pending,
                                    outcome = TaskOutcome.Replaced(TaskReplacementReason.CONFLATED),
                                )
                            }
                            state.conflated = admittedRequest
                        }
                    }
                }
            }
        }

        terminalUpdates.completeAll()
        jobsToCancel.forEach { job ->
            job.cancel(CancellationException("Task replaced by keyed policy."))
        }
        jobToStart?.start()
        return TaskLaunchResult.Accepted(admittedRequest.handle)
    }

    /** True only while this exact token generation remains active. */
    override fun isCurrent(token: TaskToken): Boolean {
        return synchronized(lock) {
            !closed &&
                registryJob.isActive &&
                states[token.key]?.activeTasks?.get(token.value)?.token === token
        }
    }

    override fun validate(token: TaskToken): Boolean {
        if (isCurrent(token)) return true
        try {
            failureReporter(
                PulseFailure.LateMutation(
                    context = FailureContext(component = token.key.value),
                    taskKey = token.key.value,
                    token = token.value,
                )
            )
        } catch (terminal: Throwable) {
            terminalFailureHandler(terminal)
            throw terminal
        }
        return false
    }

    /** Cancels active work and discards pending work for [key]. */
    override fun cancel(key: TaskKey): Boolean {
        val jobsToCancel = mutableListOf<Job>()
        val terminalUpdates = mutableListOf<TerminalUpdate>()
        synchronized(lock) {
            val state = states.remove(key) ?: return false
            state.detachAll(
                outcome = TaskOutcome.Cancelled,
                jobsToCancel = jobsToCancel,
                terminalUpdates = terminalUpdates,
            )
        }

        terminalUpdates.completeAll()
        jobsToCancel.forEach { job ->
            job.cancel(CancellationException("Keyed task cancelled."))
        }
        return true
    }

    /** Invalidates all tokens, discards pending work, and cancels every active task. */
    fun close() {
        val jobsToCancel = mutableListOf<Job>()
        val terminalUpdates = mutableListOf<TerminalUpdate>()
        synchronized(lock) {
            if (closed) return
            closed = true
            states.values.forEach { state ->
                state.detachAll(
                    outcome = TaskOutcome.Closed,
                    jobsToCancel = jobsToCancel,
                    terminalUpdates = terminalUpdates,
                )
            }
            states.clear()
        }

        terminalUpdates.completeAll()
        jobsToCancel.forEach { job ->
            job.cancel(CancellationException("TaskRegistry closed."))
        }
        registryJob.cancel(CancellationException("TaskRegistry closed."))
    }

    /** Suspends until close or parent-scope cancellation has finished all task cleanup. */
    suspend fun awaitClosed() {
        registryJob.join()
    }

    private fun createActiveTaskLocked(
        key: TaskKey,
        state: KeyState,
        request: TaskRequest,
    ): Job {
        val token = RuntimeTaskToken(key = key, value = request.requestId)
        lateinit var activeTask: ActiveTask
        val job = registryScope.async(start = CoroutineStart.LAZY) {
            execute(token, request)
        }
        activeTask = ActiveTask(
            token = token,
            request = request,
            job = job,
        )
        state.activeTasks[token.value] = activeTask
        job.invokeOnCompletion { cause ->
            onTaskCompleted(key, activeTask, cause)
        }
        return job
    }

    private suspend fun execute(
        token: TaskToken,
        request: TaskRequest,
    ) {
        try {
            request.block(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            try {
                failureReporter(
                    PulseFailure.ExecutorFailure(
                        context = FailureContext(component = token.key.value),
                        cause = failure,
                    )
                )
            } catch (terminal: Throwable) {
                request.recordTerminalFailure(terminal)
                terminalFailureHandler(terminal)
                throw terminal
            }
            request.recordOutcome(TaskOutcome.Failed(failure))
        } catch (fatal: Throwable) {
            throw fatal
        }
    }

    private fun onTaskCompleted(
        key: TaskKey,
        completedTask: ActiveTask,
        cause: Throwable?,
    ) {
        var nextJob: Job? = null
        val terminalUpdates = mutableListOf<TerminalUpdate>()
        synchronized(lock) {
            val state = states[key]
            if (state?.activeTasks?.get(completedTask.token.value) !== completedTask) {
                if (!completedTask.request.isCompleted) {
                    terminalUpdates += terminalUpdateFor(completedTask.request, cause)
                }
                return@synchronized
            }
            state.activeTasks.remove(completedTask.token.value)

            if (!completedTask.request.isCompleted) {
                terminalUpdates += terminalUpdateFor(completedTask.request, cause)
            }

            if (closed || !registryJob.isActive) {
                state.detachPending(TaskOutcome.Closed, terminalUpdates)
                states.remove(key)
                return@synchronized
            }

            if (state.activeTasks.isEmpty()) {
                val next = when (state.policy) {
                    TaskPolicy.Queue -> state.queued.pollFirst()
                    TaskPolicy.Conflate -> state.conflated.also { state.conflated = null }
                    TaskPolicy.Latest,
                    TaskPolicy.DropWhileRunning,
                    TaskPolicy.Parallel,
                    -> null
                }

                if (next == null) {
                    states.remove(key)
                } else {
                    nextJob = createActiveTaskLocked(key, state, next)
                }
            }
        }
        terminalUpdates.completeAll()
        nextJob?.start()
    }

    private fun terminalUpdateFor(
        request: TaskRequest,
        cause: Throwable?,
    ): TerminalUpdate {
        request.terminalFailure?.let { failure ->
            return TerminalUpdate.exceptional(request, failure)
        }
        request.recordedOutcome?.let { outcome ->
            return TerminalUpdate(request, outcome)
        }
        if (closed || !registryJob.isActive) {
            return TerminalUpdate(request, TaskOutcome.Closed)
        }
        return when (cause) {
            null -> TerminalUpdate(request, TaskOutcome.Completed)
            is CancellationException -> TerminalUpdate(request, TaskOutcome.Cancelled)
            is Exception -> TerminalUpdate(request, TaskOutcome.Failed(cause))
            else -> TerminalUpdate.exceptional(request, cause)
        }
    }

    private class KeyState(
        val policy: TaskPolicy,
    ) {
        val activeTasks = mutableMapOf<Long, ActiveTask>()
        val queued = ArrayDeque<TaskRequest>()
        var conflated: TaskRequest? = null

        fun detachAll(
            outcome: TaskOutcome,
            jobsToCancel: MutableList<Job>,
            terminalUpdates: MutableList<TerminalUpdate>,
        ) {
            activeTasks.values.forEach { active ->
                jobsToCancel += active.job
                terminalUpdates += active.request.terminalUpdate(outcome)
            }
            activeTasks.clear()
            detachPending(outcome, terminalUpdates)
        }

        fun detachPending(
            outcome: TaskOutcome,
            terminalUpdates: MutableList<TerminalUpdate>,
        ) {
            queued.forEach { request ->
                terminalUpdates += TerminalUpdate(request, outcome)
            }
            queued.clear()
            conflated?.let { request ->
                terminalUpdates += TerminalUpdate(request, outcome)
            }
            conflated = null
        }
    }

    private class ActiveTask(
        val token: TaskToken,
        val request: TaskRequest,
        val job: Job,
    )

    private class TaskRequest(
        val requestId: Long,
        val block: suspend (TaskToken) -> Unit,
    ) {
        val handle = RuntimeTaskHandle(requestId)
        @Volatile
        var recordedOutcome: TaskOutcome? = null
            private set
        @Volatile
        var terminalFailure: Throwable? = null
            private set
        val isCompleted: Boolean
            get() = handle.isCompleted

        fun recordOutcome(outcome: TaskOutcome) {
            recordedOutcome = outcome
        }

        fun recordTerminalFailure(cause: Throwable) {
            terminalFailure = cause
        }

        fun complete(outcome: TaskOutcome) {
            handle.complete(outcome)
        }

        fun fail(cause: Throwable) {
            handle.fail(cause)
        }

        fun terminalUpdate(fallback: TaskOutcome): TerminalUpdate {
            return terminalFailure?.let { TerminalUpdate.exceptional(this, it) }
                ?: TerminalUpdate(this, fallback)
        }
    }

    private class RuntimeTaskHandle(
        override val requestId: Long,
    ) : TaskHandle {
        private val outcome = CompletableDeferred<TaskOutcome>()

        val isCompleted: Boolean
            get() = outcome.isCompleted

        override suspend fun awaitOutcome(): TaskOutcome = outcome.await()

        fun complete(value: TaskOutcome) {
            outcome.complete(value)
        }

        fun fail(cause: Throwable) {
            outcome.completeExceptionally(cause)
        }
    }

    private class RuntimeTaskToken(
        override val key: TaskKey,
        override val value: Long,
    ) : TaskToken {
        override fun toString(): String = "TaskToken(key=$key, value=$value)"
    }

    private class TerminalUpdate private constructor(
        val request: TaskRequest,
        private val outcome: TaskOutcome?,
        private val exception: Throwable?,
    ) {
        constructor(request: TaskRequest, outcome: TaskOutcome) : this(request, outcome, null)

        fun complete() {
            val failure = exception
            if (failure == null) {
                request.complete(requireNotNull(outcome))
            } else {
                request.fail(failure)
            }
        }

        companion object {
            fun exceptional(request: TaskRequest, cause: Throwable): TerminalUpdate {
                return TerminalUpdate(request, outcome = null, exception = cause)
            }
        }
    }

    private fun List<TerminalUpdate>.completeAll() {
        forEach(TerminalUpdate::complete)
    }
}
