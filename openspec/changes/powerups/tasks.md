## 1. Race extras in the race

- [x] 1.1 `RaceTick` (what a race extra reads and moves) and `RaceInts` (one method saves and restores its state) in `nfm.lit.features`; `Feature.raceTick` and `Feature.sync`
- [x] 1.2 `Features`: which race extras play (the switches, or a recording's list), `raceTick`, `sync`, `raceIds`
- [x] 1.3 `FeatureBridge`: `RaceTick` over the race's objects, `raceBoost`, the start when the stage loads
- [x] 1.4 `advanceRace` hook after the cars drive; `Madness.drive` boost line; `RaceRandom.STREAM_EXTRAS`
- [x] 1.5 `RaceSnapshot` saves and restores the extras; `RaceState` hashes an `extras` group only while one runs
- [x] 1.6 `RaceLog` format 7 (the race extras on, input bit 64); `RaceSession` records the list and plays a recording with it

## 2. The key

- [x] 2.1 `KeyBindings.USE_POWERUP` (Ctrl), `SplitKeys` (R; L and number pad 2), `Gamepads` (right stick button)
- [x] 2.2 `Control.power` latched with the driving keys; run-ahead's key check; left out of the reflected driver

## 3. Powerups

- [x] 3.1 `PowerupRace`: boxes, pickups by place, nitrous, dynamite and its blast, the magnet, the computer cars, events
- [x] 3.2 `PowerupsFeature`: events after real ticks, HUD messages, flash, rumble
- [x] 3.3 `PowerupDrawing`: boxes, held powerups, the stick, fireball and smoke, craters, the magnet's arc, nitrous flame; `Particles.push`
- [x] 3.4 `PowerupSounds`; `Sound3D.pointSound` and the hum per car; Classic clips through `FeatureBridge`
- [x] 3.5 `PowerupsCheck` and its line in `ExtrasCheck`

## 4. Docs and landing

- [x] 4.1 README (Extras, options row, the key), `docs\features\powerups.md`, `docs\features\README.md`
- [x] 4.2 Build (`build.ps1 -Quick`); per the owner's 16:07 UTC note no test is run (verify-races, perf, spec-check, extras-check, autotests, live, blind review: not run)
