package com.magic.mvicore.extensions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Projects a root [StateFlow] and emits only semantically distinct selected values.
 *
 * The selector is evaluated for the initial state and every later state update. This helper keeps
 * selection policy outside the Store engine and is suitable for non-Compose consumers and tests.
 */
fun <S, R> StateFlow<S>.selectDistinct(
    selector: (S) -> R,
    equivalent: (R, R) -> Boolean = { left, right -> left == right },
): Flow<R> = flow {
    var initialized = false
    var previous: R? = null
    collect { state ->
        val selected = selector(state)
        @Suppress("UNCHECKED_CAST")
        val changed = !initialized || !equivalent(previous as R, selected)
        if (changed) {
            initialized = true
            previous = selected
            emit(selected)
        }
    }
}
