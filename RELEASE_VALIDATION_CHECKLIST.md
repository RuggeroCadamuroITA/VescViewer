# VescViewer — Release validation checklist

Complete this checklist manually on physical hardware before publication. Record device model, Android version, app version, expected result, actual result, and evidence for every failure.

## BLE

- [ ] API 24–30: scan and connect to the supported BLE VESC module.
- [ ] API 31–32: grant/deny Bluetooth Scan and Connect permissions and verify the resulting UI state.
- [ ] Scan permission denial is explained and does not leave a misleading loading state.
- [ ] Bluetooth disabled state is reported and recovery after enabling Bluetooth works.
- [ ] Reconnect after a temporary disconnect works.
- [ ] Manual disconnect does not trigger a false anti-distance alarm.
- [ ] An unresponsive module produces the documented diagnostic event.
- [ ] Direct VESC telemetry path works.
- [ ] BLE-to-CAN telemetry path works.
- [ ] Selective and full telemetry responses produce valid values.
- [ ] Telemetry values match the reference tool for the same controller state.
- [ ] Invalid semantic telemetry values are rejected or safely ignored.
- [ ] Bluetooth Classic/SPP-only hardware is correctly treated as unsupported.

## GPS

- [ ] Fine/coarse location permission grant path works.
- [ ] Location permission denial is visible and does not pretend that GPS points are being recorded.
- [ ] GPS provider disabled state is visible.
- [ ] Network location fallback behaves as documented when GPS is unavailable.
- [ ] Fresh location samples are recorded with expected timestamp and accuracy.
- [ ] Stale or poor-accuracy samples produce the documented recording-quality state.
- [ ] No-provider/unavailable-location state is visible to the user.

## Recording / servizio

- [ ] API 24–30: recording starts and continues with the app in the background.
- [ ] Recording saves the first available location sample immediately after start.
- [ ] Start, immediate stop, and rapid start/stop sequences finalize consistently.
- [ ] Pause from the UI stops accepting new ride points.
- [ ] Resume from the UI continues the same session.
- [ ] Pause from the persistent notification works.
- [ ] Resume from the persistent notification works.
- [ ] Stop from the persistent notification finalizes the session.
- [ ] GPS disabled during recording produces a visible state without crashing.
- [ ] Storage/database failure produces a visible degraded/error state.
- [ ] Recorded distance, Ah, Wh, and VESC distance are correct for a known ride.
- [ ] Controller counter reset during a ride does not create an invalid negative delta.
- [ ] Rotate while recording and verify recording state and saved points remain coherent.
- [ ] Kill the process during recording, relaunch the app, and inspect the recovered/incomplete session.
- [ ] Android 34+ foreground-service restrictions allow the documented recording flow or show a clear failure.

## PIN / security

- [ ] First setup requires a valid PIN confirmation.
- [ ] Invalid PIN format and mismatched confirmation are rejected.
- [ ] Correct PIN unlocks the app.
- [ ] Wrong PIN shows an error without revealing authentication metadata.
- [ ] Five failed attempts trigger the configured lockout.
- [ ] Progressive retry backoff is observable and capped at 30 seconds before lockout.
- [ ] Five-minute lockout expires and permits a new verification attempt.
- [ ] Background/foreground transition before timeout preserves access as configured.
- [ ] Background/foreground transition after timeout relocks the app.
- [ ] Task switching and notification entry enforce the configured relock policy.
- [ ] Biometric success unlocks the app.
- [ ] Biometric cancellation/failure falls back to the PIN path as documented.
- [ ] Rotation and process recreation preserve the intended authentication state.
- [ ] The app does not claim that the PBKDF2 verifier is Android-Keystore device-bound.

## Notifiche / allarme

