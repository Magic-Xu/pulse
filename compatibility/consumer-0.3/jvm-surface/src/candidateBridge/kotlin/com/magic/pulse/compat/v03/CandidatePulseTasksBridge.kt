package com.magic.pulse.compat.v03

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.runtime.PulseTasks

/** Candidate-only caller proving the new default method links to an old third-party implementation. */
fun main() {
    val implementation = LegacyPulseTasks()
    val tasks: PulseTasks = implementation
    val result = tasks.launch(
        key = TaskKey("compat-v03"),
        policy = TaskPolicy.Latest,
        failureContext = FailureContext(
            requestId = 7L,
            component = "compat-v03-bridge",
        ),
    ) { error("The legacy fixture deliberately rejects this launch.") }

    check(result == TaskLaunchResult.DroppedWhileRunning)
    check(implementation.launchCount == 1)
}
