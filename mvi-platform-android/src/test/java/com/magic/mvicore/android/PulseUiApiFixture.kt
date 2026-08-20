package com.magic.mvicore.android

import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.UiEffect

/** Positive compilation fixture for the UI-visible v0.3 Android surface. */
internal class PulseUiApiFixture<
    S : MviState,
    UI : MviUiIntent,
    M : MviMutation,
    E : UiEffect,
>(
    private val viewModel: PulseSplitStoreViewModel<S, UI, M, E>,
) {
    val stateHost: PulseStateHost<S, E> = viewModel

    fun send(intent: UI) = viewModel.send(intent)

    fun trySend(intent: UI): EnqueueResult = viewModel.trySend(intent)
}
