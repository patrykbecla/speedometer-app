# Speedometer — Build Handoff

A personal Android app that shows my current speed in real time and records speed over time. Used for skiing, takeoff/landing in a plane, driving, and general curiosity.

This file is the build plan for Claude Code. Build **phase by phase, in order**. Each phase must leave the app in a runnable, installable state before moving on. Do not jump ahead to later phases.

---

## Context & constraints (read first)

- **Audience:** 100% personal use. Me, and possibly my dad's phone. No third parties.
- **No Play Store compliance required.** Ignore Play policy, target-SDK mandates, data-safety forms, privacy policies, etc. Optimize for "works well on my phone," not "passes review."
- **Target devices:** Pixel 9 Pro (primary), plus one other modern Android phone. Both run a current Android version.
- **Distribution:** sideload via APK (see the Distribution section at the bottom). Not the Play Store.
- **Build environment:** developed on macOS using Claude Code from the command line. Use Gradle CLI, not the Android Studio GUI, for build/install steps. Assume the Android SDK + a JDK are installed (see Phase 0).
- **Design priority:** glanceable. The live readout must be readable at a glance with ski gloves on or while driving — big, high-contrast numbers.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Location:** `FusedLocationProviderClient` (Google Play Services Location)
- **Charts:** Vico (Compose-native). MPAndroidChart is an acceptable fallback if Vico causes friction.
- **Persistence:** Room (SQLite)
- **Background work:** a foreground Service with a persistent notification for recording while backgrounded/screen-off
- **Min SDK:** 31 (Android 12). **Compile/Target SDK:** latest stable.
- **Versions:** use the latest stable Android Gradle Plugin, Kotlin, and Compose BOM at build time — verify current versions rather than pinning from memory.

---

## Phase 0 — Project scaffold

**Goal:** an empty-but-runnable app that installs on the phone and shows a blank screen.

**Build:**
- Standard Gradle Android project, Kotlin + Compose, single `:app` module.
- Configure min/compile/target SDK per the stack above.
- Add a placeholder `MainActivity` with a Compose scaffold and an app title.
- Set up version catalog (`libs.versions.toml`) for dependency management.

**Done when:** `./gradlew assembleDebug` produces an APK and it launches to a blank themed screen on device.

**Notes:**
- Prerequisites the user needs locally: a JDK (17+) and the Android SDK. Easiest source is installing Android Studio once (it bundles the SDK + platform-tools/`adb`), even though we build from the CLI afterward. Alternatively the command-line tools + `sdkmanager`.

---

## Phase 1 — Live speed readout (MVP)

**Goal:** open the app, see my current speed updating live.

**Build:**
- Request `ACCESS_FINE_LOCATION` at runtime with a clean permission-request UI and a denied/rationale state.
- Start location updates via `FusedLocationProviderClient` with a high-accuracy request and a configurable interval (default ~1s).
- Read `Location.getSpeed()` (m/s). Guard with `Location.hasSpeed()` before displaying; show a "–" placeholder when unavailable.
- **Unit toggle:** m/s, km/h, mph, knots. Persist the choice (DataStore). Big, central numeric readout with the unit beside it.
- Keep the screen on while the app is in the foreground (`FLAG_KEEP_SCREEN_ON` / `keepScreenOn`).

**Done when:** walking/driving around shows a sensible live speed in the selected unit, and the unit toggle works and persists across restarts.

**Notes:**
- GPS speed is **Doppler-derived** on most phones and is the right value to use — do not compute speed from successive lat/lng deltas unless `hasSpeed()` is false.
- Expect noise/garbage at near-stationary speeds; that's handled by smoothing in Phase 2.
- A cold GPS fix outdoors can take 20–40s; show an "acquiring GPS" state.

---

## Phase 2 — Live session stats & signal quality

**Goal:** richer live telemetry beyond the single number.

**Build:**
- **Current / max / average** speed for the active session (in-memory for now).
- **Distance traveled** this session.
- **Altitude** (`Location.getAltitude()`) and **vertical speed** (rate of altitude change, m/s or ft/min) — useful for both skiing descent and takeoff/landing.
- **Speed smoothing:** apply a moving average or light Kalman filter to the displayed speed to kill jitter. Keep raw values for recording; smooth only the display (or store both).
- **GPS accuracy indicator:** use `getAccuracy()` and, on API 26+, `getSpeedAccuracyMetersPerSecond()`. Show a simple good/ok/poor signal indicator so I know when to distrust the reading.

