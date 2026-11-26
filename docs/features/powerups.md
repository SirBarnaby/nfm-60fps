# Powerups

Switch: **Powerups** (`powerups`, cookie `xtraboxes`), off by default until
1.0, in the Extras tab's Races group; under it **Explosion flash** (`flash`,
cookie `xtraflash`, on by default). Requirements: `openspec\specs\powerups`
(spawning with a pickup sound and a hum, nitrous, dynamite with the map
deforming, the bomb dropped behind, rare boxes, two nitrous burning red,
nitrous's slippery handling, dynamite's slight aim, stylised explosions with
the flash, dynamic smoke) and `openspec\specs\realism` (the exhaust flame
while nitrous burns).

Code: `src\nfm\lit\features\powerups\` (`PowerupRace` the race half,
`PowerupsFeature` the look, sound and messages, `PowerupDrawing` the shapes,
`PowerupSounds` the sounds, `PowerupsCheck` the checks); shared with the
other extras that explode, `features\BlastPush` (what an explosion does to
the cars, in the race), `features\Explosions` (its look, sound and smoke)
and `features\Wind` (the race's wind); the framework's race-extra hooks in
`Features`, `RaceTick`, `RaceInts` and `GameHooks`, and the game's side in
`nfm.lit.FeatureBridge` (`RaceAccess`, `Hooks`).

## Playing

- **Boxes**: a row of three, 170 units apart across the road, 70 over it, at
  one checkpoint in four of the first build's (which had a row at each
  checkpoint, every second one on a stage with more than 16), the rows
  spread evenly round the lap, one at least. That makes powerups about four
  times rarer: the check counts 0.26 of the first build's pickups over laps
  of 4 to 33 checkpoints. A car whose middle comes within 60 units plus 0.4
  of its radius across, and 160 up or down, takes one if it holds nothing
  and is not wasted. The box comes back 4 seconds later.
- **What a box gives**, by the car's place (a choice of the build's, not a
  requirement): in a hundred, nitrous 40, dynamite 35, bomb 25; the leader,
  with the field behind it, nitrous 45, dynamite 15, bomb 40; the last car,
  with the field ahead, nitrous 45, dynamite 45, bomb 10.
- **The key**: Use powerup, Ctrl by default (Keys tab); in split screen R for
  Player 1, L or number pad 2 for Player 2; on a controller the right stick's
  button. A press is used on the tick it starts. The key is latched only
  while a race extra plays, so a race without powerups records the same keys.
- **Nitrous**: 2 seconds of 60 more boost (the stunt power's scale, which
  the game turns into pull in every gear and top speed), then fading to none
  over 1 second. As the game's boost, it pulls only on the ground with the
  throttle held. While it burns the car is slippery: the grip the game works
  out for the tick is multiplied by 0.55, back to all of it as the nitrous
  fades (`Madness.drive`: `grip = FeatureBridge.raceGrip(im, grip)`, the
  grip untouched without a race extra), so the tyres let go sooner and a
  slide lasts longer.
- **Two nitrous at once**: a nitrous fired while the last still burns (fire
  one just before a row of boxes, take another, fire it) overlaps it for as
  long as the first had left. While they overlap the flame is red and 1.35
  times the size; when one is left it is blue again. The boost is one
  nitrous's: two do not add up.
- **Dynamite**: 2 seconds after the pickup the stick leaves the car's nose at
  its speed plus 60 units a tick forwards and 16 up, falling 1.4 a tick per
  tick. The way it leaves is turned toward the nearest car still racing
  ahead of the nose (within 6000 units) by 2.5 degrees at most, so the
  driver still has to point the car. It blows up on the ground, on a car
  (within 90 units; the thrower's own only after 12 ticks) or after 45
  ticks. Every car within 520 units is hit by `s = 1 - d/520`: each wheel
  pushed away by 55·s and up by 30·s to 45·s (the nearer wheels more, so the
  car tips), damaged by a quarter of its maximum times s, its driver's
  screen shaken. A blast within 150 units of the ground leaves a crater.
- **Bomb**: picked up like nitrous. A press of the key puts it on the road
  the car is on, its radius and 60 units behind the car's middle (dropped in
  the air it falls to the flat ground under it). It lies there until a car
  that is not wasted drives onto it: within 35 units plus 0.4 of the car's
  radius of the way the car went this tick (so a fast car cannot pass over
  it between two ticks), and within 150 up or down. The car that dropped it
  sets it off only after a second. The bang: that car takes 30% of its
  maximum damage and is thrown slightly (each wheel 20 away and 22 to 34
  up); cars within 300 units get their share by how near they are. It
  leaves no crater. Twelve can lie at once; a thirteenth takes the place of
  the one out the longest.
- **Computer cars**: nitrous on the ground with the throttle held, going
  straight above 60% of top speed, 12% a tick. A bomb with a car 150 to 1400
  units behind in line with the tail, 30% a tick; or anyway, 5% a tick,
  after carrying it 15 seconds. Dynamite throws itself.

## In the race state

Powerups change races, so everything `PowerupRace` changes is race state:

- `PowerupRace.sync(RaceInts)` saves, restores and visits it in one order
  (`x = s.value(x)`), so saving and restoring cannot drift apart.
- `RaceSnapshot` saves it after the race's own values and puts it back on
  restore (run-ahead, rollback); `differences()` reports `race extras`.
- `RaceState` walks it as a group of its own, `extras`, after `random`, only
  while a race extra plays (with each player's key first): a race without
  one hashes and dumps exactly what it did. `Control.power` is left out of the
  reflected driver for the same reason.
- Its dice are a SplitMix64 seeded from `RaceRandom.derive(seed, 7)`; the
  race's own stream is never drawn from. Trigonometry is `StrictMath`.
- `RaceLog` format 7 writes the extras that played (ids), after an empty
  `custom` field (the Custom Game branch's format 6), and the input byte's
  bit 64 is the key (masked out of the canonical state). The verifier plays a
  recording with the extras it names, whatever the switches.
- The boost is one line in `Madness.drive` after the game's own:
  `boost += FeatureBridge.raceBoost(im)`, 0 without a race extra; the grip
  one more, `grip = FeatureBridge.raceGrip(im, grip)`, which hands the grip
  back untouched without one.

## The look and the sound

`PowerupsFeature` reads `PowerupRace` after real ticks only and copies what
it draws, so a run-ahead tick is never seen or heard. What happened in a
tick comes from a ring of 32 events (pickup, nitrous fired, throw, blast,
bomb dropped, bang).

- Boxes: amber cubes spinning and bobbing, a darker diamond on each side,
  their faces turned away left out, each corner given its move to the next
  tick. The held powerup floats over the roof: a two-tone diamond (blue
  nitrous, red dynamite), or the bomb itself. The stick tumbles end over
  end, sparking.
- The bomb: a black ball of twenty flat faces, lit from dark to a dull
  shine, a short fuse out of its top and a spark at the fuse's end that
  flickers every tick; 24 units in radius on the road, 15 over a roof.
- Nitrous: a flame from the tail, blue for one, red and bigger for two at
  once; or Damage and heat's exhaust flame when that is on, which follows
  the same colours and size.
- An explosion (`Explosions`, by size: 1 a dynamite stick, 0.5 the bomb's
  bang): glowing puffs (0.6 s), clods and sparks, all dithered; the game's
  own white start flash (one drawn frame) when it goes off near a player's
  car (6000 units for a stick, 4500 for a bang) and Explosion flash is on;
  the controller's rumble by how near.
- The smoke is dynamic. A column of puffs rises from the blast and spreads,
  staying about 8 seconds; a low ring of smoke and dust, tinted by the
  stage's ground, rolls out along the ground. From then on it lives with
  what is around it: it drifts with the race's wind (`Wind`: one direction
  and a speed of 0.6 to 1.5 units a tick for each race, from its seed), it
  stays on top of the ground it was made over, every car within its radius
  and 80 units shoves it aside and drags it along (a little of it follows
  in the car's wake out to twice that), and a later explosion blows the
  smoke still within 900 units outwards. It is a look only: the race does
  not feel the smoke or the wind.
- Craters: a scorched patch, then a bowl 18 deep inside a raised lip, each
  face shaded by its slope, drawn on the road for the rest of the race (16).
- Sounds: the dynamite's fuse, the bomb's drop and the explosions are
  recordings (`sounds\README.md`); the pickup's chime, the nitrous hum and
  rush, the bomb's ticking and the throw are synthesized once from fixed
  seeds. With the 3D sound each plays at its car (`Sound3D.carSound`), an
  explosion held at its place (`Sound3D.pointSound`), and each car's hum
  from a looping source of its own that follows it (`Sound3D.Car.hum`).
  With Classic sound a player hears their own pickup, uses and hum, and
  explosions near them; these clips play on their own, outside the game's
  list of sounds, so the game's own sound commands (and the recorded races'
  sound check) stay as they were.
- Messages, in the game's message line: "Nitrous! Ctrl to use", "Dynamite!
  Thrown in 2 seconds", "Bomb! Ctrl to drop it behind you", with the
  player's own key.

## Checking

`test\extras-check.ps1 powerups` (`cloud/check.sh extras powerups`) plays
the race half on a made-up straight and draws the look half into a canvas
that counts: the boxes' rows and how rare pickups are beside the first
build, a pickup, nitrous's three seconds, two nitrous at once (red and
bigger for exactly the overlap, then blue), the grip nitrous leaves and a
corner taken by the game's grip rule with and without it, dynamite thrown at
two seconds, its aim (1 degree to an enemy 1 degree off, 2.5 to one 6 off,
one 40 off still missed) and its blast throwing a car and denting the
ground, the bomb dropped behind and set off by a car passing at 200 units a
tick, its owner's second of safety, a computer car dropping one, every
place given every powerup, an explosion's flash, fireball and lasting smoke,
the smoke drifting downwind, staying on the ground and moved by a car and by
a second blast, and the state saved, played on and restored playing on the
same.

The corner in the grip check is a model of the game's rule, not a race: the
real thing is `Madness.drive` with the hook above. Races with powerups on
can be recorded and played back in the cloud (`-Dnfm.record=<folder>
-Dnfm.autopilot=3 -Dnfm.features=powerups`, then `cloud/check.sh` style
`-Dnfm.verify=<folder> -Dnfm.verify.rollback=50`): three such races matched
tick for tick with rollback on 26 Nov 2026. Recorded races with powerups in
the repository (`test\races-extras`) wait for the testing harness the owner
will specify.
