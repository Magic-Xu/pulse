package com.magic.mvicore.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

internal fun createPulseCoroutineScope(parent: CoroutineScope): CoroutineScope {
    val childJob = SupervisorJob(parent.coroutineContext[Job])
    return CoroutineScope(parent.coroutineContext.minusKey(Job) + childJob)
}
