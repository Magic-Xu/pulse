package com.magic.mvicore.runtime

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyCallbackHubTest {
    @Test
    fun `reentrant publication stays serialized for every callback`() = runTest {
        val hub = newHub()
        val deliveries = mutableListOf<String>()

        hub.registerState(0L, State(0)) { state ->
            deliveries += "first:${state.value}"
            if (state.value == 1) {
                hub.publishState(2L, State(2), stateChanged = true)
            }
        }
        hub.registerState(0L, State(0)) { state ->
            deliveries += "second:${state.value}"
        }
        testScheduler.runCurrent()
        deliveries.clear()

        hub.publishState(1L, State(1), stateChanged = true)
        testScheduler.runCurrent()

        assertEquals(
            listOf("first:1", "second:1", "first:2", "second:2"),
            deliveries,
        )
        hub.closeAndAwait()
    }

    @Test
    fun `ordinary callback failures are typed and isolated`() = runTest {
        val failures = mutableListOf<PulseFailure>()
        val hub = newHub(failures::add)
        val states = mutableListOf<State>()
        val effects = mutableListOf<Effect>()

        hub.registerState(0L, State(0)) { state ->
            if (state.value == 1) error("broken state callback")
        }
        hub.registerState(0L, State(0), states::add)
        hub.registerEffect { throw IllegalArgumentException("broken effect callback") }
        hub.registerEffect(effects::add)
        testScheduler.runCurrent()
        states.clear()

        hub.publishState(1L, State(1), stateChanged = true)
        hub.publishEffect(1L, Effect.Done)
        testScheduler.runCurrent()

        assertEquals(listOf(State(1)), states)
        assertEquals(listOf<Effect>(Effect.Done), effects)
        assertEquals(2, failures.size)
        assertIs<PulseFailure.StateConsumerFailure>(failures[0])
        assertEquals(1L, failures[0].context.sequenceId)
        assertIs<PulseFailure.UiEffectConsumerFailure>(failures[1])
        assertEquals(1L, failures[1].context.sequenceId)
        hub.closeAndAwait()
    }

    @Test
    fun `registration chooses the newer accepted snapshot without duplicate delivery`() = runTest {
        val hub = newHub()
        val staleRegistration = mutableListOf<State>()
        val aheadRegistration = mutableListOf<State>()
        val existing = mutableListOf<State>()

        hub.registerState(0L, State(0), existing::add)
        hub.publishState(1L, State(1), stateChanged = true)
        hub.registerState(0L, State(0), staleRegistration::add)
        hub.registerState(2L, State(2), aheadRegistration::add)
        hub.publishState(2L, State(2), stateChanged = true)
        testScheduler.runCurrent()

        assertEquals(listOf(State(1), State(2)), staleRegistration)
        assertEquals(listOf(State(2)), aheadRegistration)
        assertEquals(listOf(State(0), State(1), State(2)), existing)
        hub.closeAndAwait()
    }

    @Test
    fun `cancel prevents callbacks that have not started`() = runTest {
        val hub = newHub()
        val states = mutableListOf<State>()
        val effects = mutableListOf<Effect>()
        val stateSubscription = hub.registerState(0L, State(0), states::add)
        val effectSubscription = hub.registerEffect(effects::add)
        testScheduler.runCurrent()
        states.clear()

        hub.publishState(1L, State(1), stateChanged = true)
        hub.publishEffect(1L, Effect.Done)
        stateSubscription.cancel()
        effectSubscription.cancel()
        testScheduler.runCurrent()

        assertEquals(emptyList(), states)
        assertEquals(emptyList(), effects)
        hub.closeAndAwait()
    }

    @Test
    fun `close drains accepted work and rejects later registration`() = runTest {
        val hub = newHub()
        val accepted = mutableListOf<State>()
        val rejected = mutableListOf<State>()
        hub.registerState(0L, State(0), accepted::add)
        hub.publishState(1L, State(1), stateChanged = true)

        hub.close()
        hub.registerState(1L, State(1), rejected::add)
        hub.publishState(2L, State(2), stateChanged = true)
        hub.awaitClosed()

        assertEquals(listOf(State(0), State(1)), accepted)
        assertEquals(emptyList(), rejected)
    }

    @Test
    fun `flush observes callback cancellation instead of waiting forever`() = runTest {
        val failures = mutableListOf<PulseFailure>()
        val cancellation = CancellationException("stop")
        val hub = newHub(failures::add)
        hub.registerEffect { throw cancellation }
        testScheduler.runCurrent()

        hub.publishEffect(1L, Effect.Done)
        testScheduler.runCurrent()

        val thrown = assertFailsWith<CancellationException> {
            withTimeout(TIMEOUT_MILLIS) { hub.flush() }
        }
        assertEquals(cancellation.message, thrown.message)
        assertEquals(emptyList(), failures)

        val closed = assertFailsWith<CancellationException> {
            withTimeout(TIMEOUT_MILLIS) { hub.awaitClosed() }
        }
        assertEquals(cancellation.message, closed.message)
    }

    @Test
    fun `flush observes fatal callback failure instead of waiting forever`() = runTest {
        val failures = mutableListOf<PulseFailure>()
        val fatal = LinkageError("fatal")
        val hub = newHub(failures::add)
        hub.registerState(0L, State(0)) { state ->
            if (state.value == 1) throw fatal
        }
        testScheduler.runCurrent()

        hub.publishState(1L, State(1), stateChanged = true)
        testScheduler.runCurrent()

        val thrown = assertFailsWith<LinkageError> {
            withTimeout(TIMEOUT_MILLIS) { hub.flush() }
        }
        assertEquals(fatal.message, thrown.message)
        assertEquals(emptyList(), failures)

        val closed = assertFailsWith<LinkageError> {
            withTimeout(TIMEOUT_MILLIS) { hub.awaitClosed() }
        }
        assertEquals(fatal.message, closed.message)
    }

    private fun kotlinx.coroutines.test.TestScope.newHub(
        failureReporter: (PulseFailure) -> Unit = {},
    ): LegacyCallbackHub<State, Effect> {
        return LegacyCallbackHub(
            dispatcher = StandardTestDispatcher(testScheduler),
            failureReporter = failureReporter,
            storeId = "test-store",
        )
    }

    private suspend fun LegacyCallbackHub<State, Effect>.closeAndAwait() {
        close()
        awaitClosed()
    }

    private data class State(val value: Int) : MviState

    private sealed interface Effect : MviEffect {
        data object Done : Effect
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
