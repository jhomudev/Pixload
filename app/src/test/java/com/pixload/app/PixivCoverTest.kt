package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PixivCoverTest {
    private val small = "https://i.pximg.net/c/540x540_70/img-master/img/2024/01/01/00/00/00/12345_p0_square1200.jpg"
    private val regular = "https://i.pximg.net/c/1200x1200_90/img-master/img/2024/01/01/00/00/00/12345_p0_master1200.jpg"

    @Test fun workCoverUsesTheSmallVariantWhenPixivProvidesIt() {
        assertEquals(small, coverThumbnail(small, regular))
    }

    @Test fun workCoverFallsBackToTheRegularVariantAndStaysEmptyWhenBothAreMissing() {
        assertEquals(regular, coverThumbnail("", regular))
        assertEquals("", coverThumbnail("", ""))
    }
}
