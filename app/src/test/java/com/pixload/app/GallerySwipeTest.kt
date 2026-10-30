package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GallerySwipeTest {
    @Test fun forwardSwipeOnLastPageHasNoTarget() {
        assertNull("Swiping past the last page must not resolve an index", GallerySwipe.target(1, 1, 2))
    }

    @Test fun backwardSwipeOnFirstPageHasNoTarget() {
        assertNull("Swiping before the first page must not resolve an index", GallerySwipe.target(0, -1, 2))
    }

    @Test fun swipeInsideBoundsResolvesTheNeighbouringPage() {
        assertEquals(1, GallerySwipe.target(0, 1, 2))
        assertEquals(0, GallerySwipe.target(1, -1, 2))
    }

    @Test fun emptyGalleryNeverResolvesATarget() {
        assertNull(GallerySwipe.target(0, 1, 0))
    }
}
