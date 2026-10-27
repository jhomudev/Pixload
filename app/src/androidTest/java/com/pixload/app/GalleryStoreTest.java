package com.pixload.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryStoreTest {
    @Test public void persistedEntriesKeepTheShippedJsonKeys() throws Exception {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "gallery_persisted_keys_test_" + System.nanoTime();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + "_" + name, mode);
            }
        };
        try {
            GalleryStore.add(isolated, new GalleryStore.Entry("content://example.test/keys", "Obra", "image/png",
                    "content://example.test/keys", true, 3, 1700000000000L, "999", "content://example.test/tree"));
            String raw = isolated.getSharedPreferences("gallery", Context.MODE_PRIVATE).getString("entries", "[]");
            JSONArray saved = new JSONArray(raw);
            assertEquals(1, saved.length());
            JSONObject entry = saved.getJSONObject(0);
            JSONArray names = entry.names();
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < names.length(); i++) keys.add(names.getString(i));
            assertEquals(new HashSet<>(Arrays.asList("uri", "title", "mime", "cover", "folder", "count", "date",
                    "workId", "treeUri")), keys);
            assertEquals("content://example.test/keys", entry.getString("uri"));
            assertEquals("Obra", entry.getString("title"));
            assertEquals("999", entry.getString("workId"));
        } finally {
            base.deleteSharedPreferences(prefix + "_gallery");
        }
    }

    @Test public void importLastStripsTheCompletedPrefixAndAppliesFolderShape() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "gallery_import_title_test_" + System.nanoTime();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + "_" + name, mode);
            }
        };
        try {
            DownloadStatus.completed(isolated, "Descarga completada: Mi obra", "content://example.test/imported",
                    "image/png", false);
            GalleryStore.importLast(isolated);
            GalleryStore.Entry single = GalleryStore.find(isolated, "content://example.test/imported");
            assertNotNull(single);
            assertEquals("Mi obra", single.title);
            assertEquals("image/png", single.mime);
            assertEquals("content://example.test/imported", single.cover);
            assertEquals(1, single.count);
            assertEquals(0L, single.date);
            assertEquals("", single.workId);
            assertEquals("", single.treeUri);

            GalleryStore.importLast(isolated);
            assertEquals(1, GalleryStore.list(isolated).size());

            DownloadStatus.completed(isolated, "Descarga completada: Manga", "content://example.test/imported-folder",
                    "application/vnd.android.document/directory", true);
            GalleryStore.importLast(isolated);
            GalleryStore.Entry folder = GalleryStore.find(isolated, "content://example.test/imported-folder");
            assertNotNull(folder);
            assertEquals("Manga", folder.title);
            assertEquals("", folder.cover);
            assertEquals(-1, folder.count);
            assertTrue(folder.folder);
        } finally {
            base.deleteSharedPreferences(prefix + "_gallery");
            base.deleteSharedPreferences(prefix + "_download_status");
        }
    }

    @Test public void completedWorkMatchesOnlyItsChosenTreeAndPersistsThroughMetadataRefresh() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "gallery_duplicate_test_" + System.nanoTime();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + "_" + name, mode);
            }
        };
        try {
            String tree = "content://example.test/tree/chosen";
            GalleryStore.Entry entry = new GalleryStore.Entry("content://example.test/file/1", "Obra", "image/png",
                    "", false, 1, 123L, "123", tree);
            GalleryStore.add(isolated, entry);
            assertTrue(GalleryStore.hasCompleted(isolated, "123", tree));
            assertFalse(GalleryStore.hasCompleted(isolated, "123", "content://example.test/tree/other"));
            assertFalse(GalleryStore.hasCompleted(isolated, "456", tree));
            GalleryStore.updateMetadata(isolated, GalleryStore.find(isolated, entry.uri), 2, "cover");
            assertTrue(GalleryStore.hasCompleted(isolated, "123", tree));
            assertEquals("cover", GalleryStore.find(isolated, entry.uri).cover);
            GalleryStore.add(isolated, new GalleryStore.Entry(entry.uri, "other", "", "", false, 1, 0));
            assertEquals(1, GalleryStore.list(isolated).size());
            GalleryStore.remove(isolated, entry.uri);
            assertFalse(GalleryStore.hasCompleted(isolated, "123", tree));
        } finally {
            base.deleteSharedPreferences(prefix + "_gallery");
        }
    }

    @Test public void legacyEntriesWithoutIdentityNeverMatch() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "gallery_legacy_test_" + System.nanoTime();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + "_" + name, mode);
            }
        };
        try {
            String tree = "content://example.test/tree/chosen";
            isolated.getSharedPreferences("gallery", Context.MODE_PRIVATE).edit()
                    .putString("entries", "[{\"uri\":\"content://example.test/file/1\",\"title\":\"Obra\"}]").commit();
            assertFalse(GalleryStore.hasCompleted(isolated, "123", tree));
            assertFalse(GalleryStore.hasCompleted(isolated, "", tree));
        } finally {
            base.deleteSharedPreferences(prefix + "_gallery");
        }
    }

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
