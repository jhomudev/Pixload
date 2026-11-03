package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** S2/S4: independent public entry-point and immutable-input probes. */
@RunWith(AndroidJUnit4.class)
public class IndependentMp4ProbeTest {
    @Test public void publicContractMatrix() throws Exception {
        for (int size : new int[]{1, 3, 16, 321}) {
            File directory = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getCacheDir().toPath(), "independent-mp4-").toFile();
            File zip = new File(directory, "input.zip");
            File output = new File(directory, "output.mp4");
            try {
                try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zip))) {
                    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(Color.RED);
                        stream.putNextEntry(new ZipEntry("a.png"));
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
                        stream.closeEntry();
                    } finally {
                        bitmap.recycle();
                    }
                    stream.putNextEntry(new ZipEntry("bad.png"));
                    stream.write(new byte[]{1, 2, 3});
                    stream.closeEntry();
                }
                byte[] before = digest(zip);
                JSONArray[] cases = {
                        new JSONArray(),
                        frames("absent.png", 100),
                        frames("a.png", 100),
                        frames("a.png", 100).put(frame("absent.png", 100)),
                        frames("a.png", 100).put(frame("bad.png", 100)),
                        frames("a.png", 600017),
                        frames("a.png", 100).put(frame("a.png", 600000)),
                        frames("a.png", 20).put(frame("a.png", 20)).put(frame("a.png", 20)),
                        frames("a.png", 100).put(new JSONObject()),
                        frames("a.png", 100).put(frame("a.png", 100))
                };
                for (int test = 0; test < cases.length; test++) {
                    output.delete();
                    List<Integer> progress = new ArrayList<>();
                    Throwable caught = null;
                    RuntimeException cancellation = new RuntimeException("independent cancellation");
                    try {
                        final int index = test;
                        UgoiraConverter.mp4(zip, cases[test], output, (done, total) -> {
                            assertEquals(cases[index].length(), total);
                            progress.add(done);
                            if (index == 9) throw cancellation;
                        });
                    } catch (Throwable error) {
                        caught = error;
                    }
                    assertArrayEquals("ZIP must stay byte-identical after case " + test, before, digest(zip));
                    if (size >= 16 && test == 9) {
                        assertSame(cancellation, caught);
                        assertEquals(Arrays.asList(1), progress);
                    }
                    if (size >= 16 && test == 3) {
                        assertEquals(IOException.class, caught.getClass());
                        assertEquals("Falta el fotograma absent.png en el ZIP.", caught.getMessage());
                        assertEquals(Arrays.asList(1), progress);
                    }
                    if (size >= 16 && (test == 2 || test == 7)) assertNull(caught);
                    String video = "none";
                    if (output.exists() && output.length() > 0) {
                        MediaExtractor extractor = new MediaExtractor();
                        try {
                            extractor.setDataSource(output.getPath());
                            if (extractor.getTrackCount() > 0) {
                                MediaFormat format = extractor.getTrackFormat(0);
                                extractor.selectTrack(0);
                                int samples = 0;
                                while (extractor.getSampleTime() >= 0) {
                                    assertTrue(Math.abs(extractor.getSampleTime() - samples * 1000000L / 30) <= 1);
                                    samples++;
                                    extractor.advance();
                                }
                                video = format.getInteger(MediaFormat.KEY_WIDTH) + "x"
                                        + format.getInteger(MediaFormat.KEY_HEIGHT) + ":" + samples + ":"
                                        + format.getLong(MediaFormat.KEY_DURATION);
                                if (size >= 16 && test == 7) assertEquals(2, samples);
                            } else {
                                video = "zero-tracks";
                            }
                        } catch (IOException error) {
                            video = "unreadable";
                        } finally {
                            extractor.release();
                        }
                    }
                    report("OWN_PROBE " + size + "/" + test + " error="
                            + (caught == null ? "none" : caught.getClass().getSimpleName() + ":" + caught.getMessage())
                            + " progress=" + progress + " video=" + video);
                }
            } finally {
                output.delete();
                zip.delete();
                directory.delete();
            }
        }
    }

    static void report(String text) {
        Bundle status = new Bundle();
        status.putString("stream", text + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    static JSONArray frames(String name, int delay) throws Exception {
        return new JSONArray().put(frame(name, delay));
    }

    static JSONObject frame(String name, int delay) throws Exception {
        return new JSONObject().put("file", name).put("delay", delay);
    }

    static byte[] digest(File file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
    }
}
