package com.magic.mvicore.extensions

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import kotlin.reflect.KClass

/** Bidirectional value lens. Sub-state values do not need to implement an MVI marker. */
data class StateLens<ROOT, SUB>(
    val get: (ROOT) -> SUB,
    val set: (ROOT, SUB) -> ROOT,
) {
    fun modify(
        root: ROOT,
        transform: (SUB) -> SUB,
    ): ROOT = set(root, transform(get(root)))
}

fun <ROOT, SUB> stateLens(
    get: (ROOT) -> SUB,
    set: (ROOT, SUB) -> ROOT,
): StateLens<ROOT, SUB> = StateLens(get, set)

fun <ROOT, SUB> ROOT.updateSubState(
    lens: StateLens<ROOT, SUB>,
    transform: (SUB) -> SUB,
): ROOT = lens.modify(this, transform)

/** Marker-free local reducer result lifted into a root-state reducer by [StateLens]. */
class SubStateNext<out SUB, out E : UiEffect>(
    val state: SUB,
    effects: Iterable<E> = emptyList(),
) {
    val effects: List<E> = effects.toList()
}

fun <SUB, E : UiEffect> subStateJust(state: SUB): SubStateNext<SUB, E> {
    return SubStateNext(state)
}

fun <SUB, E : UiEffect> subStateWithEffect(
    state: SUB,
    effect: E,
): SubStateNext<SUB, E> = SubStateNext(state, listOf(effect))

fun <SUB, E : UiEffect> subStateWithEffects(
    state: SUB,
    effects: Iterable<E>,
): SubStateNext<SUB, E> = SubStateNext(state, effects)

/** Reducer DSL with explicit Changed/Unchanged/Ignored outcomes. */
class PulseMutationReducerBuilder<S : MviState, M : MviMutation, E : UiEffect>
@PublishedApi internal constructor() {
    @PublishedApi
    internal val routes = MutationRouteTable<S, M, ReduceOutcome<S, E>>()

    inline fun <reified Handled : M> on(
        noinline block: (S, Handled) -> ReduceOutcome<S, E>,
    ) {
        routes.register(Handled::class) { state, mutation ->
            block(state, mutation as Handled)
        }
    }

    inline fun <SUB, reified Handled : M> onSub(
        lens: StateLens<S, SUB>,
        noinline block: (SUB, Handled) -> SubStateNext<SUB, E>,
    ) {
        routes.register(Handled::class) { root, mutation ->
            val local = block(lens.get(root), mutation as Handled)
            ReduceOutcome.Changed(
                state = lens.set(root, local.state),
                uiEffects = local.effects,
            )
        }
    }

    inline fun <reified Handled : M> ignore(reason: String) {
        require(reason.isNotBlank()) { "Ignored mutation reason must not be blank." }
        routes.register(Handled::class) { _, _ -> ReduceOutcome.Ignored(reason) }
    }

    @PublishedApi
    internal fun build(): PulseMutationReducer<S, M, E> {
        return PulseMutationReducer { state, mutation -> routes.reduce(state, mutation) }
    }
}

/** Builds a fail-fast mutation reducer with duplicate-route validation. */
fun <S : MviState, M : MviMutation, E : UiEffect> pulseMutationReducer(
    block: PulseMutationReducerBuilder<S, M, E>.() -> Unit,
): PulseMutationReducer<S, M, E> {
    return PulseMutationReducerBuilder<S, M, E>().apply(block).build()
}

@PublishedApi
internal class MutationRouteTable<S : MviState, M : MviMutation, R> {
    private val routes = mutableListOf<Route<S, M, R>>()

    fun register(
        mutationType: KClass<out M>,
        reducer: (S, M) -> R,
    ) {
        val conflict = routes.firstOrNull { existing ->
            existing.mutationType.java.isAssignableFrom(mutationType.java) ||
                mutationType.java.isAssignableFrom(existing.mutationType.java)
        }
        require(conflict == null) {
            "Mutation route ${mutationType.qualifiedName} overlaps already registered " +
                "route ${conflict?.mutationType?.qualifiedName}."
        }
        routes += Route(mutationType, reducer)
    }

    fun reduce(
        state: S,
        mutation: M,
    ): R {
        val matches = routes.filter { it.mutationType.isInstance(mutation) }
        val route = matches.singleOrNull()
        if (route == null) {
            if (matches.size > 1) {
                error(
                    "Mutation ${(mutation::class.qualifiedName ?: mutation.javaClass.name)} " +
                        "matches multiple routes: " +
                        matches.joinToString { it.mutationType.qualifiedName.orEmpty() }
                )
            }
            error(
                "No mutation route registered for " +
                    (mutation::class.qualifiedName ?: mutation.javaClass.name) +
                    ". Register a handler or an explicit ignore route."
            )
        }
        return route.reducer(state, mutation)
    }

    private class Route<S : MviState, M : MviMutation, R>(
        val mutationType: KClass<out M>,
        val reducer: (S, M) -> R,
    )
}
