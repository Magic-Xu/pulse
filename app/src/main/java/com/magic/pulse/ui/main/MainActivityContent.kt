package com.magic.pulse.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.magic.pulse.samples.common.ui.PulseSampleTopBar
import com.magic.pulse.samples.counter.CounterViewModel
import com.magic.pulse.samples.counter.ui.CounterScreen
import com.magic.pulse.samples.home.SampleDestination
import com.magic.pulse.samples.home.SamplesHomeScreen
import com.magic.pulse.samples.network.mvi.NetworkModelsViewModel
import com.magic.pulse.samples.network.ui.NetworkModelsScreen
import com.magic.pulse.ui.theme.PulseTheme

@Composable
fun MainActivityContent(
    counterViewModel: CounterViewModel,
    networkModelsViewModel: NetworkModelsViewModel,
) {
    PulseTheme {
        PulseSamplesApp(
            counterViewModel = counterViewModel,
            networkModelsViewModel = networkModelsViewModel,
        )
    }
}

@Composable
private fun PulseSamplesApp(
    counterViewModel: CounterViewModel,
    networkModelsViewModel: NetworkModelsViewModel,
) {
    var destination by rememberSaveable { mutableStateOf(SampleDestination.HOME.name) }
    val currentDestination = SampleDestination.valueOf(destination)
    val isHome = currentDestination == SampleDestination.HOME

    BackHandler(enabled = !isHome) {
        destination = SampleDestination.HOME.name
    }

    when (currentDestination) {
        SampleDestination.HOME -> {
            SamplesHomeScreen(
                onOpenCounter = { destination = SampleDestination.COUNTER.name },
                onOpenNetwork = { destination = SampleDestination.NETWORK.name },
            )
        }

        SampleDestination.COUNTER -> {
            SampleDetailScaffold(
                title = SampleDestination.COUNTER.title,
                subtitle = SampleDestination.COUNTER.subtitle,
                onBack = { destination = SampleDestination.HOME.name },
            ) { innerPadding ->
                CounterScreen(
                    viewModel = counterViewModel,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        SampleDestination.NETWORK -> {
            SampleDetailScaffold(
                title = SampleDestination.NETWORK.title,
                subtitle = SampleDestination.NETWORK.subtitle,
                onBack = { destination = SampleDestination.HOME.name },
            ) { innerPadding ->
                NetworkModelsScreen(
                    viewModel = networkModelsViewModel,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun SampleDetailScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PulseSampleTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
            )
        },
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    PulseTheme {
        SamplesHomeScreen(
            onOpenCounter = {},
            onOpenNetwork = {},
        )
    }
}
