## Context

The Radical One (`radicalone`, car 14) is the game's winged car: its model has a rear wing whose outer corners are at `p(±70,-55,-135)` in `radicalone.rad`. The game scales a model as it loads it (`ContO`, the file's `div`), and its wheels with it; for the Radical One the front wheel's 42 in the file becomes 67. The extras framework hands each feature a copy of the race after every real tick (`RaceView`): each car's place, angles, speed, keys, whether it is on the ground and for how many ticks it has been in the air.

## Goals / Non-Goals

**Goals:** the two wing-trail requirements in `effects`, each with a check; the graphics card drawing the trail; no steps between ticks; 250+ fps unchanged; nothing allocated per frame or tick; races unchanged.

**Non-Goals:** a trail on other cars, or one that depends on humidity or lift; the owner asked for the Radical One, while it is steered in the air.

## Decisions

1. **When.** "In mid-air" is at least 3 ticks (0.15 s) off the ground, so a bump does not puff; "actively steering it in mid-air" is any of the four keys held (in the air they turn, pitch and roll the car), not wasted. Computer cars count too: their keys are latched the same way.
2. **Coming and going.** A level per car rises 0.34 a tick while steering in the air (full in 3 ticks), falls 0.2 a tick when the keys are let go (gone in 5-6 ticks, about a quarter of a second) and 0.5 a tick on the ground (gone in 2). Each point keeps the level it was made at, so a released trail thins at the tip first.
3. **Vapour.** A ring of 20 points per wing tip, one a tick at the tip while the level is above 0. Each point drifts out from the car's line at 0.35 units a tick (slowing by 10% a tick), the way a tip vortex spreads, and keeps 8% of the car's move. Its half width grows from 0.9 at the tip to 7 a second on; its cover is `0.8 · level · (1 − t)(1 − 0.3t)` for `t` its age in seconds, nothing at a second. White (`0xf4f6fa`), fogged, dithered.
4. **A ribbon facing the eye.** Each stretch between two points is a quad whose width is across both the stretch and the line to the eye, so it is never seen edge-on.
5. **No steps.** The newest point is at the tip where the car was after the tick, but the car is drawn moved on between ticks. The newest stretch is therefore drawn with per-corner moves (`Canvas.moving`, the per-corner form added with the skidmarks' particles): its tip end moves with the tip's last move, its older end stays in the air, so it reaches the wing in every frame. Older stretches are fixed in the world.
6. **Where the tips are.** `WingTips` keeps the tip in the file's numbers and scales it by the car's front wheel place over the file's (67 / 42), so the trail follows the model as loaded, mods included.

## Performance

Per tick: at most 2 streams per Radical One in the race, 20 points each, a few operations a point. Per kept scene: at most 19 quads per stream. Arrays are made at race start for the race's cars and kept. With the switch off the feature is not active: the bridge's checks only.

## Races stay identical

The feature reads `RaceView` only and uses no random at all. `test\verify-races.ps1 -Features wingtrail -NoGolden` plays the 20 recorded races with it on and must give 20 matched.
