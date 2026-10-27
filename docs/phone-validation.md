# Pixel 10 validation evidence — 2026-10-26

The owner confirmed that all three 0.3.3 single-purchase controls work and supplied
screenshots of the application's VERIFIED outcomes. Each reports two matching
post-action observations, accessibility Connected and the existing saved calibration.
The app-reported native capture dimensions are 1080×2424 on Pixel 10 / Android 16.

| Upgrade | Before | Observed after | Verification samples | Evidence |
| --- | --- | --- | --- | --- |
| Power | 254 | 255 | 2/2 | 135662.jpg |
| Speed | 258 | 259 | 2/2 | 135663.jpg |
| Money | 254 | 255 | 2/2 | 135664.jpg |

Displayed OCR result ages were 495, 524 and 681 ms across these screenshots. These
are three diagnostic samples, not inference benchmarks, action-latency percentiles,
battery/thermal measurements, or proof of sustained-session reliability. Live
OTHER_APP/unknown results are expected while inspecting AutoPilot itself; the
retained game result and terminal receipt provide the relevant historical evidence.

No full screenshots are committed. Source hashes identify the supplied evidence:

- `1-135663.jpg`: `8bc342b8aea86b82d0362801dd42a02dcb31c12d6fc557edfdce28792c66f65f`
- `2-135664.jpg`: `2cd94e51a589bebb079a02a00bd7e9c8e2eb103da89a72387aa0b2620d6cb6d2`
- `3-135662.jpg`: `27f740519008abb25feb3c846a4cfca0b8db1a27b77131e037cc704f318aadf3`

This establishes the one-purchase baseline for this layout. Interruption testing,
unavailable Speed/Money layouts, custom dialogs, sustained operation and any
new bounded-run mode need separate validation. It does not validate rebirth
recognition, suffix scales, expected upgrade benefit or strategy optimality.

## Owner testing of 0.4.0 bounded runs

- A lowest-comparable-price run reached 2/3 verified, then stopped on foreground
  loss (135665.jpg). This image was captured while AutoPilot was visible; it
  does not establish whether inspection caused the stop or another window event
  occurred first. The foreground-only rule remains enforced.
- A rotation run reached 1/5 verified, then reported FAILED with no tap sent for
  the next request (135666.jpg). Its generic cancellation text came from a
  pre-send failure path, not an accepted gesture callback. Existing diagnostics
  do not establish which exact gate failed.

0.4.1 adds explicit final-gate reasons and pre-send deferral for transient stale
frames/layouts, plus outcome notifications to avoid interrupting a run to inspect
it. It still needs phone validation; a completed cap-sized run is not yet proven.

## Owner confirmation of a complete 0.4.1 run

135726.jpg shows Auto COMPLETE, verified 5/5 under Lowest comparable displayed
price, and the final single purchase VERIFIED 2/2 (Speed 277→278). The owner says
it works but takes too long. The screenshot's recording timer is not a run timer;
no exact batch duration or latency distribution is established. A later OCR
failure in the historical diagnostics does not undo the completed verified run.

Source SHA-256: `1883a7a684087c8c4a7bb606b95ce43c800a27462bf8b4bc7746d8a714ba4585`.

0.4.2 reduces OCR input and preview-copy work and records timing, but its native
OCR accuracy, end-to-end latency and active sampling battery cost remain untested.

## 0.4.3 packed OCR regression (135728.jpg)

The owner reports no action. The screenshot shows Readable=0/28, a 522 ms frame
age and 508 ms OCR work time; costs parse but Power level is `Lvs278` and Speed
and Money level strings are empty. No dispatch check was performed and the
single-purchase engine remains IDLE. Returning to AutoPilot stopped the waiting
run at 0/5. That foreground stop explains the final status, but the unreadable
levels prevented arming beforehand. No purchase timing row exists for an unarmed
request. The exact internal MLKit failure is unknown; geometry-only tests could
not establish recognition accuracy of the repacked fields.

0.4.4 removes packed fields and restores the full-strip input and token mapping
used by the completed 0.4.1 run. All per-purchase timing and purchase guards remain.
The restored combination needs a phone regression check; no new accuracy/speed
result is claimed. Source SHA-256: `f956dba194ff229de26ec0eb9d650bf5e1ae72731dad5c1fb9e76f4cb3c89f68`.

## 0.4.4 three-purchase timing evidence (135729.jpg)

All three lowest-comparable-price purchases were VERIFIED in 15.84 seconds.
Individual totals: Power 5339 ms, Money 7108 ms, Power 3392 ms. The middle
purchase had one deferral, one stale pre-call OCR observation and 3467 ms in
preparation; the exact final guard responsible is not retained in this screenshot.
Native OCR averages were roughly 530–651 ms and verification 1932–2413 ms.
This does not establish progressive slowdown (the third purchase is fastest), but
the owner confirms the absolute speed is unacceptable. First selection includes
2148 ms of game opening/readiness. Source SHA-256: `7046a57b6c452836bf2756bdd7f2cc48b2b6a083adb4dacc5e6bfb85d8b8d38d`.

0.4.5 targets redundant inference with exact live text-pixel rechecks, retaining
full-strip OCR for changed pixels. Its live match rate, accuracy and per-purchase
latency need phone validation. No phone speedup is claimed from cloud tests.

## 0.4.5 rare cache reuse and unfinished fifth purchase (135730.jpg)

Four purchases VERIFIED; the fifth Power tap is UNCONFIRMED after a foreground
check OTHER_APP. The image is of AutoPilot and does not establish which foreign
window first caused the stop. Totals: Money 5425 ms, Power 6161 ms, Speed 6462 ms,
Money 6014 ms, Power 9565 ms. Only two pixel rechecks are shown across all before/
after summaries: before #1 and after #2. Most steps still use repeated 500–700 ms
native OCR. Fifth preparation took 6040 ms with two deferrals; the exact reasons
were overwritten by the successful gesture callback and are not established here.

0.4.6 retains partial-text observations without counting them as new proof, keeps
unknown-layout resets, narrows RGB comparison to text and retains cache-admission
and queue-deferral reasons. The larger old regions included non-text areas, but
this screenshot alone does not prove which cache-admission gate dominated. There
is no new phone timing result yet. The fifth upgrade may already have occurred;
no automatic retry is permitted. Source SHA-256: `55e581c2b4fa1fde21c2fd748d31a0e3bedb6b0338ed458a9d291f46ba68a833`.

## 0.4.6 completed runs remain too slow (135731.jpg / 135732.jpg)

The lowest-comparable-price batch completed 3/3 in 25.341 seconds: 12092, 6310,
6935 ms. The first request had 8997 ms readiness and 61 unknown observations;
others had incomplete native levels, token-bound coverage failures and changing
pixels. One preparation deferral is explicitly OCR_TOO_OLD at 862 ms.

The rotation batch completed 5/5 in 21.166 seconds: 4638, 3889, 3287, 4794,
4558 ms. It recorded zero pixel rechecks. Native OCR remains roughly 500–700 ms,
with several incomplete readings. This establishes successful bounded operation,
not acceptable speed, cache effectiveness or a cause for every unknown frame.
0.4.7 retires caching, changes SDK scheduling, profiles stages and addresses the
pixel-verifiable Lv. separator issue reproduced by the native fixture probe.
No phone timing for 0.4.7 is available yet.

- `1-135731.jpg` SHA-256: `b6b5c451c0812e30af8ef56c9057d824adb114ff825b9e0f55d0afd8f904543d`
- `2-135732.jpg` SHA-256: `3323e492a5c4428faf8b15e768247ed9d0ae254a2163043e40f24f3617344cef`
