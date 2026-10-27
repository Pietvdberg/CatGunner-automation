# Known limitations

0.4.7 needs Pixel 10 latency, native rendering and sustained thermal/battery
validation. Native fixture probing runs on an unaccelerated API 30 x86_64 emulator;
its wall times cannot be compared to the phone. Fixture JPEG crops and their
nearest-neighbor enlargement are not native phone captures. A dedicated SDK
executor/scheduling hint does not guarantee priority or latency on every device.
The reported SDK stage includes internal scheduling, model work and cold loading.

The `Lv.` separator recovery is limited to the observed footer and white glyphs.
It requires a low small dot, separate plausible glyph geometry and an exact match
between the visible digit count and already-recognized ASCII digits. Ambiguity
still produces no reading. The generic parser and price suffixes are not guessed.
Native OCR can still misread a numeric value or suffix; two complete observations
and exact one-level-change checks remain necessary. Only green banknotes are
permitted. Gems, unknown currencies and real money remain forbidden.

The six-field OCR layout and exact RGB cache experiments were removed. Full-strip
input is retained, with no production masking, resizing or packing. The native
probe contains test-only masking comparisons; those do not ship in the main APK.
The app samples active visuals at up to 10 Hz; native tasks remain single-flight.
Idle OCR is limited to 1 Hz. No parallel inference or frame/job backlog is used.

The visual profile is limited to the owner's portrait game layout and is not a
complete dialog classifier. Device dimensions/game version changes invalidate
saved calibration. Unavailable Speed/Money and custom dialogs need more evidence.
Expected upgrade benefit, suffix scales, reserve policy and Rebirth Rush remain
future work. Automatic runs are explicitly started and capped at five; returning
to the app, losing the game foreground, Pause/Stop or uncertainty ends a run.
Resume only resumes observation. A sent action has no rollback or automatic retry.

UNCONFIRMED means a purchase may already have occurred; inspect before starting
again. Its review gate and run history are in-process and are lost on process
death. No pending request or run is restored. Only geometry and bounded numeric
receipts are saved; retained preview pixels live in memory and never serve as
live purchase preconditions. Capture after Stop requires fresh Android consent.
The bundled model operates offline with network permissions removed.
