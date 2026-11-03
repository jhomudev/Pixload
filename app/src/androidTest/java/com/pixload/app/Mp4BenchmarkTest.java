package com.pixload.app;

import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.BitmapUtil;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;

/** Opt-in benchmark: pass -e mp4Fixture /path/to/synthetic/zip with frames.json. */
@RunWith(AndroidJUnit4.class)
public class Mp4BenchmarkTest {
    @Test public void measureHardwareAgainstSoftware() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("mp4Fixture");
        org.junit.Assume.assumeNotNull(path);
        File archive = new File(path);
        JSONArray frames;
        try (ZipFile zip = new ZipFile(archive);
             java.io.InputStream input = zip.getInputStream(zip.getEntry("frames.json"))) {
            frames = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        // Software/hardware, then hardware/software exposes order-dependent drift.
        for (int run = 0; run < 4; run++) {
            boolean hardware = run == 1 || run == 2;
            File output = new File(archive.getParentFile(), (hardware ? "hardware-" : "software-") + run + ".mp4");
            int[] callbacks = {0};
            UgoiraConverter.Progress progress = (done, total) -> {
                assertEquals(++callbacks[0], done);
                assertEquals(frames.length(), total);
            };
            long start = System.nanoTime();
            if (hardware) UgoiraConverter.mp4(archive, frames, output, progress);
            else Mp4HardwareEncoderTest.software(archive, frames, output, progress);
            long elapsed = System.nanoTime() - start;
            assertEquals(frames.length(), callbacks[0]);
            report("paired run=" + run + " path=" + (hardware ? "hardware" : "jcodec")
                    + " totalMs=" + ms(elapsed) + " bytes=" + output.length());
        }
    }

    @Test public void measureProductionAndPhases() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("mp4Fixture");
        org.junit.Assume.assumeNotNull(path);
        File archive = new File(path);
        JSONArray frames;
        try (ZipFile zip = new ZipFile(archive);
             java.io.InputStream input = zip.getInputStream(zip.getEntry("frames.json"))) {
            frames = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        File directory = archive.getParentFile();
        // Two orders expose warm-up/thermal drift rather than comparing unrelated fixtures.
        for (int round = 0; round < 2; round++) {
            if (round == 0) production(archive, frames, directory, round);
            phases(archive, frames, directory, round);
            if (round == 1) production(archive, frames, directory, round);
        }
    }

    private void production(File archive, JSONArray frames, File directory, int round) throws Exception {
        File output = new File(directory, "production-" + round + ".mp4");
        int[] callbacks = {0};
        long start = System.nanoTime();
        UgoiraConverter.mp4(archive, frames, output, (done, total) -> {
            assertEquals(++callbacks[0], done);
            assertEquals(frames.length(), total);
        });
        long elapsed = System.nanoTime() - start;
        assertEquals(frames.length(), callbacks[0]);
        report("production round=" + round + " totalMs=" + ms(elapsed)
                + " bytes=" + output.length() + " progress=" + callbacks[0]);
    }

