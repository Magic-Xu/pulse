package com.magic.mvicore.testing

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import kotlinx.coroutines.async
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
        val amounts = List(count) { random.nextInt(1, 4) }
        val expected = amounts.sum()
        val store = testStore(
            initialState = StressState(0),
            reducer = PulseReducer<StressState, StressIntent, StressEffect> { state, input ->
                ReduceOutcome.Changed(state.copy(value = state.value + input.amount))
            },
            config = runtimeConfig(mailboxCapacity = 128),
        )

        val results = amounts
            .chunked(100)
            .map { chunk ->
                testScope.async {
                    chunk.map { amount -> store.send(StressIntent(amount)) }
                }
            }
            .flatMap { it.await() }
        val frames = store.transitionProbe.awaitCount(count)

        assertTrue(
            results.all { it is TransitionResult.Completed },
            "seed=$seed contained a rejected frame",
        )
        assertEquals(
            (1L..count.toLong()).toList(),
            frames.map { it.sequenceId },
            "seed=$seed frame trace=${frames.takeLast(8).map { it.sequenceId }}",
        )
        assertEquals(
            StressState(expected),
            store.state.value,
            "seed=$seed redactedDiff=expected-sum:$expected actual-sum:${store.state.value.value}",
        )
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

    private data class StressState(val value: Int) : MviState

    private data class StressIntent(val amount: Int) : MviIntent

    private sealed interface StressEffect : UiEffect
}
