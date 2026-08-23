package com.magic.mvicore.runtime

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken

/** Narrow task surface owned by a store; it exposes no coroutine scope or job. */
interface PulseTasks {
    val isClosed: Boolean

    fun launch(
        key: TaskKey,
        policy: TaskPolicy,
        block: suspend (TaskToken) -> Unit,
    ): TaskLaunchResult

    /**
     * Launches keyed work with correlation metadata for a possible task failure.
     *
     * The default implementation preserves source and binary compatibility for existing
     * [PulseTasks] implementations by delegating to the original launch contract.
     */
    fun launch(
        key: TaskKey,
        policy: TaskPolicy,
        failureContext: FailureContext,
        block: suspend (TaskToken) -> Unit,
    ): TaskLaunchResult = launch(key, policy, block)

    fun isCurrent(token: TaskToken): Boolean

    /** Returns false and reports a typed late-mutation diagnostic when the token is stale. */
    fun validate(token: TaskToken): Boolean

    fun cancel(key: TaskKey): Boolean

    /** Cancels every active or pending request and returns the number of keys invalidated. */
    fun cancelAll(): Int
}
