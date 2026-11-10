package de.spaze.drumboombox

/**
 * LED and control bytes from the official Akai Professional
 * "APC mini mk2 Communications Protocol (v1.0)":
 * https://cdn.inmusicbrands.com/akai/attachments/APC%20mini%20mk2%20-%20Communication%20Protocol%20-%20v1.0.pdf
 *
 * RGB pads: Note On on Port 0. Byte1 = behavior/channel, Byte2 = pad 0x00–0x3F,
 * Byte3 = fixed 128-color velocity palette.
 * Official table (0-based MIDI channel): channel 6 / byte 0x96 = solid 100% brightness.
 * Example: 96 00 05 = pad 1 (note 0) solid red; 96 00 09 = orange.
 *
 * Single-color UI buttons: always 0x90, notes 0x64–0x6B (track) and 0x70–0x77 (scene).
 * Velocity 0x00 off, 0x01 and 0x03–0x7F on, 0x02 blink.
 *
 * Faders: CC 0x30–0x37 channel faders 1–8, CC 0x38 master fader 9, channel 0, port 0.
 * Shift inbound note 0x7A, no LED.
 *
 * This app does not send undocumented SysEx color lighting for pad UI (palette only).
 */
object ApcMiniMk2Protocol {
    const val SOURCE_PDF =
        "https://cdn.inmusicbrands.com/akai/attachments/APC%20mini%20mk2%20-%20Communication%20Protocol%20-%20v1.0.pdf"

    const val CH_SOLID_10 = 0x90
    const val CH_SOLID_25 = 0x91
    const val CH_SOLID_50 = 0x92
    const val CH_SOLID_65 = 0x93
    const val CH_SOLID_75 = 0x94
    const val CH_SOLID_90 = 0x95
    const val CH_SOLID_100 = 0x96
    const val CH_PULSE_16 = 0x97

    const val COLOR_OFF = 0
    const val COLOR_DARK = 1
    const val COLOR_WHITE = 3
    const val COLOR_RED = 5
    const val COLOR_ORANGE = 9
    const val COLOR_YELLOW = 13
    const val COLOR_GREEN = 21
    const val COLOR_CYAN = 37
    const val COLOR_BLUE = 45
    const val COLOR_MAGENTA = 53

    const val TRACK_1 = 0x64
    const val SCENE_1 = 0x70
    const val SHIFT = 0x7A
    const val CC_FADER_1 = 0x30
    const val CC_MASTER = 0x38

    const val BUTTON_OFF = 0x00
    const val BUTTON_ON = 0x01
    const val BUTTON_BLINK = 0x02

    fun rgbPad(note: Int, colorVelocity: Int, behavior: Int = CH_SOLID_100): ByteArray {
        val n = note.coerceIn(0, 63)
        val v = colorVelocity.coerceIn(0, 127)
        return byteArrayOf(behavior.toByte(), n.toByte(), v.toByte())
    }

    fun singleButton(note: Int, velocity: Int): ByteArray {
        return byteArrayOf(CH_SOLID_10.toByte(), note.toByte(), velocity.coerceIn(0, 127).toByte())
    }

    fun introductionSysEx(major: Int = 0, minor: Int = 1, fix: Int = 0): ByteArray {
        return byteArrayOf(
            0xF0.toByte(), 0x47, 0x7F, 0x4F, 0x60, 0x00, 0x04, 0x00,
            major.toByte(), minor.toByte(), fix.toByte(), 0xF7.toByte()
        )
    }

    fun bulkOffPads(): List<ByteArray> = (0..63).map { rgbPad(it, COLOR_OFF) }

    fun bulkOffButtons(): List<ByteArray> {
        val notes = (0x64..0x6B) + (0x70..0x77)
        return notes.map { singleButton(it, BUTTON_OFF) }
    }

    fun colorForKit(kitId: String): Int = when (kitId) {
        "street" -> COLOR_ORANGE
        "steel" -> COLOR_CYAN
        "room" -> COLOR_YELLOW
        else -> COLOR_GREEN
    }

