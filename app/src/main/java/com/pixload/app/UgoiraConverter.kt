package com.pixload.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pixload.shared.GifPreset
import org.jcodec.api.android.AndroidSequenceEncoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile

object UgoiraConverter {
    fun interface Progress {
        @Throws(IOException::class)
        fun update(done: Int, total: Int)
    }

    private const val FPS = 30

    @JvmStatic
    @Throws(Exception::class)
    fun gif(archive: File, frames: JSONArray, output: OutputStream, progress: Progress) {
        gif(archive, frames, output, progress, GifPreset.BALANCED)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun gif(archive: File, frames: JSONArray, output: OutputStream, progress: Progress, preset: GifPreset) {
        GifConversion.convert(archive, frames, output, progress, preset)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun mp4(archive: File, frames: JSONArray, output: File, progress: Progress) {
        ZipFile(archive).use { zip ->
            if (frames.length() == 0) throw IOException("La animación no contiene fotogramas.")
            val first = decode(zip, frames.getJSONObject(0).getString("file"), 1280)
            // H.264 requires even dimensions; animations with odd dimensions are scaled by at most one pixel.
            val width = first.width - first.width % 2
            val height = first.height - first.height % 2
            if (width < 2 || height < 2) throw IOException("Fotogramas demasiado pequeños para MP4.")
            val encoder = AndroidSequenceEncoder.createSequenceEncoder(output, FPS)
            var duration = 0L
            var encoded = 0L
            try {
                for (i in 0 until frames.length()) {
                    val frame = frames.getJSONObject(i)
                    val bitmap = if (i == 0) first else decode(zip, frame.getString("file"), 1280)
                    val resized = if (bitmap.width == width && bitmap.height == height) bitmap
                    else Bitmap.createScaledBitmap(bitmap, width, height, true)
                    try {
                        duration += maxOf(20, frame.optInt("delay", 100)).toLong()
                        val needed = maxOf(1L, Math.round(duration * FPS / 1000.0))
                        if (needed > 18000) throw IOException("Animación demasiado larga para convertir a MP4 (máximo 10 minutos).")
                        while (encoded < needed) {
                            encoder.encodeImage(resized)
                            encoded++
                        }
                    } finally {
                        if (resized !== bitmap) resized.recycle()
                        bitmap.recycle()
                    }
                    progress.update(i + 1, frames.length())
                }
            } finally {
                encoder.finish()
            }
        }
    }

    @Throws(IOException::class)
    private fun decode(zip: ZipFile, name: String, maxSize: Int): Bitmap {
        val entry = zip.getEntry(name)
        if (entry == null || entry.isDirectory) throw IOException("Falta el fotograma $name en el ZIP.")
        if (entry.size > 40L * 1024 * 1024) throw IOException("Fotograma demasiado grande.")
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        if (options.outWidth < 1 || options.outHeight < 1 ||
            options.outWidth.toLong() * options.outHeight > 80_000_000L) {
            throw IOException("Dimensiones de fotograma no válidas.")
        }
        options.inSampleSize = 1
        // Decode at or above the target resolution, then scale precisely.
        // Previously 1100px was subsampled directly to ~550px for a 960px GIF.
        while (maxOf(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= maxSize) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return zip.getInputStream(entry).use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream, null, options)
                ?: throw IOException("No se pudo leer el fotograma $name")
            val largest = maxOf(bitmap.width, bitmap.height)
            if (largest <= maxSize) {
                bitmap
            } else {
                val scale = maxSize.toDouble() / largest
                val width = maxOf(1, Math.round(bitmap.width * scale).toInt())
                val height = maxOf(1, Math.round(bitmap.height * scale).toInt())
                try {
                    Bitmap.createScaledBitmap(bitmap, width, height, true)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
