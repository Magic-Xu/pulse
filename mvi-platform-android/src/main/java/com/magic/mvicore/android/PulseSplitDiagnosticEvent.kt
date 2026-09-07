package com.magic.mvicore.android

/** Payload-free correlation and timing records for a single Split Store. */
sealed interface PulseSplitDiagnosticEvent {
    val sequenceId: Long
    val storeId: String
    val originIntentId: Long

    /**
     * One executor completion, or cancellation after its Core input frame was observed.
     * [executorStartedAtNanos] is null when queued work was cancelled before execution.
     */
    data class ExecutionCompleted(
        override val sequenceId: Long,
        override val storeId: String,
        override val originIntentId: Long,
        val inputType: String,
        val submittedAtNanos: Long,
        val admittedAtNanos: Long,
        val inputFrameCompletedAtNanos: Long,
        val executorStartedAtNanos: Long?,
        val completedAtNanos: Long,
        val outcome: PulseIntentExecutionStatus,
    ) : PulseSplitDiagnosticEvent {
        val admissionWaitNanos: Long
            get() = (admittedAtNanos - submittedAtNanos).coerceAtLeast(0)
        val executorWaitNanos: Long
            get() = ((executorStartedAtNanos ?: completedAtNanos) - inputFrameCompletedAtNanos)
                .coerceAtLeast(0)
        val executionDurationNanos: Long
            get() = executorStartedAtNanos?.let { (completedAtNanos - it).coerceAtLeast(0) } ?: 0
        val totalDurationNanos: Long
            get() = (completedAtNanos - submittedAtNanos).coerceAtLeast(0)
    }

    /**
     * Connects a direct or keyed-task mutation to its originating UI intent and Core frame.
     * [mutationRequestId] is null if lifecycle/token rejection prevented a Core frame.
     * The task key, user payload, ignored reason, and exception message are not copied into this event.
     */
    data class MutationCompleted(
        override val sequenceId: Long,
        override val storeId: String,
        override val originIntentId: Long,
        val mutationRequestId: Long?,
        val mutationType: String,
        val taskToken: Long?,
        val outcome: PulseMutationStatus,
    ) : PulseSplitDiagnosticEvent
}

enum class PulseIntentExecutionStatus { COMPLETED, IGNORED, FAILED, CANCELLED, FATAL }

enum class PulseMutationStatus { CHANGED, UNCHANGED, IGNORED, FAILED, REJECTED, STALE_TASK }

internal fun PulseMutationResult.diagnosticStatus(): PulseMutationStatus = when (this) {
    PulseMutationResult.Changed -> PulseMutationStatus.CHANGED
    PulseMutationResult.Unchanged -> PulseMutationStatus.UNCHANGED
    is PulseMutationResult.Ignored -> PulseMutationStatus.IGNORED
    is PulseMutationResult.Failed -> PulseMutationStatus.FAILED
    is PulseMutationResult.Rejected -> PulseMutationStatus.REJECTED
    PulseMutationResult.StaleTask -> PulseMutationStatus.STALE_TASK
}
