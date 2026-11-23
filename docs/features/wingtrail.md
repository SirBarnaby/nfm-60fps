# The Radical One's wing trail

Switch: **Radical One's wing trail** (`wingtrail`, cookie `xtrawing`), off by
default until 1.0. Requirements: `openspec\specs\effects` ("The Radical One's
wing trail", "The wing trail looks like vapour").

Code: `src\nfm\lit\features\wingtrail\`.

## When it shows

The Radical One (car 14, `radicalone`) trails vapour from both wing tips while
it has been off the ground for at least 3 ticks (0.15 s) and its driver holds
any of the four keys, which in the air turn, pitch and roll it. Not on the
ground, not in the air with no key held, not once wasted, and not on any other
car. Computer drivers count: their keys are latched like a player's.

A level per car rises 0.34 a tick while that holds (full in 3 ticks), falls
0.2 a tick once the keys are let go (gone in about a quarter of a second) and
0.5 a tick on the ground (gone in 2 ticks).

## How it looks

Each wing tip leaves a point in the air every tick, 20 at most, kept for a
second (19 ticks). A point drifts out from the car's line, slowing, the way a
tip vortex spreads, and keeps a little of the car's way. The stream between
the points is a ribbon facing the camera: half width 0.9 at the tip, 7 a
second on; white, in the stage's fog, dithered from 80% of the pixels at the
tip down to none at a second. Each point keeps the level it was made at, so a
trail let go thins from the tip.

The newest stretch runs from the last point to the tip. The car is drawn moved
on between ticks, so that stretch's tip end moves with the tip's last move
(`Canvas.moving` with a move per corner) and its other end stays put: the
trail reaches the wing in every frame and never steps.

## Where the tips are (`WingTips`)

In the model file's own numbers, `p(±70,-55,-135)` in `radicalone.rad`, the
wing's outer rear corners. The game scales a model as it loads it, and its
wheels with it, so the tips are scaled by the car's front wheel place over the
file's (`w(42,...)`): 67 / 42 for the game's Radical One.

## Cost

At most 2 streams per Radical One, 19 quads each, in the kept scene the card
places for every frame. Arrays are made at race start. No random numbers.

## Checks

`test\extras-check.ps1 wingtrail` flies a made-up Radical One: steering in
the air (and nothing on the ground, in the air with no key, on another car or
before 0.15 s), thin white streams from both tips, spreading and fading within
a second, the newest stretch moving with the car, and fading on letting go
and on landing. `test\verify-races.ps1 -Features wingtrail -NoGolden` shows
the races are unchanged with it on.
