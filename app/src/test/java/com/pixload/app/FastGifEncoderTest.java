package com.pixload.app;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
        assertEquals("GIF89a", new String(data, 0, 6, StandardCharsets.US_ASCII));
        assertEquals(0x3b, data[data.length - 1] & 255);
    }
}
