package com.pixload.app;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import java.lang.reflect.Field;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4.class)
public class MainActivityShellTest {
    @Test public void tabsAndQueueKeepNormalShellWithoutFade() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup root = activity.findViewById(android.R.id.content);
                View shell = root.getChildAt(0);
                View header = ((ViewGroup) shell).getChildAt(0);
                View nav = nav(activity);
                View page = ((ViewGroup) shell).getChildAt(1);
                assertEquals(1f, shell.getAlpha(), 0f);

                tab(activity, R.string.nav_preview).performClick();
                assertSame(shell, root.getChildAt(0));
                assertSame(header, ((ViewGroup) shell).getChildAt(0));
                assertSame(nav, nav(activity));
                assertNotSame(page, ((ViewGroup) shell).getChildAt(1));
                assertNotNull(findText(((ViewGroup) shell).getChildAt(1), "Vista previa"));
                assertEquals(1f, shell.getAlpha(), 0f);
                assertEquals(Ui.CYAN, ((TextView) ((ViewGroup) ((ViewGroup) nav).getChildAt(1))
                        .getChildAt(1)).getCurrentTextColor());
                assertEquals(Ui.MUTED, ((TextView) ((ViewGroup) ((ViewGroup) nav).getChildAt(0))
                        .getChildAt(1)).getCurrentTextColor());

                tab(activity, R.string.nav_gallery).performClick();
                assertSame(header, ((ViewGroup) shell).getChildAt(0));
                assertSame(nav, nav(activity));
                assertEquals(1f, shell.getAlpha(), 0f);
                assertNotNull(findText(((ViewGroup) shell).getChildAt(1), "Tu galería"));

                View queue = findDescription(shell, "Cola de descargas");
                assertNotNull(queue);
                queue.performClick();
                assertNotNull(findText(((ViewGroup) shell).getChildAt(1), "Cola de descargas"));
                assertSame(header, ((ViewGroup) shell).getChildAt(0));
                assertSame(nav, nav(activity));
                assertEquals(1f, shell.getAlpha(), 0f);
                tab(activity, R.string.nav_download).performClick();
                assertSame(header, ((ViewGroup) shell).getChildAt(0));
                assertSame(nav, nav(activity));
            });
        }
    }

    @Test public void selectionBarRemainsAbovePersistentNavigation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                PixivClient.Work work = new PixivClient.Work();
                work.id = "fixture";
                work.title = "Test novel";
                work.author = "Test author";
                work.type = "novel";
                work.text = "Test content";
                try {
                    Field field = MainActivity.class.getDeclaredField("work");
                    field.setAccessible(true);
                    field.set(activity, work);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                tab(activity, R.string.nav_preview).performClick();
                View nav = nav(activity);
                ViewGroup bottom = (ViewGroup) nav.getParent();
                View count = findText(bottom, "1 novela · TXT");
                assertNotNull(count);
                assertEquals(bottom.getChildCount() - 1, bottom.indexOfChild(nav));
                assertEquals(0, bottom.indexOfChild((View) count.getParent()));
            });
        }
    }

    private static View nav(MainActivity activity) {
        return (View) tab(activity, R.string.nav_download).getParent();
    }

    private static View tab(MainActivity activity, int label) {
        return findDescription(activity.findViewById(android.R.id.content), activity.getString(label));
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

    private static View findDescription(View view, String description) {
        if (description.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
}
