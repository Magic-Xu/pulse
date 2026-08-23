package com.magic.pulse.samples.split_intent_basic.mvi

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect

enum class BasicLoadingTarget {
    IMAGE,
    VIDEO,
}

enum class BasicOperation {
    READY,
    LOADING_IMAGE_MODELS,
    LOADING_VIDEO_MODELS,
    IMAGE_MODELS_LOADED,
    VIDEO_MODELS_LOADED,
    LOAD_FAILED,
    CLEARED,
}

enum class BasicLoadError {
    IMAGE_MODELS_UNAVAILABLE,
    VIDEO_MODELS_UNAVAILABLE,
}

data class BasicModel(
    val id: String,
    val name: String,
)

data class SplitIntentBasicState(
    val isLoading: Boolean = false,
    val loadingTarget: BasicLoadingTarget? = null,
    val imageModels: List<BasicModel> = emptyList(),
    val videoModels: List<BasicModel> = emptyList(),
    val requestCount: Int = 0,
    val lastOperation: BasicOperation = BasicOperation.READY,
) : MviState

sealed interface SplitIntentBasicUiIntent : MviUiIntent {
    data object LoadImageModelsClicked : SplitIntentBasicUiIntent
    data object LoadVideoModelsClicked : SplitIntentBasicUiIntent
    data object ClearAllClicked : SplitIntentBasicUiIntent
}

sealed interface SplitIntentBasicMutation : MviMutation {
    data class LoadingStarted(val target: BasicLoadingTarget) : SplitIntentBasicMutation
    data class ImageModelsLoaded(val models: List<BasicModel>) : SplitIntentBasicMutation
    data class VideoModelsLoaded(val models: List<BasicModel>) : SplitIntentBasicMutation
    data object LoadingFinished : SplitIntentBasicMutation
    data class LoadFailed(val error: BasicLoadError) : SplitIntentBasicMutation
    data object Cleared : SplitIntentBasicMutation
}

sealed interface SplitIntentBasicEffect : UiEffect {
    data class ShowLoadError(val error: BasicLoadError) : SplitIntentBasicEffect
}

object SplitIntentBasicReducer : PulseMutationReducer<
    SplitIntentBasicState,
    SplitIntentBasicMutation,
    SplitIntentBasicEffect,
    > {
    override fun reduce(
        previous: SplitIntentBasicState,
        mutation: SplitIntentBasicMutation,
    ): ReduceOutcome<SplitIntentBasicState, SplitIntentBasicEffect> {
        return when (mutation) {
            is SplitIntentBasicMutation.LoadingStarted -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        isLoading = true,
                        loadingTarget = mutation.target,
                        lastOperation = when (mutation.target) {
                            BasicLoadingTarget.IMAGE -> BasicOperation.LOADING_IMAGE_MODELS
                            BasicLoadingTarget.VIDEO -> BasicOperation.LOADING_VIDEO_MODELS
                        },
                    )
                )
            }

            is SplitIntentBasicMutation.ImageModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        imageModels = mutation.models,
                        requestCount = previous.requestCount + 1,
                        lastOperation = BasicOperation.IMAGE_MODELS_LOADED,
                    )
                )
            }

            is SplitIntentBasicMutation.VideoModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        videoModels = mutation.models,
                        requestCount = previous.requestCount + 1,
                        lastOperation = BasicOperation.VIDEO_MODELS_LOADED,
                    )
                )
            }

            SplitIntentBasicMutation.LoadingFinished -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                    )
                )
            }

            is SplitIntentBasicMutation.LoadFailed -> {
                ReduceOutcome.Changed(
                    state = previous.copy(
                        isLoading = false,
                        loadingTarget = null,
                        lastOperation = BasicOperation.LOAD_FAILED,
                    ),
                    uiEffects = listOf(SplitIntentBasicEffect.ShowLoadError(mutation.error)),
                )
            }

            SplitIntentBasicMutation.Cleared -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        imageModels = emptyList(),
                        videoModels = emptyList(),
                        lastOperation = BasicOperation.CLEARED,
                    )
                )
            }
        }
    }
}
