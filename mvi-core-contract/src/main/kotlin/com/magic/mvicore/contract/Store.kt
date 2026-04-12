package com.magic.mvicore.contract

fun interface Subscription {
    fun cancel()
}

interface StoreLifecycle {
    val isStarted: Boolean
    val isClosed: Boolean

    fun start()
    fun stop()
    fun close()
}

sealed interface StoreError {
    data object StoreNotStarted : StoreError
    data object StoreClosed : StoreError
    data class ReducerFailure(val cause: Throwable) : StoreError
}

sealed interface DispatchResult {
    data object Accepted : DispatchResult
    data class Rejected(val error: StoreError) : DispatchResult
}

/**
 * Platform-neutral Store contract. No Android dependency.
 */
interface Store<S : MviState, I : MviIntent, E : MviEffect> : StoreLifecycle {
    val currentState: S

    fun dispatch(intent: I): DispatchResult

    fun observeState(observer: (S) -> Unit): Subscription

    fun observeEffect(observer: (E) -> Unit): Subscription
}
