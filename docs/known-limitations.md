# Known limitations

Version 0.4.1 is a bounded automatic-purchase prototype, not the complete MVP.
The owner validated Power, Speed and Money single purchases on Pixel 10 / Android
16 at 1080×2424, each showing VERIFIED 2/2. See phone-validation.md. The new run
orchestration is cloud tested but still needs handset validation.

Automatic runs require an explicit start and cap at five actions, default three.
Each prior action must verify before another can be armed. Lowest-price selection
only compares exact decimals with identical suffixes. Rotation does not compare
price magnitude. Neither rule estimates expected progress gain, opportunity cost,
income or DPS. Reserve/spend-amount policies and unknown suffix scales are not
implemented. Expected-gain optimization and Rebirth Rush remain future milestones.

Unavailable Speed/Money, rebirth, dialogs, loading states and native captures need
more corpus coverage. The screenshot-derived classifier is incomplete: custom
in-game dialogs entirely outside its visual regions may go unnoticed. Unknown
controls/OCR and stale frames block dispatch; native competing windows, loss of
foreground/accessibility, pause and stop end a run. This is not evidence of
comprehensive game-screen understanding or readiness for unattended operation.

OCR does not reconstruct unreadable digits. It accepts the observed period-as-o
separator only after the fixed Lv prefix. After a tap, invalid frames can wait
within an eight-second verification window, without another gesture. Two distinct
post-completion readings must show exactly one selected level increase with
matching cost text and unchanged other upgrade levels. A callback alone is never
purchase success. UNCONFIRMED means a purchase may already have occurred; review
the game before any new test or run. There is no rollback.

A retained historical preview survives app switching and pause for inspection;
it is never an action precondition. Only geometry and bounded receipts are saved.
Process death loses preview, pending run state and its in-process review gate.
No request resumes automatically. Device/game changes invalidate calibration.
Capture tokens require renewed Android consent after stopping.

Green banknotes only. Gems, unknown currencies and real money remain forbidden.
The bundled OCR model runs locally, and network permissions are removed from the
merged manifest. No sustained-session, battery/thermal, action-latency percentile
or rebirth-recovery performance claims have been established.

The owner observed partial 0.4.0 runs (2/3 before foreground cancellation, 1/5
before a no-send failure). 0.4.1 addresses the queue-to-send transient evidence
gap and diagnostic ambiguity without relaxing the 750 ms pre-action limit or
retrying actual gestures. These changes are cloud validated only. The exact
original final-gate failure is not established by the supplied screenshots;
new diagnostics expose it if it persists.

The owner subsequently confirmed a complete 5/5 lowest-comparable-price run on
0.4.1 (135726.jpg), while reporting slow operation. 0.4.2 uses six native text
crops, skips preview copying during purchases and samples active frames faster.
Its cloud-tested geometry does not establish native MLKit accuracy or speed.
Run duration and OCR work time are now displayed for phone comparison. The
higher active sampling rate needs battery/thermal validation; idle remains 4 Hz.

0.4.3 adds per-purchase monotonic milestones and before/after-call OCR aggregates,
including terminal failures. These expose trends but do not identify a cause of
progressive latency without phone measurements. Active-run rows are retained only
until a new run/process death; the bounded receipt journal saves terminal timing
milestones. A run stopped before any request is armed has no purchase row.
