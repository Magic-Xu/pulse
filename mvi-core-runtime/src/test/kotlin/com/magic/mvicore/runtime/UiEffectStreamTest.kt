package com.magic.mvicore.runtime

import com.magic.mvicore.contract.EffectEnvelope
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UiEffectStreamTest {
    @Test
    fun `emission without coordinator is reported and never replayed`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()

        stream.emit(envelope(1))

        val received = mutableListOf<EffectEnvelope<TestEffect>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect(received::add)
        }
        runCurrent()
        collector.cancelAndJoin()

        assertTrue(received.isEmpty())
        val failure = assertIs<PulseFailure.UndeliveredUiEffect>(reporter.failures.single())
        assertEquals(1L, failure.envelope.sequenceId)
        assertEquals("no-active-coordinator", failure.reason)
    }

    @Test
    fun `only one coordinator can collect at a time`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect { }
        }

        val error = assertFailsWith<IllegalStateException> {
            stream.collect { }
        }

        assertTrue(error.message.orEmpty().contains("active coordinator"))
        val failure = assertIs<PulseFailure.UiEffectConsumerFailure>(reporter.failures.single())
        assertSame(error, failure.cause)
        first.cancelAndJoin()
    }

    @Test
    fun `consumer delivery uses configured consumer dispatcher`() = runTest {
        val marker = ThreadLocal<Boolean>()
        val dispatcher = MarkingDispatcher(
            delegate = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            marker = marker,
        )
        val reporter = RecordingReporter(consumerDispatcher = dispatcher)
        val stream = reporter.stream()
        val delivered = CompletableDeferred<Boolean>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect { delivered.complete(marker.get() == true) }
        }

        stream.emit(envelope(1))

        assertTrue(delivered.await())
        collector.cancelAndJoin()
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `bounded buffer suspends producers when full`() = runTest {
        val reporter = RecordingReporter(effectBufferCapacity = 1)
        val stream = reporter.stream()
        val firstDeliveryStarted = CompletableDeferred<Unit>()
        val releaseFirstDelivery = CompletableDeferred<Unit>()
        val thirdDelivered = CompletableDeferred<Unit>()
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect { item ->
                received += item.sequenceId
                if (item.sequenceId == 1L) {
                    firstDeliveryStarted.complete(Unit)
                    releaseFirstDelivery.await()
                }
                if (item.sequenceId == 3L) thirdDelivered.complete(Unit)
            }
        }

        stream.emit(envelope(1))
        firstDeliveryStarted.await()
        stream.emit(envelope(2))
        val thirdProducer = launch { stream.emit(envelope(3)) }
        runCurrent()

        assertFalse(thirdProducer.isCompleted)
        releaseFirstDelivery.complete(Unit)
        thirdProducer.join()
        thirdDelivered.await()
        collector.cancelAndJoin()

        assertEquals(listOf(1L, 2L, 3L), received)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `cancelling coordinator reports every buffered effect as undelivered`() = runTest {
        val reporter = RecordingReporter(effectBufferCapacity = 2)
        val stream = reporter.stream()
        val firstDeliveryStarted = CompletableDeferred<Unit>()
        val keepFirstDeliverySuspended = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect { item ->
                if (item.sequenceId == 1L) {
                    firstDeliveryStarted.complete(Unit)
                    keepFirstDeliverySuspended.await()
                }
            }
        }

        stream.emit(envelope(1))
        firstDeliveryStarted.await()
        stream.emit(envelope(2))
        stream.emit(envelope(3))
        collector.cancelAndJoin()

        val undelivered = reporter.failures.map {
            assertIs<PulseFailure.UndeliveredUiEffect>(it)
        }
        assertEquals(setOf(2L, 3L), undelivered.map { it.envelope.sequenceId }.toSet())
        assertTrue(undelivered.all { it.reason == "coordinator-session-ended" })
    }

    @Test
    fun `coordinator cancellation reports pending effects on consumer dispatcher`() = runTest {
        val consumerLane = ThreadLocal<Boolean>()
        val consumerDispatcher = MarkingDispatcher(
            delegate = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            marker = consumerLane,
        )
        val failureLanes = mutableListOf<Boolean>()
        val reporter = RecordingReporter(
            effectBufferCapacity = 2,
            consumerDispatcher = consumerDispatcher,
            onFailure = { failureLanes += consumerLane.get() == true },
        )
        val stream = reporter.stream()
        val firstDeliveryStarted = CompletableDeferred<Unit>()
        val keepFirstDeliverySuspended = CompletableDeferred<Unit>()
        val collector = launch(
            context = Dispatchers.IO,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            stream.collect { item ->
                if (item.sequenceId == 1L) {
                    firstDeliveryStarted.complete(Unit)
                    keepFirstDeliverySuspended.await()
                }
            }
        }

        stream.emit(envelope(1))
        firstDeliveryStarted.await()
        stream.emit(envelope(2))
        collector.cancelAndJoin()

        val failure = assertIs<PulseFailure.UndeliveredUiEffect>(reporter.failures.single())
        assertEquals(2L, failure.envelope.sequenceId)
        assertEquals(listOf(true), failureLanes)
    }

    @Test
    fun `stream close reports pending effects on consumer dispatcher`() = runTest {
        val consumerLane = ThreadLocal<Boolean>()
        val consumerDispatcher = MarkingDispatcher(
            delegate = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            marker = consumerLane,
        )
        val failureLanes = mutableListOf<Boolean>()
        val reporter = RecordingReporter(
            effectBufferCapacity = 2,
            consumerDispatcher = consumerDispatcher,
            onFailure = { failureLanes += consumerLane.get() == true },
        )
        val stream = reporter.stream()
        val firstDeliveryStarted = CompletableDeferred<Unit>()
        val keepFirstDeliverySuspended = CompletableDeferred<Unit>()
        val collector = launch(
            context = Dispatchers.IO,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            stream.collect { item ->
                if (item.sequenceId == 1L) {
                    firstDeliveryStarted.complete(Unit)
                    keepFirstDeliverySuspended.await()
                }
            }
        }

        stream.emit(envelope(1))
        firstDeliveryStarted.await()
        stream.emit(envelope(2))
        stream.close()
        collector.cancelAndJoin()

        val failure = assertIs<PulseFailure.UndeliveredUiEffect>(reporter.failures.single())
        assertEquals(2L, failure.envelope.sequenceId)
        assertEquals(listOf(true), failureLanes)
    }

    @Test
    fun `ordinary consumer failure is reported and later effects continue`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()
        val expected = IllegalStateException("consumer failed")
        val secondDelivered = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect { item ->
                if (item.sequenceId == 1L) throw expected
                if (item.sequenceId == 2L) secondDelivered.complete(Unit)
            }
        }

        stream.emit(envelope(1))
        stream.emit(envelope(2))
        secondDelivered.await()
        collector.cancelAndJoin()

        val failure = assertIs<PulseFailure.UiEffectConsumerFailure>(reporter.failures.single())
        assertSame(expected, failure.cause)
        assertEquals(1L, failure.context.requestId)
        assertEquals(1L, failure.context.sequenceId)
    }

    @Test
    fun `consumer cancellation propagates without becoming a failure`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()
        val expected = CancellationException("consumer cancelled")
        val producer = launch { stream.emit(envelope(1)) }

        val thrown = assertFailsWith<CancellationException> {
            stream.collect { throw expected }
        }
        producer.join()

        assertEquals(expected.message, thrown.message)
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `fatal consumer error propagates without becoming a failure`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()
        val expected = TestFatalError()

        supervisorScope {
            val collector = async(start = CoroutineStart.UNDISPATCHED) {
                stream.collect { throw expected }
            }

            stream.emit(envelope(1))

            assertSame(expected, assertFailsWith<TestFatalError> { collector.await() })
        }
        assertTrue(reporter.failures.isEmpty())
    }

    @Test
    fun `closed stream reports later emissions and rejects collection`() = runTest {
        val reporter = RecordingReporter()
        val stream = reporter.stream()

        stream.close()
        stream.emit(envelope(1))

        val failure = assertIs<PulseFailure.UndeliveredUiEffect>(reporter.failures.single())
        assertEquals("stream-closed", failure.reason)
        assertFailsWith<IllegalStateException> {
            stream.collect { }
        }
    }

    private class RecordingReporter(
        effectBufferCapacity: Int = 4,
        consumerDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
        onFailure: (PulseFailure) -> Unit = {},
    ) {
        val failures = mutableListOf<PulseFailure>()

        private val config = PulseRuntimeConfig(
            effectBufferCapacity = effectBufferCapacity,
            consumerDispatcher = consumerDispatcher,
            errorHandler = PulseErrorHandler { _, failure, _ ->
                failures += failure
                onFailure(failure)
            },
            storeId = "effect-stream-test",
        )

        fun stream(): SingleCoordinatorUiEffectStream<TestEffect> {
            return SingleCoordinatorUiEffectStream(config)
        }
    }

    private class MarkingDispatcher(
        private val delegate: CoroutineDispatcher,
        private val marker: ThreadLocal<Boolean>,
    ) : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext): Boolean = true

        override fun dispatch(
            context: kotlin.coroutines.CoroutineContext,
            block: Runnable,
        ) {
            delegate.dispatch(context) {
                marker.set(true)
                try {
                    block.run()
                } finally {
                    marker.remove()
                }
            }
        }
    }

    private fun envelope(sequenceId: Int): EffectEnvelope<TestEffect> {
        return EffectEnvelope(
            effectId = sequenceId.toLong(),
            requestId = sequenceId.toLong(),
            sequenceId = sequenceId.toLong(),
            stateRevision = sequenceId.toLong(),
            index = 0,
            payload = TestEffect.Notice(sequenceId),
        )
    }

    private sealed interface TestEffect : UiEffect {
        data class Notice(val value: Int) : TestEffect
    }

    private class TestFatalError : LinkageError("fatal")
}
