package com.pixload.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.pixload.shared.GifPreset
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

open class MainActivity : Activity() {
    private companion object {
        const val PICK_FOLDER = 11
        const val PIXIV_LOGIN = 12
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val galleryIO = Executors.newSingleThreadExecutor()
    private val images = PreviewImages(this)
    private val galleryImages = GalleryImages(this)
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdate = object : Runnable {
        override fun run() {
            refreshProgress()
            progressHandler.postDelayed(this, 500)
        }
    }
    private val pageChecks = ArrayList<CheckBox>()
    private val queueRows = ArrayList<QueueRow>()
    private lateinit var root: LinearLayout
    private var fadeNextTab = false
    private lateinit var content: LinearLayout
    private lateinit var bottom: LinearLayout
    private var scroll: ScrollView? = null
    private var link: EditText? = null
    private var status: TextView? = null
    private var sessionBadge: TextView? = null
    private var selectionCount: TextView? = null
    private var selectionDownloadButton: TextView? = null
    private var progressTitle: TextView? = null
    private var progressNumber: TextView? = null
    private var progressClose: ImageView? = null
    private var progressOpen: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressPanel: LinearLayout? = null
    private var work: PixivClient.Work? = null
    private var linkValue = ""
    private var message = ""
    private var previewNotice: String? = null
    private var previewNoticeWarning = true
    private var tab = 0
    private var showQueue = false
    private var loading = false
    private var loggedIn = false
    private var downloadAfterFolderPick = false
    private var mp4 = false
    private var defaultMp4 = false
    private var gifPreset = GifPreset.BALANCED
    private var sessionGeneration = 0
    private var selectedPages: BooleanArray? = null
    private var galleryEntry: GalleryStore.Entry? = null
    private var galleryCache: List<GalleryStore.Entry>? = null
    private var galleryCacheError: String? = null
    private var galleryCacheLoading = false
    private var galleryItems: List<GalleryDocuments.Item>? = null
    private var galleryLoading = false
    private var galleryError: String? = null
    private var galleryIndex = 0
    private var galleryVideo: VideoView? = null
    private var fullscreenMedia: View? = null
    private var galleryFullscreen = false
    private var galleryScrollY = 0
    private var galleryListRendered = false
    private var galleryScrollPending = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeEligible = false
    private var swiping = false
    private var lastGalleryStatusUri = ""

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Ui.INPUT
        window.navigationBarColor = Ui.BACK
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        linkValue = getPreferences(MODE_PRIVATE).getString("last_link", "") ?: ""
        importLastDownload()
        defaultMp4 = "mp4" == getPreferences(MODE_PRIVATE).getString("animation_format", "gif")
        mp4 = defaultMp4
        gifPreset = GifPreset.fromId(getPreferences(MODE_PRIVATE).getString("gif_preset", "balanced"))
        if (state != null) {
            tab = state.getInt("tab", 0)
            linkValue = state.getString("link", linkValue) ?: linkValue
            mp4 = state.getBoolean("mp4", mp4)
            selectedPages = state.getBooleanArray("pages")
            downloadAfterFolderPick = state.getBoolean("downloadAfterFolderPick")
            galleryIndex = state.getInt("galleryIndex", 0)
            galleryScrollY = state.getInt("galleryScrollY", 0)
            galleryFullscreen = state.getBoolean("galleryFullscreen")
            try { galleryEntry = GalleryStore.find(this, state.getString("galleryEntry", "")) }
            catch (_: IllegalStateException) { galleryEntry = null }
            galleryLoading = galleryEntry != null
            if (galleryEntry == null) galleryFullscreen = false
            val retained = lastNonConfigurationInstance
            if (retained is PixivClient.Work) work = retained
        }
        render()
        galleryEntry?.let { loadGalleryEntry(it, galleryIndex) }
        consume(intent)
    }

    public override fun onRetainNonConfigurationInstance(): Any? = work

    override fun onSaveInstanceState(state: Bundle) {
        link?.let { linkValue = it.text.toString() }
        syncSelection()
        state.putInt("tab", tab)
        state.putString("link", linkValue)
        state.putBoolean("mp4", mp4)
        state.putBooleanArray("pages", selectedPages)
        state.putBoolean("downloadAfterFolderPick", downloadAfterFolderPick)
        state.putString("galleryEntry", galleryEntry?.uri ?: "")
        state.putInt("galleryIndex", galleryIndex)
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll!!.scrollY
        state.putInt("galleryScrollY", galleryScrollY)
        state.putBoolean("galleryFullscreen", galleryFullscreen)
        super.onSaveInstanceState(state)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        if (intent == null) return
        val input = if (Intent.ACTION_SEND == intent.action) intent.getStringExtra(Intent.EXTRA_TEXT)
            else if (Intent.ACTION_VIEW == intent.action && intent.data != null) intent.data.toString() else null
        if (input != null) {
            linkValue = PixivLink.extract(input) ?: input
            link?.setText(linkValue)
            intent.action = null
            load()
        }
    }

    private fun render() {
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll!!.scrollY
        galleryListRendered = false
        galleryScrollPending = false
        images.clear()
        galleryImages.clear()
        selectionDownloadButton = null
        galleryVideo?.let { it.stopPlayback(); galleryVideo = null }
        progressTitle = null
        progressNumber = null
        progressClose = null
        progressOpen = null
        progressBar = null
        progressPanel = null
        val fade = fadeNextTab
        fadeNextTab = false
        root = Ui.column(this)
        root.setBackgroundColor(Ui.BACK)
        if (fade) root.alpha = .92f
        setContentView(root)
        if (fade) root.animate().alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        window.decorView.systemUiVisibility = if (galleryFullscreen)
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        else 0
        if (galleryFullscreen && tab == 2 && galleryEntry != null) {
            scroll = null
            fullscreenGallery()
            return
        }
        header()
        val page = ScrollView(this)
        scroll = page
        page.setOnScrollChangeListener { _, _, _, _, _ ->
            images.loadVisible()
            galleryImages.loadVisible()
        }
        page.isFillViewport = false
        page.clipToPadding = false
        content = Ui.column(this)
        content.setPadding(Ui.dp(this, 20f), Ui.dp(this, 20f), Ui.dp(this, 20f), Ui.dp(this, 24f))
        page.addView(content)
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = Ui.column(this)
        bottom.setPadding(Ui.dp(this, 20f), Ui.dp(this, 4f), Ui.dp(this, 20f), Ui.dp(this, 12f))
        root.addView(bottom)
        when {
            showQueue -> queueScreen()
            tab == 0 -> home()
            tab == 1 -> preview()
            tab == 2 -> gallery()
            else -> settings()
        }
        navigation()
        refreshProgress()
        page.post { images.loadVisible() }
        page.post { galleryImages.loadVisible() }
    }

