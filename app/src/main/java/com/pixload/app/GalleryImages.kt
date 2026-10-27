package com.pixload.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class GalleryImages(private val activity: Activity) {
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val pending = ArrayList<Request>()
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clear() {
        pending.clear()
    }

    fun tile(uri: String?, mime: String?, sizeDp: Int): View {
        val frame = FrameLayout(activity)
        frame.background = Ui.background(activity, Ui.RAISED, 12)
        frame.clipToOutline = true
        if (uri.isNullOrEmpty() || (mime != null && mime.startsWith("text/"))) {
            val text = mime != null && mime.startsWith("text/")
            val icon = Ui.icon(activity, if (text) R.drawable.tabler_file_text else R.drawable.tabler_photo_off, Ui.CYAN)
            frame.addView(icon, FrameLayout.LayoutParams(Ui.dp(activity, 42f), Ui.dp(activity, 42f), Gravity.CENTER))
            return frame
        }
        val image = ImageView(activity)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.contentDescription = AppLanguage.label(activity, "Miniatura de archivo guardado")
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        val spinner = ProgressBar(activity)
        spinner.indeterminateTintList = ColorStateList.valueOf(Ui.CYAN)
        frame.addView(spinner, FrameLayout.LayoutParams(Ui.dp(activity, 32f), Ui.dp(activity, 32f), Gravity.CENTER))
        val cached = cache.get(uri)
        if (cached != null) {
            image.setImageBitmap(cached)
            spinner.visibility = View.GONE
        } else pending.add(Request(frame, image, spinner, uri, mime, Ui.dp(activity, sizeDp.toFloat())))
        return frame
    }

    fun loadVisible() {
        val visible = Rect()
        for (request in pending) {
            if (request.started || !request.frame.isAttachedToWindow ||
                !request.frame.getLocalVisibleRect(visible)) continue
            request.started = true
            executor.execute { load(request) }
        }
    }

    private fun load(request: Request) {
        var bitmap: Bitmap? = null
        try {
            if (request.mime != null && request.mime.startsWith("video/")) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(activity, Uri.parse(request.uri))
                    bitmap = if (Build.VERSION.SDK_INT >= 27)
                        retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            request.size * 2, request.size * 2)
                    else retriever.frameAtTime
                } finally {
                    retriever.release()
                }
            } else {
                val uri = Uri.parse(request.uri)
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                activity.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.RGB_565
                options.inSampleSize = 1
                while (bounds.outWidth / (options.inSampleSize * 2) >= request.size &&
                    bounds.outHeight / (options.inSampleSize * 2) >= request.size) options.inSampleSize *= 2
                activity.contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream, null, options)
                }
            }
            if (bitmap != null) cache.put(request.uri, bitmap)
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
        val result = bitmap
        activity.runOnUiThread {
            if (!request.frame.isAttachedToWindow) return@runOnUiThread
            request.spinner.visibility = View.GONE
            if (result != null) request.image.setImageBitmap(result)
            else {
                val missing = Ui.text(activity, "Vista previa\nno disponible", 12, Ui.MUTED, false)
                missing.gravity = Gravity.CENTER
                request.frame.addView(missing, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private class Request(
        val frame: FrameLayout,
        val image: ImageView,
        val spinner: ProgressBar,
        val uri: String,
        val mime: String?,
        val size: Int
    ) {
        var started = false
    }
}
