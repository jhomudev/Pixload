@file:Suppress("DEPRECATION") // Keep the API-26/27 Movie fallback used by the Java viewer.

package com.pixload.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.VideoView
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService

internal object GalleryViewer {
    @JvmStatic
    fun show(activity: Activity, container: LinearLayout, item: GalleryDocuments.Item,
             executor: ExecutorService): VideoView? {
        val height = minOf(Ui.dp(activity, 470f), activity.resources.displayMetrics.heightPixels / 2)
        return show(activity, container, item, executor, height)
    }

    @JvmStatic
    fun show(activity: Activity, container: LinearLayout, item: GalleryDocuments.Item,
             executor: ExecutorService, height: Int): VideoView? {
        val mime = item.mime ?: ""
        val name = item.name!!.lowercase(Locale.ROOT)
        if (mime.startsWith("video/") || name.endsWith(".mp4")) {
            val frame = FrameLayout(activity)
            frame.background = Ui.background(activity, Ui.INPUT, 12)
            container.addView(frame, LinearLayout.LayoutParams(-1, height))
            val video = VideoView(activity)
            // VideoView is a SurfaceView: an opaque background on the view itself can hide its video surface.
            frame.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            val controller = MediaController(activity)
            controller.setAnchorView(video)
            video.setMediaController(controller)
            val spinner = ProgressBar(activity)
            spinner.indeterminateTintList = ColorStateList.valueOf(Ui.CYAN)
            frame.addView(spinner, FrameLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f), Gravity.CENTER))
            video.setOnPreparedListener { player ->
                player.isLooping = true // Pixiv ugoira MP4s can last under a second.
                spinner.visibility = View.GONE
            }
            video.setOnErrorListener { _, what, extra ->
                Log.w("PixloadPlayer", "Video playback failed: $what/$extra")
                spinner.visibility = View.GONE
                container.addView(Ui.text(activity, "No se pudo reproducir el vídeo.", 14, Ui.MUTED, false))
                true
            }
            video.setVideoURI(item.uri)
            val play = Ui.button(activity, "Reproducir vídeo en bucle", R.drawable.tabler_player_play,
                true, Runnable { video.start() })
            if (height == -1) {
                val playParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                playParams.bottomMargin = Ui.dp(activity, 20f)
                frame.addView(play, playParams)
            } else {
                Ui.gap(container, activity, 10)
                container.addView(play)
            }
            return video
        }
        if (mime.startsWith("text/") || name.endsWith(".txt")) {
            val text = Ui.text(activity, "Abriendo texto…", 15, Ui.WHITE, false)
            text.setTextIsSelectable(true)
            text.setPadding(Ui.dp(activity, 12f), Ui.dp(activity, 12f), Ui.dp(activity, 12f), Ui.dp(activity, 12f))
            text.background = Ui.background(activity, Ui.PANEL, 12)
            container.addView(text)
            executor.execute {
                val value = try {
                    activity.contentResolver.openInputStream(item.uri).use { input ->
                        ByteArrayOutputStream().use { output ->
                            if (input == null) throw Exception("Archivo no disponible")
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (output.size() + read > 2 * 1024 * 1024) {
                                    output.write(buffer, 0, 2 * 1024 * 1024 - output.size())
                                    break
                                }
                                output.write(buffer, 0, read)
                            }
                            var result = String(output.toByteArray(), StandardCharsets.UTF_8)
                            if (output.size() >= 2 * 1024 * 1024) {
                                result += "\n\n… " + AppLanguage.label(activity, "Texto truncado para la vista previa.")
                            }
                            result
                        }
                    }
                } catch (error: Exception) {
                    AppLanguage.label(activity, "No se pudo leer el texto: ") + AppLanguage.error(activity, error.message)
                }
                activity.runOnUiThread { if (text.isAttachedToWindow) text.text = value }
            }
            return null
        }
        if (mime.startsWith("image/") || name.matches(Regex(".*\\.(png|jpe?g|webp|gif)$"))) {
            val frame = FrameLayout(activity)
            frame.background = Ui.background(activity, Ui.INPUT, 12)
            frame.clipToOutline = height != -1
            container.addView(frame, LinearLayout.LayoutParams(-1, height))
            val image = ImageView(activity)
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val spinner = ProgressBar(activity)
            spinner.indeterminateTintList = ColorStateList.valueOf(Ui.CYAN)
            frame.addView(spinner, FrameLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f), Gravity.CENTER))
            executor.execute {
                try {
                    if (name.endsWith(".gif") && Build.VERSION.SDK_INT < 28) {
                        val bytes = readGif(activity, item.uri)
                        val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("GIF no compatible")
                        activity.runOnUiThread {
                            if (!image.isAttachedToWindow) return@runOnUiThread
                            frame.removeView(image)
                            frame.addView(MovieView(activity, movie), 0, FrameLayout.LayoutParams(-1, -1))
                            frame.removeView(spinner)
                        }
                    } else if (Build.VERSION.SDK_INT >= 28) {
                        val drawable = ImageDecoder.decodeDrawable(
                            ImageDecoder.createSource(activity.contentResolver, item.uri)
                        ) { decoder, info, _ ->
                            val largest = maxOf(info.size.width, info.size.height)
                            decoder.setTargetSampleSize(maxOf(1, largest / 1600))
                        }
                        activity.runOnUiThread {
                            if (!image.isAttachedToWindow) return@runOnUiThread
                            image.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable) drawable.start()
                            frame.removeView(spinner)
                        }
                    } else {
                        val bounds = BitmapFactory.Options()
                        bounds.inJustDecodeBounds = true
                        activity.contentResolver.openInputStream(item.uri).use { input ->
                            if (input != null) BitmapFactory.decodeStream(input, null, bounds)
                        }
                        val options = BitmapFactory.Options()
                        options.inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 1600) {
                            options.inSampleSize *= 2
                        }
                        val bitmap = activity.contentResolver.openInputStream(item.uri).use { input ->
                            if (input == null) null else BitmapFactory.decodeStream(input, null, options)
                        } ?: throw Exception("Imagen no disponible")
                        activity.runOnUiThread {
                            if (!image.isAttachedToWindow) return@runOnUiThread
                            image.setImageBitmap(bitmap)
                            frame.removeView(spinner)
                        }
                    }
                } catch (error: Throwable) {
                    if (error !is Exception && error !is OutOfMemoryError) throw error
                    activity.runOnUiThread {
                        if (image.isAttachedToWindow) {
                            frame.removeView(spinner)
                            val failure = Ui.text(activity, "No se pudo mostrar este archivo.", 14, Ui.MUTED, false)
                            failure.gravity = Gravity.CENTER
                            frame.addView(failure, FrameLayout.LayoutParams(-1, -1))
                        }
                    }
                }
            }
            return null
        }
        container.addView(Ui.text(activity, "Este tipo de archivo no tiene vista previa.", 14, Ui.MUTED, false))
        return null
    }

    @Throws(Exception::class)
    private fun readGif(activity: Activity, uri: Uri): ByteArray {
        activity.contentResolver.openInputStream(uri).use { input ->
            ByteArrayOutputStream().use { output ->
                if (input == null) throw Exception("GIF no disponible")
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (output.size() + read > 24 * 1024 * 1024) throw Exception("GIF demasiado grande para previsualizar")
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        }
    }

    private class MovieView(activity: Activity, private val movie: Movie) : View(activity) {
        private var start = 0L

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (start == 0L) start = SystemClock.uptimeMillis()
            movie.setTime(((SystemClock.uptimeMillis() - start) % maxOf(movie.duration(), 100)).toInt())
            val scale = minOf(width.toFloat() / movie.width(), height.toFloat() / movie.height())
            canvas.save()
            canvas.translate((width - movie.width() * scale) / 2, (height - movie.height() * scale) / 2)
            canvas.scale(scale, scale)
            movie.draw(canvas, 0f, 0f)
            canvas.restore()
            if (isAttachedToWindow) postInvalidateDelayed(33)
        }
    }
}
