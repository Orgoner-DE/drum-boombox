package de.spaze.drumboombox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceDetectTest {
    @Test
    fun classifiesApcAndStudio() {
        assertEquals(DeviceKind.APC_MINI_MK2, DeviceDetect.classify("APC mini mk2"))
        assertEquals(DeviceKind.APC_MINI_MK1, DeviceDetect.classify("APC MINI"))
        assertEquals(DeviceKind.STUDIO, DeviceDetect.classify("Studio mk2"))
        assertEquals(DeviceKind.GENERIC, DeviceDetect.classify("USB MIDI Interface"))
        assertEquals(DeviceKind.NONE, DeviceDetect.classify(""))
    }

    @Test
    fun statusLabelsHaveNoBrandNames() {
        for (raw in listOf("Akai MPC Studio", "AKAI APC mini mk2", "mpc studio mk2")) {
            val kind = DeviceDetect.classify(raw)
            val label = DeviceDetect.statusLabel(kind, raw)
            val lower = label.lowercase()
            assertFalse(label, lower.contains("akai"))
            assertFalse(label, lower.contains("mpc"))
        }
        assertEquals("APC mini", DeviceDetect.statusLabel(DeviceKind.APC_MINI_MK2, "x"))
        assertEquals("Studio", DeviceDetect.statusLabel(DeviceKind.STUDIO, "x"))
    }
}
