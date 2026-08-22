package com.magic.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.magic.mvicore.android.pulseViewModel
import com.magic.pulse.samples.counter.CounterViewModel
import com.magic.pulse.samples.counter.createCounterViewModel
import com.magic.pulse.samples.network.mvi.NetworkModelsViewModel
import com.magic.pulse.samples.network.mvi.createNetworkModelsViewModel
import com.magic.pulse.samples.state_decomposition.mvi.StateDecompositionViewModel
import com.magic.pulse.samples.state_decomposition.mvi.createStateDecompositionViewModel
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicViewModel
import com.magic.pulse.samples.split_intent_basic.mvi.createSplitIntentBasicViewModel
import com.magic.pulse.ui.main.MainActivityContent

class MainActivity : ComponentActivity() {
    private val counterViewModel: CounterViewModel by lazy(LazyThreadSafetyMode.NONE) {
        pulseViewModel(
            owner = this,
            key = "pulse.counter",
            modelClass = CounterViewModel::class.java,
        ) { createCounterViewModel() }
    }
    private val splitIntentBasicViewModel: SplitIntentBasicViewModel by lazy(LazyThreadSafetyMode.NONE) {
        pulseViewModel(
            owner = this,
            key = "pulse.splitIntentBasic",
            modelClass = SplitIntentBasicViewModel::class.java,
        ) { createSplitIntentBasicViewModel() }
    }
    private val networkModelsViewModel: NetworkModelsViewModel by lazy(LazyThreadSafetyMode.NONE) {
        pulseViewModel(
            owner = this,
            key = "pulse.networkModels",
            modelClass = NetworkModelsViewModel::class.java,
        ) { createNetworkModelsViewModel() }
    }
    private val stateDecompositionViewModel: StateDecompositionViewModel by lazy(LazyThreadSafetyMode.NONE) {
        pulseViewModel(
            owner = this,
            key = "pulse.stateDecomposition",
            modelClass = StateDecompositionViewModel::class.java,
        ) { createStateDecompositionViewModel() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainActivityContent(
                counterViewModel = counterViewModel,
                splitIntentBasicViewModel = splitIntentBasicViewModel,
                networkModelsViewModel = networkModelsViewModel,
                stateDecompositionViewModel = stateDecompositionViewModel,
            )
        }
    }
}
