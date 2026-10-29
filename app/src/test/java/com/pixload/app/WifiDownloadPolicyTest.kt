package com.pixload.app

import org.junit.Assert.*
import org.junit.Test

class WifiDownloadPolicyTest {
    private class FakeTransport {
        var transferred = 0
        fun transfer(policy: WifiDownloadPolicy, restricted: Boolean, wifi: Boolean): Boolean {
            if (!policy.admit(restricted, wifi)) return false
            transferred++
            return true
        }
    }

    @Test fun optInWaitsWithoutUsingWorkerAndResumesOnlyWhileProcessLives() {
        val policy = WifiDownloadPolicy()
        val transport = FakeTransport()
        val book = DownloadQueueBook()
        assertTrue(book.add("a", "A", ""))
        assertTrue(book.add("b", "B", ""))
        assertFalse(transport.transfer(policy, true, false))
        assertTrue(book.state("a", DownloadQueueBook.WAITING_WIFI, 0, 0))
        assertTrue(book.state("b", DownloadQueueBook.WAITING_WIFI, 0, 0))
        assertEquals(2, book.count())
        assertEquals(0, transport.transferred)
        assertTrue(transport.transfer(policy, true, true))
        assertTrue(book.state("a", DownloadQueueBook.ACTIVE, 0, 0))
        assertEquals(1, transport.transferred)
        assertTrue(book.interruptPending())
        assertEquals(0, book.count())
        assertEquals(DownloadQueueBook.INTERRUPTED, book.items()[1].state)
    }

    @Test fun lossDuringTransferInterruptsAndReleasesQueueCapacity() {
        val policy = WifiDownloadPolicy()
        val transport = FakeTransport()
        val book = DownloadQueueBook()
        for (i in 1..5) assertTrue(book.add("$i", "Work $i", ""))
        assertFalse(book.add("6", "Full", ""))
        assertTrue(transport.transfer(policy, true, true))
        assertTrue(book.state("1", DownloadQueueBook.ACTIVE, 0, 100))
        assertFalse(transport.transfer(policy, true, false))
        assertTrue(book.state("1", DownloadQueueBook.INTERRUPTED, 0, 0))
        assertEquals(4, book.count())
        assertTrue(book.add("6", "New work", ""))
        assertEquals(1, transport.transferred)
        assertTrue(book.remove("2")) // Cancel a waiting row without cancelling other work.
        assertEquals(4, book.count())
    }

    @Test fun anyWifiIncludingMeteredIsAdmittedButUnknownIsClosed() {
        val policy = WifiDownloadPolicy()
        assertFalse(policy.admit(true, false))
        assertTrue(policy.admit(true, true)) // Metered flag is deliberately not consulted.
    }

    @Test fun previousUnrestrictedBehaviorIsPreserved() {
        val transport = FakeTransport()
        val policy = WifiDownloadPolicy()
        assertTrue(transport.transfer(policy, false, false))
        assertEquals(1, transport.transferred)
        val book = DownloadQueueBook()
        assertTrue(book.add("a", "A", ""))
        assertEquals(DownloadQueueBook.QUEUED, book.items()[0].state)
        assertTrue(book.state("a", DownloadQueueBook.ACTIVE, 0, 0))
        assertTrue(book.state("a", DownloadQueueBook.ERROR, 0, 0))
        assertEquals(0, book.count())
        assertTrue(book.add("a", "A", ""))
        assertTrue(book.remove("a"))
        assertEquals(0, book.count())
    }
}
