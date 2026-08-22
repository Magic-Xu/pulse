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
    val lastOperation: String = "Ready",
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
    data class LoadFailed(val message: String) : SplitIntentBasicMutation
    data object Cleared : SplitIntentBasicMutation
}

sealed interface SplitIntentBasicEffect : UiEffect {
    data class ShowMessage(val text: String) : SplitIntentBasicEffect
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
                        lastOperation = "Loading ${mutation.target.name.lowercase()} models...",
                    )
                )
            }

            is SplitIntentBasicMutation.ImageModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        imageModels = mutation.models,
                        requestCount = previous.requestCount + 1,
                        lastOperation = "Image models loaded",
                    )
                )
            }

            is SplitIntentBasicMutation.VideoModelsLoaded -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        videoModels = mutation.models,
                        requestCount = previous.requestCount + 1,
                        lastOperation = "Video models loaded",
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
                        lastOperation = "Load failed",
                    ),
                    uiEffects = listOf(SplitIntentBasicEffect.ShowMessage(mutation.message)),
                )
            }

            SplitIntentBasicMutation.Cleared -> {
                ReduceOutcome.Changed(
                    previous.copy(
                        imageModels = emptyList(),
                        videoModels = emptyList(),
                        lastOperation = "Cleared",
                    )
                )
            }
        }
    }
}
