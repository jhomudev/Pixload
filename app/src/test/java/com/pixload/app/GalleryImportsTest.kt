package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins which stored entries the cached gallery listing may import when a download completes.
 * Hidden history stays in the store on purpose, so the filter, not the caller, has to keep it out.
 */
class GalleryImportsTest {
    private fun entry(uri: String) = GalleryStore.Entry(uri, "Obra", "image/png", "", false, 1, 0)

    @Test
    fun importsOnlyNewEntriesThatStillResolve() {
        val shown = entry("content://gallery/shown")
        val hidden = entry("content://gallery/hidden")
        val missing = entry("content://gallery/missing")
        val fresh = entry("content://gallery/fresh")
        val imported = galleryImports(listOf(shown, hidden, missing, fresh), setOf(shown.uri), setOf(hidden.uri)) {
            it.uri != missing.uri
        }
        assertEquals(listOf(fresh), imported)
    }

    @Test
    fun hiddenHistoryNeverComesBackEvenWhenItResolves() {
        val hidden = entry("content://gallery/hidden")
        assertEquals(emptyList<GalleryStore.Entry>(), galleryImports(listOf(hidden), emptySet(), setOf(hidden.uri)) { true })
    }
}
