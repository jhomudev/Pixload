package com.pixload.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DownloadButtonStatusTest {
    @Test public void hiddenProgressStillBlocksNewDownloadUntilFinished() {
        // The instrumentation APK has its own preferences, separate from user data.
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        context.getSharedPreferences("download_status", Context.MODE_PRIVATE).edit().clear().commit();
        try {
            assertFalse(DownloadStatus.isActive(context));
            DownloadStatus.write(context, "Preparando descarga…", "active", 0, 0);
            assertTrue(DownloadStatus.isActive(context));
            DownloadStatus.dismiss(context);
            assertTrue(DownloadStatus.isActive(context));
            DownloadStatus.write(context, "Error", "error", 0, 0);
            assertFalse(DownloadStatus.isActive(context));
            DownloadStatus.write(context, "Preparando descarga…", "active", 0, 0);
            DownloadStatus.completed(context, "Listo", "content://test/item", "image/png", false);
            assertFalse(DownloadStatus.isActive(context));
        } finally {
            context.getSharedPreferences("download_status", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    @Test public void absentFieldsUseLegacyDefaultsAndNullForgetDoesNotEdit() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("download_status", Context.MODE_PRIVATE);
        prefs.edit().clear().putString("title", "Obra").commit();
        try {
            DownloadStatus status = DownloadStatus.read(context);
            assertEquals("active", status.state);
            assertEquals(0, status.progress);
            assertEquals(0, status.max);
            assertEquals("", status.documentUri);
            assertEquals("", status.mimeType);
            assertFalse(status.folder);
            assertEquals("", status.workId);
            java.util.Map<String, ?> before = prefs.getAll();
            try {
                DownloadStatus.forget(context, null);
                org.junit.Assert.fail("Expected NullPointerException");
            } catch (NullPointerException expected) {
                assertEquals(before, prefs.getAll());
            }
        } finally {
            prefs.edit().clear().commit();
        }
    }

    @Test public void readStagesFieldsAndHiddenActiveStatusWithoutLosingStoredMetadata() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("download_status", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            assertNull(DownloadStatus.read(context));
            DownloadStatus.completed(context, "Descarga completada: Obra", "content://test/old", "image/png", true, "work-1");
            DownloadStatus current = DownloadStatus.read(context);
            assertEquals(AppLanguage.stage(context, "Descarga completada: Obra"), current.title);
            assertEquals("complete", current.state);
            assertEquals(100, current.progress);
            assertEquals(100, current.max);
            assertEquals("content://test/old", current.documentUri);
            assertEquals("image/png", current.mimeType);
            assertTrue(current.folder);
            assertEquals("work-1", current.workId);
            DownloadStatus.dismiss(context);
            assertNull(DownloadStatus.read(context));
            assertTrue(prefs.getBoolean("hidden", false));
            assertEquals("content://test/old", prefs.getString("document_uri", ""));
            DownloadStatus.write(context, "Archivo 1", "active", 2, 7);
            assertTrue(DownloadStatus.isActive(context));
            current = DownloadStatus.read(context);
            assertEquals(AppLanguage.stage(context, "Archivo 1"), current.title);
            assertEquals("active", current.state);
            assertEquals(2, current.progress);
            assertEquals(7, current.max);
            assertEquals("", current.documentUri);
            assertEquals("", current.mimeType);
            assertFalse(current.folder);
            assertEquals("", current.workId);
            assertFalse(prefs.contains("document_uri"));
            assertFalse(prefs.contains("mime_type"));
            assertFalse(prefs.contains("folder"));
            assertFalse(prefs.contains("work_id"));
            DownloadStatus.dismiss(context);
            assertNull(DownloadStatus.read(context));
            assertTrue(DownloadStatus.isActive(context));
        } finally {
            prefs.edit().clear().commit();
        }
    }

    @Test public void forgetOnlyMatchingUriHidesAndRemovesSavedMetadataButPreservesCounters() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SharedPreferences prefs = context.getSharedPreferences("download_status", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            DownloadStatus.completed(context, "Listo", "content://test/item", "image/png", true, "work-2");
            java.util.Map<String, ?> beforeMismatch = prefs.getAll();
            DownloadStatus.forget(context, "content://test/other");
            assertEquals(beforeMismatch, prefs.getAll());
            assertFalse(prefs.getBoolean("hidden", false));
            assertEquals("content://test/item", prefs.getString("document_uri", ""));
            assertEquals("image/png", prefs.getString("mime_type", ""));
            assertTrue(prefs.getBoolean("folder", false));
            assertEquals("work-2", prefs.getString("work_id", ""));
            assertEquals("complete", prefs.getString("state", ""));
            assertEquals(100, prefs.getInt("progress", 0));
            assertEquals(100, prefs.getInt("max", 0));
            DownloadStatus.forget(context, "content://test/item");
            assertNull(DownloadStatus.read(context));
            assertTrue(prefs.getBoolean("hidden", false));
            assertFalse(prefs.contains("document_uri"));
            assertFalse(prefs.contains("mime_type"));
            assertFalse(prefs.contains("folder"));
            assertEquals("work-2", prefs.getString("work_id", ""));
            assertEquals("Listo", prefs.getString("title", ""));
            assertEquals("complete", prefs.getString("state", ""));
            assertEquals(100, prefs.getInt("progress", 0));
            assertEquals(100, prefs.getInt("max", 0));
            DownloadStatus.completed(context, "Otra", "content://test/new", "image/gif", false);
            assertEquals("", DownloadStatus.read(context).workId);
        } finally {
            prefs.edit().clear().commit();
        }
    }
}
