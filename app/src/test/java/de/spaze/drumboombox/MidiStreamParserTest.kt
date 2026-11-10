package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiStreamParserTest {
    private val p = MidiStreamParser()

    private fun bytes(vararg v: Int) = v.map { it.toByte() }.toByteArray()

    @Test
    fun noteOnAndOff() {
        val msgs = p.push(bytes(0x90, 60, 100, 0x80, 60, 40))
        val voice = msgs.filterIsInstance<ParsedMessage.Voice>().map { it.event }
        assertTrue(voice[0].isNoteOn)
        assertEquals(60, voice[0].data1)
        assertEquals(100, voice[0].data2)
        assertTrue(voice[1].isNoteOff)
    }

    @Test
    fun noteOnVelocityZeroIsNoteOff() {
        val msgs = p.push(bytes(0x99, 12, 0))
        val ev = (msgs.single() as ParsedMessage.Voice).event
        assertTrue(ev.isNoteOff)
        assertFalse(ev.isNoteOn)
        assertEquals(0x89, ev.status)
        assertEquals(9, ev.channel)
    }

    @Test
    fun runningStatus() {
        val msgs = p.push(bytes(0x90, 10, 20, 11, 21, 12, 22))
        val notes = msgs.filterIsInstance<ParsedMessage.Voice>().map { it.event.data1 }
        assertEquals(listOf(10, 11, 12), notes)
    }

    @Test
    fun realtimeInterleaveKeepsRunningStatus() {
        val msgs = p.push(bytes(0x90, 5, 40, 0xF8, 6, 41))
        assertTrue(msgs[1] is ParsedMessage.Realtime)
        val notes = msgs.filterIsInstance<ParsedMessage.Voice>().map { it.event.data1 }
        assertEquals(listOf(5, 6), notes)
        assertEquals(0xF8, (msgs[1] as ParsedMessage.Realtime).status)
    }

    @Test
    fun fragmentedAcrossPackets() {
        val first = p.push(bytes(0xB0, 7))
        assertTrue(first.isEmpty())
        val second = p.push(bytes(64))
        val ev = (second.single() as ParsedMessage.Voice).event
        assertTrue(ev.isControlChange)
        assertEquals(0, ev.channel)
        assertEquals(7, ev.data1)
        assertEquals(64, ev.data2)
    }

    @Test
    fun controlChangeChannelAndAllNotesOff() {
        val msgs = p.push(bytes(0xB2, 123, 0))
        assertTrue(msgs.any { it is ParsedMessage.AllNotesOff && it.channel == 2 })
        val ev = msgs.filterIsInstance<ParsedMessage.Voice>().single().event
        assertEquals(2, ev.channel)
        assertTrue(ev.isAllNotesOff)
    }

    @Test
    fun cc120AlsoAllNotesOff() {
        val msgs = p.push(bytes(0xB0, 120, 0))
        assertTrue(msgs.any { it is ParsedMessage.AllNotesOff })
    }
}
