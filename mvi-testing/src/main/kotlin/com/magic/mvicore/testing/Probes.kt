package com.magic.mvicore.testing

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.FailurePhase
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Recorded StateFlow values, including the initial snapshot. */
class StateProbe<S : MviState> internal constructor(
    private val redactor: PulseRedactor,
    private val latestSequence: () -> Long?,
) {
    private val buffer = ProbeBuffer<S>()

    internal fun record(state: S) = buffer.record(state)

    fun snapshot(): List<S> = buffer.snapshot()

    fun latest(): S = buffer.latest("No state has been recorded.")

    suspend fun awaitCount(count: Int, timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS): List<S> {
        return buffer.awaitCount(count, timeoutMillis)
    }

    suspend fun await(
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
        predicate: (S) -> Boolean,
    ): S = buffer.await(timeoutMillis, predicate)

    suspend fun awaitValue(
        expected: S,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): S = await(timeoutMillis) { it == expected }

    fun assertLatest(expected: S) {
        val actual = latest()
        assertTrue(
            expected == actual,
            "State mismatch at sequenceId=${latestSequence()}: " +
                "expected=${redactor.redact(expected)} actual=${redactor.redact(actual)}",
        )
    }

    fun assertValues(vararg expected: S) {
        val actual = snapshot()
        assertTrue(
            expected.toList() == actual,
            "State history mismatch at sequenceId=${latestSequence()}: " +
                "expected=${redactor.redact(expected.toList())} " +
                "actual=${redactor.redact(actual)}",
        )
    }
}

/** Completed transition frames in publication order. */
class TransitionProbe<S : MviState, I : MviIntent, E : UiEffect> internal constructor() {
    private val buffer = ProbeBuffer<TransitionFrame<S, I, E>>()

    internal fun record(frame: TransitionFrame<S, I, E>) = buffer.record(frame)

    fun snapshot(): List<TransitionFrame<S, I, E>> = buffer.snapshot()

    fun latest(): TransitionFrame<S, I, E> = buffer.latest("No transition has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<TransitionFrame<S, I, E>> = buffer.awaitCount(count, timeoutMillis)

    suspend fun awaitOutcome(
        outcome: TransitionOutcome,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): TransitionFrame<S, I, E> = buffer.await(timeoutMillis) { it.outcome == outcome }

    fun assertSequence(vararg expected: Long) {
        assertEquals(expected.toList(), snapshot().map { it.sequenceId })
    }

    fun assertOutcomes(vararg expected: TransitionOutcome) {
        assertEquals(expected.toList(), snapshot().map { it.outcome })
    }
}

/** UI-effect envelopes consumed by the test-owned coordinator. */
class EffectProbe<E : UiEffect> internal constructor(
    private val redactor: PulseRedactor,
) {
    private val buffer = ProbeBuffer<EffectEnvelope<E>>()

    internal fun record(effect: EffectEnvelope<E>) = buffer.record(effect)

    fun snapshot(): List<EffectEnvelope<E>> = buffer.snapshot()

    fun latest(): EffectEnvelope<E> = buffer.latest("No UI effect has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<EffectEnvelope<E>> = buffer.awaitCount(count, timeoutMillis)

    suspend fun awaitPayload(
        expected: E,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): EffectEnvelope<E> = buffer.await(timeoutMillis) { it.payload == expected }

    fun assertPayloads(vararg expected: E) {
        val actual = snapshot().map { it.payload }
        assertTrue(
            expected.toList() == actual,
            "UI effect mismatch: expected=${redactor.redact(expected.toList())} " +
                "actual=${redactor.redact(actual)}",
        )
    }
}

/** Typed runtime failures reported through the store's configured error handler. */
class FailureProbe {
    private val buffer = ProbeBuffer<PulseFailure>()

    internal fun record(failure: PulseFailure) = buffer.record(failure)

    fun snapshot(): List<PulseFailure> = buffer.snapshot()

    fun latest(): PulseFailure = buffer.latest("No Pulse failure has been recorded.")

    suspend fun awaitCount(
        count: Int,
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): List<PulseFailure> = buffer.awaitCount(count, timeoutMillis)

    suspend fun await(
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
        predicate: (PulseFailure) -> Boolean,
    ): PulseFailure = buffer.await(timeoutMillis, predicate)

    suspend inline fun <reified F : PulseFailure> awaitFailure(
        timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS,
    ): F = await(timeoutMillis) { it is F } as F

    fun assertEmpty() = assertTrue(snapshot().isEmpty(), "Expected no Pulse failures.")

    fun assertPhases(vararg expected: FailurePhase) {
        assertEquals(expected.toList(), snapshot().map { it.phase })
    }
}

private class ProbeBuffer<T> {
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

const val DEFAULT_PROBE_TIMEOUT_MILLIS: Long = 5_000L
