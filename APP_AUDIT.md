# Android App — Full Audit

**Audit date:** 2026-09-14  
**Scope:** Android Gradle project rooted at `VESC Companion`, module `:app`  
**Method:** repository inventory, source/resource review, cross-file flow tracing, Gradle/build inspection, unit-test inspection, lint/build execution.  
**Confidence convention:** `HIGH` means directly demonstrated by source/configuration or a reproducible build result; `MEDIUM` means the risk is clear but depends on runtime/device conditions; `LOW` means the observation needs device or production confirmation.

> **Scope note:** the working tree already contained substantial product, UI, localization, and documentation changes before this audit. Those changes were not reverted or rewritten. This document records the state observed during the audit and does not attribute every existing modification to this audit.

## Summary table

| ID | Priority | Category | File | Problem | Difficulty |
|---|---|---|---|---|---|
| A-001 | HIGH | Bug / Configuration | `AndroidManifest.xml` | OSMDroid map lacked the `INTERNET` permission *(RESOLVED in Phase 1)* | LOW |
| A-002 | HIGH | Architecture / Bug | `VescRepository.kt` | SPP transport is implemented but never reached | MEDIUM |
| A-003 | HIGH | Bug / Safety | `VescRepository.kt` | Alarm logic observes BLE even when another transport is active | MEDIUM |
| A-004 | HIGH | Security / Lifecycle | `AuthManager.kt`, `MainActivity.kt` | Authentication timeout and background relock are never enforced | MEDIUM |
| A-005 | HIGH | Bug | `SettingsRepository.kt`, `SettingsScreen.kt` | Telemetry alert settings have no evaluator or delivery path | HIGH |
| A-006 | HIGH | Data integrity | `VescDatabase.kt` | Any future Room schema change can destroy all rides | MEDIUM |
| A-007 | HIGH | Stability / Concurrency | `RideRecorder.kt` | Start, stop, location writes, and state updates race asynchronously *(FIXED in Phase 2)* | MEDIUM |
| A-008 | HIGH | Data correctness | `RideRecorder.kt` | Session energy and VESC distance use cumulative controller counters *(FIXED in Phase 2)* | MEDIUM |
| A-009 | HIGH | UX / Correctness | `RideMapScreen.kt` | Map inspector ignores the selected speed and temperature units | LOW |
| A-010 | HIGH | Build / Quality | `BleManager.kt` and other lint findings | Debug lint failed with 23 errors and 59 warnings *(RESOLVED in Phase 1; 59 warnings remain)* | MEDIUM |
| A-011 | HIGH | Release | `app/build.gradle.kts` | Release lacked production shrinking/signing configuration *(RESOLVED as configuration; credentials remain CI-owned)* | MEDIUM |
| A-012 | HIGH | Testing | `app/src/test`, `app/src/androidTest` | Core Android/data/service flows are largely untested | HIGH |
| A-013 | HIGH | Security / Privacy | `AndroidManifest.xml`, backup XML | Application data and authentication metadata are backup-eligible | MEDIUM |
| A-014 | MEDIUM | Lifecycle / Memory | `RideMapScreen.kt` | Each map load creates a new permanent Flow collector | LOW |
| A-015 | MEDIUM | Bug | `SppManager.kt` | SPP write failure is discarded before waiting for a response | LOW |
| A-016 | MEDIUM | Stability | `RideRecordingService.kt`, `RideRecorder.kt` | Process death leaves incomplete sessions without recovery/finalization *(FIXED in Phase 2; device validation pending)* | HIGH |
| A-017 | MEDIUM | UX / Background | `RideRecordingService.kt` | Recording notification cannot resume a paused recording | LOW |
| A-018 | MEDIUM | Performance / Lifecycle | Compose screens | Flows use `collectAsState()` rather than lifecycle-aware collection | LOW |
| A-019 | MEDIUM | Platform compatibility | `MainActivity.kt`, services | Permission and foreground-service failure states are not surfaced consistently | MEDIUM |
| A-020 | MEDIUM | Location / UX | `RideRecordingService.kt` | Location provider failures and quality are silently ignored | MEDIUM |
| A-021 | MEDIUM | Notification | `AlarmForegroundService.kt` | Alarm channel is permanently created with low importance and no sound/vibration | LOW |
| A-022 | MEDIUM | Product correctness | `VescRepository.kt` | Battery percentage is a generic linear voltage estimate | LOW |
| A-023 | MEDIUM | Map correctness | `RideAnalytics.kt`, `RideMapScreen.kt` | Nearest-point calculation treats latitude and longitude as equal distances | LOW |
| A-024 | MEDIUM | Map / Product gap | `RideMapScreen.kt`, `PLAN.md` | Offline cache warning and robust tile policy are not implemented | MEDIUM |
| A-025 | MEDIUM | State management | `MainActivity.kt` | Selected tab and map state are lost on recreation | LOW |
| A-026 | MEDIUM | Security | `AuthRepository.kt` | PIN verification has no rate limiting and does not use Keystore-backed metadata | MEDIUM |
| A-027 | MEDIUM | Stability | `RideRecorder.kt` | Persistence exceptions can permanently cancel the recorder scope with no user feedback *(FIXED in Phase 2)* | MEDIUM |
| A-028 | MEDIUM | Protocol robustness | `VescPacket.kt`, `VescRepository.kt` | Parser and conversion layers trust packet/vehicle invariants without broad validation | MEDIUM |
| A-029 | LOW | Architecture | `VescCompanionApp.kt` | Global ServiceLocator creates hidden coupling and weakens testability | MEDIUM |
| A-030 | LOW | Code quality | `VescRepository.kt`, `SppManager.kt`, UI | Unused/dead paths remain in the production source set | LOW |
| A-031 | LOW | Maintainability | Dashboard screens | Portrait and landscape duplicate substantial presentation logic | MEDIUM |
| A-032 | LOW | Build / Operations | Repository root | No Android CI, static-analysis policy, or release automation is present | MEDIUM |
| A-033 | LOW | Compatibility | `VescDatabase.kt`, `Theme.kt` | Deprecated APIs produce release warnings | LOW |
| A-034 | LOW | Documentation | `README.md`, `app/build.gradle.kts` | README version/build claims do not match the current Gradle version | LOW |
| A-035 | INFO | Security | Whole project | No network API, cloud account, API key, or motor-control command was found | — |
| A-036 | MEDIUM | Security / Technical debt | `AuthRepository.kt` | PIN verifier is not device-bound through Android Keystore *(OPEN; not planned)* | MEDIUM |

## Priority matrix

```text
CRITICAL: 0
HIGH:     13
MEDIUM:   15
LOW:      6
INFO:     1
```

## Category matrix

```text
Security / Privacy:       2 high + 1 medium + 1 info
Architecture:             1 high + 1 low
Bug / Correctness:        5 high + 5 medium
Data integrity:           2 high
Lifecycle / Concurrency:  2 high + 4 medium
Performance:              1 medium
Map / Location:            1 high + 4 medium
Build / Release:          2 high + 2 low
Testing:                   1 high
UX / Accessibility:        2 high + 3 medium
Technical debt:            3 low
```

# 1. Executive Summary

VescViewer is a single-module Kotlin/Jetpack Compose application for read-only VESC telemetry. The project has a clear feature direction and several strong foundations: VESC packet framing and parsing are isolated, BLE permissions are declared for modern Android versions, Room/DataStore are separated from most UI code, foreground services exist for recording and the anti-distance alarm, and the protocol/math layer has meaningful JVM tests.

The most important risks are not syntax or compilation failures. They are cross-file behavior gaps:

1. The advertised OSMDroid map has no `android.permission.INTERNET`, so online map tiles cannot reliably load.
2. The SPP transport and BLE-to-SPP fallback are present in code but are unreachable: `VescRepository` always starts BLE and never calls its private transport-switch methods.
3. Authentication exposes a PIN/biometric gate, but the implemented expiration method is never called. Once unlocked, the app remains unlocked for the lifetime of the process.
4. Telemetry alert preferences and Room alert entities exist, but no evaluator consumes them. The UI therefore exposes settings that do not produce corresponding alerts.
5. Recording is split across an Android service, a process-global holder, an unstructured IO scope, and asynchronous Room writes. Start/stop/process-death races can lose points or leave sessions incomplete.
6. Release and quality gates are now materially improved: `lintDebug` passes with 59 non-blocking warnings, release shrinking is enabled, and signing is configured through CI/environment variables without committing credentials.

No CRITICAL issue was confirmed from static evidence. The HIGH issues nevertheless block a trustworthy release because they affect a major feature, security expectations, recorded data integrity, or supported hardware compatibility.

The application is not ready for Play Store publication or unattended real-world recording until the remaining HIGH findings are addressed and validated on physical Android devices with BLE, GPS, notification permissions, process death, rotation, and map/network conditions.

# 2. Overall Score

| Area | Score | Rationale |
|---|---:|---|
| Architecture | 6/10 | Reasonable repository/ViewModel separation, but global ServiceLocator, dead transport paths, and mixed responsibilities reduce testability. |
| Code Quality | 6/10 | Readable and documented Kotlin, but duplicated UI, unused paths, unstructured scopes, and lint failures remain. |
| Security | 5/10 | PIN is salted/PBKDF2 and no secrets/cloud backend were found, but relock is not enforced, backups are open, and brute-force throttling is absent. |
| Performance | 6/10 | Telemetry polling is paced and map rendering is simple, but lifecycle-unaware collection and repeated map overlay rebuilding need profiling. |
| Stability | 5/10 | Build succeeds, but recording races, process-death gaps, permissions, and FGS edge cases are not sufficiently hardened. |
| UI/UX | 6/10 | Consistent visual system and localized primary screens, with important unit, background-action, error-state, and offline gaps. |
| Accessibility | 6/10 | Many controls have descriptions and 48dp targets, but no automated semantics/UI audit or TalkBack/device verification exists. |
| Testing | 3/10 | Protocol/math tests are useful; BLE, Room, authentication, services, Compose UI, permissions, and lifecycle are essentially untested. |
| Maintainability | 5/10 | Single module is small enough today, but global state and duplicated orientation code will make feature growth expensive. |
| Release Readiness | 5/10 | Lint passes with non-blocking warnings and release shrinking/CI signing configuration exists; physical release validation, CI workflow, and device matrix remain. |
| **OVERALL** | **5.2/10** | Functional prototype with good foundations, not yet a hardened production release. |

# 3. Project Overview

## 3.1 Inventory

