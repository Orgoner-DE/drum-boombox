package de.spaze.drumboombox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class BoomboxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Host {
        fun engine(): DrumEngine
        fun midiLabel(): String
        fun midiConnected(): Boolean
        fun learnMode(): Boolean
        fun onPadDown(pad: Int, velocity: Int)
        fun onPadUp(pad: Int)
        fun onPlay()
        fun onTransportStop()
        fun onRec()
        fun onBank(bank: Int)
        fun onKit()
        fun onLearn()
        fun onResetMap()
        fun onNoteRepeat(down: Boolean)
        fun onKnob(index: Int, value: Float)
        fun onStep(step: Int)
        fun onBpm(delta: Double)
        fun onSwing(delta: Double)
        fun padColor(pad: Int): Int
    }

    var host: Host? = null
    private var layout: LookSpec.Layout? = null
    private val pointerHit = SparseIntArray()
    private var dragKnob = -1
    private var dragStartY = 0f
    private var dragStartVal = 0f
    private val glowUntil = LongArray(16)
    private val glowVel = IntArray(16)

    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(LookSpec.EDGE_DP)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val textLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val rect = RectF()
    private var orbitron: Typeface? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.app_name)
        try {
            orbitron = Typeface.createFromAsset(context.assets, "fonts/orbitron.ttf")
        } catch (_: Exception) {
            orbitron = Typeface.MONOSPACE
        }
        text.typeface = orbitron
        textLeft.typeface = orbitron
    }

    fun flash(pad: Int, velocity: Int) {
        glowUntil[pad] = SystemClock.elapsedRealtime() + LookSpec.GLOW_MS
        glowVel[pad] = velocity
        postInvalidateOnAnimation()
    }

    fun relayout() {
        layout = null
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout = measureLayout(w, h)
    }

    private fun measureLayout(w: Int, h: Int): LookSpec.Layout {
        val landscape = w > h
        return LookSpec.measure(w, h, resources.displayMetrics.density, resources.configuration.fontScale, landscape)
    }

    private fun lay(): LookSpec.Layout {
        val cur = layout
        if (cur != null) return cur
        val next = measureLayout(width.coerceAtLeast(1), height.coerceAtLeast(1))
        layout = next
        return next
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val L = lay()
        val eng = host?.engine()
        val now = SystemClock.elapsedRealtime()
        var needTick = false

        canvas.drawColor(LookSpec.SHELL)
        shellPaint.color = LookSpec.SHELL
        rect.set(L.shell.l, L.shell.t, L.shell.r, L.shell.b)
        canvas.drawRoundRect(rect, dp(LookSpec.RADIUS_DP), dp(LookSpec.RADIUS_DP), shellPaint)

        panelPaint.color = LookSpec.PANEL
        // Speakers
        fill.color = 0xFF1A1B1E.toInt()
        stroke.color = 0xFF4A4D54.toInt()
        for (g in L.grilles) {
            rect.set(g.l, g.t, g.r, g.b)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            var y = g.t + dp(10f)
            while (y < g.b - dp(8f)) {
                canvas.drawLine(g.l + dp(6f), y, g.r - dp(6f), y, stroke)
                y += dp(5f)
            }
        }

        // Display
        fill.color = LookSpec.DISP_BG
        rect.set(L.display.l, L.display.t, L.display.r, L.display.b)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        textLeft.color = LookSpec.DISP_TEXT
        val sp = (LookSpec.TEXT_MIN_SP * resources.displayMetrics.scaledDensity)
        textLeft.textSize = sp
        text.textSize = sp
        val pat = "P${((eng?.bank ?: 0) + 1)}"
        val bpm = "${(eng?.sequencer?.bpm ?: 120.0).toInt()} BPM"
        val swing = "Swing ${((eng?.sequencer?.swing ?: 0.0) * 100).toInt()}%"
        val padLab = eng?.let { it.soundFor(it.bank, it.selectedPad)?.label ?: "Pad ${it.selectedPad + 1}" } ?: "Pad"
        val midi = host?.midiLabel() ?: "MIDI"
        val connected = host?.midiConnected() == true
        val y1 = L.display.t + L.display.h * 0.42f
        val y2 = L.display.t + L.display.h * 0.78f
        textLeft.textAlign = Paint.Align.LEFT
        canvas.drawText("$pat  ·  $padLab", L.display.l + dp(12f), y1, textLeft)
        canvas.drawText(bpm, L.display.l + L.display.w * 0.22f + dp(4f), y1, textLeft)
        canvas.drawText(swing, L.display.l + L.display.w * 0.52f + dp(4f), y1, textLeft)
        text.color = if (connected) LookSpec.MIDI_OK else LookSpec.MIDI_OFF
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(if (connected) "● $midi" else "○ $midi", L.display.l + dp(12f), y2, text)
        if (host?.learnMode() == true) {
            text.color = LookSpec.BANK
            canvas.drawText("LEARN", L.display.cx, y2, text)
        }
        drawBtn(canvas, L.bpmMinus, "–", false)
        drawBtn(canvas, L.bpmPlus, "+", false)
        drawBtn(canvas, L.swingMinus, "–", false)
        drawBtn(canvas, L.swingPlus, "+", false)
        // Learn / Reset tiny labels on the right of the display
        text.textAlign = Paint.Align.RIGHT
        text.color = LookSpec.DISP_TEXT
        text.textSize = sp * 0.85f
        canvas.drawText("Zuweisung  ·  Reset", L.display.r - dp(12f), y2, text)

        // Pads
        text.textAlign = Paint.Align.CENTER
        for (i in 0..15) {
            val r = L.pads[i]
            val glowing = now < glowUntil[i]
            if (glowing) needTick = true
            val col = if (glowing) LookSpec.hitColor(glowVel[i]) else LookSpec.PAD_OFF
            fill.color = col
            rect.set(r.l, r.t, r.r, r.b)
            canvas.drawRect(rect, fill)
            stroke.color = if (eng?.selectedPad == i) LookSpec.BANK else 0xFF2A2C30.toInt()
            canvas.drawRect(rect, stroke)
            text.color = LookSpec.PAD_TEXT
            val ts = min(sp, r.h * 0.22f).coerceAtLeast(sp * 0.85f)
            text.textSize = ts
            val label = eng?.soundFor(eng.bank, i)?.label ?: KitCatalog.DEFAULT_LABELS[i]
            canvas.drawText("${i + 1}", r.cx, r.cy - dp(4f), text)
            canvas.drawText(label, r.cx, r.cy + ts, text)
        }

        // Knobs
        val knobNames = arrayOf("Volume", "Tune", "Filter", "Decay")
        val st = eng?.pads?.getOrNull(eng.bank)?.getOrNull(eng.selectedPad)
        val knobVals = floatArrayOf(
            st?.volume ?: 1f,
            ((st?.tune ?: 0f) + 12f) / 24f,
            st?.filter ?: 1f,
            st?.decay ?: 0.75f
        )
        for (i in 0..3) {
            drawKnob(canvas, L.knobs[i], knobNames[i], knobVals[i].coerceIn(0f, 1f))
        }

        val playing = eng?.sequencer?.playing == true
        val rec = eng?.sequencer?.recording == true
        drawBtn(canvas, L.transport[0], "Play", playing)
        drawBtn(canvas, L.transport[1], "Stop", false)
        drawBtn(canvas, L.transport[2], "Rec", rec, recColor = true)
        val banks = arrayOf("A", "B", "C", "D")
        for (i in 0..3) {
            drawBtn(canvas, L.banks[i], banks[i], eng?.bank == i, accent = true)
        }
        drawBtn(canvas, L.noteRepeat, "Note Repeat", eng?.noteRepeat == true)
        drawBtn(canvas, L.kit, eng?.let { kitLabel(it) } ?: "Kit", false)

        // Steps
        val sel = eng?.selectedPad ?: 0
        val b = eng?.bank ?: 0
        val cur = eng?.sequencer?.stepIndex ?: 0
        val playingNow = eng?.sequencer?.playing == true
        for (s in 0..15) {
            val r = L.steps[s]
            val on = eng?.sequencer?.on?.getOrNull(b)?.getOrNull(sel)?.getOrNull(s) == true
            val current = playingNow && s == cur
            fill.color = when {
                current -> LookSpec.STEP_ON
                on -> LookSpec.BANK
                else -> LookSpec.STEP_OFF
            }
            rect.set(r.l, r.t, r.r, r.b)
            canvas.drawRect(rect, fill)
            text.color = if (current) LookSpec.PANEL else LookSpec.PAD_TEXT
            text.textSize = (sp * 0.8f).coerceAtMost(r.h * 0.6f)
            canvas.drawText("${s + 1}", r.cx, r.cy + text.textSize * 0.35f, text)
        }

        if (needTick || playingNow) postInvalidateOnAnimation()
    }

    private fun kitLabel(eng: DrumEngine): String {
        val id = eng.kitIdFor(eng.bank)
        val i = KitCatalog.KIT_IDS.indexOf(id)
        return if (i >= 0) KitCatalog.KIT_LABELS[i] else "Kit"
    }

    private fun drawKnob(canvas: Canvas, r: LookSpec.Rect, name: String, value: Float) {
        fill.color = LookSpec.PANEL
        rect.set(r.l, r.t, r.r, r.b)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        val cx = r.cx
        val cy = r.t + r.h * 0.42f
        val rad = min(r.w, r.h) * 0.28f
        fill.color = LookSpec.KNOB
        canvas.drawCircle(cx, cy, rad, fill)
        stroke.color = LookSpec.BANK
        canvas.drawCircle(cx, cy, rad, stroke)
        val ang = Math.toRadians((-220.0 + 280.0 * value))
        val ix = (cx + Math.cos(ang) * rad * 0.72).toFloat()
        val iy = (cy + Math.sin(ang) * rad * 0.72).toFloat()
        stroke.strokeWidth = dp(2f)
        canvas.drawLine(cx, cy, ix, iy, stroke)
        stroke.strokeWidth = dp(LookSpec.EDGE_DP)
        text.color = LookSpec.PAD_TEXT
        text.textSize = LookSpec.TEXT_MIN_SP * resources.displayMetrics.scaledDensity * 0.9f
        canvas.drawText(name, cx, r.b - dp(8f), text)
    }

    private fun drawBtn(
        canvas: Canvas,
        r: LookSpec.Rect,
        label: String,
        on: Boolean,
        recColor: Boolean = false,
        accent: Boolean = false
    ) {
        if (r.w <= 1f || r.h <= 1f) return
        fill.color = when {
            on && recColor -> LookSpec.REC
            on && accent -> LookSpec.BANK
            on -> LookSpec.PAD_HIT
            else -> LookSpec.PANEL
        }
        rect.set(r.l, r.t, r.r, r.b)
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
        text.color = if (on) 0xFF1C1D20.toInt() else LookSpec.PAD_TEXT
        text.textSize = (LookSpec.TEXT_MIN_SP * resources.displayMetrics.scaledDensity)
            .coerceAtMost(r.h * 0.42f)
        canvas.drawText(label, r.cx, r.cy + text.textSize * 0.35f, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val L = lay()
        val eng = host?.engine()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pi = event.actionIndex
                val id = event.getPointerId(pi)
                val x = event.getX(pi)
                val y = event.getY(pi)
                val pad = L.pads.indexOfFirst { it.contains(x, y) }
                if (pad >= 0) {
                    pointerHit.put(id, pad)
                    val pressure = event.getPressure(pi).coerceIn(0f, 1f)
                    val vel = if (pressure > 0.05f) (pressure * 127f).toInt().coerceIn(1, 127) else 100
                    flash(pad, vel)
                    host?.onPadDown(pad, vel)
                    return true
                }
                val knob = L.knobs.indexOfFirst { it.contains(x, y) }
                if (knob >= 0) {
                    dragKnob = knob
                    dragStartY = y
                    val st = eng?.pads?.getOrNull(eng.bank)?.getOrNull(eng.selectedPad)
                    dragStartVal = when (knob) {
                        0 -> st?.volume ?: 1f
                        1 -> ((st?.tune ?: 0f) + 12f) / 24f
                        2 -> st?.filter ?: 1f
                        else -> st?.decay ?: 0.75f
                    }
                    pointerHit.put(id, 100 + knob)
                    host?.learnTargetKnob(knob)
                    return true
                }
                hitControls(L, x, y, true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragKnob >= 0) {
                    val dy = dragStartY - event.y
                    val delta = dy / (200f * resources.displayMetrics.density)
                    val v = (dragStartVal + delta).coerceIn(0f, 1f)
                    host?.onKnob(dragKnob, v)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pi = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.actionIndex
                val id = event.getPointerId(pi)
                val hit = pointerHit[id, -1]
                pointerHit.delete(id)
                if (hit in 0..15) host?.onPadUp(hit)
                if (hit in 100..103) dragKnob = -1
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    dragKnob = -1
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (repeatHeld) {
                        host?.onNoteRepeat(false)
                        repeatHeld = false
                    }
                    dragKnob = -1
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitControls(L: LookSpec.Layout, x: Float, y: Float, down: Boolean) {
        if (!down) return
        when {
            L.transport[0].contains(x, y) -> host?.onPlay()
            L.transport[1].contains(x, y) -> host?.onTransportStop()
            L.transport[2].contains(x, y) -> host?.onRec()
            L.banks.indices.any { L.banks[it].contains(x, y) } -> {
                host?.onBank(L.banks.indexOfFirst { it.contains(x, y) })
            }
            L.noteRepeat.contains(x, y) -> { repeatHeld = true; host?.onNoteRepeat(true) }
            L.kit.contains(x, y) -> host?.onKit()
            L.bpmMinus.contains(x, y) -> host?.onBpm(-1.0)
            L.bpmPlus.contains(x, y) -> host?.onBpm(1.0)
            L.swingMinus.contains(x, y) -> host?.onSwing(-0.05)
            L.swingPlus.contains(x, y) -> host?.onSwing(0.05)
            L.steps.indices.any { L.steps[it].contains(x, y) } -> {
                host?.onStep(L.steps.indexOfFirst { it.contains(x, y) })
            }
            L.display.contains(x, y) && x > L.display.cx + L.display.w * 0.25f &&
                y > L.display.cy -> {
                // rechte untere Displayhälfte: Learn bzw. Reset
                if (x > L.display.r - L.display.w * 0.18f) host?.onResetMap() else host?.onLearn()
            }
        }
        invalidate()
    }

    private fun Host.learnTargetKnob(index: Int) {
        // hook used by activity via onKnob selection; no-op default in view
        this.onKnob(index, -1f) // -1 marks "select only" if activity cares; activity ignores <0
    }

    fun glowDeadline(pad: Int): Long = if (pad in glowUntil.indices) glowUntil[pad] else 0L

    private var repeatHeld = false

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
