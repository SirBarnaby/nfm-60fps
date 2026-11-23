## Why

The owner, 23 Nov 2026, on the extras brief: "Keep those in also. Implement." `powerups` holds the five requirements written that day: powerups that spawn on the maps with a pickup sound and a hum per powerup while held; nitrous; dynamite, thrown forwards 2 seconds after the pickup, denting the map where it blows up; the magnet the owner picked (pulled toward the car ahead for 3 seconds); and stylised, dithered explosions with a smoke cloud that stays and a screen flash of 1 to 2 frames. Powerups change races, so they come after 1.0 (`release-1-0`): the switch is off by default, as it is in every hidden run, and a race without it plays exactly as before.

The extras framework already lists the switches (**Powerups**, `powerups`, cookie `xtraboxes`, a race switch; **Explosion flash**, `flash`, cookie `xtraflash`, on by default) and designed how a race extra keeps its state (`feature-framework`, decision 8). This change builds that design and the powerups on it.

No requirement is MODIFIED or REMOVED and none is added. From 16:07 UTC on 23 Nov the owner tests by hand: this change runs no test and lands when it compiles and reads right; recording races with powerups for `test\races-extras` waits for the testing the owner will specify.

## What Changes

- **Race extras in the race** (the framework's decision 8, built now): `Feature.raceTick` runs inside `advanceRace` after the cars have driven, so run-ahead and the verifier run it too; a race extra keeps its state in fixed arrays it saves and restores through one method (`RaceInts`), which `RaceSnapshot` captures with the rest of the race and `RaceState` hashes as a group of its own (`extras`) only while one runs, so every recorded race hashes as before; it draws its dice from its own stream (`RaceRandom.STREAM_EXTRAS`); a recording notes which race extras were on (`RaceLog` format 7) and a recording played back runs with those, whatever the switches.
- **A key to use a powerup**: the action **Use powerup** (Ctrl; in split screen R for Player 1 and L or number pad 2 for Player 2; on a controller the right stick's button), latched once a tick like the driving keys, recorded with them (`RaceLog` input bit 64), and left out of the race's hashes while no powerups run.
- **`src\nfm\lit\features\powerups\`**:
  - `PowerupRace`, the race part: boxes in a row across the road at each checkpoint, taken by a car that drives through one holding nothing and back four seconds later; what a box gives (nitrous, dynamite or the magnet, never the magnet to the leader); nitrous (two seconds of the game's own stunt-power boost raised, so the car pulls harder and runs faster, fading over a second); dynamite (two seconds after the pickup one stick thrown forwards, which blows up where it lands or on a car, throwing cars near it aside and up, damaging them and shaking the screen); the magnet (three seconds pulled toward the car one place ahead, with a little extra power, until close); the computer cars' choices; events (pickups, uses, throws, blasts) the look and sound read after real ticks only.
  - `PowerupsFeature` and `PowerupDrawing`, the look: spinning boxes, the powerup a car holds floating over its roof, the stick in flight with its fuse, a dithered fireball, a smoke cloud that rises, spreads and is pushed aside by cars driving through it, clods and sparks, a crater left in the ground, the magnet's arc between the cars, nitrous's flame (realism's own flame when realism is on), and the game's white flash for one frame when a stick blows up near a player (switch `flash`).
  - `PowerupSounds`: made once from a few lines of synthesis: the pickup, a hum per powerup (nitrous's pressurised whistle, the fuse's crackle, the magnet's electric hum), the nitrous burst, the magnet's clunk, the throw and the blast. With the 3D sound each plays from its car (the hum while the car holds the powerup) and the blast from where it happened; Classic plays a player's own pickup, hum and uses, and blasts near a player.
  - `PowerupsCheck`: a check per scenario, on a made-up race.
- **HUD**: on a pickup the player is told what they have and how to use it, in the game's own message line.
- **README**: the extra in the Extras section, its options row and the key. **`docs\features\powerups.md`**: how it works.

Looked at first (rule: before a new subsystem): Mario Kart's item tables (the item depends on the place in the race, so the leader does not get what only helps cars behind; https://www.mariowiki.com/Mario_Kart_Wii_item_probability_distributions), Counter-Strike 2's responsive smoke (a cloud that fills space and is pushed aside by what passes through it; https://developer.valvesoftware.com/wiki/Cs2, and the recreations at https://80.lv/articles/counter-strike-2-style-responsive-smoke-set-up-in-unity-urp), and ordered (Bayer) dithering for retro shading (https://tympanus.net/codrops/2025/06/04/building-a-real-time-dithering-shader/), which the framework's see-through polygons already draw. No library fits a 1990s Java racer's flat polygons and fixed 19 Hz race; their designs are followed: the item by place, smoke as a cloud of puffs that cars push aside, the fire and smoke dithered.

## Capabilities

### New Capabilities
<!-- none: the requirements exist in powerups -->

### Modified Capabilities
<!-- none -->

Specs touched: none changed. Read: `powerups` (all five), `release-1-0`, `settings`, `split-screen`, `performance`, `code-health`, `done-and-testing`, `sound`.

## Impact

- New files: `src\nfm\lit\features\RaceTick.java`, `src\nfm\lit\features\RaceInts.java`, `src\nfm\lit\features\powerups\`, `docs\features\powerups.md`.
- Hooks: `GameSparker` (the tick hook, the key, the flash), `Madness.drive` (one line: a race extra's added boost, 0 without one), `Control` (the key), `RaceSnapshot`, `RaceState`, `RaceLog`, `RaceSession`, `RaceRandom` (a stream id), `KeyBindings`, `SplitKeys`, `Gamepads`, `GameUi` (a message for a player), `Sound3D` (a sound at a point, a hum per car), `Particles` (a push), `FeatureBridge`, `Features`, `ExtrasCheck`.
- With the switch off nothing a race reads changes: no `extras` group, no state saved, the boost hook adds 0, the key is never read; the recorded races, the golden frames and the sound check stay as they are.
