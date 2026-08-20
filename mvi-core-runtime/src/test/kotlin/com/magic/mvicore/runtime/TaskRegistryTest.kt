package com.magic.mvicore.runtime

import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.TaskHandle
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskReplacementReason
import com.magic.mvicore.contract.TaskToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRegistryTest {
    @Test
    fun `task key rejects blank identities`() {
        assertFailsWith<IllegalArgumentException> { TaskKey("") }
        assertFailsWith<IllegalArgumentException> { TaskKey("   ") }
        assertEquals("refresh", TaskKey("refresh").toString())
    }

    @Test
    fun `latest replaces active outcome after invalidating its token`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("refresh")
        val firstStarted = CompletableDeferred<TaskToken>()
        val firstCancelled = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<TaskToken>()

        val first = fixture.registry.launch(key, TaskPolicy.Latest) { token ->
            firstStarted.complete(token)
            try {
                awaitCancellation()
            } finally {
                firstCancelled.complete(Unit)
            }
        }.accepted()
        runCurrent()
        val firstToken = firstStarted.await()

        val second = fixture.registry.launch(key, TaskPolicy.Latest) { token ->
            secondStarted.complete(token)
            awaitCancellation()
        }.accepted()

        assertFalse(fixture.registry.isCurrent(firstToken))
        assertEquals(
            TaskOutcome.Replaced(TaskReplacementReason.LATEST),
            first.awaitOutcome(),
        )
        runCurrent()
        assertTrue(firstCancelled.isCompleted)
        assertTrue(fixture.registry.isCurrent(secondStarted.await()))

        fixture.close()
        assertEquals(TaskOutcome.Closed, second.awaitOutcome())
    }

    @Test
    fun `drop while running rejects overlap without creating an outcome handle`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("submit")
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<TaskToken>()
        var rejectedBlockRan = false

        val first = fixture.registry.launch(key, TaskPolicy.DropWhileRunning) { token ->
            started.complete(token)
            gate.await()
        }.accepted()
        runCurrent()

        assertEquals(
            TaskLaunchResult.DroppedWhileRunning,
            fixture.registry.launch(key, TaskPolicy.DropWhileRunning) {
                rejectedBlockRan = true
            },
        )
        assertFalse(rejectedBlockRan)
        assertTrue(fixture.registry.isCurrent(started.await()))

        gate.complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, first.awaitOutcome())

        val next = fixture.registry.launch(key, TaskPolicy.DropWhileRunning) { }.accepted()
        runCurrent()
        assertEquals(TaskOutcome.Completed, next.awaitOutcome())
        fixture.close()
    }

    @Test
    fun `queue runs every admitted request FIFO and completes each handle`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("upload")
        val starts = mutableListOf<Int>()
        val gates = (1..3).associateWith { CompletableDeferred<Unit>() }
        val handles = (1..3).associateWith { item ->
            fixture.registry.launch(key, TaskPolicy.Queue) {
                starts += item
                gates.getValue(item).await()
            }.accepted()
        }

        runCurrent()
        assertEquals(listOf(1), starts)

        gates.getValue(1).complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, handles.getValue(1).awaitOutcome())
        assertEquals(listOf(1, 2), starts)

        gates.getValue(2).complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, handles.getValue(2).awaitOutcome())
        assertEquals(listOf(1, 2, 3), starts)

        gates.getValue(3).complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, handles.getValue(3).awaitOutcome())
        fixture.close()
    }

    @Test
    fun `parallel starts and completes every admitted request independently`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("prefetch")
        val tokens = mutableMapOf<Int, TaskToken>()
        val gates = (1..3).associateWith { CompletableDeferred<Unit>() }
        val handles = (1..3).associateWith { item ->
            fixture.registry.launch(key, TaskPolicy.Parallel) { token ->
                tokens[item] = token
                gates.getValue(item).await()
            }.accepted()
        }
        runCurrent()

        assertEquals(setOf(1, 2, 3), tokens.keys)
        assertTrue(tokens.values.all(fixture.registry::isCurrent))

        gates.getValue(2).complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, handles.getValue(2).awaitOutcome())
        assertFalse(fixture.registry.isCurrent(tokens.getValue(2)))
        assertTrue(fixture.registry.isCurrent(tokens.getValue(1)))
        assertTrue(fixture.registry.isCurrent(tokens.getValue(3)))

        fixture.close()
        assertEquals(TaskOutcome.Closed, handles.getValue(1).awaitOutcome())
        assertEquals(TaskOutcome.Closed, handles.getValue(3).awaitOutcome())
    }

    @Test
    fun `conflate reports the pending request replaced by the third request`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("search")
        val firstGate = CompletableDeferred<Unit>()
        val thirdGate = CompletableDeferred<Unit>()
        val starts = mutableListOf<Int>()

        val first = fixture.registry.launch(key, TaskPolicy.Conflate) {
            starts += 1
            firstGate.await()
        }.accepted()
        runCurrent()
        val second = fixture.registry.launch(key, TaskPolicy.Conflate) {
            starts += 2
        }.accepted()
        val third = fixture.registry.launch(key, TaskPolicy.Conflate) {
            starts += 3
            thirdGate.await()
        }.accepted()

        assertEquals(
            TaskOutcome.Replaced(TaskReplacementReason.CONFLATED),
            second.awaitOutcome(),
        )
        assertEquals(listOf(1), starts)

        firstGate.complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, first.awaitOutcome())
        assertEquals(listOf(1, 3), starts)

        thirdGate.complete(Unit)
        runCurrent()
        assertEquals(TaskOutcome.Completed, third.awaitOutcome())
        fixture.close()
    }

    @Test
    fun `policy switch replaces active and pending generations`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("sync")
        val oldStarted = CompletableDeferred<Unit>()
        var oldPendingRan = false

        val oldActive = fixture.registry.launch(key, TaskPolicy.Queue) {
            oldStarted.complete(Unit)
            awaitCancellation()
        }.accepted()
        runCurrent()
        oldStarted.await()
        val oldPending = fixture.registry.launch(key, TaskPolicy.Queue) {
            oldPendingRan = true
        }.accepted()

        val replacement = fixture.registry.launch(key, TaskPolicy.Parallel) {
            awaitCancellation()
        }.accepted()
        val expected = TaskOutcome.Replaced(TaskReplacementReason.POLICY_CHANGED)
        assertEquals(expected, oldActive.awaitOutcome())
        assertEquals(expected, oldPending.awaitOutcome())
        runCurrent()
        assertFalse(oldPendingRan)

        fixture.close()
        assertEquals(TaskOutcome.Closed, replacement.awaitOutcome())
    }

    @Test
    fun `explicit cancel completes active and pending outcomes as cancelled`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("load")
        val activeStarted = CompletableDeferred<Unit>()

        val active = fixture.registry.launch(key, TaskPolicy.Queue) {
            activeStarted.complete(Unit)
            awaitCancellation()
        }.accepted()
        runCurrent()
        activeStarted.await()
        val pending = fixture.registry.launch(key, TaskPolicy.Queue) { }.accepted()

        assertTrue(fixture.registry.cancel(key))
        assertFalse(fixture.registry.cancel(key))
        assertEquals(TaskOutcome.Cancelled, active.awaitOutcome())
        assertEquals(TaskOutcome.Cancelled, pending.awaitOutcome())
        runCurrent()
        assertTrue(fixture.failures.isEmpty())
        fixture.close()
    }

    @Test
    fun `forged token with the same key and value never gains mutation authority`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("identity")
        val started = CompletableDeferred<TaskToken>()

        fixture.registry.launch(key, TaskPolicy.Latest) { token ->
            started.complete(token)
            awaitCancellation()
        }.accepted()
        runCurrent()
        val real = started.await()
        val forged = object : TaskToken {
            override val key: TaskKey = real.key
            override val value: Long = real.value
        }

        assertTrue(fixture.registry.isCurrent(real))
        assertFalse(fixture.registry.isCurrent(forged))
        assertFalse(fixture.registry.validate(forged))
        val late = assertIs<PulseFailure.LateMutation>(fixture.failures.single())
        assertEquals(key.value, late.taskKey)
        assertEquals(real.value, late.token)
        fixture.close()
    }

    @Test
    fun `task cancellation exception completes as cancelled without typed failure`() = runTest {
        val fixture = Fixture(this)
        val expected = CancellationException("task stopped itself")

        val handle = fixture.registry.launch(TaskKey("self-cancel"), TaskPolicy.Latest) {
            throw expected
        }.accepted()
        runCurrent()

        assertEquals(TaskOutcome.Cancelled, handle.awaitOutcome())
        assertTrue(fixture.failures.isEmpty())
        fixture.close()
    }

    @Test
    fun `ordinary task exception is both observable and reported as typed failure`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("refresh-feed")
        val expected = IllegalStateException("request failed")

        val handle = fixture.registry.launch(key, TaskPolicy.Latest) {
            throw expected
        }.accepted()
        runCurrent()

        val outcome = assertIs<TaskOutcome.Failed>(handle.awaitOutcome())
        assertSame(expected, outcome.cause)
        val failure = assertIs<PulseFailure.ExecutorFailure>(fixture.failures.single())
        assertEquals(key.value, failure.context.component)
        assertSame(expected, failure.cause)
        fixture.close()
    }

    @Test
    fun `fatal task throwable is rethrown by handle instead of becoming failed outcome`() = runTest {
        val fixture = Fixture(this)
        val fatal = LinkageError("broken runtime")

        val handle = fixture.registry.launch(TaskKey("fatal"), TaskPolicy.Latest) {
            throw fatal
        }.accepted()
        runCurrent()

        val observed = runCatching { handle.awaitOutcome() }.exceptionOrNull()
        val observedFatal = assertIs<LinkageError>(observed)
        assertEquals(fatal.message, observedFatal.message)
        assertTrue(fixture.failures.isEmpty())
        fixture.close()
    }

    @Test
    fun `terminal failure while reporting ordinary task exception is rethrown after invalidation`() =
        runTest {
            val terminals = listOf<Throwable>(
                LinkageError("fatal reporter"),
                CancellationException("cancelled reporter"),
            )

            terminals.forEachIndexed { index, terminal ->
                val config = PulseRuntimeConfig(storeId = "terminal-reporter-$index")
                val registry = TaskRegistry(
                    scope = this,
                    config = config,
                    failureReporter = { throw terminal },
                )
                val key = TaskKey("terminal-$index")
                val started = CompletableDeferred<TaskToken>()
                val handle = registry.launch(key, TaskPolicy.Latest) { token ->
                    started.complete(token)
                    throw IllegalStateException("ordinary task failure")
                }.accepted()

                runCurrent()
                val token = started.await()
                assertFalse(registry.isCurrent(token))
                val observed = runCatching { handle.awaitOutcome() }.exceptionOrNull()
                assertEquals(terminal::class, requireNotNull(observed)::class)
                assertEquals(terminal.message, observed.message)

                registry.close()
                registry.awaitClosed()
            }
        }

    @Test
    fun `close completes active and pending outcomes before awaiting cleanup`() = runTest {
        val fixture = Fixture(this)
        val key = TaskKey("shutdown")
        val activeStarted = CompletableDeferred<Unit>()
        val cleanupGate = CompletableDeferred<Unit>()
        var pendingRan = false

        val active = fixture.registry.launch(key, TaskPolicy.Queue) {
            activeStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanupGate.await() }
            }
        }.accepted()
        val pending = fixture.registry.launch(key, TaskPolicy.Queue) {
            pendingRan = true
        }.accepted()
        runCurrent()
        activeStarted.await()

        fixture.registry.close()
        fixture.registry.close()
        assertEquals(TaskOutcome.Closed, active.awaitOutcome())
        assertEquals(TaskOutcome.Closed, pending.awaitOutcome())
        assertEquals(
            TaskLaunchResult.Closed,
            fixture.registry.launch(key, TaskPolicy.Parallel) { },
        )
        val closing = async { fixture.registry.awaitClosed() }
        runCurrent()
        assertFalse(closing.isCompleted)

        cleanupGate.complete(Unit)
        closing.await()
        assertFalse(pendingRan)
        assertTrue(fixture.failures.isEmpty())
    }

    private fun TaskLaunchResult.accepted(): TaskHandle {
        return assertIs<TaskLaunchResult.Accepted>(this).handle
    }

    private class Fixture(scope: CoroutineScope) {
        val failures = mutableListOf<PulseFailure>()
        private val config = PulseRuntimeConfig(
            errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
            storeId = "task-registry-test",
        )
        val registry = TaskRegistry(scope, config)

        suspend fun close() {
            registry.close()
            registry.awaitClosed()
        }
    }
}
