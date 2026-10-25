package com.pixload.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private static Typeface titilliumRegular;
    private static Typeface titilliumBold;
    static final int BACK = Color.rgb(15, 19, 28);
    static final int PANEL = Color.rgb(24, 27, 37);
    static final int RAISED = Color.rgb(28, 31, 41);
    static final int INPUT = Color.rgb(10, 14, 23);
    static final int BLUE = Color.rgb(50, 151, 223);
    static final int CYAN = Color.rgb(123, 208, 255);
    static final int GREEN = Color.rgb(78, 222, 163);
    static final int DANGER = Color.rgb(255, 112, 122);
    static final int WHITE = Color.rgb(223, 226, 239);
    static final int MUTED = Color.rgb(174, 185, 199);
    static final int BORDER = Color.rgb(64, 72, 81);

    private Ui() { }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }

    static GradientDrawable background(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    static GradientDrawable outlined(Context context, int color, int radius) {
        GradientDrawable drawable = background(context, color, radius);
        drawable.setStroke(dp(context, 1), BORDER);
        return drawable;
    }

    static GradientDrawable action(Context context) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BLUE, Color.rgb(0, 166, 224), BLUE});
        drawable.setCornerRadius(dp(context, 14));
        return drawable;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(AppLanguage.label(context, value));
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(typeface(context, bold));
        return view;
    }

    static TextView rawText(Context context, String value, int size, int color, boolean bold) {
        TextView view = text(context, "", size, color, bold);
        view.setText(value);
        return view;
    }

    static Typeface typeface(Context context, boolean bold) {
        if (bold) {
            if (titilliumBold == null) titilliumBold = Typeface.createFromAsset(context.getAssets(), "fonts/titillium_web_bold.ttf");
            return titilliumBold;
        }
        if (titilliumRegular == null) titilliumRegular = Typeface.createFromAsset(context.getAssets(), "fonts/titillium_web_regular.ttf");
        return titilliumRegular;
    }

    static TextView button(Context context, String label, boolean primary, Runnable action) {
        TextView view = text(context, label, 15, primary ? INPUT : CYAN, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 50));
        view.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        view.setBackground(primary ? action(context) : outlined(context, RAISED, 14));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    static TextView button(Context context, String label, int icon, boolean primary, Runnable action) {
        TextView view = button(context, label, primary, action);
        withIcon(view, icon, primary ? INPUT : CYAN);
        return view;
    }

    static TextView dangerButton(Context context, String label, int icon, Runnable action) {
        TextView view = button(context, label, false, action);
        view.setTextColor(DANGER);
        GradientDrawable outline = background(context, RAISED, 14);
        outline.setStroke(dp(context, 1), DANGER);
        view.setBackground(outline);
        withIcon(view, icon, DANGER);
        return view;
    }

    static void withIcon(TextView view, int resource, int color) {
        Drawable drawable = view.getContext().getDrawable(resource).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(view.getContext(), 20), dp(view.getContext(), 20));
        String label = view.getText().toString();
        SpannableString centered = new SpannableString("\uFFFC" + label);
        centered.setSpan(new InlineIcon(drawable, dp(view.getContext(), 8)), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setCompoundDrawablesRelative(null, null, null, null);
        view.setText(centered);
        view.setContentDescription(label);
    }

    private static final class InlineIcon extends ReplacementSpan {
        private final Drawable icon;
        private final int spacing;

        InlineIcon(Drawable icon, int spacing) {
            this.icon = icon;
            this.spacing = spacing;
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            return icon.getBounds().width() + spacing;
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int baseline, int bottom, Paint paint) {
            Paint.FontMetricsInt font = paint.getFontMetricsInt();
            int center = baseline + (font.ascent + font.descent) / 2;
            canvas.save();
            canvas.translate(x, center - icon.getBounds().height() / 2f);
            icon.draw(canvas);
            canvas.restore();
        }
    }

    static ImageView icon(Context context, int resource, int color) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageTintList(ColorStateList.valueOf(color));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    static ImageView iconAction(Context context, int resource, Runnable action) {
        ImageView button = icon(context, resource, CYAN);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setBackground(outlined(context, RAISED, 14));
        button.setPadding(dp(context, 13), dp(context, 13), dp(context, 13), dp(context, 13));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static ImageView plainIconAction(Context context, int resource, Runnable action) {
        return plainIconAction(context, resource, CYAN, action);
    }

    static ImageView plainIconAction(Context context, int resource, int color, Runnable action) {
        ImageView button = icon(context, resource, color);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static LinearLayout label(Context context, String value, int resource, int size, int color) {
        LinearLayout label = row(context);
        label.addView(icon(context, resource, color), new LinearLayout.LayoutParams(dp(context, size + 2), dp(context, size + 2)));
        TextView text = text(context, value, size, color, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp(context, 8);
        label.addView(text, params);
        return label;
    }

    static void gap(LinearLayout parent, Context context, int height) {
        View gap = new View(context);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(context, height)));
    }

    static LinearLayout card(Context context, int padding) {
        LinearLayout card = column(context);
        card.setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding));
        card.setBackground(outlined(context, PANEL, 18));
        return card;
    }
}
