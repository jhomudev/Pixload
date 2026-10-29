package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDisplayTest {
    @Test fun newestQueuedWorkAppearsAboveActiveWorkWithoutChangingExecutionOrder() {
        val queue = DownloadQueueBook()
        queue.add("first", "First", "")
        queue.state("first", DownloadQueueBook.ACTIVE, 0, 0)
        queue.add("second", "Second", "")

        assertEquals(listOf("second", "first"), queueDisplayItems(queue.items()).map { it.id })
        assertEquals(listOf("first", "second"), queue.pending().map { it.id })
    }
}
