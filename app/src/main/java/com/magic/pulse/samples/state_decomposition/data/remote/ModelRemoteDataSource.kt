package com.magic.pulse.samples.state_decomposition.data.remote

import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile

interface ModelRemoteDataSource {
    suspend fun fetchImageModels(): List<ImageModelProfile>
    suspend fun fetchVideoModels(): List<VideoModelProfile>
}
