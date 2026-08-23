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
import com.magic.pulse.samples.split_intent_basic.mvi.BasicLoadError
import com.magic.pulse.samples.split_intent_basic.mvi.BasicModel
import com.magic.pulse.samples.split_intent_basic.mvi.BasicOperation
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
    val resources = LocalResources.current
    var ingressFailure by remember { mutableStateOf<SampleIngressFailure?>(null) }
    val ingress = remember(viewModel) {
        viewModel.callbackIngress { _, result ->
            ingressFailure = result.toSampleIngressFailure()
        }
    }
    val submitIntent: (SplitIntentBasicUiIntent) -> Unit = { intent ->
        if (ingress.submit(intent) is EnqueueResult.Enqueued) ingressFailure = null
    }

    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        when (effect) {
            is SplitIntentBasicEffect.ShowLoadError -> {
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.basic_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        SplitIntentBasicActionRow(
            isLoading = state.isLoading,
            loadingTarget = state.loadingTarget,
            onLoadImages = { submitIntent(SplitIntentBasicUiIntent.LoadImageModelsClicked) },
            onLoadVideos = { submitIntent(SplitIntentBasicUiIntent.LoadVideoModelsClicked) },
            onClear = { submitIntent(SplitIntentBasicUiIntent.ClearAllClicked) },
        )
        ingressFailure?.let { failure ->
            SampleIngressFailureText(failure)
        }
        Text(stringResource(R.string.basic_request_count, state.requestCount))
        Text(
            stringResource(
                R.string.basic_last_operation,
                stringResource(state.lastOperation.labelResource()),
            )
        )
        BasicModelList(
            title = stringResource(R.string.basic_image_models_title),
            items = state.imageModels,
        )
        BasicModelList(
            title = stringResource(R.string.basic_video_models_title),
            items = state.videoModels,
        )
    }
}

@Composable
private fun BasicModelList(
    title: String,
    items: List<BasicModel>,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    if (items.isEmpty()) {
        Text(stringResource(R.string.sample_list_item, stringResource(R.string.basic_empty_models)))
        return
    }
    items.forEach { model ->
        val row = stringResource(R.string.basic_model_item, model.id, model.name)
        Text(stringResource(R.string.sample_list_item, row))
    }
}

private fun BasicLoadError.messageResource(): Int {
    return when (this) {
        BasicLoadError.IMAGE_MODELS_UNAVAILABLE -> R.string.basic_load_images_failed
        BasicLoadError.VIDEO_MODELS_UNAVAILABLE -> R.string.basic_load_videos_failed
    }
}

private fun BasicOperation.labelResource(): Int {
    return when (this) {
        BasicOperation.READY -> R.string.basic_operation_ready
        BasicOperation.LOADING_IMAGE_MODELS -> R.string.basic_operation_loading_images
        BasicOperation.LOADING_VIDEO_MODELS -> R.string.basic_operation_loading_videos
        BasicOperation.IMAGE_MODELS_LOADED -> R.string.basic_operation_images_loaded
        BasicOperation.VIDEO_MODELS_LOADED -> R.string.basic_operation_videos_loaded
        BasicOperation.LOAD_FAILED -> R.string.basic_operation_load_failed
        BasicOperation.CLEARED -> R.string.basic_operation_cleared
    }
}