```text
PROJECT: Vesc Companion / VescViewer
└── :app                         Android application module
    ├── src/main
    │   ├── AndroidManifest.xml
    │   ├── java/com/ruggerocadamuro/myapplication
    │   │   ├── MainActivity.kt
    │   │   ├── VescCompanionApp.kt
    │   │   ├── LauncherIcon.kt
    │   │   ├── data
    │   │   │   ├── VescRepository.kt
    │   │   │   ├── ble/BleManager.kt
    │   │   │   ├── ble/SppManager.kt
    │   │   │   ├── database/{RideDao,RideEntities,VescDatabase}.kt
    │   │   │   ├── recording/RideRecorder.kt
    │   │   │   ├── ride/RideAnalytics.kt
    │   │   │   ├── security/{AuthManager,AuthRepository}.kt
    │   │   │   ├── settings/{AppLanguage,SettingsRepository}.kt
    │   │   │   └── vesc/VescPacket.kt
    │   │   ├── service
    │   │   │   ├── AlarmForegroundService.kt
    │   │   │   ├── RideRecordingService.kt
    │   │   │   └── SirenPlayer.kt
    │   │   └── ui
    │   │       ├── auth, components, dashboard, history, map, recording
    │   │       ├── scan, settings, setup, theme
    │   └── res
    │       ├── drawable, mipmap-*, values, values-de, values-en, values-es
    │       └── xml/{backup_rules,data_extraction_rules,locales_config}.xml
    ├── src/test                     3 JVM test files
    └── src/androidTest              1 template instrumentation test
```

There is one Gradle module, no Java source, no native code, no Retrofit/OkHttp/API backend, no WorkManager, no content provider, and no broadcast receiver. Jetpack Compose is used for the UI; Room is used for ride persistence; Preferences DataStore and private SharedPreferences are used for settings/authentication; OSMDroid is used for maps; Android BLE GATT and Bluetooth Classic RFCOMM are used as transports.

The `vescape-dev/` directory exists in the checkout but is ignored by the root `.gitignore` and is not included by `settings.gradle.kts`. It was treated as reference/material outside the Android build, not as an Android module.

## 3.2 Build configuration

- AGP: `9.1.1`
- Gradle wrapper: `9.3.1`
- Kotlin: `2.2.10`
- Compile SDK: 37
- Target SDK: 36
- Minimum SDK: 24
- Java source/target compatibility: 11
- Version: `versionCode 3`, `versionName 1.3.0`
- Compose enabled; Compose BOM is `2024.09.00`
- KSP is used for Room compiler
- Release `isMinifyEnabled = true`, `isShrinkResources = true`; signing is CI/environment controlled
- No product flavors or committed release signing secrets
- No lint baseline, detekt, ktlint, coverage, CI workflow, or dependency-update policy found

## 3.3 Manifest and permissions

Declared permissions cover BLE scan/connect, legacy Bluetooth/location compatibility, location, notifications, foreground-service types, wake lock, battery optimization settings, and OSMDroid network access. Activities and aliases have explicit `exported` values; both services are `exported=false`.

The manifest now declares `android.permission.INTERNET` for OSMDroid/online `MAPNIK` tiles. Backup is enabled and the backup rule files are effectively default/empty. The app declares both `foregroundServiceType="connectedDevice"` and `foregroundServiceType="location"`, which is directionally correct, but runtime behavior and permission failures are not fully tested.

# 4. Architecture Analysis

The effective architecture is:

```text
Compose screens
    ↓
ViewModels
    ↓
ServiceLocator singletons
    ↓
VescRepository / SettingsRepository / Room DAO / AuthRepository
    ↓
BLE GATT, Bluetooth RFCOMM, DataStore, SharedPreferences, Room, LocationManager
```

This is a pragmatic MVVM design, not a fully separated domain/use-case architecture. Telemetry protocol code is commendably isolated in `VescPacket.kt`; the main architectural weaknesses are global object access, application-lifetime scopes, service/UI coordination through process-global state, and feature code that is present without an active caller.

There is no confirmed dependency cycle, but there is strong hidden coupling: `RideRecorder` directly reads `ServiceLocator`, `RideMapViewModel` directly reads the database, and foreground services directly read the same global repository/recorder instances as ViewModels. This makes unit testing and process-recreation reasoning difficult.

# 5. Critical Problems

No CRITICAL issue was confirmed from the inspected source and build evidence. This does not mean the app is production-ready; the HIGH issues below can still cause loss of recorded data, unavailable features, security-policy violations, or unsupported hardware behavior.

# 6. High Priority Problems

### A-001 Map cannot reliably load online tiles because INTERNET is not declared

**Priority:** HIGH  
**Category:** Bug / Configuration  
**Confidence:** HIGH  
**Location:** `app/src/main/AndroidManifest.xml:1-125`; `app/src/main/java/com/ruggerocadamuro/myapplication/ui/map/RideMapScreen.kt:85-91`

**Affected components:**
- `RideMapScreen`
- OSMDroid `MapView`
- Android manifest/network policy

**Problem:** The app creates an OSMDroid `MapView`, selects `TileSourceFactory.MAPNIK`, and configures a user agent. The manifest previously lacked `android.permission.INTERNET`; Phase 1 has now added it.

**Why it matters:** OSMDroid needs network access for tiles that are not already cached. A ride can be recorded successfully and then appear to have a broken map.

**Evidence:** `RideMapScreen` calls `setTileSource(TileSourceFactory.MAPNIK)`; `app/src/main/AndroidManifest.xml` now declares `android.permission.INTERNET`.

**Impact:** Online map tiles now have the required manifest permission. Cache policy, offline messaging, and device/network validation remain tracked by A-024.

**Root cause:** The map feature was added without carrying its network permission into the manifest. **Status:** RESOLVED in Phase 1; the remaining offline/cache work is outside A-001.

**Recommended solution:** Add the INTERNET permission, configure a deliberate OSMDroid tile cache/user-agent policy, and test online, offline, metered, and denied-network conditions. If offline-only behavior is intended, make that explicit and provide a clear empty/offline state.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-024  
**Related issues:** A-032

### A-002 SPP transport and BLE-to-SPP fallback are unreachable

**Priority:** HIGH  
**Category:** Architecture / Bug  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/VescRepository.kt:120-130, 251-263, 306-309`

**Affected components:**
- `VescRepository`
- `SppManager`
- device scan and connection flow

**Problem:** `VescRepository` exposes `spp`, defines `Transport.SPP`, and contains `switchToSpp()`/`switchToBle()`, but `connect()` always sets `_transport` to BLE and calls only `ble.connect(address)`. No code path invokes either switch method. The scanner is also BLE-only.

**Why it matters:** The README and code comments describe support for Bluetooth Classic and automatic fallback, but the shipped flow cannot select or reach it.

**Evidence:** `connect()` at lines 223-235 always uses BLE. Code search found only the declarations of `switchToSpp` and `switchToBle`, not callers.

**Impact:** Classic-only VESC bridges cannot connect or provide telemetry despite appearing supported. Any future SPP state is also not integrated with alarm/polling semantics.

**Root cause:** Transport detection was implemented partially but not connected to the connection state machine.

**Recommended solution:** Choose one explicit design: remove SPP claims/code, or implement a tested transport coordinator that scans paired Classic devices, attempts BLE, falls back to RFCOMM on defined failures, owns one active transport, and exposes transport-aware state.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-003, A-015  
**Related issues:** A-030

### A-003 Alarm monitoring is hard-wired to BLE state/RSSI

**Priority:** HIGH  
**Category:** Bug / Safety behavior  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/VescRepository.kt:455-476`

**Affected components:**
- anti-distance alarm
- `VescRepository.Transport`
- `AlarmForegroundService`

**Problem:** The alarm tick calculates `linkDown` from `ble.state.value` and samples `ble.rssi.value`, even though the repository defines an active transport abstraction and an SPP manager.

**Why it matters:** Alarm behavior must match the actual connection. With SPP, BLE is disconnected and RSSI is unavailable; with an incorrectly activated SPP path this can produce a false link-loss alarm or make monitoring unusable.

**Evidence:** `linkDown` compares `ble.state.value` against `CONNECTED`; `sample` is always `ble.rssi.value`. There is no branch on `_transport`.

**Impact:** False alarm, missing alarm, or misleading UI depending on the active transport.

**Root cause:** The transport abstraction is not used by the alarm evaluator.

**Recommended solution:** Centralize active transport state and expose `activeConnectionState`, `activeRssi`, and `supportsRssi`. Define explicit behavior for transports without RSSI, then unit-test BLE, SPP, disconnect, reconnect, and intentional-disconnect cases.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-002  
**Related issues:** A-021

### A-004 PIN timeout and background relock are never enforced

**Priority:** HIGH  
**Category:** Security / Lifecycle  
**Confidence:** HIGH  
**Location:** `AuthManager.kt:12-40`; `MainActivity.kt:70-91`

**Affected components:**
- `AuthManager`
- `MainActivity`
- `AuthScreen`

**Problem:** `AuthManager` implements `lockIfExpired(timeoutMs)`, but no caller invokes it. `MainActivity` observes `unlocked` and decides whether to show `AuthScreen`, but it has no `onStop`, `onStart`, process-lifecycle observer, or timeout check.

**Why it matters:** The product design and UI imply whole-app authentication, but a user who unlocks the app can leave it in the background and return without re-authentication for as long as the process remains alive.

**Evidence:** Repository-wide search found the only occurrence of `lockIfExpired` in its declaration. No timeout setting exists in `AppSettings` or `SettingsRepository`.

**Impact:** Unauthorized access to telemetry, ride history, map locations, and settings after the device is left unattended.

**Root cause:** Authentication state was implemented as a local utility rather than integrated with Activity/process lifecycle and persisted policy.

**Recommended solution:** Add an explicit lock-timeout setting, observe process lifecycle, record background time using elapsed realtime, lock on timeout and optionally on every background transition, and add a manual lock/logout action. Verify rotation, notification taps, process recreation, and service operation independently.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-013, A-026  
**Related issues:** A-025

### A-005 Telemetry alert settings have no evaluator or delivery path

**Priority:** HIGH  
**Category:** Bug / Missing feature  
**Confidence:** HIGH  
**Location:** `SettingsRepository.kt:50-82, 170-178`; `SettingsScreen.kt:260-294`; `RideEntities.kt:57-68`

**Affected components:**
- `AppSettings` and DataStore setters
- telemetry alert section in Settings
- `AlertRuleEntity`/`AlertRuleDao`
- foreground notification/sound/vibration delivery

**Problem:** The UI exposes low battery, high temperature, high current, and low voltage alert switches and thresholds. The repository persists them, and Room contains a separate `AlertRuleEntity`, but no code evaluates telemetry against those values and no code emits corresponding notifications.

**Why it matters:** Users can enable a safety-related setting that silently does nothing. This is worse than an absent feature because it creates false confidence.

**Evidence:** Search for the alert keys finds only settings declarations/setters and UI usage. `AlertRuleDao` is never used outside its declaration/database exposure. `AlarmForegroundService` handles only the RSSI anti-distance alarm.

**Impact:** Over-temperature, low-voltage, high-current, or low-battery conditions are not reported despite being configured.

**Root cause:** Settings/schema work was completed before the runtime alert engine and delivery channels.

**Recommended solution:** Either remove/label these controls as unavailable, or implement a central alert evaluator with debounce, hysteresis, cooldown, deduplication, foreground/background delivery, notification permission handling, and tests for threshold transitions.

**Estimated difficulty:** HIGH  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-021, A-012  
**Related issues:** A-016

### A-006 Room uses destructive migration fallback for ride data

