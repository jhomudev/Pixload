package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixivOriginsTest {
    @Test fun onlyHttpsOnExactAllowedHostsIsPermitted() {
        for (host in listOf("www.pixiv.net", "pixiv.net", "i.pximg.net")) {
            assertTrue(PixivOrigins.isAllowed("https", host))
            assertTrue(PixivOrigins.isAllowed("HTTPS", host))
            assertFalse(PixivOrigins.isAllowed("http", host))
            assertFalse(PixivOrigins.isAllowed("ftp", host))
            assertFalse(PixivOrigins.isAllowed(null, host))
        }
        for (host in listOf(null, "", "evil.pixiv.net", "www.pixiv.net.evil", "i.pximg.net.evil", "pixiv.net.", "PIXIV.NET")) {
            assertFalse(PixivOrigins.isAllowed("https", host))
        }
    }

    @Test fun cookiesAreOnlyForExactPixivHosts() {
        assertTrue(PixivOrigins.acceptsCookies("www.pixiv.net"))
        assertTrue(PixivOrigins.acceptsCookies("pixiv.net"))
        for (host in listOf(null, "", "i.pximg.net", "evil.pixiv.net", "pixiv.net.evil", "PIXIV.NET")) {
            assertFalse(PixivOrigins.acceptsCookies(host))
        }
    }
}
