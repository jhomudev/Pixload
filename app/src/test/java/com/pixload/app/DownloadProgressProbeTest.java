package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Independent verification probes for S9 (whole-work progress) and S7 (legacy API). */
public class DownloadProgressProbeTest {
    @Test public void wholeWorkProgressIsMonotonicAndEndsAtHundred() {
        for (int count = 1; count <= 5; count++) {
            int previous = -1;
            for (int page = 0; page < count; page++) {
                for (int fraction : new int[] {0, 1, 49, 50, 99, 100}) {
                    int overall = DownloadQueueBook.overall(page, count, fraction);
                    assertTrue("out of range", overall >= 0 && overall <= 100);
                    assertTrue("went backwards", overall >= previous);
                    previous = overall;
                }
            }
            assertEquals(100, DownloadQueueBook.overall(count - 1, count, 100));
        }
    }

    @Test public void wholeWorkProgressBoundaries() {
        assertEquals(16, DownloadQueueBook.overall(0, 3, 50));
        assertEquals(33, DownloadQueueBook.overall(0, 3, 100));
        assertEquals(66, DownloadQueueBook.overall(1, 3, 100));
        assertEquals(100, DownloadQueueBook.overall(2, 3, 100));
        assertEquals(100, DownloadQueueBook.overall(7, 3, 100));
        assertEquals(33, DownloadQueueBook.overall(0, 3, 101));
        assertEquals(0, DownloadQueueBook.overall(0, 3, -1));
        assertEquals(0, DownloadQueueBook.overall(0, 0, 100));
        assertEquals(0, DownloadQueueBook.overall(0, -4, 100));
    }

    @Test public void legacyRowWithoutPagesOrKindStillWorks() {
        DownloadQueueBook.Item legacy = new DownloadQueueBook.Item("old", "Obra", "", DownloadQueueBook.QUEUED, 0, 0);
        assertEquals(0, legacy.pages);
        assertEquals("", legacy.kind);
        DownloadQueueBook book = new DownloadQueueBook();
        List<DownloadQueueBook.Item> restored = new ArrayList<>();
        restored.add(legacy);
        book.load(restored);
        assertEquals(1, book.count());
        assertTrue(book.progress("old", DownloadQueueBook.overall(0, 0, 50), 100));
        assertEquals(0, book.percent());
    }

    @Test public void threeArgumentAddStillQueues() {
        DownloadQueueBook book = new DownloadQueueBook();
        assertTrue(book.add("id", "Obra", "thumb"));
        assertEquals(DownloadQueueBook.QUEUED, book.items().get(0).state);
        assertEquals(0, book.items().get(0).pages);
        assertEquals("", book.items().get(0).kind);
        assertFalse(book.add("id", "Obra", "thumb"));
    }

    @Test public void legacyEnqueueOverloadStillExists() throws Exception {
        Method method = DownloadQueue.class.getDeclaredMethod("enqueue", Context.class, String.class,
            String.class, String.class);
        assertEquals(DownloadQueue.class, method.getDeclaringClass());
    }
}
