package com.magic.mvicore.contract

/** Normalized result recorded for every processed transition frame. */
sealed interface TransitionOutcome {
    data object Changed : TransitionOutcome

    data object Unchanged : TransitionOutcome

    data class Ignored(
        val reason: String,
    ) : TransitionOutcome

    data object ReducerFailed : TransitionOutcome
}

/** A UI effect correlated with the transition frame that produced it. */
data class EffectEnvelope<out E : UiEffect>(
    val effectId: Long,
    val requestId: Long,
    val sequenceId: Long,
    val stateRevision: Long,
    val index: Int,
    val payload: E,
)

/**
 * Immutable, completed record of one processed input.
 *
 * [uiEffects] is defensively copied so a reducer-owned collection cannot
 * mutate an already published frame.
 */
class TransitionFrame<out S : MviState, out I : MviIntent, out E : UiEffect>(
    val requestId: Long,
    val sequenceId: Long,
    val stateRevision: Long,
    val input: I,
    val stateBefore: S,
    val stateAfter: S,
    val outcome: TransitionOutcome,
    uiEffects: Iterable<EffectEnvelope<E>> = emptyList(),
    val startedAtNanos: Long,
    val completedAtNanos: Long,
    val dispatcher: String,
    val reducerFailure: PulseFailure.ReducerFailure? = null,
) {
    val uiEffects: List<EffectEnvelope<E>> = uiEffects.toList()

    override fun equals(other: Any?): Boolean {
        return other is TransitionFrame<*, *, *> &&
            requestId == other.requestId &&
            sequenceId == other.sequenceId &&
            stateRevision == other.stateRevision &&
            input == other.input &&
            stateBefore == other.stateBefore &&
            stateAfter == other.stateAfter &&
            outcome == other.outcome &&
            uiEffects == other.uiEffects &&
            startedAtNanos == other.startedAtNanos &&
            completedAtNanos == other.completedAtNanos &&
            dispatcher == other.dispatcher &&
            reducerFailure == other.reducerFailure
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + sequenceId.hashCode()
        result = 31 * result + stateRevision.hashCode()
        result = 31 * result + input.hashCode()
        result = 31 * result + stateBefore.hashCode()
        result = 31 * result + stateAfter.hashCode()
        result = 31 * result + outcome.hashCode()
        result = 31 * result + uiEffects.hashCode()
        result = 31 * result + startedAtNanos.hashCode()
        result = 31 * result + completedAtNanos.hashCode()
        result = 31 * result + dispatcher.hashCode()
        result = 31 * result + (reducerFailure?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "TransitionFrame(" +
            "requestId=$requestId, " +
            "sequenceId=$sequenceId, " +
            "stateRevision=$stateRevision, " +
            "input=$input, " +
            "stateBefore=$stateBefore, " +
            "stateAfter=$stateAfter, " +
            "outcome=$outcome, " +
            "uiEffects=$uiEffects, " +
            "startedAtNanos=$startedAtNanos, " +
            "completedAtNanos=$completedAtNanos, " +
            "dispatcher='$dispatcher', " +
            "reducerFailure=$reducerFailure)"
    }
}
