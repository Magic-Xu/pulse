package com.magic.mvicore.android

import androidx.lifecycle.SavedStateHandle
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSavedStateTest {
    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restored state becomes initial state and later state is written back`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val handle = SavedStateHandle(mapOf(COUNT_KEY to 5))
            val savedState = PulseSavedState(handle, CountSavedStateAdapter)
            val viewModel = viewModel(
                initialState = TestState(0),
                failures = failures,
                savedState = savedState,
            )

            assertEquals(TestState(5), viewModel.state.value)

            viewModel.send(TestUi.Add(2))
            advanceUntilIdle()

            assertEquals(TestState(7), viewModel.state.value)
            assertEquals(7, handle.get<Int>(COUNT_KEY))
            assertTrue(failures.isEmpty())

            close(viewModel)
        }

    @Test
    fun `feature adapter can migrate an older saved state schema`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val handle = SavedStateHandle(
                mapOf(
                    SCHEMA_KEY to 1,
                    LEGACY_COUNT_KEY to 6,
                )
            )
            val migratingAdapter = object : PulseSavedStateAdapter<TestState> {
                override fun restore(handle: SavedStateHandle): TestState? {
                    return when (handle.get<Int>(SCHEMA_KEY)) {
                        1 -> handle.get<Int>(LEGACY_COUNT_KEY)?.let(::TestState)
                        2 -> handle.get<Int>(COUNT_KEY)?.let(::TestState)
                        else -> null
                    }
                }

                override fun save(state: TestState, handle: SavedStateHandle) {
                    handle[SCHEMA_KEY] = 2
                    handle[COUNT_KEY] = state.value
                    handle.remove<Int>(LEGACY_COUNT_KEY)
                }
            }
            val viewModel = viewModel(
                initialState = TestState(0),
                failures = failures,
                savedState = PulseSavedState(handle, migratingAdapter),
            )

            assertEquals(TestState(6), viewModel.state.value)
            viewModel.send(TestUi.Add(1))
            advanceUntilIdle()

            assertEquals(2, handle.get<Int>(SCHEMA_KEY))
            assertEquals(7, handle.get<Int>(COUNT_KEY))
            assertEquals(null, handle.get<Int>(LEGACY_COUNT_KEY))
            assertTrue(failures.isEmpty())
            close(viewModel)
        }

    @Test
    fun `ordinary restore failure reports typed failure and uses constructor fallback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val expected = IllegalStateException("restore failed")
            val binding = PulseSavedState(
                SavedStateHandle(),
                object : PulseSavedStateAdapter<TestState> {
                    override fun restore(handle: SavedStateHandle): TestState? = throw expected

                    override fun save(state: TestState, handle: SavedStateHandle) = Unit
                },
            )
            val viewModel = viewModel(
                initialState = TestState(11),
                failures = failures,
                savedState = binding,
            )
            advanceUntilIdle()

            assertEquals(TestState(11), viewModel.state.value)
            val failure = assertTypedSavedStateFailure(failures.single())
            assertSame(expected, failure.cause)

            close(viewModel)
        }

    @Test
    fun `ordinary save failure is typed without rolling back committed state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val expected = IllegalArgumentException("save failed")
            val handle = SavedStateHandle()
            val binding = PulseSavedState(
                handle,
                object : PulseSavedStateAdapter<TestState> {
                    override fun restore(handle: SavedStateHandle): TestState? = null

                    override fun save(state: TestState, handle: SavedStateHandle) {
                        if (state.value == 3) throw expected
                        handle[COUNT_KEY] = state.value
                    }
                },
            )
            val viewModel = viewModel(
                initialState = TestState(0),
                failures = failures,
                savedState = binding,
            )

            viewModel.send(TestUi.Add(3))
            advanceUntilIdle()

            assertEquals(TestState(3), viewModel.state.value)
            assertEquals(0, handle.get<Int>(COUNT_KEY))
            val failure = assertTypedSavedStateSaveFailure(failures.single())
            assertSame(expected, failure.cause)

            close(viewModel)
        }

    @Test
    fun `close checkpoints the final state of an input admitted before cutoff`() =
        runTest(mainDispatcherRule.dispatcher) {
            val failures = mutableListOf<PulseFailure>()
            val handle = SavedStateHandle(mapOf(COUNT_KEY to 0))
            val reducerStarted = CountDownLatch(1)
            val releaseReducer = CountDownLatch(1)
            val config = PulseRuntimeConfig(
                storeDispatcher = Dispatchers.Default,
                consumerDispatcher = mainDispatcherRule.dispatcher,
                errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                storeId = "saved-state-close-drain",
            )
            val viewModel = PulseSplitStoreViewModel(
                initialState = TestState(0),
                mutationReducer = PulseMutationReducer { previous, mutation ->
                    reducerStarted.countDown()
                    check(releaseReducer.await(5, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the admitted reducer frame."
                    }
                    val add = mutation as TestMutation.Add
                    ReduceOutcome.Changed(previous.copy(value = previous.value + add.amount))
                },
                uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                    if (intent is TestUi.Add) context.mutate(TestMutation.Add(intent.amount))
                },
                runtimeConfig = config,
                savedState = PulseSavedState(handle, CountSavedStateAdapter),
            )

            viewModel.send(TestUi.Add(8))
            advanceUntilIdle()
            assertTrue(
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    reducerStarted.await(5, TimeUnit.SECONDS)
                }
            )
            viewModel.close()
            releaseReducer.countDown()
            advanceUntilIdle()
            viewModel.awaitClosed()

            assertEquals(TestState(8), viewModel.state.value)
            assertEquals(8, handle.get<Int>(COUNT_KEY))
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `restore cancellation and fatal errors propagate without typed conversion`() {
        val cancellation = CancellationException("restore cancelled")
        val fatal = TestFatalError("restore fatal")

        val cancellationFailures = mutableListOf<PulseFailure>()
        val cancellationThrown = assertFailsWith<CancellationException> {
            viewModel(
                initialState = TestState(4),
                failures = cancellationFailures,
                savedState = throwingRestore(cancellation),
            )
        }
        assertSame(cancellation, cancellationThrown)
        assertTrue(cancellationFailures.isEmpty())

        val fatalFailures = mutableListOf<PulseFailure>()
        val fatalThrown = assertFailsWith<TestFatalError> {
            viewModel(
                initialState = TestState(4),
                failures = fatalFailures,
                savedState = throwingRestore(fatal),
            )
        }
        assertSame(fatal, fatalThrown)
        assertTrue(fatalFailures.isEmpty())
    }

    @Test
    fun `terminal restore failure creates no child in an injected parent scope`() {
        val parentJob = SupervisorJob()
        val fatal = TestFatalError("restore before owner creation")
        val config = PulseRuntimeConfig(
            storeDispatcher = mainDispatcherRule.dispatcher,
            consumerDispatcher = mainDispatcherRule.dispatcher,
            errorHandler = PulseErrorHandler { _, _, _ -> },
            storeId = "restore-construction-failure",
        )

        val thrown = assertFailsWith<TestFatalError> {
            PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>(
                initialState = TestState(0),
                mutationReducer = REDUCER,
                runtimeConfig = config,
                savedState = throwingRestore(fatal),
                executionOwner = PulseAndroidExecutionOwner.from(
                    CoroutineScope(parentJob + mainDispatcherRule.dispatcher)
                ),
            )
        }

        assertSame(fatal, thrown)
        assertFalse(parentJob.children.any())
        parentJob.cancel()
    }

    @Test
    fun `saved state binding preserves cancellation and fatal save errors`() {
        val cancellation = CancellationException("save cancelled")
        val fatal = TestFatalError("save fatal")

        val cancellationBinding = throwingSave(cancellation)
        assertSame(
            cancellation,
            assertFailsWith<CancellationException> { cancellationBinding.save(TestState(1)) },
        )

        val fatalBinding = throwingSave(fatal)
        assertSame(
            fatal,
            assertFailsWith<TestFatalError> { fatalBinding.save(TestState(1)) },
        )
    }

    @Test
    fun `fatal saved state checkpoint closes the ViewModel instead of leaving a zombie lane`() =
        runTest(mainDispatcherRule.dispatcher) {
            val parentJob = SupervisorJob()
            val terminalFailure = CompletableDeferred<Throwable>()
            val ownerScope = CoroutineScope(
                parentJob +
                    mainDispatcherRule.dispatcher +
                    CoroutineExceptionHandler { _, failure ->
                        terminalFailure.complete(failure)
                    }
            )
            val fatal = TestFatalError("fatal checkpoint")
            val config = PulseRuntimeConfig(
                storeDispatcher = mainDispatcherRule.dispatcher,
                consumerDispatcher = mainDispatcherRule.dispatcher,
                errorHandler = PulseErrorHandler { _, _, _ -> },
                storeId = "fatal-checkpoint",
            )
            val viewModel = PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>(
                initialState = TestState(0),
                mutationReducer = REDUCER,
                runtimeConfig = config,
                savedState = throwingSave(fatal),
                executionOwner = PulseAndroidExecutionOwner.from(ownerScope),
            )

            advanceUntilIdle()

            val actual = assertIs<TestFatalError>(terminalFailure.await())
            assertEquals(fatal.message, actual.message)
            val closeFailure = assertFailsWith<TestFatalError> { viewModel.awaitClosed() }
            assertEquals(fatal.message, closeFailure.message)
            assertIs<EnqueueResult.Rejected>(viewModel.trySend(TestUi.Add(1)))
            assertTrue(parentJob.isActive)
            parentJob.cancel()
        }

    private fun viewModel(
        initialState: TestState,
        failures: MutableList<PulseFailure>,
        savedState: PulseSavedState<TestState>,
    ): PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect> {
        val config = PulseRuntimeConfig(
            storeDispatcher = mainDispatcherRule.dispatcher,
            consumerDispatcher = mainDispatcherRule.dispatcher,
            errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
            storeId = "saved-state-test",
        )
        return PulseSplitStoreViewModel(
            initialState = initialState,
            mutationReducer = REDUCER,
            uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                if (intent is TestUi.Add) context.mutate(TestMutation.Add(intent.amount))
            },
            runtimeConfig = config,
            savedState = savedState,
        )
    }

    private suspend fun close(
        viewModel: PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>,
    ) {
        viewModel.close()
        viewModel.awaitClosed()
    }

    private fun assertTypedSavedStateFailure(
        failure: PulseFailure,
    ): PulseFailure.StateRestoreFailure {
        val typed = failure as PulseFailure.StateRestoreFailure
        assertEquals("saved-state", typed.context.component)
        return typed
    }

    private fun assertTypedSavedStateSaveFailure(
        failure: PulseFailure,
    ): PulseFailure.StateSaveFailure {
        val typed = failure as PulseFailure.StateSaveFailure
        assertEquals("saved-state", typed.context.component)
        return typed
    }

    private fun throwingRestore(error: Throwable): PulseSavedState<TestState> {
        return PulseSavedState(
            SavedStateHandle(),
            object : PulseSavedStateAdapter<TestState> {
                override fun restore(handle: SavedStateHandle): TestState? = throw error

                override fun save(state: TestState, handle: SavedStateHandle) = Unit
            },
        )
    }

    private fun throwingSave(error: Throwable): PulseSavedState<TestState> {
        return PulseSavedState(
            SavedStateHandle(),
            object : PulseSavedStateAdapter<TestState> {
                override fun restore(handle: SavedStateHandle): TestState? = null

                override fun save(state: TestState, handle: SavedStateHandle) {
                    throw error
                }
            },
        )
    }

    private data class TestState(val value: Int) : MviState

    private sealed interface TestUi : MviUiIntent {
        data class Add(val amount: Int) : TestUi
    }

    private sealed interface TestMutation : MviMutation {
        data class Add(val amount: Int) : TestMutation
    }

    private sealed interface TestEffect : UiEffect

    private class TestFatalError(message: String) : LinkageError(message)

    private companion object {
        const val COUNT_KEY = "count"
        const val LEGACY_COUNT_KEY = "legacy-count"
        const val SCHEMA_KEY = "schema-version"

        val CountSavedStateAdapter = object : PulseSavedStateAdapter<TestState> {
            override fun restore(handle: SavedStateHandle): TestState? {
                return handle.get<Int>(COUNT_KEY)?.let(::TestState)
            }

            override fun save(state: TestState, handle: SavedStateHandle) {
                handle[COUNT_KEY] = state.value
            }
        }

        val REDUCER = PulseMutationReducer<TestState, TestMutation, TestEffect> { previous, mutation ->
            when (mutation) {
                is TestMutation.Add -> ReduceOutcome.Changed(
                    previous.copy(value = previous.value + mutation.amount)
                )
            }
        }
    }
}
