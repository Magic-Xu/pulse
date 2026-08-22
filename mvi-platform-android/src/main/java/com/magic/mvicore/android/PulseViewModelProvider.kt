package com.magic.mvicore.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

/** Factory callback that receives the real owner [CreationExtras]. */
fun interface PulseViewModelCreator<VM : ViewModel> {
    fun create(extras: CreationExtras): VM
}

/** Resolves a ViewModel from an explicit owner and stable key. */
fun <VM : ViewModel> pulseViewModel(
    owner: ViewModelStoreOwner,
    key: String,
    modelClass: Class<VM>,
    creator: PulseViewModelCreator<VM>,
): VM {
    require(key.isNotBlank()) { "ViewModel key must not be blank." }
    return ViewModelProvider(owner, pulseViewModelFactory(modelClass, creator))[key, modelClass]
}

/** Creates a modern factory that preserves [CreationExtras] and SavedState defaults. */
fun <VM : ViewModel> pulseViewModelFactory(
    modelClass: Class<VM>,
    creator: PulseViewModelCreator<VM>,
): ViewModelProvider.Factory {
    val createdClass = modelClass
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras,
        ): T {
            require(modelClass.isAssignableFrom(createdClass)) {
                "Factory for ${createdClass.name} cannot create ${modelClass.name}."
            }
            @Suppress("UNCHECKED_CAST")
            return creator.create(extras) as T
        }
    }
}

/** Creates a factory whose callback receives the owner-created [SavedStateHandle]. */
fun <VM : ViewModel> pulseSavedStateViewModelFactory(
    modelClass: Class<VM>,
    creator: (SavedStateHandle) -> VM,
): ViewModelProvider.Factory {
    return pulseViewModelFactory(modelClass) { extras ->
        creator(extras.createSavedStateHandle())
    }
}
