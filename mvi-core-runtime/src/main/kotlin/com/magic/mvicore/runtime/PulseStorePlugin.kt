package com.magic.mvicore.runtime

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.UiEffect

/** Frame-based plugin API for the ordered v0.3 runtime. */
interface PulseStorePlugin<S : MviState, I : MviIntent, E : UiEffect> {
    val pluginId: String

    fun onTransition(frame: TransitionFrame<S, I, E>) = Unit

    fun onFailure(failure: PulseFailure) = Unit
}
