package com.magic.pulse.samples.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magic.pulse.samples.common.ui.PulseSampleTopBar

@Composable
fun SamplesHomeScreen(
    onOpenCounter: () -> Unit,
    onOpenNetwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            PulseSampleTopBar(
                title = SampleDestination.HOME.title,
                subtitle = SampleDestination.HOME.subtitle,
            )
        },
    ) { innerPadding ->
        SamplesHomeContent(
            paddingValues = innerPadding,
            onOpenCounter = onOpenCounter,
            onOpenNetwork = onOpenNetwork,
        )
    }
}

@Composable
private fun SamplesHomeContent(
    paddingValues: PaddingValues,
    onOpenCounter: () -> Unit,
    onOpenNetwork: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Choose a sample:")
        Button(
            onClick = onOpenCounter,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Counter")
        }
        Button(
            onClick = onOpenNetwork,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Network Request")
        }
    }
}
