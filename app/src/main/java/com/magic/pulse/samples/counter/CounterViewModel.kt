package com.magic.pulse.samples.counter

import com.magic.mvicore.android.PulseViewModel

typealias CounterViewModel = PulseViewModel<CounterState, CounterIntent, CounterEffect>

fun createCounterViewModel(): CounterViewModel = PulseViewModel(
    initialState = CounterState(),
    reducer = CounterReducer,
)
