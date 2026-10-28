package com.pixload.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList

/**
 * Strips the completion prefix DownloadStatus writes into download_status. Kotlin's
 * String.replaceFirst(String, String) matches a literal, unlike java.lang.String.replaceFirst
 * which is regex based, so the prefix removal is literal on purpose.
 */
internal fun stripCompletedPrefix(title: String): String = title.removePrefix("Descarga completada: ")

/** The entry MIME is the saved work type; a folder's cover MIME is not its type. */
internal fun galleryFormat(folder: Boolean, mime: String): String = when {
    folder -> "Carpeta"
    mime == "video/mp4" -> "MP4"
    mime == "image/gif" -> "GIF"
    mime == "text/plain" -> "TXT"
    mime == "image/jpeg" -> "JPG"
    mime == "image/png" -> "PNG"
    mime == "image/webp" -> "WEBP"
    else -> "Archivo"
}

internal object GalleryStore {
    private const val PREFS = "gallery"
    private val LOCK = Any()

    class Entry(
        @JvmField val uri: String,
        @JvmField val title: String,
        @JvmField val mime: String,
        @JvmField val cover: String,
        @JvmField val folder: Boolean,
        @JvmField val count: Int,
        @JvmField val date: Long,
        @JvmField val workId: String,
        @JvmField val treeUri: String
    ) {
        constructor(uri: String, title: String, mime: String, cover: String, folder: Boolean, count: Int, date: Long) :
            this(uri, title, mime, cover, folder, count, date, "", "")

        @Throws(Exception::class)
        fun json(): JSONObject = JSONObject().put("uri", uri).put("title", title).put("mime", mime)
            .put("cover", cover).put("folder", folder).put("count", count).put("date", date)
            .put("workId", workId).put("treeUri", treeUri)

        companion object {
            @JvmStatic
            fun from(json: JSONObject): Entry = Entry(json.optString("uri"), json.optString("title", "Obra"),
                json.optString("mime"), json.optString("cover"), json.optBoolean("folder"),
                json.optInt("count", -1), json.optLong("date", 0),
                json.optString("workId"), json.optString("treeUri"))
        }
    }

    @JvmStatic
    fun list(context: Context): List<Entry> = synchronized(LOCK) { read(context) }

    @JvmStatic
    fun find(context: Context, uri: String?): Entry? {
        if (uri == null) return null
        for (entry in list(context)) if (entry.uri == uri) return entry
        return null
    }

    @JvmStatic
    fun hasCompleted(context: Context, workId: String?, treeUri: String?): Boolean {
        if (workId.isNullOrEmpty() || treeUri.isNullOrEmpty()) return false
        for (entry in list(context)) {
            if (workId == entry.workId && treeUri == entry.treeUri) return true
        }
        return false
    }

    @JvmStatic
    fun add(context: Context, entry: Entry) {
        synchronized(LOCK) {
            val entries = read(context)
            for (existing in entries) if (existing.uri == entry.uri) return
            entries.add(0, entry)
            write(context, entries)
        }
    }

    @JvmStatic
    fun remove(context: Context, uri: String) {
        synchronized(LOCK) {
            val entries = read(context)
            entries.removeAll { entry -> entry.uri == uri }
            write(context, entries)
        }
    }

    @JvmStatic
    fun updateMetadata(context: Context, entry: Entry, count: Int, cover: String): Entry {
        synchronized(LOCK) {
            val entries = read(context)
            val updated = Entry(entry.uri, entry.title, entry.mime, cover, entry.folder, count, entry.date,
                entry.workId, entry.treeUri)
            for (i in entries.indices) {
                if (entries[i].uri == entry.uri) {
                    entries[i] = updated
                    write(context, entries)
                    break
                }
            }
            return updated
        }
    }

    @JvmStatic
    fun importLast(context: Context) {
        val status: SharedPreferences = context.getSharedPreferences("download_status", Context.MODE_PRIVATE)
        if ("complete" != status.getString("state", "")) return
        val uri = status.getString("document_uri", "") ?: ""
        if (uri.isEmpty() || find(context, uri) != null) return
        val title = stripCompletedPrefix(status.getString("title", "Obra") ?: "Obra")
        val folder = status.getBoolean("folder", false)
        val mime = status.getString("mime_type", "") ?: ""
        add(context, Entry(uri, title, mime, if (folder) "" else uri, folder, if (folder) -1 else 1, 0))
    }

    private fun read(context: Context): MutableList<Entry> {
        val entries = ArrayList<Entry>()
        try {
            val saved = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("entries", "[]"))
            for (i in 0 until saved.length()) {
                val entry = Entry.from(saved.getJSONObject(i))
                if (Uri.EMPTY.toString() != entry.uri && entry.uri.isNotEmpty()) entries.add(entry)
            }
        } catch (error: Exception) {
            throw IllegalStateException("No se pudo leer el historial de descargas.", error)
        }
        return entries
    }

    private fun write(context: Context, entries: List<Entry>) {
        val output = JSONArray()
        try {
            for (entry in entries) output.put(entry.json())
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("entries", output.toString()).commit()) throw IllegalStateException("No se pudo guardar la galería.")
        } catch (error: Exception) {
            throw IllegalStateException("No se pudo guardar la galería.", error)
        }
    }
}
