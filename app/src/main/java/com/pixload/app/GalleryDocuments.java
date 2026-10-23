package com.pixload.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GalleryDocuments {
    static final class Item {
        final Uri uri;
        final String name;
        final String mime;

        Item(Uri uri, String name, String mime) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
        }
    }

    private GalleryDocuments() { }

    static List<Item> list(ContentResolver resolver, GalleryStore.Entry entry) throws IOException {
        List<Item> items = new ArrayList<>();
        Uri uri = Uri.parse(entry.uri);
        if (!entry.folder) {
            try (Cursor cursor = resolver.query(uri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) throw new IOException("El archivo ya no está disponible.");
                items.add(new Item(uri, cursor.getString(0), entry.mime));
            } catch (SecurityException error) { throw new IOException("Se ha perdido el acceso a este archivo.", error); }
        } else {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor == null) throw new IOException("La carpeta ya no está disponible.");
                while (cursor.moveToNext()) {
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) continue;
                    items.add(new Item(DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)),
                            cursor.getString(1), cursor.getString(2)));
                }
            } catch (SecurityException error) { throw new IOException("Se ha perdido el acceso a esta carpeta.", error); }
            items.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        return items;
    }
}
