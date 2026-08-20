package com.magic.mvicore.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PulseContractTest {
    @Test
    fun `changed snapshots ui effect input`() {
        val source = mutableListOf<SampleUiEffect>(SampleUiEffect.Notice("first"))

        val outcome = ReduceOutcome.Changed(
            state = SampleState(1),
            uiEffects = source,
        )
        source += SampleUiEffect.Notice("second")

        assertEquals(listOf(SampleUiEffect.Notice("first")), outcome.uiEffects)
    }

    @Test
    fun `unchanged snapshots ui effect input`() {
        val source = mutableListOf<SampleUiEffect>(SampleUiEffect.Notice("first"))

        val outcome = ReduceOutcome.Unchanged(source)
        source.clear()

        assertEquals(listOf(SampleUiEffect.Notice("first")), outcome.uiEffects)
    }

    @Test
    fun `ignored outcome cannot carry ui effects`() {
        val outcome = ReduceOutcome.Ignored(reason = "not applicable")

        assertTrue(outcome.uiEffects.isEmpty())
    }

    @Test
    fun `pulse reducer exposes explicit outcome`() {
        val reducer = PulseReducer<SampleState, SampleIntent, SampleUiEffect> { previous, input ->
            when (input) {
                SampleIntent.Increment -> ReduceOutcome.Changed(previous.copy(value = previous.value + 1))
                SampleIntent.Refresh -> ReduceOutcome.Unchanged(listOf(SampleUiEffect.Notice("refresh")))
            }
        }

        assertIs<ReduceOutcome.Changed<SampleState, SampleUiEffect>>(
            reducer.reduce(SampleState(0), SampleIntent.Increment)
        )
        assertIs<ReduceOutcome.Unchanged<SampleUiEffect>>(
            reducer.reduce(SampleState(0), SampleIntent.Refresh)
        )
    }

    @Test
    fun `transition frame snapshots effect envelopes`() {
        val source = mutableListOf(
            EffectEnvelope(
                effectId = 1,
                requestId = 7,
                sequenceId = 3,
                stateRevision = 2,
                index = 0,
                payload = SampleUiEffect.Notice("done"),
            )
        )

        val frame = TransitionFrame(
            requestId = 7,
            sequenceId = 3,
            stateRevision = 2,
            input = SampleIntent.Refresh,
            stateBefore = SampleState(1),
            stateAfter = SampleState(1),
            outcome = TransitionOutcome.Unchanged,
            uiEffects = source,
            startedAtNanos = 10,
            completedAtNanos = 20,
            dispatcher = "test",
        )
        source.clear()

        assertEquals(1, frame.uiEffects.size)
        assertEquals(SampleUiEffect.Notice("done"), frame.uiEffects.single().payload)
    }

    @Test
    fun `failure variants own their declared phase`() {
        val context = FailureContext(requestId = 9, sequenceId = 4)
        val cause = IllegalStateException("boom")

        val failures = listOf(
            PulseFailure.ReducerFailure(context, cause) to FailurePhase.REDUCER,
            PulseFailure.StateConsumerFailure(context, cause) to FailurePhase.STATE_CONSUMER,
            PulseFailure.UiEffectConsumerFailure(context, cause) to FailurePhase.UI_EFFECT_CONSUMER,
            PulseFailure.PluginFailure(context, cause) to FailurePhase.PLUGIN,
            PulseFailure.ExecutorFailure(context, cause) to FailurePhase.EXECUTOR,
            PulseFailure.MailboxOverflow(context, capacity = 16) to FailurePhase.OVERFLOW,
            PulseFailure.UndeliveredUiEffect(
                context = context,
                envelope = EffectEnvelope(
                    effectId = 1,
                    requestId = 9,
                    sequenceId = 4,
                    stateRevision = 2,
                    index = 0,
                    payload = SampleUiEffect.Notice("lost"),
                ),
                reason = "no coordinator",
            ) to FailurePhase.UNDELIVERED_UI_EFFECT,
            PulseFailure.LateMutation(context, taskKey = "refresh", token = 11) to FailurePhase.LATE_MUTATION,
            PulseFailure.StateRestoreFailure(context, cause) to FailurePhase.RESTORE,
            PulseFailure.StateSaveFailure(context, cause) to FailurePhase.SAVE,
        )

        failures.forEach { (failure, phase) ->
            assertEquals(phase, failure.phase)
        }
    }

    @Test
    fun `result contracts separate completion admission and rejection`() {
        val enqueued: EnqueueResult = EnqueueResult.Enqueued(requestId = 1)
        val full: EnqueueResult = EnqueueResult.Full
        val rejected: TransitionResult<SampleState, SampleIntent, SampleUiEffect> =
            TransitionResult.Rejected(RejectionReason.Closing)

        assertIs<EnqueueResult.Enqueued>(enqueued)
        assertIs<EnqueueResult.Full>(full)
        assertIs<TransitionResult.Rejected>(rejected)
    }

    @Test
    fun `accepted task exposes only stable id and terminal outcome observation`() {
        val expected = TaskOutcome.Replaced(TaskReplacementReason.CONFLATED)
        val handle = object : TaskHandle {
            override val requestId: Long = 17

            override suspend fun awaitOutcome(): TaskOutcome = expected
        }

        val accepted = TaskLaunchResult.Accepted(handle)

        assertEquals(17, accepted.handle.requestId)
        assertSame(handle, accepted.handle)
        assertFailsWith<IllegalArgumentException> { TaskKey(" ") }
    }

    private data class SampleState(val value: Int) : MviState

    private sealed interface SampleIntent : MviIntent {
        data object Increment : SampleIntent
        data object Refresh : SampleIntent
    }

    private sealed interface SampleUiEffect : UiEffect {
        data class Notice(val message: String) : SampleUiEffect
    }
}
