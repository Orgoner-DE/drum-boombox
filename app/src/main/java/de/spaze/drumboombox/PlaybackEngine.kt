package de.spaze.drumboombox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class PlaybackEngine(val drum: DrumEngine) {
    @Volatile var running: Boolean = false
        private set
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private var audioManager: AudioManager? = null

    /** Gemessene Pufferdauer in Millisekunden (Gerät kann mehr brauchen). */
    @Volatile var bufferMs: Double = 0.0
        private set

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        if (focus == AudioManager.AUDIOFOCUS_LOSS || focus == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            drum.sequencer.stop()
            drum.panic()
        }
    }

    fun start(context: Context): Boolean {
        if (running) return true
        var at: AudioTrack? = null
        try {
            audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            val min = AudioTrack.getMinBufferSize(
                drum.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(256 * 4)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .apply {
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    }
                }
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(drum.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            at = if (android.os.Build.VERSION.SDK_INT >= 23) {
                val b = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(min)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                b.build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    drum.sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    min,
                    AudioTrack.MODE_STREAM
                )
            }
            track = at
            val frames = (at.bufferSizeInFrames.takeIf { it > 0 } ?: (min / 4))
            bufferMs = frames * 1000.0 / drum.sampleRate
            at.play()
            running = true
            thread = Thread({
                val chunk = 256
                try {
                    while (running) {
                        val bytes = drum.renderPcm16(chunk)
                        if (!running) break
                        at.write(bytes, 0, bytes.size)
                    }
                } catch (_: Exception) {
                }
            }, "DrumBoomboxAudio").also {
                it.isDaemon = true
                it.priority = Thread.MAX_PRIORITY
                it.start()
            }
            return true
        } catch (_: Exception) {
            running = false
            thread = null
            try { at?.release() } catch (_: Exception) {}
            track = null
            try { audioManager?.abandonAudioFocus(focusListener) } catch (_: Exception) {}
            return false
        }
    }

    fun stop() {
        running = false
        try { thread?.join(400) } catch (_: Exception) {}
        thread = null
        try {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
        track = null
        drum.panic()
        try { audioManager?.abandonAudioFocus(focusListener) } catch (_: Exception) {}
    }
}
