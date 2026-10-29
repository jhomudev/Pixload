package com.pixload.app

import org.junit.Assert.*
import org.junit.Test

class RetryDownloadTest {
    @Test fun preservesSelectionAndRejectsChangedOrMissingWork() {
        val retry = RetryDownload("42", "image", "content://tree", listOf(0, 2), "", "")
        assertEquals(listOf(0, 2), retry.validated("42", "image", 3))
        assertNull(retry.validated("42", "image", 2))
        assertNull(retry.validated("43", "image", 3))
        assertNull(retry.validated("42", "ugoira", 3))
        assertNull(retry.copy(pages = listOf(0, 0)).validated("42", "image", 3))
        assertNull(retry.copy(pages = emptyList()).validated("42", "image", 3))
    }

    @Test fun validatesAnimationFormatAndPresetWithoutFallback() {
        val retry = RetryDownload("42", "ugoira", "content://tree", emptyList(), "gif", "quality")
        assertNotNull(retry.validated("42", "ugoira", 0))
        assertNull(retry.copy(preset = "unknown").validated("42", "ugoira", 0))
        assertNull(retry.copy(format = "zip").validated("42", "ugoira", 0))
        assertNotNull(retry.copy(format = "mp4", preset = "").validated("42", "ugoira", 0))
        assertNull(retry.copy(folder = "").validated("42", "ugoira", 0))
    }

    @Test fun novelRetryPreservesChosenEpubOrTxtFormat() {
        val epub = RetryDownload("novel-42", "novel", "content://tree", emptyList(), "epub", "")
        val txt = epub.copy(format = "txt")
        assertNotNull(epub.validated("novel-42", "novel", 0))
        assertNotNull(txt.validated("novel-42", "novel", 0))
        assertNull(epub.copy(format = "pdf").validated("novel-42", "novel", 0))
    }

    @Test fun failureAndInterruptionKeepOriginalSnapshotAndExistingAdmissionRules() {
        val book = DownloadQueueBook()
        val snapshot = RetryDownload("42", "image", "content://tree", listOf(1), "", "")
        assertTrue(book.add("42", "Original", "", 1, "manga", snapshot))
        book.state("42", DownloadQueueBook.ERROR, 0, 0)
        assertEquals(snapshot, book.items().single().retry)
        val original = book.items().single()
        assertNull(snapshot.validated("42", "image", 1))
        assertEquals(original, book.items().single())
        assertTrue(book.add("other", "Other", ""))
        assertFalse(book.add("other", "Duplicate", ""))
        assertEquals(2, book.items().size)
        book.interruptPending()
        assertEquals(DownloadQueueBook.INTERRUPTED, book.items().last().state)
        assertEquals(snapshot, book.items().first().retry)
        assertTrue(book.add("42", "New", "", 1, "manga", snapshot))
        assertEquals(DownloadQueueBook.QUEUED, book.items().last().state)
        assertEquals(snapshot, book.items().last().retry)
    }

    @Test fun rejectedServiceStartRestoresRecoverableRowWithoutChangingOtherJobs() {
        val book = DownloadQueueBook()
        val snapshot = RetryDownload("42", "image", "content://tree", listOf(1), "", "")
        assertTrue(book.add("42", "Original", "", 1, "manga", snapshot))
        book.state("42", DownloadQueueBook.ERROR, 0, 0)
        val old = book.items().single()
        assertTrue(book.add("other", "Other", ""))
        assertTrue(book.add("42", "Retry", "", 1, "manga", snapshot))
        assertTrue(book.rollbackFailedStart("42", old, 0))
        assertEquals(listOf("42", "other"), book.items().map { it.id })
        assertEquals(DownloadQueueBook.ERROR, book.items().first().state)
        assertEquals(snapshot, book.items().first().retry)
        assertEquals(DownloadQueueBook.QUEUED, book.items().last().state)
        assertEquals(1, book.count())
    }

    @Test fun legacyRowCannotRetryAndQueueLimitStillHolds() {
        val book = DownloadQueueBook()
        book.add("42", "Old", "")
        book.state("42", DownloadQueueBook.ERROR, 0, 0)
        assertNull(book.items()[0].retry)
        repeat(5) { book.add("$it", "Work", "") }
        val before = book.items().map { it.id to it.state }
        assertFalse(book.add("42", "New", ""))
        assertEquals(before, book.items().map { it.id to it.state })
    }
}
