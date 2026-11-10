package de.spaze.drumboombox

import kotlin.math.min
import kotlin.math.roundToInt

object LookSpec {
    const val SHELL = 0xFF2B2D31.toInt()
    const val PANEL = 0xFF1C1D20.toInt()
    const val PAD_OFF = 0xFF3A3C41.toInt()
    const val PAD_TEXT = 0xFFC9CCD1.toInt()
    const val PAD_HIT = 0xFFE8402A.toInt()
    const val DISP_BG = 0xFF0E1A12.toInt()
    const val DISP_TEXT = 0xFF7CFFA0.toInt()
    const val BANK = 0xFFF2A23A.toInt()
    const val STEP_ON = 0xFFF7F7F2.toInt()
    const val STEP_OFF = 0xFF2A2C30.toInt()
    const val KNOB = 0xFF4A4D54.toInt()
    const val TEXT_DIM = 0xFF8A8E96.toInt()
    const val MIDI_OK = 0xFF3DDC84.toInt()
    const val MIDI_OFF = 0xFF5A5E66.toInt()
    const val REC = 0xFFE8402A.toInt()

    const val RADIUS_DP = 24f
    const val PAD_MIN_DP = 72f
    const val TOUCH_MIN_DP = 48f
    const val GAP_FRAC = 0.08f
    const val TEXT_MIN_SP = 14f
    const val GLOW_MS = 80L
    const val GRILLE_MIN_DP = 1600f
    const val EDGE_DP = 1f

    fun lerpColor(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * u).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Velocity 1 → 40 % Hit-Rot, Velocity 127 → volles #E8402A. */
    fun hitColor(velocity: Int): Int {
        val t = (velocity.coerceIn(1, 127) - 1) / 126f
        val mix = 0.4f + 0.6f * t
        return lerpColor(PAD_OFF, PAD_HIT, mix)
    }

