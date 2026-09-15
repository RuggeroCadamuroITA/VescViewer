# VescViewer — Feature and Redesign Plan

## Design audit and visual direction

The audit found a functional telemetry app with a useful glass-card foundation, but the visual language was inconsistent: generic Material containers were mixed with glass surfaces, navigation felt detached from the content, labels and hard-coded copy were unevenly localized, and live values changed without a clear motion hierarchy. The selected direction is **Telemetry Cockpit**: a premium technical instrument panel with an obsidian base, warm coral accent, mint/sky semantic signals, compact data typography, restrained translucent surfaces, and motion reserved for state changes and live telemetry.

The redesign principles are:

- speed and connection state lead every live screen;
- telemetry values use strong numeric hierarchy and semantic colour, never colour alone;
- glass is an elevation system, not decoration on every element;
- navigation is transparent and visually subordinate to the cockpit;
- setup, authentication, scanner, history, map, settings, portrait, and landscape share the same tokens;
- reduced-motion-friendly transitions and accessible touch targets are preferred over continuous effects.

## Implementation status (2026-09-13)

Completed in the current working tree: shared Telemetry Cockpit colour/type tokens, animated Liquid Glass surfaces and buttons, animated navigation transitions, redesigned authentication gate, setup transitions and action rail, scanner, ride controls, ride archive, OpenStreetMap inspector with live point selection fix, dashboard chart surface, and gauge surface integration. Existing BLE, Room, recording, alarm, PIN, biometric, setup, and telemetry logic was preserved.

Remaining product work is intentionally outside this visual pass: full dashboard drag-and-drop editor, complete alert delivery channels, CSV/JSON export, expanded localization for newly introduced copy, and physical-device visual smoke testing with GPS, BLE, background services, and biometric hardware.


## Approved decisions

- Map provider: OpenStreetMap through OSMDroid.
- Route rendering: speed-colored route by default, with a metric selector for GPS speed, power, battery current, voltage, MOSFET temperature, and ERPM.
- Route interaction: tapping any route segment selects the nearest recorded sample and shows that sample's timestamp, GPS speed, VESC speed, battery/motor current, voltage, power, ERPM, mechanical RPM, duty cycle, MOSFET temperature, Ah, Wh, distance, altitude, and GPS accuracy.
- Recording: manual start/stop with an automatic start suggestion after connection and movement detection.
- Background recording: continuous recording through a location foreground service with a persistent notification and pause/resume/stop actions.
- Sampling: adaptive recording; VESC telemetry follows the existing ~250 ms polling cadence and GPS is collected around one second or at the platform's available cadence, with duplicate points reduced.
- Storage: local Room database, no cloud synchronization in the first implementation.
- Maps offline behavior: automatic tile cache with an offline warning.
- Battery: voltage-based percentage plus consumption-based range estimation and Wh/km.
- Alerts: configurable battery, autonomy, temperature, current, voltage, link-loss, and GPS/telemetry alerts with debounce, hysteresis, notification, sound, and vibration options.
- First-run setup: add an Alerts and Security step before the final summary.
- Authentication: whole-app lock at launch, biometric preferred with a required fallback PIN of at least six digits; PIN is never stored in plaintext and is protected with Android Keystore-backed hashing/encryption.
- Dashboard customization: visual editor with drag-and-drop ordering, visibility, size, presets, and independent portrait/landscape layouts.
- Visual system: reusable Liquid Glass surfaces and animated interactions across setup, dashboard, devices, settings, history, map, authentication, dialogs, and navigation.
- Accessibility: honor reduced-motion preferences and never rely on color alone to communicate state.

## Current baseline

- Kotlin + Jetpack Compose + Material 3.
- minSdk 24, targetSdk 36.
- DataStore Preferences already stores app, vehicle, theme, and anti-distance alarm settings.
- VescRepository already exposes VESC telemetry, RSSI, connection state, and a 60-second in-memory history.
- Existing `GlassCard` is a first-pass decorative surface and must become the shared Liquid Glass component system.
- No map, Room database, location recording, authentication, or dashboard-layout editor currently exists.

## Implementation phases

### Phase 1 — Foundations

- Add verified dependencies for Room, OSMDroid, and AndroidX Biometric.
- Add Room entities, DAOs, database, repositories, and migrations for ride sessions, ride points, and alert rules.
- Add DataStore settings for recording, map metric, dashboard layout, alert preferences, authentication timeout, and reduced motion.
- Add required localized strings in Italian, English, German, and Spanish.
- Keep all existing telemetry and BLE behavior working.

