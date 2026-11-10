package de.spaze.drumboombox

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

class MidiSession(
    context: Context,
    private val onEvents: (List<ParsedMessage>) -> Unit,
    private val onStatus: () -> Unit
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val parser = MidiStreamParser()
    private var manager: MidiManager? = null
    private var device: MidiDevice? = null
    private var inputFromDevice: MidiOutputPort? = null
    private var outputToDevice: MidiInputPort? = null
    private var openGeneration = 0
    var deviceName: String = "kein Gerät"
        private set
    var inputOpen: Boolean = false
        private set
    var outputOpen: Boolean = false
        private set
    var lastError: String? = null
        private set
    var kind: DeviceKind = DeviceKind.NONE
        private set

    val isApcMiniMk2: Boolean get() = kind == DeviceKind.APC_MINI_MK2
    val isApcMiniMk1: Boolean get() = kind == DeviceKind.APC_MINI_MK1
    val isApcMini: Boolean get() = isApcMiniMk1 || isApcMiniMk2
    val isStudio: Boolean get() = kind == DeviceKind.STUDIO

    fun available(): Boolean = manager != null

    fun statusLine(): String {
        return buildString {
            append("MIDI: ")
            append(DeviceDetect.statusLabel(kind, deviceName))
            append(" · Eingang ")
            append(if (inputOpen) "offen" else "zu")
            append(" · Ausgang ")
            append(if (outputOpen) "offen" else "zu")
            if (!available()) append(" · MidiManager nicht verfügbar")
            lastError?.let { append(" · ").append(it) }
        }
    }

    fun start() {
        try { manager?.unregisterDeviceCallback(callback) } catch (_: Exception) {}
        manager = app.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        manager?.registerDeviceCallback(callback, handler)
        tryOpen()
        onStatus()
    }

    fun stop() {
        openGeneration++
        try { manager?.unregisterDeviceCallback(callback) } catch (_: Exception) {}
        closePorts("Sitzung beendet")
        manager = null
        onStatus()
    }

    fun send(bytes: ByteArray) {
        val out = outputToDevice ?: return
        try {
            out.send(bytes, 0, bytes.size)
        } catch (e: Exception) {
            lastError = "Senden fehlgeschlagen: ${e.message}"
        }
    }

    fun sendAll(list: List<ByteArray>) {
        if (!outputOpen) return
        list.forEach { send(it) }
    }

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            if (this@MidiSession.device == null) tryOpen()
            onStatus()
        }

        override fun onDeviceRemoved(deviceInfo: MidiDeviceInfo) {
            if (this@MidiSession.device?.info == deviceInfo) {
                closePorts("Gerät entfernt")
                tryOpen()
            }
            onStatus()
        }
    }

    private fun pickDevice(devices: Array<MidiDeviceInfo>): MidiDeviceInfo? {
        if (devices.isEmpty()) return null
        return devices.firstOrNull {
            val k = DeviceDetect.classify(deviceLabel(it))
            k == DeviceKind.APC_MINI_MK2 || k == DeviceKind.APC_MINI_MK1
        } ?: devices.firstOrNull {
            DeviceDetect.classify(deviceLabel(it)) == DeviceKind.STUDIO
        } ?: devices[0]
    }

    private fun deviceLabel(info: MidiDeviceInfo): String {
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""
        val product = info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: ""
        return "$name $product".trim()
    }

    private fun tryOpen() {
        val mgr = manager ?: return
        val info = pickDevice(mgr.devices) ?: run {
            deviceName = "kein Gerät"
            lastError = null
            kind = DeviceKind.NONE
            return
        }
        deviceName = deviceLabel(info).ifBlank { "MIDI-Gerät" }
        kind = DeviceDetect.classify(deviceName)
        val generation = openGeneration
        mgr.openDevice(info, { opened ->
            if (generation != openGeneration) {
                try { opened?.close() } catch (_: Exception) {}
                return@openDevice
            }
            if (opened == null) {
                lastError = "Gerät konnte nicht geöffnet werden"
                onStatus()
                return@openDevice
            }
            device = opened
            parser.reset()
            try {
                if (info.outputPortCount > 0) {
                    inputFromDevice = opened.openOutputPort(0)
                    inputFromDevice?.connect(object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                            val parsed = parser.push(msg, offset, count)
                            if (parsed.isNotEmpty()) {
                                handler.post { onEvents(parsed) }
                            }
                        }
                    })
                    inputOpen = true
                }
                if (info.inputPortCount > 0) {
                    outputToDevice = opened.openInputPort(0)
                    outputOpen = outputToDevice != null
                    if (outputOpen && isApcMiniMk2) {
                        send(ApcMiniMk2Protocol.introductionSysEx())
                    }
                }
                lastError = if (!outputOpen) "Kein MIDI-Ausgang – LEDs werden nicht gesendet" else null
            } catch (e: Exception) {
                lastError = e.message
            }
            onStatus()
        }, handler)
    }

    private fun closePorts(reason: String) {
        try { inputFromDevice?.close() } catch (_: Exception) {}
        try { outputToDevice?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        inputFromDevice = null
        outputToDevice = null
        device = null
        inputOpen = false
        outputOpen = false
        deviceName = "kein Gerät"
        kind = DeviceKind.NONE
        lastError = reason
        parser.reset()
    }
}
