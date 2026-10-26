package com.pixload.app;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertArrayEquals;

public class PixivClientReadTest {
    @Test public void preservesBytesAcrossBufferBoundary() throws Exception {
        byte[] original = new byte[16_385];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;
        assertArrayEquals(original, PixivClient.read(new ByteArrayInputStream(original)));
    }
}
