package de.spaze.drumboombox

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class WavResult {
    data class Ok(val sampleRate: Int, val left: FloatArray, val right: FloatArray, val channels: Int) : WavResult() {
        val samples: FloatArray get() = left
    }
    data class Err(val reason: String) : WavResult()
}

object WavIO {
    const val MAX_BYTES = 6_000_000
    const val MAX_FRAMES = 44100 * 20

    fun decode(bytes: ByteArray): WavResult {
        if (bytes.size < 44) return WavResult.Err("Datei zu klein oder kein WAV")
        if (bytes.size > MAX_BYTES) return WavResult.Err("Datei größer als ${MAX_BYTES / 1_000_000} MB")
        if (ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "WAVE") {
            return WavResult.Err("Nur PCM-WAV wird ohne Gerätedecoder gelesen")
        }
        var pos = 12
        var fmtOff = -1
        var dataOff = -1
        var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val id = ascii(bytes, pos, 4)
            val size = u32(bytes, pos + 4)
            if (size < 0 || pos + 8 + size > bytes.size + 1) break
            if (id == "fmt ") fmtOff = pos + 8
            if (id == "data") {
                dataOff = pos + 8
                dataSize = size
                break
            }
            pos += 8 + size + (size and 1)
        }
        if (fmtOff < 0 || dataOff < 0) return WavResult.Err("WAV ohne fmt/data")
        val format = u16(bytes, fmtOff)
        val channels = u16(bytes, fmtOff + 2)
        val rate = u32(bytes, fmtOff + 4)
        val bits = u16(bytes, fmtOff + 14)
        if (format != 1) return WavResult.Err("Nur unkomprimiertes PCM (Format 1)")
        if (channels !in 1..2) return WavResult.Err("Nur Mono oder Stereo")
        if (bits != 16) return WavResult.Err("Nur 16-bit PCM")
        if (rate !in 8000..96000) return WavResult.Err("Abtastrate nicht unterstützt")
        val frameSize = channels * 2
        if (dataSize <= 0 || dataOff + dataSize > bytes.size) return WavResult.Err("data-Chunk unvollständig")
        val frames = dataSize / frameSize
        if (frames <= 0) return WavResult.Err("Keine Audiodaten")
        if (frames > MAX_FRAMES) return WavResult.Err("Länger als 20 Sekunden")
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        val bb = ByteBuffer.wrap(bytes, dataOff, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val l = bb.short / 32768f
            val r = if (channels == 2) bb.short / 32768f else l
            left[i] = l
            right[i] = r
        }
        return WavResult.Ok(rate, left, right, channels)
    }

    fun encodeMono16(samples: FloatArray, sampleRate: Int = 44100): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        fun put(s: String, o: Int) { s.toByteArray(Charsets.US_ASCII).copyInto(out, o) }
        put("RIFF", 0)
        w32(out, 4, 36 + dataSize)
        put("WAVE", 8)
        put("fmt ", 12)
        w32(out, 16, 16)
        w16(out, 20, 1)
        w16(out, 22, 1)
        w32(out, 24, sampleRate)
        w32(out, 28, sampleRate * 2)
        w16(out, 32, 2)
        w16(out, 34, 16)
        put("data", 36)
        w32(out, 40, dataSize)
        val bb = ByteBuffer.wrap(out, 44, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32767, 32767)
            bb.putShort(v.toShort())
        }
        return out
    }

    private fun ascii(b: ByteArray, o: Int, n: Int) = b.copyOfRange(o, o + n).toString(Charsets.US_ASCII)
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun w16(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }
    private fun w32(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte()
        b[o + 2] = (v shr 16).toByte(); b[o + 3] = (v shr 24).toByte()
    }
}
