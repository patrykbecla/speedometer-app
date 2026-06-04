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
- **Map view** — live map that follows your position during recording; recorded sessions show a speed heatmap (green → red) on the Graph/Map tab toggle in history

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- FusedLocationProviderClient for GPS
- Room (SQLite) for session/sample persistence
- DataStore for settings
- Vico for charts
- Foreground Service for background recording
- Google Maps Compose for the map view

## Maps API key

The map view requires a Google Maps API key with the **Maps SDK for Android** enabled. Without one the map screen will load but show no tiles.

1. Create a project at [console.cloud.google.com](https://console.cloud.google.com), enable **Maps SDK for Android**, and generate an API key.
2. Add it to `local.properties` (this file is git-ignored and never committed):
   ```
   MAPS_API_KEY=AIza...
   ```
3. Rebuild — the key is injected into the manifest at build time.

The Maps SDK for Android tile rendering is free with no usage cap.

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
