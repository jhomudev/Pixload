package com.pixload.app

import com.squareup.gifencoder.Color
import com.squareup.gifencoder.Image
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

class IndependentIndexCacheTest {
    private val digest = MessageDigest.getInstance("SHA-256")

    private fun record(bytes: ByteArray) { digest.update(bytes) }

    private fun hash(data: IntArray) = MessageDigest.getInstance("SHA-256").digest(
        ByteBuffer.allocate(data.size * 4).also { buffer ->
            data.forEach { buffer.putInt(it) }
        }.array()
    )

    private fun fails(action: () -> Unit): String {
        try {
            action()
        } catch (error: Exception) {
            return "${error.javaClass.name}:${error.message}"
        }
        throw AssertionError("Expected rejection")
    }

    @Test fun publicEntriesPreserveIdentityIndicesPixelsAndWriterBytes() {
        val palettes = listOf(1, 2, 16, 256).map { size ->
            (0 until size).map { index ->
                val value = if (size == 1) 0 else index * 255 / (size - 1)
                Color.fromRgbInt(value * 0x010101)
            }.toCollection(linkedSetOf())
        } + listOf(
            (0 until 16).map {
                Color(it / 17.0, (15 - it) / 19.0, it / 23.0)
            }.toCollection(linkedSetOf()),
            linkedSetOf(Color.fromRgbInt(0), Color.fromRgbInt(0x080000))
        )
        for (palette in palettes) for (diffuse in listOf(false, true)) for (precise in listOf(false, true)) {
            val entries = palette.toList()
            val mapper = PaletteLookupDitherer(palette, diffuse, precise)
            val rgb = mapper.paletteRgb()
            val stream = object : ByteArrayOutputStream() {
                var closed = false
                override fun close() { closed = true; super.close() }
            }
            val writer = FastGifEncoder(stream, 32, 48, rgb)
            repeat(2) { frame ->
                val pixels = IntArray(1536) { index ->
                    val base = if (index < entries.size) entries[index].rgbInt else
                        ((index * 7919 + frame * 12345) xor (index shl 11)) and 0xffffff
                    base or ((index % 256) shl 24)
                }
                pixels[1535] = 0xff040000.toInt() // Equal-distance tie for the two-red palette.
                val before = hash(pixels)
                val image = Image.fromRgb(pixels, 32)
                val original = Array(pixels.size) { image.getColor(it) }
                val supplied = palette.toMutableSet()
                val imageResult = mapper.dither(image, supplied)
                val rawResult = mapper.ditherPixels(pixels, 32, 48)
                val output = ByteArray(pixels.size) { 85 }
                assertSame(output, mapper.ditherIndexed(pixels, 32, 48, output))
                for (index in pixels.indices) {
                    val paletteIndex = output[index].toInt() and 255
                    assertTrue(paletteIndex < entries.size)
                    assertSame(entries[paletteIndex], imageResult.getColor(index))
                    assertSame(entries[paletteIndex], rawResult.getColor(index))
                    assertSame(original[index], image.getColor(index))
                    if (!diffuse) {
                        val components = (0..2).map { channel ->
                            val value = ((pixels[index] ushr (16 - channel * 8)) and 255) / 255.0 * 255.0
                            val rounded = (value + if (precise) 0.5f else 0f).toInt()
                            if (precise) rounded else (rounded / 8) * 8 + 4
                        }
                        val expected = entries.indices.minBy { entry ->
                            (0..2).sumOf { channel ->
                                val delta = components[channel] - (entries[entry].getComponent(channel) * 255).toInt()
                                delta * delta
                            }
                        }
                        assertEquals(expected, paletteIndex)
                    }
                }
                assertArrayEquals(before, hash(pixels))
                assertEquals(entries, palette.toList())
                assertEquals(entries, supplied.toList())
                assertArrayEquals(rgb, mapper.paletteRgb())
                record(output)
                writer.addIndexedCentiseconds(output, if (frame == 0) 1 else 65535)
            }
            writer.finish()
            assertFalse(stream.closed)
            record(rgb)
            record(stream.toByteArray())
        }
        // Visit every LUT cell independently of previous frame caches.
        val palette = palettes[3]
        val mapper = PaletteLookupDitherer(palette, false, false)
        val cells = IntArray(32768) { index ->
            (((index shr 10) * 8 + 4) shl 16) or
                ((((index shr 5) and 31) * 8 + 4) shl 8) or ((index and 31) * 8 + 4)
        }
        val output = mapper.ditherIndexed(cells, 256, 128)
        val image = mapper.ditherPixels(cells, 256, 128)
        output.indices.forEach {
            assertSame(palette.elementAt(output[it].toInt() and 255), image.getColor(it))
        }
        assertEquals(252, mapper.ditherIndexed(intArrayOf(0xffffff), 1, 1)[0].toInt() and 255)
        val boundary = (0 until 255).map { Color.fromRgbInt(it shl 16) }.toCollection(linkedSetOf())
        boundary.add(Color.WHITE)
        for (precise in listOf(false, true)) for (diffuse in listOf(false, true)) {
            val high = PaletteLookupDitherer(boundary, diffuse, precise)
            val result = high.ditherIndexed(intArrayOf(0xffffff), 1, 1)
            assertEquals(255, result[0].toInt() and 255)
            assertSame(boundary.last(), high.ditherPixels(intArrayOf(0xffffff), 1, 1).getColor(0))
            record(result)
        }
        record(output)
        // Captured independently from both implementations, without a Git-history dependency.
        assertEquals(
            "e0dd60af9fa23e5938d2a04fc37081a3e71662fcf36101b3ecba97ecb77e8565",
            digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    @Test fun rejectionCancellationAndEmptyBoundariesPreserveInputs() {
        for (precise in listOf(false, true)) for (diffuse in listOf(false, true)) {
            val palette = linkedSetOf(Color.BLACK, Color.WHITE)
            val mapper = PaletteLookupDitherer(palette, diffuse, precise)
            val pixels = intArrayOf(0, 0xffffff)
            val before = hash(pixels)
            val output = byteArrayOf(85, 85)
            val rgb = mapper.paletteRgb()
            for (action in listOf<() -> Unit>(
                { mapper.ditherIndexed(pixels, 3, 1, output) },
                { mapper.ditherIndexed(pixels, 2, 1, ByteArray(1)) },
                { mapper.ditherIndexed(pixels, -2, 1, output) }
            )) {
                assertEquals("java.lang.IllegalArgumentException:Failed requirement.", fails(action))
                assertArrayEquals(before, hash(pixels))
                assertArrayEquals(byteArrayOf(85, 85), output)
            }
            val image = Image.fromRgb(pixels, 2)
            for (action in listOf<() -> Unit>(
                { mapper.ditherIndexed(pixels, 2, 1, output) },
                { mapper.ditherPixels(pixels, 2, 1) },
                { mapper.dither(image, palette) }
            )) {
                Thread.currentThread().interrupt()
                val error = try { fails(action) } finally { Thread.interrupted() }
                assertEquals("java.lang.IllegalStateException:Conversión interrumpida", error)
                assertArrayEquals(before, hash(pixels))
                assertArrayEquals(byteArrayOf(85, 85), output)
                assertEquals(listOf(Color.BLACK, Color.WHITE), palette.toList())
                assertArrayEquals(rgb, mapper.paletteRgb())
            }
            assertEquals(0, mapper.ditherIndexed(IntArray(0), 0, 0).size)
            val empty = PaletteLookupDitherer(emptySet(), diffuse, precise)
            assertEquals("java.lang.NullPointerException:null", fails {
                empty.ditherIndexed(pixels, 2, 1, output)
            })
            assertArrayEquals(before, hash(pixels))
            assertArrayEquals(byteArrayOf(85, 85), output)
        }
    }
}
