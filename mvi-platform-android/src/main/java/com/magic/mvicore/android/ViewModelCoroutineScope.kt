package com.magic.mvicore.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun createPulseCoroutineScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
