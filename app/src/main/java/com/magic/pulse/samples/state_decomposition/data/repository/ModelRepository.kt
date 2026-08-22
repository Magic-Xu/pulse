package com.magic.pulse.samples.state_decomposition.data.repository

import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile

interface ModelRepository {
    suspend fun fetchImageModels(): List<ImageModelProfile>
    suspend fun fetchVideoModels(): List<VideoModelProfile>
}
