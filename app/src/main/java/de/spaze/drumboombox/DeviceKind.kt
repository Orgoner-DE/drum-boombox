package de.spaze.drumboombox

enum class DeviceKind {
    NONE, APC_MINI_MK1, APC_MINI_MK2, STUDIO, GENERIC
}

object DeviceDetect {
    fun classify(label: String): DeviceKind {
        val n = label.lowercase()
        if (n.isBlank()) return DeviceKind.NONE
        val apcMini = n.contains("apc") && n.contains("mini")
        if (apcMini && (n.contains("mk2") || n.contains("mk 2") || n.contains("mk-2"))) {
            return DeviceKind.APC_MINI_MK2
        }
        if (apcMini) return DeviceKind.APC_MINI_MK1
        // class-compliant "Studio"-Controller, ohne Markennamen in der UI
        if (n.contains("studio")) return DeviceKind.STUDIO
        return DeviceKind.GENERIC
    }

    /** Anzeige ohne Markennamen (Akai / Produktlinie). */
    fun statusLabel(kind: DeviceKind, rawName: String): String = when (kind) {
        DeviceKind.NONE -> "kein Gerät"
        DeviceKind.APC_MINI_MK1, DeviceKind.APC_MINI_MK2 -> "APC mini"
        DeviceKind.STUDIO -> "Studio"
        DeviceKind.GENERIC -> sanitize(rawName).ifBlank { "Controller" }
    }

    fun sanitize(raw: String): String {
        var s = raw
        for (word in listOf("Akai", "AKAI", "akai", "MPC", "Mpc", "mpc")) {
            s = s.replace(word, "")
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