    private fun header() {
        val bar = Ui.row(this)
        bar.setPadding(Ui.dp(this, 20f), Ui.dp(this, 14f), Ui.dp(this, 20f), Ui.dp(this, 12f))
        bar.setBackgroundColor(Ui.INPUT)
        val logo = ImageView(this)
        logo.setImageResource(R.mipmap.ic_launcher)
        logo.contentDescription = "Pixload"
        bar.addView(logo, LinearLayout.LayoutParams(Ui.dp(this, 42f), Ui.dp(this, 42f)))
        val brand = Ui.row(this)
        brand.addView(Ui.text(this, "Pixload", 23, Ui.WHITE, true))
        val dot = View(this)
        dot.background = Ui.background(this, Ui.CYAN, 10)
        val dotParams = LinearLayout.LayoutParams(Ui.dp(this, 6f), Ui.dp(this, 6f))
        dotParams.leftMargin = Ui.dp(this, 3f)
        brand.addView(dot, dotParams)
        val name = LinearLayout.LayoutParams(0, -2, 1f)
        name.leftMargin = Ui.dp(this, 10f)
        bar.addView(brand, name)
        val badge = Ui.text(this, AppLanguage.label(this, if (loggedIn) "Pixiv activo" else "Sin sesión")!!, 12,
            if (loggedIn) Ui.GREEN else Ui.MUTED, true)
        sessionBadge = badge
        Ui.withIcon(badge, if (loggedIn) R.drawable.tabler_circle_check else R.drawable.tabler_circle,
            if (loggedIn) Ui.GREEN else Ui.MUTED)
        badge.setPadding(Ui.dp(this, 12f), Ui.dp(this, 10f), Ui.dp(this, 12f), Ui.dp(this, 10f))
        badge.background = Ui.background(this, Ui.RAISED, 40)
        badge.setOnClickListener { switchTab(3) }
        bar.addView(badge)
        val queueButton = Ui.icon(this, R.drawable.tabler_transfer_vertical, Ui.CYAN)
        queueButton.contentDescription = AppLanguage.label(this, "Cola de descargas")
        queueButton.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        queueButton.setPadding(Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f))
        queueButton.background = null
        queueButton.setOnClickListener { openQueue() }
        val queueParams = LinearLayout.LayoutParams(Ui.dp(this, 36f), Ui.dp(this, 36f))
        queueParams.leftMargin = Ui.dp(this, 8f)
        bar.addView(queueButton, queueParams)
        root.addView(bar)
    }

    private fun navigation() {
        val nav = Ui.row(this)
        nav.setPadding(Ui.dp(this, 8f), Ui.dp(this, 4f), Ui.dp(this, 8f), Ui.dp(this, 4f))
        nav.background = Ui.outlined(this, Ui.RAISED, 48)
        val labels = arrayOf(getString(R.string.nav_download), getString(R.string.nav_preview),
            getString(R.string.nav_gallery), getString(R.string.nav_settings))
        val icons = intArrayOf(R.drawable.tabler_download, R.drawable.tabler_photo_edit,
            R.drawable.tabler_layout_grid, R.drawable.tabler_settings_2)
        for (i in labels.indices) {
            val color = if (tab == i) Ui.CYAN else Ui.MUTED
            val item = Ui.column(this)
            item.gravity = Gravity.CENTER
            item.minimumHeight = Ui.dp(this, 56f)
            item.contentDescription = labels[i]
            item.addView(Ui.icon(this, icons[i], color), LinearLayout.LayoutParams(Ui.dp(this, 22f), Ui.dp(this, 22f)))
            val label = Ui.text(this, labels[i], 12, color, tab == i)
            label.gravity = Gravity.CENTER
            item.addView(label)
            item.setOnClickListener { switchTab(i) }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        bottom.addView(nav)
    }

    private fun refreshProgress() {
        updateDownloadButton()
        if (showQueue && refreshQueue()) return
        val pending = DownloadQueue.pending(this)
        val current = if (pending.isEmpty()) DownloadStatus.read(this) else null
        if (current != null && clearCompletedWork(current)) { render(); return }
        if (current != null && current.state == "complete" && !current.documentUri.isNullOrEmpty()
            && current.documentUri != lastGalleryStatusUri) {
            lastGalleryStatusUri = current.documentUri
            importLastDownload()
            if (tab == 2 && galleryEntry == null) { render(); return }
        }
        if (pending.isEmpty() && current == null) {
            if (progressPanel != null) bottom.removeView(progressPanel)
            progressPanel = null
            progressTitle = null
            return
        }
        ensureProgressPanel()
        if (pending.isNotEmpty()) {
            renderQueueProgress(pending)
            return
        }
        val status = current ?: return
        // A process death leaves the last active record behind; the queue cannot resume it.
        val interrupted = status.state == "active"
        progressTitle!!.text = if (interrupted) AppLanguage.label(this, "Descarga interrumpida") else status.title
        val active = status.state == "active" && !interrupted
        progressClose!!.visibility = if (active) View.GONE else View.VISIBLE
        progressNumber!!.text = if (active) {
            if (status.max > 0) "${minOf(100, status.progress * 100 / status.max)}%" else AppLanguage.label(this, "En curso")
        } else if (interrupted) {
            AppLanguage.label(this, "Interrumpida")
        } else when (status.state) {
            "complete" -> AppLanguage.label(this, "Completada")
            "cancelled" -> AppLanguage.label(this, "Cancelada")
            else -> "Error"
        }
        progressNumber!!.setTextColor(if (status.state == "error" || interrupted) Ui.MUTED
            else if (status.state == "complete") Ui.GREEN else Ui.CYAN)
        progressBar!!.visibility = if (active) View.VISIBLE else View.GONE
        val canOpen = (status.state == "complete" && !status.documentUri.isNullOrEmpty()) ||
            DownloadStatus.lastUri(this) != null
        progressOpen!!.visibility = if (canOpen) View.VISIBLE else View.GONE
        if (canOpen) {
            progressOpen!!.text = AppLanguage.label(this, "Abrir en galería")
            Ui.withIcon(progressOpen!!, R.drawable.tabler_layout_grid, Ui.CYAN)
            progressOpen!!.setOnClickListener { openLastDownload() }
        }
        if (active) {
            progressBar!!.isIndeterminate = status.max <= 0
            if (status.max > 0) progressBar!!.progress = minOf(100, status.progress * 100 / status.max)
        }
    }

    private fun renderQueueProgress(pending: List<DownloadQueueBook.Item>) {
        val percent = DownloadQueue.percent(this)
        progressTitle!!.text = if (pending.size == 1) pending[0].title
            else AppLanguage.label(this, "Descargas en curso")
        progressNumber!!.text = if (pending.size == 1) {
            AppLanguage.label(this, "En curso") + if (percent > 0) " · $percent%" else ""
        } else {
            pending.size.toString() + AppLanguage.label(this, " en curso") + " · $percent%"
        }
        progressNumber!!.setTextColor(Ui.CYAN)
        progressClose!!.visibility = View.GONE
        progressBar!!.visibility = View.VISIBLE
        progressBar!!.isIndeterminate = false
        progressBar!!.progress = percent
        progressOpen!!.visibility = if (showQueue) View.GONE else View.VISIBLE
        progressOpen!!.text = AppLanguage.label(this, "Ver cola")
        Ui.withIcon(progressOpen!!, R.drawable.tabler_transfer_vertical, Ui.CYAN)
        progressOpen!!.setOnClickListener { openQueue() }
    }

    private fun ensureProgressPanel() {
        if (progressTitle != null) return
        progressPanel = Ui.column(this)
        val card = Ui.card(this, 12)
        val heading = Ui.row(this)
        progressTitle = Ui.text(this, "", 13, Ui.WHITE, true)
        progressTitle!!.maxLines = 2
        heading.addView(progressTitle, LinearLayout.LayoutParams(0, -2, 1f))
        progressNumber = Ui.text(this, "", 12, Ui.CYAN, true)
        heading.addView(progressNumber)
        progressClose = Ui.icon(this, R.drawable.tabler_x, Ui.MUTED)
        progressClose!!.contentDescription = AppLanguage.label(this, "Ocultar estado de descarga")
        progressClose!!.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        progressClose!!.setOnClickListener { DownloadStatus.dismiss(this); refreshProgress() }
        heading.addView(progressClose, LinearLayout.LayoutParams(Ui.dp(this, 36f), Ui.dp(this, 40f)))
        card.addView(heading)
        Ui.gap(card, this, 8)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar!!.max = 100
        progressBar!!.progressTintList = android.content.res.ColorStateList.valueOf(Ui.CYAN)
        progressBar!!.indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.CYAN)
        card.addView(progressBar, LinearLayout.LayoutParams(-1, Ui.dp(this, 5f)))
        progressOpen = Ui.button(this, "", false, Runnable { openLastDownload() })
        val openParams = LinearLayout.LayoutParams(-1, -2)
        openParams.topMargin = Ui.dp(this, 8f)
        card.addView(progressOpen, openParams)
        progressPanel!!.addView(card)
        Ui.gap(progressPanel!!, this, 8)
        bottom.addView(progressPanel, bottom.childCount - 1)
    }

    private fun openQueue() {
        if (showQueue) return
        showQueue = true
        render()
    }

    private class QueueRow(val id: String, val waiting: Boolean, val active: Boolean,
                           val detail: TextView, val bar: ProgressBar?)

    /** The queue screen refreshes in place so thumbnails are not reloaded every tick. */
    private fun refreshQueue(): Boolean {
        val items = DownloadQueue.items(this)
        // A finished job replaced by a new one keeps the row count but not the rows.
        if (items.size != queueRows.size || items.map { it.id } != queueRows.map { it.id }) {
            root.post { if (showQueue) render() }
            return true
        }
        for (row in queueRows) {
            val item = items.firstOrNull { it.id == row.id } ?: continue
            val waiting = DownloadQueueBook.isPending(item)
            val active = item.state == DownloadQueueBook.ACTIVE
            if (waiting != row.waiting || active != row.active) {
                root.post { if (showQueue) render() }
                return true
            }
            // Failed and interrupted rows are static: their label comes from the row state.
            if (!waiting) continue
            val expected = if (!active) AppLanguage.label(this, "En espera")!!
            else if (item.max > 0) "${minOf(100, item.progress * 100 / item.max)} %"
            else "0 %"
            if (row.detail.text.toString() != expected) row.detail.text = expected
            row.bar?.progress = if (item.max > 0) minOf(100, item.progress * 100 / item.max) else 0
        }
        return false
    }

    private fun queueScreen() {
        queueRows.clear()
        content.addView(Ui.text(this, "Cola de descargas", 26, Ui.WHITE, true))
        Ui.gap(content, this, 6)
        content.addView(Ui.text(this, "Hasta 3 a la vez · máximo 5 en cola", 14, Ui.MUTED, false))
        Ui.gap(content, this, 20)
        val items = DownloadQueue.items(this)
        if (items.isEmpty()) {
            val empty = Ui.card(this, 18)
            empty.addView(Ui.label(this, "No hay descargas en curso", R.drawable.tabler_transfer_vertical, 18, Ui.CYAN))
            Ui.gap(empty, this, 9)
            empty.addView(Ui.text(this,
                "Las obras que descargues aparecerán acá con su progreso y podrás cancelarlas una por una.",
                14, Ui.MUTED, false))
            Ui.gap(empty, this, 14)
            val hint = Ui.row(this)
            hint.addView(queueChip("3 a la vez"))
            val hintParams = LinearLayout.LayoutParams(-2, -2)
            hintParams.leftMargin = Ui.dp(this, 8f)
            hint.addView(queueChip("Máximo 5"), hintParams)
            empty.addView(hint)
            Ui.gap(empty, this, 14)
            empty.addView(Ui.button(this, "Buscar una obra", R.drawable.tabler_search, true, Runnable { switchTab(0) }))
            content.addView(empty)
            return
        }
        for (item in items) {
            val card = Ui.card(this, 12)
            val row = Ui.row(this)
            row.addView(images.view(item.thumbnail, 120, persist = true), LinearLayout.LayoutParams(Ui.dp(this, 56f), Ui.dp(this, 56f)))
            val info = Ui.column(this)
            val infoParams = LinearLayout.LayoutParams(0, -2, 1f)
            infoParams.leftMargin = Ui.dp(this, 12f)
            val title = Ui.rawText(this, item.title, 14, Ui.WHITE, true)
            title.maxLines = 2
            info.addView(title)
            Ui.gap(info, this, 4)
            info.addView(Ui.rawText(this, queueMeta(item), 12, Ui.MUTED, false))
            Ui.gap(info, this, 4)
            val waiting = DownloadQueueBook.isPending(item)
            val active = item.state == DownloadQueueBook.ACTIVE
            val failed = item.state == DownloadQueueBook.ERROR
            val percent = if (item.max > 0) minOf(100, item.progress * 100 / item.max) else 0
            val detail = when {
                failed -> AppLanguage.label(this, "Error")
                !waiting -> AppLanguage.label(this, "Interrumpida")
                !active -> AppLanguage.label(this, "En espera")
                else -> "$percent %"
            }
            val detailView = Ui.rawText(this, detail!!, 12,
                if (failed) Ui.DANGER else if (active) Ui.CYAN else Ui.MUTED, false)
            info.addView(detailView)
            row.addView(info, infoParams)
            val action = Ui.icon(this, R.drawable.tabler_x, Ui.MUTED)
            action.setPadding(Ui.dp(this, 8f), Ui.dp(this, 8f), Ui.dp(this, 8f), Ui.dp(this, 8f))
            action.contentDescription = AppLanguage.label(this, if (waiting) "Cancelar descarga" else "Quitar")
            action.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            action.setOnClickListener {
                if (waiting) DownloadService.cancel(this, item.id)
                else { DownloadQueue.finish(this, item.id); render() }
            }
            row.addView(action, LinearLayout.LayoutParams(Ui.dp(this, 48f), Ui.dp(this, 48f)))
            card.addView(row)
            var bar: ProgressBar? = null
            if (active) {
                Ui.gap(card, this, 10)
                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                progressBar.max = 100
                progressBar.progress = percent
                progressBar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.CYAN)
                card.addView(progressBar, LinearLayout.LayoutParams(-1, Ui.dp(this, 4f)))
                bar = progressBar
            }
            queueRows.add(QueueRow(item.id, waiting, active, detailView, bar))
            content.addView(card)
            Ui.gap(content, this, 10)
        }
        content.post { images.loadVisible() }
    }

    /** "Manga · 12 páginas" / "Ugoira · 1 archivo": type plus how much the item will produce. */
    private fun queueMeta(item: DownloadQueueBook.Item): String {
        val count = if (item.pages > 0) item.pages else 1
        val single = item.kind == "novel" || item.kind == "ugoira"
        val unit = if (single) AppLanguage.label(this, if (count == 1) " archivo" else " archivos")
        else AppLanguage.label(this, if (count == 1) " página" else " páginas")
        val kind = when (item.kind) {
            "novel" -> AppLanguage.label(this, "Novela")
            "ugoira" -> "Ugoira"
            "manga" -> AppLanguage.label(this, "Manga")
            "illustration" -> AppLanguage.label(this, "Ilustración")
            else -> null
        }
        // Rows persisted by an older build have no type; show the count alone.
        return if (kind == null) count.toString() + unit else kind + " · " + count + unit
    }

    private fun queueChip(label: String): TextView =
        Ui.text(this, label, 11, Ui.MUTED, true).apply {
            setPadding(Ui.dp(this@MainActivity, 10f), Ui.dp(this@MainActivity, 6f),
                Ui.dp(this@MainActivity, 10f), Ui.dp(this@MainActivity, 6f))
            background = Ui.background(this@MainActivity, Ui.RAISED, 40)
        }

    /**
     * Opens the newest finished download. The single status slot is overwritten by every
     * concurrent job, so the target resolves from the last successful completion and then from
     * the newest gallery entry instead of trusting whichever job wrote the status last.
     */
    private fun openLastDownload() {
        val entry = try { newestDownload() }
        catch (error: IllegalStateException) {
            Toast.makeText(this, AppLanguage.error(this, error.message), Toast.LENGTH_LONG).show()
            return
        }
        if (entry == null) {
            val message = if (DownloadStatus.lastUri(this) != null)
                "La descarga no está disponible en la galería." else "Todavía no hay descargas para abrir."
            Toast.makeText(this, AppLanguage.label(this, message), Toast.LENGTH_LONG).show()
            return
        }
        DownloadStatus.dismiss(this)
        // The queue screen renders instead of the gallery while it is open, and the panel that owns
        // this action keeps offering it after the queue drains, so opening a work leaves the queue.
        showQueue = false
        tab = 2
        loadGalleryEntry(entry, 0)
    }

    private fun newestDownload(): GalleryStore.Entry? {
        val status = DownloadStatus.read(this)
        if (status != null && status.state == "complete" && !status.documentUri.isNullOrEmpty()) {
            clearCompletedWork(status)
        }
        importLastDownload()
        for (uri in listOfNotNull(status?.documentUri, DownloadStatus.lastUri(this))) {
            if (uri.isNullOrEmpty()) continue
            GalleryStore.find(this, uri)?.let { return it }
        }
        // A deleted or never imported entry must not break the action while other downloads exist.
        return GalleryStore.list(this).firstOrNull()
    }

    /**
     * Clears the preview and the link box once the work they show has finished. The queue finishes
     * in its own order and only the last status is kept, so the decision uses the work on screen
     * against the last queued work instead of whichever job wrote the status last.
     */
    private fun clearCompletedWork(completed: DownloadStatus): Boolean {
        if (completed.state != "complete" || completed.documentUri.isNullOrEmpty()) return false
        val prefs = getPreferences(MODE_PRIVATE)
        if (completed.documentUri == prefs.getString("last_cleared_download_uri", "")) return false
        val clearedPreview = PreviewClear.preview(prefs.getString("last_queued_work_id", ""), work?.id)
        if (clearedPreview) {
            work = null
            selectedPages = null
            pageChecks.clear()
        }
        val currentLink = if (tab == 0 && link != null) link!!.text.toString() else linkValue
        val clearedLink = PreviewClear.link(prefs.getString("last_queued_link", ""), currentLink)
        val save = prefs.edit().putString("last_cleared_download_uri", completed.documentUri)
        if (clearedLink) {
            linkValue = ""
            link?.setText("")
            save.remove("last_link")
        }
        save.apply()
        return clearedPreview && tab == 1
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.rawX
            swipeStartY = event.rawY
            swiping = false
            val area = if (galleryFullscreen) fullscreenMedia else scroll
            swipeEligible = area != null && (if (galleryFullscreen) (galleryItems?.size ?: 0) > 1 else galleryEntry == null)
                && containsTouch(area, event) && !touchesGestureControl(area, event)
        } else if (action == MotionEvent.ACTION_MOVE && swipeEligible && !swiping) {
            val dx = event.rawX - swipeStartX
            val dy = event.rawY - swipeStartY
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            if (kotlin.math.abs(dy) > slop && kotlin.math.abs(dy) > kotlin.math.abs(dx)) swipeEligible = false
            else if (kotlin.math.abs(dx) > maxOf(Ui.dp(this, 56f), slop * 3)
                && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                val direction = if (dx < 0) 1 else -1
                val canMove = if (galleryFullscreen) galleryIndex + direction >= 0 && galleryIndex + direction < (galleryItems?.size ?: 0)
                    else tab + direction in 0..3
                if (canMove) {
                    swiping = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                } else swipeEligible = false
            }
        }
        if (swiping) {
            if (action == MotionEvent.ACTION_UP) {
                val direction = if (event.rawX < swipeStartX) 1 else -1
                if (galleryFullscreen) selectGalleryItem(galleryIndex + direction) else switchTab(tab + direction)
                swiping = false
                swipeEligible = false
            } else if (action == MotionEvent.ACTION_CANCEL) {
                swiping = false
                swipeEligible = false
            }
            return true
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swipeEligible = false
        return super.dispatchTouchEvent(event)
    }

    private fun containsTouch(view: View, event: MotionEvent): Boolean {
        val rect = Rect()
        return view.isShown && view.getGlobalVisibleRect(rect) && rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun touchesGestureControl(view: View, event: MotionEvent): Boolean {
        if (!containsTouch(view, event)) return false
        if (view is HorizontalScrollView || view is EditText || view is VideoView ||
            !galleryFullscreen && view is TextView && view.isTextSelectable) return true
        if (view is android.view.ViewGroup) {
            for (i in view.childCount - 1 downTo 0) if (touchesGestureControl(view.getChildAt(i), event)) return true
        }
        return false
    }

    private fun switchTab(target: Int) {
        fadeNextTab = target != tab
        showQueue = false
        link?.let { linkValue = it.text.toString() }
        syncSelection()
        galleryEntry = null
        galleryItems = null
        galleryFullscreen = false
        tab = target
        render()
    }

    private fun gallery() {
        if (galleryEntry != null) { galleryDetail(); return }
        content.addView(Ui.text(this, "Tu galería", 26, Ui.WHITE, true))
        Ui.gap(content, this, 6)
        content.addView(Ui.text(this, "Tus obras descargadas de Pixiv, reunidas en un solo lugar.", 14, Ui.MUTED, false))
        Ui.gap(content, this, 20)
        val cached = galleryCache
        if (cached != null) {
            galleryEntries(cached)
            restoreGalleryScroll()
            return
        }
        val error = galleryCacheError
        if (error != null) {
            content.addView(Ui.text(this, error, 14, Ui.MUTED, false))
            return
        }
        content.addView(Skeletons.gallery(this))
        validateGallery()
    }

    /** Validates the saved history once per app launch; later visits render from memory. */
    private fun validateGallery() {
        if (galleryCacheLoading) return
        galleryCacheLoading = true
        galleryIO.execute {
            var entries: List<GalleryStore.Entry>? = null
            var failure: String? = null
            try {
                entries = GalleryStore.list(this).filter { GalleryDocuments.isAvailable(contentResolver, it) }
            } catch (error: IllegalStateException) {
                failure = AppLanguage.error(this, error.message)
                    ?: AppLanguage.label(this, "No se pudo leer el historial de descargas.")
                    ?: "No se pudo leer el historial de descargas."
            }
            val result = entries
            runOnUiThread {
                galleryCacheLoading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                galleryCache = result
                galleryCacheError = failure
                if (tab == 2 && galleryEntry == null) render()
            }
        }
    }

    private fun restoreGalleryScroll() {
        galleryListRendered = true
        galleryScrollPending = true
        scroll?.post {
            scroll?.scrollTo(0, galleryScrollY)
            galleryScrollPending = false
            galleryImages.loadVisible()
        }
    }

    private fun replaceCachedEntry(entry: GalleryStore.Entry) {
        val cached = galleryCache ?: return
        galleryCache = cached.map { if (it.uri == entry.uri) entry else it }
    }

    private fun galleryEntries(entries: List<GalleryStore.Entry>) {
        if (entries.isEmpty()) {
            val empty = Ui.card(this, 18)
            empty.addView(Ui.label(this, "Aún no hay descargas", R.drawable.tabler_layout_grid, 18, Ui.CYAN))
            Ui.gap(empty, this, 9)
            empty.addView(Ui.text(this, "Las próximas obras que descargues aparecerán aquí.", 14, Ui.MUTED, false))
            Ui.gap(empty, this, 12)
            empty.addView(Ui.button(this, "Buscar una obra", R.drawable.tabler_search, true, Runnable { switchTab(0) }))
            content.addView(empty)
            return
        }
        content.addView(Ui.text(this, entries.size.toString() + AppLanguage.label(this, if (entries.size == 1) " descarga" else " descargas"), 14, Ui.CYAN, true))
        Ui.gap(content, this, 12)
        for (i in entries.indices step 2) {
            val row = Ui.row(this)
            row.gravity = Gravity.TOP
            for (j in i until minOf(i + 2, entries.size)) {
                val entry = entries[j]
                val card = Ui.card(this, 8)
                val thumb = galleryImages.tile(entry.cover, entry.mime, 140)
                val cover = FrameLayout(this)
                cover.addView(thumb, FrameLayout.LayoutParams(-1, -1))
                val format = Ui.text(this, galleryFormat(entry.folder, entry.mime), 11, Ui.WHITE, true)
                format.text = format.text.toString().uppercase(Locale.forLanguageTag(AppLanguage.current(this)))
                format.setPadding(Ui.dp(this, 7f), Ui.dp(this, 3f), Ui.dp(this, 7f), Ui.dp(this, 3f))
                format.background = Ui.background(this, Ui.INPUT, 6)
                val badge = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
                badge.setMargins(0, Ui.dp(this, 8f), Ui.dp(this, 8f), 0)
                cover.addView(format, badge)
                card.addView(cover, LinearLayout.LayoutParams(-1, Ui.dp(this, 140f)))
                Ui.gap(card, this, 8)
                val name = Ui.rawText(this, entry.title, 14, Ui.WHITE, true)
                name.maxLines = 1
                name.ellipsize = android.text.TextUtils.TruncateAt.END
                card.addView(name)
                Ui.gap(card, this, 4)
                card.addView(Ui.text(this, if (entry.date > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT, Locale.forLanguageTag(AppLanguage.current(this))).format(Date(entry.date)) else "Fecha no registrada", 11, Ui.MUTED, false))
                val count = Ui.text(this, if (entry.folder) {
                    if (entry.count >= 0) entry.count.toString() + AppLanguage.label(this, " elementos") else ""
                } else AppLanguage.label(this, "1 archivo")!!, 11, Ui.CYAN, true)
                card.addView(count)
                if (entry.folder && entry.count < 0) {
                    count.visibility = View.GONE
                    val countSkeleton = Skeletons.block(this, 12, 64)
                    card.addView(countSkeleton)
                    galleryIO.execute {
                        try {
                            val files = GalleryDocuments.list(contentResolver, entry)
                            val updated = GalleryStore.updateMetadata(this, entry, files.size, if (files.isEmpty()) "" else files[0].uri.toString())
                            runOnUiThread {
                                replaceCachedEntry(updated)
                                if (tab == 2 && galleryEntry == null && count.isAttachedToWindow) render()
                            }
                        } catch (_: Exception) { runOnUiThread {
                            card.removeView(countSkeleton)
                            count.visibility = View.VISIBLE
                            count.text = AppLanguage.label(this, "Carpeta no disponible")
                        } }
                    }
                }
                Ui.gap(card, this, 7)
                val actions = Ui.row(this)
                actions.gravity = Gravity.END
                val share = Ui.plainIconAction(this, R.drawable.tabler_share, Runnable { shareGalleryEntry(entry) })
                share.contentDescription = AppLanguage.label(this, "Compartir ") + entry.title
                actions.addView(share, LinearLayout.LayoutParams(Ui.dp(this, 44f), Ui.dp(this, 44f)))
                val delete = Ui.plainIconAction(this, R.drawable.tabler_trash, Ui.DANGER, Runnable { confirmDelete(entry) })
                delete.contentDescription = AppLanguage.label(this, "Eliminar ") + entry.title
                actions.addView(delete, LinearLayout.LayoutParams(Ui.dp(this, 44f), Ui.dp(this, 44f)))
                card.addView(actions)
                card.setOnClickListener { loadGalleryEntry(entry, 0) }
                val params = LinearLayout.LayoutParams(0, -2, 1f)
                if (j == i) params.rightMargin = Ui.dp(this, 8f)
                row.addView(card, params)
            }
            content.addView(row)
            Ui.gap(content, this, 8)
        }
    }

    private fun loadGalleryEntry(entry: GalleryStore.Entry, index: Int) {
        if (galleryListRendered && !galleryScrollPending && scroll != null) galleryScrollY = scroll!!.scrollY
        galleryEntry = entry
        galleryItems = null
        galleryLoading = true
        galleryError = null
        galleryIndex = index
        tab = 2
        render()
        galleryIO.execute {
            try {
                val files = GalleryDocuments.list(contentResolver, entry)
                var updated = entry
                if (entry.count != files.size || (entry.folder && entry.cover.isEmpty() && files.isNotEmpty())) {
                    updated = GalleryStore.updateMetadata(this, entry, files.size, if (files.isEmpty()) "" else files[0].uri.toString())
                }
                val loaded = updated
                runOnUiThread {
                    if (isFinishing || isDestroyed || galleryEntry?.uri != entry.uri) return@runOnUiThread
                    galleryEntry = loaded
                    galleryItems = files
                    replaceCachedEntry(loaded)
                    galleryIndex = minOf(galleryIndex, maxOf(0, files.size - 1))
                    galleryLoading = false
                    render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (galleryEntry?.uri != entry.uri) return@runOnUiThread
                    galleryLoading = false
                    galleryError = AppLanguage.label(this, "No se pudo acceder a la descarga: ") + AppLanguage.error(this, error.message)
                    render()
                }
            }
        }
    }

    private fun fullscreenGallery() {
        val toolbar = Ui.row(this)
        toolbar.setPadding(Ui.dp(this, 12f), Ui.dp(this, 8f), Ui.dp(this, 12f), Ui.dp(this, 8f))
        val items = galleryItems
        val title = if (items != null && items.isNotEmpty()) items[galleryIndex].name else galleryEntry!!.title
        val heading = Ui.rawText(this, title!!, 16, Ui.WHITE, true)
        heading.isSingleLine = true
        heading.ellipsize = android.text.TextUtils.TruncateAt.END
        val headingParams = LinearLayout.LayoutParams(0, -2, 1f)
        headingParams.leftMargin = Ui.dp(this, 12f)
        toolbar.addView(heading, headingParams)
        val close = Ui.plainIconAction(this, R.drawable.tabler_x, Runnable { galleryFullscreen = false; render() })
        close.contentDescription = AppLanguage.label(this, "Salir de pantalla completa")
        toolbar.addView(close, LinearLayout.LayoutParams(Ui.dp(this, 48f), Ui.dp(this, 48f)))
        root.addView(toolbar)
        fullscreenMedia = null
        if (galleryLoading) {
            root.addView(LoadingSkeleton(this), LinearLayout.LayoutParams(-1, 0, 1f))
            return
        }
        if (items == null || items.isEmpty() || galleryError != null) {
            val state = Ui.text(this, galleryError ?: "Esta carpeta está vacía.", 15, Ui.MUTED, false)
            state.gravity = Gravity.CENTER
            root.addView(state, LinearLayout.LayoutParams(-1, 0, 1f))
            return
        }
        val item = items[galleryIndex]
        val text = (item.mime?.startsWith("text/") == true) || item.name!!.lowercase(Locale.ROOT).endsWith(".txt")
        val viewer = Ui.column(this)
        if (text) {
            val reader = ScrollView(this)
            reader.isFillViewport = true
            reader.setPadding(Ui.dp(this, 12f), 0, Ui.dp(this, 12f), 0)
            reader.addView(viewer)
            root.addView(reader, LinearLayout.LayoutParams(-1, 0, 1f))
            fullscreenMedia = reader
        } else {
            root.addView(viewer, LinearLayout.LayoutParams(-1, 0, 1f))
            fullscreenMedia = viewer
        }
        galleryVideo = GalleryViewer.show(this, viewer, item, galleryIO, -1)
        if (items.size > 1) {
            val counter = Ui.text(this, "${galleryIndex + 1} / ${items.size}", 13, Ui.CYAN, true)
            counter.gravity = Gravity.CENTER
            root.addView(counter)
            val strip = HorizontalScrollView(this)
            strip.isHorizontalScrollBarEnabled = false
            strip.isFillViewport = false
            val thumbnails = Ui.row(this)
            thumbnails.setPadding(Ui.dp(this, 12f), Ui.dp(this, 8f), Ui.dp(this, 12f), Ui.dp(this, 12f))
            strip.addView(thumbnails)
            var selectedTile: View? = null
            for (i in items.indices) {
                val child = items[i]
                val tile = Ui.column(this)
                tile.setPadding(Ui.dp(this, 3f), Ui.dp(this, 3f), Ui.dp(this, 3f), Ui.dp(this, 3f))
                tile.background = Ui.outlined(this, if (i == galleryIndex) Ui.BLUE else Ui.PANEL, 10)
                tile.addView(galleryImages.tile(child.uri.toString(), child.mime, 72),
                    LinearLayout.LayoutParams(Ui.dp(this, 72f), Ui.dp(this, 72f)))
                tile.contentDescription = AppLanguage.label(this, "Archivo ") + (i + 1) + ": " + child.name
                tile.setOnClickListener { selectGalleryItem(i) }
                val params = LinearLayout.LayoutParams(-2, -2)
                params.rightMargin = Ui.dp(this, 8f)
                thumbnails.addView(tile, params)
                if (i == galleryIndex) selectedTile = tile
            }
            root.addView(strip)
            val centered = selectedTile!!
            strip.post {
                if (galleryFullscreen && strip.isAttachedToWindow) {
                    strip.scrollTo(maxOf(0, centered.left - (strip.width - centered.width) / 2), 0)
                    galleryImages.loadVisible()
                }
            }
            strip.setOnScrollChangeListener { _, _, _, _, _ -> galleryImages.loadVisible() }
        }
    }

    private fun galleryDetail() {
        val entry = galleryEntry!!
        val back = Ui.text(this, "Volver a la galería", 14, Ui.CYAN, true)
        Ui.withIcon(back, R.drawable.tabler_arrow_left, Ui.CYAN)
        back.setOnClickListener { returnToGallery() }
        content.addView(back)
        Ui.gap(content, this, 16)
        content.addView(Ui.rawText(this, entry.title, 23, Ui.WHITE, true))
        Ui.gap(content, this, 6)
        var detail = if (entry.date > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
            Locale.forLanguageTag(AppLanguage.current(this))).format(Date(entry.date)) else "Fecha no registrada"
        detail += "  ·  " + (if (entry.folder) {
            if (entry.count >= 0) entry.count.toString() + AppLanguage.label(this, " elementos") else AppLanguage.label(this, "Carpeta")
        } else AppLanguage.label(this, "1 archivo"))
        content.addView(Ui.text(this, detail, 13, Ui.MUTED, false))
        Ui.gap(content, this, 16)
        val items = galleryItems
        if (galleryLoading) {
            content.addView(Skeletons.detail(this))
        } else if (galleryError != null) {
            content.addView(Ui.text(this, galleryError!!, 14, Ui.MUTED, false))
        } else if (items == null || items.isEmpty()) {
            content.addView(Ui.text(this, "Esta carpeta está vacía.", 14, Ui.MUTED, false))
        } else {
            val item = items[galleryIndex]
            val viewer = Ui.card(this, 12)
            val viewerHeader = Ui.row(this)
            val filename = Ui.rawText(this, item.name!!, 14, Ui.WHITE, true)
            filename.isSingleLine = true
            filename.ellipsize = android.text.TextUtils.TruncateAt.END
            viewerHeader.addView(filename, LinearLayout.LayoutParams(0, -2, 1f))
            val fullscreen = Ui.plainIconAction(this, R.drawable.tabler_maximize, Runnable { galleryFullscreen = true; render() })
            fullscreen.contentDescription = AppLanguage.label(this, "Ver en pantalla completa")
            val fullscreenParams = LinearLayout.LayoutParams(Ui.dp(this, 44f), Ui.dp(this, 44f))
            fullscreenParams.leftMargin = Ui.dp(this, 8f)
            viewerHeader.addView(fullscreen, fullscreenParams)
            viewer.addView(viewerHeader)
            Ui.gap(viewer, this, 10)
            galleryVideo = GalleryViewer.show(this, viewer, item, galleryIO)
            content.addView(viewer)
            if (items.size > 1) {
                Ui.gap(content, this, 12)
                val controls = Ui.row(this)
                val previous = Ui.iconAction(this, R.drawable.tabler_chevron_left, Runnable { selectGalleryItem(galleryIndex - 1) })
                previous.contentDescription = AppLanguage.label(this, "Archivo anterior")
                previous.isEnabled = galleryIndex > 0
                previous.alpha = if (galleryIndex > 0) 1f else .35f
                controls.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
                val position = Ui.text(this, "${galleryIndex + 1} / ${items.size}", 14, Ui.CYAN, true)
                position.gravity = Gravity.CENTER
                controls.addView(position, LinearLayout.LayoutParams(0, -2, 1f))
                val next = Ui.iconAction(this, R.drawable.tabler_chevron_right, Runnable { selectGalleryItem(galleryIndex + 1) })
                next.contentDescription = AppLanguage.label(this, "Archivo siguiente")
                next.isEnabled = galleryIndex + 1 < items.size
                next.alpha = if (galleryIndex + 1 < items.size) 1f else .35f
                controls.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(controls)
                Ui.gap(content, this, 16)
                content.addView(Ui.text(this, "Contenido de la carpeta", 17, Ui.WHITE, true))
                Ui.gap(content, this, 10)
                for (i in items.indices step 2) {
                    val row = Ui.row(this)
                    row.gravity = Gravity.TOP
                    for (j in i until minOf(i + 2, items.size)) {
                        val child = items[j]
                        val card = Ui.card(this, 7)
                        card.addView(galleryImages.tile(child.uri.toString(), child.mime, 110),
                            LinearLayout.LayoutParams(-1, Ui.dp(this, 110f)))
                        val name = Ui.rawText(this, child.name!!, 12, if (j == galleryIndex) Ui.CYAN else Ui.MUTED, j == galleryIndex)
                        name.isSingleLine = true
                        name.ellipsize = android.text.TextUtils.TruncateAt.END
                        card.addView(name)
                        card.setOnClickListener { selectGalleryItem(j) }
                        val params = LinearLayout.LayoutParams(0, -2, 1f)
                        if (j == i) params.rightMargin = Ui.dp(this, 8f)
                        row.addView(card, params)
                    }
                    content.addView(row)
                    Ui.gap(content, this, 8)
                }
            }
        }
        Ui.gap(content, this, 18)
        content.addView(Ui.dangerButton(this, "Eliminar descarga", R.drawable.tabler_trash, Runnable { confirmDelete(entry) }))
    }

    private fun returnToGallery() {
        galleryFullscreen = false
        galleryEntry = null
        galleryItems = null
        render()
    }

    private fun selectGalleryItem(index: Int) {
        if (galleryItems == null || index < 0 || index >= galleryItems!!.size) return
        galleryIndex = index
        render()
    }

    private fun importLastDownload() {
        try { GalleryStore.importLast(this) }
        catch (_: IllegalStateException) { /* The gallery displays the storage error instead of replacing history. */ return }
        syncImportedGallery()
    }

    /**
     * Picks up downloads completed while the echoed listing is cached. Only entries Android can
     * still resolve are added, so history hidden by the first validation does not come back.
     */
    private fun syncImportedGallery() {
        val cached = galleryCache ?: return
        val known = cached.mapTo(HashSet()) { it.uri }
        galleryIO.execute {
            val added = try {
                GalleryStore.list(this).filter { it.uri !in known && GalleryDocuments.isAvailable(contentResolver, it) }
            } catch (_: IllegalStateException) { return@execute }
            if (added.isEmpty()) return@execute
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val current = galleryCache ?: return@runOnUiThread
                val latest = current.mapTo(HashSet()) { it.uri }
                val fresh = added.filter { it.uri !in latest }
                if (fresh.isEmpty()) return@runOnUiThread
                galleryCache = fresh + current
                if (tab == 2 && galleryEntry == null) render()
            }
        }
    }

    private fun shareGalleryEntry(entry: GalleryStore.Entry) {
        galleryIO.execute {
            val send: Intent
            try {
                val items = GalleryDocuments.list(contentResolver, entry)
                send = GalleryShare.intent(items, AppLanguage.label(this, "Descarga de Pixload")!!)
            } catch (error: Exception) {
                val message = AppLanguage.label(this, "No se pudo compartir la descarga: ") + AppLanguage.error(this, error.message)
                runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                try {
                    val chooser = Intent.createChooser(send, AppLanguage.label(this, "Compartir descarga"))
                    chooser.clipData = send.clipData
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                } catch (_: Exception) {
                    Toast.makeText(this, AppLanguage.label(this, "No hay ninguna aplicación disponible para compartir estos archivos."),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete(entry: GalleryStore.Entry) {
        val dialog = AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(AppLanguage.label(this, "Eliminar descarga"))
            .setMessage(AppLanguage.label(this, "Se eliminará «") + entry.title + AppLanguage.label(this, "» y sus archivos del almacenamiento. Esta acción no se puede deshacer."))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(AppLanguage.label(this, "Eliminar")) { _, _ ->
                galleryIO.execute {
                    var failure: String? = null
                    try {
                        if (!DocumentsContract.deleteDocument(contentResolver, Uri.parse(entry.uri)))
                            throw Exception("Android no permitió eliminar el archivo o la carpeta.")
                        DownloadStatus.forget(this, entry.uri)
                        GalleryStore.remove(this, entry.uri)
                    } catch (error: Exception) {
                        failure = AppLanguage.label(this, "No se pudo eliminar: ") + AppLanguage.error(this, error.message)
                    }
                    val result = failure
                    runOnUiThread {
                        if (result == null) {
                            galleryCache = galleryCache?.filterNot { it.uri == entry.uri }
                            if (galleryEntry?.uri == entry.uri) {
                                galleryEntry = null
                                galleryItems = null
                                galleryFullscreen = false
                            }
                            if (tab == 2) render()
                            refreshProgress()
                            Toast.makeText(this, AppLanguage.label(this, "Descarga eliminada"), Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
            }.create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.DANGER) }
        dialog.show()
    }

    private fun home() {
        content.addView(Ui.text(this, "Tus obras favoritas,\na tu manera.", 29, Ui.WHITE, true))
        Ui.gap(content, this, 8)
        content.addView(Ui.text(this, "Guarda ilustraciones, manga, novelas y ugoira de Pixiv.", 15, Ui.MUTED, false))
        Ui.gap(content, this, 22)
        val chips = Ui.row(this)
        for (chip in arrayOf("Ilustraciones", "Manga", "Ugoira", "Novelas")) {
            val badge = Ui.text(this, chip, 11, Ui.CYAN, true)
            badge.gravity = Gravity.CENTER
            badge.setPadding(Ui.dp(this, 7f), Ui.dp(this, 9f), Ui.dp(this, 7f), Ui.dp(this, 9f))
            badge.background = Ui.background(this, Ui.PANEL, 28)
            val params = LinearLayout.LayoutParams(0, -2, 1f)
            params.rightMargin = Ui.dp(this, 4f)
            chips.addView(badge, params)
        }
        content.addView(chips)
        Ui.gap(content, this, 24)
        val hub = Ui.card(this, 16)
        val field = Ui.row(this)
        field.setPadding(Ui.dp(this, 12f), 0, Ui.dp(this, 7f), 0)
        field.background = Ui.background(this, Ui.INPUT, 14)
        field.addView(Ui.icon(this, R.drawable.tabler_link, Ui.CYAN),
            LinearLayout.LayoutParams(Ui.dp(this, 24f), Ui.dp(this, 24f)))
        val linkField = EditText(this)
        link = linkField
        linkField.isSingleLine = true
        linkField.setTextColor(Ui.WHITE)
        linkField.setHintTextColor(Ui.MUTED)
        linkField.textSize = 13f
        linkField.hint = AppLanguage.label(this, "Pega la URL de pixiv aquí")
        linkField.setBackgroundColor(Color.TRANSPARENT)
        linkField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        linkField.typeface = Ui.typeface(this, false)
        linkField.setText(linkValue)
        val edit = LinearLayout.LayoutParams(0, Ui.dp(this, 56f), 1f)
        edit.leftMargin = Ui.dp(this, 8f)
        field.addView(linkField, edit)
        val clear = Ui.icon(this, R.drawable.tabler_x, Ui.MUTED)
        clear.contentDescription = AppLanguage.label(this, "Limpiar URL")
        clear.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        clear.setOnClickListener {
            linkField.setText("")
            linkValue = ""
            getPreferences(MODE_PRIVATE).edit().remove("last_link").apply()
        }
        clear.visibility = if (linkValue.isEmpty()) View.GONE else View.VISIBLE
        linkField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                clear.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(text: Editable?) {}
        })
        field.addView(clear, LinearLayout.LayoutParams(Ui.dp(this, 32f), Ui.dp(this, 44f)))
        val paste = Ui.icon(this, R.drawable.tabler_clipboard, Ui.CYAN)
        paste.contentDescription = AppLanguage.label(this, "Pegar URL del portapapeles")
        paste.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        paste.setPadding(Ui.dp(this, 9f), Ui.dp(this, 9f), Ui.dp(this, 9f), Ui.dp(this, 9f))
        paste.background = Ui.background(this, Ui.RAISED, 30)
        paste.setOnClickListener { paste() }
        field.addView(paste, LinearLayout.LayoutParams(Ui.dp(this, 38f), Ui.dp(this, 38f)))
        hub.addView(field)
        Ui.gap(hub, this, 12)
        val open = Ui.button(this, if (loading) "Consultando Pixiv…" else "Previsualizar obra",
            R.drawable.tabler_photo_edit, true, Runnable { load() })
        open.isEnabled = !loading
        hub.addView(open)
        Ui.gap(hub, this, 12)
        val notice = Ui.text(this, "", 13, Ui.WARNING, false)
        status = notice
        hub.addView(notice)
        applyPreviewNotice()
        content.addView(hub)
        Ui.gap(content, this, 22)
        content.addView(Ui.text(this, "Hecho para tus obras", 19, Ui.WHITE, true))
        Ui.gap(content, this, 12)
        feature(R.drawable.tabler_photo, "Imágenes originales", "Selecciona las páginas que quieras descargar.")
        Ui.gap(content, this, 10)
        feature(R.drawable.tabler_movie, "GIF o MP4", "Convierte las ugoira al formato que prefieras.")
        Ui.gap(content, this, 10)
        feature(R.drawable.tabler_file_text, "Novelas en TXT", "Guarda el texto proporcionado por Pixiv.")
        Ui.gap(content, this, 20)
        content.addView(Ui.text(this, "Las descargas continúan en segundo plano. Consulta la notificación para ver su progreso o cancelarlas.", 13, Ui.MUTED, false))
    }

    private fun feature(icon: Int, title: String, detail: String) {
        val card = Ui.card(this, 14)
        val row = Ui.row(this)
        val glyph = Ui.icon(this, icon, Ui.CYAN)
        row.addView(glyph, LinearLayout.LayoutParams(Ui.dp(this, 38f), Ui.dp(this, 38f)))
        val description = Ui.column(this)
        description.addView(Ui.text(this, title, 15, Ui.WHITE, true))
        description.addView(Ui.text(this, detail, 12, Ui.MUTED, false))
        val params = LinearLayout.LayoutParams(0, -2, 1f)
        params.leftMargin = Ui.dp(this, 10f)
        row.addView(description, params)
        card.addView(row)
        content.addView(card)
    }

    /**
     * Keeps the feedback line under the preview button empty unless the preview flow produced a
     * warning or an error, and colors it by that type.
     */
    private fun setPreviewNotice(text: String?, warning: Boolean) {
        previewNotice = text
        previewNoticeWarning = warning
        applyPreviewNotice()
    }

    private fun applyPreviewNotice() {
        val view = status ?: return
        val note = previewNotice
        if (note.isNullOrEmpty()) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.setTextColor(if (previewNoticeWarning) Ui.WARNING else Ui.DANGER)
        view.text = note
    }

    private fun paste() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val data: ClipData? = clipboard?.primaryClip
        if (data == null || data.itemCount == 0) {
            setPreviewNotice(AppLanguage.label(this, "El portapapeles está vacío."), true)
            return
        }
        val value = data.getItemAt(0).coerceToText(this)
        val url = PixivLink.extract(value?.toString())
        if (url == null) {
            setPreviewNotice(AppLanguage.label(this, "El portapapeles no contiene un enlace de Pixiv."), true)
            return
        }
        link?.setText(url)
        link?.setSelection(link!!.length())
    }

    private fun load() {
        if (loading) return
        link?.let { linkValue = it.text.toString().trim() }
        val extracted = PixivLink.extract(linkValue)
        if (extracted != null && extracted != linkValue) {
            linkValue = extracted
            link?.setText(extracted)
        }
        if (linkValue.isEmpty()) {
            setPreviewNotice(AppLanguage.label(this, "Introduce un enlace de obra de Pixiv."), true)
            return
        }
        loading = true
        work = null
        mp4 = defaultMp4
        selectedPages = null
        setPreviewNotice(null, true)
        getPreferences(MODE_PRIVATE).edit().putString("last_link", linkValue).apply()
        val keyboard = getSystemService(InputMethodManager::class.java)
        if (keyboard != null && link != null) keyboard.hideSoftInputFromWindow(link!!.windowToken, 0)
        render()
        val input = linkValue
        executor.execute {
            try {
                val found = PixivClient.load(input)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    loading = false
                    work = found
                    tab = 1
                    render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    loading = false
                    setPreviewNotice(AppLanguage.label(this, "No se pudo abrir la obra: ") + AppLanguage.error(this, error.message), false)
                    tab = 0
                    render()
                }
            }
        }
    }

    private fun preview() {
        pageChecks.clear()
        val work = work
        if (loading) {
            content.addView(Skeletons.work(this))
            return
        }
        if (work == null) {
            content.addView(Ui.text(this, "Vista previa", 26, Ui.WHITE, true))
            Ui.gap(content, this, 18)
            val empty = Ui.card(this, 20)
            empty.addView(Ui.label(this, "No hay una obra cargada", R.drawable.tabler_photo_off, 18, Ui.CYAN))
            Ui.gap(empty, this, 9)
            empty.addView(Ui.text(this, "Introduce un enlace de Pixiv para ver sus detalles y elegir qué descargar.", 14, Ui.MUTED, false))
            Ui.gap(empty, this, 16)
            empty.addView(Ui.button(this, "Ir a descargar", R.drawable.tabler_download, true, Runnable { switchTab(0) }))
            content.addView(empty)
            return
        }
        content.addView(Ui.label(this, "OBRA ENCONTRADA", R.drawable.tabler_circle_check, 12, Ui.GREEN))
        Ui.gap(content, this, 12)
        val summary = Ui.card(this, 16)
        summary.addView(Ui.text(this, kind() + "  ·  Pixiv #" + work.id, 12, Ui.CYAN, true))
        Ui.gap(summary, this, 10)
        summary.addView(Ui.rawText(this, work.title!!, 22, Ui.WHITE, true))
        Ui.gap(summary, this, 4)
        summary.addView(Ui.text(this, AppLanguage.label(this, "Por ") + work.author, 14, Ui.MUTED, false))
        if (!work.thumbnail.isNullOrEmpty()) {
            Ui.gap(summary, this, 13)
            val cover = images.view(work.thumbnail!!, 190)
            summary.addView(cover, LinearLayout.LayoutParams(-1, Ui.dp(this, 190f)))
        }
        content.addView(summary)
        Ui.gap(content, this, 20)
        when (work.type) {
            "image" -> imagePages()
            "ugoira" -> animation()
            else -> novel()
        }
        Ui.gap(content, this, 20)
        content.addView(Ui.text(this, "La carpeta y el nombre final se determinarán al guardar. Algunas obras pueden requerir una sesión autorizada en Pixiv.", 12, Ui.MUTED, false))
        downloadBar()
    }

    private fun kind(): String {
        val work = work!!
        if (work.type == "novel") return AppLanguage.label(this, "Novela")!!
        if (work.type == "ugoira") return "Ugoira"
        return AppLanguage.label(this, if (work.pages.size > 1) "Ilustración / manga" else "Ilustración")!!
    }

    /** Queue rows name manga explicitly instead of sharing the preview's combined label. */
    private fun queueKind(work: PixivClient.Work): String = when {
        work.type == "novel" -> "novel"
        work.type == "ugoira" -> "ugoira"
        work.pages.size > 1 -> "manga"
        else -> "illustration"
    }

    private fun imagePages() {
        val work = work!!
        val count = work.pages.size
        val label = Ui.row(this)
        val heading = Ui.text(this, AppLanguage.label(this, "Páginas (") + count + ")", 18, Ui.WHITE, true)
        label.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val all = Ui.text(this, "Deseleccionar todas", 12, Ui.CYAN, true)
        all.setOnClickListener {
            var everything = true
            for (check in pageChecks) everything = everything && check.isChecked
            for (check in pageChecks) check.isChecked = !everything
            all.text = AppLanguage.label(this, if (everything) "Seleccionar todas" else "Deseleccionar todas")
        }
        label.addView(all)
        content.addView(label)
        Ui.gap(content, this, 12)
        for (index in 0 until count step 2) {
            val row = Ui.row(this)
            for (j in index until minOf(index + 2, count)) {
                val card = Ui.card(this, 8)
                val url = if (j < work.pageThumbnails.size) work.pageThumbnails[j] else ""
                val thumb = images.view(url, 152)
                card.addView(thumb, LinearLayout.LayoutParams(-1, Ui.dp(this, 152f)))
                val check = CheckBox(this)
                check.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.CYAN)
                check.text = AppLanguage.label(this, "Página ") + (j + 1)
                check.textSize = 13f
                check.setTextColor(Ui.WHITE)
                check.typeface = Ui.typeface(this, false)
                val selected = selectedPages
                check.isChecked = selected == null || j >= selected.size || selected[j]
                check.setOnCheckedChangeListener { _, _ -> syncSelection(); updateSelection() }
                card.setOnClickListener { check.isChecked = !check.isChecked }
                pageChecks.add(check)
                card.addView(check)
                val params = LinearLayout.LayoutParams(0, -2, 1f)
                if (j == index) params.rightMargin = Ui.dp(this, 8f)
                row.addView(card, params)
            }
            content.addView(row)
            Ui.gap(content, this, 8)
        }
    }

    private fun animation() {
        content.addView(Ui.text(this, "Formato de animación", 18, Ui.WHITE, true))
        Ui.gap(content, this, 10)
        val formats = Ui.row(this)
        for (i in 0 until 2) {
            val chooseMp4 = i == 1
            val option = Ui.button(this, if (chooseMp4) "MP4" else "GIF", chooseMp4 == mp4,
                Runnable { mp4 = chooseMp4; render() })
            if (chooseMp4 == mp4) Ui.withIcon(option, R.drawable.tabler_check, Ui.INPUT)
            val params = LinearLayout.LayoutParams(0, -2, 1f)
            if (i == 0) params.rightMargin = Ui.dp(this, 8f)
            formats.addView(option, params)
        }
        content.addView(formats)
        Ui.gap(content, this, 10)
        content.addView(Ui.text(this, "MP4 no incluye sonido. La conversión puede reducir el tamaño o la fidelidad de la animación.", 13, Ui.MUTED, false))
        if (!mp4) {
            Ui.gap(content, this, 14)
            content.addView(Ui.text(this, "Calidad del GIF", 16, Ui.WHITE, true))
            Ui.gap(content, this, 8)
            val presets = Ui.row(this)
            for (choice in GifPreset.values()) {
                val title = if (choice == GifPreset.FAST) "Rápido" else if (choice == GifPreset.BALANCED) "Equilibrado" else "Calidad"
                val button = Ui.button(this, title, choice == gifPreset, Runnable {
                    gifPreset = choice
                    getPreferences(MODE_PRIVATE).edit().putString("gif_preset", choice.id).apply()
                    render()
                })
                val params = LinearLayout.LayoutParams(0, -2, 1f)
                params.rightMargin = Ui.dp(this, 6f)
                presets.addView(button, params)
            }
            content.addView(presets)
            Ui.gap(content, this, 6)
            content.addView(Ui.text(this, AppLanguage.label(this, "Hasta ") + gifPreset.maxWidth + " px · " + gifPreset.fpsMax
                + " fps · " + AppLanguage.label(this, if (gifPreset.diffuse) "difusión de error" else "sin dithering"), 12, Ui.MUTED, false))
        }
    }

    private fun novel() {
        val work = work!!
        content.addView(Ui.text(this, "Texto de la novela", 18, Ui.WHITE, true))
        Ui.gap(content, this, 10)
        val card = Ui.card(this, 16)
        val text = work.text!!
        val excerpt = if (text.length > 600) text.substring(0, 600) + "…" else text
        card.addView(Ui.rawText(this, excerpt, 14, Ui.WHITE, false))
        content.addView(card)
        Ui.gap(content, this, 10)
        content.addView(Ui.text(this, "Formato: TXT · texto proporcionado por Pixiv", 12, Ui.CYAN, false))
    }

    private fun downloadBar() {
        val bar = Ui.card(this, 12)
        selectionCount = Ui.text(this, "", 14, Ui.WHITE, true)
        bar.addView(selectionCount)
        Ui.gap(bar, this, 9)
        selectionDownloadButton = Ui.button(this, "Descargar selección", R.drawable.tabler_download, true, Runnable { download() })
        bar.addView(selectionDownloadButton)
        updateDownloadButton()
        bottom.addView(bar)
        Ui.gap(bottom, this, 8)
        updateSelection()
    }

    private fun updateDownloadButton() {
        val button = selectionDownloadButton ?: return
        val current = work
        val pendingWork = current != null && isWorkPending(current)
        val full = DownloadQueue.isFull(this)
        val enabled = !full && !pendingWork
        val label = when {
            pendingWork -> "Descargando..."
            full -> "Cola llena"
            else -> "Descargar selección"
        }
        val expected = AppLanguage.label(this, label)
        if (button.isEnabled == enabled && button.text.toString() == expected) return
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else .55f
        button.text = expected
        if (!enabled) button.contentDescription = expected
        Ui.withIcon(button, if (full) R.drawable.tabler_transfer_vertical else R.drawable.tabler_download, Ui.INPUT)
    }

    /** The same work may not be queued twice while the first copy is still pending. */
    private fun isWorkPending(target: PixivClient.Work): Boolean {
        val id = target.id ?: return false
        if (id.isEmpty()) return false
        return DownloadQueue.pending(this).any { it.id == id }
    }

    private fun syncSelection() {
        if (pageChecks.isEmpty()) return
        selectedPages = BooleanArray(pageChecks.size) { pageChecks[it].isChecked }
    }

    private fun updateSelection() {
        val count = selectionCount ?: return
        val work = work ?: return
        if (work.type != "image") {
            count.text = if (work.type == "novel") AppLanguage.label(this, "1 novela · TXT")
                else AppLanguage.label(this, "1 animación · ") + if (mp4) "MP4" else "GIF"
            return
        }
        var chosen = 0
        for (check in pageChecks) if (check.isChecked) chosen++
        count.text = chosen.toString() + AppLanguage.label(this, " de ") + work.pages.size + AppLanguage.label(this, " páginas seleccionadas")
    }

    private fun settings() {
        content.addView(Ui.text(this, getString(R.string.settings_title), 25, Ui.WHITE, true))
        Ui.gap(content, this, 16)
        content.addView(Ui.label(this, getString(R.string.language_title), R.drawable.tabler_settings_2, 17, Ui.CYAN))
        Ui.gap(content, this, 10)
        val languages = Ui.card(this, 16)
        languages.addView(Ui.text(this, getString(R.string.language_description), 14, Ui.WHITE, true))
        Ui.gap(languages, this, 12)
        val languageChoices = Ui.row(this)
        for (language in arrayOf("es", "en")) {
            val option = Ui.button(this, if (language == "es") "Español" else "English",
                language == AppLanguage.current(this), Runnable {
                    if (language != AppLanguage.current(this)) {
                        AppLanguage.select(this, language)
                        recreate()
                    }
                })
            if (language == AppLanguage.current(this)) Ui.withIcon(option, R.drawable.tabler_check, Ui.INPUT)
            val params = LinearLayout.LayoutParams(0, -2, 1f)
            params.rightMargin = Ui.dp(this, 8f)
            languageChoices.addView(option, params)
        }
        languages.addView(languageChoices)
        content.addView(languages)
        Ui.gap(content, this, 24)
        content.addView(Ui.label(this, "Ubicación de descargas", R.drawable.tabler_folder, 17, Ui.CYAN))
        Ui.gap(content, this, 10)
        val storage = Ui.card(this, 16)
        storage.addView(Ui.text(this, "Carpeta principal", 15, Ui.WHITE, true))
        Ui.gap(storage, this, 7)
        val folder = getPreferences(MODE_PRIVATE).getString("folder", "") ?: ""
        storage.addView(Ui.text(this, if (folder.isEmpty()) "Aún no se ha elegido una carpeta" else "Carpeta seleccionada mediante Android", 13, Ui.MUTED, false))
        Ui.gap(storage, this, 12)
        storage.addView(Ui.button(this, "Cambiar carpeta", R.drawable.tabler_folder, false, Runnable { pickFolder(false) }))
        Ui.gap(storage, this, 14)
        val ask = CheckBox(this)
        ask.text = AppLanguage.label(this, "Preguntar dónde guardar cada descarga")
        ask.setTextColor(Ui.WHITE)
        ask.textSize = 14f
        ask.typeface = Ui.typeface(this, false)
        ask.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.CYAN)
        val prefs = getPreferences(MODE_PRIVATE)
        ask.isChecked = prefs.getBoolean("ask", true)
        ask.setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean("ask", value).apply() }
        storage.addView(ask)
        content.addView(storage)
        Ui.gap(content, this, 24)
        content.addView(Ui.label(this, "Cuenta de Pixiv", R.drawable.tabler_user_circle, 17, Ui.CYAN))
        Ui.gap(content, this, 10)
        val account = Ui.card(this, 16)
        account.addView(Ui.text(this, if (loggedIn) "Sesión de Pixiv activa" else "Sin sesión confirmada", 16,
            if (loggedIn) Ui.GREEN else Ui.WHITE, true))
        Ui.gap(account, this, 7)
        account.addView(Ui.text(this, "Puedes consultar obras públicas sin iniciar sesión. El acceso a otras obras depende de los permisos de tu cuenta en Pixiv.", 13, Ui.MUTED, false))
        Ui.gap(account, this, 12)
        account.addView(Ui.button(this, if (loggedIn) "Abrir mi sesión de Pixiv" else "Iniciar sesión en Pixiv",
            R.drawable.tabler_user_circle, false, Runnable { startActivityForResult(Intent(this, LoginActivity::class.java), PIXIV_LOGIN) }))
        if (loggedIn) {
            Ui.gap(account, this, 10)
            account.addView(Ui.button(this, "Cerrar sesión de Pixiv", R.drawable.tabler_logout, false, Runnable { confirmLogout() }))
        }
        content.addView(account)
        Ui.gap(content, this, 24)
        content.addView(Ui.label(this, "Acerca de Pixload", R.drawable.tabler_info_circle, 17, Ui.CYAN))
        Ui.gap(content, this, 10)
        val about = Ui.card(this, 16)
        var version = "0.1.0"
        try { version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.1.0" }
        catch (_: PackageManager.NameNotFoundException) {}
        about.addView(Ui.text(this, AppLanguage.label(this, "Versión ") + version, 14, Ui.WHITE, true))
        Ui.gap(about, this, 7)
        about.addView(Ui.text(this, "Pixload es una aplicación de desarrollo independiente; no es un servicio oficial de Pixiv.", 12, Ui.MUTED, false))
        content.addView(about)
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(AppLanguage.label(this, "Cerrar sesión de Pixiv"))
            .setMessage(AppLanguage.label(this, "Se borrarán las cookies de Pixiv guardadas en Pixload. No se cerrará la sesión del navegador del teléfono ni se borrarán los archivos descargados."))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(AppLanguage.label(this, "Cerrar sesión")) { _, _ ->
                sessionGeneration++
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    loggedIn = false
                    work = null
                    selectedPages = null
                    message = AppLanguage.label(this, "Sesión cerrada. Puedes entrar con otra cuenta de Pixiv.")!!
                    render()
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun download() {
        val work = work ?: return
        if (DownloadQueue.isFull(this)) {
            Toast.makeText(this, AppLanguage.label(this, "Máximo 5 descargas en la cola."), Toast.LENGTH_SHORT).show()
            return
        }
        if (work.type == "image") {
            var chosen = false
            for (check in pageChecks) chosen = chosen || check.isChecked
            if (!chosen) {
                Toast.makeText(this, AppLanguage.label(this, "Selecciona al menos una página."), Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (getPreferences(MODE_PRIVATE).getBoolean("ask", true) || !getPreferences(MODE_PRIVATE).contains("folder")) pickFolder(true)
        else queue(Uri.parse(getPreferences(MODE_PRIVATE).getString("folder", "")))
    }

    private fun pickFolder(startDownload: Boolean) {
        downloadAfterFolderPick = startDownload
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, PICK_FOLDER)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == PIXIV_LOGIN) { refreshSession(true); return }
        if (request != PICK_FOLDER) return
        if (result != RESULT_OK || data?.data == null) {
            downloadAfterFolderPick = false
            return
        }
        val folder = data.data!!
        contentResolver.takePersistableUriPermission(folder,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (downloadAfterFolderPick && work != null) queue(folder)
        else {
            getPreferences(MODE_PRIVATE).edit().putString("folder", folder.toString()).apply()
            Toast.makeText(this, AppLanguage.label(this, "Carpeta principal seleccionada"), Toast.LENGTH_SHORT).show()
            render()
        }
        downloadAfterFolderPick = false
    }

    private fun queue(folder: Uri) {
        val work = work ?: return
        if (DownloadQueue.isFull(this)) {
            Toast.makeText(this, AppLanguage.label(this, "Máximo 5 descargas en la cola."), Toast.LENGTH_SHORT).show()
            return
        }
        if (isWorkPending(work)) {
            Toast.makeText(this, AppLanguage.label(this, "Esta obra ya se está descargando."), Toast.LENGTH_SHORT).show()
            return
        }
        val duplicate: Boolean
        try { duplicate = GalleryStore.hasCompleted(this, work.id, folder.toString()) }
        catch (error: IllegalStateException) {
            Toast.makeText(this, AppLanguage.error(this, error.message), Toast.LENGTH_LONG).show()
            return
        }
        if (duplicate) {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.duplicate_title)
                .setMessage(R.string.duplicate_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_download) { _, _ -> queueConfirmed(folder, true) }
                .show()
            return
        }
        queueConfirmed(folder, false)
    }

    private fun queueConfirmed(folder: Uri, duplicate: Boolean) {
        val work = work ?: return
        if (DownloadQueue.isFull(this)) {
            Toast.makeText(this, AppLanguage.label(this, "Máximo 5 descargas en la cola."), Toast.LENGTH_SHORT).show()
            return
        }
        if (isWorkPending(work)) {
            Toast.makeText(this, AppLanguage.label(this, "Esta obra ya se está descargando."), Toast.LENGTH_SHORT).show()
            return
        }
        // The work id doubles as queue key so the same work cannot be enqueued twice.
        val queueId = (work.id ?: "").ifEmpty { "job:" + System.currentTimeMillis() }
        val queuedPages = if (work.type == "image") pageChecks.count { it.isChecked } else 1
        if (!DownloadQueue.enqueue(this, queueId, work.title ?: "", work.thumbnail ?: "",
                queuedPages, queueKind(work))) {
            Toast.makeText(this, AppLanguage.label(this, "Máximo 5 descargas en la cola."), Toast.LENGTH_SHORT).show()
            return
        }
        var started = false
        try {
            val job = JSONObject()
            job.put("queueId", queueId)
            job.put("title", work.title)
            job.put("id", work.id)
            job.put("type", work.type)
            job.put("folder", folder.toString())
            job.put("grouped", work.type == "image" && work.pages.size >= 2)
            job.put("duplicate", duplicate)
            val files = JSONArray()
            if (work.type == "image") {
                for (i in pageChecks.indices) {
                    if (pageChecks[i].isChecked) {
                        val url = work.pages[i]
                        files.put(JSONObject().put("url", url).put("name", String.format(Locale.ROOT,
                            "%03d.%s", i + 1, PixivClient.extension(url))))
                    }
                }
            } else if (work.type == "ugoira") {
                files.put(JSONObject().put("url", work.animationUrl)
                    .put("name", if (mp4) "animacion.mp4" else "animacion.gif")
                    .put("frames", work.frames).put("format", if (mp4) "mp4" else "gif")
                    .put("preset", gifPreset.id))
            } else {
                files.put(JSONObject().put("name", "novela.txt")
                    .put("text", work.title + "\n" + work.author + "\n\n" + work.text))
            }
            job.put("files", files)
            updateDownloadButton()
            getPreferences(MODE_PRIVATE).edit().putString("folder", folder.toString())
                .putString("last_queued_work_id", work.id)
                .putString("last_queued_link", linkValue).apply()
            val intent = Intent(this, DownloadService::class.java)
            intent.putExtra("job", job.toString())
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            started = true
            message = AppLanguage.label(this, "Descarga iniciada. Consulta la notificación para ver el avance.")!!
            refreshProgress()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            if (!started) {
                DownloadQueue.finish(this, queueId)
                DownloadStatus.write(this, "No se pudo iniciar la descarga", "error", 0, 0)
                refreshProgress()
            }
            Toast.makeText(this, AppLanguage.label(this, "No se pudo iniciar la descarga: ") + AppLanguage.error(this, error.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        importLastDownload()
        if (tab == 2) {
            val entry = galleryEntry
            if (entry == null) render()
            else galleryIO.execute {
                val available = GalleryDocuments.isAvailable(contentResolver, entry)
                runOnUiThread {
                    if (!available && !isFinishing && !isDestroyed && tab == 2 && galleryEntry === entry) {
                        returnToGallery()
                    }
                }
            }
        }
        progressHandler.removeCallbacks(progressUpdate)
        progressHandler.post(progressUpdate)
        if (sessionBadge != null) refreshSession(false)
    }

    override fun onPause() {
        progressHandler.removeCallbacks(progressUpdate)
        galleryVideo?.pause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showQueue) {
            showQueue = false
            render()
        } else if (galleryFullscreen) {
            galleryFullscreen = false
            render()
        } else if (tab == 2 && galleryEntry != null) returnToGallery()
        else super.onBackPressed()
    }

    private fun refreshSession(reloadPreview: Boolean) {
        val generation = sessionGeneration
        executor.execute {
            val active = PixivClient.isLoggedIn()
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != sessionGeneration) return@runOnUiThread
                loggedIn = active
                sessionBadge?.let { badge ->
                    badge.text = AppLanguage.label(this, if (active) "Pixiv activo" else "Sin sesión")
                    badge.setTextColor(if (active) Ui.GREEN else Ui.MUTED)
                    Ui.withIcon(badge, if (active) R.drawable.tabler_circle_check else R.drawable.tabler_circle,
                        if (active) Ui.GREEN else Ui.MUTED)
                }
                if (tab == 3) render()
                if (reloadPreview && active && linkValue.trim().isNotEmpty()) load()
                else if (reloadPreview && !active) {
                    message = AppLanguage.label(this, "No se ha confirmado la sesión. Puedes seguir con contenido público.")!!
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        galleryIO.shutdownNow()
        images.shutdown()
        galleryImages.shutdown()
        super.onDestroy()
    }
}
