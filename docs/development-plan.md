# CatGunner AutoPilot — Development Plan

**Version:** 1.0<br>
**Date:** 2026-10-26<br>
**Target game:** Idle Cat Gunner: Shooter RPG (`com.Chodun.CatGunner`)<br>
**Platform:** Android (private, sideloaded APK)<br>
**Primary goal:** On-device, low-latency, intelligent purchasing of in-game upgrades, especially immediately after rebirth, with start/pause/resume controls in the notification shade and Quick Settings.

## 1. Scope and success criteria

### In scope (MVP)
- Run directly on a physical Android phone, without a PC, root, or cloud connection during use.
- Observe the foreground game, recognize an explicitly calibrated upgrade screen, and read relevant values.
- Purchase eligible upgrades with deterministic, user-configured optimization rules.
- Accelerate purchasing after rebirth using a verified, cached action plan.
- Pause/resume from both an ongoing notification and a Quick Settings tile.
- Verify purchases, detect unexpected screens, and fail closed.
- Store calibration and purchase telemetry locally.

### Out of scope (initially)
- Automatic rebirth initiation, weapon merging, inventory management, other screens, and general gameplay.
- Premium-currency purchases, real-money purchase flows, ad clicking, anti-cheat evasion, game API interception, memory modification, or packet manipulation.
- Google Play distribution, multi-device synchronization, cloud inference, and remote unattended operation.

### Acceptance criteria
1. A user can install, calibrate, start, pause, and resume the app on the target Android phone.
2. The bot never intentionally taps outside calibrated and verified upgrade controls.
3. Pause prevents the scheduling of any new gesture; an already-dispatched gesture may finish.
4. Unknown layouts, confidence failures, interruptions, or unrecognized dialogs suspend purchasing.
5. Every purchase has a correlatable precondition and post-action verification, with periodic sampling permitted only in explicitly bounded fast-mode batches.
6. The bot transitions into and out of Rebirth Rush based on observed evidence, not a single ambiguous visual cue.
7. Latency, purchase correctness, battery usage, and heat are measured on the real phone before claiming performance targets.

## 2. Constraints and assumptions to validate

- **Game UI is currently unknown:** obtain screenshots or recordings of the upgrade screen in normal progression and immediately after rebirth. Do not hard-code labels, upgrade formulas, coordinates, or rebirth signals until observed.
- **Device details are not yet verified:** record Android version, screen resolution, density, refresh rate, gesture navigation, and manufacturer battery policy.
- **Foreground-only operation:** the game must remain visible; the device usually must remain unlocked.
- **Android permissions:** Accessibility Service must be manually enabled. MediaProjection requires user consent for capture sessions; on Android 14+ capture tokens and sessions have additional restrictions. A stopped capture session cannot simply be reused.
- **Android foreground-service rules:** declare appropriate foreground-service type(s), permissions, and notification handling for the device's Android version. Start capture from a permitted user-initiated state. Android may stop the service or revoke capture; handle both gracefully.
- **Notifications:** Android 13+ notification permission and system notification visibility differ by platform; provide Quick Settings and in-app controls as alternatives. Quick Settings tiles may need to be added by the user.
- **Private sideloading avoids Play Store review**, but does not bypass Android platform permission requirements or the game's terms of service.
- **No guarantees about game anti-automation policy:** investigate game rules before use; accept possible account risks.

## 3. Architecture

```text
Idle Cat Gunner (foreground)
         |
         v
MediaProjection -> ImageReader (latest-frame-only buffer)
         |
         v
ROI crop / image matching / screen-state classifier
         |
         +--> OCR (ML Kit, only for changed/needed numeric regions)
         |
         v
State Estimator (currency, cost, level, affordability, confidence)
         |
         v
Strategy Engine (adaptive or Rebirth Rush)
         |
         v
Safety Gate -> Gesture Queue -> AccessibilityService.dispatchGesture()
         |                              |
         +<------- post-action verification

Foreground service <-> StateFlow controller <-> Notification actions
                                           <-> Quick Settings TileService
                                           <-> In-app settings
Room: purchase log, learned costs, calibration; DataStore: preferences
```

### Recommended modules/packages

```text
app/
  ui/                    # Setup, calibration, diagnostics, settings
  service/               # ForegroundService, lifecycle, notifications
  accessibility/         # AccessibilityService and gesture executor
  capture/               # MediaProjection, ImageReader, frame buffers
  vision/                # ROI detectors, OpenCV templates, OCR adapter
  domain/state/          # Stable game-state representation
  domain/strategy/       # Ranker, savings, rebirth logic
  domain/safety/         # Guards, confidence thresholds, kill switch
  data/                  # Room entities/DAOs and DataStore
  tiles/                 # TileService
  testing/               # Replay fixtures, measurements
```

**Stack:** Kotlin, coroutines/StateFlow, Android SDK, MediaProjection, AccessibilityService, OpenCV (if necessary), ML Kit Text Recognition, Room, DataStore. Prefer lightweight native pixel/template detection over OCR for rapid UI checks. Keep app operational without network access.

## 4. Execution modes and state machine

