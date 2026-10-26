# CatGunner AutoPilot

Private, on-device Android assistant for Idle Cat Gunner (`com.Chodun.CatGunner`).
Version 0.3.0 implements calibration and an explicitly armed **single-purchase
test**. Adaptive buying and Rebirth Rush remain future milestones. Green banknotes
are the only permitted currency; gems, unknown currencies and real money are forbidden.

## Download on your phone or computer

- **[Download Pixel 10 APK (16.8 MB)](https://github.com/Pietvdberg/CatGunner-automation/raw/refs/heads/main/downloads/AutoPilot-Pixel10.apk)**
- **[Download ZIP with APK and USB-install instructions (9.4 MB)](https://github.com/Pietvdberg/CatGunner-automation/raw/refs/heads/main/downloads/AutoPilot-Pixel10-download.zip)**
- [View installation instructions](downloads/INSTALL.txt) and [SHA-256 checksums](downloads/SHA256SUMS).

These are the same signed 0.3.0 files supplied in the chat. Hosting on GitHub fixes
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
   The app opens the game and arms one request for up to 30 seconds. Keep its normal
   upgrade bar visible. Do not manually buy upgrades during this test.
7. Return through the notification to inspect the outcome and OCR readings.
   **VERIFIED** requires two post-gesture observations of exactly one level increase;
   a gesture callback alone is insufficient. Failed, cancelled and timed-out tests
   never retry or rearm themselves. Each button press is a separate test.

Pause/Stop in the app or notification prevents further gesture dispatch. A gesture
already sent to Android may finish. After Stop, starting capture needs fresh consent.
Quick Settings toggles observation, never arms a purchase. Stop releases projection
resources and is the appropriate control when finished testing.

The accessibility and screen-capture grants must be completed on the phone through
Android; the app cannot grant them itself. Allow notifications for the status and
Pause/Stop actions, or use the in-app and Quick Settings controls.

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
Android 16. The new calibration, OCR and real-game purchase path still require
physical-device validation. The experimental screenshot profile recognizes two
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
