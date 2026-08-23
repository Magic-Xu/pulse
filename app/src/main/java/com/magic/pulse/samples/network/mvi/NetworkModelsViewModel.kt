package com.magic.pulse.samples.network.mvi

import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import com.magic.pulse.samples.common.toSampleExecutionDecision
import com.magic.pulse.samples.network.data.remote.DefaultModelRemoteDataSource
import com.magic.pulse.samples.network.data.remote.FakeModelRemoteService
import com.magic.pulse.samples.network.data.repository.DefaultModelRepository
import com.magic.pulse.samples.network.data.repository.ModelRepository
import kotlinx.coroutines.CancellationException

typealias NetworkModelsViewModel = PulseSplitStoreViewModel<
    NetworkModelsState,
    NetworkModelsUiIntent,
    NetworkModelsMutation,
    NetworkModelsEffect,
>

private class NetworkIntentExecutor(
    private val repository: ModelRepository = DefaultModelRepository(
        remoteDataSource = DefaultModelRemoteDataSource(
            remoteService = FakeModelRemoteService(),
        )
    ),
) {
    val executor = PulseUiIntentExecutor<
        NetworkModelsState,
        NetworkModelsUiIntent,
        NetworkModelsMutation,
    > { intent, context ->
        val target = when (intent) {
            NetworkModelsUiIntent.LoadImageModelsClicked -> LoadingTarget.IMAGE
            NetworkModelsUiIntent.LoadVideoModelsClicked -> LoadingTarget.VIDEO
        }
        val launchResult = context.launchTask(LOAD_TASK, TaskPolicy.Latest) {
            val loadingStarted = mutate(NetworkModelsMutation.LoadingStarted(target))
            if (!loadingStarted) return@launchTask
            try {
                when (intent) {
                    NetworkModelsUiIntent.LoadImageModelsClicked -> {
                        val modelsLoaded = mutate(
                            NetworkModelsMutation.ImageModelsLoaded(
                                repository.fetchImageModels()
                            )
                        )
                        if (!modelsLoaded) return@launchTask
                    }

                    NetworkModelsUiIntent.LoadVideoModelsClicked -> {
                        val modelsLoaded = mutate(
                            NetworkModelsMutation.VideoModelsLoaded(
                                repository.fetchVideoModels()
                            )
                        )
                        if (!modelsLoaded) return@launchTask
                    }
                }
                val loadingCompleted = mutate(NetworkModelsMutation.LoadingCompleted)
                if (!loadingCompleted) return@launchTask
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val error = when (target) {
                    LoadingTarget.IMAGE -> NetworkLoadError.IMAGE_MODELS_UNAVAILABLE
                    LoadingTarget.VIDEO -> NetworkLoadError.VIDEO_MODELS_UNAVAILABLE
                }
                val failureRecorded = mutate(
                    NetworkModelsMutation.LoadFailed(
                        error = error,
                    )
                )
                if (!failureRecorded) return@launchTask
            }
        }
        launchResult.toSampleExecutionDecision()
    }

    private companion object {
        val LOAD_TASK = TaskKey("network-models.load")
    }
}

fun createNetworkModelsViewModel(): NetworkModelsViewModel {
    val executor = NetworkIntentExecutor()
    return PulseSplitStoreViewModel(
        initialState = NetworkModelsState(),
        mutationReducer = NetworkModelsReducer,
        uiIntentExecutor = executor.executor,
    )
}
