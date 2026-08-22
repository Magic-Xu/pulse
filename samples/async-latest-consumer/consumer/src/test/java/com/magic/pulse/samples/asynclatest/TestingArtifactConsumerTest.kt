package com.magic.pulse.samples.asynclatest

import androidx.lifecycle.SavedStateHandle
import com.magic.mvicore.testing.runPulseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestingArtifactConsumerTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun testingArtifactIsConsumable() = runPulseTest {
        // Compiling and running this block proves the staged testing artifact is on the classpath.
    }

    @Test
    fun latestCancelsOldWorkRejectsLateResultAndCheckpointsCommittedState() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val firstStarted = CompletableDeferred<Unit>()
        var firstCancelled = false
        var calls = 0
        var operationId = 0L
        val viewModel = AsyncLatestViewModel(
            savedStateHandle = savedState,
            load = {
                calls += 1
                if (calls == 1) {
                    firstStarted.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                    } finally {
                        firstCancelled = true
                    }
                }
                "candidate-$calls"
            },
            nextOperationId = { ++operationId },
        )

        viewModel.send(AsyncUiIntent.Refresh)
        runCurrent()
        firstStarted.await()
        viewModel.send(AsyncUiIntent.Refresh)
        advanceUntilIdle()

        assertTrue(firstCancelled)
        assertFalse(viewModel.state.value.loading)
        assertEquals("candidate-2", viewModel.state.value.value)
        assertEquals(2L, viewModel.state.value.operationId)
        assertEquals("candidate-2", savedState.get<String>("value"))

        viewModel.close()
        advanceUntilIdle()
        viewModel.awaitClosed()
    }

    @Test
    fun ordinaryFailureBecomesForegroundEffectAndRestoredValueStartsNewProcessState() =
        runTest(dispatcher) {
            val savedState = SavedStateHandle(mapOf("value" to "restored"))
            val deliveredEffect = CompletableDeferred<AsyncEffect>()
            val viewModel = AsyncLatestViewModel(
                savedStateHandle = savedState,
                load = { throw IllegalStateException("offline") },
                nextOperationId = { 7L },
            )
            assertEquals("restored", viewModel.state.value.value)
            assertFalse(viewModel.state.value.loading)

            val coordinator = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                viewModel.uiEffects.collect { envelope ->
                    deliveredEffect.complete(envelope.payload)
                }
            }
            viewModel.send(AsyncUiIntent.Refresh)
            advanceUntilIdle()

            assertEquals(
                AsyncEffect.ShowFailure("offline"),
                withTimeout(5_000) { deliveredEffect.await() },
            )
            assertFalse(viewModel.state.value.loading)
            assertEquals("restored", viewModel.state.value.value)

            viewModel.close()
            advanceUntilIdle()
            viewModel.awaitClosed()
            coordinator.cancelAndJoin()
        }
}
