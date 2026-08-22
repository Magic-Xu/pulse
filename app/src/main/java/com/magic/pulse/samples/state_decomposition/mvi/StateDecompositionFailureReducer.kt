package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.extensions.PulseMutationReducerBuilder

internal fun PulseMutationReducerBuilder<
    StateDecompositionState,
    StateDecompositionMutation,
    StateDecompositionEffect,
    >.installDomainFailureReducer() {
    on<StateDecompositionMutation.DomainFailed> { previous, mutation ->
        val nextState = when (mutation.domain) {
            DomainType.IMAGE -> previous.copy(image = previous.image.copy(isLoading = false))
            DomainType.VIDEO -> previous.copy(video = previous.video.copy(isLoading = false))
        }
        val prefix = when (mutation.domain) {
            DomainType.IMAGE -> "图片模型加载失败"
            DomainType.VIDEO -> "视频模型加载失败"
        }
        ReduceOutcome.Changed(
            state = nextState,
            uiEffects = listOf(StateDecompositionEffect.ShowMessage("$prefix: ${mutation.message}")),
        )
    }
}
