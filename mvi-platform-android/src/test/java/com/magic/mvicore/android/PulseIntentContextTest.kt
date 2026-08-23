package com.magic.mvicore.android

import com.magic.mvicore.contract.FailureContext
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskLaunchResult
import com.magic.mvicore.contract.TaskPolicy
import com.magic.mvicore.contract.TaskToken
import com.magic.mvicore.runtime.PulseTasks
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
            mutationDispatcher = MutationDispatcher { _, _ -> true },
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
