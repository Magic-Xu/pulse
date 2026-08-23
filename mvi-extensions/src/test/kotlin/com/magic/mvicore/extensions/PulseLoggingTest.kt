package com.magic.mvicore.extensions

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionFrame
import com.magic.mvicore.contract.TransitionOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.PulseStorePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class PulseLoggingTest {
    @Test
    fun `pulse plugin logs DefaultPulseStore transitions without exposing values`() = runBlocking {
        val lines = mutableListOf<String>()
        val plugin: PulseStorePlugin<SecretState, SecretIntent, SecretEffect> = PulseLoggingPlugin(
            tag = "TEST",
            sink = LogSink(lines::add),
        )
        val store = DefaultPulseStore(
            initialState = SecretState("state-before-secret"),
            reducer = PulseReducer { _, _ ->
                ReduceOutcome.Changed(
                    state = SecretState("state-after-secret"),
                )
            },
            config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Unconfined,
                consumerDispatcher = Dispatchers.Unconfined,
                errorHandler = PulseErrorHandler { _, _, _ -> },
            ),
            plugins = listOf(plugin),
        )

        store.send(SecretIntent("intent-secret"))
        store.close()
        store.awaitClosed()

        assertEquals(1, lines.size)
        val line = lines.single()
        assertContains(line, "[TEST] transition")
        assertContains(line, "outcome=Changed")
        assertContains(line, "SecretState")
        assertContains(line, "SecretIntent")
        assertFalse(line.contains("state-before-secret"))
        assertFalse(line.contains("state-after-secret"))
        assertFalse(line.contains("intent-secret"))
    }

    @Test
    fun `pulse plugin redacts failure strings and omits throwable messages`() {
        val lines = mutableListOf<String>()
        val plugin = PulseLoggingPlugin<SecretState, SecretIntent, SecretEffect>(
            sink = LogSink(lines::add),
        )
        val envelope = EffectEnvelope(
            effectId = 19L,
            requestId = 7L,
            sequenceId = 8L,
            stateRevision = 9L,
            index = 0,
            payload = SecretEffect("undelivered-effect-secret"),
        )

        plugin.onFailure(
            PulseFailure.UndeliveredUiEffect(
                context = secretFailureContext(),
                envelope = envelope,
                reason = "undelivered-reason-secret",
            )
        )
        plugin.onFailure(
            PulseFailure.ExecutorFailure(
                context = secretFailureContext(),
                cause = IllegalStateException("throwable-message-secret"),
            )
        )
        plugin.onFailure(
            PulseFailure.TaskFailure(
                context = secretFailureContext(),
                taskKey = "task-key-secret",
                token = 23L,
                cause = IllegalArgumentException("task-message-secret"),
            )
        )

        assertEquals(3, lines.size)
        val output = lines.joinToString("\n")
        assertContains(output, "phase=UNDELIVERED_UI_EFFECT")
        assertContains(output, "effectId=19")
        assertContains(output, "SecretEffect")
        assertContains(output, "phase=EXECUTOR")
        assertContains(output, "cause=java.lang.IllegalStateException")
        assertContains(output, "phase=TASK")
        assertContains(output, "token=23")
        assertContains(output, "cause=java.lang.IllegalArgumentException")
        listOf(
            "store-id-secret",
            "component-secret",
            "input-type-secret",
            "thread-secret",
            "undelivered-effect-secret",
            "undelivered-reason-secret",
            "throwable-message-secret",
            "task-key-secret",
            "task-message-secret",
        ).forEach { secret ->
            assertFalse(output.contains(secret), "Log output leaked $secret")
        }
    }

    @Test
    fun `flow logging operator is lazy transparent and uses the configured redactor`() = runBlocking {
        val lines = mutableListOf<String>()
        val frame = transitionFrame(
            outcome = TransitionOutcome.Ignored("ignored-reason-secret"),
        )
        val logged = flowOf(frame).logPulseTransitions(
            tag = "FLOW",
            sink = LogSink(lines::add),
            redactor = { value -> "redacted:${value?.javaClass?.simpleName ?: "null"}" },
        )

        assertEquals(emptyList(), lines)
        assertSame(frame, logged.single())

        assertEquals(1, lines.size)
        val line = lines.single()
        assertContains(line, "[FLOW] transition")
        assertContains(line, "outcome=Ignored(reason=redacted:String)")
        assertContains(line, "input=redacted:SecretIntent")
        assertFalse(line.contains("ignored-reason-secret"))
    }

    @Test
    fun `transition logging never renders reducer failure or dispatcher values directly`() {
        val lines = mutableListOf<String>()
        val plugin = PulseLoggingPlugin<SecretState, SecretIntent, SecretEffect>(
            sink = LogSink(lines::add),
        )
        val failure = PulseFailure.ReducerFailure(
            context = secretFailureContext(),
            cause = IllegalArgumentException("reducer-message-secret"),
        )
        val frame = transitionFrame(
            outcome = TransitionOutcome.ReducerFailed,
            reducerFailure = failure,
        )

        plugin.onTransition(frame)

        val line = lines.single()
        assertContains(line, "outcome=ReducerFailed")
        assertContains(line, "reducerFailure=java.lang.IllegalArgumentException")
        assertFalse(line.contains("reducer-message-secret"))
        assertFalse(line.contains("dispatcher-secret"))
    }

    private fun transitionFrame(
        outcome: TransitionOutcome,
        reducerFailure: PulseFailure.ReducerFailure? = null,
    ): TransitionFrame<SecretState, SecretIntent, SecretEffect> {
        return TransitionFrame(
            requestId = 7L,
            sequenceId = 8L,
            stateRevision = 9L,
            input = SecretIntent("intent-secret"),
            stateBefore = SecretState("state-before-secret"),
            stateAfter = SecretState("state-after-secret"),
            outcome = outcome,
            uiEffects = listOf(
                EffectEnvelope(
                    effectId = 19L,
                    requestId = 7L,
                    sequenceId = 8L,
                    stateRevision = 9L,
                    index = 0,
                    payload = SecretEffect("effect-secret"),
                )
            ),
            startedAtNanos = 100L,
            completedAtNanos = 125L,
            dispatcher = "dispatcher-secret",
            reducerFailure = reducerFailure,
            mailboxDepthAtStart = 2,
            mailboxHighWater = 4,
        )
    }

    private fun secretFailureContext(): FailureContext {
        return FailureContext(
            storeId = "store-id-secret",
            requestId = 7L,
            sequenceId = 8L,
            stateRevision = 9L,
            component = "component-secret",
            inputType = "input-type-secret",
            thread = "thread-secret",
        )
    }

    private data class SecretState(val secret: String) : MviState

    private data class SecretIntent(val secret: String) : MviIntent

    private data class SecretEffect(val secret: String) : UiEffect
}
