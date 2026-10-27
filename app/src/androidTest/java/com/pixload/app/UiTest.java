package com.pixload.app;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class UiTest {
    @Test public void javaCallersKeepButtonsAndAccessibleIconActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicInteger clicks = new AtomicInteger();
        TextView button = Ui.button(context, "Guardar", R.drawable.tabler_download, true, clicks::incrementAndGet);
        assertEquals("Guardar", button.getContentDescription().toString());
        assertEquals('\uFFFC', button.getText().charAt(0));
        assertTrue(button.getText() instanceof Spanned);
        assertEquals(1, ((Spanned) button.getText()).getSpans(0, 1, ReplacementSpan.class).length);
        assertEquals(Ui.dp(context, 50), button.getMinHeight());
        assertTrue(button.performClick());
        ImageView icon = Ui.plainIconAction(context, R.drawable.tabler_download, clicks::incrementAndGet);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, icon.getImportantForAccessibility());
        assertTrue(icon.performClick());
        assertEquals(2, clicks.get());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                Ui.icon(context, R.drawable.tabler_download, Ui.CYAN).getImportantForAccessibility());
    }

    @Test public void layoutHelpersKeepDimensionsAndStyles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LinearLayout card = Ui.card(context, 16);
        int padding = Ui.dp(context, 16);
        assertEquals(padding, card.getPaddingLeft());
        assertEquals(padding, card.getPaddingBottom());
        assertEquals(LinearLayout.VERTICAL, card.getOrientation());
        Ui.gap(card, context, 12);
        assertEquals(Ui.dp(context, 12), card.getChildAt(0).getLayoutParams().height);
        TextView raw = Ui.rawText(context, "Pixiv title", 16, Ui.WHITE, false);
        assertEquals("Pixiv title", raw.getText().toString());
        assertFalse(raw.getText() instanceof Spanned);
    }
}
