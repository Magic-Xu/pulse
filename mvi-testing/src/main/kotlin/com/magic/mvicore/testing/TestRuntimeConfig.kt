package com.magic.mvicore.testing

import com.magic.mvicore.runtime.PulseClock
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.MailboxOverflowPolicy
import com.magic.mvicore.runtime.PulseRedactor
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.TypeOnlyPulseRedactor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import java.util.concurrent.atomic.AtomicLong

/** Monotonic Pulse clock derived from a coroutine test scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestPulseClock(
    private val dispatcher: TestDispatcher,
) : PulseClock {
    private val lastNanos = AtomicLong(-1L)

    override fun nanoTime(): Long {
        val schedulerNanos = dispatcher.scheduler.currentTime * NANOS_PER_MILLISECOND
        while (true) {
            val previous = lastNanos.get()
            val next = maxOf(schedulerNanos, previous + 1L)
            if (lastNanos.compareAndSet(previous, next)) return next
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

/**
 * Test-owned runtime dependencies shared by a [TestPulseStore] and its probes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestRuntimeConfig(
    val dispatcher: TestDispatcher,
    val mailboxCapacity: Int = 64,
    val overflowPolicy: MailboxOverflowPolicy = MailboxOverflowPolicy.REJECT_AND_REPORT,
    val effectBufferCapacity: Int = 16,
    val failureProbe: FailureProbe = FailureProbe(),
    val clock: PulseClock = TestPulseClock(dispatcher),
    val redactor: PulseRedactor = TypeOnlyPulseRedactor,
    val storeId: String = nextStoreId(),
) {
    init {
        require(mailboxCapacity > 0) { "mailboxCapacity must be greater than zero." }
        require(effectBufferCapacity > 0) { "effectBufferCapacity must be greater than zero." }
        require(storeId.isNotBlank()) { "storeId must not be blank." }
    }

    /** Builds the production runtime configuration with deterministic test dependencies. */
    fun toPulseRuntimeConfig(): PulseRuntimeConfig {
        return PulseRuntimeConfig(
            mailboxCapacity = mailboxCapacity,
            overflowPolicy = overflowPolicy,
            effectBufferCapacity = effectBufferCapacity,
            storeDispatcher = dispatcher,
            consumerDispatcher = dispatcher,
            clock = clock,
            errorHandler = PulseErrorHandler { _, failure, _ -> failureProbe.record(failure) },
            strictMode = true,
            redactor = redactor,
            storeId = storeId,
        )
    }

    private companion object {
        val nextStoreNumber = AtomicLong(0L)

        fun nextStoreId(): String = "pulse-test-${nextStoreNumber.incrementAndGet()}"
    }
}
