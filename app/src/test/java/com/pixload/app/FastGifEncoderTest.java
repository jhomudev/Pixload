package com.pixload.app;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FastGifEncoderTest {
    @Test public void writesAnimatedGifWithIndexedFrames() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FastGifEncoder encoder = new FastGifEncoder(output, 256, 256);
        for (int frame = 0; frame < 3; frame++) {
            byte[] pixels = new byte[256 * 256];
            for (int i = 0; i < pixels.length; i++) pixels[i] = (byte) (i * 37 + frame * 60);
            encoder.addIndexed(pixels, 100);
        }
        encoder.finish();
        byte[] data = output.toByteArray();
        assertTrue(data.length > 1024);
        assertEquals(38565, data.length);
        assertEquals("0406f01ef9791639ce12f08afeab3b8c6e5fc4b44b526e224dfaa61b26bdb0d6",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        assertEquals("GIF89a", new String(data, 0, 6, StandardCharsets.US_ASCII));
        assertEquals(0x3b, data[data.length - 1] & 255);
    }
}
