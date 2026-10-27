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

## 0.3.1 startup waiting and diagnostic regression

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 73 tests executed, with zero failures/errors/skips.
New tests cover pre-dispatch waiting without extending the deadline, clearing
partial preconditions, rejecting uncertainty after a request, preventing terminal
request revival, exact OCR token extraction/diagnostics, and service connection
identity during late teardown.

Owner evidence from 0.3.0 showed all three available at 1080×2424, no complete OCR
reading, a failed unknown-frame test, and a subsequently unavailable accessibility
service. The code confirms a startup frame could fail the request immediately.
The screenshot does not establish the cause of Android's service disconnection.
0.3.1 corrects the startup path, guards stale instance callbacks, distinguishes
Android's enabled setting from a real connection, and provides local copyable
diagnostics. Phone revalidation remains outstanding; no successful real purchase
or native OCR accuracy is claimed by these cloud tests.

APK SHA-256: `e03d40afb8c961fb4e6f668574e8438e2bd21b40cdbf60ce14e29d8ca72a2608`.

## 0.3.2 observed level-prefix regression

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck` successfully. 77 tests executed, zero failures/errors/skips.
New regression cases use the owner's exact `Lvo166`/`8h`, `Lvo167`/`11h`,
`LV165`/`5.79h` transcript and native 1080×2424 profile through OCR extraction
and two stable engine readings. The fixed-prefix separator now parses; numeric
ambiguity remains rejected. Timeout tests confirm the last blocking reason is
retained. Actual phone purchase success is still not claimed.

APK SHA-256: `dc29887718e85b8569c8a61ab7c093922b41ddef77871d480ad55618c31a95c3`.

## 0.3.3 post-action verification and uncertain outcomes

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 88 tests executed, zero failures/errors/skips.
Regression tests cover temporary bad OCR/label reads after one accepted gesture,
the observed 866 ms post-OCR delay, an unextended eight-second verification
deadline, no second actual framework call, uncertainty/review blocking, queued
versus actually sent cancellation, refusal versus exception, pre-callback image
rejection, and retaining single post-observations without claiming VERIFIED.

Owner testing observed two real Power upgrade effects despite FAILED UI reports;
screenshots showed later values 168 and 169 alongside transient `Lva68`/Power
label mismatch. This confirms the importance of separating action delivery and
verification. The new automatic verification behavior still needs phone testing.
Pre-tap age remains 750 ms; post-tap observation allowance is two seconds and
can never authorize any gesture.

APK SHA-256: `68b09d8764f2f8beb41ff13155d90e0f387a93a89163cdbdfce71990706ddbe6`.

## 0.4.0 bounded automatic purchasing

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 112 tests executed with zero failures/errors/skips.
New selector tests cover observed decimal prices, matching suffix requirements,
incomplete/unaffordable input and explicit rotation. Orchestration tests use the
real single-purchase engine to verify cap enforcement, request correlation, no
implicit startup, no next action before verification, no double-counting, prior
manual-result exclusion, unconfirmed/failure stops, profile/lease changes, missing
readiness timeout, foreground loss and accurate counts during stop.

The owner has validated all three one-purchase controls with VERIFIED 2/2 phone
screenshots; see phone-validation.md. The new orchestration layer still requires
handset validation. No expected-gain optimization, suffix-scale inference, reserve
policy, fast batch or Rebirth Rush performance is claimed.

APK SHA-256: `52bfb68a4e926aca473ba3040b50555770d038e1969be16723e58d8401e163b2`.

## 0.4.1 final-gate preparation regression and run outcomes

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 123 tests executed, zero failures/errors/skips.
New tests classify transient versus hard dispatch gates; require two new readings
after unsent deferral; block stale jobs/callbacks by preparation token; retain
original readiness deadlines (including queued attempts near expiry); prohibit
deferral after an accepted gesture; preserve specific refusal reasons; and defer
only unsent preparation timeouts. The 750 ms OCR and 500 ms final visual gates,
finance checks and five-purchase cap remain unchanged. Actual gesture rejection
still terminates the run with no retry.

Owner evidence establishes partial 0.4.0 runs, not a complete cap-sized run. It
does not reveal the exact old final-gate block because those paths reported a
generic pre-send cancellation. 0.4.1 logs each block and actual callback separately,
retains foreground-event status, and adds terminal-run outcome notifications.
Notification/device behavior and the new deferral path still need phone validation.

APK SHA-256: `0f75e74f06485bdbe3712c7c2c3168a89f363746230e819be76e200ec42c2dcf`.

## 0.4.2 focused OCR and active capture cadence

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 127 tests executed with zero failures/errors/skips.
Four new tests verify native field pixels/coordinates at three resolutions,
reject padding/spanning tokens, map all six fields to the existing strict parser,
and bound the Pixel 10 atlas to less than half the previous input pixel count.
The example corpus atlas was visually inspected for complete level/price glyphs.
Native MLKit inference was not available in the cloud; recognition accuracy and
end-to-end speedup still require phone validation. No timeouts, financial guards,
verification requirements or actual gesture retry rules were relaxed.

The owner supplied a completed 5/5 run on 0.4.1 and reported slow operation;
see phone-validation.md. 0.4.2 records OCR work time/readability counters and
complete-run duration including game opening. It copies no preview while active,
uses six native text crops (118,776 atlas pixels versus 276,480 full strip pixels)
and polls fresh frames at up to 10 Hz while active, 4 Hz otherwise. OCR remains
single-flight without a task/frame queue. Increased active sampling needs battery
validation; a pixel-count reduction is not a measured latency improvement.

The APK signature, unchanged signing certificate, version code 9, native ARM64
packaging and 16 KiB ZIP alignment were verified. The ZIP's embedded APK matches
the standalone bytes. APK SHA-256: `101cdbe97a741438363ab425b9cfa6a78a05671f5f9e0a85f458f923a4dfdcaf`.

## 0.4.3 per-request timing and bounded run history

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 134 tests executed, zero failures/errors/skips.
Seven new tests exercise actual call/callback versus queue timestamps; first-queue
retention across unsent deferrals and ignored old-attempt callbacks; missing-stage
reporting for cancellation/uncertainty; terminal timestamp freezing; per-request
OCR counter reset and stale/readability aggregates; individually increasing batch
durations without double-counting; failed-request history; and inter-purchase
reconciliation delay accounting. Existing purchase guards still pass unchanged.

Timing-only updates are filtered from capture notification updates and duplicate
receipt event keys are filtered before journal reads. The journal remains bounded
to 200 events, run history to at most five requests, and OCR to one in-flight task.
No per-frame disk logging, deadline extension, extra gesture or automatic resume
was added. Millisecond milestones use elapsed realtime, not the wall clock.
Native timing/UI behavior and the cause of the owner's reported progressive
slowdown still need phone measurements. No cause or speedup is claimed.

APK signature, unchanged development certificate, version code 10 and 16 KiB
ZIP alignment verified. The ZIP contains identical standalone APK bytes.
APK SHA-256: `ded606f446ce77409ad7ce4fa0ee5353e773c629b96958523beff88876f36696`.

## 0.4.4 restore phone-validated full-strip recognition

Owner screenshot 135728.jpg shows zero readable results out of 28 with packed
fields; prices are read but level tokens are missing/malformed. The engine stays
IDLE and never prepares a tap. The experimental atlas and its four geometry tests
were removed. They validated geometry but could not validate native MLKit.

Restored the exact bitmap extraction block and token coordinate mapping from
v0.4.1, checked against that tag. The native upgrade strip spans reference
1130–1265 at unchanged resolution. Timing measurement and per-purchase histories
are retained, along with strict level parsing, all freshness/currency/foreground
guards, receipt deduplication, single-flight OCR and active preview suspension.
No malformed level is repaired and no action deadline is extended.

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 130 tests executed, zero failures/errors/skips; four
atlas-only tests were intentionally removed with the unused implementation.
The restored input has prior phone evidence from 0.4.1, but the combined 0.4.4
build still needs native regression validation. No speedup or native OCR accuracy
for this new APK is claimed.

Signature, unchanged certificate, version code 11 and 16 KiB ZIP alignment
verified. ZIP and standalone APK bytes match. APK SHA-256: `4cdc488c14687ed857dbe4d2f53da3ba3334f9c541ad147355861f264c93b2a9`.

## 0.4.5 exact live-text rechecks and bulk RGBA copies

Ran `tools/cloud-gradle.sh -PsplitApks assembleDebug testDebugUnitTest lintDebug
ktlintCheck`: all passed. 145 tests executed, zero failures/errors/skips.
Fifteen added tests cover native pixel identity across RGBA strides, row padding,
buffer offsets, crop coordinates and missing final alpha; copy bounds; every
watched level/price region changing by one RGB value; source expiry without
extension; dimensions, capture lease and game-version changes; actual gesture/
unknown-layout invalidation; late completion; complete native readings and token
box coverage; current availability refresh and unknown appearance rejection;
per-purchase native OCR averages excluding pixel rechecks; distinct captured frame
requirements before and after a call; and a complete real-engine transaction with
native interpretations for changed text plus exact pixel rechecks for the second
frames. The latter is a synthetic integration test, not a native MLKit benchmark.

All changed-text OCR still uses the full native strip and original token mapping.
Bulk copy tests verify identical RGB values to the previous checked per-pixel
path. ExactTextCache uses content equality, not fuzzy matching or hashes; it
requires all six regions and covered native OCR token bounds. It never extends
the original two-second source lifetime or derives new numbers. Each recheck
uses its own captured timestamp and renews availability, foreground and banknote
checks. Native interpretation is reused only when actual current watched pixels
are identical. Actual calls and unknown layouts invalidate pending admissions.
Freshness thresholds, deadlines, two distinct pre/post observations, no gesture
retry, explicit interruption and the cap remain unchanged. Retained patches are
bounded and in memory only. No per-frame disk writes or parallel native OCR were
added.

The owner validated 0.4.4 at 3/3 in 15.84 seconds. Native hit rate, OCR accuracy,
end-to-end latency and battery impact of 0.4.5 still require phone validation.
There is no measured phone speedup yet; unchanged pixels can avoid redundant
inference, but animation may force frequent fallback to native OCR.

Signature, unchanged certificate, version code 12 and 16 KiB ZIP alignment were
verified. ZIP and standalone APK bytes match. APK SHA-256: `3aa6ad4f300389df8f8de8ae6e4ecd9d10d04977bc92df8dc3700405c13794f4`.
