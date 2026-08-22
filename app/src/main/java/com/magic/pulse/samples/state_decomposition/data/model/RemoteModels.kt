package com.magic.pulse.samples.state_decomposition.data.model

enum class ImageEffect(val label: String) {
    FACE_RECOGNITION("人脸识别"),
    STYLE_TRANSFER("风格迁移"),
    DEPTH_ENHANCEMENT("景深增强"),
}

enum class VideoTask(val label: String) {
    MOTION_TRACKING("运动跟踪"),
    FRAME_INTERPOLATION("慢动作补帧"),
    AUTO_SUBTITLE("自动字幕"),
    STABILIZATION_ENHANCE("防抖增强"),
}

data class ImageModelProfile(
    val id: String,
    val name: String,
    val recommendedEffect: ImageEffect,
    val maxResolution: String,
    val strengths: List<String>,
)

data class VideoModelProfile(
    val id: String,
    val name: String,
    val recommendedTask: VideoTask,
    val maxFps: Int,
    val maxDurationSeconds: Int,
)
