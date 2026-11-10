package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStoreTest {
    @Test
    fun patternAndPadSettingsSurviveRoundTrip() {
        val eng = DrumEngine()
        eng.bank = 2
        eng.sequencer.bpm = 136.0
        eng.sequencer.swing = 0.25
        eng.sequencer.setStep(2, 4, 7, true, 111)
        eng.pads[2][4].volume = 0.6f
        eng.pads[2][4].tune = -3f
        eng.pads[2][4].filter = 0.4f
        eng.pads[2][4].decay = 0.2f
        val table = MappingTable()
        table.resetDefaults(2)
        table.learn(MidiKind.NOTE, 5, 42, ActionId.PAD_8)
        val json = ProjectJson.toJson(eng, MappingDocument(bank = 2, kit = "room", bindings = table.all()))
        val eng2 = DrumEngine()
        val table2 = MappingTable()
        ProjectJson.apply(json, eng2, table2)
        assertEquals(2, eng2.bank)
        assertEquals(136.0, eng2.sequencer.bpm, 0.01)
        assertEquals(0.25, eng2.sequencer.swing, 0.01)
        assertTrue(eng2.sequencer.on[2][4][7])
        assertEquals(111, eng2.sequencer.vel[2][4][7])
        assertEquals(0.6f, eng2.pads[2][4].volume, 0.01f)
        assertEquals(-3f, eng2.pads[2][4].tune, 0.01f)
        assertEquals(ActionId.PAD_8, table2.resolve(MidiKind.NOTE, 5, 42, 2))
    }
}
