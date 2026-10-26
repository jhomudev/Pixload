package com.pixload.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadNamesTest {
    @Test fun preservesAndroidFilenamePolicy() {
        assertEquals("¡Ilustración de prueba!", DownloadNames.titleName("¡Ilustración de prueba!"))
        assertEquals("作品 🌸", DownloadNames.titleName("作品 🌸"))
        assertEquals("Mi obra parte 1", DownloadNames.titleName("  Mi obra / parte: 1?  "))
        assertEquals("Obra", DownloadNames.titleName(".../\\"))
        assertEquals("a b", DownloadNames.titleName("a\u200Bb"))
        assertEquals("a b", DownloadNames.titleName("a\u2060b"))
        assertEquals("a\u00A0b", DownloadNames.titleName("a\u00A0b"))
        assertEquals("a\u2003b", DownloadNames.titleName("a\u2003b"))
        assertEquals("a b", DownloadNames.titleName("a\t\nb"))
        assertEquals(180, DownloadNames.titleName("花".repeat(100)).encodeToByteArray().size)
        assertEquals("🌸".repeat(45), DownloadNames.titleName("🌸".repeat(46)))
        assertEquals("a".repeat(179), DownloadNames.titleName("a".repeat(179) + "."))
        assertEquals("a".repeat(179) + "\uD800", DownloadNames.titleName("a".repeat(179) + "\uD800" + "b"))

        assertEquals("Mi ilustración.png", DownloadNames.flatFileName("Mi ilustración", "001.png"))
        assertEquals("Mi animación.gif", DownloadNames.flatFileName("Mi animación", "animacion.gif"))
        assertEquals("Mi animación.mp4", DownloadNames.flatFileName("Mi animación", "animacion.mp4"))
        assertEquals("Mi novela.txt", DownloadNames.flatFileName("Mi novela", "novela.txt"))
        assertEquals(1, DownloadNames.startOrdinal(false))
        assertEquals(2, DownloadNames.startOrdinal(true))
        for (ordinal in listOf(1, 2, 3, 31)) {
            val suffix = if (ordinal == 1) "" else " ($ordinal)"
            assertEquals("Mi obra$suffix", DownloadNames.folderName("Mi obra", ordinal))
            assertEquals("Mi obra$suffix.png", DownloadNames.collisionName("Mi obra.png", ordinal))
        }
        assertEquals("Mi.obra (3)", DownloadNames.folderName("Mi.obra", 3))
        assertEquals("Mi obra (2).gif", DownloadNames.collisionName("Mi obra.gif", DownloadNames.startOrdinal(true)))
    }
}
