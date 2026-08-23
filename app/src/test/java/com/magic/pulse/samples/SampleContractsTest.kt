package com.magic.pulse.samples

import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.RejectionReason
import com.magic.mvicore.contract.TaskHandle
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.pulse.samples.common.SampleIngressFailure
import com.magic.pulse.samples.common.toSampleExecutionDecision
import com.magic.pulse.samples.common.toSampleIngressFailure
import com.magic.pulse.samples.network.data.model.VideoModel
import com.magic.pulse.samples.network.mvi.LoadingTarget
import com.magic.pulse.samples.network.mvi.NetworkLoadError
import com.magic.pulse.samples.network.mvi.NetworkModelsEffect
import com.magic.pulse.samples.network.mvi.NetworkModelsMutation
import com.magic.pulse.samples.network.mvi.NetworkModelsReducer
import com.magic.pulse.samples.network.mvi.NetworkModelsState
import com.magic.pulse.samples.network.mvi.NetworkModelsUpdate
import com.magic.pulse.samples.split_intent_basic.mvi.BasicLoadError
import com.magic.pulse.samples.split_intent_basic.mvi.BasicLoadingTarget
import com.magic.pulse.samples.split_intent_basic.mvi.BasicOperation
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicEffect
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicMutation
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicReducer
import com.magic.pulse.samples.split_intent_basic.mvi.SplitIntentBasicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleContractsTest {
    @Test
    fun `accepted task means executor handled and does not await task outcome`() {
        val handle = object : TaskHandle {
            override val requestId: Long = 41L

            override suspend fun awaitOutcome(): TaskOutcome {
                error("The executor decision must not await the background task")
            }
        }

        val decision = TaskLaunchResult.Accepted(handle).toSampleExecutionDecision()

        assertSame(PulseIntentExecutionDecision.Completed, decision)
    }

    @Test
    fun `all non-accepted task results become explicit ignored decisions`() {
        val results = listOf(
            TaskLaunchResult.DroppedWhileRunning,
            TaskLaunchResult.QueueFull(capacity = 2),
            TaskLaunchResult.ParallelLimitReached(maxConcurrency = 3),
            TaskLaunchResult.Closed,
        )

        results.forEach { result ->
            assertTrue(result.toSampleExecutionDecision() is PulseIntentExecutionDecision.Ignored)
        }
    }

    @Test
    fun `callback admission failures map to visible sample feedback`() {
        assertNull(EnqueueResult.Enqueued(requestId = 1L).toSampleIngressFailure())
        assertEquals(
            SampleIngressFailure.CAPACITY_REACHED,
            EnqueueResult.Full.toSampleIngressFailure(),
        )
        assertEquals(
            SampleIngressFailure.SCREEN_UNAVAILABLE,
            EnqueueResult.Rejected(RejectionReason.Closed).toSampleIngressFailure(),
        )
    }

    @Test
    fun `basic reducer keeps operation and load failure typed`() {
        val loading = changed(
            SplitIntentBasicReducer.reduce(
                SplitIntentBasicState(),
                SplitIntentBasicMutation.LoadingStarted(BasicLoadingTarget.IMAGE),
            )
        )
        assertEquals(BasicOperation.LOADING_IMAGE_MODELS, loading.state.lastOperation)

        val failed = changed(
            SplitIntentBasicReducer.reduce(
                loading.state,
                SplitIntentBasicMutation.LoadFailed(
                    BasicLoadError.IMAGE_MODELS_UNAVAILABLE
                ),
            )
        )
        assertEquals(BasicOperation.LOAD_FAILED, failed.state.lastOperation)
        assertEquals(
            listOf(
                SplitIntentBasicEffect.ShowLoadError(
                    BasicLoadError.IMAGE_MODELS_UNAVAILABLE
                )
            ),
            failed.uiEffects,
        )
    }

    @Test
    fun `network reducer keeps update and load failure typed`() {
        val loading = changed(
            NetworkModelsReducer.reduce(
                NetworkModelsState(),
                NetworkModelsMutation.LoadingStarted(LoadingTarget.VIDEO),
            )
        )
        val loaded = changed(
            NetworkModelsReducer.reduce(
                loading.state,
                NetworkModelsMutation.VideoModelsLoaded(
                    listOf(VideoModel("video-id", "video-name", 12))
                ),
            )
        )
        val failed = changed(
            NetworkModelsReducer.reduce(
                loaded.state,
                NetworkModelsMutation.LoadFailed(
                    NetworkLoadError.VIDEO_MODELS_UNAVAILABLE
                ),
            )
        )

        assertEquals(NetworkModelsUpdate.VIDEO_MODELS, loaded.state.lastUpdated)
        assertEquals(false, failed.state.isLoading)
        assertEquals(
            listOf(
                NetworkModelsEffect.ShowLoadError(
                    NetworkLoadError.VIDEO_MODELS_UNAVAILABLE
                )
            ),
            failed.uiEffects,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : MviState, E : UiEffect>
        changed(outcome: ReduceOutcome<S, E>): ReduceOutcome.Changed<S, E> {
        assertTrue(outcome is ReduceOutcome.Changed<*, *>)
        return outcome as ReduceOutcome.Changed<S, E>
    }
}
