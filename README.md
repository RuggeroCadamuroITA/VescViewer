# VescViewer

Android companion app for VESC motor controllers. Connects to a Flipsky FSESC 75200 Pro over Bluetooth Low Energy, reads live telemetry, and provides an RSSI-based anti-distance alarm.

> **Current release:** `v1.3.0` · read-only telemetry companion · Android APK available in [Releases](https://github.com/RuggeroCadamuroITA/VescViewer/releases)

## What it does

- Live dashboard for speed, battery voltage, current, power, duty cycle, MOSFET temperature, energy counters, distance, and ERPM.
- BLE discovery and connection through Nordic UART Service, HM-10-compatible devices, and generic notify/write UART characteristics. Bluetooth Classic/SPP is not supported.
- Automatic detection of direct VESC and BLE-to-CAN transport paths.
- RSSI signal indicator and configurable anti-distance alarm with foreground service and siren.
- Light/dark/system theme, accent palette, analog/digital gauges, km/h or mph, and °C or °F.
- Fully read-only: the app never sends motor control setpoints.

## Supported hardware

The primary tested target is:

- Flipsky FSESC 75200 Pro
- Integrated BLE module
- Firmware 6.02 (`FSESC_75_200_ALU`, `no_hw_limits`)
- Android phone with BLE support

Other VESC-compatible BLE UART bridges may work when they expose a compatible VESC packet endpoint, but they are not guaranteed by this release.

## Install

1. Open the [latest release](https://github.com/RuggeroCadamuroITA/VescViewer/releases/latest).
2. Download the `VescViewer` release APK for the version you want.
3. Allow installation from the browser/file manager when Android asks.
4. Open the app, grant Bluetooth and notification permissions, then scan for the VESC BLE module.

The app is currently distributed as a debug-signed APK for personal testing. Production release signing is intentionally supplied by CI/environment variables and no keystore credentials are committed to the repository.

## First connection

1. Power the VESC and close VESC Tool, nRF Connect, or any other app connected to the same BLE module.
2. Open **Dispositivi** and start a scan.
3. Select the integrated Flipsky BLE device.
4. Return to **Dashboard** and wait for telemetry.
5. Compare voltage and current values with VESC Tool while validating the installation.

Only one BLE client should be connected to the module at a time.

## Build from source

Requirements:

- Android Studio with Android SDK 37.0 installed
- JDK 21 (the Gradle daemon toolchain is pinned to 21)
- Windows: `gradlew.bat`; macOS/Linux: `./gradlew`

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug

# Release APK/AAB (requires a configured Android signing environment for production output)
./gradlew :app:assembleRelease
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Architecture

The app is a single-module Kotlin/Jetpack Compose application using MVVM:

```text
Compose UI → ViewModels → VescRepository
                         ├─ BleManager (BLE GATT, NUS/UART)
                         ├─ VescPacket (framing, CRC, parsing)
                         └─ SettingsRepository (DataStore)
```

The telemetry parser supports the modern VESC `COMM_GET_VALUES` response used by the tested FSESC firmware. The 74-byte payload uses signed/scaled `int16` and `int32` fields rather than IEEE754 floats for voltage, duty, currents, RPM, and energy counters.

## Safety and privacy

- Read-only telemetry: no duty, current, RPM, or other motor-control command is implemented.
- Bluetooth data is processed locally on the phone.
- No account, analytics, cloud service, or API key is required.
- The anti-distance alarm is a convenience feature based on noisy Bluetooth RSSI; it must not be treated as a guaranteed theft-prevention system.
- Never test a motor-control system in an unsafe environment. Validate readings against the VESC Tool and the hardware documentation.

## Known limitations

- The alarm foreground service and siren still need full end-to-end validation on additional Android devices.
- Landscape layout, launcher icon switching, and non-Flipsky bridge variants need broader device testing.
- The release is a hardware-focused milestone; the dashboard visual redesign is included in `v1.3.0` and will continue to evolve with hardware feedback.

## Data and privacy

Ride history contains location, timestamps, and VESC telemetry and is stored locally in the app database. It is not uploaded to a server and is excluded from Android cloud backup and device-transfer rules, as is the local PIN verifier. Settings that do not contain ride or authentication data may be transferred by Android. The app currently provides no cloud sharing or analytics service; delete local rides from the archive when they are no longer needed.

## Release validation

Before publishing, run the device/accessibility/privacy/profiling matrix in [RELEASE_VALIDATION.md](RELEASE_VALIDATION.md). Local lint, tests, and release assembly do not validate BLE hardware, GPS, Android foreground-service restrictions, process death, backup behavior, TalkBack, or OEM battery policies.

VescViewer is released under the [MIT License](LICENSE).

## Release notes

See [CHANGELOG.md](CHANGELOG.md) for the release history.
