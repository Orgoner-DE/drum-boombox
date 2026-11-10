package de.spaze.drumboombox

import java.util.Locale

enum class MidiKind { NOTE, CC }

enum class ActionId {
    PAD_0, PAD_1, PAD_2, PAD_3, PAD_4, PAD_5, PAD_6, PAD_7,
    PAD_8, PAD_9, PAD_10, PAD_11, PAD_12, PAD_13, PAD_14, PAD_15,
    BANK_0, BANK_1, BANK_2, BANK_3,
    MASTER_VOLUME, TEMPO, SWING,
    PAD_VOLUME, PAD_TUNE, PAD_FILTER, PAD_DECAY,
    TRANSPORT_PLAY, TRANSPORT_STOP, TRANSPORT_REC,
    NOTE_REPEAT, KIT_NEXT, PANIC;

    val padIndex: Int?
        get() = if (name.startsWith("PAD_") && name.length <= 6) name.removePrefix("PAD_").toInt() else null

    companion object {
        fun pad(i: Int): ActionId = valueOf("PAD_$i")
        fun bank(i: Int): ActionId = valueOf("BANK_$i")
        fun parseOrNull(raw: String): ActionId? = try {
            valueOf(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

data class Binding(
    val kind: MidiKind,
    val channel: Int,
    val number: Int,
    val action: ActionId,
    val defaulted: Boolean = false
)

data class MappingDocument(
    val version: Int = 1,
    val bank: Int = 0,
    val kit: String = "street",
    val masterGain: Float = 0.70f,
    val quadrantOrigin: String = ApcLayout.ORIGIN_BOTTOM_LEFT,
    val bindings: List<Binding> = emptyList(),
    val blocked: List<String> = emptyList()
)

/**
 * Official protocol: clip-launch notes 0x00–0x3F. Example "pad 1" = note 00.
 * The PDF has no spatial diagram. Classic APC Mini Session / community notes
 * place note 0 at the bottom-left, rows increasing upward.
 */
object ApcLayout {
    const val ORIGIN_BOTTOM_LEFT = "bottom_left_note0"

    fun noteForPad(pad: Int, bank: Int): Int {
        require(pad in 0..15)
        require(bank in 0..3)
        val uiRow = pad / 4
        val uiCol = pad % 4
        val rowBase = if (bank < 2) 4 else 0
        val colBase = if (bank % 2 == 0) 0 else 4
        val hwRow = rowBase + (3 - uiRow)
        val hwCol = colBase + uiCol
        return hwRow * 8 + hwCol
    }

    fun padForNote(note: Int, bank: Int): Int? {
        if (note !in 0..63 || bank !in 0..3) return null
        val hwRow = note / 8
        val hwCol = note % 8
        val rowBase = if (bank < 2) 4 else 0
        val colBase = if (bank % 2 == 0) 0 else 4
        if (hwRow !in rowBase until (rowBase + 4)) return null
        if (hwCol !in colBase until (colBase + 4)) return null
        val uiRow = 3 - (hwRow - rowBase)
        val uiCol = hwCol - colBase
        return uiRow * 4 + uiCol
    }

    /** 16 Steps auf zwei Reihen, die nicht mit dem aktuellen Bank-Quadranten überlappen. */
    fun noteForStep(step: Int, bank: Int): Int {
        require(step in 0..15)
        val base = if (bank < 2) 0 else 48
        return base + step
    }

    fun defaultBindings(bank: Int): List<Binding> {
        val out = ArrayList<Binding>()
        for (pad in 0..15) {
            out.add(Binding(MidiKind.NOTE, 0, noteForPad(pad, bank), ActionId.pad(pad), defaulted = true))
        }
        // Scene Launch 1–4 = notes 0x70–0x73
        for (b in 0..3) {
            out.add(Binding(MidiKind.NOTE, 0, 0x70 + b, ActionId.bank(b), defaulted = true))
        }
        out.add(Binding(MidiKind.NOTE, 0, 0x6B, ActionId.PANIC, defaulted = true))
        out.add(Binding(MidiKind.NOTE, 0, 0x64, ActionId.TRANSPORT_PLAY, defaulted = true))
        out.add(Binding(MidiKind.NOTE, 0, 0x65, ActionId.TRANSPORT_STOP, defaulted = true))
        out.add(Binding(MidiKind.NOTE, 0, 0x66, ActionId.TRANSPORT_REC, defaulted = true))
        out.add(Binding(MidiKind.NOTE, 0, 0x67, ActionId.KIT_NEXT, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x38, ActionId.MASTER_VOLUME, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x30, ActionId.PAD_VOLUME, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x31, ActionId.PAD_TUNE, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x32, ActionId.PAD_FILTER, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x33, ActionId.PAD_DECAY, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x34, ActionId.TEMPO, defaulted = true))
        out.add(Binding(MidiKind.CC, 0, 0x35, ActionId.SWING, defaulted = true))
        // Generic class-compliant pad range (C1–D#2), typical for many pad controllers
        for (pad in 0..15) {
            out.add(Binding(MidiKind.NOTE, 0, 36 + pad, ActionId.pad(pad), defaulted = true))
        }
        return out
    }
}

class MappingTable {
    private val bindings = ArrayList<Binding>()
    private val blocked = HashSet<String>()

    fun all(): List<Binding> = bindings.toList()
    fun blockedSources(): Set<String> = blocked.toSet()

    fun replaceAll(next: List<Binding>) {
        bindings.clear()
        bindings.addAll(next)
    }

    fun replaceBlocked(next: Collection<String>) {
        blocked.clear()
        blocked.addAll(next)
    }

    private fun key(kind: MidiKind, channel: Int, number: Int) = "${kind.name}:$channel:$number"

    fun resetDefaults(bank: Int) {
        blocked.clear()
        replaceAll(ApcLayout.defaultBindings(bank))
    }

    fun applyBankDefaults(bank: Int) {
        val kept = bindings.filterNot { it.defaulted && it.action.padIndex != null }
        val next = ArrayList(kept)
        for (pad in 0..15) {
            val already = next.any { !it.defaulted && it.action == ActionId.pad(pad) }
            if (!already) {
                next.add(Binding(MidiKind.NOTE, 0, ApcLayout.noteForPad(pad, bank), ActionId.pad(pad), true))
            }
        }
        replaceAll(next)
    }

    fun learn(kind: MidiKind, channel: Int, number: Int, action: ActionId) {
        blocked.remove(key(kind, channel, number))
        bindings.removeAll { it.kind == kind && it.channel == channel && it.number == number }
        bindings.add(Binding(kind, channel, number, action, defaulted = false))
    }

    fun unbind(kind: MidiKind, channel: Int, number: Int) {
        bindings.removeAll { it.kind == kind && it.channel == channel && it.number == number }
        blocked.add(key(kind, channel, number))
    }

    fun clearAction(action: ActionId) {
        val sources = bindings.filter { it.action == action }
        bindings.removeAll { it.action == action }
        sources.forEach { blocked.add(key(it.kind, it.channel, it.number)) }
    }

    fun resolve(kind: MidiKind, channel: Int, number: Int, bank: Int): ActionId? {
        if (blocked.contains(key(kind, channel, number))) return null
        val exact = bindings.lastOrNull { it.kind == kind && it.channel == channel && it.number == number }
        if (exact != null) return exact.action
        if (kind == MidiKind.NOTE && channel == 0) {
            ApcLayout.padForNote(number, bank)?.let { return ActionId.pad(it) }
            if (number in 36..51) return ActionId.pad(number - 36)
        }
        return null
    }
}

object MappingJson {
    fun toJson(doc: MappingDocument): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(doc.version).append(",\n")
        sb.append("  \"bank\": ").append(doc.bank).append(",\n")
        sb.append("  \"kit\": \"").append(escape(doc.kit)).append("\",\n")
        sb.append("  \"masterGain\": ").append(doc.masterGain).append(",\n")
        sb.append("  \"quadrantOrigin\": \"").append(escape(doc.quadrantOrigin)).append("\",\n")
        sb.append("  \"blocked\": [").append(doc.blocked.joinToString(",") { "\"" + escape(it) + "\"" }).append("],\n")
        sb.append("  \"bindings\": [\n")
        doc.bindings.forEachIndexed { i, b ->
            sb.append("    {\"kind\":\"").append(b.kind.name)
                .append("\",\"channel\":").append(b.channel)
                .append(",\"number\":").append(b.number)
                .append(",\"action\":\"").append(b.action.name)
                .append("\",\"defaulted\":").append(b.defaulted)
                .append("}")
            if (i != doc.bindings.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    fun fromJson(raw: String): MappingDocument {
        val version = intField(raw, "version") ?: 1
        val bank = (intField(raw, "bank") ?: 0).coerceIn(0, 3)
        val kit = stringField(raw, "kit") ?: stringField(raw, "genre") ?: "street"
        val master = (floatField(raw, "masterGain") ?: 0.70f).coerceIn(0f, 0.85f)
        val origin = stringField(raw, "quadrantOrigin") ?: ApcLayout.ORIGIN_BOTTOM_LEFT
        val bindings = parseBindings(raw)
        val blocked = stringArrayField(raw, "blocked") ?: emptyList()
        return MappingDocument(version, bank, kit, master, origin, bindings, blocked)
    }

    private fun parseBindings(raw: String): List<Binding> {
        val start = raw.indexOf("\"bindings\"")
        if (start < 0) return emptyList()
        val bracket = raw.indexOf('[', start)
        val end = raw.indexOf(']', bracket)
        if (bracket < 0 || end < 0) return emptyList()
        val chunk = raw.substring(bracket + 1, end)
        val out = ArrayList<Binding>()
        val objRe = Regex("\\{([^}]*)\\}")
        for (m in objRe.findAll(chunk)) {
            val body = m.groupValues[1]
            val kind = stringField("{" + body + "}", "kind") ?: continue
            val actionRaw = stringField("{" + body + "}", "action") ?: continue
            val action = ActionId.parseOrNull(actionRaw) ?: continue
            val midiKind = try { MidiKind.valueOf(kind) } catch (_: Exception) { continue }
            val ch = intField("{" + body + "}", "channel") ?: 0
            val num = intField("{" + body + "}", "number") ?: continue
            val def = body.contains("\"defaulted\":true")
            out.add(Binding(midiKind, ch, num, action, def))
        }
        return out
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun stringField(raw: String, key: String): String? {
        val m = Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(raw) ?: return null
        return m.groupValues[1]
    }

    private fun intField(raw: String, key: String): Int? {
        val m = Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(raw) ?: return null
        return m.groupValues[1].toInt()
    }

    private fun floatField(raw: String, key: String): Float? {
        val m = Regex("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)" ).find(raw) ?: return null
        return m.groupValues[1].toFloat()
    }

    private fun stringArrayField(raw: String, key: String): List<String>? {
        val m = Regex("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").find(raw) ?: return null
        return m.groupValues[1].split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
    }
}

object MidiLearnActions {
    fun releaseFromUi(table: MappingTable, action: ActionId, bank: Int) {
        table.clearAction(action)
        val pad = action.padIndex ?: return
        val note = ApcLayout.noteForPad(pad, bank)
        if (table.resolve(MidiKind.NOTE, 0, note, bank) == action) {
            table.unbind(MidiKind.NOTE, 0, note)
        }
    }
}

object MidiParameters {
    fun u(value: Int): Float = value.coerceIn(0, 127) / 127f
}
