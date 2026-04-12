package com.magic.mvicore.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Store

@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.collectStateAsState(): State<S> {
    val state: MutableState<S> = remember(this) { mutableStateOf(currentState) }
    DisposableEffect(this) {
        val subscription = observeState { newState ->
            state.value = newState
        }
        onDispose {
            subscription.cancel()
        }
    }
    return state
}

@Composable
fun <S : MviState, I : MviIntent, E : MviEffect> Store<S, I, E>.observeEffects(
    onEffect: (E) -> Unit,
) {
    val latestOnEffect = rememberUpdatedState(onEffect)
    DisposableEffect(this) {
        val subscription = observeEffect { effect ->
            latestOnEffect.value(effect)
        }
        onDispose {
            subscription.cancel()
        }
    }
}
