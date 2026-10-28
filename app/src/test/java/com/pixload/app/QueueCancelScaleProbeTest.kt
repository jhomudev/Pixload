package com.pixload.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Structural fallback until the rendered touch target can be checked on-device. */
class QueueCancelScaleProbeTest {
    @Test fun cancelGlyphIsTwentyPercentSmallerWithUsableTarget() {
        val source = File("src/main/java/com/pixload/app/MainActivity.kt").readText()
        val block = source.substringAfter("val action = Ui.icon(this, R.drawable.tabler_x")
            .substringBefore("card.addView(row)")
        val inset = Regex("""action\.setPadding\(Ui\.dp\(this, ([\d.]+)f\)""")
            .find(block)!!.groupValues[1].toFloat()
        val target = Regex("""row\.addView\(action, LinearLayout\.LayoutParams\(Ui\.dp\(this, ([\d.]+)f\)""")
            .find(block)!!.groupValues[1].toFloat()
        assertTrue(target >= 48f)
        assertEquals(32f, target - 2 * inset, 0.01f)
    }
}
