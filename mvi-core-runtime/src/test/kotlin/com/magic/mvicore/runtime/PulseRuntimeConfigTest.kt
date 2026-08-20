package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PulseRuntimeConfigTest {
    @Test
    fun `capacities and store id must be valid`() {
        assertFailsWith<IllegalArgumentException> {
            PulseRuntimeConfig(mailboxCapacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PulseRuntimeConfig(effectBufferCapacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PulseRuntimeConfig(storeId = "  ")
        }
    }

    @Test
    fun `runtime dependencies remain injectable`() {
        val clock = PulseClock { 42L }
        val redactor = PulseRedactor { "redacted" }
        val failures = mutableListOf<PulseFailure>()
        val handler = PulseErrorHandler { storeId, failure, configuredRedactor ->
            assertEquals("test-store", storeId)
            assertSame(redactor, configuredRedactor)
            failures += failure
        }
        val config = PulseRuntimeConfig(
            mailboxCapacity = 3,
            overflowPolicy = MailboxOverflowPolicy.REJECT,
            effectBufferCapacity = 5,
            storeDispatcher = Dispatchers.Unconfined,
            consumerDispatcher = Dispatchers.IO,
            clock = clock,
            errorHandler = handler,
            strictMode = true,
            redactor = redactor,
            storeId = "test-store",
        )
        val failure = PulseFailure.MailboxOverflow(
            context = FailureContext(component = "mailbox"),
            capacity = 3,
        )

        config.reportFailure(failure)

        assertEquals(3, config.mailboxCapacity)
        assertEquals(MailboxOverflowPolicy.REJECT, config.overflowPolicy)
        assertEquals(5, config.effectBufferCapacity)
        assertSame(Dispatchers.Unconfined, config.storeDispatcher)
        assertSame(Dispatchers.IO, config.consumerDispatcher)
        assertEquals(42L, config.clock.nanoTime())
        assertTrue(config.strictMode)
        assertEquals(listOf<PulseFailure>(failure), failures)
    }

    @Test
    fun `default diagnostics are non-silent and redact application values`() {
        val output = ByteArrayOutputStream()
        val previousError = System.err
        val secretComponent = "customer-token-123"
        val secretMessage = "database-password-456"
        val failure = PulseFailure.UiEffectConsumerFailure(
            context = FailureContext(
                requestId = 7L,
                sequenceId = 9L,
                component = secretComponent,
                inputType = "com.example.Refresh",
                thread = "pulse-main",
            ),
            cause = IllegalStateException(secretMessage),
        )

        try {
            System.setErr(PrintStream(output, true, Charsets.UTF_8.name()))
            PulseRuntimeConfig(storeId = "safe-store").reportFailure(failure)
        } finally {
            System.setErr(previousError)
        }

        val diagnostic = output.toString(Charsets.UTF_8.name())
        assertTrue(diagnostic.contains("[Pulse] store=safe-store"))
        assertTrue(diagnostic.contains("phase=UI_EFFECT_CONSUMER"))
        assertTrue(diagnostic.contains("inputType=com.example.Refresh"))
        assertTrue(diagnostic.contains("thread=pulse-main"))
        assertTrue(diagnostic.contains("cause=java.lang.IllegalStateException"))
        assertFalse(diagnostic.contains(secretComponent))
        assertFalse(diagnostic.contains(secretMessage))
    }

    @Test
    fun `strict mode propagates a broken diagnostic handler`() {
        val expected = IllegalStateException("handler failed")
        val config = PulseRuntimeConfig(
            errorHandler = PulseErrorHandler { _, _, _ -> throw expected },
            strictMode = true,
            storeId = "strict-store",
        )
        val failure = PulseFailure.MailboxOverflow(
            context = FailureContext(component = "mailbox"),
            capacity = 1,
        )

        assertSame(
            expected,
            assertFailsWith<IllegalStateException> { config.reportFailure(failure) },
        )
    }

    @Test
    fun `pulse plugin exposes identity and frame based hooks`() {
        val transitions = mutableListOf<TransitionFrame<TestState, TestIntent, TestEffect>>()
        val failures = mutableListOf<PulseFailure>()
        val plugin = object : PulseStorePlugin<TestState, TestIntent, TestEffect> {
            override val pluginId: String = "recording-plugin"

            override fun onTransition(frame: TransitionFrame<TestState, TestIntent, TestEffect>) {
                transitions += frame
            }

            override fun onFailure(failure: PulseFailure) {
                failures += failure
            }
        }
        val effect: EffectEnvelope<TestEffect> = EffectEnvelope(
            effectId = 1L,
            requestId = 1L,
            sequenceId = 2L,
            stateRevision = 1L,
            index = 0,
            payload = TestEffect.Notice,
        )
        val frame: TransitionFrame<TestState, TestIntent, TestEffect> = TransitionFrame(
            requestId = 1L,
            sequenceId = 2L,
            stateRevision = 1L,
            input = TestIntent.Refresh,
            stateBefore = TestState(0),
            stateAfter = TestState(1),
            outcome = TransitionOutcome.Changed,
            uiEffects = listOf(effect),
            startedAtNanos = 10L,
            completedAtNanos = 20L,
            dispatcher = "test",
        )
        val failure = PulseFailure.MailboxOverflow(
            context = FailureContext(requestId = 1L),
            capacity = 1,
        )

        plugin.onTransition(frame)
        plugin.onFailure(failure)

        assertEquals("recording-plugin", plugin.pluginId)
        assertEquals(listOf(frame), transitions)
        assertEquals(listOf<PulseFailure>(failure), failures)
    }

    private data class TestState(val value: Int) : MviState

    private sealed interface TestIntent : MviIntent {
        data object Refresh : TestIntent
    }

    private sealed interface TestEffect : UiEffect {
        data object Notice : TestEffect
    }
}
