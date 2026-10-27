package com.pixload.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pins progress bounds in the notification helper shared by download stages. */
public class DownloadServiceBoundaryTest {
    @Test public void clampsProgressAndKeepsUnknownTotal() {
        assertEquals("Archivo 1/3 · 0 %",
                DownloadService.notificationDetail("T · 1/3", "T", -1, 100, false));
        assertEquals("Archivo 1/3 · 100 %",
                DownloadService.notificationDetail("T · 1/3", "T", 101, 100, false));
        assertEquals("Archivo 1/3",
                DownloadService.notificationDetail("T · 1/3", "T", 1, 0, false));
    }
}
