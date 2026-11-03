package com.pixload.app;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.pixload.app.GalleryRenderBenchmarkTest.*;
import static org.junit.Assert.*;

/** Synthetic cached entries only; never writes gallery history or confirms deletion. */
@RunWith(AndroidJUnit4.class)
public class GalleryRenderingTest {
    @Test public void deferredRowSharesItsOwnUriThroughTheExistingHandler() {
        AtomicReference<Uri> queried = new AtomicReference<>();
        ContentProvider provider = new ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
                queried.set(uri);
                MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});
                cursor.addRow(new Object[]{"fixture.png"});
                return cursor;
            }
            @Override public String getType(Uri uri) { return "image/png"; }
            @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
            @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
            @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
        };
        AtomicReference<Fixture> ref = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture(501, ContentResolver.wrap(provider));
            ref.set(fixture);
            fixture.render();
            fixture.page.scrollTo(0, fixture.content.getHeight());
            fixture.layout();
            View share = description(fixture.page, AppLanguage.label(fixture.activity, "Compartir ") + "Fixture 500");
            assertNotNull("Last odd card must be realized after a deep scroll", share);
            share.performClick();
        });
        Fixture fixture = ref.get();
        try {
            await(() -> ((ShareProbeActivity) fixture.activity).shared != null);
            Intent chooser = ((ShareProbeActivity) fixture.activity).shared;
            Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            Uri expected = Uri.parse("content://gallery.fixture/500");
            assertEquals(expected, queried.get());
            assertEquals(expected, send.getParcelableExtra(Intent.EXTRA_STREAM));
            assertEquals(expected, send.getClipData().getItemAt(0).getUri());
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(fixture::close);
        }
    }

    @Test public void nativeSurfacePreservesDeepScrollActionsFilteringAndThumbnails() throws Exception {
        File cover = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "gallery-render-test.png");
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.CYAN);
        try (FileOutputStream output = new FileOutputStream(cover)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                java.util.List<GalleryStore.Entry> entries = new java.util.ArrayList<>();
                for (int i = 0; i < 501; i++) entries.add(new GalleryStore.Entry("content://gallery.fixture/" + i,
                        "Fixture " + i, "image/png", Uri.fromFile(cover).buildUpon()
                                .appendQueryParameter("entry", Integer.toString(i)).build().toString(),
                        false, 1, 1700000000000L + i));
                set(activity, "galleryCache", entries);
                set(activity, "galleryOrder", GalleryOrder.ORIGINAL);
                set(activity, "gallerySearch", "");
                set(activity, "galleryTypes", Collections.emptySet());
                invoke(activity, "switchTab", new Class<?>[]{int.class}, 2);
            });
            await(() -> onActivity(scenario, activity -> {
                View title = findText(page(activity), "Fixture 0");
                return title != null && hasBitmap((View) title.getParent());
            }));
            scenario.onActivity(activity -> {
                assertTrue(countTitles(page(activity)) < 40);
                page(activity).scrollTo(0, ((View) page(activity).getChildAt(0)).getHeight());
            });
            await(() -> onActivity(scenario, activity -> {
                View title = findText(page(activity), "Fixture 500");
                return title != null && hasBitmap((View) title.getParent());
            }));
            final int[] savedY = new int[1];
            scenario.onActivity(activity -> {
                savedY[0] = page(activity).getScrollY();
                assertTrue(savedY[0] > HEIGHT * 10);
                View last = findText(page(activity), "Fixture 500");
                ((View) last.getParent()).performClick();
                assertEquals("content://gallery.fixture/500", ((GalleryStore.Entry) field(activity, "galleryEntry")).uri);
            });
            scenario.onActivity(activity -> invoke(activity, "returnToGallery", new Class<?>[]{}));
            await(() -> onActivity(scenario, activity -> page(activity).getScrollY() == savedY[0]
                    && findText(page(activity), "Fixture 500") != null));
            AtomicReference<String> cancelLabel = new AtomicReference<>();
            scenario.onActivity(activity -> {
                cancelLabel.set(activity.getString(R.string.cancel));
                assertNotNull(description(page(activity), AppLanguage.label(activity, "Compartir ") + "Fixture 500"));
                description(page(activity), AppLanguage.label(activity, "Eliminar ") + "Fixture 500").performClick();
            });
            await(() -> activeText("Fixture 500") != null);
            AccessibilityNodeInfo message = activeText("Fixture 500");
            assertTrue(message.getText().toString().contains("Fixture 500"));
            // Cancel through the native dialog: no provider deletion or history mutation is allowed.
            AccessibilityNodeInfo cancel = activeText(cancelLabel.get());
            assertNotNull(cancel);
            assertTrue(cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("Assistive navigation must expose every card", 501, countTitles(page(activity)));
                assertEquals(501, ((java.util.List<?>) field(activity, "galleryCache")).size());
                set(activity, "gallerySearch", "Fixture 49");
                set(activity, "galleryOrder", GalleryOrder.OLDEST);
                invoke(activity, "render", new Class<?>[]{});
            });
            await(() -> onActivity(scenario, activity -> findText(page(activity), "Fixture 490") != null));
            scenario.onActivity(activity -> {
                assertEquals(11, countTitles(page(activity)));
                assertNotNull(findText(page(activity), "Fixture 49"));
                assertNotNull(findText(page(activity), "Fixture 499"));
                assertNull(findText(page(activity), "Fixture 500"));
                assertNotNull(findText(page(activity), "501" + AppLanguage.label(activity, " descargas")));
                assertEquals(501, ((java.util.List<?>) field(activity, "galleryCache")).size());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(100); // Let SurfaceFlinger present the filtered native layout before capture.
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File capture = new File(cover.getParentFile(), "gallery-render-surface.png");
            try (FileOutputStream output = new FileOutputStream(capture)) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
        }
    }

    private interface ActivityCheck { boolean test(MainActivity activity); }
    private interface Check { boolean test(); }

    private static boolean onActivity(ActivityScenario<MainActivity> scenario, ActivityCheck check) {
        AtomicBoolean result = new AtomicBoolean();
        scenario.onActivity(activity -> result.set(check.test(activity)));
        return result.get();
    }

    private static void await(Check check) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (check.test()) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for gallery state");
    }

    private static ScrollView page(MainActivity activity) { return (ScrollView) field(activity, "scroll"); }

    private static void set(MainActivity activity, String name, Object value) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(activity, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static boolean hasBitmap(View view) {
        if (view instanceof ImageView && ((ImageView) view).getDrawable() instanceof BitmapDrawable) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasBitmap(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static View description(View view, String text) {
        if (text.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = description(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo activeText(String text) {
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
            if (node.getText() != null && node.getText().toString().contains(text)) return node;
        }
        return null;
    }
}
