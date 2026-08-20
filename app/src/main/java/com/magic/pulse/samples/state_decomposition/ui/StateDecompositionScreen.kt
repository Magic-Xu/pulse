package com.magic.pulse.samples.state_decomposition.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magic.mvicore.android.compose.ObserveUiEffects
import com.magic.mvicore.android.compose.collectStateAsStateWithLifecycle
import com.magic.pulse.samples.state_decomposition.mvi.StateDecompositionEffect
import com.magic.pulse.samples.state_decomposition.mvi.StateDecompositionUiIntent
import com.magic.pulse.samples.state_decomposition.mvi.StateDecompositionViewModel

@Composable
fun StateDecompositionScreen(
    viewModel: StateDecompositionViewModel,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
    val context = LocalContext.current

    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        when (effect) {
            is StateDecompositionEffect.ShowMessage -> {
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
        Text("大状态拆分示例：RootState = ImageDomain + VideoDomain")

        ImageDomainActions(
            isLoading = state.image.isLoading,
            selectedEffect = state.image.selectedEffect,
            onLoadModels = { viewModel.send(StateDecompositionUiIntent.LoadImageModelsClicked) },
            onSelectEffect = { effect ->
                viewModel.send(StateDecompositionUiIntent.SelectImageEffect(effect))
            },
        )

        HorizontalDivider()

        VideoDomainActions(
            isLoading = state.video.isLoading,
            selectedTask = state.video.selectedTask,
            onLoadModels = { viewModel.send(StateDecompositionUiIntent.LoadVideoModelsClicked) },
            onSelectTask = { task ->
                viewModel.send(StateDecompositionUiIntent.SelectVideoTask(task))
            },
        )

        HorizontalDivider()

        ImageDomainStateSection(state = state.image)
        VideoDomainStateSection(state = state.video)
    }
}
