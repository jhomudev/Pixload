package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class GifPresetTest {
    @Test fun preservesPresetValuesAndDefault() {
        assertEquals(listOf(GifPreset.FAST, GifPreset.BALANCED, GifPreset.QUALITY), GifPreset.entries)
        assertEquals(listOf("fast", "balanced", "quality"), GifPreset.entries.map { it.id })
        assertEquals(listOf(360, 480, 720), GifPreset.entries.map { it.maxWidth })
        assertEquals(listOf(15, 24, 30), GifPreset.entries.map { it.fpsMax })
        assertEquals(listOf(false, true, true), GifPreset.entries.map { it.diffuse })
        for (preset in GifPreset.entries) assertEquals(preset, GifPreset.fromId(preset.id))
        assertEquals(GifPreset.BALANCED, GifPreset.fromId(null))
        assertEquals(GifPreset.BALANCED, GifPreset.fromId("unknown"))
        assertEquals(GifPreset.BALANCED, GifPreset.fromId("FAST"))
    }
}