    @Test public void measureEncodingBreakdown() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("mp4Fixture");
        org.junit.Assume.assumeNotNull(path);
        File archive = new File(path);
        try (ZipFile zip = new ZipFile(archive);
             java.io.InputStream input = zip.getInputStream(zip.getEntry("frames.json"))) {
            phases(archive, new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8)),
                    archive.getParentFile(), 2);
        }
    }

    private void phases(File archive, JSONArray frames, File directory, int round) throws Exception {
        // Use the exact private decoder, including bounds checks, subsampling and scaling.
        Method decode = UgoiraConverter.class.getDeclaredMethod("decode", ZipFile.class, String.class, int.class);
        decode.setAccessible(true);
        long decodeNs = 0, rgbNs = 0, nativeNs = 0, finishNs;
        long duration = 0, encoded = 0;
        long[] sinkNs = {0};
        long start = System.nanoTime();
        File output = new File(directory, "phases-" + round + ".mp4");
        try (ZipFile zip = new ZipFile(archive)) {
            long tick = System.nanoTime();
            Bitmap first = (Bitmap) decode.invoke(UgoiraConverter.INSTANCE, zip,
                    frames.getJSONObject(0).getString("file"), 1280);
            decodeNs += System.nanoTime() - tick;
            int width = first.getWidth() - first.getWidth() % 2;
            int height = first.getHeight() - first.getHeight() % 2;
            AndroidSequenceEncoder encoder = AndroidSequenceEncoder.createSequenceEncoder(output, 30);
            if (round == 2) {
                // Time the real sink: H.264 + packet copying/muxing, excluding RGB-to-YUV.
                Field field = org.jcodec.api.SequenceEncoder.class.getDeclaredField("sink");
                field.setAccessible(true);
                org.jcodec.api.transcode.Sink sink = (org.jcodec.api.transcode.Sink) field.get(encoder);
                Object timed = Proxy.newProxyInstance(sink.getClass().getClassLoader(),
                        new Class<?>[]{org.jcodec.api.transcode.Sink.class}, (proxy, method, args) -> {
                            boolean encoding = method.getName().equals("outputVideoFrame");
                            long before = encoding ? System.nanoTime() : 0;
                            try { return method.invoke(sink, args); }
                            catch (InvocationTargetException error) { throw error.getCause(); }
                            finally { if (encoding) sinkNs[0] += System.nanoTime() - before; }
                        });
                field.set(encoder, timed);
            }
            try {
                for (int i = 0; i < frames.length(); i++) {
                    tick = System.nanoTime();
                    Bitmap bitmap = i == 0 ? first : (Bitmap) decode.invoke(UgoiraConverter.INSTANCE,
                            zip, frames.getJSONObject(i).getString("file"), 1280);
                    Bitmap resized = bitmap.getWidth() == width && bitmap.getHeight() == height
                            ? bitmap : Bitmap.createScaledBitmap(bitmap, width, height, true);
                    decodeNs += System.nanoTime() - tick;
                    try {
                        duration += Math.max(20, frames.getJSONObject(i).optInt("delay", 100));
                        long needed = Math.max(1, Math.round(duration * 30 / 1000.0));
                        if (needed > 18000) throw new IllegalArgumentException("Benchmark exceeds ten minutes");
                        while (encoded < needed) {
                            // This is exactly AndroidSequenceEncoder.encodeImage, split at its call boundary.
                            tick = System.nanoTime();
                            Picture picture = BitmapUtil.fromBitmap(resized);
                            rgbNs += System.nanoTime() - tick;
                            tick = System.nanoTime();
                            encoder.encodeNativeFrame(picture);
                            nativeNs += System.nanoTime() - tick;
                            encoded++;
                        }
                    } finally {
                        if (resized != bitmap) resized.recycle();
                        bitmap.recycle();
                    }
                }
            } finally {
                tick = System.nanoTime();
                encoder.finish();
                finishNs = System.nanoTime() - tick;
            }
        }
        report("phases round=" + round + " totalMs=" + ms(System.nanoTime() - start)
                + " decodeScaleMs=" + ms(decodeNs) + " bitmapRgbMs=" + ms(rgbNs)
                + " nativeEncodeMs=" + ms(nativeNs) + " finishMs=" + ms(finishNs)
                + (round == 2 ? " h264MuxMs=" + ms(sinkNs[0])
                        + " yuvAndDispatchMs=" + ms(nativeNs - sinkNs[0]) : "")
                + " encoded=" + encoded + " bytes=" + output.length());
        assertEquals(300, encoded);
    }

    private static long ms(long ns) { return ns / 1_000_000; }

    private static void report(String text) {
        Bundle status = new Bundle();
        status.putString("stream", "MP4_BENCH " + text + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }
}
