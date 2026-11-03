package com.pixload.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pixload.shared.GifPreset
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipFile

object UgoiraConverter {
    fun interface Progress {
        @Throws(IOException::class)
        fun update(done: Int, total: Int)
    }

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
        Mp4Conversion.convert(archive, frames, output, progress, ::decode)
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