### Global states
`STOPPED`, `AWAITING_PERMISSION`, `CALIBRATING`, `READY`, `RUNNING_ADAPTIVE`, `RUNNING_REBIRTH_RUSH`, `PAUSED`, `ERROR`.

- `START` may move `READY`/`PAUSED` into `RUNNING_*` only if required permissions, game foreground, valid calibration, and an active capture session are confirmed.
- `PAUSE` immediately disables new gesture scheduling, drains the serialized queue, and retains settings and strategy state. One in-flight gesture may complete.
- `STOP` closes capture and automation resources, clears volatile queues, and stops the foreground service appropriately.
- Capture interruption, accessibility revocation, game leaving foreground, detection uncertainty, and unsafe dialogs => `PAUSED` or `ERROR`.

### Adaptive mode
1. Read most recent frame without blocking the gesture executor.
2. Classify the screen and extract essential regions of interest (ROIs).
3. OCR dynamic values only if changed, unknown, or stale.
4. Estimate upgrade value and compare an immediate purchase against saving.
5. Queue **one** validated purchase.
6. Confirm the resulting UI/currency/level change before continuing.
7. Periodically update price/benefit models using observations.

### Rebirth Rush
1. Confirm rebirth using at least two independent signals (e.g., level reset + characteristic currency/upgrade-state change), or a user-triggered mode.
2. Load a per-layout, per-rebirth-stage cached sequence learned from previously verified purchases.
3. Execute a *bounded* batch of sequential gestures at calibrated targets; use gesture completion callbacks and enforce a minimum safe interval.
4. Verify after each small batch using lightweight visual checks; reduce batch size to 1 after any mismatch.
5. Reassess upgrade priorities and fall back to adaptive mode when costs/affordability deviate, progress plateaus, or the opening phase ends.
6. Never assume that a tap succeeded merely because a callback completed.

**Important:** Do not implement blindly repeated high-frequency taps. Dynamic dialogs, screen shifts, and unexpected spending must force immediate suspension.

## 5. Upgrade decision engine

### Initial deterministic formula
For each affordable upgrade *i*, estimate:

`score_i = expected_progress_gain_i / effective_currency_cost_i`

This is only a baseline. For attack speed, crit chance/damage, and multipliers, calculate *effective* DPS from observed game mechanics rather than adding stat gains directly. Where exact formulas are unknown, use measured before/after progress and confidence intervals.

### Longer-horizon decisions
- Evaluate **save vs buy now**, considering income rate and time-to-afford.
- Avoid spending reserved currency on a weaker upgrade when a markedly stronger one is imminent.
- Allow user-selectable strategies: **Balanced**, **Farming**, and **Stage Push**.
- Track stage progress per minute, resource income per minute, and rebirth recovery time; use these as longer-horizon objectives.
- Learn incremental gain, cost scaling, and strategy performance from successful purchases.
- Record why each decision was made for reproducibility and debugging.

### Guardrails
- Whitelist allowed upgrade buttons and currency types.
- Never click purchases requiring premium currency or real money.
- Require a minimum confidence score for the current screen and target.
- Cap purchases per batch and per session; add an optional spend/reserve policy.
- Compare pre-/post-state for rollback-free verification (purchases generally cannot be undone).

## 6. UI control design

### Notification
Use an ongoing notification associated with the foreground service:
- When running: `Pause` and `Stop` actions.
- When paused: `Resume` and `Stop` actions.
- Display current mode, last purchase, and error/permission state in compact text.
- Use explicit, immutable `PendingIntent`s when appropriate and handle actions in a single command controller.
- A notification action must not silently restart a MediaProjection session without renewed user consent when consent is required.

### Quick Settings tile
- `TileService` with `ACTIVE` (running) and `INACTIVE` (paused/stopped) visual states.
- Tapping toggles pause/resume when the capture session is valid.
- If permissions/setup are missing, launch the app into the appropriate flow.
- Provide an in-app route to add the tile when supported and clear manual setup instructions otherwise.
- Ensure controller state drives both notification and tile; do not store independent booleans.

### Main app
- Permission/setup checklist.
- Calibration wizard with overlay previews of detection ROIs and tap targets.
- Mode selector and configurable rule profiles.
- Start/Pause/Stop controls.
- Diagnostics: latest recognized values, confidence, decision, latency, and error logs.
- Export of anonymized diagnostic logs (opt-in, never enabled by default).

## 7. Phased delivery

