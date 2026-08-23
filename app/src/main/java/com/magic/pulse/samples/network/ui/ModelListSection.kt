package com.magic.pulse.samples.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magic.pulse.R

@Composable
fun ModelListSection(
    title: String,
    emptyText: String,
    items: List<String>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (items.isEmpty()) {
            Text(stringResource(R.string.sample_list_item, emptyText))
        } else {
            items.forEach { item ->
                Text(stringResource(R.string.sample_list_item, item))
            }
        }
    }
}
