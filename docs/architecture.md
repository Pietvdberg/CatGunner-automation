# Architecture

`MainActivity` obtains fresh MediaProjection consent and starts the nonsticky
`CaptureService` from a visible user action. The projection callback is registered
before creating the virtual display. Capture dimensions changing ends the session.
`LatestFrameCapture` acquires only the latest image and samples at up to 4 Hz
while observing or 10 Hz while purchasing. Borrowed RGBA pixels respect row stride, crop offsets and buffer position.

`SessionController` provides immutable StateFlow snapshots. Generation and action
epoch leases prevent stale results or pause/resume races from reviving a pending
gesture. The final guard and `dispatchGesture` call execute under the controller's
same synchronization lock as pause/stop. A dispatched gesture may still finish.

`PurchasePipeline` performs lightweight label/color matching before bundled OCR
of the full native upgrade strip (reference y=1130–1265), using the token mapping
from the phone-validated 0.4.1 baseline. The six-field packing experiment from
0.4.2/0.4.3 was removed after native level recognition regressed. OCR levels and
costs must parse strictly; suffix magnitudes
are not guessed. Only the current foreground game can update calibration previews
or purchase evidence. One bounded OCR task runs at a time. Raw frames are closed;
one scaled preview is retained in memory for calibration, never used for action safety.
Preview copying is suspended while a purchase/run is active.

`SinglePurchaseEngine` has IDLE, ARMED, DISPATCHED (request pending), VERIFYING,
VERIFIED, FAILED and CANCELLED states. Arming consumes an explicit user command;
it does not restore on startup. Two distinct stable pre-action readings are
required. A local intent receipt is written off the UI thread before the final
freshness/foreground/session checks and actual one-shot gesture. Pause/stop can
cancel while receipt I/O is pending. A completion callback advances only to
VERIFYING. Two later frames must show exactly one selected level increase and no
other upgrade level change. Failure, cancellation and timeout never retry.

`GameAccessibilityService` checks current window/package, competing windows,
keyguard and display state immediately before gesture dispatch. The service does
not perform arbitrary node actions or gestures outside saved upgrade targets.
Screen lock pauses/cancels; accessibility revocation and capture interruption
invalidate active tests. Custom game-drawn dialogs still need corpus validation.

`CalibrationStore` atomically saves small versioned geometry. `PurchaseJournal`
stores at most 200 correlated receipt events using a serialized atomic writer.
A process-scoped recorder survives normal capture-service stop. A DISPATCHED
receipt records intent, not proof the system accepted or completed the gesture.
No request resumes after process death; an unfinished receipt remains unverified.

UI, notification and Quick Settings derive state from the same controllers. The
tile and Resume only resume observation; they cannot arm or repeat a purchase.
Notification text includes the current test outcome. Adaptive optimization,
Rebirth Rush, Room analytics and DataStore preferences are future milestones.
The current small profiles/receipts use atomic JSON without storing image data.

In 0.3.1, `waitForEvidence` suspends ARMED requests during game-launch/OCR startup,
clearing partial preconditions without extending their original deadline. It never
revives a terminal request; after DISPATCHED it fails instead of waiting. Returning
to AutoPilot cancels active tests. A connection-identity registry ignores teardown
from superseded accessibility instances so an old callback cannot erase a new
connection. Android's enabled setting and actual connection are shown separately.
`LiveDiagnostics` retains bounded local text about the latest verified game frame
and OCR fields. Clipboard export is explicitly user initiated.

## 0.3.3 outcome semantics

DISPATCHED still identifies a queued request. `executeGesture` wraps the actual
framework call under the current observation lease and marks possible dispatch
before the call. An explicit Android refusal clears that marker; an exception
preserves uncertainty. The marker and verification sample count are journaled.
A duplicate framework call for the same id is rejected.

After a sent action, bad OCR/visual samples reset the confirmation run and wait
within a fixed eight-second verification budget. No gesture is scheduled in this
phase. Validity requires two distinct post-completion captured frames with a
one-level increase, matching selected cost text and unchanged other upgrade levels.
Pre-action max age remains 750 ms; post-action OCR max age is two seconds.

Failure/cancellation/timeout after possible dispatch produces UNCONFIRMED, never
a no-purchase FAILED result. It blocks rearming until explicit game review, then
records REVIEWED without changing the prior outcome to VERIFIED. The review gate
is process-local; pending jobs are never restored after process death, and
unfinished historical receipts remain unverified.

## 0.4.0 bounded run orchestration

