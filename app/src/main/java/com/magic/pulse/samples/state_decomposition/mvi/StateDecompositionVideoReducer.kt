package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.extensions.PulseMutationReducerBuilder
import com.magic.mvicore.extensions.subStateJust
import com.magic.pulse.samples.state_decomposition.data.model.VideoTask

internal fun PulseMutationReducerBuilder<
    StateDecompositionState,
    StateDecompositionMutation,
    StateDecompositionEffect,
    >.installVideoDomainReducer() {
    onSub<VideoDomainState, StateDecompositionMutation.Video>(lens = videoLens) { previous, mutation ->
        when (mutation) {
            StateDecompositionMutation.Video.LoadStarted -> {
                subStateJust(previous.copy(isLoading = true))
            }

            is StateDecompositionMutation.Video.ModelsLoaded -> {
                val selectedId = mutation.models.firstOrNull()?.id
                subStateJust(
                    previous.copy(
                        isLoading = false,
                        models = mutation.models,
                        selectedModelId = selectedId,
                        lastSyncLabel = "Video domain synced",
                    )
                )
            }

            is StateDecompositionMutation.Video.TaskChanged -> {
                val tunedState = when (mutation.task) {
                    VideoTask.MOTION_TRACKING -> {
                        previous.copy(
                            selectedTask = mutation.task,
                            stabilizationLevel = "Balanced",
                            interpolationFrames = 2,
                            outputFps = 30,
                            clipLengthSeconds = 8,
                            autoSubtitle = false,
                        )
                    }

                    VideoTask.FRAME_INTERPOLATION -> {
                        previous.copy(
                            selectedTask = mutation.task,
                            stabilizationLevel = "Low",
                            interpolationFrames = 6,
                            outputFps = 60,
                            clipLengthSeconds = 6,
                            autoSubtitle = false,
                        )
                    }

                    VideoTask.AUTO_SUBTITLE -> {
                        previous.copy(
                            selectedTask = mutation.task,
                            stabilizationLevel = "Balanced",
                            interpolationFrames = 2,
                            outputFps = 30,
                            clipLengthSeconds = 20,
                            autoSubtitle = true,
                        )
                    }

                    VideoTask.STABILIZATION_ENHANCE -> {
                        previous.copy(
                            selectedTask = mutation.task,
                            stabilizationLevel = "High",
                            interpolationFrames = 3,
                            outputFps = 30,
                            clipLengthSeconds = 10,
                            autoSubtitle = false,
                        )
                    }
                }
                subStateJust(tunedState)
            }
        }
    }
}
