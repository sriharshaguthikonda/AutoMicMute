# Auto Mic Mute - Android App

Automatically mutes your device's microphone when audio is playing through the speaker, and unmutes it when the audio stops.

## Features

- **Automatic mic muting** — Detects when any app plays audio (music, videos, podcasts, games) and instantly mutes the microphone
- **Auto unmute** — Restores the microphone when audio playback stops, with a configurable delay
- **Configurable unmute delay** — Set a delay (0-5 seconds) to prevent rapid toggling during brief pauses between tracks
- **Foreground service** — Runs reliably in the background with a persistent notification showing current state
- **Boot auto-start** — Optionally starts monitoring automatically when the device boots
- **Event log** — Real-time log of mute/unmute events in the app

## Requirements

- **Android 8.0 (API 26) or higher** — Required for `AudioManager.AudioPlaybackCallback` API
- **Permissions:**
  - `RECORD_AUDIO` — Required to control the microphone mute state
  - `MODIFY_AUDIO_SETTINGS` — Required to toggle mic mute
  - `POST_NOTIFICATIONS` — Required for the foreground service notification (Android 13+)
  - `FOREGROUND_SERVICE` — Required to run the monitoring service
  - `RECEIVE_BOOT_COMPLETED` — Required for auto-start on boot

## How It Works

The app uses Android's `AudioManager.AudioPlaybackCallback` API to listen for changes in active audio playback streams. This is a system-level callback that fires whenever any app starts or stops playing audio.

### Flow:
1. User taps "Start Monitoring" → Foreground service starts
2. `AudioPlaybackCallback.onPlaybackConfigChanged()` fires when audio state changes
3. If active audio streams detected → `AudioManager.isMicrophoneMute = true`
4. If all audio streams stop → Waits for configured delay → `AudioManager.isMicrophoneMute = false`
5. The delay prevents rapid toggling during brief pauses (e.g., between songs)

### Key Classes:
- **`AudioPlaybackMonitor`** — Core logic: registers the playback callback and controls mic mute/unmute
- **`AudioMonitorService`** — Foreground service that keeps the monitor alive and shows a notification
- **`MainActivity`** — UI with status dashboard, controls, and event log
- **`BootReceiver`** — BroadcastReceiver that auto-starts the service on boot if enabled

## Building

1. Open the project in Android Studio (Hedgehog 2023.1+ recommended)
2. Sync Gradle
3. Build and run on a device or emulator with API 26+

```bash
./gradlew assembleDebug
```

## Project Structure

```
AutoMicMute/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/automicmute/
│       │   ├── AudioPlaybackMonitor.kt   # Core audio detection + mic control
│       │   ├── AudioMonitorService.kt     # Foreground service
│       │   ├── MainActivity.kt            # Main UI
│       │   └── BootReceiver.kt            # Auto-start on boot
│       └── res/
│           ├── layout/activity_main.xml   # Main layout
│           ├── drawable/                  # Icons and shapes
│           └── values/                    # Colors, strings, themes
├── build.gradle                           # Root build file
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## Limitations

- Requires Android 8.0+ (API 26) for `AudioPlaybackCallback`
- Some apps may not correctly set `AudioAttributes`, causing their audio to be undetected
- Android provides anonymized audio playback configs — the app cannot detect *which* app is playing audio
- `AudioManager.isMicrophoneMute` may not work on all devices/ROMs consistently
- Some manufacturer skins may restrict background mic control

## License

MIT License — Free to use, modify, and distribute.
