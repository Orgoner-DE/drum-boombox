package de.spaze.drumboombox

data class ProjectSnapshot(
    val version: Int = 1,
    val bank: Int = 0,
    val kitIds: List<String> = listOf("street", "street", "street", "street"),
    val master: Float = 0.70f,
    val bpm: Double = 120.0,
    val swing: Double = 0.0,
    val pattern: String = "",
    val pads: String = "",
    val midi: MappingDocument = MappingDocument()
)

object ProjectJson {
    fun toJson(engine: DrumEngine, midi: MappingDocument): String {
        val seq = engine.sequencer
        val pattern = StringBuilder()
        pattern.append("[")
        for (b in 0..3) {
            if (b > 0) pattern.append(",")
            pattern.append("[")
            for (p in 0..15) {
                if (p > 0) pattern.append(",")
                pattern.append("[")
                for (s in 0..15) {
                    if (s > 0) pattern.append(",")
                    if (seq.on[b][p][s]) pattern.append(seq.vel[b][p][s]) else pattern.append(0)
                }
                pattern.append("]")
            }
            pattern.append("]")
        }
        pattern.append("]")
        val pads = StringBuilder("[")
        for (b in 0..3) {
            if (b > 0) pads.append(",")
            pads.append("[")
            for (p in 0..15) {
                if (p > 0) pads.append(",")
                val st = engine.pads[b][p]
                pads.append("{\"v\":").append(st.volume)
                    .append(",\"t\":").append(st.tune)
                    .append(",\"f\":").append(st.filter)
                    .append(",\"d\":").append(st.decay)
                    .append(",\"k\":\"").append(st.sampleKey.replace("\"", ""))
                    .append("\"}")
            }
            pads.append("]")
        }
        pads.append("]")
        val kits = (0..3).joinToString(",") { "\"" + engine.kitIdFor(it) + "\"" }
        return """
        {
          "version": 1,
          "bank": ${engine.bank},
          "kitIds": [$kits],
          "master": ${engine.master},
          "bpm": ${seq.bpm},
          "swing": ${seq.swing},
          "pattern": $pattern,
          "pads": $pads,
          "midi": ${MappingJson.toJson(midi)}
        }
        """.trimIndent()
    }

    fun apply(raw: String, engine: DrumEngine, table: MappingTable): MappingDocument {
        val midiChunk = extractObject(raw, "midi")
        val midi = if (midiChunk != null) MappingJson.fromJson(midiChunk) else MappingDocument()
        engine.bank = intVal(raw, "bank", 0).coerceIn(0, 3)
        engine.master = num(raw, "master", 0.7f).coerceIn(0f, 0.95f)
        engine.sequencer.bpm = num(raw, "bpm", 120f).toDouble()
        engine.sequencer.swing = num(raw, "swing", 0f).toDouble()
        val kitIds = stringArray(raw, "kitIds") ?: listOf("street", "street", "street", "street")
        for (b in 0..3) {
            val id = kitIds.getOrNull(b) ?: "street"
            engine.setBankKit(b, id)
            engine.applyKit(id, b)
        }
        parsePattern(raw, engine)
        parsePads(raw, engine)
        table.replaceAll(midi.bindings)
        table.replaceBlocked(midi.blocked)
        if (midi.bindings.isEmpty()) table.resetDefaults(engine.bank)
        return midi
    }

    private fun parsePattern(raw: String, engine: DrumEngine) {
        val start = raw.indexOf("\"pattern\"")
        if (start < 0) return
        val nums = Regex("-?\\d+").findAll(extractArray(raw, "pattern") ?: return).map { it.value.toInt() }.toList()
        if (nums.size < 4 * 16 * 16) return
        var i = 0
        for (b in 0..3) for (p in 0..15) for (s in 0..15) {
            val v = nums[i++]
            engine.sequencer.on[b][p][s] = v > 0
            if (v > 0) engine.sequencer.vel[b][p][s] = v.coerceIn(1, 127)
        }
    }

    private fun parsePads(raw: String, engine: DrumEngine) {
        val objs = Regex("\\{\"v\":([\\-\\d.]+),\"t\":([\\-\\d.]+),\"f\":([\\-\\d.]+),\"d\":([\\-\\d.]+),\"k\":\"([^\"]*)\"\\}")
            .findAll(extractArray(raw, "pads") ?: return).toList()
        if (objs.size < 64) return
        var i = 0
        for (b in 0..3) for (p in 0..15) {
            val m = objs[i++]
            val st = engine.pads[b][p]
            st.volume = m.groupValues[1].toFloat().coerceIn(0f, 1.5f)
            st.tune = m.groupValues[2].toFloat().coerceIn(-12f, 12f)
            st.filter = m.groupValues[3].toFloat().coerceIn(0f, 1f)
            st.decay = m.groupValues[4].toFloat().coerceIn(0f, 1f)
            st.sampleKey = m.groupValues[5]
        }
    }

    private fun intVal(raw: String, key: String, d: Int) =
        Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(raw)?.groupValues?.get(1)?.toInt() ?: d
    private fun num(raw: String, key: String, d: Float) =
        Regex("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(raw)?.groupValues?.get(1)?.toFloat() ?: d
    private fun stringArray(raw: String, key: String): List<String>? {
        val m = Regex("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)]").find(raw) ?: return null
        return m.groupValues[1].split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
    }
    private fun extractObject(raw: String, key: String): String? {
        val idx = raw.indexOf("\"$key\"")
        if (idx < 0) return null
        val start = raw.indexOf('{', idx)
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            if (raw[i] == '{') depth++
            if (raw[i] == '}') {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
        return null
    }
    private fun extractArray(raw: String, key: String): String? {
        val idx = raw.indexOf("\"$key\"")
        if (idx < 0) return null
        val start = raw.indexOf('[', idx)
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            if (raw[i] == '[') depth++
            if (raw[i] == ']') {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
        return null
    }
}
