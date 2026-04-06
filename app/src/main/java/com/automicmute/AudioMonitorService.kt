package com.automicmute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * AudioMonitorService
 *
 * Foreground service that keeps the AudioPlaybackMonitor alive in the background.
 * Shows a persistent notification with the current mic/audio state.
 */
class AudioMonitorService : Service() {

    companion object {
        private const val TAG = "AudioMonitorService"
        private const val CHANNEL_ID = "auto_mic_mute_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.automicmute.ACTION_START"
        const val ACTION_STOP = "com.automicmute.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, AudioMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // Binder for activity communication
    inner class LocalBinder : Binder() {
        fun getService(): AudioMonitorService = this@AudioMonitorService
    }

    private val binder = LocalBinder()
    private var monitor: AudioPlaybackMonitor? = null
    private var notificationManager: NotificationManager? = null

    // State tracking
    private var currentMicState = "Unmuted"
    private var currentAudioState = "No audio playing"
    private var activeStreamCount = 0

    // Callbacks for UI updates
    var onStateChanged: ((micMuted: Boolean, audioPlaying: Boolean, streams: Int) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        Log.i(TAG, "Starting foreground service")

        // Start foreground immediately
        startForeground(NOTIFICATION_ID, buildNotification())

        // Initialize monitor
        if (monitor == null) {
            monitor = AudioPlaybackMonitor(this, object : AudioPlaybackMonitor.MicStateListener {
                override fun onMicMuted(reason: String) {
                    currentMicState = "Muted"
                    updateNotification()
                    onStateChanged?.invoke(true, activeStreamCount > 0, activeStreamCount)
                }

                override fun onMicUnmuted(reason: String) {
                    currentMicState = "Unmuted"
                    updateNotification()
                    onStateChanged?.invoke(false, activeStreamCount > 0, activeStreamCount)
                }

                override fun onAudioStateChanged(isPlaying: Boolean, activeStreams: Int) {
                    activeStreamCount = activeStreams
                    currentAudioState = if (isPlaying) {
                        "Audio playing ($activeStreams stream${if (activeStreams != 1) "s" else ""})"
                    } else {
                        "No audio playing"
                    }
                    updateNotification()
                }
            })

            // Load unmute delay from preferences
            val prefs = getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
            val delay = prefs.getLong("unmute_delay", 1500L)
            monitor?.setUnmuteDelay(delay)
        }

        monitor?.startMonitoring()

        // Save running state
        getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("service_running", true)
            .apply()
    }

    private fun stopMonitoring() {
        Log.i(TAG, "Stopping foreground service")
        monitor?.stopMonitoring()
        monitor = null

        // Save stopped state
        getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("service_running", false)
            .apply()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitor?.stopMonitoring()
        monitor = null
        super.onDestroy()
    }

    fun setUnmuteDelay(delayMs: Long) {
        monitor?.setUnmuteDelay(delayMs)
        getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
            .edit()
            .putLong("unmute_delay", delayMs)
            .apply()
    }

    fun isMicMuted(): Boolean = monitor?.isMicMuted() ?: false
    fun isMonitoring(): Boolean = monitor?.isActive() ?: false

    // ─── Notification ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto Mic Mute Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Auto Mic Mute is monitoring audio playback"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val micIcon = if (currentMicState == "Muted") {
            android.R.drawable.ic_lock_silent_mode
        } else {
            android.R.drawable.ic_btn_speak_now
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Mic Mute Active")
            .setContentText("Mic: $currentMicState • $currentAudioState")
            .setSmallIcon(micIcon)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }
}
