package com.magic.pulse.samples.counter.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
import com.magic.pulse.samples.counter.CounterEffect
import com.magic.pulse.samples.counter.CounterIntent
import com.magic.pulse.samples.counter.CounterViewModel

@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectStateAsState()
    val context = LocalContext.current

    viewModel.observeEffects { effect ->
        when (effect) {
            CounterEffect.ResetCompleted -> {
                Toast.makeText(context, "Counter reset", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CounterHeader(count = state.count)
        CounterActionButtons(
            onDecrease = { viewModel.dispatch(CounterIntent.Decrease) },
            onIncrease = { viewModel.dispatch(CounterIntent.Increase) },
            onReset = { viewModel.dispatch(CounterIntent.Reset) },
        )
    }
}
