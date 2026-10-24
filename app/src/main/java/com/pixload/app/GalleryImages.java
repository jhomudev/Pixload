package com.pixload.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GalleryImages {
    private final Activity activity;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final List<Request> pending = new ArrayList<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    GalleryImages(Activity activity) { this.activity = activity; }

    void clear() { pending.clear(); }

    View tile(String uri, String mime, int sizeDp) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(Ui.background(activity, Ui.RAISED, 12));
        frame.setClipToOutline(true);
        if (uri == null || uri.isEmpty() || (mime != null && mime.startsWith("text/"))) {
            boolean text = mime != null && mime.startsWith("text/");
            ImageView icon = Ui.icon(activity, text ? R.drawable.tabler_file_text : R.drawable.tabler_photo_off, Ui.CYAN);
            frame.addView(icon, new FrameLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 42), Gravity.CENTER));
            return frame;
        }
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Miniatura de archivo guardado");
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
        frame.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(activity, 32), Ui.dp(activity, 32), Gravity.CENTER));
        Bitmap cached = cache.get(uri);
        if (cached != null) {
            image.setImageBitmap(cached);
            spinner.setVisibility(View.GONE);
        } else pending.add(new Request(frame, image, spinner, uri, mime, Ui.dp(activity, sizeDp)));
        return frame;
    }

    void loadVisible() {
        Rect visible = new Rect();
        for (Request request : pending) {
            if (request.started || !request.frame.isAttachedToWindow() || !request.frame.getLocalVisibleRect(visible)) continue;
            request.started = true;
            executor.execute(() -> load(request));
        }
    }

    private void load(Request request) {
        Bitmap bitmap = null;
        try {
            if (request.mime != null && request.mime.startsWith("video/")) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(activity, Uri.parse(request.uri));
                    bitmap = android.os.Build.VERSION.SDK_INT >= 27
                            ? retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            request.size * 2, request.size * 2) : retriever.getFrameAtTime();
                } finally { retriever.release(); }
            } else {
                Uri uri = Uri.parse(request.uri);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
                    if (stream != null) BitmapFactory.decodeStream(stream, null, bounds);
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inSampleSize = 1;
                while (bounds.outWidth / (options.inSampleSize * 2) >= request.size &&
                        bounds.outHeight / (options.inSampleSize * 2) >= request.size) options.inSampleSize *= 2;
                try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
                    if (stream != null) bitmap = BitmapFactory.decodeStream(stream, null, options);
                }
            }
            if (bitmap != null) cache.put(request.uri, bitmap);
        } catch (Exception | OutOfMemoryError ignored) { }
        Bitmap result = bitmap;
        activity.runOnUiThread(() -> {
            if (!request.frame.isAttachedToWindow()) return;
            request.spinner.setVisibility(View.GONE);
            if (result != null) request.image.setImageBitmap(result);
            else {
                TextView missing = Ui.text(activity, "Vista previa\nno disponible", 12, Ui.MUTED, false);
                missing.setGravity(Gravity.CENTER);
                request.frame.addView(missing, new FrameLayout.LayoutParams(-1, -1));
            }
        });
    }

    void shutdown() { executor.shutdownNow(); }

    private static final class Request {
        final FrameLayout frame;
        final ImageView image;
        final ProgressBar spinner;
        final String uri;
        final String mime;
        final int size;
        boolean started;

        Request(FrameLayout frame, ImageView image, ProgressBar spinner, String uri, String mime, int size) {
            this.frame = frame;
            this.image = image;
            this.spinner = spinner;
            this.uri = uri;
            this.mime = mime;
            this.size = size;
        }
    }
}
