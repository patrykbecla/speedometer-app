# Speedometer

A personal Android app that shows current speed in real time and records speed over time. Built for skiing, driving, flights, hikes, and general curiosity.

## Features

- **Live speed readout** — large, glanceable display with unit toggle (m/s, kmh, mph)
- **Session stats** — max speed, average speed, distance, altitude, vertical speed
- **GPS accuracy indicator** — shows exact ±Xm accuracy in real time
- **Background recording** — foreground service keeps recording with the screen off
- **Speed-over-time graph** — Vico line chart for any recorded session
- **Session history** — browse, rename, and delete past recordings
- **Export** — share any session as CSV or GPX via the Android share sheet
- **Jitter-resistant distance** — Doppler-speed gating prevents GPS noise from inflating distance while stationary

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- FusedLocationProviderClient for GPS
- Room (SQLite) for session/sample persistence
- DataStore for settings
- Vico for charts
- Foreground Service for background recording

## Building

Requires JDK 17 and the Android SDK (platforms;android-36, build-tools;36.0.0).

```bash
export JAVA_HOME=/usr/local/opt/openjdk@17
./gradlew assembleDebug
```

Install directly to a connected device:

```bash
./gradlew installDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk` and can be sideloaded without any signing setup.

## Distribution

Personal use only — sideloaded via USB or APK file share. Not on the Play Store.
