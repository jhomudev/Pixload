package com.pixload.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

    @Test public void boundTransferPreservesOriginChecksAndRequestHeaders() throws Exception {
        ConnectivityManager manager = (ConnectivityManager) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        org.junit.Assume.assumeNotNull(network);
        try {
            PixivClient.open("https://fakepixiv.net/image.jpg", network);
            fail("Expected origin rejection before connection");
        } catch (IOException expected) {
            assertEquals("Origen de archivo no permitido.", expected.getMessage());
        }
        java.net.HttpURLConnection image = PixivClient.open("https://i.pximg.net/image.jpg", network);
        try {
            assertEquals("https://www.pixiv.net/", image.getRequestProperty("Referer"));
            assertNotNull(image.getRequestProperty("User-Agent"));
            assertEquals(null, image.getRequestProperty("Cookie"));
            assertEquals(false, image.getInstanceFollowRedirects());
        } finally {
            image.disconnect();
        }
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
