package com.magic.mvicore.android

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.runtime.PulseTasks
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PulseIntentContextTest {
    @Test
    fun `launch task forwards the originating UI intent correlation`() {
        val tasks = RecordingPulseTasks()
        val key = TaskKey("refresh-feed")
        val context = PulseIntentContext<TestState, TestMutation>(
            intentId = 83L,
            stateAtStart = TestState,
            inputType = "com.example.RefreshIntent",
            stateProvider = { TestState },
            mutationDispatcher = DetailedMutationDispatcher { _, _, _ -> PulseMutationResult.Changed },
            tasks = tasks,
            lifecycleActive = { true },
            failureReporter = { },
        )

        val result = context.launchTask(key, TaskPolicy.Latest) { }

        assertEquals(TaskLaunchResult.Closed, result)
        assertEquals(83L, tasks.failureContext?.requestId)
        assertEquals("com.example.RefreshIntent", tasks.failureContext?.inputType)
        assertEquals(key.value, tasks.failureContext?.component)
    }

    @Test
    fun `legacy dispatcher and task constructor retain Boolean behavior`() = runTest {
        val token = object : TaskToken {
            override val key = TaskKey("legacy")
            override val value = 1L
        }
        var accepted = true
        val dispatcher = MutationDispatcher<TestMutation> { mutation, receivedToken ->
            assertEquals(TestMutation, mutation)
            assertEquals(token, receivedToken)
            accepted
        }
        val context = PulseTaskContext({ TestState }, dispatcher, token)
        assertEquals(true, context.mutate(TestMutation))
        accepted = false
        assertEquals(false, context.mutate(TestMutation))
    }

    private class RecordingPulseTasks : PulseTasks {
        override val isClosed: Boolean = false

        var failureContext: FailureContext? = null
            private set

        override fun launch(
            key: TaskKey,
            policy: TaskPolicy,
            block: suspend (TaskToken) -> Unit,
        ): TaskLaunchResult {
            error("PulseIntentContext must use the correlated task launch path.")
        }

        override fun launch(
            key: TaskKey,
            policy: TaskPolicy,
            failureContext: FailureContext,
            block: suspend (TaskToken) -> Unit,
        ): TaskLaunchResult {
            this.failureContext = failureContext
            return TaskLaunchResult.Closed
        }

        override fun isCurrent(token: TaskToken): Boolean = false

        override fun validate(token: TaskToken): Boolean = false

        override fun cancel(key: TaskKey): Boolean = false

        override fun cancelAll(): Int = 0
    }

    private data object TestState : MviState

    private data object TestMutation : MviMutation
}
