package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class LoadingSkeletonTest {
    @Test public void downloadScreenKeepsItsContentDuringWorkLookupWithoutSkeletons() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertNull(root.findViewWithTag("loading-skeleton"));
                try {
                    Field loading = MainActivity.class.getDeclaredField("loading");
                    loading.setAccessible(true);
                    loading.setBoolean(activity, true);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                assertTrue(clickLabel(root, activity.getString(R.string.nav_preview)));
                assertNotNull(root.findViewWithTag("loading-skeleton"));
                assertTrue(clickLabel(root, activity.getString(R.string.nav_download)));
                assertNull(root.findViewWithTag("loading-skeleton"));
                assertTrue(hasText(root, AppLanguage.label(activity, "Hecho para tus obras")));
                java.util.ArrayList<View> buttons = new java.util.ArrayList<>();
                root.findViewsWithText(buttons, AppLanguage.label(activity, "Consultando Pixiv…"),
                        View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                assertFalse(buttons.isEmpty());
                assertFalse(buttons.get(0).isEnabled());
            });
            capture("skeleton-download.png");
        }
    }

    private static boolean containsSpinner(View view) {
        if (view instanceof ProgressBar) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsSpinner(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static boolean clickLabel(View view, String label) {
        if (view.getContentDescription() != null && label.contentEquals(view.getContentDescription())) return view.performClick();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (clickLabel(group.getChildAt(i), label)) return true;
        }
        return false;
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private static boolean hasImage(View view) {
        if (view instanceof ImageView && ((ImageView) view).getDrawable() != null) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasImage(group.getChildAt(i))) return true;
        }
        return false;
    }

    @Test public void detachedSkeletonIsStaticAndStillDrawsItsPlaceholder() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LoadingSkeleton skeleton = new LoadingSkeleton(InstrumentationRegistry.getInstrumentation().getTargetContext());
            skeleton.layout(0, 0, 100, 40);
            Bitmap first = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888);
            Bitmap second = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888);
            skeleton.draw(new Canvas(first));
            android.os.SystemClock.sleep(50);
            skeleton.draw(new Canvas(second));
            assertTrue(first.sameAs(second));
            assertTrue(first.getPixel(50, 20) != 0);
        });
    }

    @Test public void previewQueueAndGalleryThumbnailsUseDecorativeSkeletons() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                PreviewImages preview = new PreviewImages(activity);
                GalleryImages gallery = new GalleryImages(activity);
                try {
                    for (View tile : new View[]{preview.view("https://example.invalid/image", 152),
                            preview.view("https://example.invalid/queue", 120, true),
                            gallery.tile("content://example.test/document/image", "image/png", 140)}) {
                        View skeleton = tile.findViewWithTag("loading-skeleton");
                        assertNotNull(skeleton);
                        assertFalse(containsSpinner(tile));
                        assertFalse(skeleton.isClickable());
                        assertFalse(skeleton.isFocusable());
                        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, skeleton.getImportantForAccessibility());
                    }
                    assertNull(preview.view(null, 152).findViewWithTag("loading-skeleton"));
                    assertNull(gallery.tile("", "image/png", 140).findViewWithTag("loading-skeleton"));
                    assertNull(gallery.tile("content://example.test/document/text", "text/plain", 140)
                            .findViewWithTag("loading-skeleton"));
                } finally { preview.shutdown(); gallery.shutdown(); }
            });
        }
    }

    @Test public void cachedThumbnailsSkipLoadingAndKeepTheirImage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                PreviewImages preview = new PreviewImages(activity);
                GalleryImages gallery = new GalleryImages(activity);
                try {
                    Bitmap image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                    for (Object loader : new Object[]{preview, gallery}) {
                        Field field = loader.getClass().getDeclaredField("cache");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked") android.util.LruCache<String, Bitmap> cache =
                                (android.util.LruCache<String, Bitmap>) field.get(loader);
                        cache.put("cached-image", image);
                    }
                    for (View tile : new View[]{preview.view("cached-image", 152),
                            gallery.tile("cached-image", "image/png", 140)}) {
                        assertNull(tile.findViewWithTag("loading-skeleton"));
                        assertFalse(containsSpinner(tile));
                        assertTrue(hasImage(tile));
                    }
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally { preview.shutdown(); gallery.shutdown(); }
            });
        }
    }

    @Test public void galleryShowsSkeletonUntilItsLookupCompletes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field field = MainActivity.class.getDeclaredField("galleryIO");
                    field.setAccessible(true);
                    ((ExecutorService) field.get(activity)).execute(() -> {
                        blocked.countDown();
                        try { release.await(10, TimeUnit.SECONDS); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                    });
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertTrue(clickLabel(root, activity.getString(R.string.nav_gallery)));
                assertNotNull(root.findViewWithTag("loading-skeleton"));
            });
            capture("skeleton-gallery.png");
            release.countDown();
            awaitNoSkeleton(scenario);
        } finally { release.countDown(); }
    }

    @Test public void galleryRevisitsRenderFromCacheWithoutReloading() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("gallery", android.content.Context.MODE_PRIVATE);
        String saved = prefs.getString("entries", null);
        prefs.edit().putString("entries", "[]").commit();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);
        CountDownLatch secondBlocked = new CountDownLatch(1);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            blockGalleryIO(scenario, blocked, release);
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertTrue(clickLabel(root, activity.getString(R.string.nav_gallery)));
                assertNotNull(root.findViewWithTag("loading-skeleton"));
            });
            release.countDown();
            awaitNoSkeleton(scenario);
            scenario.onActivity(activity -> assertTrue(hasText(activity.getWindow().getDecorView(),
                    AppLanguage.label(activity, "Aún no hay descargas"))));
            blockGalleryIO(scenario, secondBlocked, secondRelease);
            assertTrue(secondBlocked.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                assertTrue(clickLabel(root, activity.getString(R.string.nav_download)));
                assertTrue(clickLabel(root, activity.getString(R.string.nav_gallery)));
                assertNull(root.findViewWithTag("loading-skeleton"));
                assertTrue(hasText(root, AppLanguage.label(activity, "Tu galería")));
                assertTrue(hasText(root, AppLanguage.label(activity, "Aún no hay descargas")));
            });
            capture("cache-gallery.png");
        } finally {
            release.countDown();
            secondRelease.countDown();
            if (saved == null) prefs.edit().remove("entries").commit();
            else prefs.edit().putString("entries", saved).commit();
        }
    }

    private static void blockGalleryIO(ActivityScenario<MainActivity> scenario, CountDownLatch blocked,
                                       CountDownLatch release) {
        scenario.onActivity(activity -> {
            try {
                Field field = MainActivity.class.getDeclaredField("galleryIO");
                field.setAccessible(true);
                ((ExecutorService) field.get(activity)).execute(() -> {
                    blocked.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                });
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    @Test public void viewerSkeletonsGiveWayToTextImagesAndErrors() throws Exception {
        for (String type : new String[]{"text/plain", "image/png"}) {
            for (boolean exists : new boolean[]{true, false}) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                CountDownLatch release = new CountDownLatch(1);
                executor.execute(() -> {
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                });
                File file = File.createTempFile("skeleton-test-", type.startsWith("text") ? ".txt" : ".png",
                        InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
                try {
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        if (type.startsWith("text")) output.write("Example novel".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        else Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output);
                    }
                    if (!exists) assertTrue(file.delete());
                    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                        scenario.onActivity(activity -> {
                            LinearLayout container = Ui.column(activity);
                            activity.setContentView(container);
                            GalleryViewer.show(activity, container,
                                    new GalleryDocuments.Item(Uri.fromFile(file), file.getName(), type), executor);
                            assertNotNull(container.findViewWithTag("loading-skeleton"));
                            assertFalse(containsSpinner(container));
                        });
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        release.countDown();
                        awaitNoSkeleton(scenario);
                        scenario.onActivity(activity -> {
                            View root = activity.getWindow().getDecorView();
                            assertFalse(containsSpinner(root));
                            if (type.startsWith("text")) assertTrue(hasText(root,
                                    exists ? "Example novel" : AppLanguage.label(activity, "No se pudo leer el texto: ")));
                            else if (exists) assertTrue(hasImage(root));
                            else assertTrue(hasText(root, AppLanguage.label(activity, "No se pudo mostrar este archivo.")));
                        });
                    }
                } finally { release.countDown(); executor.shutdownNow(); file.delete(); }
            }
        }
    }

    private static void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        File capture = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), name);
        try (FileOutputStream output = new FileOutputStream(capture)) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot()
                    .compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    private static void awaitNoSkeleton(ActivityScenario<MainActivity> scenario) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        boolean[] pending = {true};
        do {
            scenario.onActivity(activity -> pending[0] = activity.getWindow().getDecorView()
                    .findViewWithTag("loading-skeleton") != null);
            if (!pending[0]) return;
            android.os.SystemClock.sleep(20);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Loading skeleton did not finish");
    }
}
