# Known limitations

Version 0.3.0 is a calibrated single-purchase test build, not the autonomous MVP.
The owner confirmed capture startup and notification display on a Pixel 10 running
Android 16. New calibration, native OCR accuracy, accessibility dispatch and
post-purchase verification remain untested on the physical phone.

The two screenshots support Power/Speed/Money available and Power unavailable.
Disabled Speed/Money, rebirth, dialogs, loading states and native-resolution
captures need more coverage. The screenshot-derived classifier is incomplete:
custom dialogs entirely outside its visual regions may not be recognized.
The app refuses unknown regions, failed OCR, unverified icons, changed layouts,
non-game foreground, competing native windows, lock and stale evidence. These
checks do not establish comprehensive game-screen understanding. Unattended
purchasing, loops and batches are not enabled.

OCR requires exact numeric level notation and strict cost text; it does not guess
ambiguous characters or convert suffixes to numeric currency magnitudes. Cold OCR
startup or processing delays can fail freshness checks. Such failures never retry.
A cancelled, rejected or unverified tap may still have changed the game: inspect
it before manually arming another test. There is no purchase rollback.

A retained preview survives app switching and pause for inspection. It is historical
and never an action precondition. Only geometry and bounded receipts are saved;
process death loses previews and pending requests. Device/game changes invalidate
calibration. Capture tokens are never reused after stopping.

Gems, unknown currency and real money remain forbidden. The bundled model operates
locally, and network permissions are explicitly removed from the merged manifest.
No performance, battery, thermal, or rebirth-recovery claims have been established.
