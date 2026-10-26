package com.pixload.app;

import android.content.Intent;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GalleryShareTest {
    @Test public void emptyListKeepsExactError() {
        try {
            GalleryShare.intent(Collections.emptyList());
            org.junit.Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException error) {
            assertEquals("No hay archivos para compartir.", error.getMessage());
        }
    }

    @Test public void defaultLabelAndMissingMimeKeepShareContract() {
        Uri first = Uri.parse("content://example.test/1");
        Uri second = Uri.parse("content://example.test/2");
        Intent send = GalleryShare.intent(Arrays.asList(
                new GalleryDocuments.Item(first, "1", null),
                new GalleryDocuments.Item(second, "2", null)));
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.getAction());
        assertEquals("*/*", send.getType());
        assertEquals("Descarga de Pixload", send.getClipData().getDescription().getLabel().toString());
        assertEquals(Arrays.asList(first, second), send.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
        assertEquals(first, send.getClipData().getItemAt(0).getUri());
        assertEquals(second, send.getClipData().getItemAt(1).getUri());
        assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    @Test public void singleFileGrantsReadAccess() {
        Uri uri = Uri.parse("content://example.test/image/1");
        Intent send = GalleryShare.intent(Collections.singletonList(new GalleryDocuments.Item(uri, "001.png", "image/png")));
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("image/png", send.getType());
        assertEquals(uri, send.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotNull(send.getClipData());
        assertEquals(uri, send.getClipData().getItemAt(0).getUri());
        assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    @Test public void shareLabelFollowsChosenAppLanguageWithoutChangingUri() {
        Uri uri = Uri.parse("content://example.test/image/1");
        Intent send = GalleryShare.intent(Collections.singletonList(new GalleryDocuments.Item(uri, "001.png", "image/png")), "Pixload download");
        assertEquals("Pixload download", send.getClipData().getDescription().getLabel().toString());
        assertEquals(uri, send.getClipData().getItemAt(0).getUri());
    }

    @Test public void multipleFilesShareAllUrisAndUseCommonMime() {
        Uri first = Uri.parse("content://example.test/image/1");
        Uri second = Uri.parse("content://example.test/image/2");
        Intent send = GalleryShare.intent(Arrays.asList(
                new GalleryDocuments.Item(first, "001.png", "image/png"),
                new GalleryDocuments.Item(second, "002.jpg", "image/jpeg")));
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.getAction());
        assertEquals("*/*", send.getType());
        assertEquals(Arrays.asList(first, second), send.getParcelableArrayListExtra(Intent.EXTRA_STREAM));
        assertEquals(2, send.getClipData().getItemCount());
        assertEquals(second, send.getClipData().getItemAt(1).getUri());
        assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }
}
