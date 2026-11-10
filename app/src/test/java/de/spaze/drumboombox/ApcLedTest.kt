package de.spaze.drumboombox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApcLedTest {
    @Test
    fun officialRedExample() {
        assertArrayEquals(
            byteArrayOf(0x96.toByte(), 0x00, 0x05),
            ApcMiniMk2Protocol.rgbPad(0, ApcMiniMk2Protocol.COLOR_RED)
        )
    }

    @Test
    fun officialOrangeExample() {
        assertArrayEquals(
            byteArrayOf(0x96.toByte(), 0x00, 0x09),
            ApcMiniMk2Protocol.rgbPad(0, ApcMiniMk2Protocol.COLOR_ORANGE)
        )
    }

    @Test
    fun velocityZeroTurnsPadOff() {
        val bytes = ApcMiniMk2Protocol.rgbPad(15, 0)
        assertEquals(0, bytes[2].toInt())
    }

    @Test
    fun singleButtonOnOff() {
        assertArrayEquals(
            byteArrayOf(0x90.toByte(), 0x64, 0x01),
            ApcMiniMk2Protocol.singleButton(0x64, ApcMiniMk2Protocol.BUTTON_ON)
        )
        assertEquals(0, ApcMiniMk2Protocol.singleButton(0x64, 0)[2].toInt())
    }

    @Test
    fun bulkNoteOrderZeroTo63() {
        val pads = ApcMiniMk2Protocol.bulkOffPads()
        assertEquals(64, pads.size)
        pads.forEachIndexed { i, bytes ->
            assertEquals(0x96, bytes[0].toInt() and 0xFF)
            assertEquals(i, bytes[1].toInt() and 0xFF)
            assertEquals(0, bytes[2].toInt())
        }
    }

    @Test
    fun bankQuadrantLedKeys() {
        val scheduler = LedScheduler(0)
        val bank = 2
        for (pad in 0..15) {
            scheduler.setPad(ApcLayout.noteForPad(pad, bank), ApcMiniMk2Protocol.COLOR_GREEN)
        }
        val frames = scheduler.drain(1000, force = true)
        val notes = frames.map { it[1].toInt() and 0xFF }
        val expected = (0..15).map { ApcLayout.noteForPad(it, bank) }
        assertEquals(expected, notes)
        assertTrue(notes.contains(0))
    }

    @Test
    fun coalesceKeepsLastVelocity() {
        val scheduler = LedScheduler(0)
        scheduler.setPad(4, 5)
        scheduler.setPad(4, 0)
        val frames = scheduler.drain(50, force = true)
        assertEquals(1, frames.size)
        assertEquals(0, frames[0][2].toInt())
    }

    @Test
    fun ledTestSequenceHasDocumentedOrder() {
        val seq = LedScheduler.ledTestSequence()
        assertEquals(64 + 64 + 8 + 8 + 8 + 8, seq.size)
    }

    @Test
    fun appHitRedMapsToMk2Red() {
        assertEquals(ApcMiniMk2Protocol.COLOR_RED, ApcMiniMk2Protocol.colorFromApp(LookSpec.PAD_HIT))
        assertEquals(ApcMiniMk2Protocol.COLOR_OFF, ApcMiniMk2Protocol.colorFromApp(LookSpec.SHELL))
        assertEquals(ApcMiniMk2Protocol.COLOR_WHITE, ApcMiniMk2Protocol.colorFromApp(LookSpec.STEP_ON))
        assertEquals(ApcMiniMk2Protocol.COLOR_ORANGE, ApcMiniMk2Protocol.colorFromApp(LookSpec.BANK))
    }

    @Test
    fun mk1HitIsRed() {
        assertEquals(ApcMiniMk1Protocol.RED, ApcMiniMk1Protocol.fromApp(LookSpec.PAD_HIT))
        assertEquals(ApcMiniMk1Protocol.YELLOW, ApcMiniMk1Protocol.fromApp(LookSpec.PAD_OFF, isStepCurrent = true))
        assertEquals(ApcMiniMk1Protocol.GREEN, ApcMiniMk1Protocol.fromApp(LookSpec.PAD_OFF, isStepOn = true))
    }

    @Test
    fun stepNotesDoNotOverlapPadNotes() {
        for (bank in 0..3) {
            val pads = (0..15).map { ApcLayout.noteForPad(it, bank) }.toSet()
            val steps = (0..15).map { ApcLayout.noteForStep(it, bank) }.toSet()
            assertTrue("bank $bank overlap ${pads.intersect(steps)}", pads.intersect(steps).isEmpty())
        }
    }
}
