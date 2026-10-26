package com.pixload.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import com.pixload.shared.GifPreset
import com.squareup.gifencoder.Color
import com.squareup.gifencoder.ColorQuantizer
import com.squareup.gifencoder.Ditherer
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.HashMultiset
import com.squareup.gifencoder.Image
import com.squareup.gifencoder.ImageOptions
import com.squareup.gifencoder.MedianCutQuantizer
import com.squareup.gifencoder.Multiset
import com.squareup.gifencoder.UniformQuantizer
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** One decoded bitmap and one reusable ARGB buffer at a time; Square still owns its per-frame objects. */
internal object GifConversion {
    @JvmStatic
    fun convert(archive: File, frames: JSONArray, output: OutputStream,
                progress: UgoiraConverter.Progress, preset: GifPreset) {
        require(frames.length() > 0) { "La animación no contiene fotogramas." }
        ZipFile(archive).use { zip ->
            val total = frames.length()
            progress.update(0, total)
            val sampleIndices = (if (preset == GifPreset.QUALITY)
                listOf(0, total / 4, total / 2, total * 3 / 4, total - 1)
            else listOf(0, total / 2, total - 1)).distinct()
            val samples = HashMultiset<Color>()
            for (index in sampleIndices) {
                checkCancelled()
                val bitmap = decode(zip, frames.getJSONObject(index).getString("file"), preset.maxWidth)
                try {
                    val row = IntArray(bitmap.width)
                    val step = (bitmap.width / 64).coerceAtLeast(1)
                    for (y in 0 until bitmap.height step 4) {
                        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                        for (x in 0 until bitmap.width step step) {
                            val rgb = row[x] and 0x00f8f8f8
                            samples.add(Color.fromRgbInt(rgb))
                        }
                    }
                } finally { bitmap.recycle() }
            }
            val palette: MutableSet<Color> = when {
                preset == GifPreset.FAST -> LinkedHashSet(UniformQuantizer.INSTANCE.quantize(samples, 256))
                samples.distinctElements.size <= 256 -> LinkedHashSet(samples.distinctElements)
                else -> LinkedHashSet(MedianCutQuantizer.INSTANCE.quantize(samples, 256))
            }
            val quantizer = FixedQuantizer(palette)
            val ditherer = PaletteLookupDitherer(palette, preset.diffuse)
            val first = decode(zip, frames.getJSONObject(0).getString("file"), preset.maxWidth)
            val width = first.width
            val height = first.height
            val pixels = IntArray(width * height)
            val encoder = GifEncoder(output, width, height, 0)
            var elapsedMs = 0L
            var encodedCs = 0L
            var lastKeptMs = 0L
            for (i in 0 until total) {
                checkCancelled()
                val frame = frames.getJSONObject(i)
                elapsedMs += frame.optLong("delay", 100L).coerceAtLeast(0L)
                val targetCs = ((elapsedMs + 5) / 10).coerceAtMost(65535L * total)
                val due = targetCs - encodedCs
                // Drop intermediate frames when the requested FPS cannot represent every frame.
                val enoughTime = elapsedMs - lastKeptMs >= 1000L / preset.fpsMax
                val keep = i == total - 1 || (due > 0 && enoughTime)
                if (keep) {
                    val bitmap = if (i == 0) first else decode(zip, frame.getString("file"), preset.maxWidth)
                    val resized = if (bitmap.width == width && bitmap.height == height) bitmap
                        else Bitmap.createScaledBitmap(bitmap, width, height, true)
                    try {
                        resized.getPixels(pixels, 0, width, 0, 0, width, height)
                        // Round the cumulative elapsed time, never individual frame delays.
                        val delayCs = (targetCs - encodedCs).coerceAtLeast(1).coerceAtMost(65535)
                        encoder.addImage(pixels, width, ImageOptions()
                            .setColorQuantizer(quantizer).setDitherer(ditherer)
                            .setDelay(delayCs * 10, TimeUnit.MILLISECONDS))
                        encodedCs += delayCs
                        lastKeptMs = elapsedMs
                    } finally {
                        if (resized !== bitmap) resized.recycle()
                        bitmap.recycle()
                    }
                } else if (i == 0) first.recycle()
                progress.update(i + 1, total)
            }
            encoder.finishEncoding()
        }
    }

