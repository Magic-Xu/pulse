package com.magic.mvicore.android

import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.RejectionReason

/**
 * Completed outcome of one executor or keyed-task mutation.
 *
 * [Changed] and [Unchanged] are committed reducer decisions, including any emitted UI effects.
 * [Ignored] is a business decision and is distinct from a stale task or a closed Store. Cancellation
 * and fatal JVM errors propagate rather than being converted into results.
 */
sealed interface PulseMutationResult {
    data object Changed : PulseMutationResult

    data object Unchanged : PulseMutationResult

    data class Ignored(val reason: String) : PulseMutationResult

    data class Failed(val failure: PulseFailure.ReducerFailure) : PulseMutationResult

    data class Rejected(val reason: RejectionReason) : PulseMutationResult

    /** The task token was replaced or cancelled before its mutation could commit. */
    data object StaleTask : PulseMutationResult
}

internal fun PulseMutationResult.toLegacyBoolean(): Boolean = when (this) {
    PulseMutationResult.Changed,
    PulseMutationResult.Unchanged,
    is PulseMutationResult.Ignored -> true
    is PulseMutationResult.Failed,
    is PulseMutationResult.Rejected,
    PulseMutationResult.StaleTask -> false
}
