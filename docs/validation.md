# Cloud validation — 2026-10-26

## Phase 1 historical baseline

Executed `tools/setup-cloud.sh` successfully in `/workspace/CatGunner-automation`.
This exercised SDK package/license setup and the checked-in Gradle wrapper, then
`assembleDebug testDebugUnitTest lintDebug ktlintCheck`.

- Debug APK: build passed, API 36 target / API 26 minimum.
- JVM controller tests: 12 executed, 12 passed, zero skipped/failures/errors.
- Android lint: passed with warnings treated as errors. Two documented, local
  suppressions cover Intent component matching (a SAM false positive) and the
  API 26–33 Quick Settings compatibility fallback. No lint baseline is used.
- Kotlin style: passed using ktlint's Android profile and 100-column formatting.
- `apksigner verify --verbose`: APK verified with signature scheme v2.
- APK SHA-256: `a9ffded02b8af762960efeb5e8cdd54b61d195235cd55fbde9ed53dca97571f3`.
- Manifest inspection: no Internet or Accessibility permission/service.
- `git diff --check` and shell syntax checks passed.

The JDK archive and Android command-line tools were verified against official
release checksums. Gradle distribution SHA-256 verification is enabled. The
managed machine's Java CA trust is reused; TLS verification remains enabled.

CI configuration is checked in but has not been executed by GitHub Actions here.
Physical-device UI, MediaProjection, notification and Quick Settings behavior
have not been validated in this cloud workspace. Follow `docs/test-plan.md` on
the Pixel 10 before declaring Phase 1's device exit condition met. Later phases
remain blocked on observed game UI/mechanics and permitted spending decisions.

## Phase 2 read-only appearance preview, v0.2.0

Executed `tools/cloud-gradle.sh assembleDebug testDebugUnitTest lintDebug
ktlintCheck` successfully after adding the screenshot profile and spending rule.

- 31 JVM tests executed, zero failures/errors/skips: 15 controller, 9 visual
  replay/negative/scaling, 5 RGBA buffer, and 2 spending-policy tests.
- Both supplied screenshots replay with the expected three upgrade appearances.
- APK build, Android lint (warnings as errors), ktlint, signature verification,
  shell syntax and whitespace checks passed.
- APK SHA-256: `4fc64ace1f95c8e5d62caffc1d7c70a08f6689bfc69abf3e9fdd440ee493cf03`.
- Physical Pixel 10 capture, native rendering, calibration and purchase behavior
  remain untested. There is still no gesture executor or Accessibility Service.
- The color/label checks cannot rule out dialogs elsewhere on the screen and
  must never be used as sufficient authorization for a purchase.

## Calibration and single-purchase build, v0.3.0

Executed `tools/cloud-gradle.sh assembleDebug testDebugUnitTest lintDebug
ktlintCheck` successfully on 2026-10-26.

- 62 JVM tests executed; 0 failures, 0 errors, 0 skipped. Includes 20
  single-purchase tests, 18 controller tests, 4 OCR parser tests, 2 banknote
  fixture tests and 2 frame-freshness tests alongside prior vision/buffer/policy tests.
- APK build, lint (warnings as errors), ktlint, signature verification,
  16 KiB ZIP native-library alignment, shell syntax and whitespace checks passed.
- Merged permissions were inspected: no Internet or network-state permissions.
  The bundled OCR model is included; it has not been executed on Android here.
- Distribution: `dist/CatGunner-AutoPilot-0.3.0-debug.apk`.
- APK SHA-256: `bd5f99b5134b86af570936ca73ddf995c285d1817687b61400edf8fa34f2c936`.
- Owner evidence confirms prior capture startup and notification display on the
  Pixel 10. New native OCR, calibration UI, gesture dispatch and real purchase
  verification remain pending physical execution. No emulator was used.
- Unattended adaptive buying and Rebirth Rush are not implemented. Complete
  the handset checklist before advancing to those milestones.

## Pixel 10 download package

After the owner reported an app-server download timeout for the 45 MiB universal
APK, built standalone ABI splits using `-PsplitApks assembleDebug`. Build, lint
and ktlint passed. `dist/AutoPilot-Pixel10.apk` is 16,779,522 bytes and contains only
arm64-v8a native libraries. Signature and 16 KiB ZIP alignment verify. Its signing
certificate, DEX files and model assets match the prior universal package.
The client download itself cannot be verified from the build environment.

SHA-256: `d986f658a36da8bb0e17752febbfe38a9af47bcdbbbceeb3540754a3b9e0a361`.
