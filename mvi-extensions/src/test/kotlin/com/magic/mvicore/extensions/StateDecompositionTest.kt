package com.magic.mvicore.extensions

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class StateDecompositionTest {
    @Test
    fun `lens obeys get-put put-get and put-put laws without marker substate`() {
        val lens = stateLens<Root, PlainValue>(
            get = Root::value,
            set = { root, value -> root.copy(value = value) },
        )
        val root = Root(PlainValue(1), untouched = "stable")
        val first = PlainValue(2)
        val second = PlainValue(3)

        assertEquals(root, lens.set(root, lens.get(root)))
        assertEquals(first, lens.get(lens.set(root, first)))
        assertEquals(lens.set(root, second), lens.set(lens.set(root, first), second))
    }

    @Test
    fun `pulse substate route preserves sibling state and effects`() {
        val lens = valueLens()
        val reducer = pulseMutationReducer<Root, Mutation, Effect> {
            onSub<PlainValue, Mutation.Increment>(lens) { previous, _ ->
                subStateWithEffect(previous.copy(count = previous.count + 1), Effect.Done)
            }
            ignore<Mutation.Ignore>("not-applicable")
        }

        val result = assertIs<ReduceOutcome.Changed<Root, Effect>>(
            reducer.reduce(Root(PlainValue(1), "stable"), Mutation.Increment)
        )

        assertEquals(Root(PlainValue(2), "stable"), result.state)
        assertEquals(listOf(Effect.Done), result.uiEffects)
        assertIs<ReduceOutcome.Ignored>(
            reducer.reduce(result.state, Mutation.Ignore)
        )
    }

    @Test
    fun `unhandled mutation fails instead of becoming silent no-op`() {
        val reducer = pulseMutationReducer<Root, Mutation, Effect> {
            on<Mutation.Increment> { state, _ -> ReduceOutcome.Changed(state) }
        }

        val failure = assertFailsWith<IllegalStateException> {
            reducer.reduce(Root(PlainValue(0), "stable"), Mutation.Ignore)
        }

        assertEquals(true, failure.message.orEmpty().contains("explicit ignore"))
    }

    @Test
    fun `duplicate and overlapping handlers fail while builder is created`() {
        assertFailsWith<IllegalArgumentException> {
            pulseMutationReducer<Root, Mutation, Effect> {
                on<Mutation.Increment> { state, _ -> ReduceOutcome.Changed(state) }
                on<Mutation.Increment> { state, _ -> ReduceOutcome.Changed(state) }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            pulseMutationReducer<Root, Mutation, Effect> {
                on<Mutation> { state, _ -> ReduceOutcome.Changed(state) }
                ignore<Mutation.Ignore>("ignored")
            }
        }
    }

    @Test
    fun `one mutation matching unrelated interface routes fails instead of first match winning`() {
        val reducer = pulseMutationReducer<Root, IntersectionMutation, Effect> {
            on<LeftMutation> { state, _ -> ReduceOutcome.Changed(state) }
            on<RightMutation> { state, _ -> ReduceOutcome.Changed(state) }
        }

        val failure = assertFailsWith<IllegalStateException> {
            reducer.reduce(Root(PlainValue(0), "stable"), BothMutation)
        }

        assertEquals(true, failure.message.orEmpty().contains("multiple routes"))
    }

    private fun valueLens(): StateLens<Root, PlainValue> {
        return stateLens(
            get = Root::value,
            set = { root, value -> root.copy(value = value) },
        )
    }

    private data class Root(
        val value: PlainValue,
        val untouched: String,
    ) : MviState

    private data class PlainValue(val count: Int)

    private sealed interface Mutation : MviMutation {
        data object Increment : Mutation
        data object Ignore : Mutation
    }

    private sealed interface Effect : UiEffect {
        data object Done : Effect
    }

    private sealed interface IntersectionMutation : MviMutation

    private interface LeftMutation : IntersectionMutation

    private interface RightMutation : IntersectionMutation

    private data object BothMutation : LeftMutation, RightMutation
}
