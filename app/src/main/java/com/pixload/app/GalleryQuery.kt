package com.pixload.app

internal enum class GalleryType { ALL, IMAGE, GIF, MP4, TXT, FOLDER }
internal enum class GalleryOrder { ORIGINAL, NEWEST, OLDEST }

/** Presentation only: neither the validated input nor the stored history is reordered. */
internal fun galleryQuery(
    entries: List<GalleryStore.Entry>, title: String, type: GalleryType, order: GalleryOrder
): List<GalleryStore.Entry> {
    val filtered = entries.filter { entry ->
        entry.title.contains(title.trim(), ignoreCase = true) && when (type) {
            GalleryType.ALL -> true
            GalleryType.FOLDER -> entry.folder
            GalleryType.IMAGE -> !entry.folder && entry.mime in setOf("image/jpeg", "image/png", "image/webp")
            GalleryType.GIF -> !entry.folder && entry.mime == "image/gif"
            GalleryType.MP4 -> !entry.folder && entry.mime == "video/mp4"
            GalleryType.TXT -> !entry.folder && entry.mime == "text/plain"
        }
    }
    if (order == GalleryOrder.ORIGINAL) return filtered
    // Kotlin's stable sort keeps stored order for ties, including legacy entries without dates.
    return filtered.sortedWith(compareBy<GalleryStore.Entry> { it.date <= 0 }
        .thenBy { if (order == GalleryOrder.NEWEST) -it.date else it.date })
}
