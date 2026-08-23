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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magic.mvicore.android.compose.ObserveUiEffects
import com.magic.mvicore.android.compose.collectStateAsStateWithLifecycle
import com.magic.mvicore.contract.EnqueueResult
import com.magic.pulse.R
import com.magic.pulse.samples.common.SampleIngressFailure
import com.magic.pulse.samples.common.toSampleIngressFailure
import com.magic.pulse.samples.common.ui.SampleIngressFailureText
import com.magic.pulse.samples.network.mvi.NetworkLoadError
import com.magic.pulse.samples.network.mvi.NetworkModelsEffect
import com.magic.pulse.samples.network.mvi.NetworkModelsUpdate
import com.magic.pulse.samples.network.mvi.NetworkModelsUiIntent
import com.magic.pulse.samples.network.mvi.NetworkModelsViewModel

@Composable
fun NetworkModelsScreen(
    viewModel: NetworkModelsViewModel,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
    val context = LocalContext.current
    val resources = LocalResources.current
    var ingressFailure by remember { mutableStateOf<SampleIngressFailure?>(null) }
    val ingress = remember(viewModel) {
        viewModel.callbackIngress { _, result ->
            ingressFailure = result.toSampleIngressFailure()
        }
    }
    val submitIntent: (NetworkModelsUiIntent) -> Unit = { intent ->
        if (ingress.submit(intent) is EnqueueResult.Enqueued) ingressFailure = null
    }

    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        when (effect) {
            is NetworkModelsEffect.ShowLoadError -> {
                Toast.makeText(
                    context,
                    resources.getString(effect.error.messageResource()),
                    Toast.LENGTH_SHORT,
                ).show()
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
        Text(stringResource(R.string.network_architecture_path))
        NetworkActionButtons(
            isLoading = state.isLoading,
            loadingTarget = state.loadingTarget,
            onLoadImages = { submitIntent(NetworkModelsUiIntent.LoadImageModelsClicked) },
            onLoadVideos = { submitIntent(NetworkModelsUiIntent.LoadVideoModelsClicked) },
        )
        ingressFailure?.let { failure ->
            SampleIngressFailureText(failure)
        }

        state.lastUpdated?.let { update ->
            Text(
                stringResource(
                    R.string.network_status,
                    stringResource(update.labelResource()),
                )
            )
        }

        ModelListSection(
            title = stringResource(R.string.network_image_models_title),
            emptyText = stringResource(R.string.network_empty_models),
            items = state.imageModels.map { model ->
                resources.getString(
                    R.string.network_image_model_item,
                    model.id,
                    model.name,
                    model.url,
                )
            },
        )
        ModelListSection(
            title = stringResource(R.string.network_video_models_title),
            emptyText = stringResource(R.string.network_empty_models),
            items = state.videoModels.map { model ->
                resources.getString(
                    R.string.network_video_model_item,
                    model.id,
                    model.name,
                    model.durationSeconds,
                )
            },
        )
    }
}

private fun NetworkLoadError.messageResource(): Int {
    return when (this) {
        NetworkLoadError.IMAGE_MODELS_UNAVAILABLE -> R.string.network_load_images_failed
        NetworkLoadError.VIDEO_MODELS_UNAVAILABLE -> R.string.network_load_videos_failed
    }
}

private fun NetworkModelsUpdate.labelResource(): Int {
    return when (this) {
        NetworkModelsUpdate.IMAGE_MODELS -> R.string.network_images_updated
        NetworkModelsUpdate.VIDEO_MODELS -> R.string.network_videos_updated
    }
}
