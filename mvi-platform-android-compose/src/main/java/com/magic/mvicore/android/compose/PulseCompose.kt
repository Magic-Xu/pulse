package com.magic.mvicore.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.magic.mvicore.android.PulseStateHost
import com.magic.mvicore.android.PulseViewModelCreator
import com.magic.mvicore.android.pulseViewModelFactory
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.flow.collect

/** Compose owner lookup with no implicit Activity or navigation-owner fallback. */
@Composable
fun <VM : ViewModel> pulseViewModel(
    owner: ViewModelStoreOwner,
    key: String,
    modelClass: Class<VM>,
    creator: PulseViewModelCreator<VM>,
): VM {
    require(key.isNotBlank()) { "ViewModel key must not be blank." }
    val latestCreator = rememberUpdatedState(creator)
    val factory = remember(modelClass) {
        pulseViewModelFactory(modelClass) { extras ->
            latestCreator.value.create(extras)
        }
    }
    return viewModel(
        modelClass = modelClass,
        viewModelStoreOwner = owner,
        key = key,
        factory = factory,
    )
}

/** Lifecycle-aware whole-state collection for a v0.3 Android host. */
@Composable
fun <S : MviState, E : UiEffect> PulseStateHost<S, E>.collectStateAsStateWithLifecycle(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<S> {
    requireForegroundLifecycle(minActiveState)
    return state.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = minActiveState,
    )
}

/**
 * Lifecycle-aware selector collection. Equality is evaluated only on the selected value, so an
 * unrelated root-state change does not invalidate the caller.
 */
@Composable
fun <S : MviState, E : UiEffect, R> PulseStateHost<S, E>.collectSelectedState(
    lifecycleOwner: LifecycleOwner,
    selector: (S) -> R,
    equivalent: (R, R) -> Boolean = { left, right -> left == right },
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<R> {
    requireForegroundLifecycle(minActiveState)
    val latestSelector = rememberUpdatedState(selector)
    val latestEquivalent = rememberUpdatedState(equivalent)
    val selectedState = remember(state, lifecycleOwner, minActiveState) {
        mutableStateOf(selector(state.value))
    }
    SideEffect {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(minActiveState)) {
            val selected = selector(state.value)
            if (!equivalent(selectedState.value, selected)) {
                selectedState.value = selected
            }
        }
    }
    LaunchedEffect(state, lifecycleOwner, minActiveState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            state.collect { root ->
                val selected = latestSelector.value(root)
                if (!latestEquivalent.value(selectedState.value, selected)) {
                    selectedState.value = selected
                }
            }
        }
    }
    return selectedState
}

/**
 * Connects the host's single UI-effect coordinator only while [lifecycleOwner] is active.
 * Effects are replay-zero; a new STARTED session never receives effects from an earlier session.
 */
@Composable
fun <S : MviState, E : UiEffect> PulseStateHost<S, E>.ObserveUiEffects(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit,
) {
    requireForegroundLifecycle(minActiveState)
    val latestOnEffect = rememberUpdatedState(onEffect)
    LaunchedEffect(this, lifecycleOwner, minActiveState) {
        collectUiEffectsWithLifecycle(lifecycleOwner, minActiveState) { effect ->
            latestOnEffect.value(effect)
        }
    }
}

internal suspend fun <S : MviState, E : UiEffect> PulseStateHost<S, E>.collectUiEffectsWithLifecycle(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State,
    onEffect: suspend (E) -> Unit,
) {
    requireForegroundLifecycle(minActiveState)
    lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
        uiEffects.collect { envelope -> onEffect(envelope.payload) }
    }
}

private fun requireForegroundLifecycle(minActiveState: Lifecycle.State) {
    require(
        minActiveState == Lifecycle.State.STARTED ||
            minActiveState == Lifecycle.State.RESUMED
    ) {
        "Pulse UI collection requires STARTED or RESUMED lifecycle state."
    }
}
