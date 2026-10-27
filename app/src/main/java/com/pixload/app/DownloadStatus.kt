package com.pixload.app

import android.content.Context

internal class DownloadStatus private constructor(
    @JvmField val title: String?,
    @JvmField val state: String?,
    @JvmField val progress: Int,
    @JvmField val max: Int,
    @JvmField val documentUri: String?,
    @JvmField val mimeType: String?,
    @JvmField val folder: Boolean,
    @JvmField val workId: String?
) {
    companion object {
        private const val PREFS = "download_status"

        @JvmStatic fun read(context: Context): DownloadStatus? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains("title") || prefs.getBoolean("hidden", false)) return null
            return DownloadStatus(AppLanguage.stage(context, prefs.getString("title", "")),
                prefs.getString("state", "active"), prefs.getInt("progress", 0), prefs.getInt("max", 0),
                prefs.getString("document_uri", ""), prefs.getString("mime_type", ""),
                prefs.getBoolean("folder", false), prefs.getString("work_id", ""))
        }

        // A hidden progress card does not mean the download has stopped.
        @JvmStatic fun isActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.contains("title") && prefs.getString("state", "") == "active"
        }

        @JvmStatic fun write(context: Context, title: String?, state: String?, progress: Int, max: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("state", state)
                .putInt("progress", progress).putInt("max", max).putBoolean("hidden", false)
                .remove("document_uri").remove("mime_type").remove("folder").remove("work_id").apply()
        }

        @JvmStatic fun completed(context: Context, title: String?, uri: String?, mimeType: String?, folder: Boolean) {
            completed(context, title, uri, mimeType, folder, "")
        }

        @JvmStatic fun completed(context: Context, title: String?, uri: String?, mimeType: String?, folder: Boolean, workId: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("state", "complete")
                .putInt("progress", 100).putInt("max", 100).putBoolean("hidden", false)
                .putString("document_uri", uri).putString("mime_type", mimeType)
                .putBoolean("folder", folder).putString("work_id", workId).apply()
        }

        @JvmStatic fun dismiss(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hidden", true).apply()
        }

        @JvmStatic fun forget(context: Context, uri: String?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (uri!!.equals(prefs.getString("document_uri", ""))) {
                if (!prefs.edit().putBoolean("hidden", true).remove("document_uri")
                        .remove("mime_type").remove("folder").commit())
                    throw IllegalStateException("No se pudo actualizar el estado de la descarga.")
            }
        }
    }
}
