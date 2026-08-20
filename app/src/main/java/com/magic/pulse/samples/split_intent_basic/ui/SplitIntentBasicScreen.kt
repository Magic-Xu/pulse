package com.magic.pulse.samples.split_intent_basic.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magic.mvicore.android.compose.ObserveUiEffects
import com.magic.mvicore.android.compose.collectStateAsStateWithLifecycle
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicEffect
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicUiIntent
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicViewModel

@Composable
fun SplitIntentBasicScreen(
    viewModel: SplitIntentBasicViewModel,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
    val context = LocalContext.current

    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        when (effect) {
            is SplitIntentBasicEffect.ShowMessage -> {
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Basic path: direct reducer + DropWhileRunning task, no DSL.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SplitIntentBasicActionRow(
            isLoading = state.isLoading,
            loadingTarget = state.loadingTarget,
            onLoadImages = { viewModel.send(SplitIntentBasicUiIntent.LoadImageModelsClicked) },
            onLoadVideos = { viewModel.send(SplitIntentBasicUiIntent.LoadVideoModelsClicked) },
            onClear = { viewModel.send(SplitIntentBasicUiIntent.ClearAllClicked) },
        )
        Text("requestCount = ${state.requestCount}")
        Text("lastOperation = ${state.lastOperation}")
        BasicModelList(
            title = "Image Models",
            items = state.imageModels.map { "${it.id} | ${it.name}" },
        )
        BasicModelList(
            title = "Video Models",
            items = state.videoModels.map { "${it.id} | ${it.name}" },
        )
    }
}

@Composable
private fun BasicModelList(
    title: String,
    items: List<String>,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    if (items.isEmpty()) {
        Text("- empty")
        return
    }
    items.forEach { row ->
        Text("- $row")
    }
}