**Priority:** HIGH  
**Category:** Data integrity  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/database/VescDatabase.kt:6-29`

**Affected components:**
- `VescDatabase`
- ride sessions and points
- future schema versions

**Problem:** The database is version 1 and uses `.fallbackToDestructiveMigration()` with `exportSchema = false`.

**Why it matters:** Any future schema change without a registered migration can drop all recorded rides. For a ride archive, silent data loss is unacceptable.

**Evidence:** The builder explicitly calls `fallbackToDestructiveMigration()` and no migration or schema directory is configured.

**Impact:** Updating the app can erase the complete local ride history.

**Root cause:** The initial prototype prioritized avoiding migration crashes over preserving user data.

**Recommended solution:** Enable schema export, add versioned migrations, test upgrades from every released schema, and use destructive fallback only for explicitly disposable caches—not primary ride data. Consider an export/backup path before migrations.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-013  
**Related issues:** A-016

### A-007 Recording has asynchronous start/stop/location races

**Priority:** HIGH  
**Category:** Stability / Concurrency  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/recording/RideRecorder.kt:34-90, 92-145`

**Affected components:**
- `RideRecorder`
- `RideRecordingService`
- `RecorderHolder`
- Room writes and recording state

**Problem:** `start()` launches an IO coroutine before assigning `session`; `onLocation()` can run immediately from `requestLocation()`; `stop()` returns if `session` is still null. Point insertion is launched separately for every accepted location, and state counters are updated from those child coroutines.

**Why it matters:** The service starts recording and requests a last-known location back-to-back. A fast stop, immediate location callback, or concurrent callback can observe a partially initialized recorder and lose the first point or fail to stop/finalize the session.

**Evidence:** `start()` assigns `session` only after `dao.insertSession`; `onLocation()` exits when `session == null`; `stop()` also exits when `session == null`; `scope.launch` is used for independent point insertions.

**Impact:** Empty or partially recorded rides, stale `active` state, out-of-order persistence, or a session that remains open after the user presses Stop.

**Root cause:** No single serialized recording actor/mutex owns lifecycle, samples, aggregate counters, and database writes.

**Recommended solution:** Use a dedicated recorder coroutine/actor or `Mutex`, make start/stop suspendable commands with acknowledgements, serialize sample processing and persistence, and expose explicit operation/error states to the service/UI.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-016, A-027  
**Related issues:** A-008

### A-008 Session aggregates use cumulative VESC counters and are not reset completely

**Priority:** HIGH  
**Category:** Data correctness  
**Confidence:** HIGH  
**Location:** `RideRecorder.kt:47-53, 106-118, 149-174`

**Affected components:**
- session energy/distance summaries
- `RidePointEntity` and `RideSessionEntity`
- VESC tachometer/Ah/Wh counters

**Problem:** At stop, `wattHours` and `ampHours` are copied directly from the latest controller telemetry. These fields are controller-lifetime cumulative counters, not necessarily session deltas. `lastSavedVescDistanceKm` is not reset in `start()` either.

**Why it matters:** History can report energy consumed before the ride, and distance deltas can be wrong when the controller counter resets, wraps, or changes controller/session.

**Evidence:** `stop()` writes `currentTelemetry?.wattHoursConsumed` and `ampHoursConsumed` directly. `lastSavedVescDistanceKm` is declared at line 177 but is not assigned null in `start()`.

**Impact:** Incorrect Wh, Ah, VESC distance, range/consumption summaries, and misleading historical comparisons.

**Root cause:** Absolute telemetry counters are treated as session aggregates without a captured baseline and reset policy.

**Recommended solution:** Capture counter baselines at session start, calculate monotonic deltas with reset/wrap detection, persist both raw and session values if needed, and reset all per-session state explicitly.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-007  
**Related issues:** A-028

### A-009 Map inspector ignores user-selected units

**Priority:** HIGH  
**Category:** UX / Correctness  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/ui/map/RideMapScreen.kt:252-286`

**Affected components:**
- `PointInspector`
- map metric display
- speed and temperature settings

**Problem:** The point inspector always formats speeds with `R.string.unit_speed_kmh` and temperature with `R.string.unit_temperature_c`. It does not receive `AppSettings` and does not convert values for MPH or Fahrenheit.

**Why it matters:** The same telemetry point is shown with units that contradict the user's selected application settings.

**Evidence:** `InspectorValue` calls use `unit_speed_kmh` for GPS/VESC values and `unit_temperature_c` for MOSFET temperature with no settings input.

**Impact:** Misinterpretation of ride data, especially when reviewing a route rather than the live dashboard.

**Root cause:** Map presentation was implemented independently from dashboard unit formatting.

**Recommended solution:** Pass the relevant settings or a shared formatter into the map screen, convert speed/temperature consistently, and test both unit combinations in map and history views.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-012  
**Related issues:** A-031

### A-010 Debug lint fails with 23 errors and 59 warnings

**Priority:** HIGH  
**Category:** Build / Quality  
**Confidence:** HIGH  
**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/ble/BleManager.kt:140` and generated lint report; `app/build.gradle.kts`

**Affected components:**
- Android lint
- BLE permission checks
- debug quality gate

**Problem:** Before Phase 1, `./gradlew :app:lintDebug` failed with 23 errors and 59 warnings. The first reported error was `MissingPermission` at `BleManager.kt:140` for `dev.name` inside scan handling.

**Why it matters:** The project has no passing static-analysis gate. Lint errors include permission-sensitive Android calls and may identify real crash paths on devices where permissions are revoked.

**Evidence:** The initial command exited with code 1. After Phase 1 fixes for BLE permission handling, API guards, notification channels, Compose resource/content-lambda checks, and Bluetooth status constants, `./gradlew :app:lintDebug` exits successfully with 59 non-blocking warnings and no baseline.

**Impact:** The debug static-analysis gate now passes; the remaining warnings are visible cleanup items rather than hidden errors.

**Root cause:** Runtime permission handling and lint configuration were not completed together. **Status:** RESOLVED in Phase 1 without adding a lint baseline.

**Recommended solution:** Keep lint in the build/CI gate, reduce the remaining warnings deliberately, and preserve local suppressions only where runtime guards or compatibility branches provide the evidence. **Status:** RESOLVED in Phase 1.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-019, A-032  
**Related issues:** A-033

### A-011 Release build is not configured for production distribution

**Priority:** HIGH  
**Category:** Release engineering  
**Confidence:** HIGH  
**Location:** `app/build.gradle.kts:25-33`; `README.md:38-51`

**Affected components:**
- release APK/AAB
- signing and obfuscation
- Play Store/release pipeline

**Problem:** Before Phase 1, release used `isMinifyEnabled = false` and had no signing configuration or environment separation. The README described only a debug-signed personal-testing APK.

**Why it matters:** A successful `assembleRelease` only proves packaging, not production signing, shrinking correctness, mapping retention, privacy configuration, or Play Store readiness.

**Evidence:** Phase 1 enables `isMinifyEnabled = true` and `isShrinkResources = true`; signing is attached only when all four CI/environment variables are present. No keystore credentials are committed.

**Impact:** Release builds have production shrinking configured and cannot accidentally embed repository signing secrets. A signed production artifact still depends on CI configuration.

**Root cause:** The project was configured as a hardware-testing application rather than a release pipeline. **Status:** RESOLVED as Gradle configuration in Phase 1; CI artifact/signing validation remains.

**Recommended solution:** Keep secure CI-managed signing, R8/resource shrinking, AAB generation, mapping/symbol retention, versioning, artifact verification, and a physical-device release smoke test. Never commit signing secrets. **Status:** RESOLVED as Gradle configuration in Phase 1; CI artifact/signing validation remains.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-010, A-012, A-032  
**Related issues:** A-033

### A-012 Core Android flows are largely untested

**Priority:** HIGH  
**Category:** Testing  
**Confidence:** HIGH  
**Location:** `app/src/test/java/...`, `app/src/androidTest/java/...`

**Affected components:**
- BLE/GATT and SPP
- Room and migrations
- authentication/lifecycle
- foreground services/location/notifications
- Compose screens and permissions

**Problem:** JVM tests cover VESC packet framing/parsing and three analytics functions. The only instrumentation test is the generated package-name check. There are no tests for the repository state machine, BLE callbacks, permission denial, Room persistence/migrations, recorder concurrency, alarm debounce, auth relock, services, map selection, or localized UI.

**Why it matters:** The highest-risk behavior is platform and lifecycle dependent and cannot be validated by the current JVM suite.

**Evidence:** Three files exist under `src/test`; one template file exists under `src/androidTest`. Existing tests do not instantiate Room, services, Compose, BLE fakes, or authentication lifecycle flows.

**Impact:** Regressions can compile and pass all current tests while losing rides, failing on Android 12–14 permissions, or bypassing relock.

**Root cause:** Testing focused on pure protocol/math code while the feature surface expanded into Android services and persistence.

**Recommended solution:** Add fake transport tests, repository state-machine tests, Room in-memory tests and migration tests, recorder serialization tests, AuthManager lifecycle tests, service/notification tests, Compose semantics tests, and a real-device matrix.

**Estimated difficulty:** HIGH  
**Estimated impact of fix:** HIGH  
**Dependencies:** All HIGH implementation findings  
**Related issues:** A-007, A-010, A-016, A-019

### A-013 Backup rules can include ride data and authentication metadata

**Priority:** HIGH  
**Category:** Security / Privacy  
**Confidence:** HIGH  
**Location:** `AndroidManifest.xml:49-53`; `res/xml/backup_rules.xml`; `res/xml/data_extraction_rules.xml`; `AuthRepository.kt:15-18`

**Affected components:**
- Android Auto Backup/device transfer
- Room database
- DataStore settings
- private auth SharedPreferences

**Problem:** `android:allowBackup="true"` is enabled while both backup rule files are default/empty. The app stores ride history and a salted PIN verifier in local storage, but no explicit include/exclude policy is defined.

**Why it matters:** Backup/device transfer can copy location history and authentication state to another device or cloud account according to platform/device policy. A salted verifier is not plaintext, but it is still security metadata and restoring it may preserve access policy unexpectedly.

**Evidence:** Manifest enables backup and references both rule files; the files contain no effective exclusions. `AuthRepository` stores `pin_salt` and `pin_hash` in SharedPreferences.

**Impact:** Privacy exposure of ride locations and unexpected authentication state restoration.

**Root cause:** Backup behavior was left at template defaults while sensitive local data was added.

**Recommended solution:** Decide whether ride history should transfer. Explicitly exclude auth preferences and any sensitive location data unless product requirements say otherwise; test cloud backup and device transfer on supported API levels. Document the privacy decision.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-006  
**Related issues:** A-004, A-026

## Phase 1 status — A-001, A-010, A-011

**Updated:** 2026-09-14, before Phase 2.

