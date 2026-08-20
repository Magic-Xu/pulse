package com.magic.pulse.samples.state_decomposition.data.remote

import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile

class DefaultModelRemoteDataSource(
    private val service: ModelRemoteService,
) : ModelRemoteDataSource {

    override suspend fun fetchImageModels(): List<ImageModelProfile> {
        return service.fetchImageModels()
    }

    override suspend fun fetchVideoModels(): List<VideoModelProfile> {
        return service.fetchVideoModels()
    }
}
