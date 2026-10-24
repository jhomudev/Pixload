package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Debug;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pixload.bench.square.GifEncoder;
import com.pixload.bench.square.ImageOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertTrue;

/** Reproducible synthetic JPEG benchmark of Square 0.10.1 (diagnostic fork in androidTest only). */
@RunWith(AndroidJUnit4.class)
public class GifBaselineProfileTest {
    private static final String TAG = "PixloadProfile";

    @Test public void profileJpegBaseline() throws Exception {
        File archive = File.createTempFile("pixload-profile-", ".zip",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            int width = 960, height = 540;
            Bitmap source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] colors = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                colors[y * width + x] = Color.rgb(x * 255 / (width - 1), y * 255 / (height - 1),
                        (x + y) * 255 / (width + height - 2));
            source.setPixels(colors, 0, width, 0, 0, width, height);
            colors = null;
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("000000.jpg"));
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, zip));
                zip.closeEntry();
            }
            source.recycle();
            System.gc();
            Debug.startAllocCounting();
            long allocatedStart = Integer.toUnsignedLong(Debug.getThreadAllocSize());
            int initialPss = pss();
            long start = System.nanoTime();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipFile zip = new ZipFile(archive)) {
                Bitmap bitmap;
                try (InputStream input = zip.getInputStream(zip.getEntry("000000.jpg"))) {
                    bitmap = BitmapFactory.decodeStream(input);
                }
                long decodeNanos = System.nanoTime() - start;
                start = System.nanoTime();
                Bitmap resized = bitmap; // 960x540 baseline: no scaling required.
                long scaleNanos = System.nanoTime() - start;
                int[] pixels = new int[width * height];
                start = System.nanoTime();
                resized.getPixels(pixels, 0, width, 0, 0, width, height);
                long pixelsNanos = System.nanoTime() - start;
                GifEncoder encoder = new GifEncoder(output, width, height, 0);
                start = System.nanoTime();
                encoder.addImage(pixels, width, new ImageOptions().setDelay(100, TimeUnit.MILLISECONDS));
                encoder.finishEncoding();
                long totalNanos = System.nanoTime() - start;
                Log.i(TAG, "baseline 960x540 JPEG: decode=" + ms(decodeNanos)
                        + " scale=" + ms(scaleNanos) + " getPixels=" + ms(pixelsNanos)
                        + " toColorArray=" + ms(encoder.conversionNanos)
                        + " histogram=" + ms(encoder.histogramNanos)
                        + " quant=" + ms(encoder.quantizationNanos)
                        + " dither=" + ms(encoder.ditheringNanos)
                        + " index=" + ms(encoder.indexingNanos)
                        + " lzw=" + ms(encoder.lzwNanos)
                        + " write=" + ms(encoder.writeNanos)
                        + " encodeTotal=" + ms(totalNanos)
                        + " allocBytes=" + (Integer.toUnsignedLong(Debug.getThreadAllocSize()) - allocatedStart)
                        + " pssDeltaKb=" + (pss() - initialPss)
                        + " gifBytes=" + output.size());
                bitmap.recycle();
            } finally { Debug.stopAllocCounting(); }
            assertTrue(output.size() > 100);
        } finally { archive.delete(); }
    }

    private static long ms(long nanos) { return nanos / 1_000_000; }

    private static int pss() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss();
    }
}
