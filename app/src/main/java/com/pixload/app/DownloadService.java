package com.pixload.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadService extends Service {
    private static final String CHANNEL = "pixload_downloads";
    private static final int NOTIFICATION = 100;
    private final ExecutorService queue = Executors.newSingleThreadExecutor();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean cancelled;
    private NotificationManager notifications;
    private long lastNotification;
    private volatile String currentWorkTitle = "Obra de Pixiv";

    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancel".equals(intent.getAction())) {
            cancelled = true;
            queue.shutdownNow();
            notifyState("Descarga cancelada", 0, 0, true, "cancelled");
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
            return START_NOT_STICKY;
        }
        String job = intent == null ? null : intent.getStringExtra("job");
        if (job == null) { stopSelf(startId); return START_NOT_STICKY; }
        String jobTitle;
        try { jobTitle = new JSONObject(job).optString("title", "Obra de Pixiv"); }
        catch (Exception ignored) { jobTitle = "Obra de Pixiv"; }
        final String queuedTitle = jobTitle;
        if (pending.getAndIncrement() == 0) {
            currentWorkTitle = queuedTitle;
            startForeground(NOTIFICATION, notification("Preparando descarga…", 0, 0, false));
            DownloadStatus.write(this, "Preparando descarga…", "active", 0, 0);
        }
        queue.execute(() -> {
            try {
                currentWorkTitle = queuedTitle;
                notifyState("Preparando descarga…", 0, 0, false);
                JSONObject data = new JSONObject(job);
                SavedDocument saved = save(data);
                if (!cancelled) {
                    String label = "Descarga completada: " + data.getString("title");
                    try {
                        GalleryStore.add(this, new GalleryStore.Entry(saved.uri.toString(), data.getString("title"),
                                saved.mime, saved.cover == null ? "" : saved.cover.toString(), saved.folder,
                                saved.count, System.currentTimeMillis()));
                    } catch (RuntimeException ignored) { /* The last completed status can be imported on next launch. */ }
                    DownloadStatus.completed(this, label, saved.uri.toString(), saved.mime, saved.folder,
                            data.optString("id", ""));
                    notifications.notify(NOTIFICATION, notification(label, 100, 100, true));
                }
            } catch (Exception error) {
                if (!cancelled) notifyState("Error: " + error.getMessage(), 0, 0, true, "error");
            } finally {
                if (pending.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_DETACH);
                    stopSelf();
                }
            }
        });
        return START_REDELIVER_INTENT;
    }

    private SavedDocument save(JSONObject job) throws Exception {
        Uri tree = Uri.parse(job.getString("folder"));
        String title = titleName(job.getString("title"));
        // Old jobs without "grouped" keep their original directory layout if redelivered.
        boolean grouped = job.optBoolean("grouped", true);
        Uri folder = grouped ? createUniqueFolder(tree, title)
                : DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        JSONArray files = job.getJSONArray("files");
        if (files.length() == 0) throw new IOException("No hay archivos seleccionados.");
        Uri lastFile = null;
        Uri firstFile = null;
        String lastMime = null;
        for (int i = 0; i < files.length(); i++) {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Descarga interrumpida");
            JSONObject file = files.getJSONObject(i);
            String name = grouped ? file.getString("name") : uniqueFileName(folder, flatFileName(title, file.getString("name")));
            String label = job.getString("title") + " · " + (i + 1) + "/" + files.length();
            Uri output = DocumentsContract.createDocument(getContentResolver(), folder, mime(name), name + ".part");
            if (output == null) throw new IOException("No se pudo crear " + name);
            try {
                if (file.has("frames")) {
                    convert(file, output, label);
                } else if (file.has("text")) {
                    try (OutputStream stream = getContentResolver().openOutputStream(output, "wt")) {
                        if (stream == null) throw new IOException("No se pudo escribir " + name);
                        stream.write(file.getString("text").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                } else {
                    try (OutputStream stream = getContentResolver().openOutputStream(output, "wt")) {
                        if (stream == null) throw new IOException("No se pudo escribir " + name);
                        download(file.getString("url"), stream, label);
                    }
                }
                Uri renamed = DocumentsContract.renameDocument(getContentResolver(), output, name);
                if (renamed == null) throw new IOException("No se pudo finalizar " + name);
                lastFile = renamed;
                if (firstFile == null) firstFile = renamed;
                lastMime = mime(name);
                notifyState(label, i + 1, files.length(), false);
            } catch (Exception error) {
                DocumentsContract.deleteDocument(getContentResolver(), output);
                throw error;
            }
        }
        return grouped ? new SavedDocument(folder, DocumentsContract.Document.MIME_TYPE_DIR, true, firstFile, files.length())
                : new SavedDocument(lastFile, lastMime, false, lastFile, 1);
    }

    private static final class SavedDocument {
        final Uri uri;
        final String mime;
        final boolean folder;
        final Uri cover;
        final int count;

        SavedDocument(Uri uri, String mime, boolean folder, Uri cover, int count) {
            this.uri = uri;
            this.mime = mime;
            this.folder = folder;
            this.cover = cover;
            this.count = count;
        }
    }

    private void convert(JSONObject file, Uri output, String label) throws Exception {
        File archive = File.createTempFile("pixload-", ".zip", getCacheDir());
        File video = null;
        try {
            try (OutputStream target = new FileOutputStream(archive)) {
                download(file.getString("url"), target, "Descargando animación · " + label);
            }
            JSONArray frames = file.getJSONArray("frames");
            UgoiraConverter.Progress progress = (done, total) -> {
                if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Conversión interrumpida");
                long now = System.currentTimeMillis();
                if (now - lastNotification > 500 || done == total) {
                    lastNotification = now;
                    notifyState("Convirtiendo " + file.optString("format").toUpperCase(Locale.ROOT) + " · " + done + "/" + total,
                            done, total, false);
                }
            };
            if ("gif".equals(file.getString("format"))) {
                notifyState("Convirtiendo GIF · 0/" + frames.length(), 0, frames.length(), false);
                try (OutputStream target = getContentResolver().openOutputStream(output, "wt")) {
                    if (target == null) throw new IOException("No se pudo escribir el GIF.");
                    UgoiraConverter.gif(archive, frames, target, progress,
                            GifPreset.fromId(file.optString("preset", "balanced")));
                }
            } else if ("mp4".equals(file.getString("format"))) {
                video = File.createTempFile("pixload-", ".mp4", getCacheDir());
                UgoiraConverter.mp4(archive, frames, video, progress);
                try (InputStream input = new FileInputStream(video);
                     OutputStream target = getContentResolver().openOutputStream(output, "wt")) {
                    if (target == null) throw new IOException("No se pudo escribir el MP4.");
                    byte[] bytes = new byte[32768];
                    int count;
                    while ((count = input.read(bytes)) != -1) {
                        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Descarga interrumpida");
                        target.write(bytes, 0, count);
                    }
                }
            } else throw new IOException("Formato de animación no válido.");
        } finally {
            if (video != null) video.delete();
            archive.delete();
        }
    }

    private void download(String url, OutputStream output, String label) throws IOException {
        HttpURLConnection source = PixivClient.open(url);
        try {
            if (source.getResponseCode() != 200) throw new IOException("Pixiv respondió con HTTP " + source.getResponseCode());
            long length = source.getContentLengthLong();
            try (InputStream input = source.getInputStream()) {
                byte[] bytes = new byte[32768];
                long read = 0;
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Descarga interrumpida");
                    output.write(bytes, 0, count);
                    read += count;
                    long now = System.currentTimeMillis();
                    if (now - lastNotification > 500) {
                        lastNotification = now;
                        int percent = length > 0 ? (int) Math.min(100, read * 100 / length) : 0;
                        notifyState(label, percent, length > 0 ? 100 : 0, false);
                    }
                }
            }
        } finally { source.disconnect(); }
    }

    private Uri createUniqueFolder(Uri tree, String title) throws Exception {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        for (int attempt = 0; attempt < 30; attempt++) {
            String candidate = title + "-" + suffix();
            if (!documentExists(parent, candidate)) {
                Uri result = DocumentsContract.createDocument(getContentResolver(), parent,
                        DocumentsContract.Document.MIME_TYPE_DIR, candidate);
                if (result != null) return result;
            }
        }
        throw new IOException("No se pudo crear una carpeta única.");
    }

    private String uniqueFileName(Uri parent, String desired) throws IOException {
        int dot = desired.lastIndexOf('.');
        String stem = desired.substring(0, dot);
        String extension = desired.substring(dot);
        for (int attempt = 0; attempt < 30; attempt++) {
            String candidate = attempt == 0 ? desired : stem + "-" + suffix() + extension;
            if (!documentExists(parent, candidate) && !documentExists(parent, candidate + ".part")) return candidate;
        }
        throw new IOException("No se pudo crear un nombre de archivo único.");
    }

    private boolean documentExists(Uri parent, String name) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = getContentResolver().query(children,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor == null) throw new IOException("No se puede acceder a la carpeta elegida.");
            while (cursor.moveToNext()) if (name.equals(cursor.getString(0))) return true;
        }
        return false;
    }

    private static String suffix() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder suffix = new StringBuilder(4);
        for (int i = 0; i < 4; i++) suffix.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return suffix.toString();
    }

    static String flatFileName(String title, String pageName) {
        int dot = pageName.lastIndexOf('.');
        return title + pageName.substring(dot);
    }

    static String titleName(String title) {
        // Keep accents, non-Latin scripts and punctuation where the document provider permits them.
        String clean = title.replaceAll("[\\p{Cntrl}\\p{Cf}/\\\\:*?\"<>|]", " ")
                .replaceAll("\\s+", " ").trim().replaceAll("^[. ]+|[. ]+$", "");
        if (clean.isEmpty()) return "Obra";
        StringBuilder name = new StringBuilder();
        int bytes = 0;
        for (int index = 0; index < clean.length();) {
            int point = clean.codePointAt(index);
            String character = new String(Character.toChars(point));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 180) break; // Reserve room for extension, collision suffix and .part.
            name.append(character);
            bytes += size;
            index += Character.charCount(point);
        }
        String shortened = name.toString().replaceAll("[. ]+$", "");
        return shortened.isEmpty() ? "Obra" : shortened;
    }

    private static String mime(String name) {
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private void notifyState(String title, int progress, int max, boolean done) {
        notifyState(title, progress, max, done, done ? "complete" : "active");
    }

    private void notifyState(String title, int progress, int max, boolean done, String state) {
        DownloadStatus.write(this, title, state, progress, max);
        notifications.notify(NOTIFICATION, notification(title, progress, max, done));
    }

    private Notification notification(String title, int progress, int max, boolean done) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.pixload.app.R.drawable.ic_download)
                .setContentTitle(currentWorkTitle)
                .setContentText(notificationDetail(title, currentWorkTitle, progress, max, done))
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(!done);
        if (!done) {
            builder.setProgress(max, progress, max == 0);
            Intent cancel = new Intent(this, DownloadService.class).setAction("cancel");
            PendingIntent action = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(com.pixload.app.R.drawable.tabler_x, "Cancelar", action);
        }
        return builder.build();
    }

    static String notificationDetail(String stage, String workTitle, int progress, int max, boolean done) {
        if (done) return stage.startsWith("Descarga completada:") ? "Descarga completada" : stage;
        String description = stage;
        String animation = "Descargando animación · " + workTitle + " · ";
        if (stage.startsWith(animation)) description = "Descargando animación · Archivo " + stage.substring(animation.length());
        else if (stage.startsWith(workTitle + " · ")) description = "Archivo " + stage.substring(workTitle.length() + 3);
        if (max > 0) description += " · " + Math.min(100, Math.max(0, (int) ((long) progress * 100 / max))) + " %";
        return description;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        queue.shutdownNow();
        super.onDestroy();
    }
}
