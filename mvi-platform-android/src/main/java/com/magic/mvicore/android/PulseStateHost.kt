package com.magic.mvicore.android

import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.UiEffectStream
import kotlinx.coroutines.flow.StateFlow

/** Read-only state and UI-effect surface exposed by Android owners. */
interface PulseStateHost<S : MviState, E : UiEffect> {
    val state: StateFlow<S>

    val uiEffects: UiEffectStream<E>
}
