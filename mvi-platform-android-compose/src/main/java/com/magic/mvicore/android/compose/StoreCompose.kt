package com.magic.mvicore.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Store
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect

/** Legacy Store bridge. New v0.3 hosts should use [collectStateAsStateWithLifecycle]. */
@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.collectStateAsState(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): State<S> {
    requireLegacyForegroundLifecycle(minActiveState)
    return produceState(
        initialValue = currentState,
        this,
        lifecycleOwner,
        minActiveState,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            callbackFlow {
                val subscription = observeState { state -> trySend(state) }
                awaitClose(subscription::cancel)
            }.collect { state -> value = state }
        }
    }
}

/** Source-compatible v0.2 overload using the current composition owner. */
@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.collectStateAsState(): State<S> {
    return collectStateAsState(lifecycleOwner = LocalLifecycleOwner.current)
}

/**
 * Legacy effect bridge. It subscribes only while the explicit owner is active, so effects are
 * replay-zero across STOP/START boundaries.
 */
@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.observeEffects(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: (E) -> Unit,
) {
    requireLegacyForegroundLifecycle(minActiveState)
    val latestOnEffect = rememberUpdatedState(onEffect)
    LaunchedEffect(this, lifecycleOwner, minActiveState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            callbackFlow {
                val subscription = observeEffect { effect -> trySend(effect) }
                awaitClose(subscription::cancel)
            }.collect { effect -> latestOnEffect.value(effect) }
        }
    }
}

/** Source-compatible v0.2 overload using the current composition owner. */
@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.observeEffects(
    onEffect: (E) -> Unit,
) {
    observeEffects(
        lifecycleOwner = LocalLifecycleOwner.current,
        onEffect = onEffect,
    )
}

private fun requireLegacyForegroundLifecycle(minActiveState: Lifecycle.State) {
    require(
        minActiveState == Lifecycle.State.STARTED ||
            minActiveState == Lifecycle.State.RESUMED
    ) {
        "Pulse UI collection requires STARTED or RESUMED lifecycle state."
    }
}
