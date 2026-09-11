# Changelog

All notable changes to VescViewer are documented here.

## [1.0.0] - 2026-09-11

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
