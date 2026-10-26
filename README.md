# CatGunner AutoPilot

Private, on-device Android assistant for Idle Cat Gunner (`com.Chodun.CatGunner`).
Version 0.4.1 adds **bounded automatic purchasing** on top of the phone-validated
single-purchase engine. Each run has a 1/3/5-purchase cap, with one verified action
at a time. Expected-gain optimization and Rebirth Rush remain future milestones. Green banknotes
are the only permitted currency; gems, unknown currencies and real money are forbidden.

## Download on your phone or computer

- **[Download Pixel 10 APK (17.2 MB)](https://github.com/Pietvdberg/CatGunner-automation/raw/refs/tags/v0.4.1/downloads/AutoPilot-Pixel10-0.4.1.apk)**
- **[Download ZIP with APK and USB-install instructions (9.5 MB)](https://github.com/Pietvdberg/CatGunner-automation/raw/refs/tags/v0.4.1/downloads/AutoPilot-Pixel10-download.zip)**
- [View installation instructions](downloads/INSTALL.txt) and [SHA-256 checksums](downloads/SHA256SUMS).

These are signed 0.4.1 builds. Hosting on GitHub fixes
file access; it does not remove Play Protect's installation block. A work computer
can download the files without installing software. USB/ADB installation requires
a computer on which you are allowed to run Android Platform-Tools.

## On your Pixel 10

1. Install the new APK over the previous version.
2. Open AutoPilot and select **Enable AutoPilot accessibility service**. Enable
   its single-purchase service in Android settings. For a sideloaded app Android
   may first require **Allow restricted settings** from the app's information page.
3. Return, select **Start screen capture**, and complete Android's capture prompt.
   Open the game and leave the upgrade bar visible for a few seconds.
4. Return to AutoPilot. Its **retained game result** survives app switching and
   shows the age and native dimensions of the last recognized frame. Live evidence
   will usually say unknown while AutoPilot itself is visible; this is expected.
5. Select **View last game frame and calibrate**. Capture pauses. Select Power,
   Speed and Money in turn, tapping each outlined inner area in the preview. Check
   that the pictured controls use green banknotes, then save all three targets.
6. Return and press **Resume**. Choose **Test ONE Power purchase** (or Speed/Money).
   The app opens the game and arms one request for up to 30 seconds. While opening,
   unknown layout/OCR frames suspend the request without tapping; two fresh valid
   readings must then arrive. The original deadline is never extended. Keep its normal
   upgrade bar visible. Do not manually buy upgrades during this test.
7. After the tap, leave the game visible for about ten seconds, then return through
   the notification to inspect the outcome and OCR readings.
   **VERIFIED** requires two post-gesture observations of exactly one level increase;
   a gesture callback alone is insufficient. FAILED/CANCELLED before dispatch means
   no tap was sent. If a tap may have happened but verification cannot complete, the
   result is UNCONFIRMED. Inspect the game, then use the explicit review button before
   arming any new purchase. No outcome retries or rearms itself.

Pause/Stop in the app or notification prevents further gesture dispatch. A gesture
already sent to Android may finish. After Stop, starting capture needs fresh consent.
Quick Settings toggles observation, never arms a purchase. Stop releases projection
resources and is the appropriate control when finished testing.

The accessibility and screen-capture grants must be completed on the phone through
Android; the app cannot grant them itself. Allow notifications for the status and
Pause/Stop actions, or use the in-app and Quick Settings controls.

## Bounded automatic run (0.4.0)

The owner validated single purchases for Power, Speed and Money with VERIFIED 2/2
outcomes on Pixel 10; see [phone evidence](docs/phone-validation.md). The new run
mode still needs phone validation. Existing calibration is retained.

1. Enable accessibility, start/resume capture and leave your calibration in place.
2. In **Bounded automatic run**, choose a maximum of **1, 3 or 5 purchases**. The
   default is 3. Choose **Rotate Power → Speed → Money** for a first deterministic
   run across available controls, or **Lowest comparable displayed price**.
3. Press **Start bounded automatic run**. The app opens the game. Keep its normal
   upgrade bar visible while the run finishes; do not manually buy upgrades.
4. Keep the game visible until the **Automatic run complete** or **Automatic run
   stopped** outcome notification appears (notification alerts must be enabled).
   Opening AutoPilot or the notification shade before completion interrupts the
   foreground-only run; do not use a fixed 30-second wait as a completion signal.
   Every purchase must reach VERIFIED before the next can be armed. The run ends
   at its cap or earlier on a failure, unconfirmed action, lack of eligible targets,
   incompatible units, game interruption, or readiness timeout. It never retries
   a failed action. The status shows verified progress against the cap.
5. Pause/Stop, accessibility loss or returning to AutoPilot stops the run. Resume
   only resumes observation; starting another run always needs its own button press.
   An unconfirmed tap still requires game review before any new purchase can arm.

The lowest-price rule compares exact decimal values **only when suffixes match**.
It stops on mixed or case-different suffixes because their scales are unverified.
Rotation avoids price comparisons and skips unavailable controls. These are
transparent baseline rules, not learned DPS or longer-horizon optimizers. There
is no currency-amount budget/reserve policy yet; the hard bound is purchase count.
No run or pending purchase automatically restores after process death.

## Build

Use JDK 17 or 21, Android SDK Platform 36, Build Tools 35.0.0 and the checked-in
Gradle 8.13 wrapper. Set `ANDROID_HOME` or Android Studio's `local.properties`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck
```

In the managed cloud environment run `tools/setup-cloud.sh` once, then use
`tools/cloud-gradle.sh` for the same Gradle tasks. The debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. Install using the phone's sideloading
flow or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The bundled ML Kit OCR model makes the APK larger; it needs no runtime model
download. The merged APK explicitly excludes Internet and network-state permissions.
No release signing key is stored in the repository.

## Current limits

The owner confirmed capture startup and its notification on the Pixel 10 running
Android 16. The owner has validated single purchases for all three upgrades. The new bounded
run and interruption paths still require physical-device validation. The experimental screenshot profile recognizes two
supplied examples; it is not a complete classifier for every in-game dialog.
Unknown controls or OCR failures stop a test. Native/system dialogs, app switching,
lock and competing windows are guarded; custom game-drawn dialogs require further
corpus testing. Unattended operation is not enabled.

Only one scaled preview is retained in memory for explicit calibration; no raw
screenshot or video is saved. Calibration geometry and up to 200 receipt events
are stored locally using atomic files. Clearing app data removes them. Purchase
requests and capture tokens never restore after process death.

See [calibration](docs/calibration.md), [architecture](docs/architecture.md),
[test plan](docs/test-plan.md), [limits](docs/known-limitations.md),
[vision profile](docs/vision-profile.md) and [roadmap](docs/development-plan.md).
Kotlin formatting follows the Android guide and Kotlin conventions, enforced by ktlint.

For a smaller standalone Pixel 10 download, build ABI splits using
`tools/cloud-gradle.sh -PsplitApks assembleDebug`. Install only
`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`; it contains the same app
and bundled OCR model as the universal package. The distribution copy is
`dist/AutoPilot-Pixel10.apk`. Default builds still support all four ABIs.

## 0.3.1 startup fix and phone diagnostics

Before the first gesture request, temporary startup frames or cold OCR now wait
within the original 30-second deadline rather than immediately failing. An invalid
frame clears any partial precondition; two new valid readings are required.
After a gesture request, uncertainty still fails immediately and never retries.
Returning to AutoPilot cancels any active test before another tap can dispatch.

Accessibility status now distinguishes Connected, Disabled in Android, and Enabled
but disconnected. Follow the displayed setting instructions until Connected.
The app also retains the last game detector scores, exact level/cost OCR fields
and task error codes. **Copy test diagnostics** places this local text on your
clipboard only when pressed; paste it into the chat if a new test fails. No image
or diagnostic text is uploaded automatically.

## 0.3.2 observed OCR prefix fix

The phone reported `Lvo166` and `Lvo167` in the exact level ROI. The recognizer
can render the period after `Lv` as `o`. This fixed-prefix separator is now accepted
while all numeric digits remain strict. Letters inside the number, missing digits,
fractional levels and unrelated labels remain invalid. Two fresh stable readings
and the existing currency/layout/gesture guards are still required. Timeouts now
retain their last readiness reason rather than only reporting a generic expiry.

## 0.3.3 post-purchase verification

Owner testing observed real upgrades despite FAILED reports. A short-lived Power
label animation or a malformed `Lva68` OCR result was ending verification before
subsequent valid `Lvo168`/`Lvo169` readings arrived. Unreadable digits are still
rejected; they are never reconstructed. After an actual tap, visual/OCR uncertainty
now suspends verification within one fixed eight-second window, without another
gesture. Two distinct fresh-enough post-completion frames must show exactly one
selected level increase, matching cost text and unchanged other upgrade levels.

Pre-dispatch freshness remains 750 ms. Post-action OCR observations may be up to
two seconds old to accommodate the observed 866 ms processing delay; they must
still have been captured after the callback. This allowance can never authorize
a tap. Losing foreground/accessibility, pause or stop ends an unverified sent
action as UNCONFIRMED. Only user review permits a new test, without marking the
prior purchase automatically verified. The app records whether the actual gesture
call was attempted/accepted separately from the queued request.

## 0.4.1 pre-send preparation and outcome alerts

A queued request is not an actual tap. A temporary stale OCR/frame or label miss
before any send now returns to waiting for two new fresh readings within the
original readiness deadline. Every preparation has a token, so a late task or
callback from an older preparation cannot execute or cancel the newer one. An
actual accepted tap cannot return to this pre-send waiting path. Refusal, currency
mismatch, changed calibration, missing foreground and session changes still stop
the request. There is no retry of a rejected or possibly accepted gesture.

Copy diagnostics now includes the exact final dispatch reason, ages, target and
banknote check. Receipt-storage failures and actual gesture callbacks are logged
separately. Foreground stops retain the observed status instead of only a generic
message. A separate outcome notification alerts only after a run completes or
stops, so you can keep the game visible while it is active. Android notification
settings and Do Not Disturb can silence alerts.
