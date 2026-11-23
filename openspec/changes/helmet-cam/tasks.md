## 1. The extra

- [x] 1.1 `Head`: three damped springs driven by the car's acceleration in its own frame, the look into the corner, the g-force felt, a reset on a jump
- [x] 1.2 `Cockpit`: the eyes, the polygons drawn from them and the dashboard, from the car's model
- [x] 1.3 `HelmetCam`: a head per player after each tick; the camera from a car pose (whole numbers and fractions); the vignette's strength

## 2. Hooks in the game

- [x] 2.1 The view key: chase, around, watch, helmet (`FeatureBridge.changeView`); reset with the views; `-Dnfm.helmetview`
- [x] 2.2 `GameSparker`: the shown camera in `renderScene`, `poseSpan`, `paneSpan` (and the run-ahead frame through them); the helmet's own depths
- [x] 2.3 `PoseSpan`, `FramePose`: the camera's fractions
- [x] 2.4 `ContO`: the own car's polygons the helmet does not show
- [x] 2.5 The vignette: `Shaders.VIGNETTE_FS`, `GpuDisplay.vignette`, the kept scene's strengths, the software gradient

## 3. Checks, settings, docs

- [x] 3.1 `HelmetCheck`: braking (forward, overshoot back), acceleration, a corner (outside, looking in), a landing, the vignette, the eyes and the cockpit, the camera aligned with the car; the lap waits for the owner
- [x] 3.2 Register in `Features.ALL` and `ExtrasCheck`; the switch's help says what it does
- [x] 3.3 README Extras line and options rows; `docs\features\helmetcam.md`

## 4. Gates

- [x] 4.1 `test\extras-check.ps1`: every check passes (the lap waits for the owner)
- [ ] 4.2 `build.ps1 -Quick`, `test\verify-races.ps1` (20 matched, golden frames all the same), `test\perf.ps1`, `test\spec-check.ps1`, `test\verify-sound.ps1`: built, and verify-races passed with every extra off (exit 0, golden frames 95 same, 0 changed); perf, spec-check and verify-sound not run: from 16:07 UTC on 23 Nov the owner tests by hand
- [ ] 4.3 `test\verify-races.ps1 -Features looks` with `-Dnfm.helmetview=on`: 20 matched: not run (as 4.2)
- [x] 4.4 Hidden GPU autotest in the helmet view with `-Dnfm.projectiontest=40`, single and split screen: single screen at most 0.09% of pixels differ; split screen the same but for three frames of 3.3 to 3.9% (the split HUD frame seen before this change; the run without the helmet to compare them was stopped when testing moved to the owner), and the split run ran out of time with verify-races running beside it

The live test and the blind review run once at the end of the extras work.
