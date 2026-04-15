package com.magic.pulse.samples.counter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun CounterActionButtons(
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        Button(onClick = onDecrease) { Text("-") }
        Button(onClick = onIncrease) { Text("+") }
        Button(onClick = onReset) { Text("Reset") }
    }
}
