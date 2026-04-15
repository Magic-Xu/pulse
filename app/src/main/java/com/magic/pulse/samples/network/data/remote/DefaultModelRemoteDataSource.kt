package com.magic.pulse.samples.network.data.remote

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel

class DefaultModelRemoteDataSource(
    private val remoteService: ModelRemoteService,
) : ModelRemoteDataSource {

    override suspend fun getImageModels(): List<ImageModel> = remoteService.fetchImageModels()

    override suspend fun getVideoModels(): List<VideoModel> = remoteService.fetchVideoModels()
}