**Done when:** a live dashboard shows current/max/avg/distance/altitude/vertical-speed, the number is stable (not twitchy) when moving steadily, and signal quality is visible.

**Notes:**
- Vertical speed from GPS altitude is noisy; smooth it more aggressively than horizontal speed.

---

## Phase 3 — Recording, persistence & the speed-over-time graph

**Goal:** record a session in the background and view speed over time as a graph.

**Build:**
- **Room schema:** a `Session` (id, start, end, label, summary stats) and `Sample` rows `(sessionId, timestamp, speed, lat, lng, altitude, accuracy)`.
- **Start/Stop recording** controls. On start, create a session and persist samples as they arrive.
- **Foreground Service** with a persistent notification so recording continues with the screen off or app backgrounded. Move location collection into the service while recording.
- Request `ACCESS_BACKGROUND_LOCATION` (separate runtime prompt; required for background collection) and declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` permissions.
- **Speed-vs-time graph** (Vico): plot the active or a selected session. Live-updating during recording is a plus; a post-session render is the baseline.
- **Configurable sampling interval** in settings (battery vs. resolution tradeoff).

**Done when:** I can start a recording, lock the phone / put it in a pocket, move around, stop, and see an accurate speed-vs-time graph of that session. Battery drain is acceptable at the default interval.

**Notes:**
- High sample rates drain battery fast — that's why the interval is user-configurable.
- **Airplane use:** full airplane mode disables the GPS receiver. For in-flight recording I'll need to leave Location Services on (many flights allow this even in airplane mode by re-enabling location). Note this in the UI somewhere.

---

## Phase 4 — Session history & export

**Goal:** browse past recordings and get the data off the phone.

**Build:**
- **History screen:** list of saved sessions with date, duration, max/avg speed, distance. Tap to open the graph + stats. Delete and rename.
- **Export:**
  - **CSV** — flat rows of samples for spreadsheet analysis.
  - **GPX** — standard track format so I can replay routes in other tools/maps.
- Use Android's share sheet / Storage Access Framework so files land somewhere I can retrieve (Drive, email, USB).

**Done when:** I can pick an old session, export it as CSV and GPX, and open the result on my Mac.

---

## Phase 5 — Map view (STRETCH GOAL)

**Goal:** see the recorded track on a map, not just a graph. Only attempt after Phases 0–4 are solid.

**Build:**
- Render a recorded session's track as a polyline on a map.
- Color the polyline by speed if feasible (heatmap-style).
- Library options: Google Maps Compose (needs a Maps API key) or an OSM-based library (osmdroid / MapLibre) to avoid the API key. Pick whichever is least friction for personal use and note the choice.

**Done when:** opening a session shows its path on a map.

---

## Distribution — getting it from my Mac to my phone

Play Store is **not** needed and not worth it for personal use (paid developer account + signing + review). Use sideloading:

**To my Pixel 9 Pro (USB, the easy dev loop):**
1. Enable Developer Options on the phone (tap Build Number 7×), then turn on **USB debugging**.
2. Plug into the Mac, accept the debugging prompt.
3. `./gradlew installDebug` — builds and installs directly. This is the normal iterate-on-device command during development.

**To my dad's phone (no cable):**
1. Build a release-ish APK: `./gradlew assembleDebug` (debug APK is fine for personal use — no signing setup needed; it's self-signed with the debug key).
2. Find it at `app/build/outputs/apk/debug/app-debug.apk`.
3. Send the APK file (Drive, email, messaging).
4. On his phone: open the file, allow "install unknown apps" for the app he's installing from, tap install.

**Note for Claude Code:** keep a plain debug build working at all times so the APK is always sideloadable without signing-config setup. If a signed release build is ever wanted later, add a simple keystore + signing config, but don't block on it.

---

## Working agreement for Claude Code

- Build in phase order. Each phase ends with a runnable app and a short note on how to verify it on device.
- Prefer the latest stable library versions; verify them at build time rather than assuming.
- Keep the live readout glanceable and high-contrast as the central design constraint.
- When a phase introduces a new runtime permission, handle the denied state gracefully — never crash on denial.
