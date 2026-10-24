package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.os.Debug;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.pixload.bench.square.ColorQuantizer;
import com.pixload.bench.square.Ditherer;
import com.pixload.bench.square.FloydSteinbergDitherer;
import com.pixload.bench.square.FixedQuantizer;
import com.pixload.bench.square.LookupDitherer;
import com.pixload.bench.square.GifEncoder;
import com.pixload.bench.square.ImageOptions;
import com.pixload.bench.square.MedianCutQuantizer;
import com.pixload.bench.square.NearestColorDitherer;
import com.pixload.bench.square.UniformQuantizer;

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
        profile("baseline", 960, MedianCutQuantizer.INSTANCE, FloydSteinbergDitherer.INSTANCE);
    }

    @Test public void profileJpegNoDither() throws Exception {
        profile("nearest960", 960, MedianCutQuantizer.INSTANCE, NearestColorDitherer.INSTANCE);
    }

    @Test public void profileJpegUniformDither() throws Exception {
        profile("uniform960", 960, UniformQuantizer.INSTANCE, FloydSteinbergDitherer.INSTANCE);
    }

    @Test public void profileJpegSampled() throws Exception {
        profile("sampled480", 480, MedianCutQuantizer.INSTANCE, FloydSteinbergDitherer.INSTANCE);
    }

    @Test public void profileJpegSampledNoDither() throws Exception {
        profile("nearest480", 480, MedianCutQuantizer.INSTANCE, NearestColorDitherer.INSTANCE);
    }

    @Test public void profileJpegFixedLookup() throws Exception {
        profile("fixed480", 480, null, new LookupDitherer(false));
    }

    @Test public void profileJpegFixedDiffusion() throws Exception {
        profile("fixedDiff480", 480, null, new LookupDitherer(true));
    }

    @SuppressWarnings("deprecation")
    private void profile(String variant, int maxWidth, ColorQuantizer quantizer, Ditherer ditherer) throws Exception {
        File archive = File.createTempFile("pixload-profile-", ".zip",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            int sourceWidth = 960, sourceHeight = 540;
            Bitmap source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
            int[] colors = new int[sourceWidth * sourceHeight];
            for (int y = 0; y < sourceHeight; y++) for (int x = 0; x < sourceWidth; x++)
                colors[y * sourceWidth + x] = Color.rgb(x * 255 / (sourceWidth - 1),
                        y * 255 / (sourceHeight - 1), (x + y) * 255 / (sourceWidth + sourceHeight - 2));
            source.setPixels(colors, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight);
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("000000.jpg"));
                assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, zip));
                zip.closeEntry();
            }
            source.recycle();
            colors = null;
            System.gc();
            Debug.startAllocCounting();
            long allocatedStart = Integer.toUnsignedLong(Debug.getThreadAllocSize());
            long runtimeAllocatedStart = allocatedBytes();
            int initialPss = pss();
            long start = System.nanoTime();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipFile zip = new ZipFile(archive)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sourceWidth / maxWidth;
                Bitmap bitmap;
                try (InputStream input = zip.getInputStream(zip.getEntry("000000.jpg"))) {
                    bitmap = BitmapFactory.decodeStream(input, null, opts);
                }
                long decodeNanos = System.nanoTime() - start;
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                start = System.nanoTime();
                Bitmap resized = bitmap; // No scaling for these two precisely divisible sizes.
                long scaleNanos = System.nanoTime() - start;
                int[] pixels = new int[width * height];
                start = System.nanoTime();
                resized.getPixels(pixels, 0, width, 0, 0, width, height);
                long pixelsNanos = System.nanoTime() - start;
                long paletteStart = System.nanoTime();
                ColorQuantizer chosen = quantizer == null ? new FixedQuantizer(pixels) : quantizer;
                long paletteNanos = System.nanoTime() - paletteStart;
                GifEncoder encoder = new GifEncoder(output, width, height, 0);
                start = System.nanoTime();
                encoder.addImage(pixels, width, new ImageOptions().setDelay(100, TimeUnit.MILLISECONDS)
                        .setColorQuantizer(chosen).setDitherer(ditherer));
                encoder.finishEncoding();
                long totalNanos = System.nanoTime() - start;
                int allocated = Debug.getThreadAllocSize();
                long runtimeAllocated = allocatedBytes() - runtimeAllocatedStart;
                int pssDelta = pss() - initialPss;
                byte[] data = output.toByteArray();
                Movie movie = Movie.decodeByteArray(data, 0, data.length);
                assertTrue(movie != null);
                Bitmap decoded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                movie.setTime(0);
                movie.draw(new Canvas(decoded), 0, 0);
                long error = 0;
                int samples = 0;
                for (int y = 5; y < height; y += 8) for (int x = 5; x < width; x += 8) {
                    int a = pixels[y * width + x], b = decoded.getPixel(x, y);
                    error += Math.abs(Color.red(a) - Color.red(b));
                    error += Math.abs(Color.green(a) - Color.green(b));
                    error += Math.abs(Color.blue(a) - Color.blue(b));
                    samples += 3;
                }
                decoded.recycle();
                bitmap.recycle();
                Log.i(TAG, variant + " " + width + "x" + height + " JPEG: decode=" + ms(decodeNanos)
                        + " scale=" + ms(scaleNanos) + " getPixels=" + ms(pixelsNanos)
                        + " toColorArray=" + ms(encoder.conversionNanos)
                        + " histogram=" + ms(encoder.histogramNanos)
                        + " paletteSetup=" + ms(paletteNanos) + " quant=" + ms(encoder.quantizationNanos)
                        + " dither=" + ms(encoder.ditheringNanos)
                        + " index=" + ms(encoder.indexingNanos)
                        + " lzw=" + ms(encoder.lzwNanos)
                        + " write=" + ms(encoder.writeNanos)
                        + " encodeTotal=" + ms(totalNanos)
                        + " threadAllocModuloBytes=" + (Integer.toUnsignedLong(allocated) - allocatedStart)
                        + " runtimeAllocBytes=" + runtimeAllocated
                        + " pssDeltaKb=" + pssDelta
                        + " gifBytes=" + output.size() + " mae=" + ((double) error / samples));
            } finally { Debug.stopAllocCounting(); }
            assertTrue(output.size() > 100);
        } finally { archive.delete(); }
    }

    private static long ms(long nanos) { return nanos / 1_000_000; }

    private static long allocatedBytes() {
        String value = Debug.getRuntimeStat("art.gc.bytes-allocated");
        return value == null ? -1 : Long.parseLong(value);
    }

    private static int pss() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        return info.getTotalPss();
    }
}
