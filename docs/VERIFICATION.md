# Verification and compatibility

## Hardware qualification

The original hardware checks were performed on 2026-10-07 and 2026-10-08 with the non-display Rokid AI Glasses Style, a Galaxy S24 FE on Android 16 / One UI 8, Hi Rokid global `G1.14.20.0921`, and glasses firmware `1.26.009-20260917-151201`.

The exact installed official package was verified as `com.rokid.sprite.global.aiapp`. Battery access was physically tested before implementing the companion. These are observations from that combination, not a manufacturer compatibility statement.

## Observations

| Check | Result |
| --- | --- |
| Battery access through phone-side SDK | Actual percentages and charging state returned on Style |
| Changing battery | Values changed during charging and unplugged operation |
| Disconnect/reconnect | Notification removed and returned after fresh data |
| Notification tap | Opened the official Hi Rokid app |
| Short background / screen-off check | Notification remained; background readings continued |
| Spotify, normal photo capture, Hi Rokid connection | User reported all working normally during tests |
| Home widget | User added it and confirmed its initial appearance; later changed to default 4x1 |
| 30-second refresh | Consecutive requested readings about 30 seconds apart recorded |
| Notification pinning / promotion | Samsung kept it as standard ongoing; top placement not confirmed |
| Lock-screen widget | Not physically qualified |

Raw hardware logs are deliberately not published, because vendor SDK logging can contain tokens and device identifiers.

## Passive callback experiment

A temporary test made one initial `getDeviceInfo()` request and then disabled further requests, retaining the device-status subscription. The initial reading was 86% and charging. No further battery callbacks arrived during roughly four minutes; the user reported 92% in Hi Rokid. Restoring explicit requests immediately returned 94%.

This failed the project's qualification for reliable callback-only battery updates. It does not prove that unsolicited callbacks never occur. The normal app polls every 30 seconds and keeps connection callbacks for prompt state changes.

## Implementation boundaries

The companion uses `IMediaStreamService` and `IDeviceStatusCallback` shipped in CXR-L 1.0.4. Only status callbacks are registered. The lower-level bound-service action and authorization extras match the SDK implementation. No camera/audio/AI/custom-view observers or glasses sessions are opened.

The battery policy accepts only explicit integral 0-100 readings, requires a connected device, clears data on disconnect, requires fresh data on reconnect and expires readings after 90 seconds. The selected device name must match.

## Automated checks

`policyTest` runs the existing plain-Java regression checks for disconnect removal, reconnect freshness, 90-second expiry, invalid values, 0%/100%, charging and repeated connected polls.

The public source export is built and linted separately from the private development workspace. Android lint can report English-only text and ARM64/ChromeOS coverage warnings; warnings do not establish broader device support.

Publication check on 2026-10-20: `policyTest`, `:app:assembleDebug`, `:app:lintDebug` and unsigned `:app:assembleRelease` completed successfully in the clean export. The 37 selected repository files were reviewed for excluded build/private artifacts and credential/device-identifier patterns. Gradle wrapper and distribution checksums were checked against the official distribution service. No APK or signing material is published.

The export changes the setup hint from a particular device name to generic paired-glasses wording, includes only the battery app module, pins the Gradle distribution checksum, and removes local release signing. Battery/status behavior is otherwise the version 1.1.3 implementation.

## Remaining limits

Reboot behavior, prolonged standby/Doze operation, quantified battery drain, other phones and glasses, and later Hi Rokid/firmware updates still need separate qualification. Notification ordering, dismissal and lock-screen presentation remain system-controlled. No new hardware test is implied by publication of this repository.

## References

- [Rokid developer portal](https://open.rokid.com/)
- [Hi Rokid global package](https://play.google.com/store/apps/details?id=com.rokid.sprite.global.aiapp)
- [Android companion device pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing)
- [Android live update notifications](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Android app widgets](https://developer.android.com/develop/ui/views/appwidgets)
