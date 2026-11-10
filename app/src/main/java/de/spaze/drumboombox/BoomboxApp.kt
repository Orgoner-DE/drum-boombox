package de.spaze.drumboombox

import android.app.Application

class BoomboxApp : Application() {
    lateinit var engine: DrumEngine
        private set
    lateinit var playback: PlaybackEngine
        private set
    lateinit var store: MappingStore
        private set
    val table = MappingTable()
    var midi: MidiSession? = null
    var learnMode: Boolean = false
    var learnTarget: ActionId = ActionId.PAD_0
    val padGlowUntil = LongArray(16)
    val padGlowVel = IntArray(16)

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = DrumEngine()
        playback = PlaybackEngine(engine)
        store = MappingStore(this)
    }

    fun ensureKits() {
        if (engine.repo.kits.isNotEmpty()) return
        engine.repo.loadFromAssets({ path ->
            assets.open(path).use { it.readBytes() }
        })
        for (b in 0..3) {
            engine.setBankKit(b, "street")
            engine.applyKit("street", b)
        }
    }

    fun persist() {
        val midiDoc = MappingDocument(
            bank = engine.bank,
            kit = engine.kitId,
            masterGain = engine.master,
            bindings = table.all(),
            blocked = table.blockedSources().toList()
        )
        store.save(midiDoc)
        store.saveProject(ProjectJson.toJson(engine, midiDoc))
    }

    fun restore() {
        ensureKits()
        val raw = store.loadProject()
        if (raw != null) {
            try {
                ProjectJson.apply(raw, engine, table)
                return
            } catch (_: Exception) {
            }
        }
        val map = store.load()
        if (map != null) {
            table.replaceAll(map.bindings)
            table.replaceBlocked(map.blocked)
            engine.bank = map.bank
            engine.master = map.masterGain
            engine.applyKit(map.kit)
        } else {
            table.resetDefaults(0)
        }
    }

    companion object {
        lateinit var instance: BoomboxApp
            private set
        fun get(): BoomboxApp = instance
    }
}
