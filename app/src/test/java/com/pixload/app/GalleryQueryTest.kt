package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryQueryTest {
    private fun entry(uri: String, title: String, mime: String = "image/png", folder: Boolean = false, date: Long = 0) =
        GalleryStore.Entry(uri, title, mime, "", folder, 1, date)

    @Test fun searchesTitlesCaseInsensitivelyWithoutChangingHistory() {
        val entries = listOf(entry("1", "Árbol Azul"), entry("2", "Otro"), entry("3", "azulejo"))
        assertEquals(listOf("1", "3"), galleryQuery(entries, "AZU", GalleryType.ALL, GalleryOrder.ORIGINAL).map { it.uri })
        assertEquals(emptyList<GalleryStore.Entry>(), galleryQuery(entries, "ninguno", GalleryType.ALL, GalleryOrder.ORIGINAL))
        assertEquals(listOf("1", "2", "3"), entries.map { it.uri })
    }

    @Test fun filtersAvailableTypesTogetherWithSearch() {
        val entries = listOf(entry("1", "obra jpg", "image/jpeg"), entry("2", "obra gif", "image/gif"),
            entry("3", "obra mp4", "video/mp4"), entry("4", "obra txt", "text/plain"),
            entry("5", "obra carpeta", "image/jpeg", true), entry("6", "otro png"))
        val types = listOf(GalleryType.IMAGE, GalleryType.GIF, GalleryType.MP4, GalleryType.TXT, GalleryType.FOLDER)
        for ((index, type) in types.withIndex()) {
            assertEquals(listOf((index + 1).toString()), galleryQuery(entries, "OBRA", type, GalleryOrder.ORIGINAL).map { it.uri })
        }
        assertEquals(listOf("6"), galleryQuery(entries, "otro", GalleryType.IMAGE, GalleryOrder.ORIGINAL).map { it.uri })
    }

    @Test fun filtersEpubNovelsWithoutTreatingThemAsTxt() {
        val entries = listOf(entry("epub", "Story", "application/epub+zip"), entry("txt", "Story", "text/plain"))
        assertEquals(listOf("epub"), galleryQuery(entries, "story", GalleryType.EPUB, GalleryOrder.ORIGINAL).map { it.uri })
        assertEquals(listOf("txt"), galleryQuery(entries, "story", GalleryType.TXT, GalleryOrder.ORIGINAL).map { it.uri })
    }

    @Test fun sortsByRegisteredDateStablyWithUndatedEntriesLast() {
        val entries = listOf(entry("undated", "A"), entry("old", "B", date = 10),
            entry("tie1", "C", date = 20), entry("tie2", "D", date = 20), entry("missing", "E"))
        assertEquals(listOf("tie1", "tie2", "old", "undated", "missing"),
            galleryQuery(entries, "", GalleryType.ALL, GalleryOrder.NEWEST).map { it.uri })
        assertEquals(listOf("old", "tie1", "tie2", "undated", "missing"),
            galleryQuery(entries, "", GalleryType.ALL, GalleryOrder.OLDEST).map { it.uri })
        assertEquals(emptyList<GalleryStore.Entry>(), galleryQuery(emptyList(), "", GalleryType.ALL, GalleryOrder.NEWEST))
    }

    @Test fun defaultQueryPreservesValidatedListingAndFolderCoverSemantics() {
        val entries = listOf(entry("folder", "Folder", "image/gif", true), entry("gif", "GIF", "image/gif"))
        assertEquals(entries, galleryQuery(entries, "", GalleryType.ALL, GalleryOrder.ORIGINAL))
        assertEquals(listOf("folder"), galleryQuery(entries, "", GalleryType.FOLDER, GalleryOrder.ORIGINAL).map { it.uri })
        assertEquals(listOf("gif"), galleryQuery(entries, "", GalleryType.GIF, GalleryOrder.ORIGINAL).map { it.uri })
    }
}
