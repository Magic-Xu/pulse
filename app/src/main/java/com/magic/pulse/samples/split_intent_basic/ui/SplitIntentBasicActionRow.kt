package com.magic.pulse.samples.split_intent_basic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magic.pulse.samples.split_intent_basic.mvi.BasicLoadingTarget

@Composable
fun SplitIntentBasicActionRow(
    isLoading: Boolean,
    loadingTarget: BasicLoadingTarget?,
    onLoadImages: () -> Unit,
    onLoadVideos: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LoadingButton(
            text = "Load Images",
            loading = isLoading && loadingTarget == BasicLoadingTarget.IMAGE,
            enabled = !isLoading,
            onClick = onLoadImages,
            modifier = Modifier.weight(1f),
        )
        LoadingButton(
            text = "Load Videos",
            loading = isLoading && loadingTarget == BasicLoadingTarget.VIDEO,
            enabled = !isLoading,
            onClick = onLoadVideos,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedButton(
        onClick = onClear,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Clear Result")
    }
}

@Composable
private fun LoadingButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(text)
        }
    }
}
