# VescViewer release validation checklist

This checklist is intentionally required before publishing a release. A passing local Gradle build does not replace physical-device validation.

## Device matrix

- Android API 24–30: BLE scan/connect, location permission, recording, background service.
- Android API 31–32: Bluetooth Scan/Connect runtime permissions and screen-off behavior.
- Android API 33: notification permission denied/granted, telemetry alerts, recording notification.
- Android API 34+: foreground-service type restrictions, notification channels, process kill/restart.
- At least one small phone, one large phone/tablet, and one OEM with aggressive battery optimization.

## Required functional scenarios

1. First setup: all steps, locale recreation, theme/units, alert toggles, invalid vehicle values.
2. BLE: scan permission denial, Bluetooth disabled, connection, reconnect, manual disconnect, module unresponsive.
3. Telemetry: direct and BLE-CAN paths, selective/full responses, invalid semantic values, comparison with VESC Tool.
4. Recording: start, immediate location, pause/resume from notification, stop, GPS disabled, stale/poor accuracy, storage error.
5. Process/lifecycle: rotate while on each tab and map, background/foreground before and after PIN timeout, kill process during recording, relaunch and inspect recovered session.
6. Alarm: RSSI debounce, unexpected disconnect, manual disconnect, return-to-range hysteresis, sound/vibration and channel settings.
7. Map: online tiles, offline cached tiles, offline uncached tiles, long route performance, route selection and MPH/°F inspector.
8. Privacy: verify ride database and PIN metadata are excluded from cloud backup/device transfer on supported API/OEM combinations.

## Accessibility and performance

- TalkBack traversal and announcements for navigation, custom switches, map actions, recording state, and alert state.
- 200% font scale in portrait and landscape; no clipped primary action or unreadable metric.
- Light/dark contrast review, keyboard/D-pad focus order, touch targets, and chart/map fallback semantics.
- Profile 4 Hz telemetry, GPS recording, notification updates, and map overlay rendering on a long ride; capture memory and battery impact.

Record device model, Android version, app version, scenario, expected result, actual result, and evidence (log/screenshot/video) for every failure.
