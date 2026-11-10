package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiMappingTest {
    @Test
    fun topLeftQuadrantNotes() {
        assertEquals(56, ApcLayout.noteForPad(0, 0))
        assertEquals(59, ApcLayout.noteForPad(3, 0))
        assertEquals(32, ApcLayout.noteForPad(12, 0))
        assertEquals(35, ApcLayout.noteForPad(15, 0))
        assertEquals(0, ApcLayout.padForNote(56, 0))
        assertEquals(15, ApcLayout.padForNote(35, 0))
        assertNull(ApcLayout.padForNote(0, 0))
    }

    @Test
    fun bottomLeftContainsNoteZero() {
        assertEquals(0, ApcLayout.noteForPad(12, 2))
        assertEquals(12, ApcLayout.padForNote(0, 2))
    }

    @Test
    fun learnOverridesSameSource() {
        val table = MappingTable()
        table.resetDefaults(0)
        val note = ApcLayout.noteForPad(0, 0)
        assertEquals(ActionId.PAD_0, table.resolve(MidiKind.NOTE, 0, note, 0))
        table.learn(MidiKind.NOTE, 0, note, ActionId.PANIC)
        assertEquals(ActionId.PANIC, table.resolve(MidiKind.NOTE, 0, note, 0))
    }

    @Test
    fun fallbackUsesCurrentBankWhenUnmapped() {
        val table = MappingTable()
        table.replaceAll(emptyList())
        assertEquals(ActionId.PAD_12, table.resolve(MidiKind.NOTE, 0, 0, 2))
        assertNull(table.resolve(MidiKind.NOTE, 3, 0, 2))
        assertNull(table.resolve(MidiKind.CC, 0, 1, 0))
    }

    @Test
    fun unbindBlocksFallback() {
        val table = MappingTable()
        table.replaceAll(emptyList())
        table.unbind(MidiKind.NOTE, 0, 0)
        assertNull(table.resolve(MidiKind.NOTE, 0, 0, 2))
        table.learn(MidiKind.NOTE, 0, 0, ActionId.PAD_1)
        assertEquals(ActionId.PAD_1, table.resolve(MidiKind.NOTE, 0, 0, 2))
    }

    @Test
    fun unknownActionSkippedOnImport() {
        val json = """
            {"version":1,"bank":1,"kit":"street","masterGain":0.5,
             "quadrantOrigin":"bottom_left_note0","blocked":[],
             "bindings":[
               {"kind":"NOTE","channel":0,"number":10,"action":"DOES_NOT_EXIST","defaulted":false},
               {"kind":"NOTE","channel":0,"number":11,"action":"PAD_3","defaulted":false}
             ]}
        """.trimIndent()
        val doc = MappingJson.fromJson(json)
        assertEquals(1, doc.bindings.size)
        assertEquals(ActionId.PAD_3, doc.bindings[0].action)
        assertEquals(1, doc.bank)
    }

    @Test
    fun jsonRoundTrip() {
        val table = MappingTable()
        table.resetDefaults(2)
        table.learn(MidiKind.CC, 1, 7, ActionId.MASTER_VOLUME)
        val doc = MappingDocument(bank = 2, kit = "steel", masterGain = 0.4f, bindings = table.all(), blocked = table.blockedSources().toList())
        val again = MappingJson.fromJson(MappingJson.toJson(doc))
        assertEquals(2, again.bank)
        assertEquals("steel", again.kit)
        assertEquals(ActionId.MASTER_VOLUME, again.bindings.last().action)
    }

    @Test
    fun studioDefaultNotesMapPads() {
        val table = MappingTable()
        table.resetDefaults(0)
        assertEquals(ActionId.PAD_0, table.resolve(MidiKind.NOTE, 0, 36, 0))
        assertEquals(ActionId.PAD_15, table.resolve(MidiKind.NOTE, 0, 51, 0))
    }

    @Test
    fun unbindFromUiBlocksPad() {
        val table = MappingTable()
        table.resetDefaults(0)
        val action = ActionId.PAD_0
        MidiLearnActions.releaseFromUi(table, action, 0)
        val note = ApcLayout.noteForPad(0, 0)
        assertNull(table.resolve(MidiKind.NOTE, 0, note, 0))
    }

    @Test
    fun learnPersistsAfterResetOnlyIfSavedSeparately() {
        val table = MappingTable()
        table.resetDefaults(0)
        table.learn(MidiKind.NOTE, 3, 60, ActionId.PAD_4)
        assertEquals(ActionId.PAD_4, table.resolve(MidiKind.NOTE, 3, 60, 0))
        table.resetDefaults(0)
        assertNull(table.resolve(MidiKind.NOTE, 3, 60, 0))
    }
}
