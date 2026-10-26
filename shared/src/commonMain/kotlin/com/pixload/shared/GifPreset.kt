package com.pixload.shared

enum class GifPreset(val id: String, val maxWidth: Int, val fpsMax: Int, val diffuse: Boolean) {
    FAST("fast", 360, 15, false),
    BALANCED("balanced", 480, 24, true),
    QUALITY("quality", 720, 30, true);

    companion object {
        fun fromId(id: String?): GifPreset = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}
