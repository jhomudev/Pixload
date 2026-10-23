package com.pixload.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PixivLinkTest {
    @Test public void extractsArtworkLinkFromPixivShareText() {
        assertEquals("https://www.pixiv.net/artworks/12345", PixivLink.extract(
                "Título de la obra / Autor | pixiv\nhttps://www.pixiv.net/artworks/12345\nMira esta ilustración"));
    }

    @Test public void preservesNovelQueryAndTrimsTrailingPunctuation() {
        assertEquals("https://www.pixiv.net/novel/show.php?id=456", PixivLink.extract(
                "『Mi novela』 (https://www.pixiv.net/novel/show.php?id=456)."));
    }

    @Test public void findsPixivInsteadOfAnotherDomain() {
        assertEquals("HTTP://pixiv.net/en/artworks/99", PixivLink.extract(
                "https://example.org/abc y HTTP://pixiv.net/en/artworks/99。"));
    }

    @Test public void rejectsClipboardWithoutPixivUrl() {
        assertNull(PixivLink.extract("Obra sin enlace"));
        assertNull(PixivLink.extract("https://fakepixiv.net/artworks/1"));
        assertNull(PixivLink.extract(null));
    }
}
