package com.pixload.app

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Arrays

/** Fast RGB 3:3:2 indexing with per-frame color averages and a reusable LZW dictionary. */
internal class FastGifEncoder @Throws(IOException::class) constructor(output: OutputStream, private val width: Int,
                                                                    private val height: Int) {
    private val out: OutputStream
    private val pixels: IntArray
    private val indexed: ByteArray
    private val reds = LongArray(256)
    private val greens = LongArray(256)
    private val blues = LongArray(256)
    private val counts = IntArray(256)
    private val localPalette = ByteArray(256 * 3)
    private val keys = IntArray(8192)
    private val values = IntArray(8192)
    private val generations = IntArray(8192)
    private var generation = 0

    init {
        if (width < 1 || height < 1 || width > 65535 || height > 65535) {
            throw IOException("Dimensiones de GIF no válidas.")
        }
        out = BufferedOutputStream(output, 65536)
        pixels = IntArray(width * height)
        indexed = ByteArray(width * height)
        out.write(byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte()))
        shortValue(width)
        shortValue(height)
        out.write(0xf7) // Global 256-color table, 8-bit color resolution.
        out.write(0)
        out.write(0)
        for (i in 0 until 256) {
            out.write((i ushr 5) * 255 / 7)
            out.write(((i ushr 2) and 7) * 255 / 7)
            out.write((i and 3) * 85)
        }
        out.write(byteArrayOf(0x21, 0xff.toByte(), 11, 'N'.code.toByte(), 'E'.code.toByte(),
            'T'.code.toByte(), 'S'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte(),
            'P'.code.toByte(), 'E'.code.toByte(), '2'.code.toByte(), '.'.code.toByte(),
            '0'.code.toByte(), 3, 1, 0, 0, 0))
    }

    @Throws(IOException::class)
    fun addFrame(bitmap: Bitmap, delayMillis: Int) {
        if (bitmap.width != width || bitmap.height != height) throw IOException("Tamaño de fotograma distinto.")
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        Arrays.fill(reds, 0L)
        Arrays.fill(greens, 0L)
        Arrays.fill(blues, 0L)
        Arrays.fill(counts, 0)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pos = y * width + x
                val rgb = pixels[pos]
                val dither = BAYER[(y and 3) * 4 + (x and 3)] - 8
                val red = clamp(((rgb ushr 16) and 255) + dither * 2) ushr 5
                val green = clamp(((rgb ushr 8) and 255) + dither * 2) ushr 5
                val blue = clamp((rgb and 255) + dither * 4) ushr 6
                val index = (red shl 5) or (green shl 2) or blue
                indexed[pos] = index.toByte()
                reds[index] += ((rgb ushr 16) and 255).toLong()
                greens[index] += ((rgb ushr 8) and 255).toLong()
                blues[index] += (rgb and 255).toLong()
                counts[index]++
            }
        }
        // Keep the fast 3:3:2 lookup, but represent each occupied bucket by its
        // actual mean color. A local table avoids a second pass over the ZIP frames.
        for (i in 0 until 256) {
            localPalette[i * 3] = (if (counts[i] == 0) (i ushr 5) * 255 / 7 else reds[i] / counts[i]).toByte()
            localPalette[i * 3 + 1] = (if (counts[i] == 0) ((i ushr 2) and 7) * 255 / 7 else greens[i] / counts[i]).toByte()
            localPalette[i * 3 + 2] = (if (counts[i] == 0) (i and 3) * 85 else blues[i] / counts[i]).toByte()
        }
        writeFrame(indexed, delayMillis, localPalette)
    }

    @Throws(IOException::class)
    fun addIndexed(frame: ByteArray, delayMillis: Int) {
        if (frame.size != indexed.size) throw IOException("Tamaño de fotograma distinto.")
        writeFrame(frame, delayMillis, null)
    }

    @Throws(IOException::class)
    private fun writeFrame(frame: ByteArray, delayMillis: Int, palette: ByteArray?) {
        val delay = maxOf(2, minOf(65535, Math.round(delayMillis / 10f)))
        out.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 0, delay.toByte(), (delay ushr 8).toByte(), 0, 0))
        out.write(0x2c)
        shortValue(0)
        shortValue(0)
        shortValue(width)
        shortValue(height)
        out.write(if (palette == null) 0 else 0x87) // Optional 256-color local table.
        if (palette != null) out.write(palette)
        out.write(8) // Minimum LZW code size for the 256-color palette.
        encode(frame)
    }

    @Throws(IOException::class)
    fun finish() {
        out.write(0x3b)
        out.flush()
    }

    @Throws(IOException::class)
    private fun encode(frame: ByteArray) {
        val bits = Bits()
        var size = 9
        var next = END + 1
        generation++
        bits.write(CLEAR, size)
        var prefix = frame[0].toInt() and 255
        for (i in 1 until frame.size) {
            val symbol = frame[i].toInt() and 255
            val key = (prefix shl 8) or symbol
            var slot = (key * 0x9e3779b9.toInt()) ushr 19
            while (generations[slot] == generation && keys[slot] != key) slot = (slot + 1) and 8191
            if (generations[slot] == generation) {
                prefix = values[slot]
            } else {
                bits.write(prefix, size)
                if (next < MAX_CODE) {
                    generations[slot] = generation
                    keys[slot] = key
                    values[slot] = next++
                    if (next > (1 shl size) && size < 12) size++
                } else {
                    bits.write(CLEAR, size)
                    generation++
                    next = END + 1
                    size = 9
                }
                prefix = symbol
            }
        }
        bits.write(prefix, size)
        bits.write(END, size)
        bits.finish()
    }

    @Throws(IOException::class)
    private fun shortValue(value: Int) {
        out.write(value and 255)
        out.write((value ushr 8) and 255)
    }

    private inner class Bits {
        private val block = ByteArray(255)
        private var count = 0
        private var buffer = 0
        private var remaining = 0

        @Throws(IOException::class)
        fun write(code: Int, width: Int) {
            buffer = buffer or (code shl remaining)
            remaining += width
            while (remaining >= 8) {
                put(buffer and 255)
                buffer = buffer ushr 8
                remaining -= 8
            }
        }

        @Throws(IOException::class)
        fun finish() {
            if (remaining > 0) put(buffer and 255)
            flushBlock()
            out.write(0)
        }

        @Throws(IOException::class)
        private fun put(value: Int) {
            block[count++] = value.toByte()
            if (count == 255) flushBlock()
        }

        @Throws(IOException::class)
        private fun flushBlock() {
            if (count == 0) return
            out.write(count)
            out.write(block, 0, count)
            count = 0
        }
    }

    companion object {
        private const val CLEAR = 256
        private const val END = 257
        private const val MAX_CODE = 4096
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        private fun clamp(value: Int): Int = maxOf(0, minOf(255, value))
    }
}
