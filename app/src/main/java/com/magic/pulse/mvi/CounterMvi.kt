package com.magic.pulse.mvi

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.magic.mvicore.android.MviViewModel
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
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

private object CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> Next.just(previous.copy(count = previous.count + 1))
            CounterIntent.Decrease -> Next.just(previous.copy(count = previous.count - 1))
            CounterIntent.Reset -> Next.withEffect(CounterState(0), CounterEffect.ResetCompleted)
        }
    }
}

class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(
    initialState = CounterState(),
    reducer = CounterReducer,
)

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
        Text(text = "MVI Counter")
        Text(text = "Count = ${state.count}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            Button(onClick = { viewModel.dispatch(CounterIntent.Decrease) }) {
                Text("-")
            }
            Button(onClick = { viewModel.dispatch(CounterIntent.Increase) }) {
                Text("+")
            }
            Button(onClick = { viewModel.dispatch(CounterIntent.Reset) }) {
                Text("Reset")
            }
        }
    }
}
