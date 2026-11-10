package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencerTest {
    @Test
    fun straightStepsHaveEqualLength() {
        val s = StepSequencer()
        s.bpm = 120.0
        s.swing = 0.0
        val a = s.stepLengthSamples(44100, 0)
        val b = s.stepLengthSamples(44100, 1)
        assertEquals(a, b)
        // 16th at 120 BPM = 0.125 s = 5512.5 samples
        assertEquals(5512L, a)
    }

    @Test
    fun swingKeepsEighthPairLength() {
        val s = StepSequencer()
        s.bpm = 120.0
        s.swing = 0.4
        val even = s.stepLengthSamples(44100, 0)
        val odd = s.stepLengthSamples(44100, 1)
        assertTrue(odd > even)
        val pair = even + odd
        val straight = s.stepLengthSamples(44100, 0, 120.0, 0.0) * 2
        assertEquals(straight, pair)
    }

    @Test
    fun processEmitsProgrammedPadAtStepBoundary() {
        val s = StepSequencer()
        s.bpm = 120.0
        s.setStep(0, 3, 0, true, 110)
        s.play()
        val first = s.process(64, 44100, 0)
        assertEquals(1, first.size)
        assertEquals(3, first[0].pad)
        assertEquals(110, first[0].velocity)
        assertEquals(0, first[0].step)
        // rest of first step should be silent
        val rest = s.process(1000, 44100, 0)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun recordHitQuantizesToNearestStep() {
        val s = StepSequencer()
        s.bpm = 120.0
        s.recording = true
        s.play()
        // advance almost to the end of step 0
        val len = s.stepLengthSamples(44100, 0)
        s.process((len * 0.8).toInt(), 44100, 0)
        val step = s.recordHit(1, 5, 90, 44100)
        assertEquals(1, step)
        assertTrue(s.on[1][5][1])
        assertEquals(90, s.vel[1][5][1])
        assertFalse(s.on[0][5][1]) // other bank untouched
    }

    @Test
    fun banksAreIndependent() {
        val s = StepSequencer()
        s.setStep(0, 0, 0, true)
        s.setStep(2, 0, 0, true)
        s.clearBank(0)
        assertFalse(s.on[0][0][0])
        assertTrue(s.on[2][0][0])
    }

    @Test
    fun engineTriggersFromSequencer() {
        val eng = DrumEngine(44100)
        // inject a tiny click so trigger has audio
        val click = FloatArray(64) { if (it < 8) 0.5f else 0f }
        eng.repo.kits.add(
            DrumKit("street", "Street", (0..15).map {
                PadSound(KitCatalog.DEFAULT_KEYS[it], KitCatalog.DEFAULT_LABELS[it], "x", click, 44100)
            })
        )
        eng.setBankKit(0, "street")
        eng.sequencer.setStep(0, 0, 0, true, 127)
        eng.sequencer.play()
        val pcm = eng.renderPcm16(256)
        assertTrue(pcm.any { it != 0.toByte() })
    }
}
