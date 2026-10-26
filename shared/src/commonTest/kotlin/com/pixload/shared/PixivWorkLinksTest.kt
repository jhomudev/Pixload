package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PixivWorkLinksTest {
    private fun work(host: String? = "www.pixiv.net", scheme: String? = "https",
                     path: String? = "/artworks/123", queryId: String? = null) =
        PixivWorkLinks.classify(host, scheme, path, queryId)

    @Test fun hostIsCheckedBeforeSchemeAndId() {
        for (host in listOf(null, "fakepixiv.net", "i.pximg.net", "www.pixiv.net.evil")) {
            assertEquals(PixivWorkLinks.Rejected(PixivWorkLinks.Reason.HOST),
                work(host = host, scheme = "ftp", path = "/no-id"))
        }
        assertEquals(PixivWorkLinks.Work("123", false), work(host = "PIXIV.NET"))
        assertEquals(PixivWorkLinks.Work("123", false), work(host = "WWW.PIXIV.NET"))
    }

    @Test fun schemeIsCheckedBeforeId() {
        for (scheme in listOf(null, "ftp", "httpsx")) {
            assertEquals(PixivWorkLinks.Rejected(PixivWorkLinks.Reason.SCHEME),
                work(scheme = scheme, path = "/no-id"))
        }
        assertEquals(PixivWorkLinks.Work("123", false), work(scheme = "HTTP"))
        assertEquals(PixivWorkLinks.Work("123", false), work(scheme = "hTtPs"))
    }

    @Test fun queryIdOverridesPathEvenWhenInvalidOrEmpty() {
        assertEquals(PixivWorkLinks.Work("007", false), work(queryId = "007"))
        for (query in listOf("", "abc", "12a", "-1", "１２")) {
            assertEquals(PixivWorkLinks.Rejected(PixivWorkLinks.Reason.ID), work(queryId = query))
        }
        assertEquals(PixivWorkLinks.Work("456", false), work(path = "/other", queryId = "456"))
    }

    @Test fun pathIdRequiresAsciiDigitsAndExactLowercaseSegment() {
        assertEquals(PixivWorkLinks.Work("123", false), work(path = "/en/artworks/123/extra"))
        assertEquals(PixivWorkLinks.Work("456", true), work(path = "/novels/456"))
        for (path in listOf(null, "", "/Artworks/123", "/notartworks/123", "/artworks/12a", "/artworks/１２", "/artworks/123x")) {
            assertEquals(PixivWorkLinks.Rejected(PixivWorkLinks.Reason.ID), work(path = path))
        }
    }

    @Test fun novelClassificationIsCaseSensitiveSubstringIndependentOfIdSource() {
        assertEquals(PixivWorkLinks.Work("3", true), work(path = "/novel/show.php", queryId = "3"))
        assertEquals(PixivWorkLinks.Work("3", true), work(path = "/notnovels/3", queryId = "3"))
        assertEquals(PixivWorkLinks.Work("3", false), work(path = "/Novel/show.php", queryId = "3"))
        assertEquals(PixivWorkLinks.Work("3", false), work(path = null, queryId = "3"))
    }
}