`AutoPurchaseRunner` owns an explicit user-started run with a cap from 1 through 5,
a frozen calibration/capture lease, and one owned purchase request id. It can only
arm another request after its owned result is VERIFIED. Counts are updated once
per owned id; a previous manual purchase cannot consume or inflate a new run's
budget. Receipts carry `autoRunId` for correlation.

Before each step, fresh complete readings are ranked by `PurchaseSelector`:
lowest exact decimal price among affordable controls with identical suffixes, or
explicit Power/Speed/Money rotation without price-scale assumptions. Mixed units
block the price-ranking rule. This is not expected-progress or DPS optimization.

`PurchasePipeline` samples all three banknote icons and applies the selected
request's match to both preconditions and final gesture checks. The one-purchase
engine retains all existing calibration, foreground, timing, double-read,
currency, callback, uncertainty and post-action verification guards. The runner
never dispatches gestures itself and has no gesture queue or retry path.

WAITING has a fixed 30-second readiness budget per new step. Existing one-purchase
deadlines govern an owned step. Failure, uncertain outcome, lease change, explicit
stop, access revocation or foreground loss stops the run. Resume never starts a
run; a fresh explicit command is required. All pending run state is volatile.

## 0.4.1 queued preparation tokens

`DispatchGuard` classifies the last pre-send check. Only stale OCR/frame and
unknown visual evidence can wait before a tap. Finance, calibration, target,
foreground and request-identity failures remain refusals. Deferring an unsent
request clears its precondition pair, preserves its original 30-second deadline
and requires two new readings; it does not call the gesture API.

Each queued preparation increments `dispatchAttempt`. The deferred job, final
framework call, receipt and callback carry that token. Obsolete tasks cannot
execute or change a later attempt with the same purchase id. Preparation deadline
is bounded by the original arm deadline. The two-second callback deadline starts
only around the actual framework call; post-action verification remains separate.
Actual gesture refusal/cancellation never becomes a preparatory recheck.

A separate high-importance outcome channel posts only terminal automatic-run
notifications, never active-run alerts. Foreground-stop details and dispatch
checks are retained locally for explicit diagnostic copying.

Per-purchase timing is immutable state on PurchaseReport, measured using the same
elapsed-realtime clock as the engine. Actual call/callback milestones are distinct
from queue preparation. Unsent deferral retains the first queue timestamp. Terminal
outcomes freeze timing. Current-id and arm-time checks exclude old OCR callbacks;
all OCR counters reset on each arm. AutoRunReport retains up to five correlated
terminal purchase snapshots for display/copy, including interrupted requests.
ReceiptRecorder filters duplicate event keys before I/O; notification collectors
ignore timing-only report changes. The existing journal persists terminal timing.

0.4.6 separates partial/stale text results (waitForText) from unknown actual UI
(waitForEvidence). Partial text does not dispatch or count as verification; it
retains valid observations within the original time bounds. A precondition pair
more than 1500 ms apart is restarted with the new reading, never dispatched from
the old one. Valid conflicting readings still fail/clear according to the normal
engine rules. Unknown UI retains its existing reset/deferral behavior. No action
deadline is extended. Bounded enum-based cache reject counts and four deferral reasons live on each
purchase timing snapshot and its receipt summary.

0.4.7 removes the exact-text cache and its per-frame snapshots. UpgradeOcr copies
only when no task is in flight, supplies a dedicated executor to the native SDK,
parses completion on that worker and posts results to the main handler. Scheduling
priority is an optional own-thread hint with a safe fallback. Only main-confined
state controls admission/close, and the bitmap stays alive until native completion
and result delivery. Closing a busy recognizer defers cleanup until that completion.
An idle throttle limits native work to 1 Hz, bypassed during explicit purchasing.

NativeOcrToken preserves raw text and may attach a proven level only after
LevelMarkerProof checks current native level pixels. This validates the fixed
separator and digit-component count; it never synthesizes a digit. Known merged
headings must match the control. Original parser rules remain for unproven text,
prices and suffixes. Full-strip input is unchanged. OcrStages measures contiguous
copy/SDK/parse/main-handoff intervals; per-request aggregates exclude missing
profiles from stage averages. The obsolete cache counters are removed; bounded
preparation deferral reasons and interruption rules remain.

SinglePurchaseEngine stores one immutable numeric GameReading only when the final
verification succeeds. Its matching verified request can consume it once. The
runner accepts it only for the same run's next waiting step, original capture
age <=750 ms and no unknown screen since that capture. Lease, profile and target
banknote checks remain. It seeds one precondition, never a gesture; another newer
complete frame is required. A process-scoped explicit terminal receipt write
captures the verified report before same-callback rearming can conflate flow
updates. No image or manufactured timestamp is used in this handoff.
