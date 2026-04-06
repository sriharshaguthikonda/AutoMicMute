package com.automicmute

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * AudioPlaybackMonitor
 *
 * Monitors active audio playback streams using AudioManager.AudioPlaybackCallback (API 26+).
 * When any audio stream starts playing → mutes the microphone.
 * When all audio streams stop playing → unmutes the microphone after a configurable delay.
 */
class AudioPlaybackMonitor(
    private val context: Context,
    private val listener: MicStateListener
) {

    companion object {
        private const val TAG = "AudioPlaybackMonitor"
        private const val DEFAULT_UNMUTE_DELAY_MS = 1500L // 1.5 second delay before unmuting
    }

    interface MicStateListener {
        fun onMicMuted(reason: String)
        fun onMicUnmuted(reason: String)
        fun onAudioStateChanged(isPlaying: Boolean, activeStreams: Int)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var isMicCurrentlyMuted = false
    private var unmuteDelayMs = DEFAULT_UNMUTE_DELAY_MS

    /**
     * Count active streams.
     * getActivePlaybackConfigurations() already returns only active configs on all API levels,
     * so a simple size check is sufficient without needing the API-29-only isActive() method.
     */
    private fun countActive(configs: List<AudioPlaybackConfiguration>?): Int {
        return configs?.size ?: 0
    }

    // Runnable for delayed unmute
    private val unmuteRunnable = Runnable {
        if (!hasActiveAudioPlayback()) {
            unmuteMicrophone("Audio playback stopped")
        } else {
            Log.d(TAG, "Audio resumed during unmute delay — keeping mic muted")
        }
    }

    // Audio playback callback
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)

            val activeCount = countActive(configs)
            val isPlaying = activeCount > 0

            Log.d(TAG, "Playback config changed: $activeCount active streams, isPlaying=$isPlaying")

            listener.onAudioStateChanged(isPlaying, activeCount)

            if (isPlaying) {
                // Cancel any pending unmute
                handler.removeCallbacks(unmuteRunnable)
                muteMicrophone("Audio playback detected ($activeCount streams)")
            } else {
                // Delay unmute to handle brief pauses (e.g., between tracks)
                handler.removeCallbacks(unmuteRunnable)
                handler.postDelayed(unmuteRunnable, unmuteDelayMs)
                Log.d(TAG, "All audio stopped — scheduling unmute in ${unmuteDelayMs}ms")
            }
        }
    }

    /**
     * Start monitoring audio playback
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring")
            return
        }

        Log.i(TAG, "Starting audio playback monitoring")
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        isMonitoring = true

        // Check current state immediately
        val currentConfigs = audioManager.activePlaybackConfigurations
        val activeCount = countActive(currentConfigs)
        if (activeCount > 0) {
            muteMicrophone("Audio already playing on start ($activeCount streams)")
            listener.onAudioStateChanged(true, activeCount)
        } else {
            listener.onAudioStateChanged(false, 0)
        }
    }

    /**
     * Stop monitoring and restore mic state
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        Log.i(TAG, "Stopping audio playback monitoring")
        handler.removeCallbacks(unmuteRunnable)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        isMonitoring = false

        // Always unmute when stopping
        if (isMicCurrentlyMuted) {
            unmuteMicrophone("Monitoring stopped")
        }
    }

    /**
     * Mute the microphone
     */
    private fun muteMicrophone(reason: String) {
        if (isMicCurrentlyMuted) return

        try {
            audioManager.isMicrophoneMute = true
            isMicCurrentlyMuted = true
            Log.i(TAG, "Mic MUTED: $reason")
            listener.onMicMuted(reason)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to mute mic: ${e.message}")
        }
    }

    /**
     * Unmute the microphone
     */
    private fun unmuteMicrophone(reason: String) {
        if (!isMicCurrentlyMuted) return

        try {
            audioManager.isMicrophoneMute = false
            isMicCurrentlyMuted = false
            Log.i(TAG, "Mic UNMUTED: $reason")
            listener.onMicUnmuted(reason)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to unmute mic: ${e.message}")
        }
    }

    /**
     * Check if there are any active audio playback streams
     */
    private fun hasActiveAudioPlayback(): Boolean {
        val configs = audioManager.activePlaybackConfigurations
        return countActive(configs) > 0
    }

    /**
     * Set the delay before unmuting after audio stops
     */
    fun setUnmuteDelay(delayMs: Long) {
        unmuteDelayMs = delayMs
    }

    /**
     * Get current mic mute state
     */
    fun isMicMuted(): Boolean = isMicCurrentlyMuted

    /**
     * Get monitoring state
     */
    fun isActive(): Boolean = isMonitoring
}
