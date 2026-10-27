package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Pins the queue limit, FIFO order and slot release rules. */
public class DownloadQueueBookTest {
    private static DownloadQueueBook bookOf(DownloadQueueBook.Item... items) {
        DownloadQueueBook book = new DownloadQueueBook();
        List<DownloadQueueBook.Item> restored = new ArrayList<>();
        for (DownloadQueueBook.Item item : items) restored.add(item);
        book.load(restored);
        return book;
    }

    private static DownloadQueueBook.Item item(String id, String state) {
        return new DownloadQueueBook.Item(id, "Obra " + id, "", state, 0, 0);
    }

    @Test public void acceptsFivePendingAndRejectsTheSixth() {
        DownloadQueueBook book = new DownloadQueueBook();
        for (int i = 1; i <= DownloadQueueBook.LIMIT; i++) assertTrue(book.add("job-" + i, "Obra " + i, ""));
        assertTrue(book.isFull());
        assertEquals(5, book.count());
        assertFalse(book.add("job-6", "Obra 6", ""));
    }

    @Test public void keepsFifoOrder() {
        DownloadQueueBook book = new DownloadQueueBook();
        book.add("first", "Primera", "");
        book.add("second", "Segunda", "");
        assertEquals("first", book.pending().get(0).id);
        assertEquals("second", book.pending().get(1).id);
    }

    @Test public void finishingAJobFreesItsSlot() {
        DownloadQueueBook book = new DownloadQueueBook();
        for (int i = 1; i <= DownloadQueueBook.LIMIT; i++) book.add("job-" + i, "Obra " + i, "");
        assertFalse(book.add("job-6", "Obra 6", ""));
        assertTrue(book.remove("job-1"));
        assertTrue(book.add("job-6", "Obra 6", ""));
        assertEquals(5, book.count());
        assertEquals(5, book.items().size());
    }

    @Test public void interruptedJobsDoNotKeepASlot() {
        DownloadQueueBook book = bookOf(item("dead", DownloadQueueBook.ACTIVE), item("stale", DownloadQueueBook.QUEUED));
        assertTrue(book.interruptPending());
        assertEquals(0, book.count());
        assertFalse(book.isFull());
        assertTrue(book.add("fresh", "Nueva", ""));
        assertEquals(3, book.items().size());
        assertEquals(1, book.count());
    }

    @Test public void ignoresAnIdThatIsAlreadyPending() {
        DownloadQueueBook book = new DownloadQueueBook();
        assertTrue(book.add("same", "Obra", ""));
        assertFalse(book.add("same", "Obra", ""));
        assertEquals(1, book.count());
    }

    @Test public void aggregatesProgressOfPendingJobsOnly() {
        DownloadQueueBook book = bookOf(
                item("a", DownloadQueueBook.ACTIVE), item("b", DownloadQueueBook.QUEUED),
                item("dead", DownloadQueueBook.INTERRUPTED));
        book.progress("a", 50, 100);
        book.progress("b", 100, 100);
        book.progress("dead", 100, 100);
        assertEquals(75, book.percent());
    }

    @Test public void tracksProgressAndStateTransitions() {
        DownloadQueueBook book = new DownloadQueueBook();
        book.add("job", "Obra", "");
        assertEquals(DownloadQueueBook.QUEUED, book.pending().get(0).state);
        assertTrue(book.state("job", DownloadQueueBook.ACTIVE, 0, 0));
        assertEquals(DownloadQueueBook.ACTIVE, book.pending().get(0).state);
        assertTrue(book.progress("job", 3, 7));
        assertEquals(3, book.pending().get(0).progress);
        assertEquals(7, book.pending().get(0).max);
        assertFalse(book.progress("missing", 1, 1));
    }
}
