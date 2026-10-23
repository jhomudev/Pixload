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
