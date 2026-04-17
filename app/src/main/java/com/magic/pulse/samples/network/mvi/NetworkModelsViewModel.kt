package com.magic.pulse.samples.network.mvi

import com.magic.mvicore.android.PulseSplitViewModel
import com.magic.mvicore.android.UiIntentExecutionScope
import com.magic.mvicore.android.UiIntentExecutor
import com.magic.pulse.samples.network.data.remote.DefaultModelRemoteDataSource
import com.magic.pulse.samples.network.data.remote.FakeModelRemoteService
import com.magic.pulse.samples.network.data.repository.DefaultModelRepository
import com.magic.pulse.samples.network.data.repository.ModelRepository

typealias NetworkModelsViewModel =
    PulseSplitViewModel<
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
    fun onIntent(
        intent: NetworkModelsUiIntent,
        scope: UiIntentExecutionScope<
            NetworkModelsState,
            NetworkModelsUiIntent,
            NetworkModelsMutation,
            NetworkModelsEffect,
        >,
    ) {
        when (intent) {
            NetworkModelsUiIntent.LoadImageModelsClicked -> {
                if (scope.currentState.isLoading) return
                scope.dispatchMutation(
                    NetworkModelsMutation.LoadingStarted(LoadingTarget.IMAGE)
                )
                scope.launch {
                    runCatching { repository.fetchImageModels() }
                        .onSuccess { models ->
                            dispatchMutation(NetworkModelsMutation.ImageModelsLoaded(models))
                            dispatchMutation(NetworkModelsMutation.LoadingCompleted)
                        }
                        .onFailure { error ->
                            dispatchMutation(
                                NetworkModelsMutation.LoadFailed(
                                    message = error.message ?: "Loading image models failed",
                                )
                            )
                        }
                }
            }

            NetworkModelsUiIntent.LoadVideoModelsClicked -> {
                if (scope.currentState.isLoading) return
                scope.dispatchMutation(
                    NetworkModelsMutation.LoadingStarted(LoadingTarget.VIDEO)
                )
                scope.launch {
                    runCatching { repository.fetchVideoModels() }
                        .onSuccess { models ->
                            dispatchMutation(NetworkModelsMutation.VideoModelsLoaded(models))
                            dispatchMutation(NetworkModelsMutation.LoadingCompleted)
                        }
                        .onFailure { error ->
                            dispatchMutation(
                                NetworkModelsMutation.LoadFailed(
                                    message = error.message ?: "Loading video models failed",
                                )
                            )
                        }
                }
            }
        }
    }
}

fun createNetworkModelsViewModel(): NetworkModelsViewModel {
    val executor = NetworkIntentExecutor()
    return PulseSplitViewModel(
        initialState = NetworkModelsState(),
        mutationReducer = NetworkModelsReducer,
        uiIntentExecutor = UiIntentExecutor { intent, scope -> executor.onIntent(intent, scope) },
    )
}
