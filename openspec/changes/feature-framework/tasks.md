## 1. The package

- [x] 1.1 `features\Feature`, `Features`, `FeatureSettings` (the switches, `-Dnfm.features`, `-Dnfm.<id>`, cookies `xtra…`)
- [x] 1.2 `features\RaceView` and `nfm.lit.FeatureBridge` (filled after each real tick; hits from `regx/regy/regz`, none on speculative ticks)
- [x] 1.3 `features\Canvas`, `DrawQueue`, `Dice`; `nfm.lit.FeatureCanvas` (world, moving and car polygons, fog, dither)

## 2. Drawing

- [x] 2.1 `RetainedScene.MOVING` and `PROJECTED_VS` kind 7; `Precise` world, moving and car corners
- [x] 2.2 `SceneList.DITHER`, `SceneGeometry` alpha, `COLOR_FS` Bayer discard, `SceneRecorder` record and replay; the white flash on `COVER_BLEND`
- [x] 2.3 `drawWorldObjects` merges the queue (ground after the flat pieces, depth, after a car); `ContO.flat()`

## 3. Hooks and settings

- [x] 3.1 `GameSparker`: load and save the switches, race start, tick end, scene begin and end, run-ahead pose
- [x] 3.2 `SettingsScreen` Extras tab (a row per registered feature) and shared tab spacing
- [x] 3.3 `DevTool` `extras` and its description line
- [x] 3.4 `test\verify-races.ps1 -Features`
- [x] 3.5 README Extras section and options rows; `docs\features\README.md`

## 4. Gates

- [x] 4.1 `build.ps1 -Quick`, `test\verify-races.ps1` (20 matched, golden frames all the same), `test\perf.ps1`, `test\spec-check.ps1`
- [x] 4.2 `test\verify-races.ps1 -Features all -NoGolden` (20 matched; no feature registered yet, so this proves the frame alone)
- [x] 4.3 `-Dnfm.projectiontest=40` with the GPU renderer (hidden autotest)

The live test (`test\live.ps1`, `-Split`) and the blind review
(`test\blind-review.ps1 -Base 201142a`) run once, at the end of the extras
work, as tasks of the last change (`raycast-handling`).
