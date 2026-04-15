package com.magic.pulse.samples.network.mvi

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
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

sealed interface NetworkModelsIntent : MviIntent {
    data object LoadImageModels : NetworkModelsIntent
    data object LoadVideoModels : NetworkModelsIntent
    data class ImageModelsLoaded(val models: List<ImageModel>) : NetworkModelsIntent
    data class VideoModelsLoaded(val models: List<VideoModel>) : NetworkModelsIntent
    data object LoadCompleted : NetworkModelsIntent
    data class LoadFailed(val message: String) : NetworkModelsIntent
}

sealed interface NetworkModelsEffect : MviEffect {
    data class ShowMessage(val text: String) : NetworkModelsEffect
}

object NetworkModelsReducer :
    Reducer<NetworkModelsState, NetworkModelsIntent, NetworkModelsEffect> {

    override fun reduce(
        previous: NetworkModelsState,
        intent: NetworkModelsIntent,
    ): Next<NetworkModelsState, NetworkModelsEffect> {
        return when (intent) {
            NetworkModelsIntent.LoadImageModels -> {
                Next.just(
                    previous.copy(
                        isLoading = true,
                        loadingTarget = LoadingTarget.IMAGE,
                    )
                )
            }

            NetworkModelsIntent.LoadVideoModels -> {
                Next.just(
                    previous.copy(
                        isLoading = true,
                        loadingTarget = LoadingTarget.VIDEO,
                    )
                )
            }

            is NetworkModelsIntent.ImageModelsLoaded -> {
                Next.just(
                    previous.copy(
                        imageModels = intent.models,
                        lastUpdatedLabel = "Image models updated",
                    )
                )
            }

            is NetworkModelsIntent.VideoModelsLoaded -> {
                Next.just(
                    previous.copy(
                        videoModels = intent.models,
                        lastUpdatedLabel = "Video models updated",
                    )
                )
            }

            NetworkModelsIntent.LoadCompleted -> {
                Next.just(
                    previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                    )
                )
            }

            is NetworkModelsIntent.LoadFailed -> {
                Next.withEffect(
                    previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                    ),
                    NetworkModelsEffect.ShowMessage(intent.message),
                )
            }
        }
    }
}
