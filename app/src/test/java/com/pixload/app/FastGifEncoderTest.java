package com.pixload.app;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FastGifEncoderTest {
    @Test public void legacyIndexedCallKeepsItsInterruptedCallerBehavior() throws Exception {
        byte[][] results = new byte[2][];
        for (int n = 0; n < 2; n++) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            FastGifEncoder encoder = new FastGifEncoder(output, 128, 64);
            if (n == 1) Thread.currentThread().interrupt();
            try { encoder.addIndexed(new byte[8192], 100); }
            finally { Thread.interrupted(); }
            encoder.finish();
            results[n] = output.toByteArray();
        }
        org.junit.Assert.assertArrayEquals(results[0], results[1]);
    }

    @Test public void productionIndexedCallStillCancelsInsideLzw() throws Exception {
        java.io.OutputStream output = new java.io.OutputStream() {
            @Override public void write(int value) { Thread.currentThread().interrupt(); }
        };
        FastGifEncoder encoder = new FastGifEncoder(output, 300, 300, new byte[768]);
        byte[] frame = new byte[90000];
        new java.util.Random(51).nextBytes(frame);
        try {
            assertEquals("Conversión interrumpida", org.junit.Assert.assertThrows(java.io.IOException.class,
                    () -> encoder.addIndexedCentiseconds(frame, 10)).getMessage());
        } finally { Thread.interrupted(); }
    }

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
