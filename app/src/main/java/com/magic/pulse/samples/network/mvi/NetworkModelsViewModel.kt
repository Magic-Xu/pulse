package com.magic.pulse.samples.network.mvi

import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
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
        context.launchTask(LOAD_TASK, TaskPolicy.Latest) {
            val target = when (intent) {
                NetworkModelsUiIntent.LoadImageModelsClicked -> LoadingTarget.IMAGE
                NetworkModelsUiIntent.LoadVideoModelsClicked -> LoadingTarget.VIDEO
            }
            mutate(NetworkModelsMutation.LoadingStarted(target))
            try {
                when (intent) {
                    NetworkModelsUiIntent.LoadImageModelsClicked -> mutate(
                        NetworkModelsMutation.ImageModelsLoaded(repository.fetchImageModels())
                    )

                    NetworkModelsUiIntent.LoadVideoModelsClicked -> mutate(
                        NetworkModelsMutation.VideoModelsLoaded(repository.fetchVideoModels())
                    )
                }
                mutate(NetworkModelsMutation.LoadingCompleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutate(
                    NetworkModelsMutation.LoadFailed(
                        message = failure.message ?: "Loading models failed",
                    )
                )
            }
        }
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
