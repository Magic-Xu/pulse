package com.magic.mvicore.contract

/** Lifecycle reason for rejecting an input before it can be processed. */
sealed interface RejectionReason {
    data object NotStarted : RejectionReason

    data object Closing : RejectionReason

    data object Closed : RejectionReason
}

/** Completion result returned by suspending transition submission. */
sealed interface TransitionResult<out S : MviState, out I : MviIntent, out E : UiEffect> {
    data class Completed<out S : MviState, out I : MviIntent, out E : UiEffect>(
        val frame: TransitionFrame<S, I, E>,
    ) : TransitionResult<S, I, E>

    data class Failed<out S : MviState, out I : MviIntent, out E : UiEffect>(
        val frame: TransitionFrame<S, I, E>,
        val failure: PulseFailure.ReducerFailure,
    ) : TransitionResult<S, I, E>

    data class Rejected(
        val reason: RejectionReason,
    ) : TransitionResult<Nothing, Nothing, Nothing>
}

/** Immediate admission result returned by non-suspending submission. */
sealed interface EnqueueResult {
    data class Enqueued(
        val requestId: Long,
    ) : EnqueueResult

    data object Full : EnqueueResult

    data class Rejected(
        val reason: RejectionReason,
    ) : EnqueueResult
}