| ID | Status | Implementation evidence | Residual limitation / next validation |
|---|---|---|---|
| A-001 | **RESOLVED** | Added `android.permission.INTERNET` to `app/src/main/AndroidManifest.xml`; `lintDebug` passes. | OSMDroid cache, offline warning, and online/offline device testing remain part of A-024 and physical validation. |
| A-010 | **RESOLVED** | Fixed the 23 blocking lint errors across BLE permission handling, API guards, notification channels, Compose resources/content lambdas, and Android Bluetooth status constants. `./gradlew :app:lintDebug` now succeeds. | 59 warnings remain; they are tracked as non-blocking cleanup and do not use a lint baseline. |
| A-011 | **RESOLVED — CI CONFIGURATION** | Release now enables R8/resource shrinking and accepts signing values only from `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. No signing secret is stored in the repository. | A real production artifact still requires CI/environment secrets and a physical release smoke test; local builds without those variables remain unsigned by design. |

Phase 1 is complete for these three findings. No Phase 2 implementation was started before recording this status.


## Phase 2 status — A-006, A-007, A-008, A-016, A-027

**Updated:** 2026-09-14. A-006 is intentionally blocked pending explicit approval of a Room migration proposal.

| ID | Status | Implementation evidence | Verification / residual limitation |
|---|---|---|---|
| A-006 | **BLOCKED** | No change applied to `VescDatabase.kt`, schema version, or destructive fallback. | Requires product approval of migration strategy and a migration test before any schema change. |
| A-007 | **FIXED** | `RideRecorder` now serializes lifecycle and location operations through an operation queue plus `Mutex`; point insertion and state updates execute in the same serialized operation. | Regression coverage added for the pure recording counter helper; full Room/concurrency coverage remains planned in A-012. |
| A-008 | **FIXED** | Session baselines are captured for Ah/Wh and VESC distance; deltas handle monotonic progress and controller reset; all per-session fields reset at start/stop. | Counter wrap behavior and real controller semantics require hardware validation. |
| A-016 | **FIXED** | Recording service uses `START_STICKY`, finalizes incomplete sessions on service creation, and recorder checkpoints aggregate values after each persisted point. | Process-kill/restart behavior still requires an Android device test; finalization does not reconstruct volatile recorder state. |
| A-027 | **FIXED** | Recorder uses a `SupervisorJob`, catches session/point/checkpoint persistence failures, exposes `RecordingError.PERSISTENCE`, and keeps the service alive for user-visible notification feedback. | Room failure injection test remains part of A-012. |

**Phase 2 verification command:** `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleRelease`.


**Priority:** MEDIUM  
**Category:** Lifecycle / Memory  
**Confidence:** HIGH  
**Location:** `RideMapScreen.kt:337-345`

**Affected components:** `RideMapViewModel`, Room Flow, map route state.

**Problem:** `load(sessionId)` launches a new `viewModelScope` collector every time it is called. No previous Job is cancelled and the ViewModel does not retain the active session ID.

**Why it matters:** Reopening/changing sessions can leave old database observers running and competing to update the same `_points` state.

**Evidence:** `viewModelScope.launch { observePoints(sessionId).collect { _points.value = it } }` with no Job field/cancellation.

**Impact:** Extra database observation, stale updates, unnecessary work, and incorrect route flashes.

**Root cause:** Loading is modeled as an imperative launch rather than a keyed state flow.

**Recommended solution:** Store/cancel the load Job or expose `sessionId.flatMapLatest { observePoints(it) }`.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** —  
**Related issues:** A-018

### A-015 SPP request ignores failed writes

**Priority:** MEDIUM  
**Category:** Bug  
**Confidence:** HIGH  
**Location:** `SppManager.kt:174-195`

**Affected components:** `SppManager.request`, repository polling.

**Problem:** The `withContext` block returns `false` when output is unavailable or write fails, but that result is not assigned or checked. The method then checks only a string representation of connection state and waits for the response timeout.

**Why it matters:** A write failure becomes an avoidable full timeout and does not produce a precise transport error.

**Evidence:** The return value from `withContext(Dispatchers.IO) { ... true/false }` is discarded before `withTimeoutOrNull(timeoutMs) { deferred.await() }`.

**Impact:** Slow failure, misleading diagnostics, and polling stalls if SPP is later enabled.

**Root cause:** The response waiter was written before write-result propagation was completed.

**Recommended solution:** Store `sent`, return immediately on false, cancel/remove the pending waiter, and surface a typed transport error.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-002  
**Related issues:** A-028

### A-016 Process death does not recover or finalize active recordings

**Priority:** MEDIUM  
**Category:** Stability / Persistence  
**Confidence:** MEDIUM  
**Location:** `RideRecordingService.kt:58-66`; `RideRecorder.kt:55-89, 149-174`; `RideDao.kt`

**Affected components:** location foreground service, `RecorderHolder`, Room sessions.

**Problem:** The recording service returns `START_NOT_STICKY`; the recorder is process-global but has no persisted active-session recovery protocol. An incomplete session remains in Room with `completed=false`, and no startup reconciliation/finalization job was found.

**Why it matters:** Android may kill a process during recording. A foreground service reduces but does not eliminate this possibility.

**Evidence:** The service returns `START_NOT_STICKY`; `RideSessionEntity` supports `completed=false`, but no query or recovery routine handles incomplete sessions.

**Impact:** Lost final aggregate values, orphaned active sessions, or a history row that says “recovered” without actually being finalized.

**Root cause:** Runtime recorder state and durable session state are not reconciled on process/service restart.

**Recommended solution:** Persist recording state/checkpoints, reconcile incomplete sessions at app/service startup, define a crash-safe finalization policy, and test process kill/restart on real devices.

**Estimated difficulty:** HIGH  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-006, A-007  
**Related issues:** A-017

### A-017 Paused recording notification has no Resume action

**Priority:** MEDIUM  
**Category:** UX / Background behavior  
**Confidence:** HIGH  
**Location:** `RideRecordingService.kt:98-117`

**Affected components:** recording notification, `ACTION_PAUSE`, `ACTION_RESUME`.

**Problem:** The notification always contains Pause and Stop actions. It never rebuilds the notification with Resume when the recorder is paused, even though an `ACTION_RESUME` exists.

**Why it matters:** A user who pauses with the screen off cannot resume from the persistent notification—the primary background control surface.

**Evidence:** `notification()` unconditionally calls `.addAction(action(ACTION_PAUSE, ...))`; no paused state is read and no notification update occurs on pause/resume.

**Impact:** Confusing or incomplete background recording control.

**Root cause:** Notification content is static while recorder state is dynamic.

**Recommended solution:** Observe recorder state in the service, rebuild notification actions for active/paused states, and test notification action behavior after service recreation.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-016  
**Related issues:** A-019

### A-018 Compose collection is not lifecycle-aware

**Priority:** MEDIUM  
**Category:** Performance / Lifecycle  
**Confidence:** MEDIUM  
**Location:** `MainActivity.kt`, all screen composables, for example `DashboardScreen.kt:83-96`

**Affected components:** Compose StateFlow collection, BLE telemetry UI, settings/history/map screens.

**Problem:** Screens use `collectAsState()` rather than `collectAsStateWithLifecycle()`.

**Why it matters:** Composition can remain alive while an Activity is stopped or backgrounded. Collecting high-frequency telemetry and database flows outside the started lifecycle can waste work and keep UI subscriptions active unnecessarily.

**Evidence:** Repository search found repeated `collectAsState()` and no `collectAsStateWithLifecycle()`.

**Impact:** Extra recompositions/battery use and less predictable lifecycle behavior.

**Root cause:** Lifecycle-aware runtime-compose collection was added as a dependency but not adopted in screens.

**Recommended solution:** Use lifecycle-aware collection for UI flows and keep service/repository collection independent of UI lifecycle.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** —  
**Related issues:** A-014

### A-019 Permission and foreground-service denial states are inconsistent

**Priority:** MEDIUM  
**Category:** Platform compatibility / UX  
**Confidence:** HIGH  
**Location:** `MainActivity.kt:117-131`; `ScanScreen.kt:88-105`; `RideRecordingService.kt:68-87`; `AlarmForegroundService.kt:50-73`

**Affected components:** BLE/location/notification permission flows, FGS starts.

**Problem:** `MainApp` requests BLE, fine/coarse location, and notification permissions on entry without a dedicated result model. Recording silently returns when location is missing. Alarm/recording service starts can throw platform restrictions or security exceptions that are not converted into user-visible state.

**Why it matters:** Android permission denial, “don’t ask again,” disabled location providers, and background FGS restrictions are normal user paths, not exceptional corner cases.

**Evidence:** MainActivity calls `requestPermissions` and does not process the result; `requestLocation()` simply returns if permissions/providers are unavailable; service start helpers do not catch/report start failures.

**Impact:** Buttons appear to work but nothing records/monitors, or service start fails without explanation.

**Root cause:** Permission acquisition and feature readiness are not modeled as explicit states.

**Recommended solution:** Create permission/feature state models, request only when needed, explain rationale, handle denial permanently, and report FGS start failures in the UI.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-010, A-012  
**Related issues:** A-020

### A-020 Location provider failures and sample quality are silently ignored

**Priority:** MEDIUM  
**Category:** Location / UX  
**Confidence:** HIGH  
**Location:** `RideRecordingService.kt:68-87`; `RideRecorder.kt:92-145`

**Affected components:** `LocationManager`, GPS/network provider selection, ride points.

**Problem:** The service chooses GPS or network provider, catches all exceptions with `runCatching`, and has no state for provider disabled, stale last-known location, unavailable accuracy, or insufficient GPS quality.

**Why it matters:** A ride can appear recorded while points are stale, coarse, inaccurate, or absent. The user receives no indication that route quality is poor.

**Evidence:** `runCatching { requestLocationUpdates ... getLastKnownLocation ... }` discards the result; points store optional accuracy but no quality policy or UI state exists.

**Impact:** Wrong route/distance and false confidence in map and statistics.

**Root cause:** Location delivery was treated as a best-effort callback without a quality/error contract.

**Recommended solution:** Track provider/permission/accuracy status, reject or mark poor samples, expose recording-quality warnings, and use a testable location abstraction. Evaluate fused provider only after verifying the dependency need.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-012, A-019  
**Related issues:** A-024

### A-021 Alarm notification channel is low importance and deliberately silent

**Priority:** MEDIUM  
**Category:** Notification / Safety UX  
**Confidence:** HIGH  
**Location:** `AlarmForegroundService.kt:211-224`

**Affected components:** alarm notification channel, foreground notification, siren.

**Problem:** The channel is created with `IMPORTANCE_LOW` and `setSound(null, null)`. Later setting notification priority to MAX cannot raise the already-created channel's importance.

**Why it matters:** The separate siren may work, but notification visibility/heads-up behavior will be constrained by channel configuration and prior user/channel state.

**Evidence:** `NotificationChannel(CHANNEL_ID, ..., IMPORTANCE_LOW)` and `setSound(null, null)`; alarm notification only changes builder priority/category.

**Impact:** Alarm notification may not alert visibly or audibly as users expect.

**Root cause:** Foreground-monitoring and alarm-delivery requirements share one low-importance channel.

**Recommended solution:** Use separate monitoring and alarm channels with deliberate importance, sound/vibration policy, user documentation, and notification tests. Do not assume builder priority overrides channel policy.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-005  
**Related issues:** A-019

### A-022 Battery percentage is only a generic linear voltage estimate

**Priority:** MEDIUM  
**Category:** Product correctness  
**Confidence:** HIGH  
**Location:** `VescRepository.kt:53-66`

**Affected components:** battery display, low-battery alerts, range estimates.

**Problem:** `batteryPercent()` maps 3.3V/cell to 4.2V/cell linearly without chemistry, load sag, charge state, calibration, or pack profile.

**Why it matters:** Voltage under load is not state of charge. The estimate can be materially wrong, especially near empty/full or with different battery chemistry.

**Evidence:** The function uses one linear formula and the comment explicitly calls it an approximation.

**Impact:** Incorrect battery warnings and autonomy expectations.

**Root cause:** A universal battery model was chosen before a vehicle/battery calibration model existed.

**Recommended solution:** Label the value as an estimate with confidence, smooth/filter it, support configurable chemistry/voltage curves where appropriate, and avoid safety-critical decisions based solely on this estimate.

**Estimated difficulty:** LOW to MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-005  
**Related issues:** A-028

### A-023 Nearest-point selection uses degree-space distance

**Priority:** MEDIUM  
**Category:** Map correctness  
**Confidence:** HIGH  
**Location:** `RideAnalytics.kt:22-31`

**Affected components:** map tap selection, `nearestPointIndex`.

**Problem:** Latitude and longitude deltas are squared and summed directly. A degree of longitude changes physical size with latitude, so this is not a true ground-distance metric.

**Why it matters:** At ordinary local routes the error may be small, but selection can be wrong at high latitudes, wide routes, or sparse samples.

**Evidence:** `latDistance * latDistance + lonDistance * lonDistance` with no latitude scaling or projection.

**Impact:** Tapping a route can inspect a non-nearest sample.

**Root cause:** Simple coordinate-space comparison was used instead of a geographic distance/projection.

**Recommended solution:** Use a local equirectangular approximation for short routes or a geodesic distance; add dense/sparse/high-latitude tests.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-012  
**Related issues:** A-009

### A-024 Offline map behavior promised by the plan is incomplete

**Priority:** MEDIUM  
**Category:** Map / Product gap  
**Confidence:** HIGH  
**Location:** `RideMapScreen.kt:85-91`; `PLAN.md` approved decisions and acceptance criteria

**Affected components:** OSMDroid tile cache, map empty/offline state.

**Problem:** The plan specifies automatic tile cache and an offline warning, but the screen only loads default OSMDroid configuration and renders a map. There is no network reachability check, offline banner, cache policy, or map-specific error state.

**Why it matters:** Recorded rides are explicitly intended to remain useful offline, yet the user cannot distinguish “no network,” “no cached tiles,” and “no route points.”

**Evidence:** `MapEmptyState` handles only `points.isEmpty()`. No offline/network text or connectivity logic appears in the map package.

**Impact:** Blank or confusing map experience in common riding conditions.

**Root cause:** Route rendering was implemented before offline product behavior.

**Recommended solution:** Configure cache limits and storage, detect network/tile failures, show an offline warning, preserve route overlays without tiles, and test a clean offline install.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-001, A-012  
**Related issues:** A-020

### A-025 Navigation state is not saved across recreation

**Priority:** MEDIUM  
**Category:** State management / UX  
**Confidence:** HIGH  
**Location:** `MainActivity.kt:108-114, 142-169`

**Affected components:** tab selection, map session selection, rotation/process recreation.

**Problem:** `tab` and `mapSessionId` use `remember`, not `rememberSaveable` or a navigation state holder.

**Why it matters:** Rotation, locale recreation, and other Activity recreation return users to Dashboard and close the map detail they were viewing.

**Evidence:** `var tab by remember { mutableIntStateOf(...) }` and `var mapSessionId by remember { mutableStateOf<Long?>(null) }`.

**Impact:** Lost navigation context and avoidable user friction.

**Root cause:** The app uses a hand-rolled tab shell without saved-state integration.

**Recommended solution:** Use `rememberSaveable` for small state or a proper navigation/state model with saved-state support; test locale changes and rotation.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-004  
**Related issues:** A-031

### A-026 PIN has no rate limiting and no Keystore-backed protection

**Priority:** MEDIUM  
**Category:** Security  
**Confidence:** HIGH  
**Location:** `AuthRepository.kt:15-51`

**Affected components:** local PIN verification and storage.

**Problem:** The PIN verifier is salted PBKDF2, which is better than plaintext, but repeated verification attempts are unlimited and the repository does not use Android Keystore-backed encryption/metadata despite the broader design language suggesting Keystore protection.

**Why it matters:** An attacker with access to the app process/storage can attempt unlimited PIN guesses. A six-digit PIN has a limited search space.

**Evidence:** `verifyPin()` derives and compares on every call; there is no attempt counter, lockout, delay, or failure persistence. Storage is ordinary private `SharedPreferences`.

**Impact:** Easier offline/automated guessing than a hardened local-auth design.

**Root cause:** Cryptographic hashing was implemented without a complete authentication threat model.

**Recommended solution:** Add bounded backoff/lockout and clear recovery policy; decide whether Keystore wrapping is required for the verifier/metadata. Never claim biometric/PIN protection is device-bound unless verified.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-004, A-013  
**Related issues:** A-035

### A-027 Recorder persistence exceptions can kill its scope silently

**Priority:** MEDIUM  
**Category:** Stability / Error handling  
**Confidence:** HIGH  
**Location:** `RideRecorder.kt:34-38, 121-139`

**Affected components:** recorder IO scope, Room insertions, UI state.

**Problem:** `RideRecorder` creates `CoroutineScope(Dispatchers.IO)` with the default Job and launches database operations without `try/catch` or an error state.

**Why it matters:** A Room/storage exception can cancel the parent scope or leave the recorder without future writes, while the UI still shows recording as active.

**Evidence:** `dao.insertPoint(point)` is inside a child launch with no error handling; `RecordingState` has no failure field.

**Impact:** Silent data loss and stale recording controls.

**Root cause:** Persistence failure is not part of the recorder state machine.

**Recommended solution:** Use `SupervisorJob`, serialize writes, catch/report failures, maintain a bounded retry policy where safe, and expose “recording degraded/failed” to notification and UI.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** HIGH  
**Dependencies:** A-007, A-016  
**Related issues:** A-020

### A-028 Parser/conversion layers trust invariants without broad validation

**Priority:** MEDIUM  
**Category:** Protocol robustness  
**Confidence:** MEDIUM  
**Location:** `VescPacket.kt:236-410`; `VescRepository.kt:27-66`

**Affected components:** packet parser, VESC math, vehicle settings.

**Problem:** Packet parsing handles length/CRC well, but semantic fields are largely accepted without range/finite checks. Conversion functions assume positive pole pairs, gear ratio, and finite telemetry values.

**Why it matters:** A valid CRC does not guarantee a semantically valid frame. Firmware variants, corrupted sensor values, counter resets, NaN/Infinity, or malformed settings can produce nonsensical UI/math.

**Evidence:** `parseSelective`/`parseGetValues` create telemetry from decoded values with limited semantic checks; `speedKmh` divides directly by `p.polePairs * p.gearRatio`.

**Impact:** Spikes, negative/overflowed distances, invalid range, or arithmetic exceptions under unexpected inputs.

**Root cause:** Transport validity and domain validity are treated as the same condition.

**Recommended solution:** Validate finite/range-bounded telemetry, make vehicle parameter types/ranges explicit at the domain boundary, define counter-reset behavior, and add fuzz/property tests for packet inputs.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-012  
**Related issues:** A-008, A-022

# 8. Low Priority Problems

### A-029 Global ServiceLocator weakens dependency boundaries and testability

**Priority:** LOW  
**Category:** Architecture  
**Confidence:** HIGH  
**Location:** `VescCompanionApp.kt:12-29`; multiple ViewModels/services/data classes

**Affected components:** all ViewModels, services, `RideRecorder`, map ViewModel.

**Problem:** Dependencies are obtained from mutable global `lateinit` properties. Components cannot be constructed with fakes without initializing the whole application locator.

**Why it matters:** This makes isolated tests and lifecycle ownership harder and encourages application-global state.

**Evidence:** `ServiceLocator` owns repositories/database/auth manager and consumers read it directly.

**Impact:** More expensive tests, hidden coupling, and initialization-order risk.

**Root cause:** A minimal DI substitute was chosen for a single module.

**Recommended solution:** Introduce constructor injection and a small application container; add a DI library only if the project size justifies it.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-012  
**Related issues:** A-031

### A-030 Dead and unused production paths remain

**Priority:** LOW  
**Category:** Code quality / Dead code  
**Confidence:** HIGH  
**Location:** `VescRepository.kt:251-263`; `SppManager.kt:66,199`; `Color.kt:48,54-55`; UI component files

**Affected components:** SPP fallback, unused helpers/resources, maintenance tooling.

**Problem:** `switchToSpp`/`switchToBle`, `SppManager.hasSelfPermission`, `totalFrames`, several color constants, and legacy/reusable UI functions have no active callers according to repository search.

**Why it matters:** Dead code obscures supported behavior and creates false confidence that a feature is complete.

**Evidence:** Code search found declarations without call sites; SPP is also covered by A-002.

**Impact:** Maintenance confusion and accidental divergence between intended and actual behavior.

**Root cause:** Feature iterations left partial implementations in place.

**Recommended solution:** Remove dead code after confirming product scope, or connect it through tested flows. Use a dead-code tool such as Knip only where applicable to Kotlin/Android equivalents, and review results manually.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** LOW  
**Dependencies:** A-002  
**Related issues:** A-032

### A-031 Portrait and landscape dashboards duplicate presentation logic

**Priority:** LOW  
**Category:** Maintainability  
**Confidence:** HIGH  
**Location:** `ui/dashboard/DashboardScreen.kt`; `ui/dashboard/LandscapeDashboard.kt`

**Affected components:** speed, battery, power, temperature, consumption, unit formatting.

**Problem:** Both screens independently calculate and format many of the same values and construct similar controls/cards.

**Why it matters:** Fixes to units, thresholds, accessibility, or formatting can land in one orientation and not the other, as demonstrated by A-009.

**Evidence:** Both files contain independent speed/battery/temperature/consumption formatting and duplicate recording controls.

**Impact:** Orientation-specific inconsistencies and growing regression surface.

**Root cause:** Layout adaptation was implemented by duplicating feature presentation instead of sharing view data/formatters.

**Recommended solution:** Extract a dashboard UI model and shared metric formatters/components; keep only layout arrangement orientation-specific.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-009  
**Related issues:** A-025

### A-032 No Android CI or repeatable release verification is present

**Priority:** LOW  
**Category:** DevOps / Quality  
**Confidence:** HIGH  
**Location:** repository root and `.gitignore`

**Affected components:** build/release process, regression detection.

**Problem:** No `.github/workflows` under the Android root, lint policy, device matrix, coverage, or automated artifact verification was found. Build output files are ignored/local artifacts rather than a reproducible verification record.

**Why it matters:** The project can regress between local hardware-focused sessions without a shared gate.

**Evidence:** Root inventory contains Gradle files but no Android CI workflow; `build_*.txt` and `test_output.txt` are ignored.

**Impact:** Inconsistent releases and late discovery of lint/build/device failures.

**Root cause:** Verification is currently manual/local.

**Recommended solution:** Add CI for unit tests, lint, debug/release assembly, dependency checks, and optionally emulator/instrumentation tests. Keep secrets external and publish signed artifacts only from protected workflows.

**Estimated difficulty:** MEDIUM  
**Estimated impact of fix:** MEDIUM  
**Dependencies:** A-010, A-011, A-012  
**Related issues:** —

### A-033 Deprecated APIs generate warnings

**Priority:** LOW  
**Category:** Compatibility / Technical debt  
**Confidence:** HIGH  
**Location:** `VescDatabase.kt:25`; `ui/theme/Theme.kt:98-99`; release build output

**Affected components:** Room migration API, window bar colors.

**Problem:** `assembleRelease` reports deprecation warnings for `fallbackToDestructiveMigration()` and `statusBarColor`/`navigationBarColor`.

**Why it matters:** Deprecated APIs can become harder to maintain and may hide intentional compatibility decisions.

**Evidence:** Release build output explicitly reports all three warnings.

**Impact:** Maintenance burden and future API migration work.

**Root cause:** Compatibility code was left on deprecated overloads/properties.

**Recommended solution:** Replace the Room overload with an explicit policy and use modern window insets/system-bar APIs where minSdk behavior permits; retain localized suppressions only when necessary.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** LOW  
**Dependencies:** A-006  
**Related issues:** A-010

### A-034 Documentation does not match current project version

**Priority:** LOW  
**Category:** Documentation / Release  
**Confidence:** HIGH  
**Location:** `README.md:5,39-51`; `app/build.gradle.kts:21-23`; `CHANGELOG.md`

**Affected components:** install/build instructions, release communication.

**Problem:** README says current release `v1.1.0`, while Gradle/CHANGELOG show `1.3.0`. It also says JDK 25 while mentioning a configured JVM toolchain 21, although no toolchain declaration was found in the inspected Gradle files.

**Why it matters:** Contributors can build with the wrong assumptions and users can install/identify the wrong artifact.

**Evidence:** Direct version mismatch between README and Gradle/CHANGELOG.

**Impact:** Confusing release support and inaccurate setup instructions.

**Root cause:** Product/release documentation was not updated after later feature work.

**Recommended solution:** Make README versioned from the release process, document the actual JDK/Gradle requirements, and link the current artifact name.

**Estimated difficulty:** LOW  
**Estimated impact of fix:** LOW  
**Dependencies:** A-011  
**Related issues:** A-032

### A-036 PIN verifier migration to Android Keystore remains open

**Status:** **OPEN — NOT PLANNED**

**Priority:** MEDIUM

**Category:** Security / Technical debt

**Confidence:** MEDIUM

**Location:** `app/src/main/java/com/ruggerocadamuro/myapplication/data/security/AuthRepository.kt`; `AuthManager.kt`; `AuthScreen.kt`

**Problem:** The current PIN verifier uses PBKDF2-HMAC-SHA256 with a random salt in private `SharedPreferences`, but it is not wrapped or bound to an Android Keystore key. This is an explicit future security task; no Keystore implementation is included in the current publication cycle.

**Estimated complexity:** MEDIUM.

**Likely files involved:** `AuthRepository.kt`, `AuthManager.kt`, `AuthScreen.kt`, security unit/instrumentation tests, and potentially a small Keystore adapter under `data/security/`. Backup-rule documentation would need review if the persisted format changes.

**Migration risk:** MEDIUM. Existing PBKDF2 salt/hash records cannot be read by a Keystore-only format unless the app performs a one-time authenticated upgrade after a successful PIN verification. Devices restored without the original Keystore key require a controlled re-enrollment path; the app must not silently accept or discard an unverifiable verifier.

**Decision:** Keep this ID OPEN and **not planned**. Before scheduling it, define device-loss/reset policy, migration UX, supported API behavior, recovery semantics, and Android instrumentation coverage.

# 9. Security Audit

## Findings

- **No hardcoded API keys, access tokens, passwords, private keys, cloud endpoints, or analytics credentials were found** in the inspected Android source/configuration. This is a positive finding, not a guarantee about ignored/local files.
- Bluetooth telemetry is processed locally. No HTTP client, Retrofit API, cloud sync, analytics SDK, or account backend is present.
- The app is explicitly read-only at the protocol layer: no motor-control setpoint command was found in `VescPacket.kt`.
- The PIN is not stored as plaintext. `AuthRepository` uses a random 32-byte salt, PBKDF2-HMAC-SHA256 with 120,000 iterations, and constant-time `MessageDigest.isEqual`.
- A-004, A-013, and A-026 are the material security findings: missing relock enforcement, backup exposure, and no attempt throttling/device-bound metadata.
- SPP uses `createInsecureRfcommSocketToServiceRecord()` (`SppManager.kt:107`). Because the app is read-only, the direct impact is lower than it would be for motor control, but telemetry confidentiality/integrity is not protected by this choice. Treat as a deliberate product decision and document supported hardware/security assumptions.
- Launcher aliases are exported intentionally for launcher behavior. Services are `exported=false`; no exported receiver/provider/deep-link attack surface was found.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared. The settings screen opens generic battery settings, but this permission is sensitive from a platform/policy perspective and should be justified in product documentation.

## Privacy

Ride points contain latitude, longitude, altitude, accuracy, timestamps, and controller telemetry. This is sensitive movement data. The app currently has no export/cloud path, but A-013 means platform backup policy matters. A privacy statement should explain storage, retention, deletion, backup, and any future sharing/export.

# 10. Performance Audit

- BLE polling is paced around 250ms and request timeouts are bounded. That is reasonable for a telemetry prototype, but physical-device profiling is required.
- `VescRepository.pushHistory()` creates a new list and calls `dropWhile` for every telemetry sample. At 4Hz over a 60-second window this is bounded and likely acceptable, but a ring buffer would reduce allocations.
- `RideMapScreen.update` removes and recreates all `Polyline` and `Marker` overlays on every Compose update. For a long ride this can be expensive and should be profiled; route simplification or overlay reuse may be needed.
- `HistoryChart` draws on Canvas and is appropriately lightweight, but it maps and formats point data during recomposition.
- The app has several continuous collectors/scopes: repository singleton collectors, BLE RSSI loop, alarm loop, service collectors, UI collectors, and map collectors. A-014 and A-018 are the most actionable lifecycle/performance concerns.
- `SirenPlayer` generates a 3-second WAV once in cache, which is bounded. It changes the alarm stream volume to maximum and restores it; interruption/process-kill behavior should be device-tested because audio volume is system state.
- No confirmed main-thread database or network call was found. Room suspend operations and SPP I/O use background contexts; BLE callbacks use a main-immediate scope but do not perform large blocking work.

# 11. Stability & Crash Risks

The build compiles, but static evidence identifies platform-sensitive paths:

- MissingPermission lint errors in BLE scan handling.
- Permission/provider denial can make recording a no-op without user feedback.
- Foreground service start restrictions and notification permission behavior are untested.
- Recorder start/stop/persistence races can leave state inconsistent.
- `VescMath` assumes valid non-zero vehicle parameters at the domain boundary.
- `SirenPlayer` catches playback failures and silently leaves the user without a siren.
- `BleManager` catches many `SecurityException`s, which avoids crashes but can turn important failures into silent disconnects.

No evidence was found of a confirmed crash on a real device because no physical-device logs or runtime crash report were available. Confidence for device-specific crash behavior is therefore MEDIUM/LOW until instrumentation is performed.

# 12. Lifecycle Problems

- `MainActivity` has no process lifecycle integration for authentication relock (A-004).
- `RideRecordingService` owns location callbacks but recording state is process-global through `RecorderHolder`; process death and service recreation are not reconciled (A-016).
- `RideMapViewModel.load()` leaks collectors across loads (A-014).
- UI StateFlow collection is not lifecycle-aware (A-018).
- `MapView` calls `onDetach()` on release but no explicit pause/resume integration was found; this should be verified against OSMDroid guidance on Activity pause/resume.
- Repository/BLE/SPP scopes are application-lifetime and have no explicit shutdown. This is acceptable only if they are intentionally application-scoped; it should be documented and tested for duplicate initialization.

# 13. Concurrency Problems

- `RideRecorder` has no mutex/actor around `session`, location state, telemetry state, aggregate counters, and writes (A-007).
- Point insertions are launched independently, so completion order is not guaranteed even though timestamp order is expected by consumers.
- `BleManager` serializes GATT writes with a Mutex, which is a good foundation, but pending responses are keyed only by COMM_ID. This is safe only while the repository guarantees one in-flight request per command ID.
- `SppManager` uses a dedicated raw thread and shared pending map; transport activation is incomplete and write failure propagation is broken (A-002/A-015).
- Mutable fields such as `lastSavedVescDistanceKm`, `commandPath`, and `canId` are accessed from polling/IO contexts without a single owner. Volatile protection is partial.

# 14. Database & Storage

Room schema has sessions, points, and alert rules. Points have a useful `(sessionId, timestampMs)` index. DAO methods provide observation and deletion, but delete methods are not exposed in the current history UI. There is no migration history, exported schema, transaction covering session finalization plus final point, or crash-recovery protocol.

DataStore is the main settings source and uses typed keys with range clamping, which is good. Authentication uses a separate private SharedPreferences store. OSMDroid uses a separate `osmdroid` SharedPreferences file. Backup policy is unresolved (A-013).

# 15. Networking

There is no application API/networking layer. OSMDroid is the only network-capable feature. The map uses MAPNIK tiles but lacks INTERNET permission (A-001), explicit offline behavior (A-024), and a documented tile cache/privacy policy. No authentication token, API key, or custom HTTP client was found.

# 16. UI/UX

Strengths:

- Shared Glass/Liquid Cockpit components and theme tokens are used across major screens.
- Setup, settings, dashboard, scanner, history, map, and authentication have explicit empty/primary states.
- Portrait and landscape layouts are intentionally distinct.
- Primary strings are localized across Italian, English, German, and Spanish resources based on the preceding localization audit.
- Most interactive controls meet or exceed 48dp targets.

Risks/gaps:

- Map unit mismatch (A-009).
- Permission/service failures are not consistently visible (A-019/A-020).
- Offline map state is ambiguous (A-024).
- Paused recording cannot resume from its notification (A-017).
- Navigation context is lost on recreation (A-025).
- Telemetry alert controls imply functionality that does not exist (A-005).
- The dashboard imports/retains reusable components that are not all used, increasing visual drift risk.

# 17. Accessibility

Positive evidence includes content descriptions on navigation and icon-only map controls, semantic roles on custom clickable controls, explicit text labels, and minimum touch sizes in shared components. State is generally communicated with text as well as color.

Not verified:

- TalkBack traversal and announcement of live telemetry updates.
- Font scaling at 200% and layout behavior on small screens.
- Contrast of all translucent/glass combinations in light mode.
- Keyboard/D-pad navigation and focus order.
- Whether custom `clickable`/`toggleable` surfaces expose the same semantics as native controls.
- Accessibility of the Canvas chart and OSMDroid map.

Recommended tests should include Compose semantics assertions plus manual TalkBack checks in portrait/landscape, dark/light, and large-font modes.

# 18. Dependencies

Direct dependencies are coherent with the feature set: AndroidX core/lifecycle/activity, Compose Material 3/icons, DataStore, coroutines, Room/KSP, OSMDroid, and AndroidX Biometric. No clearly unnecessary external networking or analytics dependency was found.

Risks/observations:

- Compose BOM `2024.09.00` is much older than the Kotlin/AGP versions declared in this project. The build currently succeeds, so this is not classified as a bug, but compatibility should be reviewed and versions should be kept on a deliberate support matrix.
- `biometric:1.1.0` is old relative to the rest of the dependency set; verify current compatibility before release.
- OSMDroid has operational requirements (INTERNET, user agent, cache policy) that are not fully configured.
- Room compiler is correctly wired through KSP, but schema export/migrations are absent.

No dependency vulnerability scan was run, so vulnerability status is **not verified** from this audit.

# 19. Build & Release

Baseline executed during this audit:

- `./gradlew :app:assembleDebug` — **PASS**
- `./gradlew :app:testDebugUnitTest` — **PASS**
- `./gradlew :app:lintDebug` — **PASS**, no errors and 59 non-blocking warnings after Phase 1 and Phase 2 fixes.
- `./gradlew :app:testDebugUnitTest` — **PASS**, including `RideRecordingMathTest` regression coverage for session baselines and controller resets.
- `./gradlew :app:assembleRelease` — **PASS**, R8/resource shrinking completed; with no signing environment it remains intentionally unsigned.
- `git diff --check` — **PASS**; only Git line-ending conversion warnings were emitted.

Phase 2 verification was completed for A-007, A-008, A-016, and A-027. A-006 remains blocked pending approval of the Room migration strategy described in the Phase 2 status section.

# 20. Testing

Existing test value is concentrated in `VescPacketTest.kt`: CRC-16/XMODEM, short/long framing behavior, noise/corruption recovery, selective parsing, modern/legacy layouts, CAN wrapping, and parser rejection. `RideAnalyticsTest.kt` covers Wh/km, range, and nearest-point basics.

Highest-value missing tests, in order:

1. `VescRepository` with fake BLE/SPP transports: connect, timeout, reconnect, direct/CAN detection, stale responses, manual disconnect.
2. Alarm state machine: RSSI debounce, link loss, intentional disconnect, return hysteresis, SPP/no-RSSI behavior.
3. `RideRecorder`: serialized start/stop/sample, counter baselines/resets, Room failure, process recovery.
4. Room in-memory persistence and migration tests.
5. Auth relock, attempt throttling, rotation, process recreation, biometric cancellation/fallback.
6. Foreground service permission/notification/action tests.
7. Compose semantics/UI tests for setup, settings, units, map inspector, and notification-driven states.
8. Device tests across API 24, 30, 31/32, 33, 34+, with BLE hardware, notification denial, location denial, rotation, screen-off, and process kill.

# 21. Code Quality

The code is generally readable and heavily commented, especially around BLE and VESC packet behavior. The main quality concerns are:

- many responsibilities in `VescRepository` (transport, polling, parsing orchestration, settings snapshot, events, alarm state);
- global dependency access;
- unstructured application/service scopes;
- duplicated dashboard formatting/layout;
- dead transport and helper code;
- direct `String.format`/formatting scattered across screens rather than shared presentation formatters;
- lint errors and warnings not yet triaged;
- public development reset method in `AuthRepository` that is unused in production.

These are maintainability concerns, not all bugs. They should be corrected in the roadmap after behavior blockers.

# 22. Technical Debt

The following residual debt is explicitly accepted for the current release scope and is **not release-blocking**:

- **A-029 — ServiceLocator migration:** constructor injection covers the recorder and map data source, while some ViewModels/services retain the application container. Complete migration is deferred until a focused architecture pass can preserve lifecycle behavior and testability.
- **A-031 — Dashboard presentation duplication:** portrait and landscape retain separate layout arrangements with shared conversion helpers. Full UI-model/component consolidation is deferred until a dedicated dashboard refactor.

Previously identified debt resolved by Phases 2–8 includes Room migration policy/schema export, durable recording checkpoints/recovery, the telemetry alert engine, BLE-only transport scope, CI/static-analysis gating, release shrinking configuration, and release documentation synchronization.

# 23. Dead Code

Confirmed or strongly indicated unused paths include:

- `VescRepository.switchToSpp()` and `switchToBle()` with no callers.
- `SppManager.hasSelfPermission()` with no callers.
- `SppManager.totalFrames()` with no callers found.
- `AlertRuleDao`/`AlertRuleEntity` with no runtime consumer.
- Some shared UI components and theme constants retained for legacy/possible future use.

Dead code should not be deleted blindly because some components may be intentionally reserved; each item should be either connected, covered by a future issue, or removed after product confirmation.

# 24. Duplicated Code

The largest duplication is between `DashboardScreen.kt` and `LandscapeDashboard.kt`: speed, battery, power, temperature, consumption, units, and recording controls are implemented independently. Similar formatting is repeated in history and map inspector. This duplication already caused A-009 and will make future localization/accessibility changes error-prone.

# 25. Cross-File Problems

### Transport flow

```text
ScanScreen (BLE scan)
  → ScanViewModel.connect()
    → VescRepository.connect()
      → BLE only
        → polling/detection
