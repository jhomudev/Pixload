package com.pixload.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal object GalleryDocuments {
    internal class Item(
        @JvmField val uri: Uri,
        @JvmField val name: String?,
        @JvmField val mime: String?
    )

    @JvmStatic
    @Throws(IOException::class)
    fun list(resolver: ContentResolver, entry: GalleryStore.Entry): List<Item> {
        val items = ArrayList<Item>()
        val uri = Uri.parse(entry.uri)
        if (!entry.folder) {
            try {
                resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) throw IOException("El archivo ya no está disponible.")
                    items.add(Item(uri, cursor.getString(0), entry.mime))
                }
            } catch (error: SecurityException) {
                throw IOException("Se ha perdido el acceso a este archivo.", error)
            }
        } else {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri))
            try {
                resolver.query(children, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null).use { cursor ->
                    if (cursor == null) throw IOException("La carpeta ya no está disponible.")
                    while (cursor.moveToNext()) {
                        if (DocumentsContract.Document.MIME_TYPE_DIR == cursor.getString(2)) continue
                        items.add(Item(DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)),
                            cursor.getString(1), cursor.getString(2)))
                    }
                }
            } catch (error: SecurityException) {
                throw IOException("Se ha perdido el acceso a esta carpeta.", error)
            }
            items.sortWith { a, b -> java.lang.String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
        }
        return items
    }
}
