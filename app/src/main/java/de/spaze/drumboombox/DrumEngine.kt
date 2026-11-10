package de.spaze.drumboombox

import kotlin.math.pow

/**
 * Sample-Mixer mit Anschlagdynamik, Tune, Filter, Decay und Choke-Gruppen.
 * Läuft auf dem Audio-Thread; Trigger dürfen von überall kommen (synchronized voices).
 */
class DrumEngine(val sampleRate: Int = 44100) {
    val sequencer = StepSequencer()
    val repo = KitRepository()
    val pads: Array<Array<PadSettings>> = Array(4) { b ->
        Array(16) { p ->
            PadSettings(
                chokeGroup = KitCatalog.chokeGroupForPad(p),
                sampleKey = KitCatalog.DEFAULT_KEYS[p]
            )
        }
    }
    var bank: Int = 0
        set(value) { field = value.coerceIn(0, 3) }
    var kitId: String = "street"
    var selectedPad: Int = 0
        set(value) { field = value.coerceIn(0, 15) }

    @Volatile var master: Float = 0.70f
        set(value) { field = value.coerceIn(0f, 0.95f) }

    @Volatile var noteRepeat: Boolean = false
    private var noteRepeatHeldPad: Int = -1
    private var noteRepeatVel: Int = 100
    private var samplesSinceRepeat: Long = 0

    private val voices = Array(MAX_VOICES) { Voice() }
    private val lock = Any()

    @Volatile var lastTriggerNanos: Long = 0L
        private set

    fun applyKit(id: String, toBank: Int = bank) {
        val kit = repo.kit(id) ?: return
        kitId = kit.id
        val b = toBank.coerceIn(0, 3)
        bankKit[b] = kit.id
        for (p in 0..15) {
            pads[b][p].sampleKey = kit.pads.getOrNull(p)?.key ?: KitCatalog.DEFAULT_KEYS[p]
            pads[b][p].chokeGroup = KitCatalog.chokeGroupForPad(p)
        }
    }

    fun soundFor(bank: Int, pad: Int): PadSound? {
        val kit = repo.kit(kitIdFor(bank)) ?: repo.kit(kitId) ?: return null
        return kit.pads.getOrNull(pad)
    }

    private val bankKit = Array(4) { "street" }

    fun kitIdFor(bank: Int) = bankKit[bank.coerceIn(0, 3)]

    fun setBankKit(bank: Int, id: String) {
        bankKit[bank.coerceIn(0, 3)] = id
        if (bank == this.bank) kitId = id
    }

    fun trigger(pad: Int, velocity: Int, fromSeq: Boolean = false, atBank: Int = bank) {
        val p = pad.coerceIn(0, 15)
        val b = atBank.coerceIn(0, 3)
        val sound = run {
            val kit = repo.kit(kitIdFor(b)) ?: return
            kit.pads.getOrNull(p) ?: return
        }
        if (sound.samples.isEmpty()) return
        val st = pads[b][p]
        val vel = velocity.coerceIn(1, 127) / 127f
        val gain = (master * st.volume.coerceIn(0f, 1.5f) * vel).coerceIn(0f, 0.95f)
        val ratio = 2.0.pow((st.tune.coerceIn(-12f, 12f) / 12.0))
        val srcRate = sound.sampleRate.coerceAtLeast(8000)
        val increment = ratio * (srcRate.toDouble() / sampleRate)
        val decaySec = 0.04 + st.decay.coerceIn(0f, 1f) * 1.6
        val envDecay = (1.0 / (decaySec * sampleRate)).toFloat()
        val cutoff = 400f + st.filter.coerceIn(0f, 1f) * 12000f
        val lpCoef = (1f - (cutoff / (cutoff + sampleRate))).coerceIn(0.02f, 0.98f).let { 1f - it }
        synchronized(lock) {
            if (st.chokeGroup != 0) {
                for (v in voices) {
                    if (v.active && v.chokeGroup == st.chokeGroup) v.active = false
                }
            }
            val v = voices.minByOrNull { if (it.active) it.age else -1 } ?: return
            v.active = true
            v.sample = sound.samples
            v.pos = 0.0
            v.increment = increment
            v.gain = gain
            v.lp = 0f
            v.lpCoef = lpCoef
            v.env = 1f
            v.envDecay = envDecay
            v.chokeGroup = st.chokeGroup
            v.age = 0
            v.pad = p
        }
        lastTriggerNanos = System.nanoTime()
        if (!fromSeq) selectedPad = p
        if (sequencer.recording && !fromSeq) {
            sequencer.recordHit(b, p, velocity, sampleRate)
        }
    }

