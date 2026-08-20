package com.magic.mvicore.testing

import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseStorePlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Safe facade over coroutine-test scheduling and Pulse store creation. */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseTestScope internal constructor(
    internal val testScope: TestScope,
    val dispatcher: TestDispatcher,
) {
    private val stores = mutableListOf<TestPulseStore<*, *, *>>()

    val currentTimeMillis: Long
        get() = testScope.testScheduler.currentTime

    fun runtimeConfig(
        mailboxCapacity: Int = 64,
        effectBufferCapacity: Int = 16,
        failureProbe: FailureProbe = FailureProbe(),
        storeId: String = "pulse-test-store",
    ): TestRuntimeConfig {
        return TestRuntimeConfig(
            dispatcher = dispatcher,
            mailboxCapacity = mailboxCapacity,
            effectBufferCapacity = effectBufferCapacity,
            failureProbe = failureProbe,
            storeId = storeId,
        )
    }

    fun <S : MviState, I : MviIntent, E : UiEffect> testStore(
        initialState: S,
        reducer: PulseReducer<S, I, E>,
        config: TestRuntimeConfig = runtimeConfig(),
        plugins: List<PulseStorePlugin<S, I, E>> = emptyList(),
        factory: PulseStoreTckFactory = DefaultPulseStoreTckFactory,
    ): TestPulseStore<S, I, E> {
        val store = TestPulseStore(
            delegate = factory.create(
                initialState = initialState,
                reducer = reducer,
                config = config,
                plugins = plugins,
            ),
            collectorScope = testScope.backgroundScope,
            failureProbe = config.failureProbe,
        )
        stores += store
        return store
    }

    suspend fun <S : MviState, I : MviIntent, E : UiEffect> sendConcurrently(
        store: TestPulseStore<S, I, E>,
        inputs: Iterable<I>,
    ): List<TransitionResult<S, I, E>> {
        return inputs.map { input -> testScope.async { store.send(input) } }.awaitAll()
    }

    fun runCurrent() = testScope.runCurrent()

    fun advanceTimeBy(delayMillis: Long) = testScope.advanceTimeBy(delayMillis)

    fun advanceUntilIdle() = testScope.advanceUntilIdle()

    internal suspend fun closeAll() {
        stores.forEach { it.close() }
        testScope.advanceUntilIdle()
        stores.asReversed().forEach { it.awaitClosed() }
        testScope.advanceUntilIdle()
    }
}

/** Runs a Pulse test with one virtual-time scheduler and automatic store cleanup. */
@OptIn(ExperimentalCoroutinesApi::class)
fun runPulseTest(block: suspend PulseTestScope.() -> Unit) {
    runTest {
        val pulseScope = PulseTestScope(
            testScope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        try {
            pulseScope.block()
        } finally {
            pulseScope.closeAll()
        }
    }
}
