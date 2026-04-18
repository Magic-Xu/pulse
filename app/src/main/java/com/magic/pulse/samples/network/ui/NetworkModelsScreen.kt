package com.magic.pulse.samples.network.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
import com.magic.pulse.samples.network.mvi.NetworkModelsEffect
import com.magic.pulse.samples.network.mvi.NetworkModelsUiIntent
import com.magic.pulse.samples.network.mvi.NetworkModelsViewModel

@Composable
fun NetworkModelsScreen(
    viewModel: NetworkModelsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectStateAsState()
    val context = LocalContext.current

    viewModel.observeEffects { effect ->
        when (effect) {
            is NetworkModelsEffect.ShowMessage -> {
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Repository -> RemoteDataSource -> RemoteService")
        NetworkActionButtons(
            isLoading = state.isLoading,
            loadingTarget = state.loadingTarget,
            onLoadImages = { viewModel.send(NetworkModelsUiIntent.LoadImageModelsClicked) },
            onLoadVideos = { viewModel.send(NetworkModelsUiIntent.LoadVideoModelsClicked) },
        )

        state.lastUpdatedLabel?.let { label ->
            Text("状态: $label")
        }

        ModelListSection(
            title = "图片模型列表",
            emptyText = "暂无数据",
            items = state.imageModels.map { model ->
                "${model.id} | ${model.name} | ${model.url}"
            },
        )
        ModelListSection(
            title = "视频模型列表",
            emptyText = "暂无数据",
            items = state.videoModels.map { model ->
                "${model.id} | ${model.name} | ${model.durationSeconds}s"
            },
        )
    }
}
