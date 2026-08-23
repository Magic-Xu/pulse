@file:Suppress("DEPRECATION", "unused")

package com.magic.pulse.compat.v03.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseIntentExecutionResult
import com.magic.mvicore.android.PulseSavedStateAdapter
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.android.androidPulseRuntimeConfig
import com.magic.mvicore.android.compose.ObserveUiEffects
import com.magic.mvicore.android.compose.collectSelectedState
import com.magic.mvicore.android.compose.collectStateAsStateWithLifecycle
import com.magic.mvicore.android.compose.pulseViewModel
import com.magic.mvicore.android.pulseSavedStateViewModelFactory
import com.magic.mvicore.android.pulseViewModelFactory
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.UiEffect

private val LegacyMutationReducer = PulseMutationReducer<
    LegacyAndroidState,
    LegacyAndroidMutation,
    LegacyAndroidEffect,
> { previous, mutation ->
    when (mutation) {
        LegacyAndroidMutation.Loaded -> ReduceOutcome.Changed(
            previous.copy(loaded = true),
            listOf(LegacyAndroidEffect.Loaded),
        )
    }
}

private val LegacyExecutor = PulseUiIntentExecutor<
    LegacyAndroidState,
    LegacyAndroidIntent,
    LegacyAndroidMutation,
> { intent, context ->
    context.stateAtStart
    context.currentState
    when (intent) {
        LegacyAndroidIntent.Load -> {
            val launch: TaskLaunchResult = context.launchTask(
                key = TaskKey("load"),
                policy = TaskPolicy.Latest,
            ) {
                currentState
                mutate(LegacyAndroidMutation.Loaded)
            }
            check(launch is TaskLaunchResult.Accepted || launch is TaskLaunchResult.Closed)
            PulseIntentExecutionDecision.Completed
        }
    }
}

open class LegacySplitStoreViewModel : PulseSplitStoreViewModel<
    LegacyAndroidState,
    LegacyAndroidIntent,
    LegacyAndroidMutation,
    LegacyAndroidEffect,
>(
    initialState = LegacyAndroidState(),
    mutationReducer = LegacyMutationReducer,
    uiIntentExecutor = LegacyExecutor,
    runtimeConfig = androidPulseRuntimeConfig(),
) {
    fun enqueue(): EnqueueResult = trySend(LegacyAndroidIntent.Load)

    suspend fun execute(): PulseIntentExecutionResult = send(LegacyAndroidIntent.Load)

    override fun onPulseCleared() = Unit
}

object LegacySavedStateAdapter : PulseSavedStateAdapter<LegacyAndroidState> {
    override fun restore(handle: SavedStateHandle): LegacyAndroidState? {
        val loaded: Boolean? = handle["loaded"]
        return loaded?.let(::LegacyAndroidState)
    }

    override fun save(
        state: LegacyAndroidState,
        handle: SavedStateHandle,
    ) {
        handle["loaded"] = state.loaded
    }
}

fun legacyFactory(): ViewModelProvider.Factory = pulseViewModelFactory(
    modelClass = LegacySplitStoreViewModel::class.java,
    creator = { LegacySplitStoreViewModel() },
)

fun legacySavedStateFactory(): ViewModelProvider.Factory = pulseSavedStateViewModelFactory(
    modelClass = LegacySplitStoreViewModel::class.java,
    creator = { LegacySplitStoreViewModel() },
)

@Composable
fun LegacyComposeSurface(
    owner: ViewModelStoreOwner,
    lifecycleOwner: LifecycleOwner,
) {
    val viewModel = pulseViewModel(
        owner = owner,
        key = "compat-v03",
        modelClass = LegacySplitStoreViewModel::class.java,
        creator = { LegacySplitStoreViewModel() },
    )
    val state: State<LegacyAndroidState> =
        viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
    val loaded: State<Boolean> = viewModel.collectSelectedState(
        lifecycleOwner = lifecycleOwner,
        selector = LegacyAndroidState::loaded,
    )
    state.value
    loaded.value
    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        check(effect == LegacyAndroidEffect.Loaded)
    }
}

data class LegacyAndroidState(
    val loaded: Boolean = false,
) : MviState

sealed interface LegacyAndroidIntent : MviUiIntent {
    data object Load : LegacyAndroidIntent
}

sealed interface LegacyAndroidMutation : MviMutation {
    data object Loaded : LegacyAndroidMutation
}

sealed interface LegacyAndroidEffect : UiEffect {
    data object Loaded : LegacyAndroidEffect
}
