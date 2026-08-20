package com.magic.mvicore.android.compose

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.magic.mvicore.android.PulseStateHost
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TransitionResult
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.PulseStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PulseComposeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `created lifecycle cannot be used as the active collection threshold`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = StoreFixture(mainDispatcherRule.dispatcher)
            val owner = TestLifecycleOwner(
                Lifecycle.State.CREATED,
                UnconfinedTestDispatcher(testScheduler),
            )

            val failure = assertFailsWith<IllegalArgumentException> {
                fixture.host.collectUiEffectsWithLifecycle(owner, Lifecycle.State.CREATED) { }
            }

            assertTrue(failure.message.orEmpty().contains("STARTED or RESUMED"))
            fixture.close()
        }

    @Test
    fun `effects collect only while started and never replay after restart`() =
        runTest(mainDispatcherRule.dispatcher) {
        val dispatcher = mainDispatcherRule.dispatcher
        val fixture = StoreFixture(dispatcher)
        val owner = TestLifecycleOwner(
            Lifecycle.State.CREATED,
            UnconfinedTestDispatcher(testScheduler),
        )
        val received = mutableListOf<String>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.host.collectUiEffectsWithLifecycle(owner, Lifecycle.State.STARTED) {
                received += it.value
            }
        }
        runCurrent()

        fixture.send("before-start")
        runCurrent()
        assertTrue(received.isEmpty())

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        fixture.send("started")
        runCurrent()
        assertEquals(listOf("started"), received)

        owner.currentState = Lifecycle.State.CREATED
        runCurrent()
        fixture.send("while-stopped")
        runCurrent()
        assertEquals(listOf("started"), received)

        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        assertEquals(listOf("started"), received)
        fixture.send("after-restart")
        runCurrent()
        assertEquals(listOf("started", "after-restart"), received)

        owner.currentState = Lifecycle.State.DESTROYED
        collector.join()
        fixture.close()
        assertEquals(
            listOf("before-start", "while-stopped"),
            fixture.undeliveredEffects(),
        )
    }

    @Test
    fun `one effect stream rejects a second lifecycle coordinator`() =
        runTest(mainDispatcherRule.dispatcher) {
            val dispatcher = mainDispatcherRule.dispatcher
            val fixture = StoreFixture(dispatcher)
            val owner = TestLifecycleOwner(
                Lifecycle.State.STARTED,
                UnconfinedTestDispatcher(testScheduler),
            )
            val firstReceived = mutableListOf<String>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.host.collectUiEffectsWithLifecycle(owner, Lifecycle.State.STARTED) {
                    firstReceived += it.value
                }
            }
            runCurrent()
            fixture.send("owned-by-first")
            runCurrent()
            assertEquals(listOf("owned-by-first"), firstReceived)

            val failure = assertFailsWith<IllegalStateException> {
                supervisorScope {
                    fixture.host.collectUiEffectsWithLifecycle(owner, Lifecycle.State.STARTED) { }
                }
            }
            assertTrue(failure.message.orEmpty().contains("active coordinator"))
            val diagnostic = assertIs<PulseFailure.UiEffectConsumerFailure>(
                fixture.failures().single { it is PulseFailure.UiEffectConsumerFailure }
            )
            assertEquals("ui-effect-stream", diagnostic.context.component)
            first.cancelAndJoin()
            fixture.close()
        }

    @Test
    fun `selector exposes initial value and updates only when selection changes`() =
        runTest(mainDispatcherRule.dispatcher) {
        val dispatcher = mainDispatcherRule.dispatcher
        val fixture = StoreFixture(dispatcher)
        val owner = TestLifecycleOwner(
            Lifecycle.State.CREATED,
            UnconfinedTestDispatcher(testScheduler),
        )
        val selectedValues = mutableListOf<Int>()
        val composition = ComposeTestHarness(this, coroutineContext)
        try {
            composition.setContent {
                val selected by fixture.host.collectSelectedState(
                    lifecycleOwner = owner,
                    selector = TestState::selected,
                )
                val selectedInComposition = selected
                SideEffect {
                    if (selectedValues.lastOrNull() != selectedInComposition) {
                        selectedValues += selectedInComposition
                    }
                }
            }
            runCurrent()
            assertEquals(listOf(0), selectedValues)

            owner.currentState = Lifecycle.State.STARTED
            runCurrent()
            fixture.send(selected = 0, unrelated = 1)
            runCurrent()
            assertEquals(listOf(0), selectedValues)

            fixture.send(selected = 1, unrelated = 1)
            runCurrent()
            composition.advanceFrame()
            runCurrent()
            assertEquals(listOf(0, 1), selectedValues)

            fixture.send(selected = 1, unrelated = 2)
            runCurrent()
            assertEquals(listOf(0, 1), selectedValues)

            owner.currentState = Lifecycle.State.CREATED
            runCurrent()
            fixture.send(selected = 2, unrelated = 2)
            runCurrent()
            assertEquals(listOf(0, 1), selectedValues)

            owner.currentState = Lifecycle.State.STARTED
            runCurrent()
            composition.advanceFrame()
            runCurrent()
            assertEquals(listOf(0, 1, 2), selectedValues)
        } finally {
            composition.close()
            fixture.close()
        }
    }

    @Test
    fun `changing selector updates from current root without waiting for a new state`() =
        runTest(mainDispatcherRule.dispatcher) {
        val dispatcher = mainDispatcherRule.dispatcher
        val fixture = StoreFixture(dispatcher)
        val owner = TestLifecycleOwner(
            Lifecycle.State.STARTED,
            UnconfinedTestDispatcher(testScheduler),
        )
        val selectedValues = mutableListOf<Int>()
        val composition = ComposeTestHarness(this, coroutineContext)
        var selectUnrelated by mutableStateOf(false)
        try {
            composition.setContent {
                val selector: (TestState) -> Int = if (selectUnrelated) {
                    TestState::unrelated
                } else {
                    TestState::selected
                }
                val selected by fixture.host.collectSelectedState(
                    lifecycleOwner = owner,
                    selector = selector,
                )
                val selectedInComposition = selected
                SideEffect {
                    if (selectedValues.lastOrNull() != selectedInComposition) {
                        selectedValues += selectedInComposition
                    }
                }
            }
            runCurrent()
            fixture.send(selected = 1, unrelated = 7)
            runCurrent()
            composition.advanceFrame()
            runCurrent()
            assertEquals(listOf(0, 1), selectedValues)

            selectUnrelated = true
            composition.advanceFrame()
            runCurrent()
            composition.advanceFrame()
            runCurrent()

            assertEquals(listOf(0, 1, 7), selectedValues)
        } finally {
            composition.close()
            fixture.close()
        }
    }

    private class StoreFixture(
        dispatcher: TestDispatcher,
    ) {
        private val failures = mutableListOf<PulseFailure>()
        private val store: PulseStore<TestState, TestInput, TestEffect> = DefaultPulseStore(
            initialState = TestState(selected = 0, unrelated = 0),
            reducer = PulseReducer { previous, input ->
                ReduceOutcome.Changed(
                    state = TestState(input.selected, input.unrelated),
                    uiEffects = input.effect?.let { listOf(TestEffect(it)) }.orEmpty(),
                )
            },
            config = PulseRuntimeConfig(
                storeDispatcher = dispatcher,
                consumerDispatcher = dispatcher,
                errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                storeId = "pulse-compose-test",
            ),
        )

        val host: PulseStateHost<TestState, TestEffect> = object : PulseStateHost<TestState, TestEffect> {
            override val state = store.state
            override val uiEffects = store.effects
        }

        suspend fun send(effect: String) {
            assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
                store.send(TestInput(host.state.value.selected, host.state.value.unrelated, effect))
            )
        }

        suspend fun send(
            selected: Int,
            unrelated: Int,
        ) {
            assertIs<TransitionResult.Completed<TestState, TestInput, TestEffect>>(
                store.send(TestInput(selected, unrelated, effect = null))
            )
        }

        fun undeliveredEffects(): List<String> {
            return failures.mapNotNull { failure ->
                (failure as? PulseFailure.UndeliveredUiEffect)
                    ?.envelope
                    ?.payload
                    ?.let { it as? TestEffect }
                    ?.value
            }
        }

        fun failures(): List<PulseFailure> = failures.toList()

        suspend fun close() {
            store.close()
            store.awaitClosed()
        }
    }

    private data class TestState(
        val selected: Int,
        val unrelated: Int,
    ) : MviState

    private data class TestInput(
        val selected: Int,
        val unrelated: Int,
        val effect: String?,
    ) : MviIntent

    private data class TestEffect(val value: String) : UiEffect
}
