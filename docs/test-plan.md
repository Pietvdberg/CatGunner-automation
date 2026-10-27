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

## 0.3.1 phone regression checks

- Enable accessibility until the app explicitly says Connected. If Android shows
  it enabled but the app says disconnected, turn that service off and back on once.
- Arm one test while the game is reopening. Startup frames must show Waiting with
  no tap; only two new complete fresh readings permit the one gesture request.
- Startup uncertainty must not extend the original 30-second deadline. After a
  gesture request, unknown evidence still fails and cannot trigger a retry.
- Return to AutoPilot before a request dispatches; it must cancel without a tap.
- If failure persists, use Copy test diagnostics and inspect the exact last game
  detector reason, foreground status, OCR field strings/task error, and accessibility
  connection event. No need to guess values from an empty historical reading.

## 0.3.2 OCR prefix regression

Owner diagnostics: connected accessibility; last game label match 98% for all
three controls; raw `Lvo166` / `8h`, `Lvo167` / `11h`, `LV165` / `5.79h`. Historical
readings sometimes parsed, but repeated fixed-prefix failures prevented two
consecutive complete readings. Unit tests replay these exact strings through
region extraction and the engine: one frame cannot dispatch; two distinct fresh
frames can make one request. Numeric ambiguity such as `LvoI66` or `Lvo16O` stays
rejected. On the phone verify both raw and parsed levels before confirming success.

## 0.3.3 post-purchase regression

- Replay bad `Lva68` OCR and Power-only 49% label-match interruptions after one
  accepted gesture. Neither may schedule another tap; later two valid matching
  post-completion readings can verify within the original eight-second budget.
- An 866 ms post-action OCR processing delay may be used for verification only.
  The 750 ms action gate and actual-capture freshness checks remain unchanged.
- Callback-only, repeated frame, a pre-callback captured frame, or a single valid
  post-read can never report VERIFIED.
- Timeout, pause, foreground loss, cancelled callback or a framework exception
  after possible dispatch yields UNCONFIRMED. A clear refusal before any actual
  send can remain FAILED. No report implying no tap may follow a known sent tap.
- UNCONFIRMED blocks new tests until game review. Review itself never taps or marks
  the old purchase VERIFIED. After a real test keep the game visible about ten
  seconds before returning to AutoPilot so verification can finish.

## 0.4.0 bounded run validation

The single-purchase baseline is validated by the owner's three screenshots; see
phone-validation.md. For the first new run choose cap 3 and Rotate. Verify:

- Exactly one request proceeds at a time and the next is not armed before VERIFIED.
- At most three taps occur; COMPLETE shows 3/3 when all succeed. More observations
  after completion cannot restart the run. An unavailable control may be skipped.
- Lowest-price selection chooses the minimum only for equal suffixes and stops
  on mixed units. Rotation explicitly does not compare their magnitude.
- Stop/Pause, returning to AutoPilot, disabling accessibility or leaving the game
  ends the run. Resume/Quick Settings must not resume automatic purchases.
- UNKNOWN preconditions continue to block taps. FAILED/UNCONFIRMED stops the whole
  run with no retry; an unconfirmed action still blocks a new run until review.
- A manual single-purchase result from before a run never counts toward its cap.
- Restarting the process returns to automatic purchasing off; no job restores.

Unit tests exercise a complete capped run through the real one-purchase engine,
rotation, price comparison, no implicit startup, callback/verification sequencing,
lease changes, unconfirmed stops, profile mismatch, timeout and run correlation.

## 0.4.1 preparation deferral and foreground outcomes

- Inject an expired OCR/frame or transient label miss after a request is queued
  but before send. It must wait for two new readings, never tap stale content,
  and never extend the original readiness deadline.
- Complete an old job or callback after a newer preparation token is issued. It
  must neither dispatch nor cancel the newer attempt.
- Real Android refusal, currency mismatch, profile/target changes, foreground
  loss and pause/stop remain hard blocks. A sent gesture never re-enters the
  pre-send preparation path.
- Diagnostics must distinguish receipt failure, each last-gate reason, service
  acceptance and actual Android callback. A missing send must not be mislabeled
  as an Android callback cancellation.
- Keep the game visible until the complete/stopped outcome alert. Inspecting
  AutoPilot or the notification shade earlier still stops the run. Verify alerts
  under the phone's actual notification settings; no sound/banner is guaranteed
  when the user has muted the channel or enabled Do Not Disturb.

## Per-purchase timing comparison (0.4.3)

Run a five-purchase batch using the same rule and calibration, leaving the game
visible until the outcome notification. Return once complete/stopped, then use
Copy test diagnostics before starting another run. Check numbered rows #1–#5,
not the session-wide latest OCR result. Compare total, selection, readiness,
preparation, callback and verification milliseconds. The first selection includes
game opening; later selections include any gap after the previous completion.
Preparation includes unsent deferrals and subsequent fresh evidence acquisition.
Check each row's before/after-call OCR average/max times, readability/stale counts,
deferrals and unknown observations. Missing stages on failures must stay unknown,
not zero. Each request resets counters; completed rows must remain unchanged when
later OCR runs, after returning to the app, or when a later purchase fails.
Timings use elapsed realtime and must not change action deadlines or verification.
A new run resets its numbered history. No slowdown cause should be inferred from
an overall timer alone; compare individual stages and sample quality first.

## Exact live-pixel rechecks (0.4.5)

Repeat the same three-purchase rule. Confirm each requested purchase completes
with VERIFIED 2/2 before the next tap. Compare numbered total/preparation/
verification milliseconds and the new pixel recheck counts. Native OCR avg/max
must exclude recheck work. Unchanged live text should yield some rechecks; changed
levels/costs must force native OCR. If animation prevents equality, recheck counts
may stay zero and inference must still work normally. No reported phone speedup
is established until these measurements are supplied. Pause, app switching,
unknown dialogs and actual gestures must invalidate reuse. Existing tests cover
cache expiry/context, changed pixels, late native completion and distinct frames;
manual phone checks are still needed for the full native/rendering integration.
