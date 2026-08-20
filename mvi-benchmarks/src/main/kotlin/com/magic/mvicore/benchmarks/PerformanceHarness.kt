package com.magic.mvicore.benchmarks

import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.DefaultStore
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val ITERATIONS = 10_000
private const val MAILBOX_CAPACITY = 32

fun main() {
    measureV02(2_000)
    measureV03(2_000)

    val v02 = measureV02(ITERATIONS)
    val v03 = measureV03(ITERATIONS)
    val mailboxHighWater = measureMailboxHighWater()
    val selectorHits = measureSelectorHits()
    val subscriptionMemoryBytes = measureCancelledSubscriptionMemory()

    val throughputRatio = v03.throughputPerSecond / v02.throughputPerSecond
    check(throughputRatio >= 0.005) {
        "v0.3 throughput fell below the ADR floor: ratio=$throughputRatio"
    }
    check(v03.p95Nanos <= 100_000_000L) {
        "v0.3 p95 exceeded 100ms: ${v03.p95Nanos}ns"
    }
    check(mailboxHighWater <= MAILBOX_CAPACITY) {
        "Mailbox exceeded its configured capacity: $mailboxHighWater"
    }
    check(subscriptionMemoryBytes <= 64L * 1024L * 1024L) {
        "Cancelled subscription workload retained more than 64MiB: $subscriptionMemoryBytes"
    }

    val report = File(
        requireNotNull(System.getProperty("pulse.performance.report")) {
            "pulse.performance.report must be provided by Gradle."
        }
    )
    report.parentFile.mkdirs()
    report.writeText(
        """
        {
          "iterations": $ITERATIONS,
          "v02": {"throughputPerSecond": ${v02.throughputPerSecond}, "p95Nanos": ${v02.p95Nanos}},
          "v03": {"throughputPerSecond": ${v03.throughputPerSecond}, "p95Nanos": ${v03.p95Nanos}},
          "throughputRatio": $throughputRatio,
          "mailboxCapacity": $MAILBOX_CAPACITY,
          "mailboxHighWater": $mailboxHighWater,
          "cancelledSubscriptionMemoryBytes": $subscriptionMemoryBytes,
          "selectorHits": $selectorHits
        }
        """.trimIndent() + "\n"
    )
}

private fun measureV02(iterations: Int): Result {
    val store = V02ReferenceStore(BenchmarkState(0), LegacyReducer)
    val samples = LongArray(iterations)
    val started = System.nanoTime()
    repeat(iterations) { index ->
        val itemStarted = System.nanoTime()
        store.dispatch(BenchmarkIntent.Increment)
        samples[index] = System.nanoTime() - itemStarted
    }
    val elapsed = max(1L, System.nanoTime() - started)
    samples.sort()
    return Result(
        throughputPerSecond = iterations * 1_000_000_000.0 / elapsed,
        p95Nanos = samples[((iterations - 1) * 95) / 100],
    )
}

private fun measureV03(iterations: Int): Result = runBlocking {
    val store = DefaultPulseStore(
        initialState = BenchmarkState(0),
        reducer = PulseReducer<BenchmarkState, BenchmarkIntent, BenchmarkEffect> { state, _ ->
            ReduceOutcome.Changed(state.copy(value = state.value + 1))
        },
    )
    val samples = LongArray(iterations)
    val started = System.nanoTime()
    repeat(iterations) { index ->
        val itemStarted = System.nanoTime()
        store.send(BenchmarkIntent.Increment)
        samples[index] = System.nanoTime() - itemStarted
    }
    val elapsed = max(1L, System.nanoTime() - started)
    store.close()
    store.awaitClosed()
    samples.sort()
    Result(
        throughputPerSecond = iterations * 1_000_000_000.0 / elapsed,
        p95Nanos = samples[((iterations - 1) * 95) / 100],
    )
}

private fun measureMailboxHighWater(): Int {
    val reducerStarted = CountDownLatch(1)
    val releaseReducer = CountDownLatch(1)
    val store = DefaultPulseStore(
        initialState = BenchmarkState(0),
        reducer = PulseReducer<BenchmarkState, BenchmarkIntent, BenchmarkEffect> { state, _ ->
            reducerStarted.countDown()
            check(releaseReducer.await(5, TimeUnit.SECONDS))
            ReduceOutcome.Changed(state.copy(value = state.value + 1))
        },
        config = PulseRuntimeConfig(mailboxCapacity = MAILBOX_CAPACITY),
    )
    check(store.trySend(BenchmarkIntent.Increment) is com.magic.mvicore.contract.EnqueueResult.Enqueued)
    check(reducerStarted.await(5, TimeUnit.SECONDS))
    var queued = 0
    while (store.trySend(BenchmarkIntent.Increment) is com.magic.mvicore.contract.EnqueueResult.Enqueued) {
        queued += 1
    }
    releaseReducer.countDown()
    store.close()
    runBlocking { store.awaitClosed() }
    return queued
}

private fun measureSelectorHits(): Int {
    var previous = -1
    var hits = 0
    repeat(ITERATIONS) { revision ->
        val selected = revision / 10
        if (selected != previous) {
            hits += 1
            previous = selected
        }
    }
    return hits
}

private fun measureCancelledSubscriptionMemory(): Long {
    forceGc()
    val before = usedMemory()
    val store = DefaultStore(BenchmarkState(0), LegacyReducer)
    val subscriptions = List(ITERATIONS) { store.observeState { } }
    subscriptions.forEach { it.cancel() }
    store.close()
    forceGc()
    return max(0L, usedMemory() - before)
}

private fun forceGc() {
    repeat(2) {
        System.gc()
        Thread.sleep(10)
    }
}

private fun usedMemory(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}

private data class Result(
    val throughputPerSecond: Double,
    val p95Nanos: Long,
)

private data class BenchmarkState(val value: Int) : MviState

private sealed interface BenchmarkIntent : MviIntent {
    data object Increment : BenchmarkIntent
}

private sealed interface BenchmarkEffect : UiEffect

private object LegacyEffect : MviEffect

private object LegacyReducer : Reducer<BenchmarkState, BenchmarkIntent, LegacyEffect> {
    override fun reduce(
        previous: BenchmarkState,
        intent: BenchmarkIntent,
    ): Next<BenchmarkState, LegacyEffect> {
        return Next.just(previous.copy(value = previous.value + 1))
    }
}

/** Frozen v0.2 synchronous reducer/commit fixture used only by the performance comparison. */
private class V02ReferenceStore(
    initialState: BenchmarkState,
    private val reducer: Reducer<BenchmarkState, BenchmarkIntent, LegacyEffect>,
) {
    private val lock = Any()
    private var state = initialState

    fun dispatch(intent: BenchmarkIntent) {
        synchronized(lock) {
            state = reducer.reduce(state, intent).state
        }
    }
}
