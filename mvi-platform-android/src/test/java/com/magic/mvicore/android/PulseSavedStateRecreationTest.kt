package com.magic.mvicore.android

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.magic.mvicore.contract.MviMutation
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.MviUiIntent
import com.magic.mvicore.contract.PulseFailure
import com.magic.mvicore.contract.PulseMutationReducer
import com.magic.mvicore.contract.ReduceOutcome
import com.magic.mvicore.contract.UiEffect
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRuntimeConfig
import com.magic.mvicore.contract.TaskKey
import com.magic.mvicore.contract.TaskPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PulseSavedStateRecreationTest {
    @get:Rule
    internal val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `explicit owner factory restores committed state without resurrecting task or effect`() =
        runTest(mainDispatcherRule.dispatcher) {
            val firstOwner = SavedStateOwner(restoredState = null, defaultCount = 0)
            val firstProbe = GenerationProbe()
            val firstCreation = CreationProbe()
            val firstFailures = mutableListOf<PulseFailure>()
            val first = resolve(
                owner = firstOwner,
                generation = firstProbe,
                creation = firstCreation,
                failures = firstFailures,
            )

            first.send(RecreationUi.Commit(4))
            advanceUntilIdle()
            assertEquals(RecreationState(4), first.state.value)
            assertEquals(4, firstCreation.handle?.get<Int>(COUNT_KEY))
            val firstEffect = assertIs<PulseFailure.UndeliveredUiEffect>(firstFailures.single())
            assertEquals(RecreationEffect.Committed(4), firstEffect.envelope.payload)

            first.send(RecreationUi.StartInFlightTask)
            advanceUntilIdle()
            firstProbe.taskStarted.await()
            assertEquals(1, firstProbe.taskStarts)

            val savedRegistryState = Bundle()
            firstOwner.performSave(savedRegistryState)

            firstOwner.viewModelStore.clear()
            advanceUntilIdle()
            first.awaitClosed()
            firstProbe.taskStopped.await()

            val restoredOwner = SavedStateOwner(
                restoredState = savedRegistryState,
                defaultCount = 99,
            )
            val restoredProbe = GenerationProbe()
            val restoredCreation = CreationProbe()
            val restoredFailures = mutableListOf<PulseFailure>()
            val restored = resolve(
                owner = restoredOwner,
                generation = restoredProbe,
                creation = restoredCreation,
                failures = restoredFailures,
            )

            assertEquals(RecreationState(4), restored.state.value)
            assertEquals(4, restoredCreation.handle?.get<Int>(COUNT_KEY))
            assertNotSame(firstCreation.extras, restoredCreation.extras)
            assertNotSame(firstCreation.handle, restoredCreation.handle)
            assertEquals(0, restoredProbe.taskStarts)
            assertTrue(!restoredProbe.taskStarted.isCompleted)

            val restoredEffects = mutableListOf<RecreationEffect>()
            val coordinator = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                restored.uiEffects.collect { envelope ->
                    restoredEffects += envelope.payload
                }
            }
            runCurrent()
            assertTrue(restoredEffects.isEmpty())

            restored.send(RecreationUi.Commit(1))
            advanceUntilIdle()

            assertEquals(RecreationState(5), restored.state.value)
            assertEquals(
                listOf<RecreationEffect>(RecreationEffect.Committed(5)),
                restoredEffects,
            )
            assertTrue(restoredFailures.isEmpty())

            coordinator.cancelAndJoin()
            restoredOwner.viewModelStore.clear()
            advanceUntilIdle()
            restored.awaitClosed()
        }

    private fun resolve(
        owner: SavedStateOwner,
        generation: GenerationProbe,
        creation: CreationProbe,
        failures: MutableList<PulseFailure>,
    ): RecreationViewModel {
        return pulseViewModel(
            owner = owner,
            key = VIEW_MODEL_KEY,
            modelClass = RecreationViewModel::class.java,
        ) { extras ->
            creation.extras = extras
            val handle = extras.createSavedStateHandle()
            creation.handle = handle
            RecreationViewModel(
                handle = handle,
                generation = generation,
                runtimeConfig = PulseRuntimeConfig(
                    storeDispatcher = mainDispatcherRule.dispatcher,
                    consumerDispatcher = mainDispatcherRule.dispatcher,
                    errorHandler = PulseErrorHandler { _, failure, _ -> failures += failure },
                    storeId = "saved-state-recreation",
                ),
            )
        }
    }

    private class SavedStateOwner(
        restoredState: Bundle?,
        defaultCount: Int,
    ) : ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
        private val controller = SavedStateRegistryController.create(this)

        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        override val savedStateRegistry: SavedStateRegistry
            get() = controller.savedStateRegistry
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            ViewModelProvider.NewInstanceFactory()
        override val defaultViewModelCreationExtras: CreationExtras =
            MutableCreationExtras().apply {
                this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@SavedStateOwner
                this[VIEW_MODEL_STORE_OWNER_KEY] = this@SavedStateOwner
                this[DEFAULT_ARGS_KEY] = Bundle().apply { putInt(COUNT_KEY, defaultCount) }
            }

        init {
            controller.performAttach()
            controller.performRestore(restoredState)
            lifecycle.currentState = Lifecycle.State.CREATED
            enableSavedStateHandles()
        }

        fun performSave(outBundle: Bundle) {
            controller.performSave(outBundle)
        }
    }

    private class RecreationViewModel(
        handle: SavedStateHandle,
        generation: GenerationProbe,
        runtimeConfig: PulseRuntimeConfig,
    ) : PulseSplitStoreViewModel<
        RecreationState,
        RecreationUi,
        RecreationMutation,
        RecreationEffect,
        >(
        initialState = RecreationState(-1),
        mutationReducer = REDUCER,
        uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
            when (intent) {
                is RecreationUi.Commit -> context.mutate(RecreationMutation.Add(intent.amount))
                RecreationUi.StartInFlightTask -> {
                    context.launchTask(IN_FLIGHT_TASK_KEY, TaskPolicy.Latest) {
                        generation.taskStarts += 1
                        generation.taskStarted.complete(Unit)
                        try {
                            generation.releaseTask.await()
                            mutate(RecreationMutation.Add(100))
                        } finally {
                            generation.taskStopped.complete(Unit)
                        }
                    }
                }
            }
        },
        runtimeConfig = runtimeConfig,
        savedState = PulseSavedState(handle, SAVED_STATE_ADAPTER),
    )

    private class CreationProbe {
        var extras: CreationExtras? = null
        var handle: SavedStateHandle? = null
    }

    private class GenerationProbe {
        val taskStarted = CompletableDeferred<Unit>()
        val taskStopped = CompletableDeferred<Unit>()
        val releaseTask = CompletableDeferred<Unit>()
        var taskStarts: Int = 0
    }

    private data class RecreationState(val count: Int) : MviState

    private sealed interface RecreationUi : MviUiIntent {
        data class Commit(val amount: Int) : RecreationUi
        data object StartInFlightTask : RecreationUi
    }

    private sealed interface RecreationMutation : MviMutation {
        data class Add(val amount: Int) : RecreationMutation
    }

    private sealed interface RecreationEffect : UiEffect {
        data class Committed(val count: Int) : RecreationEffect
    }

    private companion object {
        const val COUNT_KEY = "count"
        const val VIEW_MODEL_KEY = "saved-state-recreation"

        val IN_FLIGHT_TASK_KEY = TaskKey("in-flight")

        val SAVED_STATE_ADAPTER = object : PulseSavedStateAdapter<RecreationState> {
            override fun restore(handle: SavedStateHandle): RecreationState? {
                return handle.get<Int>(COUNT_KEY)?.let(::RecreationState)
            }

            override fun save(state: RecreationState, handle: SavedStateHandle) {
                handle[COUNT_KEY] = state.count
            }
        }

        val REDUCER = PulseMutationReducer<
            RecreationState,
            RecreationMutation,
            RecreationEffect,
            > { previous, mutation ->
            when (mutation) {
                is RecreationMutation.Add -> {
                    val next = previous.copy(count = previous.count + mutation.amount)
                    ReduceOutcome.Changed(
                        state = next,
                        uiEffects = listOf(RecreationEffect.Committed(next.count)),
                    )
                }
            }
        }
    }
}
