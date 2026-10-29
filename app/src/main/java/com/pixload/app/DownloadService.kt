package com.pixload.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val queue: ExecutorService = Executors.newFixedThreadPool(MAX_ACTIVE)
    private val pending = AtomicInteger()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val nameLock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val policy = WifiDownloadPolicy()
    private var activeSlots = 0
    private var destroyed = false // Main thread; callbacks already posted after unregister must not restart work.
    private lateinit var connectivity: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post { reconcile() } }
        override fun onLost(network: Network) { main.post { reconcile() } }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            main.post { reconcile() }
        }
    }
    private lateinit var notifications: NotificationManager
    @Volatile private var lastFinishedTitle = "Obra de Pixiv"
    @Volatile private var lastFinishedStage = "Descarga completada"

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(networkCallback)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            AppLanguage.label(this, "Descargas"), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "policy") {
            if (pending.get() == 0) stopSelf(startId)
            else reconcile()
            return START_NOT_STICKY
        }
        if (intent != null && "cancel" == intent.action) {
            val queueId = intent.getStringExtra("queueId")
            if (queueId.isNullOrEmpty()) cancelAll() else cancelJob(queueId)
            return START_NOT_STICKY
        }
        val raw = intent?.getStringExtra("job")
        if (raw == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val data = try {
            JSONObject(raw)
        } catch (_: Exception) {
            DownloadStatus.write(this, "No se pudo iniciar la descarga", "error", 0, 0)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val job = Job(data.optString("queueId", ""), data.optString("title", "Obra de Pixiv"), data)
        jobs[job.queueId] = job
        if (pending.getAndIncrement() == 0) {
            DownloadStatus.write(this, "Preparando descarga…", "active", 0, 0)
            startForeground(NOTIFICATION, notification())
        }
        reconcile()
        return START_NOT_STICKY
    }

    /** Each job owns its cancellation flag so cancelling one download leaves the others running. */
    private class Job(val queueId: String, val title: String, val data: JSONObject) {
        @Volatile var cancelled = false
        @Volatile var wifiLost = false
        var scheduled = false // Main thread only; waiting jobs never occupy an executor slot.
        @Volatile var network: Network? = null // Captured before admission; null means unrestricted.
        @Volatile var thread: Thread? = null
        @Volatile var lastNotification = 0L

        /** Page being transferred and how many pages the whole work has. */
        @Volatile var pageIndex = 0
        @Volatile var pageCount = 1

        /** Slice of the whole work this phase fills: the ugoira archive is half, conversion the rest. */
        @Volatile var phaseBase = 0
        @Volatile var phaseSpan = 100
    }

    private fun run(job: Job) {
        job.thread = Thread.currentThread()
        try {
            if (job.cancelled) return
            ensureAllowed(job)
            notifyState(job, 0)
            val saved = save(job)
            ensureAllowed(job)
            if (job.cancelled) return
            val label = "Descarga completada: " + job.data.getString("title")
            try {
                GalleryStore.add(this, GalleryStore.Entry(saved.uri!!.toString(), job.data.getString("title"),
                    saved.mime!!, saved.cover?.toString() ?: "", saved.folder,
                    saved.count, System.currentTimeMillis(), job.data.optString("id", ""),
                    job.data.optString("folder", "")))
            } catch (_: RuntimeException) {
                // The last completed status can be imported on next launch.
            }
            DownloadStatus.completed(this, label, saved.uri!!.toString(), saved.mime, saved.folder,
                job.data.optString("id", ""))
            lastFinishedTitle = job.title
            lastFinishedStage = label
            DownloadQueue.finish(this, job.queueId)
        } catch (error: Exception) {
            if (job.cancelled) {
                if (jobs[job.queueId] === job) DownloadQueue.finish(this, job.queueId)
            } else if (job.wifiLost || !allowed()) {
                DownloadQueue.interrupt(this, job.queueId)
                lastFinishedTitle = job.title
                lastFinishedStage = "Wi-Fi perdido · Reintentar"
                DownloadStatus.write(this, lastFinishedStage, "interrupted", 0, 0)
            } else {
                val safe = UserError.safeSpanish(error.message)
                DownloadStatus.write(this, "Error: $safe", "error", 0, 0)
                lastFinishedTitle = job.title
                lastFinishedStage = "Error: $safe"
                DownloadQueue.fail(this, job.queueId)
            }
        } finally {
            job.thread = null
            jobs.remove(job.queueId, job)
            pending.decrementAndGet()
            main.post {
                activeSlots--
                reconcile()
                stopIfEmpty()
            }
        }
    }

    private fun cancelJob(queueId: String) {
        val job = jobs[queueId]
        job?.let {
            it.cancelled = true
            it.thread?.interrupt()
            if (!it.scheduled && jobs.remove(queueId, it)) pending.decrementAndGet()
        }
        DownloadQueue.finish(this, queueId)
        DownloadStatus.write(this, "Descarga cancelada", "cancelled", 0, 0)
        lastFinishedTitle = job?.title ?: lastFinishedTitle
        lastFinishedStage = "Descarga cancelada"
        reconcile()
        stopIfEmpty()
    }

    private fun cancelAll() {
        // Jobs finish through their own cancellation flag; shutting the pool down here could
        // reject a start that arrives while this instance is still alive.
        for (job in jobs.values) {
            job.cancelled = true
            job.thread?.interrupt()
            if (!job.scheduled && jobs.remove(job.queueId, job)) pending.decrementAndGet()
            DownloadQueue.finish(this, job.queueId)
        }
        DownloadStatus.write(this, "Descarga cancelada", "cancelled", 0, 0)
        lastFinishedStage = "Descarga cancelada"
        reconcile()
        stopIfEmpty()
    }

    private fun onlyWifi(): Boolean = getSharedPreferences(WIFI_PREFS, MODE_PRIVATE).getBoolean(WIFI_ONLY, false)

    /** Unknown connectivity is denied only when opted in. Never inspect metered status. */
    private fun wifiNetwork(): Network? = try {
        connectivity.activeNetwork?.takeIf { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    } catch (_: SecurityException) { null }

    private fun allowed(): Boolean = policy.admit(onlyWifi(), wifiNetwork() != null)

    private fun ensureAllowed(job: Job) {
        if (job.wifiLost || (onlyWifi() && (job.network == null || wifiNetwork() != job.network))) {
            job.wifiLost = true
            throw IOException("Wi-Fi perdido · Reintentar")
        }
        if (job.cancelled || Thread.currentThread().isInterrupted) throw IOException("Descarga interrumpida")
    }

    /** Serialized on the main thread: admit only three workers and retain the other jobs in memory. */
    private fun reconcile() {
        if (destroyed) return
        val restricted = onlyWifi()
        val wifi = if (restricted) wifiNetwork() else null
        val admitted = policy.admit(restricted, wifi != null)
        // Queue order is the admission order; ConcurrentHashMap.values has no FIFO guarantee.
        for (item in DownloadQueue.items(this)) {
            val job = jobs[item.id] ?: continue
            if (job.cancelled) continue
            if (!admitted || (restricted && job.scheduled && job.network != wifi)) {
                if (job.thread != null) {
                    job.wifiLost = true
                    job.thread?.interrupt()
                } else if (!job.scheduled) DownloadQueue.waitForWifi(this, job.queueId)
            } else if (!job.scheduled && activeSlots < MAX_ACTIVE) {
                job.network = wifi
                job.scheduled = true
                activeSlots++
                DownloadQueue.markActive(this, job.queueId)
                queue.execute { run(job) }
            }
        }
        updateNotification()
    }

    private fun stopIfEmpty() {
        if (pending.get() == 0) {
            updateNotification()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    @Throws(Exception::class)
    private fun save(job: Job): SavedDocument {
        val data = job.data
        val tree = Uri.parse(data.getString("folder"))
        val title = titleName(data.getString("title"))
        // Old jobs without "grouped" keep their original directory layout if redelivered.
        val grouped = data.optBoolean("grouped", true)
        val start = startOrdinal(data.optBoolean("duplicate", false))
        val folder = if (grouped) synchronized(nameLock) { createUniqueFolder(tree, title, start) }
            else DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = data.getJSONArray("files")
        if (files.length() == 0) throw IOException("No hay archivos seleccionados.")
        var lastFile: Uri? = null
        var firstFile: Uri? = null
        var lastMime: String? = null
        for (i in 0 until files.length()) {
            ensureAllowed(job)
            val file = files.getJSONObject(i)
            job.pageIndex = i
            job.pageCount = files.length()
            // Resolution and creation stay atomic: three workers may share the same tree.
            val created = synchronized(nameLock) {
                val candidate = if (grouped) file.getString("name")
                    else uniqueFileName(folder, flatFileName(title, file.getString("name")), start)
                candidate to (DocumentsContract.createDocument(contentResolver, folder, mime(candidate), "$candidate.part")
                    ?: throw IOException("No se pudo crear $candidate"))
            }
            val name = created.first
            val output = created.second
            try {
                ensureAllowed(job)
                if (file.has("frames")) {
                    convert(job, file, output)
                } else if (file.has("text")) {
                    contentResolver.openOutputStream(output, "wt").use { stream ->
                        if (stream == null) throw IOException("No se pudo escribir $name")
                        ensureAllowed(job)
                        NovelDownload.write(file, stream)
                    }
                } else {
                    contentResolver.openOutputStream(output, "wt").use { stream ->
                        if (stream == null) throw IOException("No se pudo escribir $name")
                        download(job, file.getString("url"), stream)
                    }
                }
                ensureAllowed(job)
                val renamed = DocumentsContract.renameDocument(contentResolver, output, name)
                    ?: throw IOException("No se pudo finalizar $name")
                lastFile = renamed
                if (firstFile == null) firstFile = renamed
                lastMime = mime(name)
                notifyState(job, jobPercent(job, 100))
            } catch (error: Exception) {
                try { DocumentsContract.deleteDocument(contentResolver, output) }
                catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }
        return if (grouped) SavedDocument(folder, DocumentsContract.Document.MIME_TYPE_DIR, true, firstFile, files.length())
            else SavedDocument(lastFile, lastMime, false, lastFile, 1)
    }

    private class SavedDocument(val uri: Uri?, val mime: String?, val folder: Boolean,
                                val cover: Uri?, val count: Int)

    @Throws(Exception::class)
    private fun convert(job: Job, file: JSONObject, output: Uri) {
        val archive = File.createTempFile("pixload-", ".zip", cacheDir)
        var video: File? = null
        // Half the work is fetching the archive, the other half converting it.
        job.phaseBase = 0
        job.phaseSpan = 50
        try {
            FileOutputStream(archive).use { target ->
                download(job, file.getString("url"), target)
            }
            job.phaseBase = 50
            job.phaseSpan = 50
            val frames = file.getJSONArray("frames")
            val progress = UgoiraConverter.Progress { done, total ->
                ensureAllowed(job)
                val now = System.currentTimeMillis()
                if (now - job.lastNotification > 500 || done == total) {
                    job.lastNotification = now
                    notifyState(job, jobPercent(job, if (total > 0) done * 100 / total else 0))
                }
            }
            if ("gif" == file.getString("format")) {
                notifyState(job, jobPercent(job, 0))
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
                            ensureAllowed(job)
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
    private fun download(job: Job, url: String, output: OutputStream) {
        ensureAllowed(job)
        val source = job.network?.let { PixivClient.open(url, it) } ?: PixivClient.open(url)
        try {
            ensureAllowed(job)
            if (source.responseCode != 200) throw IOException("Pixiv respondió con HTTP " + source.responseCode)
            val length = source.contentLengthLong
            source.inputStream.use { input ->
                val bytes = ByteArray(32768)
                var read = 0L
                while (true) {
                    val count = input.read(bytes)
                    ensureAllowed(job)
                    if (count == -1) break
                    output.write(bytes, 0, count)
                    read += count
                    val now = System.currentTimeMillis()
                    if (now - job.lastNotification > 500) {
                        job.lastNotification = now
                        val percent = if (length > 0) minOf(100L, read * 100 / length).toInt() else 0
                        notifyState(job, jobPercent(job, percent))
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

    /**
     * Reports [overall], the progress of the whole work, to the queue, so a multi-page row never
     * oscillates between the page count and the current page's byte percentage.
     */
    private fun notifyState(job: Job, overall: Int) {
        DownloadQueue.onProgress(this, job.queueId, overall.coerceIn(0, 100), 100)
        updateNotification()
    }

    private fun jobPercent(job: Job, fraction: Int): Int {
        val phase = job.phaseBase + fraction.coerceIn(0, 100) * job.phaseSpan / 100
        return DownloadQueueBook.overall(job.pageIndex, job.pageCount, phase)
    }

    private fun updateNotification() {
        notifications.notify(NOTIFICATION, notification())
    }

    @Suppress("DEPRECATION") // Preserve the existing notification action on supported Android versions.
    private fun notification(): Notification {
        val open = Intent(this, MainActivity::class.java)
        val content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
        val running = DownloadQueue.pending(this)
        if (running.isEmpty()) {
            builder.setContentTitle(lastFinishedTitle)
                .setContentText(AppLanguage.stage(this, lastFinishedStage))
                .setOngoing(false)
            return builder.build()
        }
        val percent = DownloadQueue.percent(this)
        val single = running.size == 1
        val wifiWaiting = running.count { it.state == DownloadQueueBook.WAITING_WIFI }
        val waitingWifi = wifiWaiting == running.size
        val detail = if (waitingWifi) {
            AppLanguage.label(this, "Esperando Wi-Fi · Red no disponible o sin verificar")!!
        } else if (wifiWaiting > 0) {
            "${running.size - wifiWaiting}" + AppLanguage.label(this, " en curso") +
                " · $wifiWaiting" + AppLanguage.label(this, " esperando Wi-Fi")
        } else if (single) {
            AppLanguage.label(this, "En curso") + " · " + percent + " %"
        } else {
            running.size.toString() + AppLanguage.label(this, " en curso") + " · " + percent + " %"
        }
        builder.setContentTitle(if (single) running[0].title else AppLanguage.label(this, "Descargas en curso"))
            .setContentText(AppLanguage.stage(this, detail))
            .setOngoing(true)
            .setProgress(100, percent, waitingWifi)
        val cancel = Intent(this, DownloadService::class.java).setAction("cancel")
        val action = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.addAction(R.drawable.tabler_x, AppLanguage.label(this, "Cancelar"), action)
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        connectivity.unregisterNetworkCallback(networkCallback)
        queue.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "pixload_downloads"
        private const val NOTIFICATION = 100
        private const val MAX_ACTIVE = 3
        private const val WIFI_PREFS = "wifi_download"
        private const val WIFI_ONLY = "only_wifi"

        @JvmStatic fun policyChanged(context: android.content.Context, enabled: Boolean) {
            context.getSharedPreferences(WIFI_PREFS, MODE_PRIVATE).edit().putBoolean(WIFI_ONLY, enabled).apply()
            context.startService(Intent(context, DownloadService::class.java).setAction("policy"))
        }

        @JvmStatic fun wifiOnly(context: android.content.Context): Boolean =
            context.getSharedPreferences(WIFI_PREFS, MODE_PRIVATE).getBoolean(WIFI_ONLY, false)

        /** Cancels a single queued or running job; the rest of the queue keeps going. */
        @JvmStatic fun cancel(context: android.content.Context, queueId: String) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction("cancel")
                .putExtra("queueId", queueId)
            context.startService(intent)
        }

        /** The document MIME registered with SAF; `.epub` must not fall back to JPEG. */
        @JvmStatic
        fun mime(name: String): String {
            if (name.endsWith(".zip")) return "application/zip"
            if (name.endsWith(".epub")) return "application/epub+zip"
            if (name.endsWith(".mp4")) return "video/mp4"
            if (name.endsWith(".json")) return "application/json"
            if (name.endsWith(".txt")) return "text/plain"
            if (name.endsWith(".png")) return "image/png"
            if (name.endsWith(".webp")) return "image/webp"
            if (name.endsWith(".gif")) return "image/gif"
            return "image/jpeg"
        }

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
