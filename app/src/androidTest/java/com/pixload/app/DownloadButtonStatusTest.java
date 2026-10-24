package com.pixload.app;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
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
}
