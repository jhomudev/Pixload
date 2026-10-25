package com.pixload.app;

import android.content.Context;
import android.content.SharedPreferences;

final class DownloadStatus {
    private static final String PREFS = "download_status";
    final String title;
    final String state;
    final int progress;
    final int max;
    final String documentUri;
    final String mimeType;
    final boolean folder;
    final String workId;

    private DownloadStatus(String title, String state, int progress, int max,
                           String documentUri, String mimeType, boolean folder, String workId) {
        this.title = title;
        this.state = state;
        this.progress = progress;
        this.max = max;
        this.documentUri = documentUri;
        this.mimeType = mimeType;
        this.folder = folder;
        this.workId = workId;
    }

    static DownloadStatus read(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains("title") || prefs.getBoolean("hidden", false)) return null;
        return new DownloadStatus(AppLanguage.stage(context, prefs.getString("title", "")), prefs.getString("state", "active"),
                prefs.getInt("progress", 0), prefs.getInt("max", 0),
                prefs.getString("document_uri", ""), prefs.getString("mime_type", ""),
                prefs.getBoolean("folder", false), prefs.getString("work_id", ""));
    }

    // A hidden progress card does not mean the download has stopped.
    static boolean isActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.contains("title") && "active".equals(prefs.getString("state", ""));
    }

    static void write(Context context, String title, String state, int progress, int max) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("state", state)
                .putInt("progress", progress).putInt("max", max).putBoolean("hidden", false)
                .remove("document_uri").remove("mime_type").remove("folder").remove("work_id").apply();
    }

    static void completed(Context context, String title, String uri, String mimeType, boolean folder) {
        completed(context, title, uri, mimeType, folder, "");
    }

    static void completed(Context context, String title, String uri, String mimeType, boolean folder, String workId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("state", "complete")
                .putInt("progress", 100).putInt("max", 100).putBoolean("hidden", false)
                .putString("document_uri", uri).putString("mime_type", mimeType)
                .putBoolean("folder", folder).putString("work_id", workId).apply();
    }

    static void dismiss(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hidden", true).apply();
    }

    static void forget(Context context, String uri) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (uri.equals(prefs.getString("document_uri", ""))) {
            if (!prefs.edit().putBoolean("hidden", true).remove("document_uri")
                    .remove("mime_type").remove("folder").commit())
                throw new IllegalStateException("No se pudo actualizar el estado de la descarga.");
        }
    }
}
