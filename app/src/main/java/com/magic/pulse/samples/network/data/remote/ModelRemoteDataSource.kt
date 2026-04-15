package com.magic.pulse.samples.network.data.remote

import com.magic.pulse.samples.network.data.model.ImageModel
import com.magic.pulse.samples.network.data.model.VideoModel

interface ModelRemoteDataSource {
    suspend fun getImageModels(): List<ImageModel>
    suspend fun getVideoModels(): List<VideoModel>
}
