package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import com.magic.pulse.samples.state_decomposition.data.remote.DefaultModelRemoteDataSource
import com.magic.pulse.samples.state_decomposition.data.remote.FakeModelRemoteService
import com.magic.pulse.samples.state_decomposition.data.repository.DefaultModelRepository
import com.magic.pulse.samples.state_decomposition.data.repository.ModelRepository
import kotlinx.coroutines.CancellationException

typealias StateDecompositionViewModel =
    PulseSplitStoreViewModel<
        StateDecompositionState,
        StateDecompositionUiIntent,
        StateDecompositionMutation,
        StateDecompositionEffect,
    >

fun createStateDecompositionViewModel(
    repository: ModelRepository = DefaultModelRepository(
        remoteDataSource = DefaultModelRemoteDataSource(
            service = FakeModelRemoteService(),
        )
    ),
): StateDecompositionViewModel {
    return PulseSplitStoreViewModel(
        initialState = StateDecompositionState(),
        mutationReducer = StateDecompositionReducer,
        uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
            with(context) {
                when (intent) {
                    StateDecompositionUiIntent.LoadImageModelsClicked -> {
                        mutate(StateDecompositionMutation.Image.LoadStarted)
                        launchTask(IMAGE_LOAD_TASK, TaskPolicy.Latest) {
                            try {
                                mutate(
                                    StateDecompositionMutation.Image.ModelsLoaded(
                                        repository.fetchImageModels()
                                    )
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                mutate(
                                    StateDecompositionMutation.DomainFailed(
                                        domain = DomainType.IMAGE,
                                        message = failure.message ?: "unknown error",
                                    )
                                )
                            }
                        }
                    }

                    StateDecompositionUiIntent.LoadVideoModelsClicked -> {
                        mutate(StateDecompositionMutation.Video.LoadStarted)
                        launchTask(VIDEO_LOAD_TASK, TaskPolicy.Latest) {
                            try {
                                mutate(
                                    StateDecompositionMutation.Video.ModelsLoaded(
                                        repository.fetchVideoModels()
                                    )
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                mutate(
                                    StateDecompositionMutation.DomainFailed(
                                        domain = DomainType.VIDEO,
                                        message = failure.message ?: "unknown error",
                                    )
                                )
                            }
                        }
                    }

                    is StateDecompositionUiIntent.SelectImageEffect -> {
                        mutate(StateDecompositionMutation.Image.EffectChanged(intent.effect))
                    }

                    is StateDecompositionUiIntent.SelectVideoTask -> {
                        mutate(StateDecompositionMutation.Video.TaskChanged(intent.task))
                    }
                }
            }
        },
    )
}

private val IMAGE_LOAD_TASK = TaskKey("state-decomposition.image-load")
private val VIDEO_LOAD_TASK = TaskKey("state-decomposition.video-load")
