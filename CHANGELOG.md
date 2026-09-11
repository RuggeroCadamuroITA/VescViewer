## [1.1.0] - 2026-09-11

### Changed

- Reworked the dashboard around a live-ride hierarchy: speed, battery, power, live metrics, MOSFET temperature, consumption, alarm, and history are now separated into consistent cards.
- Removed motor temperature from the user interface; MOSFET temperature remains the controller safety indicator.
- Redesigned the BLE device scanner with clearer discovery states and VESC-focused device cards.
- Redesigned settings into grouped sections for appearance, units, vehicle, battery, and security.
- Renamed the app branding to VescViewer.

### Verification

- `:app:testDebugUnitTest` — 18/18 tests passed.
- `:app:assembleDebug` — successful.

[1.1.0]: https://github.com/RuggeroCadamuroITA/VescViewer/releases/tag/v1.1.0


### Added

- BLE scan and connection for VESC UART devices.
- Direct and CAN-forwarded VESC transport detection.
- Live telemetry dashboard for the Flipsky FSESC 75200 Pro.
- Modern VESC 74-byte telemetry parser with signed/scaled fields.
- Configurable RSSI anti-distance alarm with foreground-service support.
- Persistent settings through DataStore.
- Theme, gauge style, unit, launcher icon, and vehicle configuration options.
- APK distribution through GitHub Releases.

### Fixed

- Corrected VESC CRC-16/XMODEM implementation.
- Corrected `COMM_GET_VALUES_SELECTIVE` command ID and response mask parsing.
- Corrected signed 16-bit and scaled 32-bit telemetry decoding.

[1.0.0]: https://github.com/RuggeroCadamuroITA/VescViewer/releases/tag/v1.0.0
