package com.magic.mvicore.android

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.runtime.PulseTasks

/** Executor-only context. UI code never receives this mutation capability. */
class PulseIntentContext<S : MviState, M : MviMutation> internal constructor(
    private val stateProvider: () -> S,
    private val mutationDispatcher: MutationDispatcher<M>,
    private val tasks: PulseTasks,
    private val lifecycleActive: () -> Boolean,
) {
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
        return tasks.launch(key, policy) { token ->
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
    )

    companion object {
        fun <S : MviState, UI : MviUiIntent, M : MviMutation> noop(): PulseUiIntentExecutor<S, UI, M> {
            return PulseUiIntentExecutor { _, _ -> }
        }
    }
}

internal fun interface MutationDispatcher<M : MviMutation> {
    suspend fun dispatch(
        mutation: M,
        token: TaskToken?,
    ): Boolean
}
