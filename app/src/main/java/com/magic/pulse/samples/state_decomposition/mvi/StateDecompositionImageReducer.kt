package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.extensions.PulseMutationReducerBuilder
import com.magic.mvicore.extensions.subStateJust
import com.magic.pulse.samples.state_decomposition.data.model.ImageEffect

internal fun PulseMutationReducerBuilder<
    StateDecompositionState,
    StateDecompositionMutation,
    StateDecompositionEffect,
    >.installImageDomainReducer() {
    onSub<ImageDomainState, StateDecompositionMutation.Image>(lens = imageLens) { previous, mutation ->
        when (mutation) {
            StateDecompositionMutation.Image.LoadStarted -> {
                subStateJust(previous.copy(isLoading = true))
            }

            is StateDecompositionMutation.Image.ModelsLoaded -> {
                val selectedId = mutation.models.firstOrNull()?.id
                subStateJust(
                    previous.copy(
                        isLoading = false,
                        models = mutation.models,
                        selectedModelId = selectedId,
                        lastSyncLabel = "Image domain synced",
                    )
                )
            }

            is StateDecompositionMutation.Image.EffectChanged -> {
                val tunedState = when (mutation.effect) {
                    ImageEffect.FACE_RECOGNITION -> {
                        previous.copy(
                            selectedEffect = mutation.effect,
                            stylePreset = "真实人像",
                            depthStrength = 55,
                            facePriority = true,
                            guidanceScale = 8.2f,
                        )
                    }

                    ImageEffect.STYLE_TRANSFER -> {
                        previous.copy(
                            selectedEffect = mutation.effect,
                            stylePreset = "印象派",
                            depthStrength = 50,
                            facePriority = false,
                            guidanceScale = 6.8f,
                        )
                    }

                    ImageEffect.DEPTH_ENHANCEMENT -> {
                        previous.copy(
                            selectedEffect = mutation.effect,
                            stylePreset = "电影虚化",
                            depthStrength = 85,
                            facePriority = true,
                            guidanceScale = 7.9f,
                        )
                    }
                }
                subStateJust(tunedState)
            }
        }
    }
}