- [ ] API 33: granting notification permission shows recording, monitoring, and telemetry notifications.
- [ ] API 33: denying notification permission produces the documented degraded behavior.
- [ ] API 34+: foreground-service notification and service-type restrictions behave as documented.
- [ ] RSSI debounce triggers the anti-distance alarm only after the configured confirmation interval.
- [ ] Unexpected BLE disconnect triggers the configured alarm behavior.
- [ ] Intentional/manual disconnect does not trigger the alarm.
- [ ] Return-to-range hysteresis clears the alarm as configured.
- [ ] Monitoring notification uses the monitoring channel.
- [ ] Active alarm notification uses the high-importance alarm channel.
- [ ] Sound and vibration match the channel settings and user expectations.
- [ ] Siren starts, stops, and restores audio behavior correctly after alarm recovery or service destruction.
- [ ] Telemetry low-battery alert is emitted when its threshold is crossed.
- [ ] Telemetry high-temperature alert is emitted when its threshold is crossed.
- [ ] Telemetry high-current alert is emitted when its threshold is crossed.
- [ ] Telemetry low-voltage alert is emitted when its threshold is crossed.
- [ ] Alert debounce/cooldown prevents repeated notification spam.

## Mappe / offline

- [ ] Online OpenStreetMap tiles load with a supported network connection.
- [ ] Offline cached tiles remain usable.
- [ ] Offline uncached tiles show the offline indication without hiding the recorded route.
- [ ] A session with no GPS points shows the empty-map state.
- [ ] A long route remains usable without unacceptable scrolling, memory, or rendering degradation.
- [ ] Selecting a route/session opens the correct points.
- [ ] Tapping the route selects the nearest recorded point.
- [ ] Point inspector displays the selected MPH/°F or km/h/°C units consistently.
- [ ] Map state survives rotation and locale recreation.
- [ ] Map actions have a usable fallback when tiles are unavailable.

## Accessibilità

- [ ] TalkBack traversal and announcements work for navigation.
- [ ] TalkBack exposes custom switches, radio controls, and alert state correctly.
- [ ] TalkBack exposes map actions and recording actions with meaningful labels.
- [ ] Live telemetry updates do not create unusable or excessively repetitive announcements.
- [ ] Font scale at 200% works in portrait without clipping primary actions or metrics.
- [ ] Font scale at 200% works in landscape without clipping primary actions or metrics.
- [ ] Light-theme glass surfaces meet readable contrast expectations.
- [ ] Dark-theme glass surfaces meet readable contrast expectations.
- [ ] Keyboard/D-pad focus order is complete and understandable.
- [ ] Touch targets meet the intended minimum size.
- [ ] The Canvas chart has an understandable fallback/semantic description.
- [ ] The OSMDroid map has an understandable action/fallback experience for non-visual users.

## Profiling

- [ ] Profile 4 Hz telemetry collection during a representative ride.
- [ ] Profile GPS recording and Room writes during a representative ride.
- [ ] Profile notification updates while recording and monitoring are active.
- [ ] Profile map overlay rendering with a long route.
- [ ] Capture memory impact and look for growth over a long ride.
- [ ] Capture battery impact with telemetry, GPS, notifications, and screen-off operation.
- [ ] Review small phone, large phone/tablet, portrait, and landscape performance.

## Backup / privacy

- [ ] Verify the ride database is excluded from Android cloud backup.
- [ ] Verify the ride database is excluded from Android device transfer.
- [ ] Verify PIN metadata is excluded from Android cloud backup.
- [ ] Verify PIN metadata is excluded from Android device transfer.
- [ ] Verify non-sensitive settings transfer according to the documented policy.
- [ ] Verify ride history, location, timestamps, and VESC telemetry remain local-only.
- [ ] Verify the app presents no undocumented cloud sharing or analytics behavior.
- [ ] Verify local ride deletion behaves as documented.

## Device OEM

- [ ] Validate on at least one small phone.
- [ ] Validate on at least one large phone or tablet.
- [ ] Validate on at least one OEM with aggressive battery optimization.
- [ ] Confirm the OEM does not silently stop BLE polling, GPS recording, notification delivery, or either foreground service.
- [ ] Confirm behavior after screen-off, extended background time, and task removal on the aggressive-battery OEM.
- [ ] Confirm battery-optimization guidance and the documented limitation are accurate on that OEM.

## Validation record

For each failed item, record:

- Device model and OEM
- Android/API level
- App version and build variant
- Scenario and expected result
- Actual result
- Log, screenshot, or video evidence
- Issue/reference ID and retest date
