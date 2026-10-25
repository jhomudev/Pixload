package com.pixload.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TitleNameTest {
    @Test public void keepsOriginalAccentsAndSpacing() {
        assertEquals("¡Ilustración de prueba!", DownloadService.titleName("¡Ilustración de prueba!"));
    }

    @Test public void supportsNonLatinTitles() {
        assertEquals("作品 🌸", DownloadService.titleName("作品 🌸"));
    }

    @Test public void replacesForbiddenCharactersAndEmptyNames() {
        assertEquals("Mi obra parte 1", DownloadService.titleName("  Mi obra / parte: 1?  "));
        assertEquals("Obra", DownloadService.titleName(".../\\"));
    }

    @Test public void limitsUtf8BytesWithoutCuttingCharacters() {
        assertEquals(180, DownloadService.titleName("花".repeat(100)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test public void singleIllustrationKeepsItsExtension() {
        assertEquals("Mi ilustración.png", DownloadService.flatFileName("Mi ilustración", "001.png"));
    }

    @Test public void foldersUseTitleFirstAndNumberOnlyOnCollision() {
        assertEquals("Mi obra", DownloadService.folderName("Mi obra", 1));
        assertEquals("Mi obra (2)", DownloadService.folderName("Mi obra", 2));
        assertEquals("Mi.obra (3)", DownloadService.folderName("Mi.obra", 3));
        assertEquals("Mi obra (31)", DownloadService.folderName("Mi obra", 31));
    }

    @Test public void confirmedRepeatStartsAtSecondNameEvenWithDifferentExtension() {
        assertEquals(1, DownloadService.startOrdinal(false));
        assertEquals(2, DownloadService.startOrdinal(true));
        assertEquals("Mi obra (2).gif", DownloadService.collisionName("Mi obra.gif", DownloadService.startOrdinal(true)));
    }

    @Test public void filesKeepExtensionAfterNumberWithoutSlug() {
        assertEquals("Mi obra.png", DownloadService.collisionName("Mi obra.png", 1));
        assertEquals("Mi obra (2).png", DownloadService.collisionName("Mi obra.png", 2));
        assertEquals("Mi obra (3).png", DownloadService.collisionName("Mi obra.png", 3));
        assertEquals("Mi obra (31).png", DownloadService.collisionName("Mi obra.png", 31));
    }

    @Test public void animationAndNovelUseTitleInsteadOfGenericName() {
        assertEquals("Mi animación.gif", DownloadService.flatFileName("Mi animación", "animacion.gif"));
        assertEquals("Mi animación.mp4", DownloadService.flatFileName("Mi animación", "animacion.mp4"));
        assertEquals("Mi novela.txt", DownloadService.flatFileName("Mi novela", "novela.txt"));
    }
}
