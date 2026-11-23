## Context

The game's cars have no tyres: every tick all four wheels' velocities chase one target, the engine's speed along the car's heading, by at most the car's grip (`Madness`, the grip block). Turning at speed costs grip, the handbrake far more, loose ground scales it down. While the grip is enough the velocity reaches the target and has no sideways part after the tick; when it is not, the car slides. The extras framework hands each feature a copy of the race after every real tick (`RaceView`), with each wheel's contact point, whether it touches, its surface and the sideways and lengthwise slide.

## Goals / Non-Goals

**Goals:** the five skidmark requirements in `effects`, each with a check that plays its maneuver; the graphics card drawing every mark and particle; 250+ fps unchanged; nothing allocated per frame or tick; races unchanged.

**Non-Goals:** tyre physics. The marks show what the game's own physics does; they never change it.

## Decisions

1. **The game's slide is the signal.** `slipSide` and `slipLong` after the tick are exactly the velocity the grip could not take away, so a car holding its line never marks, and every slide the game makes (handbrake, loose ground, hits, landings) does. A per-wheel kinematic model (the turn's sideways speed at each axle, the steering angle) was weighed and rejected: the game's steering does not follow tyre geometry, so it would mark every fast turn the car holds.
2. **Sharing the slide over the wheels.** The body's angle to its path decides whose slide it is: under 6 degrees the fronts (understeer), over 18 the rear (power slide), smoothly between; the handbrake always the rear, the fronts rolling (share 0.05, under the start threshold at any speed). In a slide the rear swings with the car's turn; in a landing's first two ticks every wheel does, so the first wheel down draws a little hook. The fronts' own angle to the path includes their steering, so countersteering straightens them alone. Outside wheels carry up to 35% more. The fronts never spin (the rear drives).
3. **Thresholds.** The slip is lengthwise as a share of the speed and sideways twice the slip angle's sine, so the brief's "slip under 0.1 rolls cleanly" holds for both (about 3 degrees of slip angle). Start: slide over 4 units a tick and slip over 0.12 (2.5 in a landing's first ticks); end: slide under 2.5 or slip under 0.1. A mark therefore ends the tick its wheel rolls cleanly (check: one wheel locks, last piece one tick before the clean one).
4. **A ring of strips.** 4,096 pieces in parallel arrays; each wheel's mark is a strip whose pieces share corners; width across the motion from the patch (16 wide, 18 long). A move under 3 units darkens the piece instead (the burnout's spot). A fresh mark's first piece ends under the wheel so the strip never folds back. The oldest piece is replaced; a wheel still drawing from it starts afresh.
5. **Mud.** A tyre on loose ground gains 0.2 a tick of its colour (the piece's colour from `Trackers.c`, or the stage's ground off every piece), and on the road loses it as `exp(-distance / 600)`: 37% after 10 m, 5% after 30 m (check: 1 level from rubber 30 m on).
6. **Hits.** `Madness.regx/regz` push along world axes; a hit at least 30 hard whose axis is mostly across the car (|cos| or |sin| of the yaw over 0.5) scrubs the four wheels for 4 ticks from that tick.
7. **Body scrapes.** Rolled 50-130 degrees, the lower of the body's two side edges (just outside the wheels, half the wheel drop down); 130 or more, or capsized, the roof; if within 8 units of the ground under the car and moving, a metal mark and two sparks a tick.
8. **Drawing.** Road pieces are one `ground` item, drawn oldest first after the flat road pieces; ramp pieces (`RaceView.onRamp`) are `at` items pulled 15% towards the eye, because the game sorts a ramp by its middle (and its grounded factor of at least 1.05 puts it farther). Pieces over 200 m away are left to the fog. Particles are `at` items with `Canvas.moving`: a puff is a hexagon facing the eye that turns slowly as it grows, a clod a small square tumbling as it falls, a spark a glowing streak. A per-corner `Canvas.moving` (added here) gives each corner its own move to the next tick, so the growing and turning are as smooth as the flight; the first frames showed square puffs all facing the same way, which read as flat cut-outs rather than the brief's "fancy physics-style particles". The angle and the way of turning come from the particle's slot, so they need no random. The simple detail throws half the particles.
9. **Zones.** An open-addressing grid of 4 m squares (16,384, filled to three quarters at most) of summed slide; written on `raceEnded` through a temporary file and an atomic move, only when `keepsFiles`; read at `raceStarted` keeping the 600 heaviest, shown if at least 35% of the most.
10. **Checks.** `nfm.lit.features.ExtrasCheck` (run by `test\extras-check.ps1`) gives each feature a class of checks in its own folder; `SkidmarkCheck` drives the feature with a hand-made `RaceView` (the Radical One's wheel places) through each maneuver and measures the ring. `CheckCanvas` counts what would be drawn.

## Performance

Per tick: `SkidSlip` and the marking are a few arithmetic operations per wheel (8 cars, 32 wheels). Per kept scene (once per tick on the card): at most 4,096 quads plus the detail threads, each a fog lookup and four projected corners; the card then places them for every frame with everything else, so the frame rate does not depend on them. Software draws them each frame (not the 250 fps path). Every array is made at race start (or once); the ring, the particle pool and the grid are fixed. With the switches off the features are not active: the bridge's checks only.

## Races stay identical

The feature reads `RaceView` only and draws its dice from its own `Dice` (seeded from the race's seed), never the race's random stream. `test\verify-races.ps1 -Features skidmarks,skidzones -NoGolden` plays the 20 recorded races with both on and must give 20 matched; with them off the gates give 20 matched and every golden frame the same.

## Risks / Trade-offs

- [The marks are the game's slide shared by rule, not a tyre model] → each maneuver's look is checked against the owner's table; the rule is in one class.
- [Ramp marks pulled towards the eye can be drawn over a car standing on them] → only on ramps, only for the moment a car stands on its own fresh mark there.
- [A full grid (more than 12,000 squares slid in one race) stops counting] → a stage that large is beyond any in the game; the rest is simply not counted.
