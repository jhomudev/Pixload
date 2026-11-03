package com.pixload.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/** Native view construction/layout only: no history, SAF, network or image decoding. */
@RunWith(AndroidJUnit4.class)
public class GalleryRenderBenchmarkTest {
    static final int WIDTH = 1080;
    static final int HEIGHT = 1600;

    @Test public void measure500Entries() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int run = 0; run < 6; run++) {
                Fixture fixture = new Fixture(500);
                long start = SystemClock.elapsedRealtimeNanos();
                fixture.render();
                long elapsed = SystemClock.elapsedRealtimeNanos() - start;
                Log.i("GalleryRender", "run=" + run + " entries=500 renderLayoutMs="
                        + elapsed / 1_000_000.0 + " views=" + countViews(fixture.page)
                        + " cards=" + countTitles(fixture.page));
                fixture.close();
            }
        });
    }

    @Test public void initialRenderingDefersOffscreenCards() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture(500);
            try {
                fixture.render();
                int cards = countTitles(fixture.page);
                assertTrue("Initial cards must be viewport-sized, not " + cards, cards < 40);
                assertTrue("First card remains available", findText(fixture.page, "Fixture 0") != null);
            } finally {
                fixture.close();
            }
        });
    }

    @Test public void fullTraversalRetainsCardsWithoutRebindingTheFirstRow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture(500);
            try {
                fixture.render();
                View first = findText(fixture.page, "Fixture 0");
                for (int y = HEIGHT; y < fixture.content.getHeight() + HEIGHT; y += HEIGHT) {
                    fixture.page.scrollTo(0, y);
                    fixture.layout();
                }
                org.junit.Assert.assertEquals(500, countTitles(fixture.page));
                Log.i("GalleryRender", "fullTraversal entries=500 views=" + countViews(fixture.page)
                        + " cards=" + countTitles(fixture.page));
                fixture.page.scrollTo(0, 0);
                fixture.layout();
                org.junit.Assert.assertSame(first, findText(fixture.page, "Fixture 0"));
            } finally {
                fixture.close();
            }
        });
    }

    @Test public void returningToMixedHeightRowsPreservesTheMeasuredScrollExtent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture(100);
            fixture.width = 500;
            for (int i = 2; i < fixture.entries.size(); i++) {
                fixture.entries.set(i, new GalleryStore.Entry("content://gallery.fixture/" + i,
                        "Fixture " + i, "image/png", "", true, Integer.MAX_VALUE, 1700000000000L));
            }
            try {
                fixture.render();
                for (int y = HEIGHT; y < fixture.content.getHeight() + HEIGHT; y += HEIGHT) {
                    fixture.page.scrollTo(0, y);
                    fixture.layout();
                }
                int measuredExtent = fixture.content.getHeight();
                fixture.content.removeAllViews();
                fixture.page.scrollTo(0, 0);
                fixture.render();
                org.junit.Assert.assertEquals("Returning must retain measured mixed-row scroll geometry",
                        measuredExtent, fixture.content.getHeight());
            } finally {
                fixture.close();
            }
        });
    }

    static final class Fixture implements AutoCloseable {
        final MainActivity activity;
        final ScrollView page;
        final LinearLayout content;
        final List<GalleryStore.Entry> entries = new ArrayList<>();
        int width = WIDTH;

        Fixture(int count) {
            this(count, null);
        }

        Fixture(int count, android.content.ContentResolver resolver) {
            Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Context isolated = new ContextWrapper(target) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences("gallery_render_test_" + name, mode);
                }
                @Override public android.content.ContentResolver getContentResolver() {
                    // UI widgets still need Android's Settings provider; only gallery IO is synthetic.
                    return resolver != null && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                            ? resolver : super.getContentResolver();
                }
                @Override public Context createConfigurationContext(android.content.res.Configuration configuration) {
                    return this; // Keep the synthetic resolver/preferences through AppLanguage.wrap.
                }
            };
            activity = resolver == null ? new MainActivity() : new ShareProbeActivity();
            invoke(activity, "attachBaseContext", new Class<?>[]{Context.class}, isolated);
            page = new ScrollView(activity);
            content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            page.addView(content);
            for (int i = 0; i < count; i++) {
                entries.add(new GalleryStore.Entry("content://gallery.fixture/" + i,
                        "Fixture " + i, "image/png", "", false, 1, 1700000000000L + i));
            }
        }

        void render() {
            invoke(activity, "galleryEntries", new Class<?>[]{List.class, LinearLayout.class, boolean.class},
                    entries, content, false);
            layout();
        }

        void layout() {
            if (content.getChildCount() > 0) content.getChildAt(0).requestLayout();
            page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            page.layout(0, 0, width, HEIGHT);
        }

        @Override public void close() {
            Object images = field(activity, "galleryImages");
            invoke(images, "shutdown", new Class<?>[]{});
            ((java.util.concurrent.ExecutorService) field(activity, "galleryIO")).shutdownNow();
        }
    }

    static Object field(Object target, String name) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method;
            try {
                method = target.getClass().getDeclaredMethod(name, types);
            } catch (NoSuchMethodException missing) {
                method = MainActivity.class.getDeclaredMethod(name, types);
            }
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static int countViews(View view) {
        int count = 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countViews(group.getChildAt(i));
        }
        return count;
    }

    static int countTitles(View view) {
        int count = view instanceof TextView && ((TextView) view).getText().toString().startsWith("Fixture ") ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countTitles(group.getChildAt(i));
        }
        return count;
    }

    static View findText(View view, String text) {
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

    public static class ShareProbeActivity extends MainActivity {
        volatile android.content.Intent shared;

        @Override public void startActivity(android.content.Intent intent) {
            shared = intent;
        }
    }
}
