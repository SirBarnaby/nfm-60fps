## 1. The slide

- [x] 1.1 `SkidSlip`: per-wheel sideways and lengthwise slide shared out by body angle, handbrake, turn, landing, steering and load; kinds; the slip
- [x] 1.2 `RaceView.groundRgb` and `onRamp`, filled by `FeatureBridge`

## 2. The marks and particles

- [x] 2.1 `MarkRing`: 4,096 pieces, strips per wheel, the spot, streaks and striations
- [x] 2.2 `SkidmarksFeature`: start, continue, end; shades; mud; scrubs after a side hit; body scrapes and sparks; smoke, dust, clods
- [x] 2.3 Drawing: road pieces as one ground item, ramp pieces by depth; particles moving, puffs as turning hexagons and clods as tumbling squares, each corner with its own move to the next tick (`Canvas.moving` with per-corner moves); the simple detail throws half the particles

## 3. The zones

- [x] 3.1 `SkidZonesFeature`: the grid, kept per stage on race end (not in test runs), shown in the next race

## 4. Checks, settings, docs

- [x] 4.1 `ExtrasCheck`, `CheckCanvas`, `SkidmarkCheck` (each maneuver), `test\extras-check.ps1`
- [x] 4.2 Register both in `Features.ALL` (their switches, rows and console command come from the framework)
- [x] 4.3 README Extras lines and options rows; `docs\features\skidmarks.md`

## 5. Gates

- [x] 5.1 `test\extras-check.ps1`: every check passes
- [x] 5.2 `build.ps1 -Quick`, `test\verify-races.ps1` (20 matched, golden frames all the same), `test\perf.ps1`, `test\spec-check.ps1`
- [x] 5.3 `test\verify-races.ps1 -Features skidmarks,skidzones -NoGolden`: 20 matched
- [x] 5.4 Hidden GPU autotest with the skidmarks on and `-Dnfm.projectiontest=40`: card and processor agree (40 frames, at most 0.0025%); frames with the marks on looked at (`verify-races -Features ... -Golden <scratch folder>`)
- [x] 5.5 Split screen with them on (`-Dnfm.autotest.split=auto,3`, hidden): 39 of 40 frames at most 0.05%; one 3.6%, the HUD laid out at another size between the two draws (the race picture the same)

The live test and the blind review run once at the end of the extras work (tasks of `raycast-handling`).
