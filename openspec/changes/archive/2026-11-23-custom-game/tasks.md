## 1. The custom game

- [x] 1.1 `CustomGame`: choices, lineups, grid, staggered start
- [x] 1.2 Custom Game on the main menu and its page (`SettingsScreen.customGame`), Race! to the car select, back to the page after a race
- [x] 1.3 `GameUi.carspergame` and `sortcars` take the custom game's cars; custom races unlock nothing
- [x] 1.4 The grid in `loadstage`, the held cars and `World.ticksSinceGo` in `advanceRace`, the fly-by car
- [x] 1.5 The launch countdown and the standings for more than 10 cars

## 2. A big field

- [x] 2.1 Replay sound arrays, `Precise`, `Medium.addSp` sized for every car
- [x] 2.2 `Sound3D`: the 8 nearest cars traced in a bigger race, the old path for 8 or fewer

## 3. Saved, scriptable, recorded

- [x] 3.1 Cookies, `-Dnfm.custom.*`, the `custom` console command, README
- [x] 3.2 Race file format 6 with the choices; the verifier sets them up
- [x] 3.3 `-Dnfm.autotest.custom` and `test\live.ps1 -Custom`

## 4. Cleanup

- [x] 4.1 Cleanup pass over the change (/simplify): setters for the choices, the start kept in `World`, one occlusion loop, shared countdown drawing, ordinals and the classic boss and car-span rules
- [x] 4.2 Lessons in `docs\solutions\custom-game-big-fields.md`

## 5. Gates

- [x] 5.1 `build.ps1` (full, with the startup cache; 2026-11-23 on the owner's PC, worktree)
- [x] 5.2 `test\verify-races.ps1`: 20 matched, golden frames 95 same; `test\verify-sound.ps1`: all 3 renders match
- [x] 5.3 Hidden custom runs: 9 Default, 12 Novice, 32 Pro, a 20-car Custom pick. The first round found the warm-up crash with 10 to 24 computer cars (fixed, see the lessons), captions over the player's countdown portrait and the campaign wording on the results (both fixed)
- [ ] 5.4 `test\perf.ps1`; `test\live.ps1 -Custom 32,pro` and `test\live.ps1` on the owner's PC
- [x] 5.5 `test\spec-check.ps1`, and `openspec validate custom-game --strict`
- [ ] 5.6 `test\blind-review.ps1 -Base <the commit before the work>`
