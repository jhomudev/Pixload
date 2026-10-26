package com.pixload.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class PixivClientTest {
    @Test public void rejectsInvalidLinksAndOriginsBeforeAnyNetworkRequest() throws Exception {
        assertLoadRejected("https://fakepixiv.net/artworks/123", "Introduce un enlace de pixiv.net.");
        assertLoadRejected("ftp://www.pixiv.net/artworks/123", "El enlace debe usar HTTP o HTTPS.");
        assertLoadRejected("https://www.pixiv.net/users/123", "Enlace de obra no reconocido.");
        assertOriginRejected("https://fakepixiv.net/image.jpg");
        assertOriginRejected("http://i.pximg.net/image.jpg");
    }

    @Test public void keepsRejectionPrecedenceAndImageExtensions() throws Exception {
        assertLoadRejected("ftp://fakepixiv.net/users/no-id", "Introduce un enlace de pixiv.net.");
        assertLoadRejected("ftp://www.pixiv.net/users/no-id", "El enlace debe usar HTTP o HTTPS.");
        assertLoadRejected("https://www.pixiv.net/users/no-id", "Enlace de obra no reconocido.");
        assertEquals("png", PixivClient.extension("https://i.pximg.net/a.PNG?x=1"));
        assertEquals("gif", PixivClient.extension("https://i.pximg.net/a.GIF"));
        assertEquals("zip", PixivClient.extension("https://i.pximg.net/a.ZIP"));
        assertEquals("jpg", PixivClient.extension("https://i.pximg.net/a.svg"));
    }

    private static void assertLoadRejected(String address, String message) throws Exception {
        try {
            PixivClient.load(address);
            fail("Expected rejection: " + address);
        } catch (IOException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    private static void assertOriginRejected(String address) throws Exception {
        try {
            PixivClient.open(address);
            fail("Expected origin rejection: " + address);
        } catch (IOException expected) {
            assertEquals("Origen de archivo no permitido.", expected.getMessage());
        }
    }
}
