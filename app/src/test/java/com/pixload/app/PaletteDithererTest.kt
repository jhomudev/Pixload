package com.pixload.app

import com.squareup.gifencoder.Color
import com.squareup.gifencoder.Image
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The encoder dithers from an Image it builds pixel by pixel. GifConversion now dithers the raw
 * ARGB buffer directly to skip that grid, so both entry points must pick the same palette color for
 * every pixel, in every preset combination.
 */
class PaletteDithererTest {
    private val palette: Set<Color> = linkedSetOf(
        Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE,
        Color(0.2, 0.4, 0.6), Color(0.8, 0.1, 0.3), Color(0.5, 0.5, 0.1)
    )

    private fun pixels(width: Int, height: Int): IntArray {
        val data = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            data[y * width + x] = (0xff shl 24) or (((x * 7 + y * 13) % 256) shl 16) or
                (((x * 3 + y * 5) % 256) shl 8) or ((x + y * 11) % 256)
        }
        return data
    }

    @Test fun rawPixelsDitherExactlyLikeTheEncodersColorGrid() {
        val width = 37
        val height = 23
        val data = pixels(width, height)
        val image = Image.fromRgb(data, width)
        for (diffuse in booleanArrayOf(true, false)) for (precise in booleanArrayOf(true, false)) {
            val ditherer = PaletteLookupDitherer(palette, diffuse, precise)
            val fromImage = ditherer.dither(image, palette.toMutableSet())
            val fromPixels = ditherer.ditherPixels(data, width, height)
            for (y in 0 until height) for (x in 0 until width) {
                assertEquals("diffuse=$diffuse precise=$precise at $x,$y",
                    fromImage.getColor(x, y), fromPixels.getColor(x, y))
            }
        }
    }

    @Test fun singlePixelFrameDithersThroughBothEntryPoints() {
        val data = intArrayOf(0xff12ab34.toInt())
        val ditherer = PaletteLookupDitherer(palette, true, true)
        assertEquals(ditherer.dither(Image.fromRgb(data, 1), palette.toMutableSet()).getColor(0, 0),
            ditherer.ditherPixels(data, 1, 1).getColor(0, 0))
    }
}