    private fun decode(zip: ZipFile, name: String, maxWidth: Int): Bitmap {
        val entry = zip.getEntry(name) ?: throw IOException("Falta el fotograma $name en el ZIP.")
        if (entry.isDirectory || entry.size > 40L * 1024 * 1024) throw IOException("Fotograma no válido: $name")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight
        if (originalWidth < 1 || originalHeight < 1 || originalWidth.toLong() * originalHeight > 80_000_000)
            throw IOException("Dimensiones de fotograma no válidas.")
        val targetWidth = originalWidth.coerceAtMost(maxWidth)
        val targetHeight = (originalHeight.toLong() * targetWidth / originalWidth).toInt().coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= 28 && targetWidth < originalWidth) {
            // ImageDecoder decodes directly at the requested size (no full-resolution Bitmap).
            val bytes = zip.getInputStream(entry).use { input ->
                ByteArrayOutputStream().use { data ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        checkCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (data.size() + read > 40 * 1024 * 1024) throw IOException("Fotograma demasiado grande.")
                        data.write(buffer, 0, read)
                    }
                    data.toByteArray()
                }
            }
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (originalWidth / options.inSampleSize > maxWidth) options.inSampleSize *= 2
        return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("No se pudo leer el fotograma $name")
    }

    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw IOException("Conversión interrumpida")
    }
}

private class FixedQuantizer(private val palette: MutableSet<Color>) : ColorQuantizer {
    override fun quantize(originalColors: Multiset<Color>, maxColorCount: Int): MutableSet<Color> = palette
}

/** 32k-cell RGB lookup, constructed once and shared by every encoded frame. */
private class PaletteLookupDitherer(palette: Set<Color>, private val diffuse: Boolean) : Ditherer {
    private val lookup = Array(32768) { Color.BLACK }

    init {
        val entries = palette.toTypedArray()
        for (i in lookup.indices) {
            val r = ((i shr 10) and 31) * 8 + 4
            val g = ((i shr 5) and 31) * 8 + 4
            val b = (i and 31) * 8 + 4
            var closest = Int.MAX_VALUE
            for (color in entries) {
                val dr = r - (color.getComponent(0) * 255).toInt()
                val dg = g - (color.getComponent(1) * 255).toInt()
                val db = b - (color.getComponent(2) * 255).toInt()
                val error = dr * dr + dg * dg + db * db
                if (error < closest) { closest = error; lookup[i] = color }
            }
        }
    }

    override fun dither(image: Image, newColors: MutableSet<Color>): Image {
        val width = image.width
        val height = image.height
        val result = Array(height) { Array(width) { Color.BLACK } }
        var current = FloatArray((width + 2) * 3)
        var next = FloatArray((width + 2) * 3)
        for (y in 0 until height) {
            if (Thread.currentThread().isInterrupted) throw IllegalStateException("Conversión interrumpida")
            for (x in 0 until width) {
                val source = image.getColor(x, y)
                val pos = (x + 1) * 3
                val red = (source.getComponent(0) * 255 + current[pos]).toInt().coerceIn(0, 255)
                val green = (source.getComponent(1) * 255 + current[pos + 1]).toInt().coerceIn(0, 255)
                val blue = (source.getComponent(2) * 255 + current[pos + 2]).toInt().coerceIn(0, 255)
                val chosen = lookup[((red shr 3) shl 10) or ((green shr 3) shl 5) or (blue shr 3)]
                result[y][x] = chosen
                if (diffuse) for (c in 0..2) {
                    val error = (when (c) { 0 -> red; 1 -> green; else -> blue }) -
                        (chosen.getComponent(c) * 255).toFloat()
                    if (x + 1 < width) current[pos + 3 + c] += error * 7 / 16
                    if (y + 1 < height) {
                        if (x > 0) next[pos - 3 + c] += error * 3 / 16
                        next[pos + c] += error * 5 / 16
                        if (x + 1 < width) next[pos + 3 + c] += error / 16
                    }
                }
            }
            val previous = current
            current = next
            next = previous
            next.fill(0f)
        }
        return Image.fromColors(result)
    }
}
