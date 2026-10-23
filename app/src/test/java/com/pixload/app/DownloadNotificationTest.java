package com.pixload.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DownloadNotificationTest {
    @Test public void keepsArtworkTitleOutOfDescription() {
        assertEquals("Archivo 2/5 · 42 %", DownloadService.notificationDetail("Mi obra · 2/5", "Mi obra", 42, 100, false));
        assertEquals("Preparando descarga…", DownloadService.notificationDetail("Preparando descarga…", "Mi obra", 0, 0, false));
    }

    @Test public void describesConversionAndAnimationProgress() {
        assertEquals("Convirtiendo MP4 · 9/30 · 30 %",
                DownloadService.notificationDetail("Convirtiendo MP4 · 9/30", "Mi obra", 9, 30, false));
        assertEquals("Descargando animación · Archivo 1/1 · 50 %",
                DownloadService.notificationDetail("Descargando animación · Mi obra · 1/1", "Mi obra", 50, 100, false));
    }

    @Test public void displaysCompletionAndFailure() {
        assertEquals("Descarga completada", DownloadService.notificationDetail("Descarga completada: Mi obra", "Mi obra", 100, 100, true));
        assertEquals("Error: sin espacio", DownloadService.notificationDetail("Error: sin espacio", "Mi obra", 0, 0, true));
        assertEquals("Descarga cancelada", DownloadService.notificationDetail("Descarga cancelada", "Mi obra", 0, 0, true));
    }
}
