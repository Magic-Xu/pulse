package com.magic.mvicore.contract

/** Stable identity used to coordinate related process-local work. */
@JvmInline
value class TaskKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "TaskKey must not be blank." }
    }

    override fun toString(): String = value
}

/** Admission and replacement policy for work sharing a [TaskKey]. */
sealed interface TaskPolicy {
    /** Cancel and invalidate existing work before starting the replacement. */
    data object Latest : TaskPolicy

    /** Reject new work while the key already has active work. */
    data object DropWhileRunning : TaskPolicy

    /** Run admitted work serially in FIFO order with at most [capacity] pending requests. */
    data class Queue(
        val capacity: Int,
    ) : TaskPolicy {
        init {
            require(capacity > 0) { "Queue capacity must be greater than zero." }
        }
    }

    /** Run at most [maxConcurrency] requests concurrently; excess requests are rejected. */
    data class Parallel(
        val maxConcurrency: Int,
    ) : TaskPolicy {
        init {
            require(maxConcurrency > 0) { "Parallel maxConcurrency must be greater than zero." }
        }
    }

    /** While work is active, retain only the most recently admitted pending block. */
    data object Conflate : TaskPolicy
}

/** Read-only identity of a runtime-created task generation. */
interface TaskToken {
    val key: TaskKey

    val value: Long
}

/** Why an admitted task request was superseded before it completed. */
enum class TaskReplacementReason {
    LATEST,
    CONFLATED,
    POLICY_CHANGED,
}

/** Terminal outcome of one admitted task request. */
sealed interface TaskOutcome {
    data object Completed : TaskOutcome

    data class Replaced(
        val reason: TaskReplacementReason,
    ) : TaskOutcome

    data object Cancelled : TaskOutcome

    data object Closed : TaskOutcome

    data class Failed(
        val cause: Exception,
    ) : TaskOutcome
}

/** Narrow observation surface for one admitted task request. */
interface TaskHandle {
    val requestId: Long

    /** Returns the terminal outcome; a fatal non-[Exception] throwable is rethrown unchanged. */
    suspend fun awaitOutcome(): TaskOutcome
}

/** Immediate admission result; accepted work exposes its separately observable final outcome. */
sealed interface TaskLaunchResult {
    data class Accepted(
        val handle: TaskHandle,
    ) : TaskLaunchResult

    data object DroppedWhileRunning : TaskLaunchResult

    data class QueueFull(
        val capacity: Int,
    ) : TaskLaunchResult

    data class ParallelLimitReached(
        val maxConcurrency: Int,
    ) : TaskLaunchResult

    data object Closed : TaskLaunchResult
}
