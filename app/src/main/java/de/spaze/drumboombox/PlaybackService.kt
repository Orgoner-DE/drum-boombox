package de.spaze.drumboombox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Hält die Wiedergabe im Hintergrund mit Medienbenachrichtigung und Stop.
 * Ohne androidx.media: MediaSessionCompat ist optional – fällt auf eine eigene Notification zurück,
 * falls die Support-Lib fehlt. Wir nutzen die Framework-Notification plus eine einfache Session,
 * wenn vorhanden. Hier: reine Framework-APIs (kein androidx).
 */
class PlaybackService : Service() {

    private var session: android.media.session.MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        if (Build.VERSION.SDK_INT >= 21) {
            session = android.media.session.MediaSession(this, "DrumBoombox").apply {
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onStop() {
                        stopPlayback()
                    }
                    override fun onPause() {
                        stopPlayback()
                    }
                })
                isActive = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        val app = application as BoomboxApp
        app.playback.start(this)
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        session?.isActive = false
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun stopPlayback() {
        val app = application as BoomboxApp
        app.engine.sequencer.stop()
        app.engine.panic()
        app.persist()
        stopForeground(true)
        stopSelf()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags()
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            pendingFlags()
        )
        val playing = (application as BoomboxApp).engine.sequencer.playing
        val title = getString(R.string.app_name)
        val text = if (playing) getString(R.string.notif_playing) else getString(R.string.notif_ready)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_play)
            .setContentIntent(open)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stop)
        if (Build.VERSION.SDK_INT >= 21) {
            session?.let { builder.setStyle(Notification.MediaStyle().setMediaSession(it.sessionToken)) }
        }
        return builder.build()
    }

    private fun pendingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 23) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    companion object {
        const val CHANNEL = "boombox_playback"
        const val NOTIF_ID = 681
        const val ACTION_STOP = "de.spaze.drumboombox.STOP"
        const val ACTION_STOPPED = "de.spaze.drumboombox.STOPPED"

        fun start(context: Context) {
            val i = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
