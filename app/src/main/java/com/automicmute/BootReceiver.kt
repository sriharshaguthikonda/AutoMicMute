package com.automicmute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver
 *
 * Starts the AudioMonitorService automatically when the device boots,
 * if the user has enabled the "Start on Boot" option.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("auto_mic_mute_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start", false)
        val wasRunning = prefs.getBoolean("service_running", false)

        if (autoStart && wasRunning) {
            Log.i(TAG, "Boot completed — auto-starting AudioMonitorService")
            AudioMonitorService.startService(context)
        } else {
            Log.d(TAG, "Boot completed — auto-start disabled or service wasn't running")
        }
    }
}
