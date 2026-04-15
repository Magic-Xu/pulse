package com.magic.pulse.samples.network.mvi

import androidx.lifecycle.viewModelScope
import com.magic.mvicore.android.MviViewModel
import com.magic.pulse.samples.network.data.remote.DefaultModelRemoteDataSource
import com.magic.pulse.samples.network.data.remote.FakeModelRemoteService
import com.magic.pulse.samples.network.data.repository.DefaultModelRepository
import com.magic.pulse.samples.network.data.repository.ModelRepository
import kotlinx.coroutines.launch

class NetworkModelsViewModel(
    private val repository: ModelRepository = DefaultModelRepository(
        remoteDataSource = DefaultModelRemoteDataSource(
            remoteService = FakeModelRemoteService(),
        )
    ),
) : MviViewModel<NetworkModelsState, NetworkModelsIntent, NetworkModelsEffect>(
    initialState = NetworkModelsState(),
    reducer = NetworkModelsReducer,
) {

    fun loadImageModels() {
        if (currentState.isLoading) return
        dispatch(NetworkModelsIntent.LoadImageModels)
        viewModelScope.launch {
            runCatching { repository.fetchImageModels() }
                .onSuccess { models ->
                    dispatch(NetworkModelsIntent.ImageModelsLoaded(models))
                    dispatch(NetworkModelsIntent.LoadCompleted)
                }
                .onFailure { error ->
                    dispatch(
                        NetworkModelsIntent.LoadFailed(
                            message = error.message ?: "Loading image models failed",
                        )
                    )
                }
        }
    }

    fun loadVideoModels() {
        if (currentState.isLoading) return
        dispatch(NetworkModelsIntent.LoadVideoModels)
        viewModelScope.launch {
            runCatching { repository.fetchVideoModels() }
                .onSuccess { models ->
                    dispatch(NetworkModelsIntent.VideoModelsLoaded(models))
                    dispatch(NetworkModelsIntent.LoadCompleted)
                }
                .onFailure { error ->
                    dispatch(
                        NetworkModelsIntent.LoadFailed(
                            message = error.message ?: "Loading video models failed",
                        )
                    )
                }
        }
    }
}