```

`SppManager` exists beside this flow but is not connected. The documented “automatic detection/fallback” is therefore not an end-to-end behavior (A-002).

### Recording flow

```text
Dashboard / RecordingViewModel
  → RideRecordingService.start()
    → RecorderHolder.recorder()
      → RideRecorder.start() [async Room insert]
      → LocationManager callbacks
        → RideRecorder.onLocation() [async point insert]
      → stop action
        → RideRecorder.stop() [async finalization]
```

The flow has multiple asynchronous boundaries and no serialized command owner. This produces A-007, A-008, A-016, and A-027.

### Authentication flow

```text
MainActivity
  → AuthManager.unlocked
  → AuthScreen biometric/PIN
  → AuthManager.unlock()
```

The reverse flow is missing: no lifecycle event calls `lock()`, and no caller invokes `lockIfExpired()` (A-004).

### Alert flow

```text
Settings UI
  → DataStore alert preferences
  → no evaluator
  → no telemetry alert event/notification
```

Room alert entities are also disconnected from this path (A-005).

### Map flow

```text
HistoryScreen
  → mapSessionId
    → RideMapViewModel.load(sessionId)
      → Room points Flow
        → OSMDroid route + inspector
```

The map has both a missing network permission (A-001) and an un-cancelled collector (A-014); the inspector also bypasses global unit settings (A-009).

# 26. Recommended Improvements

1. Establish a typed domain model for connection, recording, alarm, authentication, and map states.
2. Replace imperative global flows with explicit coordinators/use cases where behavior crosses UI/service boundaries.
3. Introduce shared telemetry formatting and dashboard UI models.
4. Add durable recording checkpoints and migration-safe persistence.
5. Implement or remove SPP based on a confirmed supported-hardware decision.
6. Make all user-facing safety settings truthful: either implement alert delivery or remove inactive controls.
7. Add lifecycle-aware Compose collection and saved navigation state.
8. Add a formal privacy/backup policy for location and authentication data.
9. Make lint, unit tests, release assembly, and artifact signing CI gates.
10. Validate with physical hardware before publishing any release.

# 27. Refactoring Opportunities

- Extract `ConnectionCoordinator` from `VescRepository`.
- Extract `AlarmEvaluator` as a pure state machine.
- Extract `RecordingCoordinator` with a serialized command/sample channel.
- Extract `TelemetryFormatter` that receives `AppSettings` and is shared by dashboard/history/map/landscape.
- Convert `RideMapViewModel` to a keyed Flow/state model.
- Introduce interfaces for `BleTransport`, `SppTransport`, `LocationSource`, and `RideStore` for tests.
- Replace `ServiceLocator` with constructor injection gradually, beginning with pure/domain classes.
- Share metric card components between portrait and landscape without forcing identical layouts.

# 28. Missing Features / Missing Safeguards

The following are either absent or not verified:

- lifecycle-enforced app relock and manual lock;
- telemetry alert evaluation/delivery;
- safe Room migrations and schema export;
- recording recovery after process death;
- counter baseline/reset handling;
- actual SPP fallback or an explicit removal of SPP support;
- map INTERNET permission and offline state;
- release signing/R8/CI;
- rate limiting for PIN attempts;
- export/delete/privacy controls for ride locations;
- physical-device validation matrix;
- accessibility verification at large font sizes and with TalkBack.

# 29. Recommended Fix Order

## Phase 1 — Blockers

- A-001: restore map network capability.
- A-010: resolve lint errors, especially permission-sensitive calls.
- A-011: define release/signing policy before distributing artifacts.

## Phase 2 — Critical behavior and data integrity

- A-007, A-008, A-016, A-027: serialize and harden recording.
- A-006: replace destructive Room fallback with migrations.
- A-002, A-003, A-015: either complete transport support or remove SPP claims.

## Phase 3 — Security

- A-004: enforce lifecycle timeout/manual lock.
- A-013: define and implement backup exclusions.
- A-026: add PIN attempt protection and clarify Keystore/device-binding requirements.

## Phase 4 — Architecture

- A-029: reduce ServiceLocator coupling.
- A-014: fix map collector lifecycle.
- A-031: share dashboard models/formatters.

## Phase 5 — Performance and platform behavior

- A-018: lifecycle-aware UI collection.
- A-019/A-020: explicit permission, provider, and FGS states.
- A-021/A-024: notification channels and offline map behavior.
- Profile long routes and high-frequency telemetry on physical devices.

## Phase 6 — UX/UI and correctness

- A-005: implement or remove telemetry alert controls.
- A-009/A-023: unify map units and geographic selection.
- A-017/A-025: complete background controls and saved navigation state.
- A-022/A-028: communicate battery confidence and validate domain values.

## Phase 7 — Testing

- A-012: add repository, recorder, Room, auth, service, Compose, and device tests.
- Add regression tests for every fixed issue before release.

## Phase 8 — Technical debt and polish

- A-030/A-033/A-034: remove dead paths, address deprecations, and synchronize docs.
- A-032: add CI and release artifact verification.

# 30. Final Roadmap

```text
PHASE 1 — BLOCKERS
↓
A-001, A-010, A-011

