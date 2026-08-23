package com.magic.pulse.samples.network.mvi

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel

enum class LoadingTarget {
    IMAGE,
    VIDEO,
}

enum class NetworkModelsUpdate {
    IMAGE_MODELS,
    VIDEO_MODELS,
}

enum class NetworkLoadError {
    IMAGE_MODELS_UNAVAILABLE,
    VIDEO_MODELS_UNAVAILABLE,
}

data class NetworkModelsState(
    val isLoading: Boolean = false,
    val loadingTarget: LoadingTarget? = null,
    val imageModels: List<ImageModel> = emptyList(),
    val videoModels: List<VideoModel> = emptyList(),
    val lastUpdated: NetworkModelsUpdate? = null,
) : MviState

sealed interface NetworkModelsUiIntent : MviUiIntent {
    data object LoadImageModelsClicked : NetworkModelsUiIntent
    data object LoadVideoModelsClicked : NetworkModelsUiIntent
}

sealed interface NetworkModelsMutation : MviMutation {
    data class LoadingStarted(val target: LoadingTarget) : NetworkModelsMutation
    data class ImageModelsLoaded(val models: List<ImageModel>) : NetworkModelsMutation
    data class VideoModelsLoaded(val models: List<VideoModel>) : NetworkModelsMutation
    data object LoadingCompleted : NetworkModelsMutation
    data class LoadFailed(val error: NetworkLoadError) : NetworkModelsMutation
}

sealed interface NetworkModelsEffect : UiEffect {
    data class ShowLoadError(val error: NetworkLoadError) : NetworkModelsEffect
}

object NetworkModelsReducer :
    PulseMutationReducer<NetworkModelsState, NetworkModelsMutation, NetworkModelsEffect> {

    override fun reduce(
        previous: NetworkModelsState,
        mutation: NetworkModelsMutation,
    ): ReduceOutcome<NetworkModelsState, NetworkModelsEffect> {
        return when (mutation) {
            is NetworkModelsMutation.LoadingStarted -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        isLoading = true,
                        loadingTarget = mutation.target,
                    )
                )
            }

            is NetworkModelsMutation.ImageModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        imageModels = mutation.models,
                        lastUpdated = NetworkModelsUpdate.IMAGE_MODELS,
                    )
                )
            }

            is NetworkModelsMutation.VideoModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        videoModels = mutation.models,
                        lastUpdated = NetworkModelsUpdate.VIDEO_MODELS,
                    )
                )
            }

            NetworkModelsMutation.LoadingCompleted -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                    )
                )
            }

            is NetworkModelsMutation.LoadFailed -> {
                ReduceOutcome.Changed(
                    state = previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                    ),
                    uiEffects = listOf(NetworkModelsEffect.ShowLoadError(mutation.error)),
                )
            }
        }
    }
}