    /** Mappt App-Padfarbe auf die nächstgelegene mk2-Palette (Dokumentfarben). */
    fun colorFromApp(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        if (r < 70 && g < 70 && b < 70) return COLOR_OFF
        if (r < 90 && g < 90 && b < 90) return COLOR_DARK
        if (r > 200 && g > 200 && b > 200) return COLOR_WHITE
        // Bank-Orange #F2A23A vor Hit-Rot, sonst würde Orange als Rot enden.
        if (r > 180 && g > 120 && b < 90) return COLOR_ORANGE
        if (r > 180 && r > g + 40 && r > b + 40) return COLOR_RED
        if (g > r + 20 && g > b) return COLOR_GREEN
        return COLOR_DARK
    }

    fun brightnessForVelocity(velocity: Int): Int {
        val v = velocity.coerceIn(1, 127)
        return when {
            v >= 110 -> CH_SOLID_100
            v >= 80 -> CH_SOLID_90
            v >= 50 -> CH_SOLID_75
            else -> CH_SOLID_50
        }
    }
}

/** Klassisches APC-mini (mk1): Note-On, Velocity 0 aus, 1 grün, 3 rot, 5 gelb (inkl. Blink +1). */
object ApcMiniMk1Protocol {
    const val OFF = 0
    const val GREEN = 1
    const val GREEN_BLINK = 2
    const val RED = 3
    const val RED_BLINK = 4
    const val YELLOW = 5
    const val YELLOW_BLINK = 6

    fun pad(note: Int, velocity: Int): ByteArray {
        return byteArrayOf(0x90.toByte(), note.coerceIn(0, 63).toByte(), velocity.coerceIn(0, 127).toByte())
    }

    fun fromApp(argb: Int, isStepCurrent: Boolean = false, isStepOn: Boolean = false): Int {
        if (isStepCurrent) return YELLOW
        if (isStepOn) return GREEN
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        if (r > 180 && r > g + 40 && r > b + 40) return RED
        if (r < 80 && g < 80 && b < 80) return OFF
        return GREEN
    }
}

class LedScheduler(private val minIntervalMs: Long = 30L) {
    private val pending = LinkedHashMap<Int, ByteArray>()
    private var lastFlush = 0L

    fun enqueue(key: Int, bytes: ByteArray) {
        pending[key] = bytes
    }

    fun setPad(note: Int, colorVelocity: Int, behavior: Int = ApcMiniMk2Protocol.CH_SOLID_100) {
        enqueue(note, ApcMiniMk2Protocol.rgbPad(note, colorVelocity, behavior))
    }

    fun setButton(note: Int, velocity: Int) {
        enqueue(1000 + note, ApcMiniMk2Protocol.singleButton(note, velocity))
    }

    fun allOff() {
        ApcMiniMk2Protocol.bulkOffPads().forEachIndexed { i, bytes -> enqueue(i, bytes) }
        ApcMiniMk2Protocol.bulkOffButtons().forEach { bytes ->
            val note = bytes[1].toInt() and 0xFF
            enqueue(1000 + note, bytes)
        }
    }

    fun drain(nowMs: Long, force: Boolean = false): List<ByteArray> {
        if (pending.isEmpty()) return emptyList()
        if (!force && nowMs - lastFlush < minIntervalMs) return emptyList()
        lastFlush = nowMs
        val out = pending.values.map { it.copyOf() }
        pending.clear()
        return out
    }

    fun pendingCount(): Int = pending.size

    companion object {
        fun ledTestSequence(): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            for (n in 0..63) out.add(ApcMiniMk2Protocol.rgbPad(n, ApcMiniMk2Protocol.COLOR_RED))
            for (n in 0..63) out.add(ApcMiniMk2Protocol.rgbPad(n, ApcMiniMk2Protocol.COLOR_OFF))
            for (n in 0x64..0x6B) out.add(ApcMiniMk2Protocol.singleButton(n, ApcMiniMk2Protocol.BUTTON_ON))
            for (n in 0x70..0x77) out.add(ApcMiniMk2Protocol.singleButton(n, ApcMiniMk2Protocol.BUTTON_ON))
            for (n in 0x64..0x6B) out.add(ApcMiniMk2Protocol.singleButton(n, ApcMiniMk2Protocol.BUTTON_OFF))
            for (n in 0x70..0x77) out.add(ApcMiniMk2Protocol.singleButton(n, ApcMiniMk2Protocol.BUTTON_OFF))
            return out
        }
    }
}
