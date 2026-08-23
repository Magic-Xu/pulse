package com.magic.mvicore.android

import com.magic.mvicore.runtime.MailboxOverflowPolicy
import com.magic.mvicore.runtime.PulseClock
import com.magic.mvicore.runtime.PulseErrorHandler
import com.magic.mvicore.runtime.PulseRedactor
import com.magic.mvicore.runtime.PulseRuntimeConfig
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PulseAndroidRuntimeConfigTest {
    @Test
    fun `Android overlay preserves options and applies Main dispatchers`() {
        val clock = PulseClock { 17L }
        val errorHandler = PulseErrorHandler { _, _, _ -> }
        val redactor = PulseRedactor { "safe" }
        val base = PulseRuntimeConfig(
            mailboxCapacity = 7,
            overflowPolicy = MailboxOverflowPolicy.REJECT,
            effectBufferCapacity = 9,
            storeDispatcher = Dispatchers.IO,
            consumerDispatcher = Dispatchers.Default,
            clock = clock,
            errorHandler = errorHandler,
            strictMode = true,
            redactor = redactor,
            storeId = "android-overlay-test",
        )

        val actual = androidPulseRuntimeConfig(base)

        assertEquals(7, actual.mailboxCapacity)
        assertEquals(MailboxOverflowPolicy.REJECT, actual.overflowPolicy)
        assertEquals(9, actual.effectBufferCapacity)
        assertSame(Dispatchers.Main.immediate, actual.storeDispatcher)
        assertSame(Dispatchers.Main.immediate, actual.consumerDispatcher)
        assertSame(clock, actual.clock)
        assertSame(errorHandler, actual.errorHandler)
        assertTrue(actual.strictMode)
        assertSame(redactor, actual.redactor)
        assertEquals("android-overlay-test", actual.storeId)
    }
}
