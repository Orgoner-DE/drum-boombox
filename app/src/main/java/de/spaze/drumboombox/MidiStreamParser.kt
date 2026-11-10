package de.spaze.drumboombox

data class MidiEvent(
    val status: Int,
    val data1: Int,
    val data2: Int,
    val rawStatus: Int
) {
    val type: Int get() = rawStatus and 0xF0
    val channel: Int get() = rawStatus and 0x0F
    val isNoteOn: Boolean get() = (rawStatus and 0xF0) == 0x90 && data2 > 0
    val isNoteOff: Boolean get() = (rawStatus and 0xF0) == 0x80 || ((rawStatus and 0xF0) == 0x90 && data2 == 0)
    val isControlChange: Boolean get() = (rawStatus and 0xF0) == 0xB0
    val isAllNotesOff: Boolean get() = isControlChange && (data1 == 123 || data1 == 120)
}

sealed class ParsedMessage {
    data class Voice(val event: MidiEvent) : ParsedMessage()
    data class Realtime(val status: Int) : ParsedMessage()
    data class AllNotesOff(val channel: Int, val source: MidiEvent) : ParsedMessage()
}

class MidiStreamParser {
    private var runningStatus = 0
    private val buf = ArrayList<Int>(4)
    private var neededData = 0
    private var inSysex = false

    fun reset() {
        runningStatus = 0
        buf.clear()
        neededData = 0
        inSysex = false
    }

    fun push(data: ByteArray, offset: Int = 0, count: Int = data.size): List<ParsedMessage> {
        val out = ArrayList<ParsedMessage>()
        var i = offset
        val end = offset + count
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            if (b >= 0xF8) {
                out.add(ParsedMessage.Realtime(b))
                i++
                continue
            }
            if (inSysex) {
                if (b == 0xF7) {
                    inSysex = false
                    i++
                    continue
                }
                if (b < 0x80) {
                    i++
                    continue
                }
                inSysex = false
            }
            if (b == 0xF0) {
                inSysex = true
                runningStatus = 0
                buf.clear()
                i++
                continue
            }
            if (b == 0xF7) {
                i++
                continue
            }
            if (b >= 0x80) {
                buf.clear()
                buf.add(b)
                neededData = dataLength(b)
                runningStatus = if (b < 0xF0) b else 0
                i++
                if (neededData == 0) emit(out)
                continue
            }
            if (buf.isEmpty()) {
                if (runningStatus == 0) {
                    i++
                    continue
                }
                buf.add(runningStatus)
                neededData = dataLength(runningStatus)
            }
            buf.add(b)
            i++
            if (buf.size - 1 >= neededData) emit(out)
        }
        return out
    }

    private fun emit(out: MutableList<ParsedMessage>) {
        if (buf.isEmpty()) return
        val st = buf[0]
        val d1 = if (buf.size > 1) buf[1] else 0
        val d2 = if (buf.size > 2) buf[2] else 0
        val normalizedStatus = if ((st and 0xF0) == 0x90 && d2 == 0) {
            0x80 or (st and 0x0F)
        } else {
            st
        }
        val ev = MidiEvent(
            status = normalizedStatus,
            data1 = d1,
            data2 = d2,
            rawStatus = st
        )
        if (ev.isAllNotesOff) {
            out.add(ParsedMessage.AllNotesOff(ev.channel, ev))
        }
        out.add(ParsedMessage.Voice(ev))
        buf.clear()
    }

    companion object {
        fun dataLength(status: Int): Int {
            return when (status and 0xF0) {
                0xC0, 0xD0 -> 1
                0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 2
                else -> when (status) {
                    0xF1, 0xF3 -> 1
                    0xF2 -> 2
                    else -> 0
                }
            }
        }
    }
}
