package de.spaze.drumboombox

data class PadSound(
    val key: String,
    val label: String,
    val path: String,
    val samples: FloatArray,
    val sampleRate: Int
)

data class DrumKit(
    val id: String,
    val label: String,
    val pads: List<PadSound>
)

data class PadSettings(
    var volume: Float = 1f,
    var tune: Float = 0f,      // semitones -12..12
    var filter: Float = 1f,    // 0 closed .. 1 open
    var decay: Float = 0.75f,  // 0 short .. 1 long
    var chokeGroup: Int = 0,
    var sampleKey: String = ""
)

object KitCatalog {
    val DEFAULT_LABELS = listOf(
        "Kick", "Snare", "Clap", "Rim",
        "HH C", "HH O", "Shaker", "Ride",
        "Tom L", "Tom M", "Tom H", "Glocke",
        "Perc 1", "Perc 2", "Perc 3", "Crash"
    )
    val DEFAULT_KEYS = listOf(
        "kick", "snare", "clap", "rim",
        "hat_c", "hat_o", "shaker", "ride",
        "tom_lo", "tom_mid", "tom_hi", "cowbell",
        "perc1", "perc2", "perc3", "crash"
    )
    // street/steel/room: synthetisiert; dusty/live/crate: echte CC0-Aufnahmen (Virtuosity Drums, VCSL)
    val KIT_IDS = listOf("street", "steel", "room", "dusty", "live", "crate")
    val KIT_LABELS = listOf("Street", "Steel", "Room", "Dusty", "Live", "Crate")
    val REAL_KIT_IDS = listOf("dusty", "live", "crate")

    fun chokeGroupForPad(pad: Int): Int = if (pad == 4 || pad == 5) 1 else 0
}

class KitRepository {
    val kits = ArrayList<DrumKit>()

    fun loadFromAssets(open: (String) -> ByteArray, manifestJson: String? = null) {
        kits.clear()
        val ids = KitCatalog.KIT_IDS
        val labels = KitCatalog.KIT_LABELS
        for (k in ids.indices) {
            val id = ids[k]
            val pads = ArrayList<PadSound>()
            for (p in 0..15) {
                val key = KitCatalog.DEFAULT_KEYS[p]
                val path = "samples/$id/$key.wav"
                val bytes = try {
                    open(path)
                } catch (_: Exception) {
                    ByteArray(0)
                }
                val decoded = if (bytes.isNotEmpty()) WavIO.decode(bytes) else WavResult.Err("leer")
                val sound = when (decoded) {
                    is WavResult.Ok -> PadSound(key, KitCatalog.DEFAULT_LABELS[p], path, decoded.left, decoded.sampleRate)
                    is WavResult.Err -> PadSound(key, KitCatalog.DEFAULT_LABELS[p], path, FloatArray(0), 44100)
                }
                pads.add(sound)
            }
            kits.add(DrumKit(id, labels[k], pads))
        }
        manifestJson?.let { /* reserved for future path overrides */ }
    }

    fun kit(id: String): DrumKit? = kits.firstOrNull { it.id == id } ?: kits.firstOrNull()
    fun nextId(current: String): String {
        val i = KitCatalog.KIT_IDS.indexOf(current).let { if (it < 0) 0 else it }
        return KitCatalog.KIT_IDS[(i + 1) % KitCatalog.KIT_IDS.size]
    }
}
