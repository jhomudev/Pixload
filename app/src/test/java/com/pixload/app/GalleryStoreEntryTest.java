package com.pixload.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the Entry data wiring the store depends on: both constructors and the argument order of the
 * nine-field one. The persisted JSON format needs org.json, which is a throwing stub in Android JVM
 * tests, so it stays covered by the instrumented GalleryStoreTest instead.
 */
public class GalleryStoreEntryTest {
    @Test
    public void nineFieldConstructorKeepsArgumentOrder() {
        GalleryStore.Entry entry = new GalleryStore.Entry("content://tree/1", "Obra", "image/png",
                "content://cover/1", true, 7, 1700000000000L, "12345", "content://tree");
        assertEquals("content://tree/1", entry.uri);
        assertEquals("Obra", entry.title);
        assertEquals("image/png", entry.mime);
        assertEquals("content://cover/1", entry.cover);
        assertTrue(entry.folder);
        assertEquals(7, entry.count);
        assertEquals(1700000000000L, entry.date);
        assertEquals("12345", entry.workId);
        assertEquals("content://tree", entry.treeUri);
    }

    @Test
    public void shortConstructorLeavesCompletionKeysEmpty() {
        GalleryStore.Entry entry = new GalleryStore.Entry("content://a", "Obra", "text/plain", "content://a", false, 1, 5L);
        assertEquals("content://a", entry.uri);
        assertEquals("Obra", entry.title);
        assertEquals("text/plain", entry.mime);
        assertEquals("content://a", entry.cover);
        assertFalse(entry.folder);
        assertEquals(1, entry.count);
        assertEquals(5L, entry.date);
        assertEquals("", entry.workId);
        assertEquals("", entry.treeUri);
    }
}
