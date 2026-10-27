package com.pixload.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal object Ui {
    private var titilliumRegular: Typeface? = null
    private var titilliumBold: Typeface? = null
    @JvmField val BACK = Color.rgb(15, 19, 28)
    @JvmField val PANEL = Color.rgb(24, 27, 37)
    @JvmField val RAISED = Color.rgb(28, 31, 41)
    @JvmField val INPUT = Color.rgb(10, 14, 23)
    @JvmField val BLUE = Color.rgb(50, 151, 223)
    @JvmField val CYAN = Color.rgb(123, 208, 255)
    @JvmField val GREEN = Color.rgb(78, 222, 163)
    @JvmField val DANGER = Color.rgb(255, 112, 122)
    @JvmField val WHITE = Color.rgb(223, 226, 239)
    @JvmField val MUTED = Color.rgb(174, 185, 199)
    @JvmField val BORDER = Color.rgb(64, 72, 81)

    @JvmStatic fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()

    @JvmStatic fun background(context: Context, color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius.toFloat()).toFloat()
        }

    @JvmStatic fun outlined(context: Context, color: Int, radius: Int): GradientDrawable =
        background(context, color, radius).apply { setStroke(dp(context, 1f), BORDER) }

    @JvmStatic fun action(context: Context): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(BLUE, Color.rgb(0, 166, 224), BLUE))
            .apply { cornerRadius = dp(context, 14f).toFloat() }

    @JvmStatic fun column(context: Context): LinearLayout =
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    @JvmStatic fun row(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    @JvmStatic fun text(context: Context, value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = AppLanguage.label(context, value)
            setTextColor(color)
            setTextSize(size.toFloat())
            typeface = typeface(context, bold)
        }

    @JvmStatic fun rawText(context: Context, value: String, size: Int, color: Int, bold: Boolean): TextView =
        text(context, "", size, color, bold).apply { text = value }

    @JvmStatic fun typeface(context: Context, bold: Boolean): Typeface {
        if (bold) {
            if (titilliumBold == null) titilliumBold = Typeface.createFromAsset(context.assets, "fonts/titillium_web_bold.ttf")
            return titilliumBold!!
        }
        if (titilliumRegular == null) titilliumRegular = Typeface.createFromAsset(context.assets, "fonts/titillium_web_regular.ttf")
        return titilliumRegular!!
    }

    @JvmStatic fun button(context: Context, label: String, primary: Boolean, action: Runnable): TextView =
        text(context, label, 15, if (primary) INPUT else CYAN, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(context, 50f)
            setPadding(dp(context, 12f), dp(context, 10f), dp(context, 12f), dp(context, 10f))
            background = if (primary) action(context) else outlined(context, RAISED, 14)
            setOnClickListener { action.run() }
        }

    @JvmStatic fun button(context: Context, label: String, icon: Int, primary: Boolean, action: Runnable): TextView =
        button(context, label, primary, action).apply { withIcon(this, icon, if (primary) INPUT else CYAN) }

    @JvmStatic fun dangerButton(context: Context, label: String, icon: Int, action: Runnable): TextView =
        button(context, label, false, action).apply {
            setTextColor(DANGER)
            background = background(context, RAISED, 14).apply { setStroke(dp(context, 1f), DANGER) }
            withIcon(this, icon, DANGER)
        }

    @JvmStatic fun withIcon(view: TextView, resource: Int, color: Int) {
        val drawable = view.context.getDrawable(resource)!!.mutate()
        drawable.setTint(color)
        drawable.setBounds(0, 0, dp(view.context, 20f), dp(view.context, 20f))
        val label = view.text.toString()
        val centered = SpannableString("\uFFFC$label")
        centered.setSpan(InlineIcon(drawable, dp(view.context, 8f)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.setCompoundDrawablesRelative(null, null, null, null)
        view.text = centered
        view.contentDescription = label
    }

    private class InlineIcon(private val icon: Drawable, private val spacing: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            icon.bounds.width() + spacing

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int,
                          x: Float, top: Int, baseline: Int, bottom: Int, paint: Paint) {
            val font = paint.fontMetricsInt
            val center = baseline + (font.ascent + font.descent) / 2
            canvas.save()
            canvas.translate(x, center - icon.bounds.height() / 2f)
            icon.draw(canvas)
            canvas.restore()
        }
    }

    @JvmStatic fun icon(context: Context, resource: Int, color: Int): ImageView =
        ImageView(context).apply {
            setImageResource(resource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(color)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    @JvmStatic fun iconAction(context: Context, resource: Int, action: Runnable): ImageView =
        icon(context, resource, CYAN).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = outlined(context, RAISED, 14)
            setPadding(dp(context, 13f), dp(context, 13f), dp(context, 13f), dp(context, 13f))
            setOnClickListener { action.run() }
        }

    @JvmStatic fun plainIconAction(context: Context, resource: Int, action: Runnable): ImageView =
        plainIconAction(context, resource, CYAN, action)

    @JvmStatic fun plainIconAction(context: Context, resource: Int, color: Int, action: Runnable): ImageView =
        icon(context, resource, color).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(context, 10f), dp(context, 10f), dp(context, 10f), dp(context, 10f))
            setOnClickListener { action.run() }
        }

    @JvmStatic fun label(context: Context, value: String, resource: Int, size: Int, color: Int): LinearLayout =
        row(context).apply {
            addView(icon(context, resource, color), LinearLayout.LayoutParams(dp(context, (size + 2).toFloat()), dp(context, (size + 2).toFloat())))
            val params = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(context, 8f) }
            addView(text(context, value, size, color, true), params)
        }

    @JvmStatic fun gap(parent: LinearLayout, context: Context, height: Int) {
        parent.addView(View(context), LinearLayout.LayoutParams(1, dp(context, height.toFloat())))
    }

    @JvmStatic fun card(context: Context, padding: Int): LinearLayout = column(context).apply {
        val inset = dp(context, padding.toFloat())
        setPadding(inset, inset, inset, inset)
        background = outlined(context, PANEL, 18)
    }
}
