package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookSpecTest {
    @Test
    fun padTextContrastAtLeast45() {
        val c = LookSpec.contrastRatio(LookSpec.PAD_TEXT, LookSpec.PAD_OFF)
        assertTrue("contrast was $c", c >= 4.5)
    }

    private fun labL(c: Int): Double {
        val y = LookSpec.relLuminance(c)
        return if (y > 0.008856) 116.0 * Math.cbrt(y) - 16.0 else 903.3 * y
    }

    @Test
    fun hitRedDiffersClearlyFromPadOff() {
        val d = kotlin.math.abs(labL(LookSpec.PAD_HIT) - labL(LookSpec.PAD_OFF))
        assertTrue("ΔL* was $d", d >= 15.0)
    }

    @Test
    fun velocityMixesBetween40And100Percent() {
        val low = LookSpec.hitColor(1)
        val high = LookSpec.hitColor(127)
        assertEquals(LookSpec.PAD_HIT, high)
        // 40% mix is not full hit and not off
        assertTrue(low != LookSpec.PAD_OFF)
        assertTrue(low != LookSpec.PAD_HIT)
    }

    @Test
    fun landscapePadsMeet72dpAndGap8Percent() {
        val density = 2f
        val layout = LookSpec.measure(
            widthPx = (1280 * density).toInt(),
            heightPx = (720 * density).toInt(),
            density = density,
            fontScale = 1f,
            landscape = true
        )
        assertTrue("pad ${layout.padSizeDp} dp", layout.padSizeDp + 0.05f >= 72f)
        val expectedGap = layout.padSizeDp * 0.08f
        assertTrue("gap ${layout.padGapDp} vs $expectedGap", kotlin.math.abs(layout.padGapDp - expectedGap) < 0.6f)
        assertEquals(16, layout.pads.size)
        assertEquals(16, layout.steps.size)
        assertEquals(4, layout.knobs.size)
        assertEquals(3, layout.transport.size)
        assertEquals(4, layout.banks.size)
        for (r in layout.transport + layout.banks + listOf(layout.noteRepeat, layout.kit)) {
            assertTrue("touch ${r.minSideDp(density)} of $r", r.minSideDp(density) + 0.5f >= 48f)
        }
        assertTrue(layout.grilles.isEmpty()) // 1280 dp width < 1600
    }

    @Test
    fun wideLandscapeShowsGrilles() {
        val density = 1f
        val layout = LookSpec.measure(1800, 800, density, 1f, true)
        assertEquals(2, layout.grilles.size)
    }

    @Test
    fun portraitAnd200PercentFontKeepsTouchTargets() {
        val density = 3f
        val layout = LookSpec.measure(
            widthPx = (412 * density).toInt(),
            heightPx = (915 * density).toInt(),
            density = density,
            fontScale = 2f,
            landscape = false
        )
        assertEquals(16, layout.pads.size)
        for (r in layout.transport + layout.banks + listOf(layout.noteRepeat, layout.kit)) {
            assertTrue("touch ${r.minSideDp(density)}", r.minSideDp(density) + 0.5f >= 48f)
        }
    }
}
