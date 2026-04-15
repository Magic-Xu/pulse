package com.magic.pulse.samples.counter

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer

data class CounterState(val count: Int = 0) : MviState

sealed interface CounterIntent : MviIntent {
    data object Increase : CounterIntent
    data object Decrease : CounterIntent
    data object Reset : CounterIntent
}

sealed interface CounterEffect : MviEffect {
    data object ResetCompleted : CounterEffect
}

object CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> Next.just(previous.copy(count = previous.count + 1))
            CounterIntent.Decrease -> Next.just(previous.copy(count = previous.count - 1))
            CounterIntent.Reset -> Next.withEffect(CounterState(0), CounterEffect.ResetCompleted)
        }
    }
}