PHASE 2 — CRITICAL BUGS / DATA
↓
A-006, A-007, A-008, A-016, A-027

PHASE 3 — SECURITY
↓
A-004, A-013, A-026

PHASE 4 — ARCHITECTURE / SUPPORTED TRANSPORTS
↓
A-002, A-003, A-014, A-029, A-031

PHASE 5 — PERFORMANCE / PLATFORM
↓
A-015, A-018, A-019, A-020, A-021, A-024, A-028

PHASE 6 — UX/UI / CORRECTNESS
↓
A-005, A-009, A-017, A-022, A-023, A-025

PHASE 7 — TESTING
↓
A-012 plus regression tests for all resolved IDs

PHASE 8 — TECHNICAL DEBT
↓
A-030, A-032, A-033, A-034

PHASE 9 — POLISH
↓
Accessibility/device matrix, performance profiling, privacy copy,
release notes, and final physical-device smoke testing.
```


## Phase 4 status — A-002, A-003, A-014, A-029, A-031

| ID | Status | Verification / residual limitation |
|---|---|---|
| A-002 | **FIXED — PRODUCT SCOPE** | SPP code and user-facing fallback claims removed; the repository is BLE-only. Classic-only hardware is unsupported by design. |
| A-003 | **FIXED — PRODUCT SCOPE** | The only active transport is BLE, so alarm state/RSSI no longer has an unreachable SPP branch. Physical disconnect/RSSI validation remains pending. |
| A-014 | **FIXED** | `RideMapViewModel` now cancels the previous collector, ignores duplicate loads for the same session, and accepts an injectable points source. |
| A-029 | **ACCEPTED TECHNICAL DEBT — NON-BLOCKING** | `RideRecorder` and `RideMapViewModel` accept injected persistence/repository dependencies; legacy ViewModels/services still use the application container. The remaining ServiceLocator migration is explicitly deferred by decision, not forgotten. |
| A-031 | **ACCEPTED TECHNICAL DEBT — NON-BLOCKING** | Shared conversion helpers reduce drift; portrait/landscape layouts remain intentionally distinct with some duplicated presentation code. Consolidation is explicitly deferred by decision, not forgotten. |

**Phase 4 verification:** targeted compile and unit tests pass. The complete lint/release gate is recorded below.

The approved Room strategy has now been applied without inventing a schema change: the released schema remains version 1, `exportSchema = true`, the exported fixture is `app/schemas/com.ruggerocadamuro.myapplication.data.database.VescDatabase/1.json`, and `fallbackToDestructiveMigration()` is removed. Because the entity schema and identity hash are unchanged, there is no safe or useful `1→2` DDL migration to run; the next schema change must increment the version and ship an explicit tested migration.

| ID | Status | Implementation evidence | Verification / residual limitation |
|---|---|---|---|
| A-006 | **FIXED — POLICY/HARDENING** | Room exports the version-1 schema and no longer uses destructive migration fallback. The schema fixture is versioned in the repository. | No migration was needed because the schema is unchanged. Future schema changes require a new exported fixture and explicit migration test; existing-device upgrade validation remains required. |
| A-002 | **FIXED — PRODUCT SCOPE** | SPP manager/code path was removed; `VescRepository` is explicitly BLE-only and the UI/claims no longer advertise Classic fallback. | Classic-only hardware is intentionally unsupported. BLE hardware validation remains required. |
| A-003 | **FIXED — PRODUCT SCOPE** | Alarm monitoring is now coherent with the only supported BLE transport; no unreachable SPP transport state remains. | RSSI behavior and disconnect alarm still require physical-device validation. |
| A-004 | **FIXED** | `AuthManager` tracks elapsed background time; `MainActivity.onStop()` records it and `onStart()` enforces relock. Timeout is persisted in settings and applied to the manager. | Rotation, task switching, process recreation, biometric cancellation, and notification entry require device/instrumentation validation. |
| A-013 | **FIXED — PRIVACY POLICY** | Auto Backup and data-extraction rules exclude `vesc_viewer.db` and `vesc_viewer_auth.xml` for cloud backup and device transfer. Non-sensitive settings remain transferable. | Cloud/device-transfer behavior must be verified on supported API levels and OEM backup implementations. |
| A-026 | **FIXED** | PIN verification uses synchronized persistent attempt metadata: five failures trigger a five-minute lockout; earlier failures use progressive backoff capped at 30 seconds; successful verification clears counters. | The verifier remains private SharedPreferences/PBKDF2 rather than Keystore-wrapped metadata; no device-binding claim is made. UI/device timing and recovery behavior remain to validate. |

**Phase 3 verification:** `compileDebugKotlin`, `kspDebugKotlin`, and `testDebugUnitTest` passed after the changes, including `PinRateLimitPolicyTest`. The complete phase gate is recorded below after lint and release assembly.



## Phase 6 status — A-005, A-009, A-017, A-022, A-023, A-025

| ID | Status | Implementation evidence | Verification / residual limitation |
|---|---|---|---|
| A-005 | **FIXED** | Added a pure telemetry evaluator and debounced/cooldown engine for low battery, high MOSFET temperature, high current, and low voltage. VESC repository emits typed alert events, localized UI events, and high-importance notifications. | Notification permission and end-to-end background delivery require physical-device validation; settings thresholds remain intentionally voltage-based for battery percentage. |
| A-009 | **FIXED** | Map inspector now receives `AppSettings` and converts speed and MOSFET temperature to the selected display units. | Unit formatting is covered by shared conversion helpers; Compose rendering still needs UI/device verification. |
| A-017 | **FIXED** | Recording service notification observes recorder state and switches the action between Pause and Resume. | Notification action behavior after service recreation requires device validation. |
| A-022 | **FIXED — ESTIMATE DISCLOSED** | Battery percentage remains a bounded voltage estimate, is rejected for invalid inputs, and the dashboard labels the value as voltage-based rather than presenting it as an exact state of charge. | Chemistry, load sag, calibration, and full/empty voltage curves remain outside the current vehicle settings; safety decisions must not rely on this estimate alone. |
| A-023 | **FIXED** | Nearest-point selection uses a local equirectangular longitude scale based on latitude; a high-latitude regression test was added. | The approximation is intended for local ride routes; very large geographic spans still need a geodesic strategy if product scope expands. |
| A-025 | **FIXED** | Tab and selected map session use `rememberSaveable`, preserving navigation context through Activity recreation. | Rotation/locale recreation needs instrumentation validation. |

**Phase 6 verification:** targeted compilation and JVM tests pass. The complete lint/release gate is recorded below after execution.

## Phase 7 status — A-012 and regression coverage

| Area | Status | Verification / residual limitation |
|---|---|---|
| Pure telemetry/protocol/math | **FIXED / COVERED** | Existing VESC framing/parsing tests plus high-latitude map selection and telemetry alert debounce/evaluator tests pass on JVM. |
| Recording counters and persistence policy | **COVERED PARTIALLY** | Counter baseline/reset regression tests pass; Room failure/process-death behavior remains Android/device work. |
| Authentication | **COVERED PARTIALLY** | PIN rate-limit and timeout-policy tests pass; SharedPreferences, biometric, rotation, and lifecycle integration remain Android/device work. |
| UI, permissions, BLE, services, Room migration | **CHECKLIST REQUIRED** | No emulator/device runner is configured in this checkout; manual/instrumentation validation remains required. |

- `A-012` remains **IN PROGRESS** rather than being falsely marked complete: pure logic regressions are automated, while Room, BLE, service, Compose, permission, biometric, and lifecycle flows need an Android test environment and physical hardware.
- Phase 7 verification: `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleRelease` — **PASS**.

## Phase 8 status — A-030, A-032, A-033, A-034

| ID | Status | Implementation evidence | Verification / residual limitation |
|---|---|---|---|
| A-030 | **FIXED — BLE-ONLY CLEANUP** | Removed the unused SPP production source and stale SPP transport path; removed unused theme constants/imports identified during the pass. | Historical audit evidence still names SPP where it documents the original finding; current source search must remain the source of truth. |
| A-032 | **FIXED — CI GATE** | Added `.github/workflows/android.yml` running lint, JVM unit tests, and the shrinking release assembly with no embedded signing secrets; uploads the unsigned artifact for inspection. | Workflow execution is not available locally and signed release publication remains intentionally outside this workflow. |
| A-033 | **FIXED — LOCAL CLEANUP** | Removed direct deprecated status/navigation bar property assignments; Room destructive fallback was already removed with A-006. | Android/Compose dependency deprecations and runtime API behavior still need review as SDKs evolve. |
| A-034 | **FIXED** | README now identifies version 1.3.0, documents JDK 21, does not claim SPP support, and describes the current release artifact/build policy and privacy behavior. | Release URL/artifact naming remains dependent on the eventual GitHub release process. |

**Phase 8 verification:** source cleanup and README/CI changes are followed by the complete lint/release gate below.

## Phase 9 status — polish and release constraints

- Accessibility copy and content descriptions are present on primary icon-only actions; custom controls expose button/toggle/radio semantics. TalkBack, 200% font scale, keyboard navigation, contrast, and Canvas/OSMDroid semantics still require device verification.
- Profiling scope is documented: measure 4 Hz telemetry collection, notification delivery, GPS recording, and long-route map overlay cost on a physical device before tuning further.
- Privacy copy now states local-only ride storage, excluded ride/PIN backup, no cloud sharing/analytics, and local deletion expectations.
- Release notes include the completed security, data-integrity, BLE-only, alert, map, and testing changes in `CHANGELOG.md`.
- Phase 9 validation is captured in `RELEASE_VALIDATION.md` and the manually executable `RELEASE_VALIDATION_CHECKLIST.md`. Both remain **CHECKLIST REQUIRED** until run on physical hardware; this is not a release-readiness declaration.

## Final implementation status

Phase 2–8 implementation gates pass locally. A-029 and A-031 are explicitly accepted, non-blocking technical debt for this release scope. Remaining release blockers are execution-dependent: physical BLE/GPS/notification/foreground-service/process-death/rotation/network testing, TalkBack and large-font review, long-route profiling, backup behavior verification across supported OEM/API combinations, and CI workflow execution in the repository host.

## Android Keystore migration estimate — not implemented

This is an estimate only; no Keystore code or PIN storage behavior was changed in this closure cycle.

- **Complexity:** MEDIUM. The cryptographic primitive can remain PBKDF2, but key generation, authenticated encryption/wrapping, API/device capability handling, and lifecycle/error states need a focused security implementation and tests.
- **Files likely involved:** `data/security/AuthRepository.kt`, `data/security/AuthManager.kt`, `ui/auth/AuthScreen.kt`, security-focused unit/instrumentation tests, and possibly a small new Keystore adapter under `data/security/`. Backup-rule documentation would also need review if the stored format changes.
- **Migration risk for existing PINs:** MEDIUM. Existing PBKDF2 salt/hash records cannot be read by a new Keystore-only format unless the app performs a one-time authenticated upgrade after a successful PIN verification. Devices restored without the original Keystore key must fall back to a controlled re-enrollment path; the app must never silently accept or discard an unverifiable verifier.
- **Recommendation:** This estimate is tracked explicitly as **A-036 — OPEN, NOT PLANNED**. Do not implement during release closure; define the device-loss/reset policy, migration UX, supported API behavior, and recovery semantics first, then add device/instrumentation coverage.
