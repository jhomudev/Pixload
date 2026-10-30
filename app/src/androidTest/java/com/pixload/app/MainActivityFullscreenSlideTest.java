package com.pixload.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityFullscreenSlideTest {
    private File fixtureDirectory;

    @After public void removeFixtureFiles() { cleanup(); }
    @Test public void imagesFollowFingerThenSnapBackOrAdvanceWithoutMovingChrome() throws Exception {
        AtomicReference<View> incoming = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    fixture(activity);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            SystemClock.sleep(350); // Let the tiny fixture finish decoding before testing the gesture.
            scenario.onActivity(activity -> {
                try {
                    ViewGroup shell = (ViewGroup) ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
                    FrameLayout overlay = (FrameLayout) shell.getChildAt(0);
                    View current = overlay.getChildAt(0);
                    View toolbar = findText(overlay, "slide-first.png").getParent() instanceof View
                            ? (View) findText(overlay, "slide-first.png").getParent() : null;
                    assertNotNull(toolbar);
                    current.performClick(); // Chrome is visible but must not move with the media.
                    assertEquals(View.VISIBLE, toolbar.getVisibility());
                    int[] location = new int[2];
                    current.getLocationOnScreen(location);
                    float x = location[0] + current.getWidth() / 2f;
                    float y = location[1] + current.getHeight() / 2f;
                    long start = SystemClock.uptimeMillis();
                    touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                    float earlyDrag = Ui.dp(activity, 24f);
                    touch(activity, start, start + 15, MotionEvent.ACTION_MOVE, x - earlyDrag, y);
                    assertEquals("Image must track a short horizontal drag without waiting 56 dp",
                            -earlyDrag, current.getTranslationX(), 3f);
                    touch(activity, start, start + 30, MotionEvent.ACTION_MOVE, x - current.getWidth() * .40f, y);
                    assertNotNull(findText(overlay, "slide-first.png"));
                    assertEquals(-current.getWidth() * .40f, current.getTranslationX(), 3f);
                    assertEquals(2, mediaCount(overlay));
                    View neighbor = overlay.getChildAt(1);
                    incoming.set(neighbor);
                    assertNotNull("Adjacent full-size image must already be decoded, not a skeleton",
                            ((ImageView) ((FrameLayout) ((ViewGroup) neighbor).getChildAt(0)).getChildAt(0)).getDrawable());
                    assertEquals(current.getWidth() * .60f, neighbor.getTranslationX(), 3f);
                    assertEquals(0f, toolbar.getTranslationX(), 0f);
                    touch(activity, start, start + 45, MotionEvent.ACTION_UP, x - current.getWidth() * .40f, y);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(400);
            scenario.onActivity(activity -> {
                try {
                    assertNotNull(findText(activity.findViewById(android.R.id.content), "slide-second.png"));
                    FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                            activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                    assertSame("Settled image must reuse the decoded incoming viewer", incoming.get(), overlay.getChildAt(0));
                    assertEquals("Settled image must stay visible rather than slide offscreen",
                            0f, overlay.getChildAt(0).getTranslationX(), 3f);
                    cleanup();
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }

    @Test public void rapidForwardSwipeDoesNotSnapTheVisibleImageAtSecondTouch() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try { fixture(activity, true); } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            scenario.onActivity(activity -> {
                FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                        activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                View first = overlay.getChildAt(0);
                int[] position = new int[2];
                first.getLocationOnScreen(position);
                float x = position[0] + first.getWidth() / 2f;
                float y = position[1] + first.getHeight() / 2f;
                long start = SystemClock.uptimeMillis();
                touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                touch(activity, start, start + 20, MotionEvent.ACTION_MOVE, x - first.getWidth() * .4f, y);
                touch(activity, start, start + 35, MotionEvent.ACTION_UP, x - first.getWidth() * .4f, y);
                float before = first.getTranslationX();
                long again = SystemClock.uptimeMillis();
                touch(activity, again, again, MotionEvent.ACTION_DOWN, x, y);
                assertSame("Touch during settling must not replace the visible image", first, overlay.getChildAt(0));
                assertEquals("Touch must not jump an unfinished slide", before, first.getTranslationX(), 3f);
                touch(activity, again, again + 20, MotionEvent.ACTION_MOVE, x - first.getWidth() * .4f, y);
                touch(activity, again, again + 35, MotionEvent.ACTION_UP, x - first.getWidth() * .4f, y);
            });
            SystemClock.sleep(600);
            scenario.onActivity(activity -> {
                FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                        activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                assertNotNull("Both rapid gestures must navigate to the third image",
                        findText(overlay, "slide-third.png"));
                try { cleanup(); } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }

    @Test public void rapidSwipeStartingOnIncomingImageStillAdvances() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try { fixture(activity, true); } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            scenario.onActivity(activity -> {
                FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                        activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                View first = overlay.getChildAt(0);
                int[] position = new int[2];
                first.getLocationOnScreen(position);
                float x = position[0] + first.getWidth() / 2f;
                float y = position[1] + first.getHeight() / 2f;
                float drag = first.getWidth() * .75f;
                long start = SystemClock.uptimeMillis();
                touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                touch(activity, start, start + 20, MotionEvent.ACTION_MOVE, x - drag, y);
                touch(activity, start, start + 35, MotionEvent.ACTION_UP, x - drag, y);
                long again = SystemClock.uptimeMillis();
                touch(activity, again, again, MotionEvent.ACTION_DOWN, x, y);
                touch(activity, again, again + 20, MotionEvent.ACTION_MOVE, x - drag, y);
                touch(activity, again, again + 35, MotionEvent.ACTION_UP, x - drag, y);
            });
            SystemClock.sleep(600);
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.findViewById(android.R.id.content), "slide-third.png"));
                cleanup();
            });
        }
    }

    @Test public void rapidReverseSwipeReturnsToFirstImageWithoutSnappingAtTouch() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try { fixture(activity); } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            scenario.onActivity(activity -> {
                FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                        activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                View first = overlay.getChildAt(0);
                int[] position = new int[2];
                first.getLocationOnScreen(position);
                float x = position[0] + first.getWidth() / 2f;
                float y = position[1] + first.getHeight() / 2f;
                long start = SystemClock.uptimeMillis();
                touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                touch(activity, start, start + 20, MotionEvent.ACTION_MOVE, x - first.getWidth() * .4f, y);
                touch(activity, start, start + 35, MotionEvent.ACTION_UP, x - first.getWidth() * .4f, y);
                float before = first.getTranslationX();
                long again = SystemClock.uptimeMillis();
                touch(activity, again, again, MotionEvent.ACTION_DOWN, x, y);
                assertSame(first, overlay.getChildAt(0));
                assertEquals(before, first.getTranslationX(), 3f);
                touch(activity, again, again + 20, MotionEvent.ACTION_MOVE, x + first.getWidth() * .4f, y);
                touch(activity, again, again + 35, MotionEvent.ACTION_UP, x + first.getWidth() * .4f, y);
            });
            SystemClock.sleep(600);
            scenario.onActivity(activity -> {
                FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                        activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                assertNotNull("First image is the active viewer", findText(overlay, "slide-first.png"));
                cleanup();
            });
        }
    }

    @Test public void previousImageAlsoFollowsTheFinger() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    fixture(activity);
                    set(activity, "galleryIndex", 1);
                    java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render");
                    render.setAccessible(true);
                    render.invoke(activity);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            scenario.onActivity(activity -> {
                try {
                    FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                            activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                    View current = overlay.getChildAt(0);
                    int[] position = new int[2];
                    current.getLocationOnScreen(position);
                    float x = position[0] + current.getWidth() / 2f;
                    float y = position[1] + current.getHeight() / 2f;
                    long start = SystemClock.uptimeMillis();
                    touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                    touch(activity, start, start + 20, MotionEvent.ACTION_MOVE, x + current.getWidth() * .4f, y);
                    assertEquals(current.getWidth() * .4f, current.getTranslationX(), 3f);
                    assertEquals(-current.getWidth() * .6f, overlay.getChildAt(1).getTranslationX(), 3f);
                    touch(activity, start, start + 40, MotionEvent.ACTION_UP, x + current.getWidth() * .4f, y);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(400);
            scenario.onActivity(activity -> {
                try {
                    assertNotNull(findText(activity.findViewById(android.R.id.content), "slide-first.png"));
                    cleanup();
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }

    @Test public void shortReverseDragSnapsBackAndImageTapStillTogglesControls() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try { fixture(activity); } catch (Exception error) { throw new AssertionError(error); }
            });
            awaitSlideLayout(scenario);
            scenario.onActivity(activity -> {
                try {
                    FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                            activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                    View current = overlay.getChildAt(0);
                    int[] position = new int[2];
                    current.getLocationOnScreen(position);
                    float x = position[0] + current.getWidth() / 2f;
                    float y = position[1] + current.getHeight() / 2f;
                    long start = SystemClock.uptimeMillis();
                    touch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
                    touch(activity, start, start + 20, MotionEvent.ACTION_MOVE, x - current.getWidth() * .15f, y);
                    assertEquals(2, mediaCount(overlay));
                    touch(activity, start, start + 35, MotionEvent.ACTION_MOVE, x + 10, y);
                    assertEquals(0f, current.getTranslationX(), 0f);
                    touch(activity, start, start + 50, MotionEvent.ACTION_UP, x + 10, y);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(400);
            scenario.onActivity(activity -> {
                try {
                    assertNotNull(findText(activity.findViewById(android.R.id.content), "slide-first.png"));
                    FrameLayout overlay = (FrameLayout) ((ViewGroup) ((ViewGroup)
                            activity.findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);
                    assertEquals(2, mediaCount(overlay));
                    View current = overlay.getChildAt(0);
                    current.performClick();
                    assertEquals(View.VISIBLE, findText(overlay, "slide-first.png").getVisibility());
                    int[] position = new int[2];
                    current.getLocationOnScreen(position);
                    float x = position[0] + current.getWidth() / 2f;
                    float y = position[1] + current.getHeight() / 2f;
                    long next = SystemClock.uptimeMillis();
                    touch(activity, next, next, MotionEvent.ACTION_DOWN, x, y);
                    touch(activity, next, next + 20, MotionEvent.ACTION_MOVE, x - current.getWidth() * .4f, y);
                    assertEquals("Swipe after snap-back must follow the finger",
                            -current.getWidth() * .4f, current.getTranslationX(), 3f);
                    touch(activity, next, next + 35, MotionEvent.ACTION_UP, x - current.getWidth() * .4f, y);
                } catch (Exception error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(400);
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.findViewById(android.R.id.content), "slide-second.png"));
                cleanup();
            });
        }
    }

    private static void awaitSlideLayout(ActivityScenario<MainActivity> scenario) throws Exception {
        CountDownLatch laidOut = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            ViewGroup shell = (ViewGroup) ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            FrameLayout overlay = (FrameLayout) shell.getChildAt(0);
            if (overlay.getWidth() > 0) laidOut.countDown();
            else overlay.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right > left) laidOut.countDown();
            });
        });
        assertTrue("Fullscreen image must be laid out", laidOut.await(3, TimeUnit.SECONDS));
    }

    private void fixture(MainActivity activity) throws Exception { fixture(activity, false); }

    private void fixture(MainActivity activity, boolean thirdImage) throws Exception {
        fixtureDirectory = Files.createTempDirectory(activity.getCacheDir().toPath(), "fullscreen-slide-").toFile();
        File first = image(fixtureDirectory, "slide-first.png", Color.RED);
        File second = image(fixtureDirectory, "slide-second.png", Color.BLUE);
        File third = thirdImage ? image(fixtureDirectory, "slide-third.png", Color.GREEN) : null;
        set(activity, "tab", 2);
        set(activity, "galleryEntry", new GalleryStore.Entry(Uri.fromFile(first).toString(),
                "Fixture", "image/png", "", true, 2, 0));
        java.util.List<GalleryDocuments.Item> items = new java.util.ArrayList<>(Arrays.asList(
                new GalleryDocuments.Item(Uri.fromFile(first), first.getName(), "image/png"),
                new GalleryDocuments.Item(Uri.fromFile(second), second.getName(), "image/png")));
        if (third != null) items.add(new GalleryDocuments.Item(Uri.fromFile(third), third.getName(), "image/png"));
        set(activity, "galleryItems", items);
        set(activity, "galleryFullscreen", true);
        java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("render");
        render.setAccessible(true);
        render.invoke(activity);
    }

    private void cleanup() {
        if (fixtureDirectory == null) return;
        for (String name : Arrays.asList("slide-first.png", "slide-second.png", "slide-third.png")) {
            File file = new File(fixtureDirectory, name);
            if (file.exists()) assertTrue(file.delete());
        }
        assertTrue(fixtureDirectory.delete());
        fixtureDirectory = null;
    }

    private static int mediaCount(FrameLayout overlay) {
        int count = 0;
        for (int i = 0; i < overlay.getChildCount(); i++)
            if (overlay.getChildAt(i) instanceof LinearLayout && overlay.getChildAt(i).getLayoutParams().height == -1) count++;
        return count;
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void touch(MainActivity activity, long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        activity.dispatchTouchEvent(event);
        event.recycle();
    }

    private static File image(File directory, String name, int color) throws Exception {
        File file = new File(directory, name);
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
        return file;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
