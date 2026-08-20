package com.magic.pulse.samples.split_intent_basic.mvi

import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class SplitIntentBasicViewModel : PulseSplitStoreViewModel<
    SplitIntentBasicState,
    SplitIntentBasicUiIntent,
    SplitIntentBasicMutation,
    SplitIntentBasicEffect,
>(
    initialState = SplitIntentBasicState(),
    mutationReducer = SplitIntentBasicReducer,
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            SplitIntentBasicUiIntent.LoadImageModelsClicked -> {
                context.launchTask(LOAD_TASK, TaskPolicy.DropWhileRunning) {
                    mutate(SplitIntentBasicMutation.LoadingStarted(BasicLoadingTarget.IMAGE))
                    try {
                        mutate(
                            SplitIntentBasicMutation.ImageModelsLoaded(fakeFetchImageModels())
                        )
                        mutate(SplitIntentBasicMutation.LoadingFinished)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutate(
                            SplitIntentBasicMutation.LoadFailed(
                                failure.message ?: "Load image models failed",
                            )
                        )
                    }
                }
            }

            SplitIntentBasicUiIntent.LoadVideoModelsClicked -> {
                context.launchTask(LOAD_TASK, TaskPolicy.DropWhileRunning) {
                    mutate(SplitIntentBasicMutation.LoadingStarted(BasicLoadingTarget.VIDEO))
                    try {
                        mutate(
                            SplitIntentBasicMutation.VideoModelsLoaded(fakeFetchVideoModels())
                        )
                        mutate(SplitIntentBasicMutation.LoadingFinished)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutate(
                            SplitIntentBasicMutation.LoadFailed(
                                failure.message ?: "Load video models failed",
                            )
                        )
                    }
                }
            }

            SplitIntentBasicUiIntent.ClearAllClicked -> {
                context.mutate(SplitIntentBasicMutation.Cleared)
            }
        }
    },
) {
    private companion object {
        val LOAD_TASK = TaskKey("split-intent-basic.load")

        suspend fun fakeFetchImageModels(): List<BasicModel> {
            delay(1_200)
            return listOf(
                BasicModel("img-basic-1", "Portrait Lite"),
                BasicModel("img-basic-2", "Landscape Lite"),
                BasicModel("img-basic-3", "Anime Lite"),
            )
        }

        suspend fun fakeFetchVideoModels(): List<BasicModel> {
            delay(1_200)
            return listOf(
                BasicModel("vid-basic-1", "Clip Starter"),
                BasicModel("vid-basic-2", "Movie Starter"),
                BasicModel("vid-basic-3", "Subtitle Starter"),
            )
        }
    }
}

fun createSplitIntentBasicViewModel(): SplitIntentBasicViewModel {
    return SplitIntentBasicViewModel()
}
