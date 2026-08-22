package com.magic.mvicore.testing

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.MailboxOverflowPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiSeedStressTest {
    @Test
    fun concurrentTenThousandInputStressAcrossSeeds() {
        seeds().forEach(::runSeed)
    }

    private fun runSeed(seed: Int) = runPulseTest {
        val count = 10_000
        val random = Random(seed)
        val work = List(count) { index ->
            StressWork(
                input = StressIntent(id = index + 1, amount = random.nextInt(1, 4)),
                yieldBeforeSend = random.nextBoolean(),
                useTrySend = random.nextInt(4) == 0,
            )
        }
        val store = testStore(
            initialState = StressState(value = 0, traceHash = 1),
            reducer = PulseReducer<StressState, StressIntent, StressEffect> { state, input ->
                ReduceOutcome.Changed(
                    state.copy(
                        value = state.value + input.amount,
                        traceHash = 31 * state.traceHash + input.id,
                    )
                )
            },
            config = runtimeConfig(
                mailboxCapacity = 128,
                overflowPolicy = MailboxOverflowPolicy.REJECT,
            ),
        )

        val results = work
            .chunked(100)
            .map { chunk ->
                testScope.async {
                    chunk.map { item ->
                        if (item.yieldBeforeSend) yield()
                        if (item.useTrySend) {
                            while (true) {
                                when (val result = store.trySend(item.input)) {
                                    is EnqueueResult.Enqueued -> break
                                    EnqueueResult.Full -> yield()
                                    is EnqueueResult.Rejected -> error(
                                        "seed=$seed unexpected rejection=${result.reason}"
                                    )
                                }
                            }
                            null
                        } else {
                            store.send(item.input)
                        }
                    }
                }
            }
            .flatMap { it.await() }
        val frames = store.transitionProbe.awaitCount(count)

        assertTrue(
            results.filterNotNull().all { it is TransitionResult.Completed },
            "seed=$seed contained a rejected frame",
        )
        assertEquals(
            (1L..count.toLong()).toList(),
            frames.map { it.sequenceId },
            "seed=$seed frame trace=${frames.takeLast(8).map { it.sequenceId }}",
        )
        val expected = frames.fold(StressState(value = 0, traceHash = 1)) { state, frame ->
            state.copy(
                value = state.value + frame.input.amount,
                traceHash = 31 * state.traceHash + frame.input.id,
            )
        }
        assertEquals(expected, store.state.value, failureTrace(seed, frames, expected, store.state.value))
        store.failureProbe.assertEmpty()
    }

    private fun seeds(): List<Int> {
        return (System.getProperty("pulse.test.seeds") ?: "20260819")
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::toInt)
            .also { require(it.isNotEmpty()) { "pulse.test.seeds must contain at least one seed." } }
    }

    private fun failureTrace(
        seed: Int,
        frames: List<com.magic.mvicore.contract.TransitionFrame<StressState, StressIntent, StressEffect>>,
        expected: StressState,
        actual: StressState,
    ): String {
        return "seed=$seed trace=${frames.takeLast(8).map { it.sequenceId to it.input.id }} " +
            "redactedDiff=expected(value=${expected.value},hash=${expected.traceHash}) " +
            "actual(value=${actual.value},hash=${actual.traceHash})"
    }

    private data class StressState(
        val value: Int,
        val traceHash: Int,
    ) : MviState

    private data class StressIntent(
        val id: Int,
        val amount: Int,
    ) : MviIntent

    private data class StressWork(
        val input: StressIntent,
        val yieldBeforeSend: Boolean,
        val useTrySend: Boolean,
    )

    private sealed interface StressEffect : UiEffect
}
