package com.magic.pulse.samples.state_decomposition.data.remote

import com.magic.pulse.samples.state_decomposition.data.model.ImageEffect
import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoTask
import kotlinx.coroutines.delay

class FakeModelRemoteService : ModelRemoteService {

    override suspend fun fetchImageModels(): List<ImageModelProfile> {
        delay(2_000)
        return listOf(
            ImageModelProfile(
                id = "img-pro-001",
                name = "VisionForge Ultra",
                recommendedEffect = ImageEffect.FACE_RECOGNITION,
                maxResolution = "4096x4096",
                strengths = listOf("人像细节", "肤色还原", "边缘精修"),
            ),
            ImageModelProfile(
                id = "img-pro-002",
                name = "StyleCanvas V2",
                recommendedEffect = ImageEffect.STYLE_TRANSFER,
                maxResolution = "3072x3072",
                strengths = listOf("风格一致性", "色彩控制", "艺术质感"),
            ),
            ImageModelProfile(
                id = "img-pro-003",
                name = "DepthCraft HD",
                recommendedEffect = ImageEffect.DEPTH_ENHANCEMENT,
                maxResolution = "2048x2048",
                strengths = listOf("景深推理", "主体分离", "背景虚化"),
            ),
        )
    }

    override suspend fun fetchVideoModels(): List<VideoModelProfile> {
        delay(2_000)
        return listOf(
            VideoModelProfile(
                id = "vid-pro-001",
                name = "MotionPilot X",
                recommendedTask = VideoTask.MOTION_TRACKING,
                maxFps = 60,
                maxDurationSeconds = 120,
            ),
            VideoModelProfile(
                id = "vid-pro-002",
                name = "FlowFrame Studio",
                recommendedTask = VideoTask.FRAME_INTERPOLATION,
                maxFps = 120,
                maxDurationSeconds = 45,
            ),
            VideoModelProfile(
                id = "vid-pro-003",
                name = "CaptionSense Pro",
                recommendedTask = VideoTask.AUTO_SUBTITLE,
                maxFps = 30,
                maxDurationSeconds = 300,
            ),
            VideoModelProfile(
                id = "vid-pro-004",
                name = "SteadyShot AI",
                recommendedTask = VideoTask.STABILIZATION_ENHANCE,
                maxFps = 60,
                maxDurationSeconds = 180,
            ),
        )
    }
}
