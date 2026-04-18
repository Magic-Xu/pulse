package com.magic.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.magic.pulse.samples.counter.CounterViewModel
import com.magic.pulse.samples.counter.createCounterViewModel
import com.magic.pulse.samples.network.mvi.NetworkModelsViewModel
import com.magic.pulse.samples.network.mvi.createNetworkModelsViewModel
import com.magic.pulse.ui.main.MainActivityContent
import com.magic.pulse.viewmodel.keyedPulseViewModel

class MainActivity : ComponentActivity() {
    private val counterViewModel: CounterViewModel by lazy(LazyThreadSafetyMode.NONE) {
        keyedPulseViewModel(key = "pulse.counter") { createCounterViewModel() }
    }
    private val networkModelsViewModel: NetworkModelsViewModel by lazy(LazyThreadSafetyMode.NONE) {
        keyedPulseViewModel(key = "pulse.networkModels") { createNetworkModelsViewModel() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainActivityContent(
                counterViewModel = counterViewModel,
                networkModelsViewModel = networkModelsViewModel,
            )
        }
    }
}
