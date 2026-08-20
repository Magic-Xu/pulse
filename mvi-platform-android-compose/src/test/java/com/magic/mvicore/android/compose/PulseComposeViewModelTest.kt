package com.magic.mvicore.android.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.magic.mvicore.android.PulseStateHost
import com.magic.mvicore.android.PulseViewModelCreator
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.PulseReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.DefaultPulseStore
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.PulseStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PulseComposeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `compose factory scopes instances to the explicit owner`() =
        runTest(mainDispatcherRule.dispatcher) {
        val firstOwner = TestOwner()
        val secondOwner = TestOwner()
        val composition = ComposeTestHarness(this, coroutineContext)
        var creations = 0
        var first: TestViewModel? = null
        var firstAgain: TestViewModel? = null
        var second: TestViewModel? = null

        composition.setContent {
            first = pulseViewModel(
                owner = firstOwner,
                key = "shared-key",
                modelClass = TestViewModel::class.java,
                creator = PulseViewModelCreator { TestViewModel(++creations) },
            )
            firstAgain = pulseViewModel(
                owner = firstOwner,
                key = "shared-key",
                modelClass = TestViewModel::class.java,
                creator = PulseViewModelCreator { TestViewModel(++creations) },
            )
            second = pulseViewModel(
                owner = secondOwner,
                key = "shared-key",
                modelClass = TestViewModel::class.java,
                creator = PulseViewModelCreator { TestViewModel(++creations) },
            )
        }
        runCurrent()

        assertSame(first, firstAgain)
        assertNotSame(first, second)
        assertEquals(2, creations)

        composition.close()
        firstOwner.viewModelStore.clear()
        secondOwner.viewModelStore.clear()
    }

    @Test
    fun `configuration owner replacement reuses ViewModel from retained store`() =
        runTest(mainDispatcherRule.dispatcher) {
            val retainedStore = ViewModelStore()
            var owner by mutableStateOf(TestOwner(retainedStore))
            val composition = ComposeTestHarness(this, coroutineContext)
            var creations = 0
            var resolved: TestViewModel? = null
            composition.setContent {
                resolved = pulseViewModel(
                    owner = owner,
                    key = "retained-key",
                    modelClass = TestViewModel::class.java,
                    creator = PulseViewModelCreator { TestViewModel(++creations) },
                )
            }
            runCurrent()
            val first = resolved

            owner = TestOwner(retainedStore)
            composition.advanceFrame()
            runCurrent()

            assertSame(first, resolved)
            assertEquals(1, creations)
            composition.close()
            retainedStore.clear()
        }

    @Test
    fun `configuration replacement keeps one effect session and does not replay gap effects`() =
        runTest(mainDispatcherRule.dispatcher) {
            val retainedStore = ViewModelStore()
            var viewModelOwner by mutableStateOf(TestOwner(retainedStore))
            var lifecycleOwner by mutableStateOf(
                TestLifecycleOwner(
                    Lifecycle.State.STARTED,
                    UnconfinedTestDispatcher(testScheduler),
                )
            )
            val composition = ComposeTestHarness(this, coroutineContext)
            val received = mutableListOf<String>()
            var creations = 0
            var resolved: EffectHostViewModel? = null
            composition.setContent {
                val viewModel = pulseViewModel(
                    owner = viewModelOwner,
                    key = "retained-effect-host",
                    modelClass = EffectHostViewModel::class.java,
                    creator = PulseViewModelCreator {
                        creations += 1
                        EffectHostViewModel(mainDispatcherRule.dispatcher)
                    },
                )
                resolved = viewModel
                viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
                    received += effect.value
                }
            }
            runCurrent()

            val retained = requireNotNull(resolved)
            retained.emit("before-recreation")
            runCurrent()
            assertEquals(listOf("before-recreation"), received)

            lifecycleOwner.currentState = Lifecycle.State.CREATED
            runCurrent()
            retained.emit("between-owners")
            runCurrent()
            assertEquals(listOf("before-recreation"), received)

            viewModelOwner = TestOwner(retainedStore)
            lifecycleOwner = TestLifecycleOwner(
                Lifecycle.State.STARTED,
                UnconfinedTestDispatcher(testScheduler),
            )
            composition.advanceFrame()
            runCurrent()

            assertSame(retained, resolved)
            assertEquals(1, creations)
            retained.emit("after-recreation")
            runCurrent()
            assertEquals(listOf("before-recreation", "after-recreation"), received)
            assertTrue(retained.undelivered().contains("between-owners"))

            composition.close()
            retained.closeAndAwait()
            retainedStore.clear()
        }

    private class TestOwner(
        override val viewModelStore: ViewModelStore = ViewModelStore(),
    ) : ViewModelStoreOwner {
    }

    private class TestViewModel(
        val creationNumber: Int,
    ) : ViewModel()

    private class EffectHostViewModel(
        dispatcher: CoroutineDispatcher,
    ) : ViewModel(), PulseStateHost<EffectState, TestEffect> {
        private val failures = mutableListOf<com.magic.mvicore.contract.PulseFailure>()
        private val store: PulseStore<EffectState, EffectInput, TestEffect> = DefaultPulseStore(
            initialState = EffectState,
            reducer = PulseReducer { _, input ->
                ReduceOutcome.Unchanged(listOf(TestEffect(input.value)))
            },
            config = PulseRuntimeConfig(
                storeDispatcher = dispatcher,
                consumerDispatcher = dispatcher,
                errorHandler = { _, failure, _ -> failures += failure },
                storeId = "compose-config-effect-host",
            ),
        )

        override val state = store.state
        override val uiEffects = store.effects

        suspend fun emit(value: String) {
            store.send(EffectInput(value))
        }

        fun undelivered(): List<String> {
            return failures.mapNotNull { failure ->
                (failure as? com.magic.mvicore.contract.PulseFailure.UndeliveredUiEffect)
                    ?.envelope
                    ?.payload
                    ?.let { it as? TestEffect }
                    ?.value
            }
        }

        suspend fun closeAndAwait() {
            store.close()
            store.awaitClosed()
        }

        override fun onCleared() {
            store.close()
        }
    }

    private data object EffectState : MviState

    private data class EffectInput(val value: String) : MviIntent

    private data class TestEffect(val value: String) : UiEffect
}