### Phase 2 — Ride recording

- Add a location provider abstraction using the Android fused location APIs where available, with a platform-safe fallback.
- Add `RideRecordingForegroundService` with a persistent notification and pause/resume/stop actions.
- Add a recording coordinator that combines GPS samples with the latest VESC snapshot.
- Implement adaptive sampling, duplicate filtering, lifecycle recovery, and crash-safe session finalization.
- Add a dashboard recording control and an automatic start suggestion after movement detection.

### Phase 3 — History and OpenStreetMap

- Add a rides/history tab and session detail screen.
- Add OSMDroid map setup, attribution, tile cache, offline warning, route fitting, start/end markers, and metric legend.
- Render a speed-colored polyline by default and support metric switching.
- Select the nearest recorded sample on route tap.
- Show a Liquid Glass point-inspector card with complete GPS/VESC values and previous/next sample controls.
- Add charts, session summary, deletion, CSV/JSON export, and Android sharing.

### Phase 4 — Battery, autonomy, and alerts

- Add battery percentage confidence and consumption-based autonomy estimation.
- Add Wh/km, remaining distance, and remaining-time estimates when enough data exists.
- Add alert rules with thresholds, debounce, hysteresis, cooldown, and delivery channels.
- Add the Alerts and Security setup page and the matching settings section.
- Preserve the existing anti-distance alarm's intentional-disconnect behavior.

### Phase 5 — Authentication

- Add first-run PIN creation and confirmation with a six-digit minimum.
- Add Android BiometricPrompt with PIN fallback.
- Store only a salted verifier and protected metadata; never store the plaintext PIN.
- Lock the app on launch and after the configured background timeout.
- Ensure notifications, orientation changes, and process recreation cannot bypass authentication.

### Phase 6 — Dashboard editor

- Define a stable dashboard-card model and independent portrait/landscape layout state.
- Add presets: Cockpit, Minimal, Analysis, Battery, and Custom.
- Add visual drag-and-drop ordering, visibility, size, preview, reset, and reduced-motion behavior.
- Integrate recording, map shortcut, battery/autonomy, alerts, and telemetry cards.

### Phase 7 — Liquid Glass system and motion

- Replace the first-pass `GlassCard` with `GlassSurface`, `GlassCard`, `GlassButton`, `GlassIconButton`, `GlassCapsule`, `GlassDialog`, `GlassBottomSheet`, `GlassSwitch`, `GlassSlider`, and navigation surfaces.
- Add layered tint, glow, rim light, soft elevation, state colors, and a minSdk-compatible blur fallback.
- Apply the system to every screen, including setup, authentication, scanner, history, map, settings, and dialogs.
- Add press compression, liquid ripple, spring morph, route-marker transitions, temperature/battery color transitions, and animated navigation selection.
- Respect Android reduced-motion preferences.

### Phase 8 — Verification and release readiness

- Unit-test conversion, range, autonomy, alert, nearest-point, and sampling logic.
- Test Room persistence and migration behavior.
- Test authentication and lock timeout behavior.
- Test map selection with dense and sparse routes.
- Test foreground services, permissions, process recreation, and manual disconnect behavior.
- Run `:app:testDebugUnitTest`, `:app:assembleDebug`, and relevant lint/type checks.
- Verify portrait, landscape, light theme, dark theme, and all supported locales.

## Acceptance criteria

- A ride can be recorded continuously with the screen off.
- A saved ride opens on an OpenStreetMap route.
- Tapping any route area selects the nearest sample and shows its exact GPS and VESC statistics.
- History remains available offline after recording.
- Battery percentage, Wh/km, and autonomy are shown with a confidence state.
- Alerts can be configured during first-run setup and later in settings.
- App launch requires biometric authentication or a six-digit-or-longer PIN.
- Dashboard cards can be reordered, resized, shown, or hidden independently by orientation.
- Every major screen uses the shared Liquid Glass system.
- Major interactions have custom, reduced-motion-aware animations.
- Existing BLE telemetry, alarm behavior, localization, and vehicle numeric settings continue to work.
- Tests and debug APK build pass before release publication.

## Delivery order

Implement and verify one phase at a time. Do not publish a release until the complete feature set is built, the regression suite passes, and the APK has been manually smoke-tested on a real Android device with a VESC and GPS.
