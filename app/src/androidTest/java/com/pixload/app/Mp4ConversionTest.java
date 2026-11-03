package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Synthetic files only: pin MP4 behavior before considering a performance change. */
@RunWith(AndroidJUnit4.class)
public class Mp4ConversionTest {
    @Test public void preservesCumulativeTimingColorsAndProgressWithOddDimensions() throws Exception {
        try (Fixture fixture = new Fixture(65, 35)) {
            JSONArray frames = new JSONArray();
            int[] delays = {20, 20, 60, 33, 0};
            for (int delay : delays) frames.put(frame("red.jpg", delay));
            frames.put(new JSONObject().put("file", "blue.jpg")); // Missing delay is 100 ms.
            List<Integer> progress = new ArrayList<>();
            UgoiraConverter.mp4(fixture.archive, frames, fixture.output, (done, total) -> {
                assertEquals(6, total);
                progress.add(done);
            });
            assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), progress);
            // 253 ms rounds cumulatively to 8 frames, rather than rounding each delay.
            assertVideo(fixture.output, 64, 34, 8);
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(fixture.output.getAbsolutePath());
                assertEquals("video/mp4", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
                assertColor(retriever, 0, Color.RED);
                assertColor(retriever, 240000, Color.BLUE);
            } finally { retriever.release(); }
        }
    }

    @Test public void capsLargestDimensionWithoutReducingFrameRate() throws Exception {
        try (Fixture fixture = new Fixture(1441, 811)) {
            UgoiraConverter.mp4(fixture.archive, new JSONArray().put(frame("red.jpg", 100)),
                    fixture.output, (done, total) -> {});
            assertVideo(fixture.output, 1280, 720, 3);
        }
    }

    @Test public void cancellationPropagatesAndFinishesOnlyTheCompletedFrame() throws Exception {
        try (Fixture fixture = new Fixture(32, 32)) {
            IOException cancelled = new IOException("Cancelado");
            List<Integer> progress = new ArrayList<>();
            JSONArray frames = new JSONArray().put(frame("red.jpg", 100)).put(frame("blue.jpg", 100));
            try {
                UgoiraConverter.mp4(fixture.archive, frames, fixture.output, (done, total) -> {
                    assertEquals(2, total);
                    progress.add(done);
                    throw cancelled;
                });
                fail("Cancellation must escape the conversion");
            } catch (IOException expected) { assertSame(cancelled, expected); }
            assertEquals(Arrays.asList(1), progress);
            assertVideo(fixture.output, 32, 32, 3);
        }
    }

    @Test public void acceptsRoundedTenMinuteBoundaryAndRejectsTheNextFrame() throws Exception {
        try (Fixture fixture = new Fixture(16, 16)) {
            // 600016 ms still rounds to 18000 samples; the next minimum delay crosses the cap.
            JSONArray frames = new JSONArray().put(frame("red.jpg", 600016)).put(frame("blue.jpg", 20));
            List<Integer> progress = new ArrayList<>();
            try {
                UgoiraConverter.mp4(fixture.archive, frames, fixture.output, (done, total) -> {
                    assertEquals(2, total);
                    progress.add(done);
                });
                fail("An animation exceeding 18000 samples must fail");
            } catch (IOException expected) {
                assertEquals("Animación demasiado larga para convertir a MP4 (máximo 10 minutos).",
                        expected.getMessage());
            }
            assertEquals(Arrays.asList(1), progress);
            assertVideo(fixture.output, 16, 16, 18000);
        }
    }

    @Test public void rejectsEmptyMetadataAndMissingFrames() throws Exception {
        try (Fixture fixture = new Fixture(32, 32)) {
            assertError(fixture, new JSONArray(), "La animación no contiene fotogramas.");
            assertError(fixture, new JSONArray().put(frame("missing.jpg", 100)),
                    "Falta el fotograma missing.jpg en el ZIP.");
        }
    }

    private static void assertError(Fixture fixture, JSONArray frames, String message) throws Exception {
        try {
            UgoiraConverter.mp4(fixture.archive, frames, fixture.output, (done, total) -> fail("Unexpected progress"));
            fail("Expected conversion failure");
        } catch (IOException expected) { assertEquals(message, expected.getMessage()); }
    }

    private static JSONObject frame(String name, int delay) throws Exception {
        return new JSONObject().put("file", name).put("delay", delay);
    }

    private static void assertColor(MediaMetadataRetriever retriever, long timeUs, int expected) {
        Bitmap bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
        assertNotNull("Android decoder must return a frame", bitmap);
        try {
            int actual = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            assertTrue("Decoded red", Math.abs(Color.red(expected) - Color.red(actual)) < 25);
            assertTrue("Decoded green", Math.abs(Color.green(expected) - Color.green(actual)) < 25);
            assertTrue("Decoded blue", Math.abs(Color.blue(expected) - Color.blue(actual)) < 25);
        } finally { bitmap.recycle(); }
    }

    private static void assertVideo(File output, int width, int height, int samples) throws Exception {
        byte[] header = new byte[8];
        try (java.io.InputStream input = Files.newInputStream(output.toPath())) {
            assertEquals(8, input.read(header));
        }
        assertEquals("ftyp", new String(header, 4, 4, StandardCharsets.US_ASCII));
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(output.getAbsolutePath());
            assertEquals(1, extractor.getTrackCount());
            MediaFormat format = extractor.getTrackFormat(0);
            assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME));
            assertEquals(width, format.getInteger(MediaFormat.KEY_WIDTH));
            assertEquals(height, format.getInteger(MediaFormat.KEY_HEIGHT));
            assertEquals(30, format.getInteger(MediaFormat.KEY_FRAME_RATE));
            assertTrue(Math.abs(format.getLong(MediaFormat.KEY_DURATION) - samples * 1000000L / 30) <= 1);
            extractor.selectTrack(0);
            int count = 0;
            while (extractor.getSampleTime() >= 0) {
                assertTrue("30 fps presentation timestamp at " + count,
                        Math.abs(extractor.getSampleTime() - count * 1000000L / 30) <= 1);
                count++;
                extractor.advance();
            }
            assertEquals(samples, count);
        } finally { extractor.release(); }
    }

    private static final class Fixture implements AutoCloseable {
        final File directory;
        final File archive;
        final File output;

        Fixture(int width, int height) throws Exception {
            File cache = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
            directory = Files.createTempDirectory(cache.toPath(), "mp4-contract-").toFile();
            archive = new File(directory, "synthetic.zip");
            output = new File(directory, "result.mp4");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                String[] names = {"red.jpg", "blue.jpg"};
                int[] colors = {Color.RED, Color.BLUE};
                for (int i = 0; i < names.length; i++) {
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(colors[i]);
                        zip.putNextEntry(new ZipEntry(names[i]));
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, zip));
                        zip.closeEntry();
                    } finally { bitmap.recycle(); }
                }
            }
        }

        @Override public void close() {
            output.delete();
            archive.delete();
            directory.delete();
        }
    }
}
