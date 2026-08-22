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

data class NetworkModelsState(
    val isLoading: Boolean = false,
    val loadingTarget: LoadingTarget? = null,
    val imageModels: List<ImageModel> = emptyList(),
    val videoModels: List<VideoModel> = emptyList(),
    val lastUpdatedLabel: String? = null,
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
    data class LoadFailed(val message: String) : NetworkModelsMutation
}

sealed interface NetworkModelsEffect : UiEffect {
    data class ShowMessage(val text: String) : NetworkModelsEffect
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
                        lastUpdatedLabel = "Image models updated",
                    )
                )
            }

            is NetworkModelsMutation.VideoModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        videoModels = mutation.models,
                        lastUpdatedLabel = "Video models updated",
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
                    uiEffects = listOf(NetworkModelsEffect.ShowMessage(mutation.message)),
                )
            }
        }
    }
}
