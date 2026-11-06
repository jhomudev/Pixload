package com.pixload.app

import com.pixload.shared.GifPreset
import com.squareup.gifencoder.Color
import com.squareup.gifencoder.HashMultiset
import com.squareup.gifencoder.UniformQuantizer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class DeterministicMedianCutQuantizerTest {
    private val affected = listOf(GifPreset.BALANCED, GifPreset.QUALITY)
    private val canonical = Comparator<Color> { a, b ->
        var result = 0
        for (c in 0..2) {
            result = a.getComponent(c).compareTo(b.getComponent(c))
            if (result != 0) break
        }
        result
    }

    private fun samples(colors: List<Color>, weights: List<Int> = List(colors.size) { 1 }) =
        HashMultiset<Color>().apply { colors.forEachIndexed { i, color -> add(color, weights[i]) } }

    private fun bits(colors: Collection<Color>) = colors.map { color ->
        (0..2).map { color.getComponent(it).toRawBits() }
    }

    private fun assertPalette(expected: List<Color>, actual: Set<Color>) {
        assertEquals("exact ordered component bits", bits(expected), bits(actual))
        assertEquals("serialized RGB order", expected.map { it.rgbInt }, actual.map { it.rgbInt })
        assertEquals("serialized RGB set", expected.map { it.rgbInt }.toSet(), actual.map { it.rgbInt }.toSet())
    }

    @Test fun bypassUsesExactLexicographicOrderNotRoundedRgb() {
        val colors = listOf(Color(0.1002, 0.2, 0.3), Color(0.1001, 0.2, 0.3),
            Color.BLUE, Color.GREEN, Color.RED)
        assertEquals(colors[0].rgbInt, colors[1].rgbInt)
        for (preset in affected) {
            assertPalette(colors.sortedWith(canonical), GifConversion.selectPalette(samples(colors), preset))
        }
    }

    @Test fun weightedMedianCanSplitOccurrencesOfTheSameColor() {
        val middle = Color(0.5, 0.0, 0.0)
        val source = samples(listOf(Color.BLACK, middle, Color.RED), listOf(1, 4, 1))
        val expected = listOf(Color.BLACK.plus(middle.scaled(2.0)).scaled(1.0 / 3),
            middle.scaled(2.0).plus(Color.RED).scaled(1.0 / 3))
        for (preset in affected) assertPalette(expected, GifConversion.selectPalette(source, preset, 2))
    }

    @Test fun channelTiesPreferRedAndClusterTiesPreferEarlierCreation() {
        val colors = listOf(Color.BLACK, Color.GREEN, Color.RED, Color(1.0, 1.0, 0.0))
        val expected = listOf(Color.BLACK, Color.GREEN, Color(1.0, 0.5, 0.0))
        for (preset in affected) assertPalette(expected,
            GifConversion.selectPalette(samples(colors), preset, 3))
    }

    @Test fun greenWinsItsSpreadTieWithBlue() {
        val colors = listOf(Color.BLACK, Color.BLUE, Color.GREEN, Color(0.0, 1.0, 1.0))
        val expected = listOf(Color(0.0, 0.0, 0.5), Color(0.0, 1.0, 0.5))
        for (preset in affected) assertPalette(expected,
            GifConversion.selectPalette(samples(colors), preset, 2))
    }

    @Test fun splitUsesSubByteComponentPrecisionBeforeOtherChannels() {
        val a = Color(0.1001, 0.01, 0.0)
        val b = Color(0.1002, 0.0, 0.0)
        val c = Color(0.1003, 0.005, 0.0)
        val colors = listOf(c, b, a, Color.RED, Color(1.0, 0.01, 0.0))
        val expected = listOf(a.plus(b).scaled(0.5),
            c.plus(Color.RED).plus(colors.last()).scaled(1.0 / 3))
        for (preset in affected) assertPalette(expected,
            GifConversion.selectPalette(samples(colors), preset, 2))
    }

    @Test fun splitComponentTiesUseFullExactRgbAndOddMedianRoundsDown() {
        val colors = listOf(Color.BLACK, Color.GREEN, Color.BLUE, Color.RED, Color.WHITE)
        val expected = listOf(Color(0.0, 0.0, 0.5), Color(2.0, 2.0, 1.0).scaled(1.0 / 3))
        for (preset in affected) assertPalette(expected,
            GifConversion.selectPalette(samples(colors), preset, 2))
    }

    @Test fun centroidRetainsCanonicalWeightedMultiplyAddNormalizeBits() {
        val colors = listOf(Color(0.8, 0.04, 0.9), Color(0.01, 0.6, 0.2),
            Color(0.2, 0.3, 0.4), Color(0.20000000000000004, 0.3, 0.4))
        val weights = listOf(3, 7, 13, 11)
        val source = samples(colors, weights)
        var sum = Color.BLACK
        for (color in colors.sortedWith(canonical)) sum = sum.plus(color.scaled(source.count(color).toDouble()))
        val expected = listOf(sum.scaled(1.0 / source.size))
        for (preset in affected) assertPalette(expected, GifConversion.selectPalette(source, preset, 1))
    }

    private fun corpus(size: Int) = List(size) { i ->
        Color(i / 512.0, ((i * 73) % 257) / 512.0, ((i * 31) % 263) / 512.0)
    }

    @Test fun paletteBoundsAndBypassPreserveOne255256And257Colors() {
        for (size in listOf(1, 255, 256, 257)) for (preset in affected) {
            val colors = corpus(size)
            val palette = GifConversion.selectPalette(samples(colors), preset)
            assertTrue(palette.size in 1..minOf(size, 256))
            assertEquals(bits(palette.sortedWith(canonical)), bits(palette))
            if (size <= 256) assertPalette(colors.sortedWith(canonical), palette)
            else assertEquals(256, palette.size)
        }
    }

    @Test fun unchangedMultisetAndEqualValuedNewInstancesIgnoreInsertionOrder() {
        val colors = corpus(257)
        val weights = List(colors.size) { it % 5 + 1 }
        val source = samples(colors, weights)
        val references = source.distinctElements.toList()
        val beforeBits = bits(references)
        for (preset in affected) {
            val expected = GifConversion.selectPalette(source, preset).toList()
            repeat(3) { iteration ->
                assertPalette(expected, GifConversion.selectPalette(source, preset))
                val permutation = colors.indices.shuffled(Random(iteration))
                val equalNew = permutation.map { i -> Color(colors[i].getComponent(0),
                    colors[i].getComponent(1), colors[i].getComponent(2)) }
                val reordered = samples(equalNew, permutation.map { weights[it] })
                assertPalette(expected, GifConversion.selectPalette(reordered, preset))
            }
        }
        assertEquals(weights.sum(), source.size)
        assertEquals(beforeBits, bits(references))
        assertEquals(references.size, source.distinctElements.size)
        colors.forEachIndexed { i, color -> assertEquals(weights[i], source.count(color)) }
        for (reference in references) assertTrue(source.distinctElements.any { it === reference })
    }

    @Test fun concurrentCallsHaveNoInvocationSharedTieState() {
        val source = samples(corpus(257))
        val expected = affected.associateWith { GifConversion.selectPalette(source, it).toList() }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = List(8) { i -> Callable {
                val preset = affected[i % affected.size]
                assertPalette(expected.getValue(preset), GifConversion.selectPalette(source, preset))
            } }
            for (future in executor.invokeAll(jobs)) future.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun fastRetainsPublicUniformOrderIncludingSmallPalettes() {
        for (size in listOf(1, 255, 256, 257)) {
            val source = samples(corpus(size))
            assertPalette(UniformQuantizer.INSTANCE.quantize(source, 256).toList(),
                GifConversion.selectPalette(source, GifPreset.FAST))
        }
    }

    @Test fun missingAndUnknownPresetIdsKeepBalancedSelection() {
        val source = samples(listOf(Color.RED, Color.BLUE, Color.GREEN))
        val expected = GifConversion.selectPalette(source, GifPreset.BALANCED).toList()
        for (id in listOf(null, "unknown", "FAST")) {
            assertPalette(expected, GifConversion.selectPalette(source, GifPreset.fromId(id)))
        }
    }

    private fun widestAxis(colors: List<Color>): Int {
        var selected = 0
        var widest = -1.0
        for (axis in 0..2) {
            val spread = componentSpread(colors, axis)
            if (spread > widest) {
                selected = axis
                widest = spread
            }
        }
        return selected
    }

    private fun componentSpread(colors: List<Color>, axis: Int) =
        colors.maxOf { it.getComponent(axis) } - colors.minOf { it.getComponent(axis) }

    // Independent reference: expand occurrences, cut sequence indices, and retain boxes in creation order.
    private fun occurrenceOracle(occurrences: List<Color>, limit: Int): List<Color> {
        val unique = occurrences.toSortedSet(canonical)
        if (unique.size <= limit) return unique.toList()
        val boxes = mutableListOf(occurrences.toMutableList())
        while (boxes.size < limit) {
            var chosen = 0
            for (i in 1 until boxes.size) {
                if (componentSpread(boxes[i], widestAxis(boxes[i])) >
                    componentSpread(boxes[chosen], widestAxis(boxes[chosen]))) chosen = i
            }
            val box = boxes.removeAt(chosen)
            val axis = widestAxis(box)
            box.sortWith(compareBy<Color> { it.getComponent(axis) }.then(canonical))
            val middle = box.size / 2
            boxes.add(box.subList(0, middle).toMutableList())
            boxes.add(box.subList(middle, box.size).toMutableList())
        }
        return boxes.map { box ->
            val weights = TreeMap<Color, Int>(canonical)
            for (color in box) weights[color] = (weights[color] ?: 0) + 1
            val sum = DoubleArray(3)
            for ((color, count) in weights) for (axis in 0..2) sum[axis] += color.getComponent(axis) * count
            val reciprocal = 1.0 / box.size
            Color(sum[0] * reciprocal, sum[1] * reciprocal, sum[2] * reciprocal)
        }.toSortedSet(canonical).toList()
    }

    @Test fun expandedOccurrenceOraclePreservesExactCentroidsAndInputIdentity() {
        val random = java.util.Random(71831)
        repeat(24) { trial ->
            val occurrences = buildList {
                repeat(3 + trial) {
                    val color = Color(random.nextInt(32) / 31.0, random.nextInt(32) / 31.0,
                        random.nextInt(32) / 31.0)
                    repeat(1 + random.nextInt(9)) { add(color) }
                }
            }
            val source = samples(occurrences)
            val references = source.distinctElements.toList()
            val originalBits = bits(references)
            val counts = references.map { source.count(it) }
            for (limit in 1..minOf(12, references.size)) {
                val expected = occurrenceOracle(occurrences, limit)
                for (preset in affected) {
                    assertPalette(expected, GifConversion.selectPalette(source, preset, limit))
                    val fresh = occurrences.map { Color(it.getComponent(0), it.getComponent(1),
                        it.getComponent(2)) }.toMutableList()
                    Collections.shuffle(fresh, random)
                    assertPalette(expected, GifConversion.selectPalette(samples(fresh), preset, limit))
                }
            }
            assertEquals(occurrences.size, source.size)
            assertEquals(originalBits, bits(references))
            assertEquals(references.size, source.distinctElements.size)
            references.forEachIndexed { i, reference ->
                assertEquals(counts[i], source.count(reference))
                assertTrue(source.distinctElements.any { it === reference })
            }
        }
    }

    @Test fun occurrenceBoundaryAndCubeCreationTiesMatchExplicitCentroids() {
        val weighted = List(3) { Color.BLACK } + List(6) { Color(0.25, 0.0, 0.0) } + Color.RED
        val cube = buildList {
            for (r in 0..1) for (g in 0..1) for (b in 0..1) add(Color(r.toDouble(), g.toDouble(), b.toDouble()))
        }
        val expectedBoundary = listOf(Color(0.1, 0.0, 0.0), Color(0.4, 0.0, 0.0))
        val expectedCube = listOf(Color(0.0, 0.0, 0.5), Color(0.0, 1.0, 0.5), Color(1.0, 0.5, 0.5))
        assertEquals(bits(expectedBoundary), bits(occurrenceOracle(weighted, 2)))
        assertEquals(bits(expectedCube), bits(occurrenceOracle(cube, 3)))
        for (preset in affected) {
            assertPalette(expectedBoundary, GifConversion.selectPalette(samples(weighted), preset, 2))
            assertPalette(expectedCube, GifConversion.selectPalette(samples(cube), preset, 3))
        }
    }

    private fun indexedGif(palette: Set<Color>, preset: GifPreset): ByteArray {
        val ditherer = PaletteLookupDitherer(palette, preset.diffuse, preset == GifPreset.QUALITY)
        val pixels = IntArray(33 * 9) { 0xff000000.toInt() or ((it * 104729) and 0xffffff) }
        val output = ByteArrayOutputStream()
        val writer = FastGifEncoder(output, 33, 9, ditherer.paletteRgb())
        for (delay in listOf(1, 17)) {
            writer.addIndexedCentiseconds(ditherer.ditherIndexed(pixels, 33, 9, ByteArray(pixels.size)), delay)
        }
        writer.finish()
        return output.toByteArray()
    }

    @Test fun productionBoundaryPalettesAndConcurrentIndexedGifsRemainRepeatable() {
        val executor = Executors.newFixedThreadPool(3)
        try {
            for (size in listOf(0, 1, 2, 255, 256, 257, 513)) {
                val occurrences = buildList {
                    repeat(size) { i -> repeat(1 + i % 3) { add(Color.fromRgbInt((i * 104729) and 0xffffff)) } }
                }
                val source = samples(occurrences)
                val palettes = GifPreset.entries.associateWith { GifConversion.selectPalette(source, it) }
                assertPalette(UniformQuantizer.INSTANCE.quantize(source, 256).toList(), palettes.getValue(GifPreset.FAST))
                for (id in listOf(null, "", "unknown", "FAST", "balanced", "quality", "fast")) {
                    val expected = when (id) {
                        "fast" -> GifPreset.FAST
                        "quality" -> GifPreset.QUALITY
                        else -> GifPreset.BALANCED
                    }
                    assertSame(expected, GifPreset.fromId(id))
                    assertPalette(palettes.getValue(expected).toList(),
                        GifConversion.selectPalette(source, GifPreset.fromId(id)))
                }
                for (preset in affected) {
                    val selected = palettes.getValue(preset)
                    assertTrue(selected.size <= 256)
                    assertEquals(bits(selected.sortedWith(canonical)), bits(selected))
                    if (size <= 256) assertPalette(occurrenceOracle(occurrences, 256), selected)
                    if (size == 0) continue
                    val expected = indexedGif(selected, preset)
                    val jobs = List(6) { Callable {
                        val fresh = occurrences.asReversed().map { Color(it.getComponent(0),
                            it.getComponent(1), it.getComponent(2)) }
                        indexedGif(GifConversion.selectPalette(samples(fresh), preset), preset)
                    } }
                    for (future in executor.invokeAll(jobs)) assertArrayEquals(expected, future.get(20, TimeUnit.SECONDS))
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
