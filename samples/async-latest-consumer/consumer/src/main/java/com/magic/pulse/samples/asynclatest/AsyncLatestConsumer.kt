package com.magic.pulse.samples.asynclatest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import com.magic.mvicore.android.PulseSavedState
import com.magic.mvicore.android.PulseSavedStateAdapter
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.android.compose.collectSelectedState
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.extensions.pulseMutationReducer
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class AsyncLatestViewModel(
    savedStateHandle: SavedStateHandle,
    load: suspend () -> String = {
        delay(50)
        "ready"
    },
    nextOperationId: () -> Long = System::nanoTime,
) : PulseSplitStoreViewModel<AsyncState, AsyncUiIntent, AsyncMutation, AsyncEffect>(
    initialState = AsyncState(),
    mutationReducer = pulseMutationReducer {
        on<AsyncMutation.Loading> { state, mutation ->
            ReduceOutcome.Changed(
                state.copy(
                    loading = true,
                    operationId = mutation.operationId,
                )
            )
        }
        on<AsyncMutation.Loaded> { state, mutation ->
            if (state.operationId != mutation.operationId) {
                ReduceOutcome.Ignored("stale-operation")
            } else {
                ReduceOutcome.Changed(
                    state.copy(loading = false, value = mutation.value)
                )
            }
        }
        on<AsyncMutation.Failed> { state, mutation ->
            ReduceOutcome.Changed(
                state.copy(loading = false),
                listOf(AsyncEffect.ShowFailure(mutation.message)),
            )
        }
    },
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            AsyncUiIntent.Refresh -> {
                val operationId = nextOperationId()
                context.mutate(AsyncMutation.Loading(operationId))
                context.launchTask(LOAD_TASK, TaskPolicy.Latest) {
                    try {
                        mutate(AsyncMutation.Loaded(operationId, load()))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutate(AsyncMutation.Failed(failure.message ?: "failed"))
                    }
                }
            }
        }
        PulseIntentExecutionDecision.Completed
    },
    savedState = PulseSavedState(savedStateHandle, AsyncSavedStateAdapter),
)

@Composable
fun LoadingState(
    viewModel: AsyncLatestViewModel,
    owner: LifecycleOwner,
): Boolean {
    val loading by viewModel.collectSelectedState(owner, selector = AsyncState::loading)
    return loading
}

data class AsyncState(
    val loading: Boolean = false,
    val value: String = "idle",
    val operationId: Long = 0L,
) : MviState

sealed interface AsyncUiIntent : MviUiIntent {
    data object Refresh : AsyncUiIntent
}

sealed interface AsyncMutation : MviMutation {
    data class Loading(val operationId: Long) : AsyncMutation
    data class Loaded(val operationId: Long, val value: String) : AsyncMutation
    data class Failed(val message: String) : AsyncMutation
}

sealed interface AsyncEffect : UiEffect {
    data class ShowFailure(val message: String) : AsyncEffect
}

private object AsyncSavedStateAdapter : PulseSavedStateAdapter<AsyncState> {
    override fun restore(handle: SavedStateHandle): AsyncState? {
        val value = handle.get<String>(KEY_VALUE) ?: return null
        return AsyncState(value = value)
    }

    override fun save(state: AsyncState, handle: SavedStateHandle) {
        handle[KEY_VALUE] = state.value
    }

    private const val KEY_VALUE = "value"
}

private val LOAD_TASK = TaskKey("async-latest.load")
