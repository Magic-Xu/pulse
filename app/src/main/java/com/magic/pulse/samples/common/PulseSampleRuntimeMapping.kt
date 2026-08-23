package com.magic.pulse.samples.common

import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.TaskLaunchResult

internal enum class SampleIngressFailure {
    CAPACITY_REACHED,
    SCREEN_UNAVAILABLE,
}

internal fun EnqueueResult.toSampleIngressFailure(): SampleIngressFailure? {
    return when (this) {
        is EnqueueResult.Enqueued -> null
        EnqueueResult.Full -> SampleIngressFailure.CAPACITY_REACHED
        is EnqueueResult.Rejected -> SampleIngressFailure.SCREEN_UNAVAILABLE
    }
}

/** Maps task admission to the executor decision without claiming the task itself has completed. */
internal fun TaskLaunchResult.toSampleExecutionDecision(): PulseIntentExecutionDecision {
    return when (this) {
        is TaskLaunchResult.Accepted -> PulseIntentExecutionDecision.Completed
        TaskLaunchResult.DroppedWhileRunning -> {
            PulseIntentExecutionDecision.Ignored("task-dropped-while-running")
        }

        is TaskLaunchResult.QueueFull -> {
            PulseIntentExecutionDecision.Ignored("task-queue-full(capacity=$capacity)")
        }

        is TaskLaunchResult.ParallelLimitReached -> {
            PulseIntentExecutionDecision.Ignored(
                "task-parallel-limit-reached(maxConcurrency=$maxConcurrency)"
            )
        }

        TaskLaunchResult.Closed -> PulseIntentExecutionDecision.Ignored("task-registry-closed")
    }
}
