package de.spaze.drumboombox

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager

class MainActivity : Activity(), BoomboxView.Host {
    private lateinit var app: BoomboxApp
    private lateinit var view: BoomboxView
    private val handler = Handler(Looper.getMainLooper())
    private val leds = LedScheduler(30L)
    private var activityVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        app = application as BoomboxApp
        app.ensureKits()
        app.restore()
        setContentView(R.layout.activity_main)
        view = findViewById(R.id.boombox)
        view.host = this
        view.contentDescription = getString(R.string.app_name) + ". " + getString(R.string.midi_help)
        if (app.midi == null) {
            app.midi = MidiSession(this, ::onMidi, ::onMidiStatus)
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisible = true
        app.playback.start(this)
        app.midi?.start()
        handler.post(ledTick)
    }

    override fun onStop() {
        activityVisible = false
        handler.removeCallbacks(ledTick)
        app.persist()
        if (!app.engine.sequencer.playing) {
            // Audio bleibt nur stehen, wenn nicht gespielt wird
        } else {
            PlaybackService.start(this)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing && !app.engine.sequencer.playing) {
            app.midi?.stop()
            app.playback.stop()
        }
        super.onDestroy()
    }

    override fun engine(): DrumEngine = app.engine
    override fun midiLabel(): String = DeviceDetect.statusLabel(app.midi?.kind ?: DeviceKind.NONE, app.midi?.deviceName ?: "")
    override fun midiConnected(): Boolean = app.midi?.inputOpen == true
    override fun learnMode(): Boolean = app.learnMode

    override fun onPadDown(pad: Int, velocity: Int) {
        if (app.learnMode) {
            app.learnTarget = ActionId.pad(pad)
            return
        }
        app.engine.trigger(pad, velocity)
        if (app.engine.noteRepeat) app.engine.startNoteRepeat(pad, velocity)
        view.flash(pad, velocity)
        view.invalidate()
    }

    override fun onPadUp(pad: Int) {
        app.engine.stopNoteRepeat(pad)
    }

    override fun onPlay() {
        app.engine.sequencer.play()
        app.playback.start(this)
        PlaybackService.start(this)
        view.invalidate()
    }

    override fun onTransportStop() {
        app.engine.sequencer.stop()
        app.engine.panic()
        view.invalidate()
    }

    override fun onRec() {
        if (app.learnMode) {
            app.learnTarget = ActionId.TRANSPORT_REC
            return
        }
        app.engine.sequencer.toggleRec()
        view.invalidate()
    }

    override fun onBank(bank: Int) {
        if (app.learnMode) {
            app.learnTarget = ActionId.bank(bank)
            return
        }
        app.engine.bank = bank
        app.engine.kitId = app.engine.kitIdFor(bank)
        app.table.applyBankDefaults(bank)
        view.invalidate()
    }

    override fun onKit() {
        if (app.learnMode) {
            app.learnTarget = ActionId.KIT_NEXT
            return
        }
        val next = app.engine.repo.nextId(app.engine.kitIdFor(app.engine.bank))
        app.engine.setBankKit(app.engine.bank, next)
        app.engine.applyKit(next, app.engine.bank)
        view.invalidate()
    }

    override fun onLearn() {
        app.learnMode = !app.learnMode
        view.invalidate()
    }

    override fun onResetMap() {
        app.table.resetDefaults(app.engine.bank)
        app.learnMode = false
        app.persist()
        view.invalidate()
    }

    override fun onNoteRepeat(down: Boolean) {
        if (app.learnMode) {
            app.learnTarget = ActionId.NOTE_REPEAT
            return
        }
        app.engine.noteRepeat = down
        if (!down) app.engine.stopNoteRepeat(app.engine.selectedPad)
        view.invalidate()
    }

    override fun onKnob(index: Int, value: Float) {
        val targets = arrayOf(ActionId.PAD_VOLUME, ActionId.PAD_TUNE, ActionId.PAD_FILTER, ActionId.PAD_DECAY)
        if (index in 0..3) app.learnTarget = targets[index]
        if (value < 0f) return
        val st = app.engine.pads[app.engine.bank][app.engine.selectedPad]
        when (index) {
            0 -> st.volume = value.coerceIn(0f, 1.5f)
            1 -> st.tune = value * 24f - 12f
            2 -> st.filter = value
            3 -> st.decay = value
        }
        view.invalidate()
    }

    override fun onStep(step: Int) {
        app.engine.sequencer.toggleStep(app.engine.bank, app.engine.selectedPad, step)
        view.invalidate()
    }

    override fun onBpm(delta: Double) {
        if (app.learnMode) {
            app.learnTarget = ActionId.TEMPO
            return
        }
        app.engine.sequencer.bpm = app.engine.sequencer.bpm + delta
        view.invalidate()
    }

    override fun onSwing(delta: Double) {
        if (app.learnMode) {
            app.learnTarget = ActionId.SWING
            return
        }
        app.engine.sequencer.swing = app.engine.sequencer.swing + delta
        view.invalidate()
    }

    override fun padColor(pad: Int): Int {
        val now = SystemClock.elapsedRealtime()
        return if (now < app.padGlowUntil[pad]) LookSpec.hitColor(app.padGlowVel[pad]) else LookSpec.PAD_OFF
    }

    private fun onMidi(msgs: List<ParsedMessage>) {
        for (msg in msgs) {
            when (msg) {
                is ParsedMessage.AllNotesOff -> app.engine.panic()
                is ParsedMessage.Realtime -> {}
                is ParsedMessage.Voice -> handleVoice(msg.event)
            }
        }
        view.invalidate()
    }

