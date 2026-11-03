package com.pixload.app;

import android.graphics.Bitmap;
import android.media.MediaExtractor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** S4/S5: failure during setup, encode, finish and release; original terminal identity. */
@RunWith(AndroidJUnit4.class)
public class IndependentFallbackProbeTest {
    @Test public void terminalAndResourceMatrix() throws Exception {
        File directory = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "independent-fallback-").toFile();
        File zip = new File(directory, "input.zip");
        File output = new File(directory, "output.mp4");
        try {
            try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zip))) {
                Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(0xffff0000);
                    stream.putNextEntry(new ZipEntry("a.png"));
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
                    stream.closeEntry();
                } finally {
                    bitmap.recycle();
                }
            }
            byte[] before = IndependentMp4ProbeTest.digest(zip);
            Method decode = UgoiraConverter.class.getDeclaredMethod("decode", ZipFile.class, String.class, int.class);
            decode.setAccessible(true);
            String[] modes = {"setup", "encode0", "encode1", "finish", "close", "cancelFinish",
                    "cancelClose", "errorFinish", "missingFinish", "limitFinish"};
            for (String mode : modes) {
                output.delete();
                List<Integer> callbacks = new ArrayList<>();
                int[] decodes = {0};
                boolean[] closed = {false};
                Throwable terminal = mode.equals("errorFinish")
                        ? new AssertionError("callback error") : new IOException("callback cancel");
                JSONArray frames = IndependentMp4ProbeTest.frames("a.png", 100)
                        .put(IndependentMp4ProbeTest.frame(mode.equals("missingFinish") ? "missing.png" : "a.png",
                                mode.equals("limitFinish") ? 600000 : 100));
                Throwable caught = null;
                try {
                    Mp4Conversion.INSTANCE.convert(zip, frames, output, (done, total) -> {
                        callbacks.add(done);
                        if (mode.startsWith("cancel") || mode.equals("errorFinish")) {
                            IndependentFallbackProbeTest.<RuntimeException, Object>raise(terminal);
                        }
                    }, (archive, name, maxSize) -> {
                        if (++decodes[0] > 2 && !closed[0] && !mode.equals("setup")) {
                            throw new AssertionError("Replay before close");
                        }
                        try {
                            return (Bitmap) decode.invoke(UgoiraConverter.INSTANCE, archive, name, maxSize);
                        } catch (InvocationTargetException error) {
                            return IndependentFallbackProbeTest.<RuntimeException, Bitmap>raise(error.getCause());
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    }, (file, width, height) -> {
                        if (mode.equals("setup")) throw failure();
                        Mp4Conversion.Encoder delegate = Mp4HardwareEncoder.Companion.create(file, width, height);
                        return new Mp4Conversion.Encoder() {
                            int calls;

                            @Override public void encode(Bitmap bitmap, int count) {
                                if (mode.equals("encode0") || (mode.equals("encode1") && calls++ == 1)) {
                                    throw failure();
                                }
                                delegate.encode(bitmap, count);
                            }

                            @Override public void finish() {
                                if (mode.toLowerCase(Locale.ROOT).endsWith("finish")) throw failure();
                                delegate.finish();
                            }

                            @Override public void close() throws Exception {
                                try {
                                    delegate.close();
                                } finally {
                                    closed[0] = true;
                                }
                                if (mode.toLowerCase(Locale.ROOT).endsWith("close")) throw failure();
                            }
                        };
                    });
                } catch (Throwable error) {
                    caught = error;
                }
                assertArrayEquals(before, IndependentMp4ProbeTest.digest(zip));
                boolean cancelled = mode.startsWith("cancel") || mode.equals("errorFinish");
                boolean invalid = mode.startsWith("missing") || mode.startsWith("limit");
                if (cancelled) assertSame(mode, terminal, caught);
                else if (invalid) assertTrue(mode, caught instanceof IOException);
                else assertNull(mode + ":" + caught, caught);
                assertEquals(mode, cancelled || invalid ? Arrays.asList(1) : Arrays.asList(1, 2), callbacks);
                if (!mode.equals("setup")) assertTrue(mode, closed[0]);
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(output.getPath());
                    extractor.selectTrack(0);
                    int samples = 0;
                    while (extractor.getSampleTime() >= 0) {
                        samples++;
                        extractor.advance();
                    }
                    assertEquals(mode, cancelled || invalid ? 3 : 6, samples);
                } finally {
                    extractor.release();
                }
                IndependentMp4ProbeTest.report("OWN_FALLBACK " + mode + " callbacks=" + callbacks
                        + " closed=" + closed[0] + " decodes=" + decodes[0]);
            }
        } finally {
            output.delete();
            zip.delete();
            directory.delete();
        }
    }

    static Mp4Conversion.HardwareFailure failure() {
        return new Mp4Conversion.HardwareFailure(new IOException("independent hardware fault"));
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable, T> T raise(Throwable error) throws E {
        throw (E) error;
    }
}
