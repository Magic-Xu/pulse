package com.magic.pulse.compat.v03

import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.runtime.PulseTasks

/** Compiled only against 0.3.0; intentionally implements exactly the original abstract surface. */
class LegacyPulseTasks : PulseTasks {
    var launchCount: Int = 0
        private set

    override val isClosed: Boolean = false

    override fun launch(
        key: TaskKey,
        policy: TaskPolicy,
        block: suspend (TaskToken) -> Unit,
    ): TaskLaunchResult {
        launchCount += 1
        return TaskLaunchResult.DroppedWhileRunning
    }

    override fun isCurrent(token: TaskToken): Boolean = false

    override fun validate(token: TaskToken): Boolean = false

    override fun cancel(key: TaskKey): Boolean = false

    override fun cancelAll(): Int = 0
}
