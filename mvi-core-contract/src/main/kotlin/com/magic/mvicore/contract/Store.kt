package com.magic.mvicore.contract

@Deprecated(
    message = "Use Flow collection owned by a lifecycle or coroutine scope.",
)
fun interface Subscription {
    fun cancel()
}

@Deprecated(
    message = "PulseStore starts on construction and uses close/awaitClosed for its ordered cutoff.",
)
interface StoreLifecycle {
    val isStarted: Boolean
    val isClosed: Boolean

    @Deprecated("PulseStore starts on construction; explicit start is no longer needed.")
    fun start()

    @Deprecated("Use close() for the final ordered lifecycle cutoff.")
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
@Deprecated(
    message = "Use PulseStore/DefaultPulseStore with StateFlow and suspending send.",
    replaceWith = ReplaceWith("PulseStore<S, I, E>"),
)
interface Store<S : MviState, I : MviIntent, E : MviEffect> : StoreLifecycle {
    val currentState: S

    fun dispatch(intent: I): DispatchResult

    fun observeState(observer: (S) -> Unit): Subscription

    fun observeEffect(observer: (E) -> Unit): Subscription
}
