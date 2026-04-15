package com.magic.pulse.samples.network.data.repository

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel

interface ModelRepository {
    suspend fun fetchImageModels(): List<ImageModel>
    suspend fun fetchVideoModels(): List<VideoModel>
}
