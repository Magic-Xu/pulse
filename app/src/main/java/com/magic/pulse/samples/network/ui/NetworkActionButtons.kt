package com.magic.pulse.samples.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magic.pulse.R
import com.magic.pulse.samples.network.mvi.LoadingTarget

@Composable
fun NetworkActionButtons(
    isLoading: Boolean,
    loadingTarget: LoadingTarget?,
    onLoadImages: () -> Unit,
    onLoadVideos: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingActionButton(
            text = stringResource(R.string.network_load_images),
            isLoading = isLoading && loadingTarget == LoadingTarget.IMAGE,
            enabled = !isLoading,
            onClick = onLoadImages,
            modifier = Modifier.weight(1f),
        )
        LoadingActionButton(
            text = stringResource(R.string.network_load_videos),
            isLoading = isLoading && loadingTarget == LoadingTarget.VIDEO,
            enabled = !isLoading,
            onClick = onLoadVideos,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoadingActionButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(text)
        }
    }
}
