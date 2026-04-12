package com.magic.mvicore.contract

fun main() {
    reducerShouldProducePredictableStateEvolution()
    nextWithEffectsShouldCopyInputIterable()
}

private fun reducerShouldProducePredictableStateEvolution() {
    val reducer = CounterReducer()
    var current = CounterState(0)
    val emittedEffects = mutableListOf<CounterEffect>()

    val intents = listOf(
        CounterIntent.Increment,
        CounterIntent.Increment,
        CounterIntent.Decrement,
        CounterIntent.Reset,
    )

    intents.forEach { intent ->
        val next = reducer.reduce(current, intent)
        current = next.state
        emittedEffects += next.effects
    }

    check(current == CounterState(0)) {
        "Expected final state CounterState(0), actual: $current"
    }
    check(emittedEffects == listOf(CounterEffect.WasReset)) {
        "Expected effect [WasReset], actual: $emittedEffects"
    }
}

private fun nextWithEffectsShouldCopyInputIterable() {
    val source = mutableListOf(CounterEffect.WasReset)
    val next = Next.withEffects(CounterState(0), source)
    source.clear()

    check(next.effects == listOf(CounterEffect.WasReset)) {
        "Next.effects should be an immutable snapshot copy."
    }
}

private data class CounterState(val count: Int) : MviState

private sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
    data object Reset : CounterIntent
}

private sealed interface CounterEffect : MviEffect {
    data object WasReset : CounterEffect
}

private class CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increment -> Next.just(previous.copy(count = previous.count + 1))
            CounterIntent.Decrement -> Next.just(previous.copy(count = previous.count - 1))
            CounterIntent.Reset -> Next.withEffect(CounterState(0), CounterEffect.WasReset)
        }
    }
}
