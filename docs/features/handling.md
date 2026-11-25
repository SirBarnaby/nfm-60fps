# Suspension handling

Switch: **Suspension handling** (`handling`, cookie `xtragrip`), off by default until
1.0, in the Extras tab's Races group. Requirement: `openspec\specs\handling`
(approach B, "arcade raycast suspension": a spring and damper at each wheel,
one tyre grip shared by braking and turning, weight moving, a drift assist,
120 steps a second; it stays arcade and stunts stay).

Code: `src\nfm\lit\features\handling\` (`HandlingRace` the race part,
`HandlingFeature` the switch and the body drawn on its springs, `Chassis` what
the game hands the tyres, `HandlingCheck` the checks); the game's side in
`Madness.drive` (the tyre hook, the game's own lean left out),
`nfm.lit.FeatureBridge` (`tyres`, `springs`, `body`) and `Plane.d` (a body
polygon's corners, through `Features.carBody`).

## Driving

The game's steering keys, gears, top speeds, air control, stunts, collisions
and damage are as they were; the cars' own grip and bounce numbers are the
game's. What changes is the tyres and the body.

- **120 steps a second.** Inside each 53 ms race tick the springs and tyres
  take 6 or 7 steps (159 every 25 ticks), so the tyres feel each step's loads.
  The race around them (the rest of `drive`, the computer drivers, the
  checkpoints) still steps once a tick: see the gap below.
- **Springs.** Each wheel on a spring and damper with 16 units of travel
  either way: 1.4 to 1.85 Hz and a third to two fifths of critical damping by
  the car's bounce (the cars that leaned most in the game ride softest).
  Braking pushes the front springs in and lets the rear out, power the other
  way (0.5 units per unit of speed lost or gained a tick, times the bounce);
  a corner pushes the outside springs in (0.55 per unit of sideways pull). A
  landing after two ticks in the air kicks every spring in by its fall; a
  ramp or bump turning the ground under the car kicks the springs on its
  rising side in, so the body lags and settles. In the air they hang 5 long.
- **Tyres.** One grip for braking and turning together, a circle: each step
  each wheel's slip (the velocity the game wants less the wheel's) is pulled
  in along itself by a smooth curve (the magic formula's shape, C 1.5): in
  proportion for a small slip, the most at 70% of the grip, falling to 71% of
  that in a full slide. The grip is the game's (its losses to steering, the
  handbrake and grass taken), times 1.1 so the circle grips as the game's
  square does on average, times the axles' grip for their loads. The upward
  pull is the game's.
- **Load.** A wheel's load is `1 + travel/18`; an axle grips by the mean of
  `load^0.8`, so moving weight costs a little grip in all.
- **Balance.** The steering's turn grows by half the front axle's grip over
  the rear's: braking into a corner turns the car in more, power out of it a
  little less. Forwards above 20.
- **Drift assist.** Past 38 degrees between the nose and where the car goes,
  the nose is turned toward it by 60% of the excess each tick; steering into a
  slide turns it a quarter more; at most 12 degrees a tick. Because the tyres
  let go past their peak, a slide held on the throttle stays a slide; steering
  into it brings the slip back under the peak and the grip back.
- **Dust and skids.** While the tyres slide past their peak the game is told
  a lower grip, so its own dust, skid sounds and the skidmarks follow the
  slide.
- **The lean.** The game's own lean (`tilt`, the whole car turned, wheels
  too) is left out; the springs lean the body instead.

## The body on its springs

After each real tick each car's body pose is worked out from its springs:
pitched by the front and rear springs' difference over the wheelbase (at most
7 degrees, nose down under braking), rolled by the right and left springs'
over the track (8, the outside down in a corner) and lowered by 70% of their
mean. Each of the car's body polygons (not its wheels') is drawn rolled and
pitched about the axles and lowered, so the wheels stay on the road while the
body moves over them; between ticks it is drawn at the frame's share of the
way from the tick before, as the cars move. With the graphics card's kept
scenes, recorded once a tick, the body moves in tick steps.

## In the race state

Everything `HandlingRace` changes is race state, saved and restored by
`HandlingRace.sync` (each wheel's travel and speed, each car's last speed,
pitch and roll, ticks in the air, last fall, the part of a degree left, its
bounce, whether its tyres ran this tick; the tick count). `RaceSnapshot`
carries it with the race, `RaceState` hashes it in the `extras` group while it
plays, and a recording names it (`RaceLog` format 7). Its trigonometry is
`StrictMath`; it draws no dice. With the switch off `FeatureBridge.tyres`
returns `-1F` before touching anything, the game's own grip and lean run as
written, and nothing is saved or hashed.

## The gap

The requirement's first check says the race steps 120 times a second of game
time. The suspension and the tyres do (6360 steps in 1000 ticks); the race
around them still steps 18.9 times a second, because every number in
`drive`, the computer drivers and the checkpoints is per 53 ms tick, in whole
degrees. Stepping the whole race at 120 Hz is the rebuild after 1.0.

## Checking

`test\extras-check.ps1 handling` plays the race part on a made-up car: the
120 steps a second, braking loading the front and the springs settling, one
grip shared by braking and turning (a diagonal slip pulled as far as a
straight one), the grip falling to about 71% past its peak, the drift assist
turning a 60 degree slide back, and the state restored playing on the same.
The other check is the owner's drive: corners, bumps, a jump and a stunt.
