# Powerups

Switch: **Powerups** (`powerups`, cookie `xtraboxes`), off by default until
1.0, in the Extras tab's Races group; under it **Explosion flash** (`flash`,
cookie `xtraflash`, on by default). Requirements: `openspec\specs\powerups`
(spawning with a pickup sound and a hum, nitrous, dynamite with the map
deforming, the magnet, stylised explosions with the flash) and
`openspec\specs\realism` (the blue exhaust flame while nitrous burns).

Code: `src\nfm\lit\features\powerups\` (`PowerupRace` the race half,
`PowerupsFeature` the look, sound and messages, `PowerupDrawing` the shapes,
`PowerupSounds` the sounds, `PowerupsCheck` the checks); the framework's
race-extra hooks in `Features`, `RaceTick`, `RaceInts` and `GameHooks`, and
the game's side in `nfm.lit.FeatureBridge` (`RaceAccess`, `Hooks`).

## Playing

- **Boxes**: a row of three, 170 units apart across the road, 70 over it,
  at each checkpoint (every second one on a stage with more than 16). A car
  whose middle comes within 60 units plus 0.4 of its radius across, and 160
  up or down, takes one if it holds nothing and is not wasted. The box comes
  back 4 seconds later.
- **What a box gives**, by the car's place: nitrous 40, dynamite 30, magnet
  30 in a hundred; the leader nitrous 57, dynamite 43 (no magnet: nobody is
  ahead); the last car nitrous 40, dynamite 20, magnet 40.
- **The key**: Use powerup, Ctrl by default (Keys tab); in split screen R for
  Player 1, L or number pad 2 for Player 2; on a controller the right stick's
  button. A press is used on the tick it starts. The key is latched only
  while a race extra plays, so a race without powerups records the same keys.
- **Nitrous**: 2 seconds of 60 more boost (the stunt power's scale, which
  the game turns into pull in every gear and top speed), then fading to none
  over 1 second. As the game's boost, it pulls only on the ground with the
  throttle held.
- **Dynamite**: 2 seconds after the pickup the stick leaves the car's nose at
  its speed plus 60 units a tick forwards and 16 up, falling 1.4 a tick per
  tick. It blows up on the ground, on a car (within 90 units; the thrower's
  own only after 12 ticks) or after 45 ticks. Every car within 520 units is
  hit by `s = 1 - d/520`: each wheel pushed away by 55·s and up by 30·s to
  45·s (the nearer wheels more, so the car tips), damaged by a quarter of
  its maximum times s, its driver's screen shaken. A blast within 150 units
  of the ground leaves a crater.
- **Magnet**: with a car one place ahead and not wasted, for 3 seconds the
  car is moved toward it by up to 9 units a tick (a tenth of the gap beyond
  260 units, so it closes in without ramming) and has 25 more boost; it
  stops early if that car is wasted. With nobody ahead the magnet stays held
  and the player is told.
- **Computer cars**: nitrous on the ground with the throttle held, going
  straight above 60% of top speed, 12% a tick; the magnet with the car ahead
  400 to 3000 units away, 15% a tick. Dynamite throws itself.

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
  `boost += FeatureBridge.raceBoost(im)`, 0 without a race extra.

## The look and the sound

`PowerupsFeature` reads `PowerupRace` after real ticks only and copies what
it draws, so a run-ahead tick is never seen or heard. What happened in a
tick comes from a ring of 32 events (pickup, use, throw, blast, magnet off,
nobody ahead).

- Boxes: amber cubes spinning and bobbing, a darker diamond on each side,
  their faces turned away left out, each corner given its move to the next
  tick. The held powerup: a two-tone diamond over the roof (blue nitrous,
  red dynamite, violet magnet). The stick tumbles end over end, sparking.
- The blast: 16 glowing puffs (0.6 s), 40 smoke puffs that rise, spread and
  stay about 8 s and are shoved along and aside by a car driving through
  them (`Particles.push`), 16 clods, 12 sparks, all dithered.
- Craters: a scorched patch, then a bowl 18 deep inside a raised lip, each
  face shaded by its slope, drawn on the road for the rest of the race (16).
- The flash: the game's own white start flash (one drawn frame) when a stick
  blows up within 6000 units of a player's car and Explosion flash is on.
- The magnet: a crackling arc from roof to roof. Nitrous: a blue flame from
  the tail, or Damage and heat's exhaust flame when that is on.
- Sounds: the dynamite's fuse and the blast are recordings (a burning fuse,
  an explosion; `sounds\README.md`). The rest are still synthesized once
  from fixed seeds and are replaced with the powerups' rework: the pickup's
  chime, the nitrous and magnet hums, the nitrous rush, the magnet's clunk
  and the throw. With the 3D sound each
  plays at its car (`Sound3D.carSound`), the blast held at its place
  (`Sound3D.pointSound`), and each car's hum from a looping source of its
  own that follows it (`Sound3D.Car.hum`). With Classic sound a player hears
  their own pickup, uses and hum, and blasts within 6000 units; these clips
  play on their own, outside the game's list of sounds, so the game's own
  sound commands (and the recorded races' sound check) stay as they were.
- Messages, in the game's message line: "Nitrous! Ctrl to use", "Dynamite!
  Thrown in 2 seconds", "Magnet! Ctrl to use", with the player's own key.

## Checking

`test\extras-check.ps1 powerups` plays the race half on a made-up straight:
the boxes' rows, a pickup, nitrous's three seconds, dynamite thrown at two
seconds and its blast throwing a car and denting the ground, the magnet's
three seconds, the leader never given a magnet, and the state saved, played
on and restored playing on the same. Recorded races with powerups
(`test\races-extras`) wait for the testing harness the owner will specify.
