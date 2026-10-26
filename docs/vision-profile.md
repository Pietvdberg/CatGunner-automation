# Upgrade appearance profile v1

This experimental read-only profile is derived from owner-supplied `135594.jpg`
and `135595.jpg`, both 570×1280. It produces no tap targets and never authorizes
spending. It is not a complete game-screen classifier.

| Control | First image | Second image |
| --- | --- | --- |
| Power | Turquoise fill, white cost: available appearance | Dark fill, red cost, green lower edge: unavailable appearance |
| Speed | Available appearance | Available appearance |
| Money | Available appearance | Available appearance |

The three title-glyph templates use 501 sparse color anchors, excluding changing
levels, stats and costs. Each title must match at least 85% of anchors within the
profile's RGB tolerance. Fixed reference sampling regions then check panel fill,
white/red cost text and the disabled panel's green edge. The whole result is
unknown if any label or appearance is ambiguous. Colors alone cannot match.

The reference geometry is scaled only for nearly matching portrait aspect ratios
(within 0.5%). This is for observation, not a substitute for device calibration.
Only proportional nearest-neighbor scaling has been tested synthetically. Native
rendering, font antialiasing, brightness, game updates, UI scale and capture insets
may cause unknown results and require a new profile. Thresholds are heuristics,
not probabilities or performance guarantees.

Test resources contain only the bottom 570×150-pixel upgrade bars, encoded as
row-major RGB8 (`.rgb`); no full gameplay image is retained. `manifest.json`
records original source hashes, crop geometry, format and expected appearances.
The same CSV asset is used by the Android app and JVM replay tests.

Missing observations include disabled Speed/Money panels, dialogs, overlays,
loading states, rebirth screens and native phone captures. A dialog outside the
sampled bar can go unnoticed. The profile does not read amounts, currency names,
upgrade effects or levels, and does not establish foreground-package identity,
price affordability, purchase success, or rebirth. The detector alone never authorizes gestures. The single-purchase build combines
its evidence with saved user calibration, banknote-icon matching, foreground and
window checks, OCR stability, an explicit one-use request, and post-action verification.
