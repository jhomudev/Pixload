package com.pixload.app

import com.squareup.gifencoder.Color
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Random
import java.util.concurrent.TimeUnit
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie

class IndexedGifWriterTest {
    @Test fun indexedDitherAndWriterMatchSquarePixelsAndTiming() {
        for (count in intArrayOf(1, 2, 16, 256)) {
            val palette = linkedSetOf<Color>()
            for (i in 0 until count) palette.add(Color(i / 255.0, ((i * 37) % 256) / 255.0, 0.3125))
            val colors = palette.toList()
            for (diffuse in booleanArrayOf(false, true)) for (precise in booleanArrayOf(false, true)) {
                val ditherer = PaletteLookupDitherer(palette, diffuse, precise)
                val random = Random(519)
                val pixels = IntArray(37 * 23) { random.nextInt() }
                val image = ditherer.ditherPixels(pixels, 37, 23)
                val indices = ditherer.ditherIndexed(pixels, 37, 23)
                for (i in indices.indices) assertEquals(image.getColor(i), colors[indices[i].toInt() and 255])
                val old = ByteArrayOutputStream()
                val square = GifEncoder(old, 37, 23, 0)
                val output = ByteArrayOutputStream()
                val fast = FastGifEncoder(output, 37, 23, ditherer.paletteRgb())
                for (delay in intArrayOf(1, 7, 65535)) {
                    square.addImage(image, ImageOptions().setDelay(delay * 10L, TimeUnit.MILLISECONDS))
                    fast.addIndexedCentiseconds(indices, delay)
                }
                square.finishEncoding()
                fast.finish()
                assertAnimationEquals(old.toByteArray(), output.toByteArray(), intArrayOf(1, 7, 65535))
            }
        }
    }

    @Test fun dictionaryGrowthAndResetsDecodeEveryIndexAcrossFrames() {
        val palette = ByteArray(768) { (it / 3).toByte() }
        val output = ByteArrayOutputStream()
        val writer = FastGifEncoder(output, 257, 263, palette)
        val random = Random(123)
        val frames = Array(3) { ByteArray(257 * 263).also { random.nextBytes(it) } }
        frames.forEach { writer.addIndexedCentiseconds(it, 10) }
        writer.finish()
        val data = output.toByteArray()
        val movie = Movie.decodeByteArray(data, 0, data.size)!!
        assertEquals(listOf(10, 10, 10), delays(data))
        val bitmap = Bitmap.createBitmap(257, 263, Bitmap.Config.ARGB_8888)
        try {
            frames.forEachIndexed { n, frame ->
                movie.setTime(n * 100 + 50)
                movie.draw(Canvas(bitmap), 0f, 0f)
                for (i in frame.indices) {
                    val channel = frame[i].toInt() and 255
                    assertEquals("frame=$n pixel=$i", 0xff000000.toInt() or (channel * 0x010101),
                        bitmap.getPixel(i % 257, i / 257))
                }
            }
        } finally { bitmap.recycle() }
    }

    @Test fun onePixelAndRejectedInputsKeepStreamUntouched() {
        val output = ByteArrayOutputStream()
        val writer = FastGifEncoder(output, 1, 1, ByteArray(768))
        writer.addIndexedCentiseconds(byteArrayOf(255.toByte()), 1)
        val before = output.toByteArray()
        for (frame in arrayOf(byteArrayOf(), byteArrayOf(1, 2))) {
            assertThrows(IOException::class.java) { writer.addIndexedCentiseconds(frame, 1) }
            assertArrayEquals(before, output.toByteArray())
        }
        for (delay in intArrayOf(0, 65536)) {
            assertThrows(IOException::class.java) { writer.addIndexedCentiseconds(byteArrayOf(0), delay) }
        }
        Thread.currentThread().interrupt()
        try {
            assertThrows(IOException::class.java) { writer.addIndexedCentiseconds(byteArrayOf(0), 1) }
        } finally { Thread.interrupted() }
        writer.finish()
        val data = output.toByteArray()
        val movie = Movie.decodeByteArray(data, 0, data.size)!!
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            movie.setTime(0)
            movie.draw(Canvas(bitmap), 0f, 0f)
            assertEquals(0xff000000.toInt(), bitmap.getPixel(0, 0))
        } finally { bitmap.recycle() }
        assertThrows(IOException::class.java) { FastGifEncoder(ByteArrayOutputStream(), 1, 1, ByteArray(767)) }
    }

    @Test fun propagatesOutputFailureWithoutClosingCallerStream() {
        val failure = IOException("output failed")
        val output = object : OutputStream() {
            override fun write(b: Int) { throw failure }
            override fun close() { fail("Caller owns stream") }
        }
        val writer = FastGifEncoder(output, 1, 1, ByteArray(768))
        writer.addIndexedCentiseconds(byteArrayOf(0), 1)
        assertSame(failure, assertThrows(IOException::class.java) { writer.finish() })
    }

    private fun assertAnimationEquals(old: ByteArray, actual: ByteArray, delays: IntArray) {
        assertEquals(delays.toList(), delays(old))
        assertEquals(delays.toList(), delays(actual))
        val movies = listOf(old, actual).map { Movie.decodeByteArray(it, 0, it.size)!! }
        assertEquals(movies[0].width(), movies[1].width())
        assertEquals(movies[0].height(), movies[1].height())
        assertEquals(movies[0].duration(), movies[1].duration())
        val bitmaps = movies.map { Bitmap.createBitmap(it.width(), it.height(), Bitmap.Config.ARGB_8888) }
        try {
            var elapsed = 0
            for (n in delays.indices) {
                val pixels = movies.mapIndexed { i, movie ->
                    movie.setTime(elapsed + 1)
                    val bitmap = bitmaps[i]
                    movie.draw(Canvas(bitmap), 0f, 0f)
                    IntArray(bitmap.width * bitmap.height).also {
                        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    }
                }
                assertArrayEquals(pixels[0], pixels[1])
                elapsed += delays[n] * 10
            }
        } finally { bitmaps.forEach { it.recycle() } }
    }

    private fun delays(data: ByteArray): List<Int> {
        val input = ByteArrayInputStream(data)
        fun byte(): Int = input.read().also { assertTrue(it >= 0) }
        fun skip(n: Int) { repeat(n) { byte() } }
        fun blocks() { var size = byte(); while (size != 0) { skip(size); size = byte() } }
        skip(10)
        val packed = byte()
        skip(2)
        if (packed and 128 != 0) skip(3 * (1 shl ((packed and 7) + 1)))
        val result = mutableListOf<Int>()
        while (true) when (byte()) {
            0x3b -> return result
            0x21 -> when (byte()) {
                0xf9 -> { assertEquals(4, byte()); assertEquals(0, byte()); result.add(byte() or (byte() shl 8)); skip(2) }
                0xff -> {
                    assertEquals(11, byte())
                    val app = ByteArray(11) { byte().toByte() }
                    assertEquals("NETSCAPE2.0", String(app, Charsets.US_ASCII))
                    assertEquals(3, byte()); assertEquals(1, byte()); assertEquals(0, byte()); assertEquals(0, byte()); assertEquals(0, byte())
                }
                else -> blocks()
            }
            0x2c -> { skip(8); val flags = byte(); if (flags and 128 != 0) skip(3 * (1 shl ((flags and 7) + 1))); byte(); blocks() }
            else -> fail("Unexpected GIF block")
        }
    }
}
