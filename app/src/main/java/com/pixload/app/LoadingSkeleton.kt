package com.pixload.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import kotlin.math.cos

/** A decorative placeholder; animation only schedules another frame while it is being drawn. */
internal class LoadingSkeleton(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visible = Rect()

    init {
        tag = "loading-skeleton"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val animate = ValueAnimator.areAnimatorsEnabled() && isShown &&
            windowVisibility == VISIBLE && getLocalVisibleRect(visible)
        val pulse = if (animate) (1 - cos(SystemClock.uptimeMillis() % 1600 / 1600.0 * 2 * Math.PI)) / 2 else .5
        val radius = minOf(Ui.dp(context, 12f).toFloat(), height / 2f)
        paint.color = Ui.RAISED
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        paint.color = Ui.BORDER
        paint.alpha = (45 + 35 * pulse).toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        if (animate) postInvalidateOnAnimation()
    }
}

internal object Skeletons {
    fun block(context: Context, height: Int, width: Int = -1): View = LoadingSkeleton(context).apply {
        layoutParams = LinearLayout.LayoutParams(if (width < 0) width else Ui.dp(context, width.toFloat()),
            Ui.dp(context, height.toFloat()))
    }

    fun gallery(context: Context): LinearLayout = Ui.column(context).apply {
        contentDescription = AppLanguage.label(context, "Cargando archivos…")
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(block(context, 14, 96))
        Ui.gap(this, context, 12)
        repeat(2) {
            val row = Ui.row(context)
            repeat(2) { column ->
                val card = Ui.card(context, 8)
                card.addView(block(context, 140))
                Ui.gap(card, context, 8)
                card.addView(block(context, 16))
                Ui.gap(card, context, 6)
                card.addView(block(context, 10, 96))
                Ui.gap(card, context, 6)
                card.addView(block(context, 10, 64))
                Ui.gap(card, context, 44)
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (column == 0) rightMargin = Ui.dp(context, 8f)
                })
            }
            addView(row)
            Ui.gap(this, context, 8)
        }
    }

    fun work(context: Context): LinearLayout = Ui.card(context, 16).apply {
        contentDescription = AppLanguage.label(context, "Consultando Pixiv…")
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(block(context, 12, 96))
        Ui.gap(this, context, 10)
        addView(block(context, 24))
        Ui.gap(this, context, 8)
        addView(block(context, 14, 120))
        Ui.gap(this, context, 13)
        addView(block(context, 190))
    }

    fun text(context: Context): LinearLayout = Ui.column(context).apply {
        contentDescription = AppLanguage.label(context, "Abriendo texto…")
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(Ui.dp(context, 12f), Ui.dp(context, 12f), Ui.dp(context, 12f), Ui.dp(context, 12f))
        repeat(6) { line ->
            addView(block(context, 14, if (line == 5) 120 else -1))
            if (line < 5) Ui.gap(this, context, 12)
        }
    }

    fun detail(context: Context): LinearLayout = Ui.card(context, 12).apply {
        contentDescription = AppLanguage.label(context, "Cargando archivos…")
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(block(context, 14, 120))
        Ui.gap(this, context, 16)
        val height = minOf(Ui.dp(context, 470f), context.resources.displayMetrics.heightPixels / 2)
        addView(LoadingSkeleton(context), LinearLayout.LayoutParams(-1, height))
    }
}
