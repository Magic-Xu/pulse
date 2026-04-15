package com.magic.pulse.samples.network.data.remote

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel
import kotlinx.coroutines.delay

class FakeModelRemoteService : ModelRemoteService {

    override suspend fun fetchImageModels(): List<ImageModel> {
        delay(2_000)
        return listOf(
            ImageModel("img-001", "Landscape", "https://example.com/images/landscape.jpg"),
            ImageModel("img-002", "City", "https://example.com/images/city.jpg"),
            ImageModel("img-003", "Ocean", "https://example.com/images/ocean.jpg"),
        )
    }

    override suspend fun fetchVideoModels(): List<VideoModel> {
        delay(2_000)
        return listOf(
            VideoModel("vid-001", "Intro Clip", 32),
            VideoModel("vid-002", "Tutorial", 95),
            VideoModel("vid-003", "Showcase", 48),
        )
    }
}
