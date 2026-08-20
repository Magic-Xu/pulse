package com.magic.mvicore.android

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PulseViewModelProviderTest {
    @Test
    fun `same owner and key reuse instance while different owners stay isolated`() {
        val firstOwner = TestOwner()
        val secondOwner = TestOwner()
        var creations = 0

        val first = pulseViewModel(
            owner = firstOwner,
            key = "shared-key",
            modelClass = TrackingViewModel::class.java,
        ) {
            creations += 1
            TrackingViewModel()
        }
        val reused = pulseViewModel(
            owner = firstOwner,
            key = "shared-key",
            modelClass = TrackingViewModel::class.java,
        ) {
            error("creator must not run for an existing key")
        }
        val isolated = pulseViewModel(
            owner = secondOwner,
            key = "shared-key",
            modelClass = TrackingViewModel::class.java,
        ) {
            creations += 1
            TrackingViewModel()
        }

        assertSame(first, reused)
        assertNotSame(first, isolated)
        assertEquals(2, creations)

        firstOwner.viewModelStore.clear()
        assertEquals(1, first.clearCount)
        assertEquals(0, isolated.clearCount)
        secondOwner.viewModelStore.clear()
        assertEquals(1, isolated.clearCount)
    }

    @Test
    fun `owner CreationExtras values reach creator`() {
        val extras = MutableCreationExtras().apply {
            this[MarkerKey] = "owner-marker"
        }
        val owner = TestOwner(extras)
        var received: CreationExtras? = null

        val viewModel = pulseViewModel(
            owner = owner,
            key = "extras-key",
            modelClass = TrackingViewModel::class.java,
        ) { creationExtras ->
            received = creationExtras
            TrackingViewModel()
        }

        assertEquals("owner-marker", received?.get(MarkerKey))

        owner.viewModelStore.clear()
        assertEquals(1, viewModel.clearCount)
    }

    @Test
    fun `blank explicit key is rejected before creator runs`() {
        val owner = TestOwner()
        var creatorRan = false

        assertFailsWith<IllegalArgumentException> {
            pulseViewModel(
                owner = owner,
                key = "   ",
                modelClass = TrackingViewModel::class.java,
            ) {
                creatorRan = true
                TrackingViewModel()
            }
        }

        assertTrue(!creatorRan)
    }

    @Test
    fun `saved state factory creates handle with owner default args`() {
        val defaultArgs = Bundle().apply {
            putInt(DEFAULT_COUNT_KEY, 23)
        }
        val owner = SavedStateTestOwner(defaultArgs)
        val factory = pulseSavedStateViewModelFactory(SavedStateViewModel::class.java) { handle ->
            SavedStateViewModel(handle)
        }
        val provider = ViewModelProvider(
            owner.viewModelStore,
            factory,
            owner.defaultViewModelCreationExtras,
        )

        val viewModel = provider["saved-key", SavedStateViewModel::class.java]

        assertEquals(23, viewModel.handle.get<Int>(DEFAULT_COUNT_KEY))
        viewModel.handle[DEFAULT_COUNT_KEY] = 31
        assertEquals(31, viewModel.handle.get<Int>(DEFAULT_COUNT_KEY))

        owner.viewModelStore.clear()
        assertEquals(1, viewModel.clearCount)
    }

    private open class TestOwner(
        private val extras: CreationExtras = CreationExtras.Empty,
    ) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore: ViewModelStore = ViewModelStore()

        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            ViewModelProvider.NewInstanceFactory()

        override val defaultViewModelCreationExtras: CreationExtras
            get() = extras
    }

    private class SavedStateTestOwner(
        defaultArgs: Bundle,
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
                this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@SavedStateTestOwner
                this[VIEW_MODEL_STORE_OWNER_KEY] = this@SavedStateTestOwner
                this[DEFAULT_ARGS_KEY] = defaultArgs
            }

        init {
            controller.performAttach()
            controller.performRestore(null)
            lifecycle.currentState = Lifecycle.State.CREATED
            enableSavedStateHandles()
        }
    }

    private class TrackingViewModel : ViewModel() {
        var clearCount: Int = 0
            private set

        override fun onCleared() {
            clearCount += 1
        }
    }

    private class SavedStateViewModel(
        val handle: SavedStateHandle,
    ) : ViewModel() {
        var clearCount: Int = 0
            private set

        override fun onCleared() {
            clearCount += 1
        }
    }

    private object MarkerKey : CreationExtras.Key<String>

    private companion object {
        const val DEFAULT_COUNT_KEY = "default-count"
    }
}
