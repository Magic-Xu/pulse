package com.magic.pulse.samples.syncconsumer

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore

object SimpleSyncConsumer {
    fun createStore(): DefaultPulseStore<CounterState, CounterIntent, CounterEffect> {
        return DefaultPulseStore(
            initialState = CounterState(0),
            reducer = PulseReducer { previous, input ->
                when (input) {
                    CounterIntent.Increment -> {
                        ReduceOutcome.Changed(previous.copy(value = previous.value + 1))
                    }
                }
            },
        )
    }
}

data class CounterState(val value: Int) : MviState

sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
}

sealed interface CounterEffect : UiEffect
