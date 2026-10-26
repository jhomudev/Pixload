package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PixivLinksTest {
    @Test fun extractsShareLinksAndKeepsQuery() {
        assertEquals("https://www.pixiv.net/artworks/12345", PixivLinks.extract(
            "Título / Autor | pixiv\nhttps://www.pixiv.net/artworks/12345\nMira esta ilustración"))
        assertEquals("https://www.pixiv.net/novel/show.php?id=456", PixivLinks.extract(
            "『Mi novela』 (https://www.pixiv.net/novel/show.php?id=456)."))
        assertEquals("HTTP://pixiv.net/en/artworks/99", PixivLinks.extract(
            "https://example.org/abc y HTTP://pixiv.net/en/artworks/99。"))
    }

    @Test fun rejectsUnicodeLookalikePixivDomain() {
        assertNull(PixivLinks.extract("https://pıxiv.net/artworks/1"))
    }

    @Test fun rejectsNonPixivLinks() {
        assertNull(PixivLinks.extract(null))
        assertNull(PixivLinks.extract("Obra sin enlace"))
        assertNull(PixivLinks.extract("https://fakepixiv.net/artworks/1"))
    }
}
