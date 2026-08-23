package com.magic.mvicore.android

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.TaskToken
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Observation-only input carried by [PulseSplitStoreViewModel.transitions].
 *
 * These values contain no runtime completion, task token, admission lease, Store reference, or
 * mutation sink. Constructing one does not provide a command path back into the ViewModel.
 */
sealed interface PulseSplitInput<out UI : MviUiIntent, out M : MviMutation> : MviIntent {
    data class Ui<UI : MviUiIntent>(
        val value: UI,
    ) : PulseSplitInput<UI, Nothing>

    data class Mutation<M : MviMutation>(
        val value: M,
    ) : PulseSplitInput<Nothing, M>
}

internal sealed interface SplitStoreInput<out UI : MviUiIntent, out M : MviMutation> : MviIntent {
    data class Ui<UI : MviUiIntent>(
        val value: UI,
        val completion: CompletableDeferred<PulseIntentExecutionResult>?,
        val admission: SplitAdmissionLease,
    ) : SplitStoreInput<UI, Nothing>

    data class Mutation<M : MviMutation>(
        val value: M,
        val token: TaskToken?,
    ) : SplitStoreInput<Nothing, M>
}

internal class SplitAdmissionLease(
    private val onRelease: () -> Unit,
) {
    private val state = AtomicInteger(STATE_PENDING)
    private val permitReleased = AtomicBoolean(false)

    /** Marks that the Core processor started this UI frame. */
    fun claim(): Boolean {
        while (true) {
            when (val current = state.get()) {
                STATE_PENDING -> if (state.compareAndSet(current, STATE_CLAIMED)) return true
                STATE_CLAIMED,
                STATE_TRANSFERRED,
                -> return true
                STATE_CANCELLED,
                STATE_CANCEL_REQUESTED,
                STATE_RELEASED,
                -> return false
            }
        }
    }

    /** Transfers a claimed permit to the executor, or finalizes a pre-transfer cancellation. */
    fun transferToExecutor(): Boolean {
        while (true) {
            when (val current = state.get()) {
                STATE_CLAIMED -> {
                    if (state.compareAndSet(current, STATE_TRANSFERRED)) return true
                }
                STATE_CANCEL_REQUESTED -> {
                    if (state.compareAndSet(current, STATE_RELEASED)) {
                        releasePermit()
                        return false
                    }
                }
                STATE_PENDING -> {
                    if (state.compareAndSet(current, STATE_TRANSFERRED)) return true
                }
                STATE_TRANSFERRED -> return true
                STATE_CANCELLED,
                STATE_RELEASED,
                -> return false
            }
        }
    }

    /** Cancels pending executor work without releasing a permit already owned by the executor. */
    fun cancelBeforeExecutor() {
        while (true) {
            when (val current = state.get()) {
                STATE_PENDING -> {
                    if (state.compareAndSet(current, STATE_CANCELLED)) {
                        releasePermit()
                        return
                    }
                }
                STATE_CLAIMED -> {
                    if (state.compareAndSet(current, STATE_CANCEL_REQUESTED)) return
                }
                STATE_TRANSFERRED,
                STATE_CANCELLED,
                STATE_CANCEL_REQUESTED,
                STATE_RELEASED,
                -> return
            }
        }
    }

    fun release() {
        state.set(STATE_RELEASED)
        releasePermit()
    }

    private fun releasePermit() {
        if (permitReleased.compareAndSet(false, true)) onRelease()
    }

    private companion object {
        const val STATE_PENDING = 0
        const val STATE_CLAIMED = 1
        const val STATE_TRANSFERRED = 2
        const val STATE_CANCELLED = 3
        const val STATE_CANCEL_REQUESTED = 4
        const val STATE_RELEASED = 5
    }
}
