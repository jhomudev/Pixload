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

    @Test public void animationAndNovelUseTitleInsteadOfGenericName() {
        assertEquals("Mi animación.gif", DownloadService.flatFileName("Mi animación", "animacion.gif"));
        assertEquals("Mi animación.mp4", DownloadService.flatFileName("Mi animación", "animacion.mp4"));
        assertEquals("Mi novela.txt", DownloadService.flatFileName("Mi novela", "novela.txt"));
    }
}
