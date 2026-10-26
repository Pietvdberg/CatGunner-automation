# Architecture

`MainActivity` obtains fresh MediaProjection consent and starts the nonsticky
`CaptureService` from a visible user action. The projection callback is registered
before creating the virtual display. Capture dimensions changing ends the session.
`LatestFrameCapture` acquires only the latest image and samples at most four times
per second. Borrowed RGBA pixels respect row stride, crop offsets and buffer position.

`SessionController` provides immutable StateFlow snapshots. Generation and action
epoch leases prevent stale results or pause/resume races from reviving a pending
gesture. The final guard and `dispatchGesture` call execute under the controller's
same synchronization lock as pause/stop. A dispatched gesture may still finish.

`PurchasePipeline` performs lightweight label/color matching before bundled OCR
of the upgrade strip. OCR levels and costs must parse strictly; suffix magnitudes
are not guessed. Only the current foreground game can update calibration previews
or purchase evidence. One bounded OCR task runs at a time. Raw frames are closed;
one scaled preview is retained in memory for calibration, never used for action safety.

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
