package com.pixload.app;

import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Writes indexed GIF frames with a fixed RGB 3:3:2 palette and a reusable LZW dictionary. */
final class FastGifEncoder {
    private static final int CLEAR = 256;
    private static final int END = 257;
    private static final int MAX_CODE = 4096;
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private final OutputStream out;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final byte[] indexed;
    private final int[] keys = new int[8192];
    private final int[] values = new int[8192];
    private final int[] generations = new int[8192];
    private int generation;

    FastGifEncoder(OutputStream output, int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > 65535 || height > 65535) {
            throw new IOException("Dimensiones de GIF no válidas.");
        }
        this.out = new BufferedOutputStream(output, 65536);
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.indexed = new byte[width * height];
        out.write(new byte[]{'G', 'I', 'F', '8', '9', 'a'});
        shortValue(width);
        shortValue(height);
        out.write(0xf7); // Global 256-color table, 8-bit color resolution.
        out.write(0);
        out.write(0);
        for (int i = 0; i < 256; i++) {
            out.write((i >>> 5) * 255 / 7);
            out.write(((i >>> 2) & 7) * 255 / 7);
            out.write((i & 3) * 85);
        }
        out.write(new byte[]{0x21, (byte) 0xff, 11, 'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0', 3, 1, 0, 0, 0});
    }

    void addFrame(Bitmap bitmap, int delayMillis) throws IOException {
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) throw new IOException("Tamaño de fotograma distinto.");
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pos = y * width + x;
                int rgb = pixels[pos];
                int dither = BAYER[(y & 3) * 4 + (x & 3)] - 8;
                int red = clamp(((rgb >>> 16) & 255) + dither * 2) >>> 5;
                int green = clamp(((rgb >>> 8) & 255) + dither * 2) >>> 5;
                int blue = clamp((rgb & 255) + dither * 4) >>> 6;
                indexed[pos] = (byte) ((red << 5) | (green << 2) | blue);
            }
        }
        addIndexed(indexed, delayMillis);
    }

    void addIndexed(byte[] frame, int delayMillis) throws IOException {
        if (frame.length != indexed.length) throw new IOException("Tamaño de fotograma distinto.");
        int delay = Math.max(2, Math.min(65535, Math.round(delayMillis / 10f)));
        out.write(new byte[]{0x21, (byte) 0xf9, 4, 0, (byte) delay, (byte) (delay >>> 8), 0, 0});
        out.write(0x2c);
        shortValue(0);
        shortValue(0);
        shortValue(width);
        shortValue(height);
        out.write(0); // Use the global color table.
        out.write(8); // Minimum LZW code size for the global 256-color palette.
        encode(frame);
    }

    void finish() throws IOException {
        out.write(0x3b);
        out.flush();
    }

    private void encode(byte[] frame) throws IOException {
        Bits bits = new Bits();
        int size = 9;
        int next = END + 1;
        generation++;
        bits.write(CLEAR, size);
        int prefix = frame[0] & 255;
        for (int i = 1; i < frame.length; i++) {
            int symbol = frame[i] & 255;
            int key = (prefix << 8) | symbol;
            int slot = (key * 0x9e3779b9) >>> 19;
            while (generations[slot] == generation && keys[slot] != key) slot = (slot + 1) & 8191;
            if (generations[slot] == generation) {
                prefix = values[slot];
            } else {
                bits.write(prefix, size);
                if (next < MAX_CODE) {
                    generations[slot] = generation;
                    keys[slot] = key;
                    values[slot] = next++;
                    if (next > (1 << size) && size < 12) size++;
                } else {
                    bits.write(CLEAR, size);
                    generation++;
                    next = END + 1;
                    size = 9;
                }
                prefix = symbol;
            }
        }
        bits.write(prefix, size);
        bits.write(END, size);
        bits.finish();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private void shortValue(int value) throws IOException {
        out.write(value & 255);
        out.write((value >>> 8) & 255);
    }

    private final class Bits {
        private final byte[] block = new byte[255];
        private int count;
        private int buffer;
        private int remaining;

        void write(int code, int width) throws IOException {
            buffer |= code << remaining;
            remaining += width;
            while (remaining >= 8) {
                put(buffer & 255);
                buffer >>>= 8;
                remaining -= 8;
            }
        }

        void finish() throws IOException {
            if (remaining > 0) put(buffer & 255);
            flushBlock();
            out.write(0);
        }

        private void put(int value) throws IOException {
            block[count++] = (byte) value;
            if (count == 255) flushBlock();
        }

        private void flushBlock() throws IOException {
            if (count == 0) return;
            out.write(count);
            out.write(block, 0, count);
            count = 0;
        }
    }
}
