package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the literal prefix removal used by GalleryStore.importLast. The Java original used
 * java.lang.String.replaceFirst, which is regex based; the Kotlin literal replaceFirst overload
 * silently never matched, so this guards the behavior without needing a device.
 */
class GalleryStorePrefixTest {
    @Test
    fun stripsTheCompletionPrefix() {
        assertEquals("Mi obra", stripCompletedPrefix("Descarga completada: Mi obra"))
    }

    @Test
    fun keepsTitlesWithoutThePrefix() {
        assertEquals("Obra", stripCompletedPrefix("Obra"))
        assertEquals("", stripCompletedPrefix(""))
    }

    @Test
    fun stripsOnlyTheLeadingOccurrence() {
        assertEquals("Descarga completada: otra", stripCompletedPrefix("Descarga completada: Descarga completada: otra"))
        assertEquals("xDescarga completada: anidada", stripCompletedPrefix("xDescarga completada: anidada"))
    }
}
