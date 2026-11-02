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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        RuntimeException denied;
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

    private static GalleryStore.Entry treeEntry(String uri, String treeUri) {
        return new GalleryStore.Entry(uri, "Title", "image/png", "", false, 1, 0, "", treeUri);
    }

    private static final class TreeProvider extends ContentProvider {
        final Map<String, Set<String>> listings = new HashMap<>();
        final Set<String> documents = new HashSet<>();
        final List<Uri> queries = new ArrayList<>();
        RuntimeException listingDenied;

        @Override public boolean onCreate() { return true; }

        @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sortOrder) {
            queries.add(uri);
            MatrixCursor cursor = new MatrixCursor(columns);
            String path = uri.getPath();
            if (path != null && path.endsWith("/children")) {
                if (listingDenied != null) throw listingDenied;
                Set<String> ids = listings.get(DocumentsContract.getDocumentId(uri));
                if (ids != null) for (String id : ids) cursor.addRow(new Object[]{id});
                return cursor;
            }
            String id = DocumentsContract.getDocumentId(uri);
            if (documents.contains(id)) cursor.addRow(new Object[]{id});
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

    @Test public void availabilityTracksExternalDeletionForFilesAndFolders() {
        for (boolean folder : new boolean[]{false, true}) {
            Provider provider = new Provider();
            ContentResolver resolver = ContentResolver.wrap(provider);
            GalleryStore.Entry saved = entry(TREE, "image/png", folder);
            TrackingCursor present = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
            present.addRow(new Object[]{"root/folder"});
            provider.cursor = present;
            assertTrue(GalleryDocuments.isAvailable(resolver, saved));
            assertEquals(Uri.parse(TREE), provider.queried);
            assertArrayEquals(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, provider.projection);
            assertTrue(present.closed);

            TrackingCursor missing = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
            provider.cursor = missing;
            assertTrue(!GalleryDocuments.isAvailable(resolver, saved));
            assertTrue(missing.closed);
            provider.cursor = null;
            assertTrue(!GalleryDocuments.isAvailable(resolver, saved));

            TrackingCursor restored = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
            restored.addRow(new Object[]{"root/folder"});
            provider.cursor = restored;
            assertTrue(GalleryDocuments.isAvailable(resolver, saved));
            assertTrue(restored.closed);
        }
    }

    @Test public void availabilityDoesNotHideEntriesOnPermissionOrProviderErrors() {
        Provider provider = new Provider();
        ContentResolver resolver = ContentResolver.wrap(provider);
        for (boolean folder : new boolean[]{false, true}) {
            provider.denied = new SecurityException("denied");
            assertTrue(GalleryDocuments.isAvailable(resolver, entry(TREE, "", folder)));
            provider.denied = new IllegalStateException("provider unavailable");
            assertTrue(GalleryDocuments.isAvailable(resolver, entry(TREE, "", folder)));
        }
    }

    @Test public void deletedTreeDescendantsRejectedByAndroidAreUnavailable() {
        Provider provider = new Provider();
        ContentResolver resolver = ContentResolver.wrap(provider);
        // ExternalStorageProvider tree validation discards the original missing-file cause.
        provider.denied = new IllegalArgumentException("Failed to determine if document is child of tree");
        for (boolean folder : new boolean[]{false, true}) {
            assertTrue(!GalleryDocuments.isAvailable(resolver, entry(TREE, "", folder)));
        }
        provider.denied = null;
        TrackingCursor restored = new TrackingCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
        restored.addRow(new Object[]{"root/folder"});
        provider.cursor = restored;
        assertTrue(GalleryDocuments.isAvailable(resolver, entry(TREE, "", false)));
        assertTrue(restored.closed);
    }

    @Test public void availabilityListsEachTreeOnceAndFallsBackOnlyForMissingEntries() {
        TreeProvider provider = new TreeProvider();
        Uri tree = Uri.parse(TREE);
        Uri other = Uri.parse("content://example.test/tree/other/document/other%2Ffolder");
        String first = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/a.png").toString();
        String sibling = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/b.png").toString();
        String deleted = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/deleted.png").toString();
        String elsewhere = DocumentsContract.buildDocumentUriUsingTree(other, "other/folder/c.png").toString();
        provider.listings.put("root/folder", new HashSet<>(Arrays.asList("root/folder/a.png", "root/folder/b.png")));
        provider.listings.put("other/folder", new HashSet<>(Arrays.asList("other/folder/c.png")));
        provider.documents.add("root/folder/a.png");
        provider.documents.add("root/folder/b.png");
        provider.documents.add("other/folder/c.png");

        Map<String, Boolean> available = GalleryDocuments.available(ContentResolver.wrap(provider), Arrays.asList(
                treeEntry(first, TREE), treeEntry(sibling, TREE), treeEntry(deleted, TREE), treeEntry(elsewhere, other.toString())));

        assertEquals(Boolean.TRUE, available.get(first));
        assertEquals(Boolean.TRUE, available.get(sibling));
        assertEquals(Boolean.FALSE, available.get(deleted));
        assertEquals(Boolean.TRUE, available.get(elsewhere));
        // Two trees, one listing each, plus the per-entry fallback for the deleted document.
        assertEquals(3, provider.queries.size());
        assertTrue(provider.queries.contains(DocumentsContract.buildChildDocumentsUriUsingTree(tree, "root/folder")));
        assertTrue(provider.queries.contains(DocumentsContract.buildChildDocumentsUriUsingTree(other, "other/folder")));
        assertTrue(provider.queries.contains(Uri.parse(deleted)));
    }

    @Test public void availabilityKeepsEntryWhenListingMissesButDocumentResolves() {
        TreeProvider provider = new TreeProvider();
        Uri tree = Uri.parse(TREE);
        String nested = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/nested/deep.png").toString();
        provider.documents.add("root/folder/nested/deep.png");

        Map<String, Boolean> available = GalleryDocuments.available(ContentResolver.wrap(provider),
                Arrays.asList(treeEntry(nested, TREE)));

        assertEquals(Boolean.TRUE, available.get(nested));
        assertEquals(2, provider.queries.size());
        assertEquals(Uri.parse(nested), provider.queries.get(1));
    }

    @Test public void availabilityFallsBackPerEntryWhenListingFails() {
        TreeProvider provider = new TreeProvider();
        Uri tree = Uri.parse(TREE);
        String present = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/a.png").toString();
        String deleted = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/deleted.png").toString();
        provider.documents.add("root/folder/a.png");
        provider.listingDenied = new SecurityException("denied");

        Map<String, Boolean> available = GalleryDocuments.available(ContentResolver.wrap(provider),
                Arrays.asList(treeEntry(present, TREE), treeEntry(deleted, TREE)));

        assertEquals(Boolean.TRUE, available.get(present));
        assertEquals(Boolean.FALSE, available.get(deleted));
        assertEquals(3, provider.queries.size());
    }

    @Test public void availabilityQueriesEntriesWithoutTreeUriIndividually() {
        TreeProvider provider = new TreeProvider();
        Uri tree = Uri.parse(TREE);
        String present = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/a.png").toString();
        String deleted = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/deleted.png").toString();
        provider.documents.add("root/folder/a.png");
        provider.listings.put("root/folder", new HashSet<>(Arrays.asList("root/folder/a.png")));

        Map<String, Boolean> available = GalleryDocuments.available(ContentResolver.wrap(provider),
                Arrays.asList(treeEntry(present, ""), treeEntry(deleted, "")));

        assertEquals(Boolean.TRUE, available.get(present));
        assertEquals(Boolean.FALSE, available.get(deleted));
        assertEquals(2, provider.queries.size());
        assertEquals(Uri.parse(present), provider.queries.get(0));
        assertEquals(Uri.parse(deleted), provider.queries.get(1));
    }

    @Test public void availabilityKeepsTheBatchWhenOneStoredUriIsMalformed() {
        TreeProvider provider = new TreeProvider();
        Uri tree = Uri.parse(TREE);
        String present = DocumentsContract.buildDocumentUriUsingTree(tree, "root/folder/a.png").toString();
        provider.listings.put("root/folder", new HashSet<>(Arrays.asList("root/folder/a.png")));
        provider.documents.add("root/folder/a.png");

        Map<String, Boolean> available = GalleryDocuments.available(ContentResolver.wrap(provider),
                Arrays.asList(treeEntry(present, TREE), treeEntry("not-a-uri", TREE)));

        assertEquals(Boolean.TRUE, available.get(present));
        assertEquals(Boolean.FALSE, available.get("not-a-uri"));
        assertEquals(2, provider.queries.size());
    }
}
