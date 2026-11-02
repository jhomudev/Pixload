package com.pixload.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

internal object GalleryDocuments {
    @JvmStatic
    fun isAvailable(resolver: ContentResolver, entry: GalleryStore.Entry): Boolean {
        return try {
            // Query the document itself: an empty folder still exists.
            resolver.query(Uri.parse(entry.uri), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null).use { cursor -> cursor != null && cursor.moveToFirst() }
        } catch (_: java.io.FileNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            // Android's SAF tree validation can reject deleted descendants without a typed cause.
            false
        } catch (_: Exception) {
            // A permission or provider failure is not proof of deletion.
            true
        }
    }

    internal class Item(
        @JvmField val uri: Uri,
        @JvmField val name: String?,
        @JvmField val mime: String?
    )

    /**
     * Availability for a whole history: one child listing per saved tree instead of one SAF query
     * per entry. Every entry the listing does not cover keeps the exact [isAvailable] semantics, so
     * a deleted entry is hidden and a permission or provider failure never hides anything.
     */
    @JvmStatic
    fun available(resolver: ContentResolver, entries: List<GalleryStore.Entry>): Map<String, Boolean> {
        val available = HashMap<String, Boolean>(entries.size)
        val fallback = ArrayList<GalleryStore.Entry>()
        val trees = LinkedHashMap<String, MutableList<GalleryStore.Entry>>()
        for (entry in entries) {
            if (entry.treeUri.isEmpty() || documentId(entry.uri) == null) fallback.add(entry)
            else trees.getOrPut(entry.treeUri) { ArrayList() }.add(entry)
        }
        for ((treeUri, group) in trees) {
            val listed = children(resolver, treeUri)
            if (listed == null) {
                fallback.addAll(group)
                continue
            }
            for (entry in group) {
                val id = documentId(entry.uri)
                if (id != null && id in listed) available[entry.uri] = true else fallback.add(entry)
            }
        }
        for (entry in fallback) available[entry.uri] = isAvailable(resolver, entry)
        return available
    }

    /** Child document ids of a tree root, or null when that tree cannot be listed reliably. */
    private fun children(resolver: ContentResolver, treeUri: String): Set<String>? {
        return try {
            val tree = Uri.parse(treeUri)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(tree))
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                .use { cursor ->
                    if (cursor == null) null else HashSet<String>().apply {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
        } catch (_: Exception) {
            // Any listing failure falls back to the per-entry semantics instead of hiding entries.
            null
        }
    }

    private fun documentId(uri: String): String? = try {
        DocumentsContract.getDocumentId(Uri.parse(uri))
    } catch (_: IllegalArgumentException) {
        null
    }

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
