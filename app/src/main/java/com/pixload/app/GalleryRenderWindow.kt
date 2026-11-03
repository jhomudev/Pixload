package com.pixload.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** Defers offscreen cards, retaining realized rows so focus and action bindings never need recycling. */
internal class GalleryRenderWindow(
    context: Context,
    private val heights: RowHeights,
    private val initialScrollY: Int,
    private val createRow: (Int) -> View,
    private val rowsReady: () -> Unit
) : LinearLayout(context) {
    class RowHeights(val entries: List<GalleryStore.Entry>) {
        var width = 0
        val pixels = IntArray((entries.size + 1) / 2)
    }

    private val rowCount = heights.pixels.size
    private val slots = ArrayList<FrameLayout>(rowCount)
    private val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val visible = Rect()
    private var firstMeasure = true
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        var changed = false
        for (index in slots.indices) {
            val slot = slots[index]
            if (slot.childCount == 0 && slot.getLocalVisibleRect(visible)) {
                realize(index)
                changed = true
            }
        }
        if (changed) post { rowsReady() }
    }
    private val accessibilityListener = AccessibilityManager.AccessibilityStateChangeListener { requestLayout() }

    init {
        orientation = VERTICAL
        for (index in 0 until rowCount) {
            val slot = FrameLayout(context)
            slots.add(slot)
            addView(slot, LayoutParams(-1, -2))
        }
        if (rowCount > 0) realize(0)
    }

    private fun realize(index: Int) {
        val slot = slots[index]
        if (slot.childCount != 0) return
        slot.layoutParams.height = LayoutParams.WRAP_CONTENT
        slot.addView(createRow(index), FrameLayout.LayoutParams(-1, -2))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (slots.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (heights.width != width) {
            heights.pixels.fill(0)
            heights.width = width
        }
        val rowWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val rowHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        slots[0].measure(rowWidth, rowHeight)
        val estimate = slots[0].measuredHeight
        for (index in slots.indices) {
            if (slots[index].childCount == 0)
                slots[index].layoutParams.height = heights.pixels[index].takeIf { it > 0 } ?: estimate
        }

        var ancestor = parent
        var offset = top
        while (ancestor is View && ancestor !is ScrollView) {
            offset += ancestor.top
            ancestor = ancestor.parent
        }
        val page = ancestor as? ScrollView
        val viewport = page?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val scrollY = if (firstMeasure) initialScrollY else page?.scrollY ?: 0
        firstMeasure = false
        val start = scrollY - offset - viewport
        val end = scrollY - offset + viewport * 2
        // Keep the complete focus tree for assistive navigation and physical keyboards.
        val eager = accessibility.isEnabled || resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
        var rowTop = 0
        for (index in slots.indices) {
            val slot = slots[index]
            val reserved = heights.pixels[index].takeIf { it > 0 } ?: estimate
            if (eager || rowTop <= end && rowTop + reserved >= start) realize(index)
            slot.measure(rowWidth, if (slot.childCount == 0)
                MeasureSpec.makeMeasureSpec(reserved, MeasureSpec.EXACTLY) else rowHeight)
            if (slot.childCount != 0) heights.pixels[index] = slot.measuredHeight
            rowTop += slot.measuredHeight
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        accessibility.addAccessibilityStateChangeListener(accessibilityListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        accessibility.removeAccessibilityStateChangeListener(accessibilityListener)
        super.onDetachedFromWindow()
    }
}
