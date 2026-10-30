package com.pixload.app

/**
 * Resolves the gallery page a horizontal swipe intends to reach.
 *
 * The result is null whenever the direction leaves the available pages, so callers never index
 * outside [0, size) when a gesture reverses at the first or last entry.
 */
internal object GallerySwipe {
    fun target(current: Int, direction: Int, size: Int): Int? {
        if (size <= 0) return null
        val target = current + direction
        return if (target in 0 until size) target else null
    }
}
