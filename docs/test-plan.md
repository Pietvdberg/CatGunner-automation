# Validation

## Automated gates

Run `./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck`.
The vision suite replays the two supplied upgrade bars, rejects blank/flat-color/
obscured/contradictory/shifted layouts, and checks proportional nearest-neighbor
scaling. Pixel-buffer tests cover row padding, RGBA order, buffer position, crop
offsets, bounds and truncation. These do not validate native-resolution Pixel 10
capture or every possible dialog.

The controller suite exercises initial consent, duplicate start, pause/resume,
capture revocation, old callbacks, explicit stop, and concurrent stop/consent and
stop/resume interleavings. A successful JVM suite does not validate Android UI or
MediaProjection behavior.

## Pixel 10 / Android 16 checklist (pending physical execution)

1. Install the APK and deny notification permission; verify in-app controls remain usable.
2. Cancel capture consent; verify error is recoverable with a new prompt.
3. Approve full-display capture; verify OBSERVING and the system capture indicator.
4. Grant notifications; verify Pause/Resume and Stop match app state.
5. Add the Quick Settings tile; toggle Pause/Resume while the game is visible.
6. Stop, then use a stale notification/tile; verify no capture is silently restarted.
7. Stop capture through Android's system indicator; verify new capture needs consent.
8. Rotate, lock, unlock, switch applications, and force-stop/reopen the app.
   Screen lock must pause. Dimension changes and projection revocation must end
   the current session. Process death must not restore capture.
9. Rotate or recreate the activity during the consent prompt and verify no duplicate session.
10. Disable notifications at the system level; verify tile and app still control capture.

These are read-only tests. Purchase correctness, foreground-game checks, screenshots,
OCR accuracy, detection latency, false-tap counts, thermal/battery distributions,
and rebirth recovery benchmarks require later phases and real game fixtures.

During physical preview testing, return from the game to AutoPilot after sampling
and inspect the latest appearance summary. Check all three available and Power
unavailable; changing aspect ratio or covering labels should produce unknown.
Pause must clear evidence and suppress further sampling. A dialog outside the
upgrade bar may not be detected; no actions are permitted from these results.

## Calibrated one-purchase build

On the Pixel 10, install the update and follow README's numbered workflow. Verify:

1. A recognized game result remains inspectable after returning to AutoPilot and
   after Pause, while live evidence independently becomes unknown/clears.
2. The preview shows the expected bar, each point must be inside its own outlined
   region, and saving incomplete or invalid targets cannot enable purchase tests.
3. Calibration survives app restart; pending purchase tests do not. Game-version
   or dimension changes block old calibration.
4. With no test armed, viewing the game never produces a tap. Tile/Resume cannot
   arm a purchase. An unavailable target cannot dispatch.
5. Arm exactly one affordable upgrade. Observe one discrete tap, then return and
   inspect VERIFIED only if two frames showed a one-level increase. Confirm the
   game independently; the notification must not call a callback-only result verified.
6. Pause, stop, lock, switch apps, revoke accessibility or stop system capture
   while a request is pending. No additional gesture may dispatch. An in-flight
   gesture can finish and must not be silently retried.
7. Obscure controls, change currency icon, rotate, show native dialogs, and test
   game-drawn dialogs. Unknown evidence must refuse a new action. Custom dialogs
   outside sampled regions are an unresolved classifier limitation; do not enable
   unattended operation on the basis of this prototype.
8. In airplane mode verify bundled OCR can read known normal-game values. Confirm
   OCR diagnostics reflect actual levels/costs before relying on verification.
9. Inspect app-private purchase-receipts.json through an authorized local debug
   workflow: ids correlate preconditions and outcomes; interrupted requests stay
   unverified. No raw screenshots/video should be written.

Cloud unit tests cover one-use dispatch, stable/fresh preconditions, banknote icons,
version/dimension mismatch, missing values, false availability, timeout, late
callbacks, verification rules and epoch leases. They do not execute real gestures
or the bundled native OCR model on a phone.
