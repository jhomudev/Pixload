package com.pixload.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class GalleryStore {
    private static final String PREFS = "gallery";
    private static final Object LOCK = new Object();

    static final class Entry {
        final String uri;
        final String title;
        final String mime;
        final String cover;
        final boolean folder;
        final int count;
        final long date;
        final String workId;
        final String treeUri;

        Entry(String uri, String title, String mime, String cover, boolean folder, int count, long date) {
            this(uri, title, mime, cover, folder, count, date, "", "");
        }

        Entry(String uri, String title, String mime, String cover, boolean folder, int count, long date,
              String workId, String treeUri) {
            this.uri = uri;
            this.title = title;
            this.mime = mime;
            this.cover = cover;
            this.folder = folder;
            this.count = count;
            this.date = date;
            this.workId = workId;
            this.treeUri = treeUri;
        }

        JSONObject json() throws Exception {
            return new JSONObject().put("uri", uri).put("title", title).put("mime", mime)
                    .put("cover", cover).put("folder", folder).put("count", count).put("date", date)
                    .put("workId", workId).put("treeUri", treeUri);
        }

        static Entry from(JSONObject json) {
            return new Entry(json.optString("uri"), json.optString("title", "Obra"),
                    json.optString("mime"), json.optString("cover"), json.optBoolean("folder"),
                    json.optInt("count", -1), json.optLong("date", 0),
                    json.optString("workId"), json.optString("treeUri"));
        }
    }

    private GalleryStore() { }

    static List<Entry> list(Context context) {
        synchronized (LOCK) { return read(context); }
    }

    static Entry find(Context context, String uri) {
        if (uri == null) return null;
        for (Entry entry : list(context)) if (entry.uri.equals(uri)) return entry;
        return null;
    }

    static boolean hasCompleted(Context context, String workId, String treeUri) {
        if (workId == null || workId.isEmpty() || treeUri == null || treeUri.isEmpty()) return false;
        for (Entry entry : list(context)) {
            if (workId.equals(entry.workId) && treeUri.equals(entry.treeUri)) return true;
        }
        return false;
    }

    static void add(Context context, Entry entry) {
        synchronized (LOCK) {
            List<Entry> entries = read(context);
            for (Entry existing : entries) if (existing.uri.equals(entry.uri)) return;
            entries.add(0, entry);
            write(context, entries);
        }
    }

    static void remove(Context context, String uri) {
        synchronized (LOCK) {
            List<Entry> entries = read(context);
            entries.removeIf(entry -> entry.uri.equals(uri));
            write(context, entries);
        }
    }

    static Entry updateMetadata(Context context, Entry entry, int count, String cover) {
        synchronized (LOCK) {
            List<Entry> entries = read(context);
            Entry updated = new Entry(entry.uri, entry.title, entry.mime, cover, entry.folder, count, entry.date,
                    entry.workId, entry.treeUri);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).uri.equals(entry.uri)) {
                    entries.set(i, updated);
                    write(context, entries);
                    break;
                }
            }
            return updated;
        }
    }

    static void importLast(Context context) {
        SharedPreferences status = context.getSharedPreferences("download_status", Context.MODE_PRIVATE);
        if (!"complete".equals(status.getString("state", ""))) return;
        String uri = status.getString("document_uri", "");
        if (uri.isEmpty() || find(context, uri) != null) return;
        String title = status.getString("title", "Obra").replaceFirst("^Descarga completada: ", "");
        boolean folder = status.getBoolean("folder", false);
        String mime = status.getString("mime_type", "");
        add(context, new Entry(uri, title, mime, folder ? "" : uri, folder, folder ? -1 : 1, 0));
    }

    private static List<Entry> read(Context context) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("entries", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                Entry entry = Entry.from(saved.getJSONObject(i));
                if (!Uri.EMPTY.toString().equals(entry.uri) && !entry.uri.isEmpty()) entries.add(entry);
            }
        } catch (Exception error) { throw new IllegalStateException("No se pudo leer el historial de descargas.", error); }
        return entries;
    }

    private static void write(Context context, List<Entry> entries) {
        JSONArray output = new JSONArray();
        try {
            for (Entry entry : entries) output.put(entry.json());
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("entries", output.toString()).commit()) throw new IllegalStateException("No se pudo guardar la galería.");
        } catch (Exception error) { throw new IllegalStateException("No se pudo guardar la galería.", error); }
    }
}
