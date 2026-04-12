package com.magic.mvicore.contract

/** Marker type for user actions entering the Store. */
interface MviIntent

/** Marker type for immutable UI/business state. */
interface MviState

/** Marker type for one-off events (navigation, toast, etc.). */
interface MviEffect

/**
 * Output of a reducer step:
 * 1) the next state
 * 2) optional one-off effects
 */
data class Next<S : MviState, E : MviEffect>(
    val state: S,
    val effects: List<E> = emptyList(),
) {
    companion object {
        fun <S : MviState, E : MviEffect> just(state: S): Next<S, E> = Next(state = state)

        fun <S : MviState, E : MviEffect> withEffect(state: S, effect: E): Next<S, E> =
            Next(state = state, effects = listOf(effect))

        fun <S : MviState, E : MviEffect> withEffects(
            state: S,
            effects: Iterable<E>,
        ): Next<S, E> = Next(state = state, effects = effects.toList())
    }
}
