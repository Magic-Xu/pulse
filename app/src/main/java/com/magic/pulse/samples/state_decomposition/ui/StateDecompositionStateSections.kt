package com.magic.pulse.samples.state_decomposition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magic.pulse.samples.state_decomposition.mvi.ImageDomainState
import com.magic.pulse.samples.state_decomposition.mvi.VideoDomainState

@Composable
fun ImageDomainStateSection(
    state: ImageDomainState,
    modifier: Modifier = Modifier,
) {
    StateCard(
        title = "Image Domain State",
        lines = listOf(
            "isLoading = ${state.isLoading}",
            "selectedModelId = ${state.selectedModelId ?: "-"}",
            "selectedEffect = ${state.selectedEffect.label}",
            "stylePreset = ${state.stylePreset}",
            "depthStrength = ${state.depthStrength}",
            "facePriority = ${state.facePriority}",
            "guidanceScale = ${state.guidanceScale}",
            "lastSyncLabel = ${state.lastSyncLabel ?: "-"}",
        ),
        models = state.models.map { model ->
            "${model.id} | ${model.name} | max=${model.maxResolution} | effect=${model.recommendedEffect.label}"
        },
        modifier = modifier,
    )
}

@Composable
fun VideoDomainStateSection(
    state: VideoDomainState,
    modifier: Modifier = Modifier,
) {
    StateCard(
        title = "Video Domain State",
        lines = listOf(
            "isLoading = ${state.isLoading}",
            "selectedModelId = ${state.selectedModelId ?: "-"}",
            "selectedTask = ${state.selectedTask.label}",
            "stabilizationLevel = ${state.stabilizationLevel}",
            "interpolationFrames = ${state.interpolationFrames}",
            "outputFps = ${state.outputFps}",
            "clipLengthSeconds = ${state.clipLengthSeconds}",
            "autoSubtitle = ${state.autoSubtitle}",
            "lastSyncLabel = ${state.lastSyncLabel ?: "-"}",
        ),
        models = state.models.map { model ->
            "${model.id} | ${model.name} | ${model.maxFps}fps | ${model.maxDurationSeconds}s | task=${model.recommendedTask.label}"
        },
        modifier = modifier,
    )
}

@Composable
private fun StateCard(
    title: String,
    lines: List<String>,
    models: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            lines.forEach { line ->
                Text("- $line")
            }

            Text(
                text = "Models",
                style = MaterialTheme.typography.titleSmall,
            )
            if (models.isEmpty()) {
                Text("- 暂无数据")
            } else {
                models.forEach { row ->
                    Text("- $row")
                }
            }
        }
    }
}
