package com.magic.mvicore.android

import androidx.lifecycle.SavedStateHandle
import com.magic.mvicore.contract.MviState

/** Feature-owned SavedState codec; Pulse never requires the full state to be Parcelable. */
interface PulseSavedStateAdapter<S : MviState> {
    fun restore(handle: SavedStateHandle): S?

    fun save(
        state: S,
        handle: SavedStateHandle,
    )
}

/** Action taken when a feature codec cannot restore its saved state. */
enum class PulseRestoreFailurePolicy {
    /** Report the typed restore failure and construct the ViewModel from its initial state. */
    FALLBACK_TO_INITIAL_STATE,

    /** Report the typed restore failure and abort ViewModel construction. */
    FAIL_CREATION,
}

/** Binds a feature codec to the owner-provided [SavedStateHandle]. */
class PulseSavedState<S : MviState>(
    private val handle: SavedStateHandle,
    private val adapter: PulseSavedStateAdapter<S>,
    val restoreFailurePolicy: PulseRestoreFailurePolicy =
        PulseRestoreFailurePolicy.FALLBACK_TO_INITIAL_STATE,
) {
    internal fun restore(): S? = adapter.restore(handle)

    internal fun save(state: S) = adapter.save(state, handle)
}
