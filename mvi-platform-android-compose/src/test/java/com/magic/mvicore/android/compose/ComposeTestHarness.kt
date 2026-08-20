package com.magic.mvicore.android.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext

/** Minimal node-free composition used by local JVM tests. */
internal class ComposeTestHarness(
    scope: CoroutineScope,
    effectContext: CoroutineContext,
) {
    private val frameClock = BroadcastFrameClock()
    private var frameTimeNanos = 0L
    private val recomposer = Recomposer(effectContext + frameClock)
    private val composition = Composition(EmptyApplier(), recomposer)
    private val recomposerJob: Job = scope.launch(
        context = frameClock,
        start = CoroutineStart.UNDISPATCHED,
    ) {
        recomposer.runRecomposeAndApplyChanges()
    }

    fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
    }

    suspend fun advanceFrame() {
        check(recomposerJob.isActive) { "Compose test Recomposer stopped before frame delivery." }
        Snapshot.sendApplyNotifications()
        // Let the Recomposer observe invalidations and register with the broadcast clock before
        // sending the frame; a frame sent earlier is correctly not replayed by BroadcastFrameClock.
        var registrationYields = 0
        while (!frameClock.hasAwaiters && registrationYields < MAX_FRAME_REGISTRATION_YIELDS) {
            registrationYields += 1
            yield()
        }
        check(frameClock.hasAwaiters) { "Recomposer did not request a frame after invalidation." }
        frameTimeNanos += FRAME_STEP_NANOS
        frameClock.sendFrame(frameTimeNanos)
        yield()
    }

    suspend fun close() {
        composition.dispose()
        recomposer.cancel()
        recomposerJob.join()
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit

        override fun insertBottomUp(index: Int, instance: Unit) = Unit

        override fun remove(index: Int, count: Int) = Unit

        override fun move(from: Int, to: Int, count: Int) = Unit

        override fun onClear() = Unit
    }

    private companion object {
        const val FRAME_STEP_NANOS = 16_000_000L
        const val MAX_FRAME_REGISTRATION_YIELDS = 100
    }
}
