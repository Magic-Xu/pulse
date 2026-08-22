package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.extensions.pulseMutationReducer
import com.magic.pulse.samples.state_decomposition.data.model.ImageEffect
import com.magic.pulse.samples.state_decomposition.data.model.ImageModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoModelProfile
import com.magic.pulse.samples.state_decomposition.data.model.VideoTask

enum class DomainType {
    IMAGE,
    VIDEO,
}

data class ImageDomainState(
    val isLoading: Boolean = false,
    val models: List<ImageModelProfile> = emptyList(),
    val selectedModelId: String? = null,
    val selectedEffect: ImageEffect = ImageEffect.FACE_RECOGNITION,
    val stylePreset: String = "电影感",
    val depthStrength: Int = 60,
    val facePriority: Boolean = true,
    val guidanceScale: Float = 7.5f,
    val lastSyncLabel: String? = null,
)

data class VideoDomainState(
    val isLoading: Boolean = false,
    val models: List<VideoModelProfile> = emptyList(),
    val selectedModelId: String? = null,
    val selectedTask: VideoTask = VideoTask.MOTION_TRACKING,
    val stabilizationLevel: String = "Balanced",
    val interpolationFrames: Int = 2,
    val outputFps: Int = 30,
    val clipLengthSeconds: Int = 8,
    val autoSubtitle: Boolean = true,
    val lastSyncLabel: String? = null,
)

data class StateDecompositionState(
    val image: ImageDomainState = ImageDomainState(),
    val video: VideoDomainState = VideoDomainState(),
) : MviState

sealed interface StateDecompositionUiIntent : MviUiIntent {
    data object LoadImageModelsClicked : StateDecompositionUiIntent
    data object LoadVideoModelsClicked : StateDecompositionUiIntent
    data class SelectImageEffect(val effect: ImageEffect) : StateDecompositionUiIntent
    data class SelectVideoTask(val task: VideoTask) : StateDecompositionUiIntent
}

sealed interface StateDecompositionMutation : MviMutation {

    sealed interface Image : StateDecompositionMutation {
        data object LoadStarted : Image
        data class ModelsLoaded(val models: List<ImageModelProfile>) : Image
        data class EffectChanged(val effect: ImageEffect) : Image
    }

    sealed interface Video : StateDecompositionMutation {
        data object LoadStarted : Video
        data class ModelsLoaded(val models: List<VideoModelProfile>) : Video
        data class TaskChanged(val task: VideoTask) : Video
    }

    data class DomainFailed(val domain: DomainType, val message: String) : StateDecompositionMutation
}

sealed interface StateDecompositionEffect : UiEffect {
    data class ShowMessage(val text: String) : StateDecompositionEffect
}

val StateDecompositionReducer: PulseMutationReducer<
    StateDecompositionState,
    StateDecompositionMutation,
    StateDecompositionEffect
> = pulseMutationReducer {
    installImageDomainReducer()
    installVideoDomainReducer()
    installDomainFailureReducer()
}
