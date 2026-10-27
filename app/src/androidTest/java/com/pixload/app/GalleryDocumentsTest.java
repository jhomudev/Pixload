package com.pixload.app;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap(ContentProvider) is a test-only API 29 fixture.
public class GalleryDocumentsTest {
    private static final String TREE = "content://example.test/tree/root/document/root%2Ffolder";

    private static GalleryStore.Entry entry(String uri, String mime, boolean folder) {
        return new GalleryStore.Entry(uri, "Title", mime, "", folder, 1, 0);
    }

    private static final class TrackingCursor extends MatrixCursor {
        boolean closed;
        TrackingCursor(String[] columns) { super(columns); }
        @Override public void close() { closed = true; super.close(); }
    }

    private static final class Provider extends ContentProvider {
        Cursor cursor;
        SecurityException denied;
        Uri queried;
        String[] projection;

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sortOrder) {
            queried = uri;
            projection = columns;
            if (denied != null) throw denied;
            return cursor;
        }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    }

    private static void error(ContentResolver resolver, GalleryStore.Entry entry, String message, Throwable cause) {
        try {
            GalleryDocuments.list(resolver, entry);
            fail("Expected IOException");
        } catch (IOException ex) {
            assertEquals(message, ex.getMessage());
            assertSame(cause, ex.getCause());
        }
    }

    @Test public void singleDocumentQueriesNameAndClosesCursor() throws IOException {
        Provider provider = new Provider();
        TrackingCursor cursor = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});
        cursor.addRow(new Object[]{"Foto.png"});
        provider.cursor = cursor;
        List<GalleryDocuments.Item> items = GalleryDocuments.list(ContentResolver.wrap(provider), entry(TREE, "image/png", false));
        assertEquals(Uri.parse(TREE), provider.queried);
        assertArrayEquals(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, provider.projection);
        assertEquals(1, items.size());
        assertEquals(Uri.parse(TREE), items.get(0).uri);
        assertEquals("Foto.png", items.get(0).name);
        assertEquals("image/png", items.get(0).mime);
        assertTrue(cursor.closed);
    }

    @Test public void folderSkipsDirectoriesBuildsDocumentUrisAndSortsNames() throws IOException {
        Provider provider = new Provider();
        TrackingCursor cursor = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE});
        cursor.addRow(new Object[]{"root/folder/z", "z.png", "image/png"});
        cursor.addRow(new Object[]{"root/folder/sub", "Folder", DocumentsContract.Document.MIME_TYPE_DIR});
        cursor.addRow(new Object[]{"root/folder/A", "A.jpg", "image/jpeg"});
        provider.cursor = cursor;
        Uri tree = Uri.parse(TREE);
        List<GalleryDocuments.Item> items = GalleryDocuments.list(ContentResolver.wrap(provider), entry(TREE, "", true));
        assertEquals(DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(tree)), provider.queried);
        assertArrayEquals(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, provider.projection);
        assertEquals(2, items.size());
        assertEquals(Arrays.asList("A.jpg", "z.png"), Arrays.asList(items.get(0).name, items.get(1).name));
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/A"), items.get(0).uri);
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/z"), items.get(1).uri);
        assertEquals("image/jpeg", items.get(0).mime);
        assertEquals("image/png", items.get(1).mime);
        assertTrue(cursor.closed);
    }

    @Test public void missingAndEmptyCursorsKeepFileAndFolderBehavior() throws IOException {
        Provider provider = new Provider();
        ContentResolver resolver = ContentResolver.wrap(provider);
        error(resolver, entry(TREE, "image/png", false), "El archivo ya no está disponible.", null);
        error(resolver, entry(TREE, "", true), "La carpeta ya no está disponible.", null);
        TrackingCursor emptyFile = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});
        provider.cursor = emptyFile;
        error(resolver, entry(TREE, "image/png", false), "El archivo ya no está disponible.", null);
        assertTrue(emptyFile.closed);
        TrackingCursor emptyFolder = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE});
        provider.cursor = emptyFolder;
        assertTrue(GalleryDocuments.list(resolver, entry(TREE, "", true)).isEmpty());
        assertTrue(emptyFolder.closed);
    }

    @Test public void permissionErrorsKeepExactMessageAndCause() {
        Provider provider = new Provider();
        SecurityException denied = new SecurityException("denied");
        provider.denied = denied;
        ContentResolver resolver = ContentResolver.wrap(provider);
        error(resolver, entry(TREE, "image/png", false), "Se ha perdido el acceso a este archivo.", denied);
        error(resolver, entry(TREE, "", true), "Se ha perdido el acceso a esta carpeta.", denied);
    }
}
