package com.magic.pulse.samples.state_decomposition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magic.pulse.samples.state_decomposition.data.model.ImageEffect
import com.magic.pulse.samples.state_decomposition.data.model.VideoTask

@Composable
fun ImageDomainActions(
    isLoading: Boolean,
    selectedEffect: ImageEffect,
    onLoadModels: () -> Unit,
    onSelectEffect: (ImageEffect) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("图片域操作", style = MaterialTheme.typography.titleMedium)
        LoadingActionButton(
            text = "获取图片模型",
            isLoading = isLoading,
            onClick = onLoadModels,
            modifier = Modifier.fillMaxWidth(),
        )
        EffectSelector(
            selectedEffect = selectedEffect,
            onSelectEffect = onSelectEffect,
        )
    }
}

@Composable
fun VideoDomainActions(
    isLoading: Boolean,
    selectedTask: VideoTask,
    onLoadModels: () -> Unit,
    onSelectTask: (VideoTask) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("视频域操作", style = MaterialTheme.typography.titleMedium)
        LoadingActionButton(
            text = "获取视频模型",
            isLoading = isLoading,
            onClick = onLoadModels,
            modifier = Modifier.fillMaxWidth(),
        )
        TaskSelector(
            selectedTask = selectedTask,
            onSelectTask = onSelectTask,
        )
    }
}

@Composable
private fun EffectSelector(
    selectedEffect: ImageEffect,
    onSelectEffect: (ImageEffect) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("图片效果")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImageEffect.entries.forEach { effect ->
                OutlinedButton(
                    onClick = { onSelectEffect(effect) },
                    modifier = Modifier.weight(1f),
                ) {
                    val prefix = if (effect == selectedEffect) "✓ " else ""
                    Text(prefix + effect.label)
                }
            }
        }
    }
}

@Composable
private fun TaskSelector(
    selectedTask: VideoTask,
    onSelectTask: (VideoTask) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("视频任务")
        VideoTask.entries.forEach { task ->
            OutlinedButton(
                onClick = { onSelectTask(task) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val prefix = if (task == selectedTask) "✓ " else ""
                Text(prefix + task.label)
            }
        }
    }
}

@Composable
private fun LoadingActionButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
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
