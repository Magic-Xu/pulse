package com.magic.pulse.samples.network.data.remote

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel

interface ModelRemoteService {
    suspend fun fetchImageModels(): List<ImageModel>
    suspend fun fetchVideoModels(): List<VideoModel>
}
