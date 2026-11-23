## 1. The trail

- [x] 1.1 `WingTips`: the Radical One's tips in its file's numbers, scaled by its front wheel's place
- [x] 1.2 `WingTrailFeature`: when (0.15 s in the air, a key held), the level rising and falling, a ring of points per tip drifting out
- [x] 1.3 Drawing: ribbons facing the eye, thin at the tip and spreading, fading in a second; the newest stretch with per-corner moves so it reaches the wing in every frame

## 2. Checks, settings, docs

- [x] 2.1 `WingTrailCheck`: steering in the air (and nothing on the ground, without keys, on another car, before 0.15 s); its look; spreading and fading; no steps; letting go and landing
- [x] 2.2 Register it in `Features.ALL` and `ExtrasCheck` (its switch, row and console command came with the framework)
- [x] 2.3 README Extras line and options row; `docs\features\wingtrail.md`

## 3. Gates

- [x] 3.1 `test\extras-check.ps1`: every check passes
- [x] 3.2 `build.ps1 -Quick`, `test\verify-races.ps1` (20 matched, golden frames all the same), `test\perf.ps1`, `test\spec-check.ps1`
- [x] 3.3 `test\verify-races.ps1 -Features skidmarks,skidzones,wingtrail` (frames into a scratch folder, not `test\golden`): 20 matched
- [x] 3.4 Hidden GPU autotest with it on and `-Dnfm.projectiontest=40`, single and split screen: card and processor agree (single at most 0.0025%; split 39 of 40 at most 0.05%, one 3.6% where the HUD was laid out at another size between the two draws, which happens with every extra off too)

The live test and the blind review run once at the end of the extras work (tasks of `raycast-handling`).
