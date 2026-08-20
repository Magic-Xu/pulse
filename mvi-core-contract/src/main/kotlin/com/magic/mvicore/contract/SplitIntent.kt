package com.magic.mvicore.contract

/** Marker type for user-side intents. */
interface MviUiIntent : MviIntent

/** Marker type for internal state mutations. */
interface MviMutation : MviIntent

/**
 * Internal message channel used by split-intent architecture.
 * - Ui: external input (click, retry, refresh)
 * - Mutation: reducer-consumable state mutation
 */
@Deprecated(
    message = "Use PulseSplitStoreViewModel; its public surface does not expose mutation inputs.",
)
sealed interface SplitIntent<out UI : MviUiIntent, out M : MviMutation> : MviIntent {
    data class Ui<UI : MviUiIntent>(
        val value: UI,
    ) : SplitIntent<UI, Nothing>

    data class Mutation<M : MviMutation>(
        val value: M,
    ) : SplitIntent<Nothing, M>
}

/** Reducer for mutation-only state transitions. */
@Deprecated(
    message = "Use PulseMutationReducer and return an explicit ReduceOutcome.",
    replaceWith = ReplaceWith("PulseMutationReducer<S, M, E>"),
)
fun interface MutationReducer<S : MviState, M : MviMutation, E : MviEffect> {
    fun reduce(previous: S, mutation: M): Next<S, E>
}

/** Adapter that ignores Ui messages and reduces Mutation messages. */
@Deprecated(
    message = "Use PulseSplitStoreViewModel; the mutation lane is internal there.",
)
class SplitIntentReducer<S : MviState, UI : MviUiIntent, M : MviMutation, E : MviEffect>(
    private val mutationReducer: MutationReducer<S, M, E>,
) : Reducer<S, SplitIntent<UI, M>, E> {

    override fun reduce(previous: S, intent: SplitIntent<UI, M>): Next<S, E> {
        return when (intent) {
            is SplitIntent.Ui -> Next.just(previous)
            is SplitIntent.Mutation -> mutationReducer.reduce(previous, intent.value)
        }
    }
}
