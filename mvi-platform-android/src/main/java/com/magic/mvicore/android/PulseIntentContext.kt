package com.magic.mvicore.android

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.runtime.PulseTasks

/** Executor-only context. UI code never receives this mutation capability. */
class PulseIntentContext<S : MviState, M : MviMutation> internal constructor(
    val intentId: Long,
    val stateAtStart: S,
    private val inputType: String,
    private val stateProvider: () -> S,
    private val mutationDispatcher: MutationDispatcher<M>,
    private val tasks: PulseTasks,
    private val lifecycleActive: () -> Boolean,
    private val failureReporter: suspend (PulseFailure) -> Unit,
) {
    /** Latest committed state. Use [stateAtStart] when the decision needs a stable snapshot. */
    val currentState: S
        get() = stateProvider()

    suspend fun mutate(mutation: M): Boolean {
        return mutationDispatcher.dispatch(mutation, token = null)
    }

    fun launchTask(
        key: TaskKey,
        policy: TaskPolicy,
        block: suspend PulseTaskContext<S, M>.() -> Unit,
    ): TaskLaunchResult {
        if (!lifecycleActive()) return TaskLaunchResult.Closed
        return tasks.launch(
            key = key,
            policy = policy,
            failureContext = FailureContext(
                requestId = intentId,
                component = key.value,
                inputType = inputType,
            ),
        ) { token ->
            PulseTaskContext(
                stateProvider = stateProvider,
                mutationDispatcher = mutationDispatcher,
                token = token,
            ).block()
        }
    }

    fun cancelTask(key: TaskKey): Boolean {
        if (!lifecycleActive()) return false
        return tasks.cancel(key)
    }

    fun cancelAllTasks(): Int {
        if (!lifecycleActive()) return 0
        return tasks.cancelAll()
    }

    /** Reports a feature-owned non-fatal executor failure with this intent's correlation id. */
    suspend fun reportFailure(
        component: String,
        cause: Exception,
    ) {
        failureReporter(
            PulseFailure.ExecutorFailure(
                context = FailureContext(
                    requestId = intentId,
                    component = component,
                    inputType = inputType,
                ),
                cause = cause,
            )
        )
    }
}

/** Mutation capability scoped to one active task token. */
class PulseTaskContext<S : MviState, M : MviMutation> internal constructor(
    private val stateProvider: () -> S,
    private val mutationDispatcher: MutationDispatcher<M>,
    val token: TaskToken,
) {
    val currentState: S
        get() = stateProvider()

    suspend fun mutate(mutation: M): Boolean {
        return mutationDispatcher.dispatch(mutation, token)
    }
}

/** Executes one UI intent after its ordered input frame has completed. */
fun interface PulseUiIntentExecutor<S : MviState, UI : MviUiIntent, M : MviMutation> {
    suspend fun execute(
        intent: UI,
        context: PulseIntentContext<S, M>,
    ): PulseIntentExecutionDecision

    companion object {
        fun <S : MviState, UI : MviUiIntent, M : MviMutation> noop(): PulseUiIntentExecutor<S, UI, M> {
            return PulseUiIntentExecutor { _, _ -> PulseIntentExecutionDecision.Completed }
        }
    }
}

/** Executor-owned semantic decision for a UI intent. */
sealed interface PulseIntentExecutionDecision {
    data object Completed : PulseIntentExecutionDecision

    data class Ignored(
        val reason: String,
    ) : PulseIntentExecutionDecision {
        init {
            require(reason.isNotBlank()) { "Ignored reason must not be blank." }
        }
    }
}

/** End-to-end result returned by the suspending UI input path. */
sealed interface PulseIntentExecutionResult {
    data object Completed : PulseIntentExecutionResult

    data class Ignored(
        val reason: String,
    ) : PulseIntentExecutionResult

    data class Failed(
        val cause: Exception,
    ) : PulseIntentExecutionResult

    data object Cancelled : PulseIntentExecutionResult

    data class Rejected(
        val reason: com.magic.mvicore.contract.RejectionReason,
    ) : PulseIntentExecutionResult
}

internal fun interface MutationDispatcher<M : MviMutation> {
    suspend fun dispatch(
        mutation: M,
        token: TaskToken?,
    ): Boolean
}
