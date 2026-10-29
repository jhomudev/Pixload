package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryFormatTest {
    @Test fun folderTakesPriorityOverTheCoverImageMime() {
        assertEquals("Carpeta", galleryFormat(true, "image/jpeg"))
    }

    @Test fun supportedFilesHaveShortReadableTypes() {
        assertEquals("MP4", galleryFormat(false, "video/mp4"))
        assertEquals("GIF", galleryFormat(false, "image/gif"))
        assertEquals("TXT", galleryFormat(false, "text/plain"))
        assertEquals("EPUB", galleryFormat(false, "application/epub+zip"))
        assertEquals("JPG", galleryFormat(false, "image/jpeg"))
        assertEquals("PNG", galleryFormat(false, "image/png"))
        assertEquals("WEBP", galleryFormat(false, "image/webp"))
        assertEquals("Archivo", galleryFormat(false, "application/x-unexpected"))
    }
}
