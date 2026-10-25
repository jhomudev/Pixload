package com.pixload.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

final class GalleryViewer {
    private GalleryViewer() { }

    static VideoView show(Activity activity, LinearLayout container, GalleryDocuments.Item item,
                          ExecutorService executor) {
        int height = Math.min(Ui.dp(activity, 470), activity.getResources().getDisplayMetrics().heightPixels / 2);
        return show(activity, container, item, executor, height);
    }

    static VideoView show(Activity activity, LinearLayout container, GalleryDocuments.Item item,
                          ExecutorService executor, int height) {
        String mime = item.mime == null ? "" : item.mime;
        String name = item.name.toLowerCase(java.util.Locale.ROOT);
        if (mime.startsWith("video/") || name.endsWith(".mp4")) {
            FrameLayout frame = new FrameLayout(activity);
            frame.setBackground(Ui.background(activity, Ui.INPUT, 12));
            container.addView(frame, new LinearLayout.LayoutParams(-1, height));
            VideoView video = new VideoView(activity);
            // VideoView is a SurfaceView: an opaque background on the view itself can hide its video surface.
            frame.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            MediaController controller = new MediaController(activity);
            controller.setAnchorView(video);
            video.setMediaController(controller);
            ProgressBar spinner = new ProgressBar(activity);
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            frame.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40), Gravity.CENTER));
            video.setOnPreparedListener(player -> {
                player.setLooping(true); // Pixiv ugoira MP4s can last under a second.
                spinner.setVisibility(View.GONE);
            });
            video.setOnErrorListener((player, what, extra) -> {
                Log.w("PixloadPlayer", "Video playback failed: " + what + "/" + extra);
                spinner.setVisibility(View.GONE);
                container.addView(Ui.text(activity, "No se pudo reproducir el vídeo.", 14, Ui.MUTED, false));
                return true;
            });
            video.setVideoURI(item.uri);
            TextView play = Ui.button(activity, "Reproducir vídeo en bucle", R.drawable.tabler_player_play,
                    true, video::start);
            if (height == -1) {
                FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                playParams.bottomMargin = Ui.dp(activity, 20);
                frame.addView(play, playParams);
            } else {
                Ui.gap(container, activity, 10);
                container.addView(play);
            }
            return video;
        }
        if (mime.startsWith("text/") || name.endsWith(".txt")) {
            TextView text = Ui.text(activity, "Abriendo texto…", 15, Ui.WHITE, false);
            text.setTextIsSelectable(true);
            text.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12), Ui.dp(activity, 12));
            text.setBackground(Ui.background(activity, Ui.PANEL, 12));
            container.addView(text);
            executor.execute(() -> {
                String value;
                try (InputStream input = activity.getContentResolver().openInputStream(item.uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new Exception("Archivo no disponible");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (output.size() + read > 2 * 1024 * 1024) {
                            output.write(buffer, 0, 2 * 1024 * 1024 - output.size());
                            break;
                        }
                        output.write(buffer, 0, read);
                    }
                    value = new String(output.toByteArray(), StandardCharsets.UTF_8);
                    if (output.size() >= 2 * 1024 * 1024) value += "\n\n… " + AppLanguage.label(activity, "Texto truncado para la vista previa.");
                } catch (Exception error) { value = AppLanguage.label(activity, "No se pudo leer el texto: ") + AppLanguage.error(activity, error.getMessage()); }
                String result = value;
                activity.runOnUiThread(() -> { if (text.isAttachedToWindow()) text.setText(result); });
            });
            return null;
        }
        if (mime.startsWith("image/") || name.matches(".*\\.(png|jpe?g|webp|gif)$")) {
            FrameLayout frame = new FrameLayout(activity);
            frame.setBackground(Ui.background(activity, Ui.INPUT, 12));
            frame.setClipToOutline(height != -1);
            container.addView(frame, new LinearLayout.LayoutParams(-1, height));
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            ProgressBar spinner = new ProgressBar(activity);
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            frame.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40), Gravity.CENTER));
            executor.execute(() -> {
                try {
                    if (name.endsWith(".gif") && Build.VERSION.SDK_INT < 28) {
                        byte[] bytes = readGif(activity, item.uri);
                        Movie movie = Movie.decodeByteArray(bytes, 0, bytes.length);
                        if (movie == null) throw new Exception("GIF no compatible");
                        activity.runOnUiThread(() -> {
                            if (!image.isAttachedToWindow()) return;
                            frame.removeView(image);
                            frame.addView(new MovieView(activity, movie), 0, new FrameLayout.LayoutParams(-1, -1));
                            frame.removeView(spinner);
                        });
                    } else if (Build.VERSION.SDK_INT >= 28) {
                        Drawable drawable = ImageDecoder.decodeDrawable(
                                ImageDecoder.createSource(activity.getContentResolver(), item.uri),
                                (decoder, info, source) -> {
                                    int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                                    decoder.setTargetSampleSize(Math.max(1, largest / 1600));
                                });
                        activity.runOnUiThread(() -> {
                            if (!image.isAttachedToWindow()) return;
                            image.setImageDrawable(drawable);
                            if (drawable instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) drawable).start();
                            frame.removeView(spinner);
                        });
                    } else {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        try (InputStream input = activity.getContentResolver().openInputStream(item.uri)) {
                            if (input != null) BitmapFactory.decodeStream(input, null, bounds);
                        }
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 1;
                        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 1600)
                            options.inSampleSize *= 2;
                        Bitmap bitmap;
                        try (InputStream input = activity.getContentResolver().openInputStream(item.uri)) {
                            bitmap = input == null ? null : BitmapFactory.decodeStream(input, null, options);
                        }
                        if (bitmap == null) throw new Exception("Imagen no disponible");
                        activity.runOnUiThread(() -> {
                            if (!image.isAttachedToWindow()) return;
                            image.setImageBitmap(bitmap);
                            frame.removeView(spinner);
                        });
                    }
                } catch (Exception | OutOfMemoryError error) {
                    activity.runOnUiThread(() -> {
                        if (image.isAttachedToWindow()) {
                            frame.removeView(spinner);
                            TextView failure = Ui.text(activity, "No se pudo mostrar este archivo.", 14, Ui.MUTED, false);
                            failure.setGravity(Gravity.CENTER);
                            frame.addView(failure, new FrameLayout.LayoutParams(-1, -1));
                        }
                    });
                }
            });
            return null;
        }
        container.addView(Ui.text(activity, "Este tipo de archivo no tiene vista previa.", 14, Ui.MUTED, false));
        return null;
    }

    private static byte[] readGif(Activity activity, Uri uri) throws Exception {
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new Exception("GIF no disponible");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 24 * 1024 * 1024) throw new Exception("GIF demasiado grande para previsualizar");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static final class MovieView extends View {
        private final Movie movie;
        private long start;

        MovieView(Activity activity, Movie movie) { super(activity); this.movie = movie; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (start == 0) start = SystemClock.uptimeMillis();
            movie.setTime((int) ((SystemClock.uptimeMillis() - start) % Math.max(movie.duration(), 100)));
            float scale = Math.min((float) getWidth() / movie.width(), (float) getHeight() / movie.height());
            canvas.save();
            canvas.translate((getWidth() - movie.width() * scale) / 2, (getHeight() - movie.height() * scale) / 2);
            canvas.scale(scale, scale);
            movie.draw(canvas, 0, 0);
            canvas.restore();
            if (isAttachedToWindow()) postInvalidateDelayed(33);
        }
    }
}
