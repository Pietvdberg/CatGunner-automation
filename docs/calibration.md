# Device calibration

Enable AutoPilot accessibility, start capture, show the game's upgrade bar and
return to AutoPilot. The retained preview is a known past game frame, labelled
with age and native dimensions; it is never used as live action evidence.

The calibration screen pauses capture and shows the upgrade bar with three inset
regions. Choose each upgrade and tap its outlined stat area. All three points
must be explicitly set inside their own region. Saving confirms that the pictured
controls use green banknotes. There are no default tap coordinates.

The atomically saved profile contains schema version, native frame width/height,
game version, the three normalized points and the green-banknotes restriction.
It contains no screenshot, token or running state. Invalid schema, missing points,
nonfinite/outside coordinates, a game update or different native dimensions blocks
a test. Resetting calibration cancels an armed test.

Actual dispatch also requires two fresh, stable OCR readings, all three recognized
upgrade controls, the selected control's affordable appearance, the observed
banknote-icon template, a currently verified foreground game, no competing
application/accessibility window, an unlocked interactive display, and the
current capture session's action lease. Calibration alone never permits a tap.

Calibration overlays exist only in AutoPilot's own screen and cannot obstruct the
live game. The reference profile assumes the observed portrait layout; unknown
native rendering or UI scale must fail closed instead of guessing a new layout.