    private fun handleVoice(ev: MidiEvent) {
        val kind = if (ev.isControlChange) MidiKind.CC else MidiKind.NOTE
        if (app.learnMode && (ev.isNoteOn || ev.isControlChange)) {
            val number = ev.data1
            val ch = ev.channel
            app.table.learn(kind, ch, number, app.learnTarget)
            app.learnMode = false
            app.persist()
            return
        }
        if (ev.isNoteOff) {
            val action = app.table.resolve(MidiKind.NOTE, ev.channel, ev.data1, app.engine.bank)
            val pad = action?.padIndex
            if (pad != null) app.engine.stopNoteRepeat(pad)
            return
        }
        if (!ev.isNoteOn && !ev.isControlChange) return
        val action = app.table.resolve(kind, ev.channel, ev.data1, app.engine.bank) ?: return
        when {
            action.padIndex != null && ev.isNoteOn -> {
                val pad = action.padIndex!!
                app.engine.trigger(pad, ev.data2)
                if (app.engine.noteRepeat) app.engine.startNoteRepeat(pad, ev.data2)
                view.flash(pad, ev.data2)
            }
            action == ActionId.TRANSPORT_PLAY && ev.isNoteOn -> onPlay()
            action == ActionId.TRANSPORT_STOP && ev.isNoteOn -> onTransportStop()
            action == ActionId.TRANSPORT_REC && ev.isNoteOn -> onRec()
            action == ActionId.KIT_NEXT && ev.isNoteOn -> onKit()
            action == ActionId.PANIC && ev.isNoteOn -> app.engine.panic()
            action == ActionId.NOTE_REPEAT && ev.isNoteOn -> onNoteRepeat(true)
            action == ActionId.BANK_0 || action == ActionId.BANK_1 ||
                action == ActionId.BANK_2 || action == ActionId.BANK_3 -> {
                if (ev.isNoteOn) onBank(action.name.removePrefix("BANK_").toInt())
            }
            action == ActionId.MASTER_VOLUME -> app.engine.master = MidiParameters.u(ev.data2) * 0.95f
            action == ActionId.TEMPO -> app.engine.sequencer.bpm = 40.0 + MidiParameters.u(ev.data2) * 200.0
            action == ActionId.SWING -> app.engine.sequencer.swing = MidiParameters.u(ev.data2) * 0.6
            action == ActionId.PAD_VOLUME -> onKnob(0, MidiParameters.u(ev.data2))
            action == ActionId.PAD_TUNE -> onKnob(1, MidiParameters.u(ev.data2))
            action == ActionId.PAD_FILTER -> onKnob(2, MidiParameters.u(ev.data2))
            action == ActionId.PAD_DECAY -> onKnob(3, MidiParameters.u(ev.data2))
        }
    }

    private fun onMidiStatus() {
        view.invalidate()
        pushLeds(true)
    }

    private val ledTick = object : Runnable {
        override fun run() {
            pushLeds(false)
            if (activityVisible) handler.postDelayed(this, 40)
        }
    }

    private fun pushLeds(force: Boolean) {
        val midi = app.midi ?: return
        if (!midi.outputOpen || !midi.isApcMini) return
        val bank = app.engine.bank
        val now = SystemClock.elapsedRealtime()
        for (pad in 0..15) {
            val note = ApcLayout.noteForPad(pad, bank)
            val glowing = now < viewGlow(pad)
            val color = if (glowing) LookSpec.hitColor(100) else LookSpec.PAD_OFF
            if (midi.isApcMiniMk2) {
                val vel = if (glowing) ApcMiniMk2Protocol.COLOR_RED else ApcMiniMk2Protocol.colorFromApp(color)
                val beh = if (glowing) ApcMiniMk2Protocol.CH_SOLID_100 else ApcMiniMk2Protocol.CH_SOLID_50
                leds.setPad(note, vel, beh)
            } else {
                leds.enqueue(note, ApcMiniMk1Protocol.pad(note, ApcMiniMk1Protocol.fromApp(color)))
            }
        }
        val selected = app.engine.selectedPad
        val playing = app.engine.sequencer.playing
        val cur = app.engine.sequencer.stepIndex
        for (step in 0..15) {
            val note = ApcLayout.noteForStep(step, bank)
            // skip if overlaps a pad note of this bank
            val overlap = (0..15).any { ApcLayout.noteForPad(it, bank) == note }
            if (overlap) continue
            val on = app.engine.sequencer.on[bank][selected][step]
            if (midi.isApcMiniMk2) {
                val col = when {
                    playing && step == cur -> ApcMiniMk2Protocol.COLOR_WHITE
                    on -> ApcMiniMk2Protocol.COLOR_ORANGE
                    else -> ApcMiniMk2Protocol.COLOR_OFF
                }
                leds.setPad(note, col)
            } else {
                val v = when {
                    playing && step == cur -> ApcMiniMk1Protocol.YELLOW
                    on -> ApcMiniMk1Protocol.GREEN
                    else -> ApcMiniMk1Protocol.OFF
                }
                leds.enqueue(note, ApcMiniMk1Protocol.pad(note, v))
            }
        }
        // Banks as scene buttons
        for (b in 0..3) {
            val v = if (b == bank) ApcMiniMk2Protocol.BUTTON_ON else ApcMiniMk2Protocol.BUTTON_OFF
            leds.setButton(0x70 + b, v)
        }
        val frames = leds.drain(now, force)
        midi.sendAll(frames)
    }

    private fun viewGlow(pad: Int): Long = view.glowDeadline(pad)
}
