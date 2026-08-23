package com.magic.mvicore.android.testing

import com.magic.mvicore.android.PulseAndroidExecutionOwner
import com.magic.mvicore.android.PulseIntentExecutionDecision
import com.magic.mvicore.android.PulseIntentExecutionResult
import com.magic.mvicore.android.PulseSplitInput
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.EnqueueResult
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.TaskHandle
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskOutcome
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PulseSplitTestHostTest {
    @Test
    fun `one scheduler drives Main owner runtime state effect and failure probes`() =
        runPulseSplitTest {
            val executorDispatchers = mutableListOf<ContinuationInterceptor?>()
            val host = splitHost(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
                    executorDispatchers += currentCoroutineContext()[ContinuationInterceptor]
                    delay(100)
                    if (intent is TestUi.Add) {
                        context.mutate(TestMutation.Add(intent.amount))
                    }
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertEquals(
                PulseIntentExecutionResult.Completed,
                host.sendAndDrain(TestUi.Add(2)),
            )

            assertEquals(100L, scheduler.currentTime)
            assertSame(dispatcher, executorDispatchers.single())
            assertEquals(listOf(TestState(0), TestState(2)), host.stateProbe.snapshot())
            assertEquals(listOf(TestEffect.Changed(2)), host.effectProbe.payloads())
            val transitions = host.transitionProbe.snapshot()
            assertEquals(2, transitions.size)
            assertIs<PulseSplitInput.Ui<TestUi>>(transitions[0].input)
            assertIs<PulseSplitInput.Mutation<TestMutation>>(transitions[1].input)
            host.failureProbe.assertEmpty()
        }

    @Test
    fun `factory creates application ViewModel subtype and captures executor failure`() =
        runPulseSplitTest {
            val host = splitHost<
                TestState,
                TestUi,
                TestMutation,
                TestEffect,
                FailingTestViewModel
            > { runtimeConfig, executionOwner ->
                FailingTestViewModel(runtimeConfig, executionOwner)
            }

            val result = assertIs<PulseIntentExecutionResult.Failed>(
                host.sendAndDrain(TestUi.Crash)
            )
            assertIs<IllegalStateException>(result.cause)
            assertIs<PulseFailure.ExecutorFailure>(host.failureProbe.snapshot().single())
        }

    @Test
    fun `sendAndDrain excludes infinite task while closeAndDrain cancels it`() =
        runPulseSplitTest {
            val taskStarted = CompletableDeferred<Unit>()
            lateinit var taskHandle: TaskHandle
            val host = splitHost(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { _, context ->
                    val launch = context.launchTask(INFINITE_TASK, TaskPolicy.Latest) {
                        taskStarted.complete(Unit)
                        awaitCancellation()
                    }
                    taskHandle = assertIs<TaskLaunchResult.Accepted>(launch).handle
                    PulseIntentExecutionDecision.Completed
                },
            )

            assertEquals(
                PulseIntentExecutionResult.Completed,
                host.sendAndDrain(TestUi.StartInfiniteTask),
            )
            assertTrue(taskStarted.isCompleted)

            host.closeAndDrain()

            assertEquals(TaskOutcome.Closed, taskHandle.awaitOutcome())
            assertIs<EnqueueResult.Rejected>(host.viewModel.trySend(TestUi.Add(1)))
        }

    @Test
    fun `runPulseSplitTest automatically closes hosts`() {
        var viewModel: PulseSplitStoreViewModel<
            TestState,
            TestUi,
            TestMutation,
            TestEffect
        >? = null

        runPulseSplitTest {
            val host = splitHost<TestState, TestUi, TestMutation, TestEffect>(
                initialState = TestState(0),
                mutationReducer = reducer(),
            )
            viewModel = host.viewModel
            assertTrue(host.failureProbe.snapshot().isEmpty())
        }

        assertIs<EnqueueResult.Rejected>(requireNotNull(viewModel).trySend(TestUi.Add(1)))
    }

    @Test
    fun `closing one host does not drain an infinite delayed task owned by another host`() =
        runPulseSplitTest {
            val handles = mutableListOf<TaskHandle>()
            fun infiniteHost() = splitHost(
                initialState = TestState(0),
                mutationReducer = reducer(),
                uiIntentExecutor = PulseUiIntentExecutor { _, context ->
                    val launch = context.launchTask(INFINITE_TASK, TaskPolicy.Latest) {
                        while (true) delay(1_000)
                    }
                    val handle = assertIs<TaskLaunchResult.Accepted>(launch).handle
                    handles += handle
                    PulseIntentExecutionDecision.Completed
                },
            )
            val first = infiniteHost()
            val second = infiniteHost()

            first.sendAndDrain(TestUi.StartInfiniteTask)
            second.sendAndDrain(TestUi.StartInfiniteTask)

            first.closeAndDrain()

            assertEquals(TaskOutcome.Closed, handles.first().awaitOutcome())
            assertIs<EnqueueResult.Enqueued>(second.viewModel.trySend(TestUi.Add(1)))
        }

    @Test
    fun `nested Split test is rejected before it can replace outer Main`() =
        runPulseSplitTest {
            val failure = assertFailsWith<IllegalStateException> {
                runPulseSplitTest {}
            }

            assertTrue(failure.message.orEmpty().contains("cannot be nested"))
            val host = splitHost<TestState, TestUi, TestMutation, TestEffect>(
                initialState = TestState(0),
                mutationReducer = reducer(),
            )
            assertEquals(
                PulseIntentExecutionResult.Completed,
                host.sendAndDrain(TestUi.Add(1)),
            )
        }

    private class FailingTestViewModel(
        runtimeConfig: PulseRuntimeConfig,
        executionOwner: PulseAndroidExecutionOwner,
    ) : PulseSplitStoreViewModel<TestState, TestUi, TestMutation, TestEffect>(
        initialState = TestState(0),
        mutationReducer = reducer(),
        uiIntentExecutor = PulseUiIntentExecutor { intent, _ ->
            if (intent == TestUi.Crash) throw IllegalStateException("executor failed")
            PulseIntentExecutionDecision.Completed
        },
        runtimeConfig = runtimeConfig,
        executionOwner = executionOwner,
    )

    private data class TestState(val value: Int) : MviState

    private sealed interface TestUi : MviUiIntent {
        data class Add(val amount: Int) : TestUi
        data object Crash : TestUi
        data object StartInfiniteTask : TestUi
    }

    private sealed interface TestMutation : MviMutation {
        data class Add(val amount: Int) : TestMutation
    }

    private sealed interface TestEffect : UiEffect {
        data class Changed(val value: Int) : TestEffect
    }

    private companion object {
        val INFINITE_TASK = TaskKey("android-testing.infinite")

        fun reducer(): PulseMutationReducer<TestState, TestMutation, TestEffect> {
            return PulseMutationReducer { state, mutation ->
                when (mutation) {
                    is TestMutation.Add -> {
                        val nextValue = state.value + mutation.amount
                        ReduceOutcome.Changed(
                            state = state.copy(value = nextValue),
                            uiEffects = listOf(TestEffect.Changed(nextValue)),
                        )
                    }
                }
            }
        }
    }
}