    fun startNoteRepeat(pad: Int, velocity: Int) {
        noteRepeatHeldPad = pad
        noteRepeatVel = velocity
        samplesSinceRepeat = 0
        if (noteRepeat) trigger(pad, velocity)
    }

    fun stopNoteRepeat(pad: Int) {
        if (noteRepeatHeldPad == pad) noteRepeatHeldPad = -1
    }

    fun panic() {
        synchronized(lock) {
            for (v in voices) v.active = false
        }
        noteRepeatHeldPad = -1
    }

    fun renderPcm16(frames: Int): ByteArray {
        if (sequencer.playing) {
            val hits = sequencer.process(frames, sampleRate, bank)
            for (h in hits) trigger(h.pad, h.velocity, fromSeq = true, atBank = h.bank)
        }
        if (noteRepeat && noteRepeatHeldPad >= 0) {
            val step = sequencer.stepLengthSamples(sampleRate, 0, sequencer.bpm, 0.0)
            var remain = frames.toLong()
            while (remain > 0) {
                val left = step - samplesSinceRepeat
                if (left <= 0) {
                    trigger(noteRepeatHeldPad, noteRepeatVel)
                    samplesSinceRepeat = 0
                    continue
                }
                val take = minOf(remain, left)
                samplesSinceRepeat += take
                remain -= take
                if (samplesSinceRepeat >= step) {
                    trigger(noteRepeatHeldPad, noteRepeatVel)
                    samplesSinceRepeat = 0
                }
            }
        }
        val out = ByteArray(frames * 4)
        val accL = FloatArray(frames)
        val accR = FloatArray(frames)
        synchronized(lock) {
            for (v in voices) {
                if (!v.active) continue
                var pos = v.pos
                var env = v.env
                var lp = v.lp
                val sample = v.sample
                val last = sample.size - 1
                if (last < 1) {
                    v.active = false
                    continue
                }
                for (i in 0 until frames) {
                    val idx = pos.toInt()
                    if (idx >= last || env <= 0.001f) {
                        v.active = false
                        break
                    }
                    val frac = (pos - idx).toFloat()
                    val s = (sample[idx] * (1f - frac) + sample[idx + 1] * frac) * env * v.gain
                    lp += v.lpCoef * (s - lp)
                    accL[i] += lp
                    accR[i] += lp
                    pos += v.increment
                    env -= v.envDecay
                    v.age++
                }
                v.pos = pos
                v.env = env
                v.lp = lp
            }
        }
        var o = 0
        for (i in 0 until frames) {
            val l = (accL[i].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32767, 32767)
            val r = (accR[i].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32767, 32767)
            out[o++] = (l and 0xFF).toByte()
            out[o++] = (l shr 8).toByte()
            out[o++] = (r and 0xFF).toByte()
            out[o++] = (r shr 8).toByte()
        }
        return out
    }

    private class Voice {
        var active: Boolean = false
        var sample: FloatArray = FloatArray(0)
        var pos: Double = 0.0
        var increment: Double = 1.0
        var gain: Float = 1f
        var lp: Float = 0f
        var lpCoef: Float = 0.5f
        var env: Float = 1f
        var envDecay: Float = 0.001f
        var chokeGroup: Int = 0
        var age: Int = 0
        var pad: Int = 0
    }

    companion object {
        const val MAX_VOICES = 24
    }
}
