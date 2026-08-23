package com.magic.mvicore.android.testing

import com.magic.mvicore.android.PulseAndroidExecutionOwner
import com.magic.mvicore.android.PulseSavedState
import com.magic.mvicore.android.PulseSplitStoreViewModel
import com.magic.mvicore.android.PulseUiIntentExecutor
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.MailboxOverflowPolicy
import com.magic.mvicore.runtime.PulseRedactor
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.runtime.TypeOnlyPulseRedactor
import com.magic.mvicore.testing.FailureProbe
import com.magic.mvicore.testing.TestRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Bounded runtime settings used by a [TestPulseSplitHost]. */
data class PulseSplitTestConfig(
    val mailboxCapacity: Int = 64,
    val overflowPolicy: MailboxOverflowPolicy = MailboxOverflowPolicy.REJECT_AND_REPORT,
    val effectBufferCapacity: Int = 16,
    val redactor: PulseRedactor = TypeOnlyPulseRedactor,
    val storeId: String? = null,
) {
    init {
        require(mailboxCapacity > 0) { "mailboxCapacity must be greater than zero." }
        require(effectBufferCapacity > 0) { "effectBufferCapacity must be greater than zero." }
        require(storeId == null || storeId.isNotBlank()) { "storeId must be null or non-blank." }
    }
}

/** Creates the real Split ViewModel under test from deterministic runtime dependencies. */
fun interface PulseSplitViewModelTestFactory<
    S : MviState,
    UI : MviUiIntent,
    M : MviMutation,
    E : UiEffect,
    VM : PulseSplitStoreViewModel<S, UI, M, E>,
> {
    fun create(
        runtimeConfig: PulseRuntimeConfig,
        executionOwner: PulseAndroidExecutionOwner,
    ): VM
}

