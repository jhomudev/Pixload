package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.jcodec.api.android.AndroidSequenceEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UgoiraConverter {
    interface Progress { void update(int done, int total) throws IOException; }

    private static final int FPS = 30;

    static void gif(File archive, JSONArray frames, OutputStream output, Progress progress) throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            if (frames.length() == 0) throw new IOException("La animación no contiene fotogramas.");
            Bitmap first = decode(zip, frames.getJSONObject(0).getString("file"), 960);
            int width = first.getWidth();
            int height = first.getHeight();
            FastGifEncoder encoder = new FastGifEncoder(output, width, height);
            for (int i = 0; i < frames.length(); i++) {
                JSONObject frame = frames.getJSONObject(i);
                Bitmap bitmap = i == 0 ? first : decode(zip, frame.getString("file"), 960);
                Bitmap resized = bitmap.getWidth() == width && bitmap.getHeight() == height ? bitmap
                        : Bitmap.createScaledBitmap(bitmap, width, height, true);
                try {
                    encoder.addFrame(resized, Math.max(20, frame.optInt("delay", 100)));
                } finally {
                    if (resized != bitmap) resized.recycle();
                    bitmap.recycle();
                }
                progress.update(i + 1, frames.length());
            }
            encoder.finish();
        }
    }

    static void mp4(File archive, JSONArray frames, File output, Progress progress) throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            if (frames.length() == 0) throw new IOException("La animación no contiene fotogramas.");
            Bitmap first = decode(zip, frames.getJSONObject(0).getString("file"), 1280);
            // H.264 requires even dimensions; animations with odd dimensions are scaled by at most one pixel.
            int width = first.getWidth() - first.getWidth() % 2;
            int height = first.getHeight() - first.getHeight() % 2;
            if (width < 2 || height < 2) throw new IOException("Fotogramas demasiado pequeños para MP4.");
            AndroidSequenceEncoder encoder = AndroidSequenceEncoder.createSequenceEncoder(output, FPS);
            long duration = 0;
            long encoded = 0;
            try {
                for (int i = 0; i < frames.length(); i++) {
                    JSONObject frame = frames.getJSONObject(i);
                    Bitmap bitmap = i == 0 ? first : decode(zip, frame.getString("file"), 1280);
                    Bitmap resized = bitmap.getWidth() == width && bitmap.getHeight() == height ? bitmap
                            : Bitmap.createScaledBitmap(bitmap, width, height, true);
                    try {
                        duration += Math.max(20, frame.optInt("delay", 100));
                        long needed = Math.max(1, Math.round(duration * FPS / 1000.0));
                        if (needed > 18000) throw new IOException("Animación demasiado larga para convertir a MP4 (máximo 10 minutos).");
                        while (encoded < needed) {
                            encoder.encodeImage(resized);
                            encoded++;
                        }
                    } finally {
                        if (resized != bitmap) resized.recycle();
                        bitmap.recycle();
                    }
                    progress.update(i + 1, frames.length());
                }
            } finally {
                encoder.finish();
            }
        }
    }

    private static Bitmap decode(ZipFile zip, String name, int maxSize) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) throw new IOException("Falta el fotograma " + name + " en el ZIP.");
        if (entry.getSize() > 40L * 1024 * 1024) throw new IOException("Fotograma demasiado grande.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = zip.getInputStream(entry)) { BitmapFactory.decodeStream(stream, null, options); }
        if (options.outWidth < 1 || options.outHeight < 1 ||
                (long) options.outWidth * options.outHeight > 80_000_000L) {
            throw new IOException("Dimensiones de fotograma no válidas.");
        }
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > maxSize ||
                options.outHeight / options.inSampleSize > maxSize) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        try (InputStream stream = zip.getInputStream(entry)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap == null) throw new IOException("No se pudo leer el fotograma " + name);
            return bitmap;
        }
    }
}
