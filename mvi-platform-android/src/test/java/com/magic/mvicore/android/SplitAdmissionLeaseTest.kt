package com.magic.mvicore.android

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitAdmissionLeaseTest {
    @Test
    fun `cancellation before Core claim releases and prevents executor transfer`() {
        var releases = 0
        val lease = SplitAdmissionLease { releases += 1 }

        lease.cancelBeforeExecutor()

        assertEquals(1, releases)
        assertFalse(lease.claim())
        assertFalse(lease.transferToExecutor())
        lease.release()
        assertEquals(1, releases)
    }

    @Test
    fun `cancellation after Core claim releases at transfer boundary`() {
        var releases = 0
        val lease = SplitAdmissionLease { releases += 1 }

        assertTrue(lease.claim())
        lease.cancelBeforeExecutor()

        assertEquals(0, releases)
        assertFalse(lease.transferToExecutor())
        assertEquals(1, releases)
    }

    @Test
    fun `cancellation after transfer leaves release to executor cleanup`() {
        var releases = 0
        val lease = SplitAdmissionLease { releases += 1 }

        assertTrue(lease.claim())
        assertTrue(lease.transferToExecutor())
        lease.cancelBeforeExecutor()

        assertEquals(0, releases)
        lease.release()
        lease.release()
        assertEquals(1, releases)
    }
}
