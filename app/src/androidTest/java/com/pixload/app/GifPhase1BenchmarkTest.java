package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pixload.shared.GifPreset;
import com.squareup.gifencoder.GifEncoder;
import com.squareup.gifencoder.ImageOptions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Synthetic JPEG corpus for matched 10-frame before/after and 100-frame target measurements. */
@RunWith(AndroidJUnit4.class)
public class GifPhase1BenchmarkTest {
    @Test public void benchmarkHundredJpegFrames() throws Exception { benchmark(100, false); }
    @Test public void benchmarkTenBefore() throws Exception { benchmark(10, true); }
    @Test public void benchmarkTenAfter() throws Exception { benchmark(10, false); }

    private void benchmark(int count, boolean baseline) throws Exception {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        File archive = File.createTempFile("pixload-benchmark-", ".zip", directory);
        File result = File.createTempFile("pixload-benchmark-", ".gif", directory);
        try {
            JSONArray frames = new JSONArray();
            int width = 480, height = 270;
            int[] colors = new int[width * height];
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                for (int i = 0; i < count; i++) {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                        colors[y * width + x] = Color.rgb((x * 255 / (width - 1) + i * 4) % 256,
                                (y * 255 / (height - 1) + i * 3) % 256, (x + y + i * 7) % 256);
                    Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
                    String name = String.format(java.util.Locale.ROOT, "%06d.jpg", i);
                    zip.putNextEntry(new ZipEntry(name));
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, zip));
                    zip.closeEntry();
                    bitmap.recycle();
                    frames.put(new JSONObject().put("file", name).put("delay", 100));
                }
            }
            colors = null;
            System.gc();
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger peakPssKb = new AtomicInteger();
            AtomicInteger peakHeapKb = new AtomicInteger();
            Thread sampler = new Thread(() -> {
                while (running.get()) {
                    Debug.MemoryInfo info = new Debug.MemoryInfo();
                    Debug.getMemoryInfo(info);
                    peakPssKb.accumulateAndGet(info.getTotalPss(), Math::max);
                    peakHeapKb.accumulateAndGet((int) (Runtime.getRuntime().totalMemory() / 1024), Math::max);
                    SystemClock.sleep(20);
                }
            }, "gif-memory-sampler");
            sampler.start();
            long start = System.nanoTime();
            AtomicInteger progressCount = new AtomicInteger();
            try (FileOutputStream output = new FileOutputStream(result)) {
                if (baseline) {
                    try (ZipFile zip = new ZipFile(archive)) {
                        GifEncoder encoder = new GifEncoder(output, width, height, 0);
                        int[] pixels = new int[width * height];
                        for (int i = 0; i < count; i++) {
                            String name = frames.getJSONObject(i).getString("file");
                            try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
                                Bitmap bitmap = BitmapFactory.decodeStream(input);
                                if (bitmap == null) throw new IllegalStateException("JPEG no decodificable");
                                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                                bitmap.recycle();
                            }
                            encoder.addImage(pixels, width,
                                    new ImageOptions().setDelay(100, TimeUnit.MILLISECONDS));
                            progressCount.incrementAndGet();
                        }
                        encoder.finishEncoding();
                    }
                } else {
                    UgoiraConverter.gif(archive, frames, output,
                            (done, total) -> progressCount.set(done), GifPreset.BALANCED);
                }
            } finally { running.set(false); sampler.join(); }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Log.i("PixloadProfile", (baseline ? "before" : "after") + " " + count
                    + " JPEG 480x270: elapsedMs=" + elapsedMs + " gifBytes=" + result.length()
                    + " peakPssKb=" + peakPssKb.get() + " peakHeapKb=" + peakHeapKb.get()
                    + " progress=" + progressCount.get());
            assertEquals(count, progressCount.get());
            assertTrue(result.length() > 1000);
        } finally { archive.delete(); result.delete(); }
    }
}
