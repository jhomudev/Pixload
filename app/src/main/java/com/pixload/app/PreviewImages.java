package com.pixload.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PreviewImages {
    private static final int MAX_BYTES = 6 * 1024 * 1024;
    private final Activity activity;
    private final ExecutorService requests = Executors.newFixedThreadPool(2);
    private final List<Thumbnail> visibleRequests = new ArrayList<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String url, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    PreviewImages(Activity activity) { this.activity = activity; }

    View view(String url, int heightDp) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(Ui.background(activity, Ui.RAISED, 12));
        frame.setClipToOutline(true);
        ImageView image = new ImageView(activity);
        image.setContentDescription(AppLanguage.label(activity, "Miniatura de la obra de Pixiv"));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
        FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36), Gravity.CENTER);
        frame.addView(spinner, centered);
        if (url == null || url.isEmpty()) {
            spinner.setVisibility(View.GONE);
            showError(frame);
        } else {
            Bitmap cached = cache.get(url);
            if (cached != null) {
                image.setImageBitmap(cached);
                spinner.setVisibility(View.GONE);
            } else {
                visibleRequests.add(new Thumbnail(frame, image, spinner, url, Ui.dp(activity, heightDp)));
            }
        }
        return frame;
    }

    void clear() { visibleRequests.clear(); }

    void loadVisible() {
        Rect visible = new Rect();
        for (Thumbnail thumbnail : visibleRequests) {
            if (thumbnail.started || !thumbnail.frame.isAttachedToWindow() || !thumbnail.frame.getLocalVisibleRect(visible)) continue;
            thumbnail.started = true;
            requests.execute(() -> fetch(thumbnail));
        }
    }

    private void fetch(Thumbnail item) {
        try {
            Bitmap bitmap = cache.get(item.url);
            if (bitmap == null) {
                HttpURLConnection connection = PixivClient.open(item.url);
                try {
                    if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                    try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = stream.read(buffer)) != -1) {
                            if (data.size() + read > MAX_BYTES) throw new Exception("Miniatura demasiado grande");
                            data.write(buffer, 0, read);
                        }
                        byte[] bytes = data.toByteArray();
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                        options.inSampleSize = 1;
                        int target = Math.max(item.targetPixels, Ui.dp(activity, 180));
                        while (bounds.outWidth / (options.inSampleSize * 2) >= target &&
                                bounds.outHeight / (options.inSampleSize * 2) >= target) options.inSampleSize *= 2;
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    }
                } finally { connection.disconnect(); }
                if (bitmap == null) throw new Exception("No se pudo decodificar la miniatura");
                cache.put(item.url, bitmap);
            }
            Bitmap result = bitmap;
            activity.runOnUiThread(() -> {
                if (!item.frame.isAttachedToWindow()) return;
                item.image.setImageBitmap(result);
                item.spinner.setVisibility(View.GONE);
            });
        } catch (Exception ignored) {
            activity.runOnUiThread(() -> {
                if (!item.frame.isAttachedToWindow()) return;
                item.spinner.setVisibility(View.GONE);
                showError(item.frame);
            });
        }
    }

    private void showError(FrameLayout frame) {
        TextView error = Ui.text(activity, "Vista previa no disponible", 12, Ui.MUTED, false);
        error.setGravity(Gravity.CENTER);
        error.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        frame.addView(error, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
    }

    void shutdown() { requests.shutdownNow(); }

    private static final class Thumbnail {
        final FrameLayout frame;
        final ImageView image;
        final ProgressBar spinner;
        final String url;
        final int targetPixels;
        boolean started;

        Thumbnail(FrameLayout frame, ImageView image, ProgressBar spinner, String url, int targetPixels) {
            this.frame = frame;
            this.image = image;
            this.spinner = spinner;
            this.url = url;
            this.targetPixels = targetPixels;
        }
    }
}
