## 1. The synthesis

- [x] 1.1 `EngineGrains.Cycles`: a recording cut into its cycles (the period by autocorrelation, refined)
- [x] 1.2 `EngineGrains.Voice`: one cycle at a time, grains laid at the firing rate (pitch-synchronous overlap-add), the level kept, the cycles walked with a little variety
- [x] 1.3 `EngineGrains.Tone`: the front (engine and intake) and the back (exhaust, growl, pops on a lift)
- [x] 1.4 `EngineGrains.Skid`: the skid recordings in overlapping grains from their steady middle

## 2. The sound

- [x] 2.1 `Sound3D.Car`: `rebuilt`, the pose, the wheels' skids and loose ground; `Sound3D.carBody`
- [x] 2.2 `Sound3D`: the rebuilt voices of a car: two streamed sources (front, back) and one per wheel, placed by the pose, muffled and dulled as the engines; the five recordings silent and the one-shot skids left out while it plays the car
- [x] 2.3 Fading between the two when the switch turns during a race; sources paused while silent

## 3. The extra and its hooks

- [x] 3.1 `EngineSound` (switch `enginesound`): the body at race start, the wheels' skids after each tick
- [x] 3.2 `SkidSlip` public; `FeatureBridge.soundCar`; `GameSparker.publishSound` fills the pose and the extra's fields
- [x] 3.3 `EngineCheck`: the cycles found, the firing rate follows the pitch with the tone kept, the front brighter than the back, one wheel's skid only from that wheel; listening waits for the owner
- [x] 3.4 Register in `Features.ALL` and `ExtrasCheck`; README Extras line and options row; `docs\features\enginesound.md`

## 4. Gates

- [x] 4.1 `build.ps1 -Quick` compiles
- [ ] 4.2 Not run from 16:07 UTC on 23 Nov (the owner tests by hand): `test\extras-check.ps1`, `test\verify-races.ps1`, `test\perf.ps1`, `test\spec-check.ps1`, `test\verify-sound.ps1`
