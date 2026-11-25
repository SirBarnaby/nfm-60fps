## Why

The owner, 23 Nov 2026, on the extras brief: "Keep those in also. Implement." `handling` holds the requirement written that day, approach B of three, "Arcade raycast suspension": after 1.0 the cars ride on a spring and damper at each wheel, with smooth tyre grip shared between braking and turning, weight moving to the outside wheels in corners and forward under braking, and a drift assist that keeps slides catchable, ticking 120 times a second; it stays arcade, and stunts stay. It changes races, so it comes after 1.0 (`release-1-0`): the switch **Suspension handling** (`handling`, cookie `xtragrip`, a race switch) already listed by the extras framework is off by default, as it is in every hidden run, and a race without it plays exactly as before.

The race-extra machinery the powerups built (a tick inside `advanceRace`, state in one sync method that run-ahead, rollback and the verifier capture, the recording's list of race extras) carries this extra too. What is new is where it acts: inside `Madness.drive`, at the tyres.

No requirement is MODIFIED or REMOVED and none is added. From 16:07 UTC on 23 Nov the owner tests by hand: this change runs no test and lands when it compiles and reads right.

## What Changes

- **`src\nfm\lit\features\handling\`**:
  - `HandlingRace`, the race part, stepping 120 times a second of game time inside the 53 ms race tick (6 or 7 steps a tick, 159 every 25 ticks):
    - a spring and damper at each wheel, its travel driven by the weight moving forward under braking and back under power, to the outside wheels in corners, by the ground pitching and rolling under the car, and by landings;
    - tyres with one grip shared between braking and turning (a friction circle): a smooth curve that grips up to a peak and lets go to 70% of it in a full slide, so a slide can be held on the throttle and caught by steering into it;
    - the load on each axle setting the balance: braking into a corner turns the car in more, power out of it a little less;
    - a drift assist that turns the car's nose toward where it is going once a slide passes 38 degrees, and helps a counter-steer;
    - the game's own steering, gears, air control, stunts, collisions and damage left as they are.
  - `HandlingFeature`, the switch's feature: the race calls, and the look: each car's body drawn on its springs (pitched, rolled and lowered over its wheels, which stay on the road), between ticks as the cars move.
  - `HandlingCheck`: a check per part, on made-up cars.
- **Hooks**: `Madness.drive` hands the tyres to the extra (`FeatureBridge.tyres`, `-1` without it, so the game's own grip runs) and leaves out the game's own body lean while the springs show it; `Plane.d` asks the extra for a car body polygon's corners while it plays (`Features.carBody`); `FeatureBridge` passes the car through a `Chassis`; `RaceTick` gains each car's pitch and roll.
- **README**: the extra in the Extras section and its options row. **`docs\features\handling.md`**: how it works and its numbers.

Looked at first (rule: before a new subsystem): raycast-suspension write-ups for games (a spring force from each wheel's ray with a damper on its speed, https://lupine-vidya.itch.io/gdsim/devlog/677580/workshop-i-suspending-a-rigidbody-mirror, https://gamedev.net/forums/topic/699625-arcade-car-physics/), the Pacejka "magic formula" tyre (https://en.wikipedia.org/wiki/Hans_B._Pacejka, and the simplified forms on https://gamedev.net/forums/topic/462784-simplified-pacejka-magic-formula/), the friction circle (Kamm's circle), and a TU Delft thesis finding that drift equilibria exist with the magic formula and not with a linear tyre (https://repository.tudelft.nl/file/File_a897e66d-c21e-4804-9066-a33406e4b323), which is why the tyre curve falls past its peak: that is what lets a slide be held. No library fits: the engines' vehicle bodies (Bullet's raycast vehicle, Unity's WheelCollider) replace the whole car body, and this game's body, stunts and collisions must stay. Their designs are followed inside the game's own drive.

## Capabilities

### New Capabilities
<!-- none: the requirement exists in handling -->

### Modified Capabilities
<!-- none -->

Specs touched: none changed. Read: `handling`, `release-1-0`, `settings`, `split-screen`, `performance`, `code-health`, `done-and-testing`.

## Impact

- New files: `src\nfm\lit\features\handling\`, `docs\features\handling.md`.
- Hooks: `Madness.drive` (the tyres, the body lean), `Plane.d` (a body polygon's corners), `FeatureBridge`, `Features` (the tick count, the body), `RaceTick` (pitch, roll), `ExtrasCheck`, `FeatureSettings` (the switch's line).
- With the switch off nothing a race reads changes: `FeatureBridge.tyres` returns `-1` and the game's own grip runs as written, the lean is the game's, no `extras` group, nothing saved; the recorded races, the golden frames and the sound check stay as they are.
- The gap, reported: the requirement's first check says the race steps 120 times a second. The suspension and the tyres do; the rest of the race (the game's drive around them, the computer drivers, the checkpoints) still steps 19 times a second, as it must until the whole race is rebuilt for a new tick after 1.0.
