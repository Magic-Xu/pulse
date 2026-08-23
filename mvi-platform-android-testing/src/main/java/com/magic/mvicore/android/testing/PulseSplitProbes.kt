package com.magic.mvicore.android.testing

import com.magic.mvicore.android.PulseSplitInput
import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.testing.DEFAULT_PROBE_TIMEOUT_MILLIS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** State values observed from a Split ViewModel, including its initial state. */
class PulseSplitStateProbe<S : MviState> internal constructor() {
    private val buffer = SplitProbeBuffer<S>()

    internal fun record(state: S) = buffer.record(state)

    fun snapshot(): List<S> = buffer.snapshot()

    fun latest(): S = buffer.latest("No Split state has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<S> = buffer.awaitCount(count, timeoutMillis)

    suspend fun awaitValue(
        expected: S,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): S = buffer.await(timeoutMillis) { it == expected }
}

/** Read-only transition frames emitted by the real Split runtime. */
class PulseSplitTransitionProbe<
    S : MviState,
    UI : MviUiIntent,
    M : MviMutation,
    E : UiEffect,
> internal constructor() {
    private val buffer = SplitProbeBuffer<TransitionFrame<S, PulseSplitInput<UI, M>, E>>()

    internal fun record(frame: TransitionFrame<S, PulseSplitInput<UI, M>, E>) = buffer.record(frame)

    fun snapshot(): List<TransitionFrame<S, PulseSplitInput<UI, M>, E>> = buffer.snapshot()

    fun latest(): TransitionFrame<S, PulseSplitInput<UI, M>, E> =
        buffer.latest("No Split transition has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<TransitionFrame<S, PulseSplitInput<UI, M>, E>> =
        buffer.awaitCount(count, timeoutMillis)
}

/** UI-effect envelopes consumed by the test host's single coordinator. */
class PulseSplitEffectProbe<E : UiEffect> internal constructor() {
    private val buffer = SplitProbeBuffer<EffectEnvelope<E>>()

    internal fun record(effect: EffectEnvelope<E>) = buffer.record(effect)

    fun snapshot(): List<EffectEnvelope<E>> = buffer.snapshot()

    fun payloads(): List<E> = snapshot().map(EffectEnvelope<E>::payload)

    fun latest(): EffectEnvelope<E> = buffer.latest("No Split UI effect has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<EffectEnvelope<E>> = buffer.awaitCount(count, timeoutMillis)

    suspend fun awaitPayload(
        expected: E,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): EffectEnvelope<E> = buffer.await(timeoutMillis) { it.payload == expected }
}

private class SplitProbeBuffer<T> {
    private val lock = Any()
    private val values = mutableListOf<T>()
    private val version = MutableStateFlow(0L)

    fun record(value: T) {
        synchronized(lock) {
            values += value
            version.value += 1L
        }
    }

    fun snapshot(): List<T> = synchronized(lock) { values.toList() }

    fun latest(message: String): T = synchronized(lock) {
        check(values.isNotEmpty()) { message }
        values.last()
    }

    suspend fun awaitCount(count: Int, timeoutMillis: Long): List<T> {
        require(count >= 0) { "count must not be negative." }
        return withTimeout(timeoutMillis) {
            var observedVersion = version.value
            while (true) {
                val current = snapshot()
                if (current.size >= count) return@withTimeout current
                version.first { it != observedVersion }
                observedVersion = version.value
            }
            error("unreachable")
        }
    }

    suspend fun await(timeoutMillis: Long, predicate: (T) -> Boolean): T {
        return withTimeout(timeoutMillis) {
            var observedVersion = version.value
            while (true) {
                snapshot().firstOrNull(predicate)?.let { return@withTimeout it }
                version.first { it != observedVersion }
                observedVersion = version.value
            }
            error("unreachable")
        }
    }
}
