# Tailwhip

Switch: **Tailwhip** (`tailwhip`, cookie `xtrawhip`), off by default until
1.0, in the Extras tab's Races group. Requirement: `openspec\specs\handling`
("Tailwhip").

Code: `src\nfm\lit\features\tailwhip\` (`TailwhipFeature`, both the race half
and the call-out; `TailwhipCheck` the checks).

## Playing

- Hold the **handbrake** and the **brake** and steer **left or right** while
  the car moves over the ground at 6 units a tick or more: it turns 1 degree
  a tick more than its steering alone gives, up to 3 degrees a tick more at
  30 units a tick or faster (19 ticks a second, so 57 degrees a second more).
  Standing, with one brake only or without steering nothing changes.
- The tail swings the way the steering turns the car as the swing begins (the
  other way for a car rolling backwards) and keeps to it while the keys are
  held. The game steers a car the other way once its engine pulls backwards,
  which the brake does within a few ticks; while the tail swings that is
  taken back out, so the car does not turn back in the middle of its slide.
- A tail still swings for 6 ticks after the keys let go. If one of the car's
  rear corners touches another car while it swings, that car is
  **tailwhipped**: it is turned 60 degrees over the next 15 ticks (15 in the
  first, a quarter of what is left each tick), the way the tail pushed it:
  hit ahead of its middle it spins one way, behind it the other. A wasted car
  is left alone.
- One swing is one tailwhip: a car that tailwhipped cannot again for 2
  seconds, however long the two stay touching.
- Every player reads it in their message line, as "Checkpoint!" is shown:
  `MAX Revenge tailwhipped Sword of Justice!  TAILWHIP!`, with "You" and
  "you" for the player's own car. The game has no chat, so the message line
  of each player (both in split screen) is where it is called out. The log
  has it too (`[extras] tailwhip: ...`).
- The computer's cars are under the same rule: one that holds both brakes
  and steers turns more, and can tailwhip. They do not seek it.
- With the switch off the extra does not play in the race at all: the game
  asks and gets 0, and nothing of it runs in the race tick.

Measured in hidden races of the game from the same start (`-Dnfm.seed=7
-Dnfm.autotest.whip=300`: full throttle, then both brakes and left for 28
ticks from about 220 units a tick), the degrees turned:

| Car | Switch off | Switch on |
| --- | --- | --- |
| Tornado Shark | 102 at most, back to 39 | 186 |
| MAX Revenge | 90, back to 86 | 174 |
| EL KING | 48 at most, back to 6 | 137 |

## In the game

- `Madness.drive`, where the steering turns a car on the ground: `car.xz +=
  FeatureBridge.raceTurn(...)` (`Feature.turn`), 0 without a race extra.
- `Madness.colide`, where two cars' corners are found touching:
  `FeatureBridge.raceTouch(...)` (`Feature.touch`), whichever of the two the
  game lets push the other.
- `RaceTick.turn` turns the tailwhipped car, `RaceTick.rearWheel` says which
  corners are a tail, `GameHooks.carName` gives the names.

## In the race state

It changes races, so it is a race extra like the powerups:
`TailwhipFeature.sync` holds each car's swing, its way, the spin it still has
to take, its rest and its count of tailwhips; run-ahead, rollback and the
verifier cover it; a recording notes it among the extras that played. It
throws no dice and uses `StrictMath` only. A recorded race made without it
plays without it.

## Checks

`test\extras-check.ps1 tailwhip` (cloud: `cloud/check.sh extras tailwhip`),
on a made-up race, named after the requirement's scenarios:

- **the turn**: both brakes and left turn a moving car 3 degrees a tick more,
  right the other way, less when slow; nothing with one brake, without
  steering or standing; where the steering alone would turn the car back the
  swing carries on; through the game's own call 0 with the switch off.
- **the hit**: a tail that is not swinging and a swinging car's nose do
  nothing; a swinging tail spins the other car 60 degrees, one way hit ahead
  of its middle and the other way behind it; two players each read their
  line; no second tailwhip in the same swing; a wasted car is left.
- **restored, the tailwhip plays on the same**: its state saved in the middle
  of a spin and restored.

`-Dnfm.autotest.whip=<race tick>` makes a hidden test run's player hold the
throttle and then both brakes and left for 28 ticks, and logs the turn tick
by tick (`[autotest] whip: ...`). With `-Dnfm.autopilot` and `-Dnfm.record`
it records races with tailwhips in them, which the verifier then plays back,
with rollback too.

Not checked by a machine: how it feels to drive, and whether the turn is the
"bit more easily" the owner means. That is the owner's to judge by hand.
