package com.pixload.app

import org.junit.Assert.*
import org.junit.Test

class RetryProgressTest {
    @Test fun retryShowsBusyImmediatelyAndCannotStartTwiceUntilPreflightEnds() {
        val progress = RetryProgress()
        assertFalse(progress.active("work"))
        assertTrue(progress.begin("work"))
        assertTrue(progress.active("work"))
        assertFalse(progress.begin("work"))
        progress.end("work")
        assertFalse(progress.active("work"))
        assertTrue(progress.begin("work"))
    }

    @Test fun finishingOneRetryKeepsAnotherBusy() {
        val progress = RetryProgress()
        progress.begin("a")
        progress.begin("b")
        progress.end("a")
        assertFalse(progress.active("a"))
        assertTrue(progress.active("b"))
    }
}
