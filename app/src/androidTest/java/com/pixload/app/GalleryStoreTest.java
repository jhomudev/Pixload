package com.pixload.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class GalleryStoreTest {
    @Test public void recordsDistinctDownloadsAndImportsLegacyOnce() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "gallery_test_" + System.nanoTime();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + "_" + name, mode);
            }
        };
        try {
            GalleryStore.Entry first = new GalleryStore.Entry("content://example.test/file/1", "Obra", "image/png",
                    "content://example.test/file/1", false, 1, 123L);
            GalleryStore.Entry second = new GalleryStore.Entry("content://example.test/folder/2", "Manga", "", "",
                    true, 3, 456L);
            GalleryStore.add(isolated, first);
            GalleryStore.add(isolated, second);
            GalleryStore.add(isolated, second);
            assertEquals(2, GalleryStore.list(isolated).size());
            assertEquals("Manga", GalleryStore.list(isolated).get(0).title);
            assertEquals(3, GalleryStore.find(isolated, second.uri).count);
            GalleryStore.remove(isolated, first.uri);
            assertNull(GalleryStore.find(isolated, first.uri));

            DownloadStatus.completed(isolated, "Descarga completada: Anterior", "content://example.test/old",
                    "image/png", false);
            GalleryStore.importLast(isolated);
            GalleryStore.importLast(isolated);
            assertEquals(2, GalleryStore.list(isolated).size());
            assertEquals(0L, GalleryStore.find(isolated, "content://example.test/old").date);
            GalleryStore.remove(isolated, "content://example.test/old");
            DownloadStatus.forget(isolated, "content://example.test/old");
            GalleryStore.importLast(isolated);
            assertNull(GalleryStore.find(isolated, "content://example.test/old"));
            DownloadStatus.completed(isolated, "Descarga completada: Actual", "content://example.test/new",
                    "image/png", false, "12345");
            assertEquals("12345", DownloadStatus.read(isolated).workId);
        } finally {
            base.deleteSharedPreferences(prefix + "_gallery");
            base.deleteSharedPreferences(prefix + "_download_status");
        }
    }
}
