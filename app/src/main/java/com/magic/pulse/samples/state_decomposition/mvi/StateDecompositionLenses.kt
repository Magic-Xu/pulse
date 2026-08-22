package com.magic.pulse.samples.state_decomposition.mvi

import com.magic.mvicore.extensions.stateLens

internal val imageLens = stateLens(
    get = { state: StateDecompositionState -> state.image },
    set = { state: StateDecompositionState, image: ImageDomainState -> state.copy(image = image) },
)

internal val videoLens = stateLens(
    get = { state: StateDecompositionState -> state.video },
    set = { state: StateDecompositionState, video: VideoDomainState -> state.copy(video = video) },
)
