package com.magic.mvicore.contract

/**
 * Explicit result of one Pulse reducer invocation.
 *
 * The runtime normalizes [Changed] to [Unchanged] when the candidate state is
 * equal to the current state. [Ignored] never carries UI effects.
 */
sealed interface ReduceOutcome<out S : MviState, out E : UiEffect> {
    val uiEffects: List<E>

    class Changed<out S : MviState, out E : UiEffect>(
        val state: S,
        uiEffects: Iterable<E> = emptyList(),
    ) : ReduceOutcome<S, E> {
        override val uiEffects: List<E> = uiEffects.toList()

        override fun equals(other: Any?): Boolean {
            return other is Changed<*, *> &&
                state == other.state &&
                uiEffects == other.uiEffects
        }

        override fun hashCode(): Int = 31 * state.hashCode() + uiEffects.hashCode()

        override fun toString(): String = "Changed(state=$state, uiEffects=$uiEffects)"
    }

    class Unchanged<out E : UiEffect>(
        uiEffects: Iterable<E> = emptyList(),
    ) : ReduceOutcome<Nothing, E> {
        override val uiEffects: List<E> = uiEffects.toList()

        override fun equals(other: Any?): Boolean {
            return other is Unchanged<*> && uiEffects == other.uiEffects
        }

        override fun hashCode(): Int = uiEffects.hashCode()

        override fun toString(): String = "Unchanged(uiEffects=$uiEffects)"
    }

    data class Ignored(
        val reason: String,
    ) : ReduceOutcome<Nothing, Nothing> {
        override val uiEffects: List<Nothing> = emptyList()
    }
}
