## Why

Adds a feature to 1.0 that the owner asked for on 23 Nov 2026, ahead of the "gameplay waits for 1.0" plan; no requirement is MODIFIED or REMOVED. The classic game, its races and its golden frames stay exactly as they are: everything new runs only in a custom game.

The owner, 23 Nov 2026: "CUSTOM GAME: Allow game to be customized. Amount of AI + what cars ai have. We should have 3 presets for ai cars. 1.) Default 2.) Novice (up until drifter x) 3.) Pro (Fill the race with pro cars only, starting from dr. monstaa, and going down from there) 4.) Custom. Allows picking our enemies. This feature should be very flexible. We should have up to 32 enemies. This might need starts to be more staggered. So groups of 6 AI start at once. But this feature only starts at like >9 AI. We will always keep our last place, and launches should be max 2secs apart."

**Searched first.** Nothing to reuse: no Need for Madness mod or tool sets up custom races, and the racing games that add opponents (Need for Speed's "More Opponents in Career Races" mod, BeamNG's extra AI races, rolling and staggered starts in sims) do it inside their own engines. The engine here already holds 51 cars in every race array (the original's own limit), so the work is the menus, the grid, the start and the parts sized for the classic seven.

## What Changes

- **Custom Game on the main menu**, between Credits and Split Screen. Its page is a page of the settings screen (the same rows, keys, mouse and look), titled Custom Game:
  - **Race** tab: **Race!**, **Computer cars** (none, a practice race, to 32) and **Their cars** (Default, Novice, Pro, Custom).
  - **Pick cars** tab: how many of each of the 16 cars race, up to 32 in all, and **Clear the picks**. Picking a car turns the Custom lineup on.
  - Race! goes on to the car select and stage select as Play Game does; Back or Escape goes back to the main menu; after a custom race the page comes back.
- **The lineups.**
  - Default: the cars the classic game brings to the stage (the same span of the car list, the stage's boss at the front), dealt again for every round in a bigger race. Default with six is the classic game's own choice, unchanged.
  - Novice: Tornado Shark to Drifter X, shuffled from the race seed.
  - Pro: only the cars after Drifter X: DR Monstaa at the front, then Radical One, M A S H E E N and down to Sword of Justice, then DR Monstaa again.
  - Custom: the cars picked, the best of them at the front.
- **The grid.** Up to 9 computer cars stand on the classic grid, the player at the back. With more, they stand in groups of six across (the columns staggered half a row, the rows squeezed only as much as the stage's straight needs), and the player stands in the middle behind them.
- **The staggered start**, with more than 9 computer cars. The front group goes at the countdown's Go!, the next a second later, and so on; the player goes with the last group. Cars still waiting stand with the handbrake on and their driving is not worked out yet. The HUD says how the start goes, which group is off and when the player goes, and counts the player's own start down with the countdown's numbers, portrait and voice.
- **A big field works everywhere.**
  - The standings show the top three, the car the arrow points at and the cars nearest the player in ten rows, with dots where cars are left out; the position reads 21st, 22nd, 23rd.
  - The fly-by circles a car in the middle of a big grid.
  - The replay sound, the drawing's in-between positions and the shadows (`Medium.addSp`) were sized for 7 to 16 cars and now take every car. The 3D sound traces the 8 cars nearest the listener for muffling, from every car of a bigger race.
- **Custom races unlock nothing.** A race with no computer cars or eight novices would otherwise unlock every stage and car.
- **Saved and scriptable.** The choices are saved (cookies `customrivals`, `customlineup`, `custompicks`; none in hidden runs), `-Dnfm.custom.rivals`, `-Dnfm.custom.lineup` and `-Dnfm.custom.picks` set them for a run, and `custom` in the developer console shows and changes them.
- **Replays.** Custom races are kept in `replays` with their choices (race file format 6; formats 3 to 5 still read), so the verifier plays them back exactly.
- **Tests.** `-Dnfm.autotest.custom=<cars>[,<lineup>]` plays a custom game hidden from the main menu to the highlight and back, logging the cars, the grid and each group's start. `test\live.ps1 -Custom 32,pro` judges it like any race.

## Capabilities

### New Capabilities
- `custom-game`: the custom game's choices, lineups, grid and staggered start, as the owner asked for them.

### Modified Capabilities
<!-- none -->

Specs touched (from `openspec list --specs`): only the new `custom-game`. `release-1-0` ("Up to 1.0, races play exactly like the original" and "it looks like the original") and `performance` (the 250 fps floor) are read: classic races and pictures do not change, and a custom race is held to the same floor.

## Impact

- New: `src\nfm\lit\CustomGame.java` (choices, lineups, grid, start).
- `GameUi.java`: the menu item, the page's phase, the player count and the cars, the unlock guards, the standings rows and the launch countdown.
- `SettingsScreen.java`: a titled page, the custom game's rows. `SplitScreens.java`: its results use the shared `GameUi.ordinal`.
- `GameSparker.java`: the phase, the grid, the held cars, the fly-by, the save flow, cookies and properties, the hidden test run, the replay arrays.
- `World.java`: `startGroups` and `ticksSinceGo`, the staggered start and its clock, captured by `RaceSnapshot` with the rest of the world.
- `Madness.java`: `world()` for the HUD.
- `RaceLog.java`, `RaceSession.java`: the choices in a recording, and the verifier sets them up.
- `Precise.java`, `Medium.java`, `audio\Sound3D.java`: sized for every car.
- `DevTool.java`: the `custom` command. `Phase.java`: `CUSTOMGAME`.
- `test\live.ps1`: `-Custom`. `README.md`: the custom game and its options.
