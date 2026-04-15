package com.magic.pulse.samples.network.data.repository

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel
import com.magic.pulse.samples.network.data.remote.ModelRemoteDataSource

class DefaultModelRepository(
    private val remoteDataSource: ModelRemoteDataSource,
) : ModelRepository {

    override suspend fun fetchImageModels(): List<ImageModel> = remoteDataSource.getImageModels()

    override suspend fun fetchVideoModels(): List<VideoModel> = remoteDataSource.getVideoModels()
}
