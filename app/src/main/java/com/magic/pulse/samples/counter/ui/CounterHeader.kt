package com.magic.pulse.samples.counter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CounterHeader(count: Int) {
    Text(
        text = "MVI Counter",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "Count = $count",
        style = MaterialTheme.typography.titleLarge,
    )
}
