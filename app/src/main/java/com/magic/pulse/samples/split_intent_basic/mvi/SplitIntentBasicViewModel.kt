package com.magic.pulse.samples.split_intent_basic.mvi

import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import com.magic.pulse.samples.common.toSampleExecutionDecision
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
                val launchResult = context.launchTask(LOAD_TASK, TaskPolicy.DropWhileRunning) {
                    val loadingStarted = mutate(
                        SplitIntentBasicMutation.LoadingStarted(BasicLoadingTarget.IMAGE)
                    )
                    if (!loadingStarted) return@launchTask
                    try {
                        val modelsLoaded = mutate(
                            SplitIntentBasicMutation.ImageModelsLoaded(fakeFetchImageModels())
                        )
                        if (!modelsLoaded) return@launchTask
                        val loadingFinished = mutate(SplitIntentBasicMutation.LoadingFinished)
                        if (!loadingFinished) return@launchTask
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        val failureRecorded = mutate(
                            SplitIntentBasicMutation.LoadFailed(
                                BasicLoadError.IMAGE_MODELS_UNAVAILABLE,
                            )
                        )
                        if (!failureRecorded) return@launchTask
                    }
                }
                launchResult.toSampleExecutionDecision()
            }

            SplitIntentBasicUiIntent.LoadVideoModelsClicked -> {
                val launchResult = context.launchTask(LOAD_TASK, TaskPolicy.DropWhileRunning) {
                    val loadingStarted = mutate(
                        SplitIntentBasicMutation.LoadingStarted(BasicLoadingTarget.VIDEO)
                    )
                    if (!loadingStarted) return@launchTask
                    try {
                        val modelsLoaded = mutate(
                            SplitIntentBasicMutation.VideoModelsLoaded(fakeFetchVideoModels())
                        )
                        if (!modelsLoaded) return@launchTask
                        val loadingFinished = mutate(SplitIntentBasicMutation.LoadingFinished)
                        if (!loadingFinished) return@launchTask
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        val failureRecorded = mutate(
                            SplitIntentBasicMutation.LoadFailed(
                                BasicLoadError.VIDEO_MODELS_UNAVAILABLE,
                            )
                        )
                        if (!failureRecorded) return@launchTask
                    }
                }
                launchResult.toSampleExecutionDecision()
            }

            SplitIntentBasicUiIntent.ClearAllClicked -> {
                if (context.mutate(SplitIntentBasicMutation.Cleared)) {
                    PulseIntentExecutionDecision.Completed
                } else {
                    PulseIntentExecutionDecision.Ignored("clear-mutation-not-accepted")
                }
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
