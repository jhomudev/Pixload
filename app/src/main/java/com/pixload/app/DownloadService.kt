package com.pixload.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import com.pixload.shared.DownloadNames
import com.pixload.shared.GifPreset
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val queue: ExecutorService = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger()
    @Volatile private var cancelled = false
    private lateinit var notifications: NotificationManager
    private var lastNotification = 0L
    @Volatile private var currentWorkTitle = "Obra de Pixiv"

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            AppLanguage.label(this, "Descargas"), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && "cancel" == intent.action) {
            cancelled = true
            queue.shutdownNow()
            notifyState("Descarga cancelada", 0, 0, true, "cancelled")
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }
        val job = intent?.getStringExtra("job")
        if (job == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val queuedTitle = try {
            JSONObject(job).optString("title", "Obra de Pixiv")
        } catch (_: Exception) {
            "Obra de Pixiv"
        }
        if (pending.getAndIncrement() == 0) {
            currentWorkTitle = queuedTitle
            startForeground(NOTIFICATION, notification("Preparando descarga…", 0, 0, false))
            DownloadStatus.write(this, "Preparando descarga…", "active", 0, 0)
        }
        queue.execute {
            try {
                currentWorkTitle = queuedTitle
                notifyState("Preparando descarga…", 0, 0, false)
                val data = JSONObject(job)
                val saved = save(data)
                if (!cancelled) {
                    val label = "Descarga completada: " + data.getString("title")
                    try {
                        GalleryStore.add(this, GalleryStore.Entry(saved.uri!!.toString(), data.getString("title"),
                            saved.mime!!, saved.cover?.toString() ?: "", saved.folder,
                            saved.count, System.currentTimeMillis(), data.optString("id", ""),
                            data.optString("folder", "")))
                    } catch (_: RuntimeException) {
                        // The last completed status can be imported on next launch.
                    }
                    DownloadStatus.completed(this, label, saved.uri!!.toString(), saved.mime, saved.folder,
                        data.optString("id", ""))
                    notifications.notify(NOTIFICATION, notification(label, 100, 100, true))
                }
            } catch (error: Exception) {
                if (!cancelled) notifyState("Error: " + error.message, 0, 0, true, "error")
            } finally {
                if (pending.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    @Throws(Exception::class)
    private fun save(job: JSONObject): SavedDocument {
        val tree = Uri.parse(job.getString("folder"))
        val title = titleName(job.getString("title"))
        // Old jobs without "grouped" keep their original directory layout if redelivered.
        val grouped = job.optBoolean("grouped", true)
        val start = startOrdinal(job.optBoolean("duplicate", false))
        val folder = if (grouped) createUniqueFolder(tree, title, start)
            else DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = job.getJSONArray("files")
        if (files.length() == 0) throw IOException("No hay archivos seleccionados.")
        var lastFile: Uri? = null
        var firstFile: Uri? = null
        var lastMime: String? = null
        for (i in 0 until files.length()) {
            if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Descarga interrumpida")
            val file = files.getJSONObject(i)
            val name = if (grouped) file.getString("name")
                else uniqueFileName(folder, flatFileName(title, file.getString("name")), start)
            val label = job.getString("title") + " · " + (i + 1) + "/" + files.length()
            val output = DocumentsContract.createDocument(contentResolver, folder, mime(name), "$name.part")
                ?: throw IOException("No se pudo crear $name")
            try {
                if (file.has("frames")) {
                    convert(file, output, label)
                } else if (file.has("text")) {
                    contentResolver.openOutputStream(output, "wt").use { stream ->
                        if (stream == null) throw IOException("No se pudo escribir $name")
                        stream.write(file.getString("text").toByteArray(StandardCharsets.UTF_8))
                    }
                } else {
                    contentResolver.openOutputStream(output, "wt").use { stream ->
                        if (stream == null) throw IOException("No se pudo escribir $name")
                        download(file.getString("url"), stream, label)
                    }
                }
                val renamed = DocumentsContract.renameDocument(contentResolver, output, name)
                    ?: throw IOException("No se pudo finalizar $name")
                lastFile = renamed
                if (firstFile == null) firstFile = renamed
                lastMime = mime(name)
                notifyState(label, i + 1, files.length(), false)
            } catch (error: Exception) {
                DocumentsContract.deleteDocument(contentResolver, output)
                throw error
            }
        }
        return if (grouped) SavedDocument(folder, DocumentsContract.Document.MIME_TYPE_DIR, true, firstFile, files.length())
            else SavedDocument(lastFile, lastMime, false, lastFile, 1)
    }

    private class SavedDocument(val uri: Uri?, val mime: String?, val folder: Boolean,
                                val cover: Uri?, val count: Int)

    @Throws(Exception::class)
    private fun convert(file: JSONObject, output: Uri, label: String) {
        val archive = File.createTempFile("pixload-", ".zip", cacheDir)
        var video: File? = null
        try {
            FileOutputStream(archive).use { target ->
                download(file.getString("url"), target, "Descargando animación · $label")
            }
            val frames = file.getJSONArray("frames")
            val progress = UgoiraConverter.Progress { done, total ->
                if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Conversión interrumpida")
                val now = System.currentTimeMillis()
                if (now - lastNotification > 500 || done == total) {
                    lastNotification = now
                    notifyState("Convirtiendo " + file.optString("format").uppercase(Locale.ROOT) + " · $done/$total",
                        done, total, false)
                }
            }
            if ("gif" == file.getString("format")) {
                notifyState("Convirtiendo GIF · 0/" + frames.length(), 0, frames.length(), false)
                contentResolver.openOutputStream(output, "wt").use { target ->
                    if (target == null) throw IOException("No se pudo escribir el GIF.")
                    UgoiraConverter.gif(archive, frames, target, progress,
                        GifPreset.fromId(file.optString("preset", "balanced")))
                }
            } else if ("mp4" == file.getString("format")) {
                video = File.createTempFile("pixload-", ".mp4", cacheDir)
                UgoiraConverter.mp4(archive, frames, video, progress)
                FileInputStream(video).use { input ->
                    contentResolver.openOutputStream(output, "wt").use { target ->
                        if (target == null) throw IOException("No se pudo escribir el MP4.")
                        val bytes = ByteArray(32768)
                        while (true) {
                            val count = input.read(bytes)
                            if (count == -1) break
                            if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Descarga interrumpida")
                            target.write(bytes, 0, count)
                        }
                    }
                }
            } else throw IOException("Formato de animación no válido.")
        } finally {
            video?.delete()
            archive.delete()
        }
    }

    @Throws(IOException::class)
    private fun download(url: String, output: OutputStream, label: String) {
        val source = PixivClient.open(url)
        try {
            if (source.responseCode != 200) throw IOException("Pixiv respondió con HTTP " + source.responseCode)
            val length = source.contentLengthLong
            source.inputStream.use { input ->
                val bytes = ByteArray(32768)
                var read = 0L
                while (true) {
                    val count = input.read(bytes)
                    if (count == -1) break
                    if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Descarga interrumpida")
                    output.write(bytes, 0, count)
                    read += count
                    val now = System.currentTimeMillis()
                    if (now - lastNotification > 500) {
                        lastNotification = now
                        val percent = if (length > 0) minOf(100L, read * 100 / length).toInt() else 0
                        notifyState(label, percent, if (length > 0) 100 else 0, false)
                    }
                }
            }
        } finally {
            source.disconnect()
        }
    }

    @Throws(Exception::class)
    private fun createUniqueFolder(tree: Uri, title: String, start: Int): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for (attempt in start until start + 1000) {
            val candidate = folderName(title, attempt)
            if (!documentExists(parent, candidate)) {
                val result = DocumentsContract.createDocument(contentResolver, parent,
                    DocumentsContract.Document.MIME_TYPE_DIR, candidate)
                if (result != null) return result
            }
        }
        throw IOException("No se pudo crear una carpeta única.")
    }

    @Throws(IOException::class)
    private fun uniqueFileName(parent: Uri, desired: String, start: Int): String {
        for (attempt in start until start + 1000) {
            val candidate = collisionName(desired, attempt)
            if (!documentExists(parent, candidate) && !documentExists(parent, "$candidate.part")) return candidate
        }
        throw IOException("No se pudo crear un nombre de archivo único.")
    }

    @Throws(IOException::class)
    private fun documentExists(parent: Uri, name: String): Boolean {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor == null) throw IOException("No se puede acceder a la carpeta elegida.")
            while (cursor.moveToNext()) if (name == cursor.getString(0)) return true
        }
        return false
    }

    private fun mime(name: String): String {
        if (name.endsWith(".zip")) return "application/zip"
        if (name.endsWith(".mp4")) return "video/mp4"
        if (name.endsWith(".json")) return "application/json"
        if (name.endsWith(".txt")) return "text/plain"
        if (name.endsWith(".png")) return "image/png"
        if (name.endsWith(".webp")) return "image/webp"
        if (name.endsWith(".gif")) return "image/gif"
        return "image/jpeg"
    }

    private fun notifyState(title: String, progress: Int, max: Int, done: Boolean) {
        notifyState(title, progress, max, done, if (done) "complete" else "active")
    }

    private fun notifyState(title: String, progress: Int, max: Int, done: Boolean, state: String) {
        DownloadStatus.write(this, title, state, progress, max)
        notifications.notify(NOTIFICATION, notification(title, progress, max, done))
    }

    @Suppress("DEPRECATION") // Preserve the existing notification action on supported Android versions.
    private fun notification(title: String, progress: Int, max: Int, done: Boolean): Notification {
        val open = Intent(this, MainActivity::class.java)
        val content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(currentWorkTitle)
            .setContentText(AppLanguage.stage(this, notificationDetail(title, currentWorkTitle, progress, max, done)))
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)
        if (!done) {
            builder.setProgress(max, progress, max == 0)
            val cancel = Intent(this, DownloadService::class.java).setAction("cancel")
            val action = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(R.drawable.tabler_x, AppLanguage.label(this, "Cancelar"), action)
        }
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        queue.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "pixload_downloads"
        private const val NOTIFICATION = 100

        @JvmStatic fun startOrdinal(duplicate: Boolean): Int = DownloadNames.startOrdinal(duplicate)
        @JvmStatic fun folderName(title: String, ordinal: Int): String = DownloadNames.folderName(title, ordinal)
        @JvmStatic fun collisionName(desired: String, ordinal: Int): String = DownloadNames.collisionName(desired, ordinal)
        @JvmStatic fun flatFileName(title: String, pageName: String): String = DownloadNames.flatFileName(title, pageName)
        @JvmStatic fun titleName(title: String): String = DownloadNames.titleName(title)

        @JvmStatic
        fun notificationDetail(stage: String, workTitle: String, progress: Int, max: Int, done: Boolean): String {
            if (done) return if (stage.startsWith("Descarga completada:")) "Descarga completada" else stage
            var description = stage
            val animation = "Descargando animación · $workTitle · "
            if (stage.startsWith(animation)) description = "Descargando animación · Archivo " + stage.substring(animation.length)
            else if (stage.startsWith("$workTitle · ")) description = "Archivo " + stage.substring(workTitle.length + 3)
            if (max > 0) description += " · " + minOf(100, maxOf(0, (progress.toLong() * 100 / max).toInt())) + " %"
            return description
        }
    }
}
