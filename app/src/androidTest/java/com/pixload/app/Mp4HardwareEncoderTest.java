package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaCodec;
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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/** Real platform codec/muxer and deterministic fault injection; no user files or SAF. */
@RunWith(AndroidJUnit4.class)
public class Mp4HardwareEncoderTest {
    @Test public void hardwarePreservesTimingColorsAndCancellation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            boolean[] finished = {false};
            JSONArray frames = new JSONArray();
            for (int delay : new int[]{20, 20, 60, 33, 0}) frames.put(frame("red.jpg", delay));
            frames.put(new JSONObject().put("file", "blue.jpg"));
            List<Integer> progress = new ArrayList<>();
            convert(fixture.archive, frames, fixture.output, (done, total) -> {
                assertEquals(6, total);
                progress.add(done);
            }, (output, width, height) -> new Forwarding(
                    Mp4HardwareEncoder.Companion.create(output, width, height)) {
                @Override public void finish() { super.finish(); finished[0] = true; }
            });
            assertTrue("Must complete through hardware, not fallback", finished[0]);
            assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), progress);
            assertVideo(fixture.output, 8);
            assertColor(fixture.output, 0, Color.RED);
            assertColor(fixture.output, 240000, Color.BLUE);

            IOException cancelled = new IOException("cancel hardware");
            progress.clear();
            try {
                convert(fixture.archive, fixture.frames, fixture.output, (done, total) -> {
                    progress.add(done);
                    throw cancelled;
                }, Mp4HardwareEncoder.Companion::create);
                fail("Cancellation must escape");
            } catch (IOException expected) { assertSame(cancelled, expected); }
            assertEquals(Arrays.asList(1), progress);
            assertVideo(fixture.output, 3);
        }
    }

    @Test public void hardwareFinalizesASingleSample() throws Exception {
        try (Fixture fixture = new Fixture()) {
            boolean[] finished = {false};
            convert(fixture.archive, new JSONArray().put(frame("red.jpg", 20)), fixture.output,
                    (done, total) -> { assertEquals(1, done); assertEquals(1, total); },
                    (output, width, height) -> new Forwarding(
                            Mp4HardwareEncoder.Companion.create(output, width, height)) {
                        @Override public void finish() { super.finish(); finished[0] = true; }
                    });
            assertTrue("Single sample must finish through hardware", finished[0]);
            assertVideo(fixture.output, 1);
            assertColor(fixture.output, 0, Color.RED);
        }
    }

    @Test public void unavailableEncoderFallsBackToPlayableJcodec() throws Exception {
        try (Fixture fixture = new Fixture()) {
            convert(fixture.archive, fixture.frames, fixture.output, (done, total) -> {},
                    (output, width, height) -> { throw unavailable(); });
            assertVideo(fixture.output, 6);
            assertColor(fixture.output, 0, Color.RED);
            assertColor(fixture.output, 150000, Color.BLUE);
        }
    }

    @Test public void platformConfigurationFailureFallsBack() throws Exception {
        try (Fixture fixture = new Fixture()) {
            convert(fixture.archive, fixture.frames, fixture.output, (done, total) -> {},
                    (output, width, height) -> {
                        MediaCodec codec = null;
                        try {
                            codec = MediaCodec.createEncoderByType("video/avc");
                            MediaFormat invalid = MediaFormat.createVideoFormat("video/avc", width, height);
                            invalid.setInteger(MediaFormat.KEY_COLOR_FORMAT, -123);
                            invalid.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
                            invalid.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                            invalid.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                            codec.configure(invalid, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            codec.start();
                            throw new AssertionError("Platform accepted invalid color format");
                        } catch (Exception expected) {
                            throw new Mp4Conversion.HardwareFailure(expected);
                        } finally { if (codec != null) codec.release(); }
                    });
            assertVideo(fixture.output, 6);
            assertColor(fixture.output, 150000, Color.BLUE);
        }
    }

    @Test public void midstreamFailureRestartsWithoutDuplicateProgress() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<Integer> progress = new ArrayList<>();
            convert(fixture.archive, fixture.frames, fixture.output, (done, total) -> {
                assertEquals(2, total);
                progress.add(done);
            }, (output, width, height) -> new Forwarding(
                    Mp4HardwareEncoder.Companion.create(output, width, height)) {
                int frames;
                @Override public void encode(Bitmap bitmap, int count) {
                    if (++frames == 2) throw unavailable();
                    super.encode(bitmap, count);
                }
            });
            assertEquals(Arrays.asList(1, 2), progress);
            assertVideo(fixture.output, 6);
            assertColor(fixture.output, 150000, Color.BLUE);
        }
    }

    @Test public void cancellationSurvivesHardwareFinalizationFailure() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IOException cancelled = new IOException("cancel before failing finalization");
            List<Integer> progress = new ArrayList<>();
            try {
                convert(fixture.archive, fixture.frames, fixture.output, (done, total) -> {
                    progress.add(done);
                    throw cancelled;
                }, (output, width, height) -> new Forwarding(
                        Mp4HardwareEncoder.Companion.create(output, width, height)) {
                    @Override public void finish() { super.finish(); throw unavailable(); }
                });
                fail("Cancellation must escape");
            } catch (IOException expected) { assertSame(cancelled, expected); }
            assertEquals(Arrays.asList(1), progress);
            assertVideo(fixture.output, 3);
            assertColor(fixture.output, 0, Color.RED);
        }
    }

    static void software(File archive, JSONArray frames, File output, UgoiraConverter.Progress progress) throws Exception {
        convert(archive, frames, output, progress, (file, width, height) -> { throw unavailable(); });
    }

    private static void convert(File archive, JSONArray frames, File output, UgoiraConverter.Progress progress,
                                Mp4Conversion.HardwareFactory factory) throws Exception {
        Method decode = UgoiraConverter.class.getDeclaredMethod("decode", java.util.zip.ZipFile.class, String.class, int.class);
        decode.setAccessible(true);
        Mp4Conversion.INSTANCE.convert(archive, frames, output, progress, (zip, name, maxSize) -> {
            try { return (Bitmap) decode.invoke(UgoiraConverter.INSTANCE, zip, name, maxSize); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }, factory);
    }

    private static Mp4Conversion.HardwareFailure unavailable() {
        return new Mp4Conversion.HardwareFailure(new IOException("Injected unavailable encoder"));
    }

    private static JSONObject frame(String name, int delay) throws Exception {
        return new JSONObject().put("file", name).put("delay", delay);
    }

    private static void assertVideo(File file, int samples) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            assertEquals(1, extractor.getTrackCount());
            MediaFormat format = extractor.getTrackFormat(0);
            assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME));
            assertEquals(320, format.getInteger(MediaFormat.KEY_WIDTH));
            assertEquals(240, format.getInteger(MediaFormat.KEY_HEIGHT));
            assertEquals(30, format.getInteger(MediaFormat.KEY_FRAME_RATE));
            assertTrue("Duration " + format.getLong(MediaFormat.KEY_DURATION) + " for " + samples + " samples",
                    Math.abs(format.getLong(MediaFormat.KEY_DURATION) - samples * 1000000L / 30) <= 1);
            extractor.selectTrack(0);
            int count = 0;
            while (extractor.getSampleTime() >= 0) {
                assertTrue(Math.abs(extractor.getSampleTime() - count * 1000000L / 30) <= 1);
                count++;
                extractor.advance();
            }
            assertEquals(samples, count);
        } finally { extractor.release(); }
    }

    private static void assertColor(File file, long time, int expected) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            Bitmap bitmap = retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST);
            assertNotNull(bitmap);
            try {
                int actual = bitmap.getPixel(160, 120);
                assertTrue(Math.abs(Color.red(expected) - Color.red(actual)) < 25);
                assertTrue(Math.abs(Color.green(expected) - Color.green(actual)) < 25);
                assertTrue(Math.abs(Color.blue(expected) - Color.blue(actual)) < 25);
            } finally { bitmap.recycle(); }
        } finally { retriever.release(); }
    }

    private static class Forwarding implements Mp4Conversion.Encoder {
        final Mp4Conversion.Encoder delegate;
        Forwarding(Mp4Conversion.Encoder delegate) { this.delegate = delegate; }
        @Override public void encode(Bitmap bitmap, int count) { delegate.encode(bitmap, count); }
        @Override public void finish() { delegate.finish(); }
        @Override public void close() { try { delegate.close(); } catch (Exception error) { throw new RuntimeException(error); } }
    }

    private static final class Fixture implements AutoCloseable {
        final File directory = Files.createTempDirectory(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(), "mp4-hardware-").toFile();
        final File archive = new File(directory, "synthetic.zip");
        final File output = new File(directory, "result.mp4");
        final JSONArray frames = new JSONArray().put(frame("red.jpg", 100)).put(frame("blue.jpg", 100));
        Fixture() throws Exception {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                String[] names = {"red.jpg", "blue.jpg"};
                int[] colors = {Color.RED, Color.BLUE};
                for (int i = 0; i < names.length; i++) {
                    Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
                    try {
                        bitmap.eraseColor(colors[i]);
                        zip.putNextEntry(new ZipEntry(names[i]));
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, zip));
                        zip.closeEntry();
                    } finally { bitmap.recycle(); }
                }
            }
        }
        @Override public void close() { output.delete(); archive.delete(); directory.delete(); }
    }
}
