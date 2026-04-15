package com.magic.pulse.samples.counter

import com.magic.mvicore.android.MviViewModel

class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(
    initialState = CounterState(),
    reducer = CounterReducer,
)
