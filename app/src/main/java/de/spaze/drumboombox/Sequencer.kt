package de.spaze.drumboombox

data class SeqTrigger(val bank: Int, val pad: Int, val velocity: Int, val step: Int)

/**
 * 16-Step-Sequencer, 4 Bänke × 16 Pads.
 * Swing verschiebt ungerade 16tel, das Achtelpaar bleibt in der Summe gleich.
 */
class StepSequencer {
    val on = Array(BANKS) { Array(PADS) { BooleanArray(STEPS) } }
    val vel = Array(BANKS) { Array(PADS) { IntArray(STEPS) { 100 } } }

    @Volatile var bpm: Double = 120.0
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }

    /** 0 = gerade, 0.5 ≈ Triolen-Swing. */
    @Volatile var swing: Double = 0.0
        set(value) { field = value.coerceIn(0.0, 0.6) }

    @Volatile var playing: Boolean = false
        private set
    @Volatile var recording: Boolean = false
    @Volatile var stepIndex: Int = 0
        private set

    private var samplesIntoStep: Long = 0
    private var currentStepLength: Long = 0

    fun play() {
        if (playing) return
        playing = true
        // currentStepLength bleibt 0, bis process() den ersten Step samplegenau startet und auslöst.
    }

    fun stop() {
        playing = false
        stepIndex = 0
        samplesIntoStep = 0
        currentStepLength = 0
    }

    fun toggleRec() {
        recording = !recording
    }

    fun setStep(bank: Int, pad: Int, step: Int, enabled: Boolean, velocity: Int = 100) {
        if (bank !in 0 until BANKS || pad !in 0 until PADS || step !in 0 until STEPS) return
        on[bank][pad][step] = enabled
        if (enabled) vel[bank][pad][step] = velocity.coerceIn(1, 127)
    }

    fun toggleStep(bank: Int, pad: Int, step: Int) {
        if (bank !in 0 until BANKS || pad !in 0 until PADS || step !in 0 until STEPS) return
        on[bank][pad][step] = !on[bank][pad][step]
    }

    fun clearBank(bank: Int) {
        if (bank !in 0 until BANKS) return
        for (p in 0 until PADS) {
            on[bank][p].fill(false)
        }
    }

    fun stepLengthSamples(sampleRate: Int, step: Int, bpmValue: Double = bpm, swingValue: Double = swing): Long {
        val sixteenth = (60.0 / bpmValue / 4.0) * sampleRate
        val odd = step % 2 == 1
        val factor = if (odd) (1.0 + swingValue) else (1.0 - swingValue)
        return (sixteenth * factor).toLong().coerceAtLeast(1L)
    }

    /**
     * Schreitet samplegenau vor. Liefert Triggers genau an den Step-Grenzen.
     */
    fun process(frames: Int, sampleRate: Int, bank: Int): List<SeqTrigger> {
        if (!playing || frames <= 0) return emptyList()
        val out = ArrayList<SeqTrigger>()
        var remaining = frames.toLong()
        if (currentStepLength <= 0) {
            currentStepLength = stepLengthSamples(sampleRate, stepIndex)
            emit(bank, out)
        }
        while (remaining > 0) {
            val left = currentStepLength - samplesIntoStep
            if (left <= 0) {
                advance(sampleRate)
                emit(bank, out)
                continue
            }
            val take = minOf(remaining, left)
            samplesIntoStep += take
            remaining -= take
            if (samplesIntoStep >= currentStepLength) {
                advance(sampleRate)
                emit(bank, out)
            }
        }
        return out
    }

    private fun advance(sampleRate: Int) {
        stepIndex = (stepIndex + 1) % STEPS
        samplesIntoStep = 0
        currentStepLength = stepLengthSamples(sampleRate, stepIndex)
    }

    private fun emit(bank: Int, out: MutableList<SeqTrigger>) {
        val b = bank.coerceIn(0, BANKS - 1)
        val st = stepIndex
        for (p in 0 until PADS) {
            if (on[b][p][st]) {
                out.add(SeqTrigger(b, p, vel[b][p][st], st))
            }
        }
    }

    /** Quantisiert einen Live-Anschlag auf den nächsten/aktuellen Step. */
    fun recordHit(bank: Int, pad: Int, velocity: Int, sampleRate: Int): Int {
        val nearest = nearestStep(sampleRate)
        setStep(bank, pad, nearest, true, velocity)
        return nearest
    }

    fun nearestStep(sampleRate: Int): Int {
        if (!playing || currentStepLength <= 0) return stepIndex
        val mid = currentStepLength / 2
        return if (samplesIntoStep >= mid) (stepIndex + 1) % STEPS else stepIndex
    }

    fun copyFrom(other: StepSequencer) {
        bpm = other.bpm
        swing = other.swing
        for (b in 0 until BANKS) for (p in 0 until PADS) {
            for (s in 0 until STEPS) {
                on[b][p][s] = other.on[b][p][s]
                vel[b][p][s] = other.vel[b][p][s]
            }
        }
    }

    companion object {
        const val BANKS = 4
        const val PADS = 16
        const val STEPS = 16
        const val MIN_BPM = 40.0
        const val MAX_BPM = 240.0
    }
}