    fun relLuminance(c: Int): Double {
        fun lin(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = lin((c shr 16) and 0xFF)
        val g = lin((c shr 8) and 0xFF)
        val b = lin(c and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(a: Int, b: Int): Double {
        val l1 = relLuminance(a)
        val l2 = relLuminance(b)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        val w: Float get() = r - l
        val h: Float get() = b - t
        val cx: Float get() = (l + r) / 2f
        val cy: Float get() = (t + b) / 2f
        fun contains(x: Float, y: Float) = x >= l && x <= r && y >= t && y <= b
        fun minSideDp(density: Float) = min(w, h) / density
    }

    data class Layout(
        val shell: Rect,
        val display: Rect,
        val padArea: Rect,
        val pads: List<Rect>,
        val padSizeDp: Float,
        val padGapDp: Float,
        val knobs: List<Rect>,
        val transport: List<Rect>,
        val banks: List<Rect>,
        val noteRepeat: Rect,
        val kit: Rect,
        val midi: Rect,
        val learn: Rect,
        val reset: Rect,
        val steps: List<Rect>,
        val grilles: List<Rect>,
        val bpmMinus: Rect,
        val bpmPlus: Rect,
        val swingMinus: Rect,
        val swingPlus: Rect
    )

    fun measure(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        fontScale: Float,
        landscape: Boolean
    ): Layout {
        val touch = TOUCH_MIN_DP * density * 1f
        val inset = 8f * density
        val radius = RADIUS_DP * density
        val shell = Rect(inset, inset, widthPx - inset, heightPx - inset)
        val inner = 10f * density
        var left = shell.l + inner
        var top = shell.t + inner
        var right = shell.r - inner
        var bottom = shell.b - inner

        val showGrille = landscape && (widthPx / density) >= GRILLE_MIN_DP
        val grilles = ArrayList<Rect>()
        if (showGrille) {
            val gw = min(72f * density, (right - left) * 0.08f)
            grilles.add(Rect(left, top + 24f * density, left + gw, bottom - 24f * density))
            grilles.add(Rect(right - gw, top + 24f * density, right, bottom - 24f * density))
            left += gw + 8f * density
            right -= gw + 8f * density
        }

        val dispH = if (landscape) {
            ((bottom - top) * 0.15f).coerceAtLeast(52f * density * fontScale.coerceAtMost(1.6f))
        } else {
            (64f * density * fontScale.coerceAtMost(1.8f)).coerceAtLeast(52f * density)
        }
        val stepH = (36f * density).coerceAtLeast(touch * 0.75f)
        val display = Rect(left, top, right, top + dispH)
        val stepsTop = bottom - stepH - 6f * density
        val stepGap = 4f * density
        val stepW = ((right - left) - stepGap * 17) / 16f
        val steps = (0..15).map { i ->
            val x = left + stepGap + i * (stepW + stepGap)
            Rect(x, stepsTop, x + stepW, bottom - 4f * density)
        }

        val workTop = display.b + 8f * density
        val workBot = stepsTop - 8f * density

        val pads: List<Rect>
        val padArea: Rect
        val knobs: List<Rect>
        val transport: List<Rect>
        val banks: List<Rect>
        val noteRepeat: Rect
        val kit: Rect
        val midi: Rect
        val learn: Rect
        val reset: Rect
        val padSizeDp: Float
        val padGapDp: Float

        if (landscape) {
            val split = left + (right - left) * 0.60f
            padArea = Rect(left, workTop, split - 6f * density, workBot)
            val side = Rect(split + 6f * density, workTop, right, workBot)
            val (p, size, gap) = packPads(padArea, density)
            pads = p
            padSizeDp = size
            padGapDp = gap
            val col = packControls(side, density, touch)
            knobs = col.knobs
            transport = col.transport
            banks = col.banks
            noteRepeat = col.noteRepeat
            kit = col.kit
            midi = col.midi
            learn = col.learn
            reset = col.reset
        } else {
            val ctrlH = (touch * 4.2f + 8f * density * 5f).coerceAtMost((workBot - workTop) * 0.48f)
                .coerceAtLeast(touch * 3.2f)
            padArea = Rect(left, workTop, right, workBot - ctrlH - 8f * density)
            val (p, size, gap) = packPads(padArea, density)
            pads = p
            padSizeDp = size
            padGapDp = gap
            val side = Rect(left, workBot - ctrlH, right, workBot)
            val col = packControls(side, density, touch)
            knobs = col.knobs
            transport = col.transport
            banks = col.banks
            noteRepeat = col.noteRepeat
            kit = col.kit
            midi = col.midi
            learn = col.learn
            reset = col.reset
        }

        val dw = display.w
        val btnW = touch
        val bpmMinus = Rect(display.l + dw * 0.22f, display.t + 6f * density, display.l + dw * 0.22f + btnW, display.b - 6f * density)
        val bpmPlus = Rect(bpmMinus.r + 4f * density, bpmMinus.t, bpmMinus.r + 4f * density + btnW, bpmMinus.b)
        val swingMinus = Rect(display.l + dw * 0.52f, bpmMinus.t, display.l + dw * 0.52f + btnW, bpmMinus.b)
        val swingPlus = Rect(swingMinus.r + 4f * density, bpmMinus.t, swingMinus.r + 4f * density + btnW, bpmMinus.b)

        return Layout(
            shell, display, padArea, pads, padSizeDp, padGapDp, knobs, transport, banks,
            noteRepeat, kit, midi, learn, reset, steps, grilles, bpmMinus, bpmPlus, swingMinus, swingPlus
        )
    }

    private data class Controls(
        val knobs: List<Rect>,
        val transport: List<Rect>,
        val banks: List<Rect>,
        val noteRepeat: Rect,
        val kit: Rect,
        val midi: Rect,
        val learn: Rect,
        val reset: Rect
    )

    private fun packPads(area: Rect, density: Float): Triple<List<Rect>, Float, Float> {
        val minPad = PAD_MIN_DP * density
        val maxCell = min(area.w, area.h)
        // gap = 8 % of pad width; 4 pads + 5 gaps = 4p + 5*0.08p = 4.4p
        var pad = (maxCell / 4.4f)
        if (pad < minPad) pad = minPad
        val gap = pad * GAP_FRAC
        val total = 4 * pad + 5 * gap
        val scale = min(1f, min(area.w / total, area.h / total))
        pad *= scale
        val g = pad * GAP_FRAC
        val gridW = 4 * pad + 5 * g
        val gridH = 4 * pad + 5 * g
        val ox = area.l + (area.w - gridW) / 2f
        val oy = area.t + (area.h - gridH) / 2f
        val pads = (0..15).map { i ->
            val row = i / 4
            val col = i % 4
            val l = ox + g + col * (pad + g)
            val t = oy + g + row * (pad + g)
            Rect(l, t, l + pad, t + pad)
        }
        return Triple(pads, pad / density, g / density)
    }

    private fun packControls(area: Rect, density: Float, touch: Float): Controls {
        val gap = 6f * density
        val knobsTop = area.t
        // 4 Zeilen (Regler + Transport + Bänke + Repeat/Kit), alle >= 48 dp wenn der Bereich reicht.
        val rows = 4
        val minBlock = touch * rows + gap * (rows - 1)
        val scale = if (area.h < minBlock && area.h > 0f) area.h / minBlock else 1f
        val row = (touch * scale).coerceAtLeast(min(touch, area.h / rows))
        val g = (gap * scale).coerceAtLeast(2f * density)
        val knobsH = row
        val knobW = ((area.w - g * 5) / 4f).coerceAtLeast(row)
        val knobs = (0..3).map { i ->
            val l = area.l + g + i * (knobW + g)
            Rect(l, knobsTop, (l + knobW).coerceAtMost(area.r), knobsTop + knobsH)
        }
        var y = knobsTop + knobsH + g
        val transW = ((area.w - g * 4) / 3f).coerceAtLeast(row)
        val transport = (0..2).map { i ->
            val l = area.l + g + i * (transW + g)
            Rect(l, y, (l + transW).coerceAtMost(area.r), y + row)
        }
        y += row + g
        val bankW = ((area.w - g * 5) / 4f).coerceAtLeast(row * 0.9f)
        val banks = (0..3).map { i ->
            val l = area.l + g + i * (bankW + g)
            Rect(l, y, (l + bankW).coerceAtMost(area.r), y + row)
        }
        y += row + g
        val half = (area.w - g * 3) / 2f
        val bottom = (y + row).coerceAtMost(area.b)
        val noteRepeat = Rect(area.l + g, y.coerceAtMost(area.b - row), area.l + g + half, bottom)
        val kit = Rect(noteRepeat.r + g, noteRepeat.t, area.r - g, noteRepeat.b)
        val midi = Rect(area.l, area.t, area.l + 1f, area.t + 1f)
        val learn = Rect(area.l, area.t, area.l + 1f, area.t + 1f)
        val reset = Rect(area.l, area.t, area.l + 1f, area.t + 1f)
        return Controls(knobs, transport, banks, noteRepeat, kit, midi, learn, reset)
    }
}
