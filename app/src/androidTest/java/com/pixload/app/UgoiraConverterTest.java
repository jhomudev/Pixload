package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.media.MediaMetadataRetriever;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class UgoiraConverterTest {
    private final File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();

    private File archive() throws Exception {
        File archive = File.createTempFile("pixload-test-", ".zip", directory);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            for (int i = 0; i < 2; i++) {
                Bitmap frame = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                frame.eraseColor(i == 0 ? Color.RED : Color.BLUE);
                zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT, "%06d.png", i)));
                frame.compress(Bitmap.CompressFormat.PNG, 100, zip);
                zip.closeEntry();
                frame.recycle();
            }
        }
        return archive;
    }

    private JSONArray frames() throws Exception {
        return new JSONArray().put(new JSONObject().put("file", "000000.png").put("delay", 100))
                .put(new JSONObject().put("file", "000001.png").put("delay", 100));
    }

    @Test public void convertsToAnimatedGif() throws Exception {
        File archive = archive();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UgoiraConverter.gif(archive, frames(), output, (done, total) -> {});
            byte[] data = output.toByteArray();
            assertTrue(data.length > 100);
            assertEquals("GIF89a", new String(data, 0, 6, StandardCharsets.US_ASCII));
            Movie decoded = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(decoded != null);
            assertEquals(200, decoded.duration());
        } finally { archive.delete(); }
    }

    @Test public void decodesAnimatedGifWithChangingColors() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FastGifEncoder encoder = new FastGifEncoder(output, 128, 128);
        for (int frame = 0; frame < 2; frame++) {
            int[] colors = new int[128 * 128];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                colors[y * 128 + x] = Color.rgb(x * 2, y * 2, frame == 0 ? (x + y) : 255 - (x + y));
            }
            Bitmap bitmap = Bitmap.createBitmap(colors, 128, 128, Bitmap.Config.ARGB_8888);
            encoder.addFrame(bitmap, 100);
            bitmap.recycle();
        }
        encoder.finish();
        byte[] data = output.toByteArray();
        Movie movie = Movie.decodeByteArray(data, 0, data.length);
        assertTrue(movie != null);
        assertEquals(200, movie.duration());
        Bitmap first = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        movie.setTime(0);
        movie.draw(new Canvas(first), 0, 0);
        movie.setTime(150);
        movie.draw(new Canvas(second), 0, 0);
        assertTrue(first.getPixel(25, 25) != second.getPixel(25, 25));
        first.recycle();
        second.recycle();
    }

    @Test public void gifPreservesColorsInsideCoarsePaletteBuckets() throws Exception {
        int[] colors = {Color.rgb(12, 17, 23), Color.rgb(147, 101, 211)};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FastGifEncoder encoder = new FastGifEncoder(output, 128, 64);
        for (int color : colors) {
            Bitmap bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(color);
            encoder.addFrame(bitmap, 100);
            bitmap.recycle();
        }
        encoder.finish();
        byte[] data = output.toByteArray();
        Movie movie = Movie.decodeByteArray(data, 0, data.length);
        assertTrue(movie != null);
        assertEquals(200, movie.duration());
        Bitmap result = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < colors.length; i++) {
            movie.setTime(i * 100 + 50);
            movie.draw(new Canvas(result), 0, 0);
            int expected = colors[i];
            int actual = result.getPixel(64, 32);
            assertTrue(Math.abs(Color.red(expected) - Color.red(actual)) <= 2);
            assertTrue(Math.abs(Color.green(expected) - Color.green(actual)) <= 2);
            assertTrue(Math.abs(Color.blue(expected) - Color.blue(actual)) <= 2);
        }
        result.recycle();
    }

    @Test public void convertsGradientWithMoreThan256Colors() throws Exception {
        File archive = File.createTempFile("pixload-gradient-", ".zip", directory);
        try {
            int[] colors = new int[128 * 128];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++)
                colors[y * 128 + x] = Color.rgb(x * 2, y * 2, (x + y) * 255 / 254);
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                Bitmap frame = Bitmap.createBitmap(colors, 128, 128, Bitmap.Config.ARGB_8888);
                zip.putNextEntry(new ZipEntry("gradient.png"));
                frame.compress(Bitmap.CompressFormat.PNG, 100, zip);
                zip.closeEntry();
                frame.recycle();
            }
            JSONArray frames = new JSONArray().put(new JSONObject().put("file", "gradient.png").put("delay", 100));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UgoiraConverter.gif(archive, frames, output, (done, total) -> {});
            byte[] data = output.toByteArray();
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(movie != null);
            Bitmap result = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            movie.setTime(0);
            movie.draw(new Canvas(result), 0, 0);
            long error = 0;
            for (int y = 8; y < 128; y += 8) for (int x = 8; x < 128; x += 8) {
                int expected = colors[y * 128 + x], actual = result.getPixel(x, y);
                error += Math.abs(Color.red(expected) - Color.red(actual));
                error += Math.abs(Color.green(expected) - Color.green(actual));
                error += Math.abs(Color.blue(expected) - Color.blue(actual));
            }
            assertTrue("Error de color medio: " + error / (15 * 15 * 3), error < 15 * 15 * 3 * 24);
            result.recycle();
        } finally { archive.delete(); }
    }

    @Test public void convertsFullSizeColorfulGif() throws Exception {
        File archive = File.createTempFile("pixload-fullsize-", ".zip", directory);
        try {
            int width = 960, height = 540;
            int[] colors = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                colors[y * width + x] = Color.rgb(x * 255 / (width - 1), y * 255 / (height - 1),
                        ((x + y) % 1024) * 255 / 1023);
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                Bitmap frame = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
                zip.putNextEntry(new ZipEntry("fullsize.png"));
                frame.compress(Bitmap.CompressFormat.PNG, 100, zip);
                zip.closeEntry();
                frame.recycle();
            }
            JSONArray frames = new JSONArray().put(new JSONObject().put("file", "fullsize.png").put("delay", 100));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            long start = android.os.SystemClock.elapsedRealtime();
            UgoiraConverter.gif(archive, frames, output, (done, total) -> {});
            android.util.Log.i("PixloadGifTest", "960x540 frame: " +
                    (android.os.SystemClock.elapsedRealtime() - start) + " ms, " + output.size() + " bytes");
            byte[] data = output.toByteArray();
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(movie != null);
            assertEquals(480, movie.width());
            assertEquals(270, movie.height());
        } finally { archive.delete(); }
    }

    @Test public void gifPreservesResolutionNearSizeLimit() throws Exception {
        File archive = File.createTempFile("pixload-large-", ".zip", directory);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                Bitmap frame = Bitmap.createBitmap(1100, 110, Bitmap.Config.ARGB_8888);
                frame.eraseColor(Color.rgb(31, 97, 193));
                zip.putNextEntry(new ZipEntry("large.png"));
                frame.compress(Bitmap.CompressFormat.PNG, 100, zip);
                zip.closeEntry();
                frame.recycle();
            }
            JSONArray frames = new JSONArray().put(new JSONObject().put("file", "large.png").put("delay", 100));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UgoiraConverter.gif(archive, frames, output, (done, total) -> {});
            byte[] data = output.toByteArray();
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(movie != null);
            assertEquals(480, movie.width());
            assertEquals(48, movie.height());
            for (GifPreset preset : new GifPreset[]{GifPreset.FAST, GifPreset.QUALITY}) {
                ByteArrayOutputStream variant = new ByteArrayOutputStream();
                UgoiraConverter.gif(archive, frames, variant, (done, total) -> {}, preset);
                byte[] bytes = variant.toByteArray();
                Movie decoded = Movie.decodeByteArray(bytes, 0, bytes.length);
                assertTrue(decoded != null);
                assertEquals(preset.getMaxWidth(), decoded.width());
                assertEquals(preset.getMaxWidth() / 10, decoded.height());
            }
        } finally { archive.delete(); }
    }

    @Test public void gifAccumulatesVariableDelayRounding() throws Exception {
        File archive = archive();
        try {
            JSONArray timing = new JSONArray().put(new JSONObject().put("file", "000000.png").put("delay", 33))
                    .put(new JSONObject().put("file", "000001.png").put("delay", 33))
                    .put(new JSONObject().put("file", "000000.png").put("delay", 33));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UgoiraConverter.gif(archive, timing, output, (done, total) -> {}, GifPreset.BALANCED);
            byte[] data = output.toByteArray();
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(movie != null);
            assertEquals(100, movie.duration()); // Round cumulative 99ms once, not 3 x 30ms.
            assertTrue(new String(data, java.nio.charset.StandardCharsets.ISO_8859_1).contains("NETSCAPE2.0"));
        } finally { archive.delete(); }
    }

    @Test public void gifCapsFrameRateWithoutChangingTotalDuration() throws Exception {
        File archive = archive();
        try {
            JSONArray timing = new JSONArray();
            for (int i = 0; i < 10; i++) timing.put(new JSONObject()
                    .put("file", i % 2 == 0 ? "000000.png" : "000001.png").put("delay", 10));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            UgoiraConverter.gif(archive, timing, output, (done, total) -> {}, GifPreset.BALANCED);
            byte[] data = output.toByteArray();
            Movie movie = Movie.decodeByteArray(data, 0, data.length);
            assertTrue(movie != null);
            assertEquals(100, movie.duration());
        } finally { archive.delete(); }
    }

    @Test public void gifCancellationIsCooperativeBeforeSampling() throws Exception {
        File archive = archive();
        try {
            try {
                UgoiraConverter.gif(archive, frames(), new ByteArrayOutputStream(), (done, total) -> {
                    throw new java.io.IOException("Cancelado");
                });
                org.junit.Assert.fail("No canceló la conversión");
            } catch (java.io.IOException expected) {
                assertEquals("Cancelado", expected.getMessage());
            }
        } finally { archive.delete(); }
    }

    @Test public void convertsToPlayableMp4() throws Exception {
        File archive = archive();
        File video = File.createTempFile("pixload-test-", ".mp4", directory);
        try {
            UgoiraConverter.mp4(archive, frames(), video, (done, total) -> {});
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(video.getAbsolutePath());
                assertTrue(Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) >= 150);
            } finally { retriever.release(); }
        } finally { video.delete(); archive.delete(); }
    }
}