/**
 * Owns one coroutine-test scheduler and every Split host created during [runPulseSplitTest].
 *
 * [dispatcher], [scheduler], Android Main, each runtime config, and each explicit execution owner
 * all use the same virtual-time scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseSplitTestScope internal constructor(
    private val testScope: TestScope,
    val dispatcher: TestDispatcher,
) {
    private val closeActions = mutableListOf<SplitHostCloseAction>()

    val scheduler: TestCoroutineScheduler
        get() = testScope.testScheduler

    /** Runs work already scheduled for the current virtual time. */
    fun runCurrent() = testScope.runCurrent()

    /** Advances virtual time and then runs work scheduled at the target time. */
    fun advanceTimeBy(delayMillis: Long) {
        testScope.advanceTimeBy(delayMillis)
        testScope.runCurrent()
    }

    /**
     * Advances all finite scheduled work.
     *
     * Do not use this as a source/task drain: a ticker or an infinite source is intentionally not a
     * finite unit of work. Await its own probe or task handle instead.
     */
    fun advanceUntilIdle() = testScope.advanceUntilIdle()

    /** Creates a host around an application-owned, real [PulseSplitStoreViewModel] subtype. */
    fun <
        S : MviState,
        UI : MviUiIntent,
        M : MviMutation,
        E : UiEffect,
        VM : PulseSplitStoreViewModel<S, UI, M, E>,
    > splitHost(
        config: PulseSplitTestConfig = PulseSplitTestConfig(),
        factory: PulseSplitViewModelTestFactory<S, UI, M, E, VM>,
    ): TestPulseSplitHost<S, UI, M, E, VM> {
        val failureProbe = FailureProbe()
        val runtimeConfig = createRuntimeConfig(config, failureProbe)
        val parentJob = requireNotNull(testScope.backgroundScope.coroutineContext[Job])
        val executionScope = CoroutineScope(SupervisorJob(parentJob) + dispatcher)
        val executionOwner = PulseAndroidExecutionOwner.from(executionScope)
        val viewModel = try {
            factory.create(runtimeConfig, executionOwner)
        } catch (failure: Throwable) {
            executionScope.cancel()
            throw failure
        }
        val host = try {
            TestPulseSplitHost(
                viewModel = viewModel,
                failureProbe = failureProbe,
                testScope = testScope,
                executionScope = executionScope,
            )
        } catch (failure: Throwable) {
            viewModel.close()
            executionScope.cancel()
            throw failure
        }
        closeActions += SplitHostCloseAction(
            start = host::startClose,
            await = host::awaitCloseAndStopCollectors,
        )
        return host
    }

    /** Convenience factory for a directly configured production Split ViewModel. */
    fun <S : MviState, UI : MviUiIntent, M : MviMutation, E : UiEffect> splitHost(
        initialState: S,
        mutationReducer: PulseMutationReducer<S, M, E>,
        uiIntentExecutor: PulseUiIntentExecutor<S, UI, M> = PulseUiIntentExecutor.noop(),
        savedState: PulseSavedState<S>? = null,
        config: PulseSplitTestConfig = PulseSplitTestConfig(),
    ): TestPulseSplitHost<
        S,
        UI,
        M,
        E,
        PulseSplitStoreViewModel<S, UI, M, E>
    > {
        return splitHost(config) { runtimeConfig, executionOwner ->
            PulseSplitStoreViewModel(
                initialState = initialState,
                mutationReducer = mutationReducer,
                uiIntentExecutor = uiIntentExecutor,
                runtimeConfig = runtimeConfig,
                savedState = savedState,
                executionOwner = executionOwner,
            )
        }
    }

    internal suspend fun closeAll() {
        var failure: Throwable? = null
        val actions = closeActions.asReversed()
        actions.forEach { close ->
            try {
                close.start()
            } catch (next: Throwable) {
                failure = failure.combine(next)
            }
        }
        actions.forEach { close ->
            try {
                close.await()
            } catch (next: Throwable) {
                failure = failure.combine(next)
            }
        }
        closeActions.clear()
        failure?.let { throw it }
    }

    private fun createRuntimeConfig(
        config: PulseSplitTestConfig,
        failureProbe: FailureProbe,
    ): PulseRuntimeConfig {
        val storeId = config.storeId
        val testConfig = if (storeId == null) {
            TestRuntimeConfig(
                dispatcher = dispatcher,
                mailboxCapacity = config.mailboxCapacity,
                overflowPolicy = config.overflowPolicy,
                effectBufferCapacity = config.effectBufferCapacity,
                failureProbe = failureProbe,
                redactor = config.redactor,
            )
        } else {
            TestRuntimeConfig(
                dispatcher = dispatcher,
                mailboxCapacity = config.mailboxCapacity,
                overflowPolicy = config.overflowPolicy,
                effectBufferCapacity = config.effectBufferCapacity,
                failureProbe = failureProbe,
                redactor = config.redactor,
                storeId = storeId,
            )
        }
        return testConfig.toPulseRuntimeConfig()
    }
}

/**
 * Runs a deterministic Android Split test and closes every created host before resetting Main.
 *
 * Calls are serialized because `Dispatchers.Main` is process-global.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runPulseSplitTest(block: suspend PulseSplitTestScope.() -> Unit) {
    synchronized(MAIN_DISPATCHER_LOCK) {
        check(!mainDispatcherLeaseActive) {
            "runPulseSplitTest cannot be nested because Dispatchers.Main is process-global."
        }
        mainDispatcherLeaseActive = true
        try {
            runTest {
                val dispatcher = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(dispatcher)
                val pulseScope = PulseSplitTestScope(this, dispatcher)
                var failure: Throwable? = null
                try {
                    pulseScope.block()
                } catch (bodyFailure: Throwable) {
                    failure = bodyFailure
                }
                try {
                    pulseScope.closeAll()
                } catch (cleanupFailure: Throwable) {
                    failure = failure.combine(cleanupFailure)
                }
                try {
                    Dispatchers.resetMain()
                } catch (resetFailure: Throwable) {
                    failure = failure.combine(resetFailure)
                }
                failure?.let { throw it }
            }
        } finally {
            mainDispatcherLeaseActive = false
        }
    }
}

private fun Throwable?.combine(next: Throwable): Throwable {
    val current = this ?: return next
    if (current !== next) current.addSuppressed(next)
    return current
}

private val MAIN_DISPATCHER_LOCK = Any()
private var mainDispatcherLeaseActive = false

private data class SplitHostCloseAction(
    val start: () -> Unit,
    val await: suspend () -> Unit,
)
