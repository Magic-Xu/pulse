package com.magic.mvicore.runtime

import com.magic.mvicore.contract.DispatchResult
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer
import com.magic.mvicore.contract.StoreError
import com.magic.mvicore.contract.Subscription
import kotlinx.coroutines.CancellationException
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LegacyStoreAdapterTest {
    @Test
    fun `v0_2 constructor and synchronous lifecycle API remain compatible`() {
        val store = store(autoStart = false)
        val states = mutableListOf<State>()
        val effects = mutableListOf<Effect>()

        assertEquals(State(0), store.currentState)
        assertFalse(store.isStarted)
        assertFalse(store.isClosed)
        assertEquals(
            DispatchResult.Rejected(StoreError.StoreNotStarted),
            store.dispatch(Intent.Add(1)),
        )

        store.observeState(states::add)
        store.observeEffect(effects::add)
        assertEquals(listOf(State(0)), states)

        store.start()
        assertTrue(store.isStarted)
        assertEquals(
            DispatchResult.Accepted,
            store.dispatch(Intent.Add(2, listOf("saved"))),
        )
        assertEquals(State(2), store.currentState)
        assertEquals(listOf(State(0), State(2)), states)
        assertEquals(listOf<Effect>(Effect.Notice("saved")), effects)

        val reducerFailure = assertIs<DispatchResult.Rejected>(store.dispatch(Intent.Fail))
        val reducerError = assertIs<StoreError.ReducerFailure>(reducerFailure.error)
        assertEquals("legacy reducer failed", reducerError.cause.message)
        assertEquals(State(2), store.currentState)

        store.stop()
        assertFalse(store.isStarted)
        assertEquals(
            DispatchResult.Rejected(StoreError.StoreNotStarted),
            store.dispatch(Intent.Add(1)),
        )
        store.close()
        assertTrue(store.isClosed)
        assertEquals(
            DispatchResult.Rejected(StoreError.StoreClosed),
            store.dispatch(Intent.Add(1)),
        )

        val defaultStarted = store()
        assertTrue(defaultStarted.isStarted)
        defaultStarted.close()
    }

    @Test
    fun `callback reentrant dispatch runs after every observer of the current frame`() {
        val store = store()
        val events = mutableListOf<String>()
        val completed = CountDownLatch(1)

        store.observeState { state ->
            if (state.value == 1) {
                events += "first:1"
                assertEquals(DispatchResult.Accepted, store.dispatch(Intent.Add(10)))
                events += "reentrant-return:${store.currentState.value}"
            } else if (state.value == 11) {
                events += "first:11"
            }
        }
        store.observeState { state ->
            if (state.value > 0) events += "second:${state.value}"
            if (state.value == 11) completed.countDown()
        }

        assertEquals(DispatchResult.Accepted, store.dispatch(Intent.Add(1)))
        assertTrue(completed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        assertEquals(
            listOf(
                "first:1",
                "reentrant-return:11",
                "second:1",
                "first:11",
                "second:11",
            ),
            events,
        )
        assertEquals(State(11), store.currentState)
        store.close()
    }

    @Test
    fun `concurrent dispatch observers follow committed state order`() {
        val store = store()
        val observed = Collections.synchronizedList(mutableListOf<Int>())
        store.observeState { observed += it.value }
        observed.clear()

        withExecutor(8) { executor ->
            val start = CountDownLatch(1)
            val results = List(CONCURRENT_DISPATCH_COUNT) {
                executor.submit<DispatchResult> {
                    check(start.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    store.dispatch(Intent.Add(1))
                }
            }
            start.countDown()

            assertTrue(results.all { it.await() == DispatchResult.Accepted })
        }

        assertEquals(
            (1..CONCURRENT_DISPATCH_COUNT).toList(),
            synchronized(observed) { observed.toList() },
        )
        assertEquals(State(CONCURRENT_DISPATCH_COUNT), store.currentState)
        store.close()
    }

    @Test
    fun `observe snapshot never regresses while dispatch races registration`() {
        val store = store()
        val observations = mutableListOf<MutableList<Int>>()
        val subscriptions = mutableListOf<Subscription>()

        withExecutor(2) { executor ->
            val start = CountDownLatch(1)
            val dispatches = executor.submit {
                check(start.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                repeat(SNAPSHOT_DISPATCH_COUNT) {
                    check(store.dispatch(Intent.Add(1)) == DispatchResult.Accepted)
                }
            }
            val registrations = executor.submit {
                check(start.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                repeat(SNAPSHOT_OBSERVER_COUNT) {
                    val values = Collections.synchronizedList(mutableListOf<Int>())
                    observations += values
                    subscriptions += store.observeState { values += it.value }
                }
            }
            start.countDown()
            dispatches.await()
            registrations.await()
        }

        subscriptions.forEach(Subscription::cancel)
        observations.forEach { observation ->
            val values = synchronized(observation) { observation.toList() }
            assertTrue(values.isNotEmpty())
            assertTrue(
                values.zipWithNext().all { (before, after) -> before <= after },
                "State snapshot regressed: $values",
            )
        }
        assertEquals(State(SNAPSHOT_DISPATCH_COUNT), store.currentState)
        store.close()
    }

    @Test
    fun `ordinary callback failures are typed and do not stop later delivery`() {
        val diagnostics = captureStandardError {
            val store = store()
            val states = mutableListOf<State>()
            val effects = mutableListOf<Effect>()
            store.observeState { state ->
                if (state.value > 0) error("broken state callback")
            }
            store.observeState(states::add)
            store.observeEffect { error("broken effect callback") }
            store.observeEffect(effects::add)
            states.clear()

            assertEquals(
                DispatchResult.Accepted,
                store.dispatch(Intent.Add(1, listOf("first", "second"))),
            )
            assertEquals(
                DispatchResult.Accepted,
                store.dispatch(Intent.Add(1, listOf("third"))),
            )

            assertEquals(listOf(State(1), State(2)), states)
            assertEquals(
                listOf<Effect>(
                    Effect.Notice("first"),
                    Effect.Notice("second"),
                    Effect.Notice("third"),
                ),
                effects,
            )
            store.close()
        }

        assertTrue(diagnostics.contains("phase=STATE_CONSUMER"))
        assertTrue(diagnostics.contains("phase=UI_EFFECT_CONSUMER"))
    }

    @Test
    fun `cancel return prevents later callbacks in the active broadcast`() {
        val store = store()
        val lateStates = mutableListOf<State>()
        val lateEffects = mutableListOf<Effect>()
        lateinit var lateStateSubscription: Subscription
        lateinit var lateEffectSubscription: Subscription

        store.observeState { state ->
            if (state.value == 1) lateStateSubscription.cancel()
        }
        lateStateSubscription = store.observeState(lateStates::add)
        store.observeEffect { lateEffectSubscription.cancel() }
        lateEffectSubscription = store.observeEffect(lateEffects::add)
        lateStates.clear()

        assertEquals(
            DispatchResult.Accepted,
            store.dispatch(Intent.Add(1, listOf("cancelled"))),
        )

        assertTrue(lateStates.isEmpty())
        assertTrue(lateEffects.isEmpty())
        store.close()
    }

    @Test
    fun `external close returns after callback cutoff and is idempotent`() {
        val store = store()
        val states = mutableListOf<State>()
        val effects = mutableListOf<Effect>()
        store.observeState(states::add)
        store.observeEffect(effects::add)
        store.dispatch(Intent.Add(1, listOf("before-close")))

        store.close()
        store.close()
        val stateCountAtClose = states.size
        val effectCountAtClose = effects.size

        store.observeState(states::add).cancel()
        store.observeEffect(effects::add).cancel()
        assertEquals(
            DispatchResult.Rejected(StoreError.StoreClosed),
            store.dispatch(Intent.Add(1, listOf("after-close"))),
        )
        store.start()
        store.stop()

        assertTrue(store.isClosed)
        assertEquals(stateCountAtClose, states.size)
        assertEquals(effectCountAtClose, effects.size)
    }

    @Test
    fun `reentrant close cuts off later observers effects and plugin delivery before return`() {
        val events = mutableListOf<String>()
        val store = store(plugins = listOf(RecordingPlugin(events)))
        events.clear()

        store.observeState { state ->
            if (state.value == 1) {
                events += "closing"
                store.close()
                events += "close-returned"
            }
        }
        store.observeState { state ->
            if (state.value == 1) events += "late-state"
        }
        store.observeEffect { events += "late-effect" }

        assertEquals(
            DispatchResult.Accepted,
            store.dispatch(Intent.Add(1, listOf("notice"))),
        )

        assertEquals(
            listOf("intent:0", "closing", "close:1", "close-returned"),
            events,
        )
        assertTrue(store.isClosed)
        store.close()
    }

    @Test
    fun `ordinary legacy plugin failures are typed and do not stop later plugins`() {
        val events = mutableListOf<String>()
        val diagnostics = captureStandardError {
            val store = store(plugins = listOf(ThrowingPlugin(), RecordingPlugin(events)))
            store.dispatch(Intent.Add(1, listOf("notice")))
            store.stop()
            store.start()
            store.close()
        }

        assertEquals(
            listOf(
                "start:0",
                "intent:0",
                "state:1",
                "effect:notice",
                "stop:1",
                "start:1",
                "close:1",
            ),
            events,
        )
        assertTrue(diagnostics.contains("phase=PLUGIN"))
    }

    @Test
    fun `callback cancellation propagates and establishes legacy store cutoff`() {
        val expected = CancellationException("cancel callback")
        val store = store()
        store.observeEffect { throw expected }

        val thrown = assertFailsWith<CancellationException> {
            store.dispatch(Intent.Add(1, listOf("terminal")))
        }

        assertEquals(expected.message, thrown.message)
        assertTrue(store.isClosed)
        assertEquals(State(1), store.currentState)
        assertFailsWith<CancellationException> {
            store.dispatch(Intent.Add(10))
        }
        assertEquals(State(1), store.currentState)
    }

    @Test
    fun `fatal callback failure propagates and establishes legacy store cutoff`() {
        val expected = CallbackFatalError()
        val store = store()
        store.observeState { state ->
            if (state.value > 0) throw expected
        }

        val thrown = assertFailsWith<CallbackFatalError> {
            store.dispatch(Intent.Add(1))
        }

        assertEquals(expected.message, thrown.message)
        assertTrue(store.isClosed)
        assertEquals(State(1), store.currentState)
        assertFailsWith<CallbackFatalError> {
            store.dispatch(Intent.Add(10))
        }
        assertEquals(State(1), store.currentState)
    }

    private fun store(
        autoStart: Boolean = true,
        plugins: List<StorePlugin<State, Intent, Effect>> = emptyList(),
    ): DefaultStore<State, Intent, Effect> {
        return DefaultStore(
            initialState = State(0),
            reducer = Reducer { previous, intent ->
                when (intent) {
                    is Intent.Add -> Next.withEffects(
                        state = previous.copy(value = previous.value + intent.amount),
                        effects = intent.effects.map(Effect::Notice),
                    )

                    Intent.Fail -> error("legacy reducer failed")
                }
            },
            plugins = plugins,
            autoStart = autoStart,
        )
    }

    private inline fun withExecutor(
        threads: Int,
        block: (ExecutorService) -> Unit,
    ) {
        val executor = Executors.newFixedThreadPool(threads)
        try {
            block(executor)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun <T> Future<T>.await(): T = get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

    private inline fun captureStandardError(block: () -> Unit): String {
        val output = ByteArrayOutputStream()
        val previous = System.err
        return try {
            System.setErr(PrintStream(output, true, Charsets.UTF_8.name()))
            block()
            output.toString(Charsets.UTF_8.name())
        } finally {
            System.setErr(previous)
        }
    }

    private data class State(val value: Int) : MviState

    private sealed interface Intent : MviIntent {
        data class Add(
            val amount: Int,
            val effects: List<String> = emptyList(),
        ) : Intent

        data object Fail : Intent
    }

    private sealed interface Effect : MviEffect {
        data class Notice(val value: String) : Effect
    }

    private class ThrowingPlugin : StorePlugin<State, Intent, Effect> {
        override fun onStart(initialState: State) = fail()
        override fun onStop(lastState: State) = fail()
        override fun onClose(lastState: State) = fail()
        override fun onIntent(intent: Intent, stateBeforeReduce: State) = fail()
        override fun onState(state: State) = fail()
        override fun onEffect(effect: Effect) = fail()

        private fun fail(): Nothing = error("broken plugin")
    }

    private class CallbackFatalError : LinkageError("fatal callback")

    private class RecordingPlugin(
        private val events: MutableList<String>,
    ) : StorePlugin<State, Intent, Effect> {
        override fun onStart(initialState: State) {
            events += "start:${initialState.value}"
        }

        override fun onStop(lastState: State) {
            events += "stop:${lastState.value}"
        }

        override fun onClose(lastState: State) {
            events += "close:${lastState.value}"
        }

        override fun onIntent(intent: Intent, stateBeforeReduce: State) {
            events += "intent:${stateBeforeReduce.value}"
        }

        override fun onState(state: State) {
            events += "state:${state.value}"
        }

        override fun onEffect(effect: Effect) {
            events += "effect:${(effect as Effect.Notice).value}"
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val CONCURRENT_DISPATCH_COUNT = 64
        const val SNAPSHOT_DISPATCH_COUNT = 96
        const val SNAPSHOT_OBSERVER_COUNT = 32
    }
}