| Phase | Work | Deliverable | Exit condition |
|---|---|---|---|
| **0. Reconnaissance** | Document game screens, upgrades, costs, rebirth behavior, Android version, and restrictions. | Annotated screenshots and mechanics worksheet. | Upgrade options and safe controls are identified. |
| **1. Android skeleton** | Initialize Gradle project; implement state controller, permission setup, notification, and Quick Settings tile. | Sideloadable APK with UI controls. | Start/pause/stop state propagates consistently without touching the game. |
| **2. Capture & calibration** | Implement user-approved MediaProjection, latest-frame capture, ROI configuration, screen classifier. | Live debug overlays and saved calibration. | Correct screen classification across varied game states. |
| **3. Single verified purchase** | Implement AccessibilityService and serialized gestures with post-action verification. | Safe one-purchase prototype. | Repeated manual test cases succeed without unintended touches. |
| **4. Adaptive strategy** | Integrate OCR, state extraction, rule engine, cost-effectiveness ranking, savings logic. | Autonomous adaptive upgrade purchasing. | Decisions match offline golden test cases; unexpected states pause. |
| **5. Rebirth Rush** | Learn verified purchase patterns, detect rebirth, execute bounded fast batches, fallback on mismatch. | Fast recovery after rebirth. | Rebirth recovery is faster than adaptive-only baseline without sacrificing correctness. |
| **6. Hardening** | Add app lifecycle recovery, battery/thermal controls, logs, field tests, and calibration migration. | Release-candidate private APK. | Sustained sessions remain stable; pause/stop latency and battery impact are measured. |

## 8. Testing strategy

### Unit tests
- Upgrade ranking, special-stat effective DPS calculations, currency reserves, and save-vs-buy rules.
- Rebirth detection with positive and negative scenarios.
- Controller state transitions and pause race conditions.
- Correct refusal of premium/unknown purchase targets.

### Offline vision tests
- Build a **versioned screenshot corpus** from real gameplay: early/late stages, after rebirth, animation frames, overlays, insufficient funds, dialogs, loading states, and different brightness/scale configurations.
- Assert recognized values and per-element confidence on known frames.
- Replay recorded frames deterministically before enabling taps.

### On-device integration tests
- Notification pause/resume and Quick Settings toggle while game is visible.
- Revoke accessibility / stop MediaProjection / rotate screen / lock device / switch apps.
- Inject unexpected dialogs and verify that the bot halts.
- Stress test repeated purchases using serialized gesture callbacks.
- Confirm Android does not silently kill the service under normal usage.

### Metrics (record actual distributions, not just averages)
- Detection-to-action latency: p50 / p95 / p99.
- Successful purchases per second, verified purchase rate, and false-tap count.
- Rebirth-to-target-stage elapsed time against manual and adaptive-only baseline.
- OCR accuracy and uncertainty rate.
- Battery percentage per hour, frame processing utilization, and thermal throttling.
- Number of times explicit user reauthorization is required.

**Initial performance hypothesis:** test 100–250 ms as an action scheduling interval in fast mode, not a promise of effective purchases at that rate. Tune based on gesture completion time, actual game update frequency, and verification reliability.

## 9. Security, privacy, and operating principles

- No root, privileged APIs, or invasive game modification.
- No remote telemetry, embedded credentials, or external command execution.
- Keep capture frames in memory where feasible; do not persist raw screenshots unless explicitly requested for calibration/debugging.
- Avoid drawing overlays over purchase buttons during active automation.
- Provide an obvious emergency stop in the notification and main app.
- Bound memory and CPU use with a latest-frame buffer; drop stale frames rather than building a backlog.
- Respect Android permissions and foreground-service lifecycle. Do not attempt to bypass game security controls.

## 10. Repository and engineering workflow

Suggested repository name: `catgunner-autopilot`.

```text
catgunner-autopilot/
  app/src/main/java/.../
  app/src/test/.../
  app/src/androidTest/.../
  docs/
    architecture.md
    game-mechanics.md
    calibration.md
    test-plan.md
    known-limitations.md
  fixtures/                  # Redacted screenshot fixtures, only if safe to store
  .github/workflows/
    android-ci.yml
  README.md
  LICENSE                   # Optional for private repository
```

CI on GitHub Actions: compile debug APK, run unit tests, static analysis, and report artifacts. Signing keys for a release APK should stay outside version control; store sensitive signing information securely if release builds are automated. Do not upload gameplay recordings that expose account details.

## 11. Immediate next actions

1. Capture **2–4 screenshots** of the upgrade screen: normal progress, immediately after rebirth, after rapid purchases, and with insufficient currency.
2. Supply Android OS version, display resolution, and whether the UI changes orientation.
3. Inventory upgrade names, currencies, stat effects, cost steps, and relevant purchase multipliers (`x1`, `x10`, `MAX`, etc.).
4. Define which resources are allowed to spend and which must never be spent.
5. Record one short rebirth-to-recovery session for benchmarking (optional but valuable).
6. Scaffold the Android Studio project and implement **Phase 1** before implementing screen analysis or purchases.

## 12. Open decisions

- How are affordability and next-upgrade cost represented visually?
- Does holding an upgrade button repeatedly buy upgrades, or is each purchase a discrete tap?
- Does the game offer `MAX` or batch buying, and is it optimal?
- What happens to upgrade controls and coordinates during rebirth animations?
- Are gain, cost, and progression formulas observable, or must they be estimated experimentally?
- Which Android handset will be the first supported device?
- Which upgrade categories are explicitly permitted, including currencies and rebirth resources?

---

**Definition of done:** A locally installed Android APK can detect the calibrated upgrade screen, purchase permissible upgrades according to transparent rules, accelerate safely after a detected rebirth, and start/pause/resume from Android notifications and Quick Settings. The implementation is verified against captured game states and measured on the target handset.
