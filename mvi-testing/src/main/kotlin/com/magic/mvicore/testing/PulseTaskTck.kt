package com.magic.mvicore.testing

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TaskHandle
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskReplacementReason
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reusable behavioral contract for the keyed task surface owned by a Pulse Store. */
class PulseTaskTck(
    private val factory: PulseStoreTckFactory = DefaultPulseStoreTckFactory,
) {
    fun latestReplacesActiveRequestBeforeStartingTheNextGeneration() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("latest")
        val firstToken = CompletableDeferred<TaskToken>()
        val secondToken = CompletableDeferred<TaskToken>()

        val first = store.tasks.launch(key, TaskPolicy.Latest) { token ->
            firstToken.complete(token)
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        val stale = firstToken.await()
        assertTrue(store.tasks.isCurrent(stale))

        val second = store.tasks.launch(key, TaskPolicy.Latest) { token ->
            secondToken.complete(token)
        }.acceptedHandle()

        assertFalse(store.tasks.isCurrent(stale))
        assertEquals(
            TaskOutcome.Replaced(TaskReplacementReason.LATEST),
            first.awaitOutcome(),
        )
        runCurrent()
        val current = secondToken.await()
        assertFalse(store.tasks.isCurrent(stale))
        assertFalse(store.tasks.isCurrent(current))
        assertEquals(TaskOutcome.Completed, second.awaitOutcome())
    }

    fun dropWhileRunningRejectsOverlapWithoutCreatingAHandle() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("drop")
        val started = CompletableDeferred<Unit>()
        val first = store.tasks.launch(key, TaskPolicy.DropWhileRunning) {
            started.complete(Unit)
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        started.await()

        assertEquals(
            TaskLaunchResult.DroppedWhileRunning,
            store.tasks.launch(key, TaskPolicy.DropWhileRunning) { },
        )
        assertTrue(store.tasks.cancel(key))
        assertEquals(TaskOutcome.Cancelled, first.awaitOutcome())
    }

    fun queueRunsEveryAdmittedRequestInFifoOrder() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("queue")
        val started = mutableListOf<Int>()
        val gates = List(3) { CompletableDeferred<Unit>() }
        val handles = (1..3).map { value ->
            store.tasks.launch(key, TaskPolicy.Queue(capacity = 4)) {
                started += value
                gates[value - 1].await()
            }.acceptedHandle()
        }

        runCurrent()
        assertEquals(listOf(1), started)
        gates[0].complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), started)
        gates[1].complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2, 3), started)
        gates[2].complete(Unit)
        runCurrent()

        assertEquals(
            listOf(TaskOutcome.Completed, TaskOutcome.Completed, TaskOutcome.Completed),
            handles.map { it.awaitOutcome() },
        )
    }

    fun queueRejectsBeyondItsPendingCapacity() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("bounded-queue")
        val release = CompletableDeferred<Unit>()
        val active = store.tasks.launch(key, TaskPolicy.Queue(capacity = 1)) {
            release.await()
        }.acceptedHandle()
        runCurrent()
        val pending = store.tasks.launch(key, TaskPolicy.Queue(capacity = 1)) { }
            .acceptedHandle()

        assertEquals(
            TaskLaunchResult.QueueFull(capacity = 1),
            store.tasks.launch(key, TaskPolicy.Queue(capacity = 1)) { },
        )
        release.complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, active.awaitOutcome())
        assertEquals(TaskOutcome.Completed, pending.awaitOutcome())
    }

    fun parallelStartsEveryAdmittedRequestWithAnIndependentToken() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("parallel")
        val release = CompletableDeferred<Unit>()
        val tokens = mutableListOf<TaskToken>()
        val handles = (1..3).map {
            store.tasks.launch(key, TaskPolicy.Parallel(maxConcurrency = 4)) { token ->
                tokens += token
                release.await()
            }.acceptedHandle()
        }

        runCurrent()
        assertEquals(3, tokens.size)
        assertEquals(3, tokens.map { it.value }.toSet().size)
        assertTrue(tokens.all(store.tasks::isCurrent))

        release.complete(Unit)
        runCurrent()
        assertTrue(tokens.none(store.tasks::isCurrent))
        assertEquals(
            listOf(TaskOutcome.Completed, TaskOutcome.Completed, TaskOutcome.Completed),
            handles.map { it.awaitOutcome() },
        )
    }

    fun parallelRejectsBeyondItsConcurrencyLimit() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("bounded-parallel")
        val release = CompletableDeferred<Unit>()
        val active = store.tasks.launch(key, TaskPolicy.Parallel(maxConcurrency = 1)) {
            release.await()
        }.acceptedHandle()
        runCurrent()

        assertEquals(
            TaskLaunchResult.ParallelLimitReached(maxConcurrency = 1),
            store.tasks.launch(key, TaskPolicy.Parallel(maxConcurrency = 1)) { },
        )
        release.complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, active.awaitOutcome())
    }

    fun conflateKeepsTheActiveAndOnlyTheNewestPendingRequest() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("conflate")
        val releaseFirst = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        val first = store.tasks.launch(key, TaskPolicy.Conflate) {
            started += 1
            releaseFirst.await()
        }.acceptedHandle()
        runCurrent()

        val replaced = store.tasks.launch(key, TaskPolicy.Conflate) {
            started += 2
        }.acceptedHandle()
        val newest = store.tasks.launch(key, TaskPolicy.Conflate) {
            started += 3
        }.acceptedHandle()

        assertEquals(
            TaskOutcome.Replaced(TaskReplacementReason.CONFLATED),
            replaced.awaitOutcome(),
        )
        assertEquals(listOf(1), started)
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(listOf(1, 3), started)
        assertEquals(TaskOutcome.Completed, first.awaitOutcome())
        assertEquals(TaskOutcome.Completed, newest.awaitOutcome())
    }

    fun cancellationAndCloseInvalidateTokensBeforeCompletingHandles() = runPulseTest {
        val store = taskStore()
        val cancelledKey = TaskKey("cancelled")
        val cancelledToken = CompletableDeferred<TaskToken>()
        val cancelled = store.tasks.launch(cancelledKey, TaskPolicy.Latest) { token ->
            cancelledToken.complete(token)
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        val tokenBeforeCancel = cancelledToken.await()

        assertTrue(store.tasks.cancel(cancelledKey))
        assertFalse(store.tasks.isCurrent(tokenBeforeCancel))
        assertEquals(TaskOutcome.Cancelled, cancelled.awaitOutcome())

        val closedToken = CompletableDeferred<TaskToken>()
        val closed = store.tasks.launch(TaskKey("closed"), TaskPolicy.Latest) { token ->
            closedToken.complete(token)
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        val tokenBeforeClose = closedToken.await()

        store.close()
        assertFalse(store.tasks.isCurrent(tokenBeforeClose))
        assertEquals(TaskOutcome.Closed, closed.awaitOutcome())
        assertEquals(
            TaskLaunchResult.Closed,
            store.tasks.launch(TaskKey("after-close"), TaskPolicy.Latest) { },
        )
    }

    fun cancelAllInvalidatesEveryKeyWithoutClosingTheRegistry() = runPulseTest {
        val store = taskStore()
        val handles = listOf("one", "two").map { value ->
            store.tasks.launch(TaskKey(value), TaskPolicy.Latest) {
                awaitCancellation()
            }.acceptedHandle()
        }
        runCurrent()

        assertEquals(2, store.tasks.cancelAll())
        assertEquals(listOf(TaskOutcome.Cancelled, TaskOutcome.Cancelled), handles.map {
            it.awaitOutcome()
        })
        assertEquals(0, store.tasks.cancelAll())

        val recovered = store.tasks.launch(TaskKey("three"), TaskPolicy.Latest) { }
            .acceptedHandle()
        runCurrent()
        assertEquals(TaskOutcome.Completed, recovered.awaitOutcome())
    }

    fun staleTokenValidationReportsOneLateMutationDiagnostic() = runPulseTest {
        val store = taskStore()
        val key = TaskKey("late")
        val captured = CompletableDeferred<TaskToken>()
        val handle = store.tasks.launch(key, TaskPolicy.Latest) { token ->
            captured.complete(token)
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        val token = captured.await()

        assertTrue(store.tasks.cancel(key))
        assertFalse(store.tasks.validate(token))
        assertEquals(TaskOutcome.Cancelled, handle.awaitOutcome())
        val failure = assertIs<PulseFailure.LateMutation>(store.failureProbe.snapshot().single())
        assertEquals(key.value, failure.taskKey)
        assertEquals(token.value, failure.token)
    }

    fun taskFailureIsTypedAndCancellationRemainsSilent() = runPulseTest {
        val store = taskStore()
        val failed = store.tasks.launch(TaskKey("failed"), TaskPolicy.Latest) {
            throw IllegalStateException("task failed")
        }.acceptedHandle()
        runCurrent()

        val failedOutcome = assertIs<TaskOutcome.Failed>(failed.awaitOutcome())
        assertEquals("task failed", failedOutcome.cause.message)
        assertIs<PulseFailure.ExecutorFailure>(store.failureProbe.snapshot().single())

        val cancellationKey = TaskKey("cancelled-without-failure")
        val cancelled = store.tasks.launch(cancellationKey, TaskPolicy.Latest) {
            awaitCancellation()
        }.acceptedHandle()
        runCurrent()
        assertTrue(store.tasks.cancel(cancellationKey))
        assertEquals(TaskOutcome.Cancelled, cancelled.awaitOutcome())
        assertEquals(1, store.failureProbe.snapshot().size)
        assertTrue(store.effectProbe.snapshot().isEmpty())

        val recovered = store.tasks.launch(TaskKey("recovered"), TaskPolicy.Latest) { }
            .acceptedHandle()
        runCurrent()
        assertEquals(TaskOutcome.Completed, recovered.awaitOutcome())
    }

    private fun PulseTestScope.taskStore(): TestPulseStore<TaskState, TaskIntent, TaskEffect> {
        return testStore(
            initialState = TaskState,
            reducer = TASK_REDUCER,
            factory = factory,
        )
    }

    private fun TaskLaunchResult.acceptedHandle(): TaskHandle {
        return assertIs<TaskLaunchResult.Accepted>(this).handle
    }

    private data object TaskState : MviState

    private data object TaskIntent : MviIntent

    private data object TaskEffect : UiEffect

    private companion object {
        val TASK_REDUCER = PulseReducer<TaskState, TaskIntent, TaskEffect> { _, _ ->
            ReduceOutcome.Unchanged()
        }
    }
}
