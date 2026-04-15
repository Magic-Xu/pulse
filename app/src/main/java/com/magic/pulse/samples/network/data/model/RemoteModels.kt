package com.magic.pulse.samples.network.data.model

data class ImageModel(
    val id: String,
    val name: String,
    val url: String,
)

data class VideoModel(
    val id: String,
    val name: String,
    val durationSeconds: Int,
)
