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
