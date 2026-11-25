## 1. The race part

- [x] 1.1 `Chassis` (a car's tyres handed over), `HandlingRace`: the step count, springs, loads, tyres, balance, drift assist, the airborne springs and kicks, `sync`
- [x] 1.2 `HandlingFeature`: the switch's feature (changes races), the race calls, `plays`
- [x] 1.3 `RaceTick.pitch`, `RaceTick.roll` (defaults), `FeatureBridge`: `tyres`, `springs`, the `Chassis` copy in and out

## 2. The game's side

- [x] 2.1 `Madness.drive`: the tyre hook in the grip block, the game's pull only without it; the game's `tilt` left out while the springs show it
- [x] 2.2 `CarBody` and `Features.carBody` (one owner), `FeatureBridge.body`, the frame's share; `Plane.d` asks it for a car's body polygons

## 3. The look

- [x] 3.1 `HandlingFeature`: the body pose from the springs after real ticks, between ticks at the frame's share, the body polygons rolled, pitched and lowered over the axles

## 4. Checks and docs

- [x] 4.1 `HandlingCheck` in `ExtrasCheck`: 120 steps a second, springs settle and load the front under braking, the circle shares braking and turning, a slide held past the peak, the drift assist's cap, the state restored plays on the same
- [x] 4.2 `FeatureSettings`' line for the switch; README (Extras, options row); `docs\features\handling.md`
- [x] 4.3 `build.ps1 -Quick` compiles; the gates are not run (owner's note, 23 Nov 16:07 UTC)
- [x] 4.4 Commit; archive (no spec edits: `skip_specs`)
