# Custom game

Switch `customgame` (Settings, Extras: **Custom game**; `-Dnfm.customgame=on`; cookie `xtracustom`). A race extra, so off by default until 1.0. With it on, Custom Game is on the main menu, under Credits; with it off the main menu is the classic one, pixel for pixel.

What the owner asked for is in `openspec\specs\custom-game\spec.md`: up to 32 computer cars, the Default, Novice, Pro and Custom lineups, and above 9 computer cars a start in groups of 6, at most 2 seconds apart, with the player always last.

## The folder

`src\nfm\lit\features\customgame\`:

| File | What it holds |
| --- | --- |
| `CustomGameFeature` | The extra in `Features.ALL`: its switch's id and whether the main menu shows Custom Game. A way into a race rather than something added to one, so it has nothing to do in the race's own calls (`playsInRaces` is false: a race with only this switch on is set up, run and recorded as one with no extra). |
| `CustomGame` | The choices (computer cars, lineup, picks), how they are saved (`customrivals`, `customlineup`, `custompicks`; only the ones changed) and typed (`-Dnfm.custom.*`), the cars each lineup brings, the string a recorded race keeps (RaceLog format 6), the result line and the `custom` console command. `CustomGame.on` is true from the page to the main menu, or for a recorded custom race. |
| `StaggeredStart` | The grid of a big field, the groups and their start ticks, the cars held before their group goes, and what the HUD shows of the start. Its state is race state: `World.startGroups` and `World.ticksSinceGo`. |
| `CustomGamePage` | The page's rows, laid out through `nfm.lit.features.Page` (the settings screen's box, keys and look). |
| `CustomGameCheck` | What the hidden test holds a custom race to: the field each lineup brings, the player last on the grid, and the start, watched on the game thread from the controls each car drives with: every car of a group on its group's tick, never before, at most 2 seconds after the group before, the player with the last group. |
| `CustomGameTest` | The hidden test (`-Dnfm.autotest.custom`): the run from the main menu to the page again, through a small `Host` that `GameSparker` fills with its test helpers. ||

## The calls into it

- `GameUi`: the main menu item (shown only with the switch on), the page (`SettingsScreen.page`), the car count (`carspergame`, which fixes the race's choices: `CustomGame.fixField`), the cars (`sortcars`), the staggered countdown and captions (`launchCountdown`), the result line, and no unlocks from a custom race.
- `GameSparker`: the grid and `World.startGroups` in `loadstage`; `StaggeredStart.beforeDrive`, `drivesNext` and `afterDrive` in the race tick; the fly-by car; loading and saving the choices; `CustomGame.on` off at the main menu and the page again after the results; the frame log's `staggered start` part; the hidden test's host (`runCustomTest`, the game's test helpers for `CustomGameTest`) and two calls in the race tick that feed its watch on the start.
- `RaceSession`: the choices written into a recording and set up again before a recorded race is played.
- `DevTool`: `custom`.

What a big field needs of the engine, whatever brings it (in-between positions, shadows and replay sound for every car, Steam Audio's occlusion for the 8 nearest cars, the standings for more than ten cars), is engine work and stays in the engine's own files.

## The start, tick by tick

A car whose group has not gone has its controls let go and the handbrake on before the cars drive (`beforeDrive`). A computer car's AI runs after the drive and sets its controls for the next tick, so it runs from the tick before its group's start (`drivesNext`): the group sets off on its start tick, the same tick as the player in the last group. A held car is still a car on the road, so a car that hits it pushes it.

## With the other extras

The extras size what they keep by the race's cars, so a field of 33 has skidmarks, damage, weather, powerups and the rest on every car. In the 3D sound each car of a big field has its own rebuilt engine (its body's places and its kit), and the echo of what stands around a car is heard from the 8 cars nearest the listener, the ones Steam Audio traces. A recording keeps the custom game's choices and the race extras that played side by side (`RaceLog` format 7).

## Checks

- `-Dnfm.autotest.custom=<cars>[,<lineup>]` (hidden) and `test\live.ps1 -Custom <cars>[,<lineup>]` (your settings, still hidden).
- Recorded custom races play back with `test\verify-races.ps1 -Races <folder>`, with and without `-Rollback 7`. `-Dnfm.autotest.custom=32,pro -Dnfm.features=races -Dnfm.record=<folder>` records one with every race extra on.
- `-Dnfm.autotest.custom=32,pro -Dnfm.features=all` plays a 33-car race with every extra on; no line of its log may say an extra failed.
- With the switch off, every classic race and golden frame is as before: `test\verify-races.ps1`.
