package com.pixload.app

import com.squareup.gifencoder.Color
import com.squareup.gifencoder.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class PaletteLookupDithererTest {
    @Test fun qualityMatchesExactNearestColorAcrossCellBoundaries() {
        val random = Random(42)
        for (size in listOf(1, 2, 16, 256)) {
            val palette = linkedSetOf<Color>()
            repeat(size) { palette.add(Color(random.nextDouble(), random.nextDouble(), random.nextDouble())) }
            val cells = IntArray(2048) { random.nextInt(0x1000000) and 0xf8f8f8 }
            val pixels = IntArray(4096) { i -> cells[i / 2] or if (i % 2 == 0) 0 else 0x070707 }
            pixels[0] = 0
            pixels[1] = 0xffffff
            val mapper = PaletteLookupDitherer(palette, false, true)
            repeat(2) {
                val result = mapper.dither(Image.fromRgb(pixels, 64), palette)
                for (i in pixels.indices) {
                    val expected = palette.minOf { squaredDistance(pixels[i], it) }
                    assertEquals("Palette $size pixel $i", expected, squaredDistance(pixels[i], result.getColor(i)))
                    // Revisit the opposite end of every cell using the same cache.
                    pixels[i] = pixels[i] xor 0x070707
                }
            }
        }
    }

    @Test fun qualityPreservesCloseColorsWithDiffusion() {
        val palette = linkedSetOf(Color.fromRgbInt(0x101010), Color.fromRgbInt(0x171717))
        val pixels = IntArray(64) { if (it % 2 == 0) 0x101010 else 0x171717 }
        val result = PaletteLookupDitherer(palette, true, true).dither(Image.fromRgb(pixels, 8), palette)
        for (i in pixels.indices) assertEquals(pixels[i], result.getColor(i).rgbInt)
    }

    @Test fun interruptionRetainsExactErrorForEveryMode() {
        val palette = linkedSetOf(Color.BLACK, Color.WHITE)
        val image = Image.fromRgb(intArrayOf(0, 0xffffff), 2)
        for (precise in listOf(false, true)) for (diffuse in listOf(false, true)) {
            val mapper = PaletteLookupDitherer(palette, diffuse, precise)
            Thread.currentThread().interrupt()
            try {
                mapper.dither(image, palette)
                fail("Interrupted conversion must fail")
            } catch (error: IllegalStateException) {
                assertEquals("Conversión interrumpida", error.message)
            } finally {
                Thread.interrupted()
            }
        }
    }

    private fun squaredDistance(rgb: Int, color: Color): Int = (0..2).sumOf { c ->
        val source = (rgb ushr (16 - c * 8)) and 255
        val difference = source - (color.getComponent(c) * 255).toInt()
        difference * difference
    }
}
