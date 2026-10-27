package com.pixload.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val MAX_BYTES = 6 * 1024 * 1024

internal class PreviewImages(private val activity: Activity) {
    private val requests: ExecutorService = Executors.newFixedThreadPool(2)
    private val visibleRequests = ArrayList<Thumbnail>()
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(url: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    fun view(url: String?, heightDp: Int): View {
        val frame = FrameLayout(activity)
        frame.background = Ui.background(activity, Ui.RAISED, 12)
        frame.clipToOutline = true
        val image = ImageView(activity)
        image.contentDescription = AppLanguage.label(activity, "Miniatura de la obra de Pixiv")
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        val spinner = ProgressBar(activity)
        spinner.indeterminateTintList = ColorStateList.valueOf(Ui.CYAN)
        frame.addView(spinner, FrameLayout.LayoutParams(Ui.dp(activity, 36f), Ui.dp(activity, 36f), Gravity.CENTER))
        if (url.isNullOrEmpty()) {
            spinner.visibility = View.GONE
            showError(frame)
        } else {
            val cached = cache.get(url)
            if (cached != null) {
                image.setImageBitmap(cached)
                spinner.visibility = View.GONE
            } else {
                visibleRequests.add(Thumbnail(frame, image, spinner, url, Ui.dp(activity, heightDp.toFloat())))
            }
        }
        return frame
    }

    fun clear() {
        visibleRequests.clear()
    }

    fun loadVisible() {
        val visible = Rect()
        for (thumbnail in visibleRequests) {
            if (thumbnail.started || !thumbnail.frame.isAttachedToWindow ||
                !thumbnail.frame.getLocalVisibleRect(visible)) continue
            thumbnail.started = true
            requests.execute { fetch(thumbnail) }
        }
    }

    private fun fetch(item: Thumbnail) {
        try {
            val bitmap = cache.get(item.url) ?: download(item.url, item.targetPixels).also { cache.put(item.url, it) }
            activity.runOnUiThread {
                if (!item.frame.isAttachedToWindow) return@runOnUiThread
                item.image.setImageBitmap(bitmap)
                item.spinner.visibility = View.GONE
            }
        } catch (ignored: Exception) {
            activity.runOnUiThread {
                if (!item.frame.isAttachedToWindow) return@runOnUiThread
                item.spinner.visibility = View.GONE
                showError(item.frame)
            }
        }
    }

    private fun download(url: String, targetPixels: Int): Bitmap {
        val connection = PixivClient.open(url)
        try {
            if (connection.responseCode != 200) throw Exception("HTTP " + connection.responseCode)
            connection.inputStream.use { stream ->
                ByteArrayOutputStream().use { data ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        if (data.size() + read > MAX_BYTES) throw Exception("Miniatura demasiado grande")
                        data.write(buffer, 0, read)
                    }
                    val bytes = data.toByteArray()
                    val bounds = BitmapFactory.Options()
                    bounds.inJustDecodeBounds = true
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options()
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    options.inSampleSize = 1
                    val target = maxOf(targetPixels, Ui.dp(activity, 180f))
                    while (bounds.outWidth / (options.inSampleSize * 2) >= target &&
                        bounds.outHeight / (options.inSampleSize * 2) >= target) options.inSampleSize *= 2
                    // Explicit nullable type: decodeByteArray is a platform type, so the guard would
                    // otherwise be treated as dead code.
                    val decoded: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    return decoded ?: throw Exception("No se pudo decodificar la miniatura")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun showError(frame: FrameLayout) {
        val error: TextView = Ui.text(activity, "Vista previa no disponible", 12, Ui.MUTED, false)
        error.gravity = Gravity.CENTER
        error.setPadding(Ui.dp(activity, 8f), 0, Ui.dp(activity, 8f), 0)
        frame.addView(error, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }

    fun shutdown() {
        requests.shutdownNow()
    }

    private class Thumbnail(
        val frame: FrameLayout,
        val image: ImageView,
        val spinner: ProgressBar,
        val url: String,
        val targetPixels: Int
    ) {
        var started = false
    }
}
