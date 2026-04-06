package com.automicmute

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    // UI Elements
    private lateinit var btnToggleService: Button
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var tvAudioStatus: TextView
    private lateinit var tvStreamCount: TextView
    private lateinit var ivMicIcon: ImageView
    private lateinit var indicatorDot: View
    private lateinit var seekUnmuteDelay: SeekBar
    private lateinit var tvDelayValue: TextView
    private lateinit var switchAutoStart: SwitchCompat
    private lateinit var layoutStatus: LinearLayout
    private lateinit var tvLog: TextView

    // Service binding
    private var audioService: AudioMonitorService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioMonitorService.LocalBinder
            audioService = binder.getService()
            isBound = true

            // Register for state updates
            audioService?.onStateChanged = { micMuted, audioPlaying, streams ->
                runOnUiThread {
                    updateMicUI(micMuted)
                    updateAudioUI(audioPlaying, streams)
                    appendLog(
                        if (micMuted) "🔇 Mic muted (audio detected)"
                        else "🎙️ Mic unmuted (audio stopped)"
                    )
                }
            }

            updateServiceUI(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isBound = false
            updateServiceUI(false)
        }
    }

    private val logEntries = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        checkPermissions()

        // Restore state
        val prefs = getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", false)
        val delay = prefs.getLong("unmute_delay", 1500L)
        val autoStart = prefs.getBoolean("auto_start", false)

        seekUnmuteDelay.progress = (delay / 500).toInt().coerceIn(0, 10)
        tvDelayValue.text = "${delay}ms"
        switchAutoStart.isChecked = autoStart

        if (isRunning) {
            bindToService()
        }
    }

    private fun initViews() {
        btnToggleService = findViewById(R.id.btn_toggle_service)
        tvServiceStatus = findViewById(R.id.tv_service_status)
        tvMicStatus = findViewById(R.id.tv_mic_status)
        tvAudioStatus = findViewById(R.id.tv_audio_status)
        tvStreamCount = findViewById(R.id.tv_stream_count)
        ivMicIcon = findViewById(R.id.iv_mic_icon)
        indicatorDot = findViewById(R.id.indicator_dot)
        seekUnmuteDelay = findViewById(R.id.seek_unmute_delay)
        tvDelayValue = findViewById(R.id.tv_delay_value)
        switchAutoStart = findViewById<SwitchCompat>(R.id.switch_auto_start)
        layoutStatus = findViewById(R.id.layout_status)
        tvLog = findViewById(R.id.tv_log)
    }

    private fun setupListeners() {
        btnToggleService.setOnClickListener {
            if (isBound && audioService?.isMonitoring() == true) {
                stopMonitoringService()
            } else {
                startMonitoringService()
            }
        }

        seekUnmuteDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delayMs = (progress * 500).toLong()
                tvDelayValue.text = "${delayMs}ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val delayMs = ((seekBar?.progress ?: 3) * 500).toLong()
                audioService?.setUnmuteDelay(delayMs)
                getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
                    .edit()
                    .putLong("unmute_delay", delayMs)
                    .apply()
                appendLog("⏱️ Unmute delay set to ${delayMs}ms")
            }
        })

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("auto_mic_mute_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("auto_start", isChecked)
                .apply()
        }
    }

    private fun startMonitoringService() {
        if (!hasRequiredPermissions()) {
            checkPermissions()
            return
        }

        AudioMonitorService.startService(this)

        // Bind to get updates
        bindToService()
        appendLog("▶️ Service started")
    }

    private fun stopMonitoringService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            audioService = null
        }
        AudioMonitorService.stopService(this)
        updateServiceUI(false)
        updateMicUI(false)
        updateAudioUI(false, 0)
        appendLog("⏹️ Service stopped")
    }

    private fun bindToService() {
        val intent = Intent(this, AudioMonitorService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ─── UI Updates ──────────────────────────────────────────────────

    private fun updateServiceUI(running: Boolean) {
        if (running) {
            btnToggleService.text = "Stop Monitoring"
            btnToggleService.setBackgroundColor(Color.parseColor("#DC3545"))
            tvServiceStatus.text = "Service: Running"
            tvServiceStatus.setTextColor(Color.parseColor("#28A745"))
            indicatorDot.setBackgroundResource(R.drawable.dot_green)
        } else {
            btnToggleService.text = "Start Monitoring"
            btnToggleService.setBackgroundColor(Color.parseColor("#007BFF"))
            tvServiceStatus.text = "Service: Stopped"
            tvServiceStatus.setTextColor(Color.parseColor("#DC3545"))
            indicatorDot.setBackgroundResource(R.drawable.dot_red)
        }
    }

    private fun updateMicUI(muted: Boolean) {
        if (muted) {
            tvMicStatus.text = "Microphone: MUTED"
            tvMicStatus.setTextColor(Color.parseColor("#DC3545"))
            ivMicIcon.setImageResource(android.R.drawable.ic_lock_silent_mode)
            ivMicIcon.setColorFilter(Color.parseColor("#DC3545"))
        } else {
            tvMicStatus.text = "Microphone: Active"
            tvMicStatus.setTextColor(Color.parseColor("#28A745"))
            ivMicIcon.setImageResource(android.R.drawable.ic_btn_speak_now)
            ivMicIcon.setColorFilter(Color.parseColor("#28A745"))
        }
    }

    private fun updateAudioUI(playing: Boolean, streams: Int) {
        tvAudioStatus.text = if (playing) "Audio: Playing" else "Audio: Silent"
        tvAudioStatus.setTextColor(
            if (playing) Color.parseColor("#FFC107") else Color.parseColor("#6C757D")
        )
        tvStreamCount.text = "Active streams: $streams"
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logEntries.add(0, "[$timestamp] $message")
        if (logEntries.size > 50) logEntries.removeAt(logEntries.lastIndex)
        tvLog.text = logEntries.joinToString("\n")
    }

    // ─── Permissions ─────────────────────────────────────────────────

    private fun hasRequiredPermissions(): Boolean {
        val audioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return audioPermission && notifPermission
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                appendLog("✅ All permissions granted")
            } else {
                appendLog("⚠️ Some permissions denied — mic control may not work")
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            audioService?.onStateChanged = null
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
