package com.magic.pulse.samples.state_decomposition.data.repository

import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile
import com.magic.pulse.samples.state_decomposition.data.remote.ModelRemoteDataSource

class DefaultModelRepository(
    private val remoteDataSource: ModelRemoteDataSource,
) : ModelRepository {

    override suspend fun fetchImageModels(): List<ImageModelProfile> {
        return remoteDataSource.fetchImageModels()
    }

    override suspend fun fetchVideoModels(): List<VideoModelProfile> {
        return remoteDataSource.fetchVideoModels()
    }
}
