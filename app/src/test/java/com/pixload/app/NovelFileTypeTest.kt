package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NovelFileTypeTest {
    @Test fun epubKeepsTheFlatUnicodeNameCollisionSuffixAndCorrectMime() {
        val name = DownloadService.flatFileName(DownloadService.titleName("Café 日本語 😀"), "novela.epub")
        assertEquals("Café 日本語 😀.epub", name)
        assertEquals("Café 日本語 😀 (2).epub", DownloadService.collisionName(name, 2))
        assertEquals("application/epub+zip", DownloadService.mime(name))
        assertEquals("application/epub+zip", DownloadService.mime(DownloadService.collisionName(name, 2)))
    }

    @Test fun txtNamingAndMimeStayUnchanged() {
        assertEquals("Título.txt", DownloadService.flatFileName("Título", "novela.txt"))
        assertEquals("Título (3).txt", DownloadService.collisionName("Título.txt", 3))
        assertEquals("text/plain", DownloadService.mime("Título.txt"))
    }
}
