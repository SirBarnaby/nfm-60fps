## 1. Into the extras framework

- [x] 1.1 `src\nfm\lit\features\customgame\`: `CustomGameFeature`, `CustomGame`, `StaggeredStart`, `CustomGamePage`, `CustomGameCheck`; `CustomGame` moved out of `nfm.lit`
- [x] 1.2 The `customgame` switch (race extra, off until 1.0) in `FeatureSettings`, the feature in `Features.ALL`
- [x] 1.3 `nfm.lit.features.Page` and `SettingsScreen.page`; the page's rows out of `SettingsScreen`
- [x] 1.4 The main menu shows Custom Game only with the switch on, the classic menu otherwise
- [x] 1.5 The console command, the saved choices and the HUD's countdown moved into the folder; calls left in `GameUi`, `GameSparker`, `DevTool`, `RaceSession`

## 2. The findings

- [x] 2.1 A held computer car's AI runs from the tick before its group starts (`drivesNext`)
- [x] 2.2 `test\live.ps1 -Custom` takes one string or several and checks it
- [x] 2.3 The hidden custom test fails on a wrong field, the player not last, an early or late group, a skipped highlight or results
- [x] 2.4 The frame log's `staggered start` part, graded and asked for by `test\live.ps1`
- [x] 2.5 Only the choices changed are saved
- [x] 2.6 The archived `custom-game` change: no claim of a measurement not made; task 5.4 names `test\live.ps1 -Split`
- [x] 2.7 README, `docs\features\customgame.md`, lessons in `docs\solutions\custom-game-big-fields.md`

## 3. Gates

- [ ] 3.1 `build.ps1 -Quick`; `test\verify-races.ps1`; `test\verify-races.ps1 -Features races -NoGolden`; `test\verify-sound.ps1`; `test\spec-check.ps1`
- [ ] 3.2 Hidden custom runs with the switch off by default: 9 Default, 12 Novice, 20 Custom picks, 32 Pro; a 32-car race recorded and played back, with and without `-Rollback 7`
- [ ] 3.3 `test\perf.ps1`; `test\live.ps1 -Custom 32,pro`, `test\live.ps1` and `test\live.ps1 -Split` on the owner's PC
- [ ] 3.4 `test\blind-review.ps1 -Base <the commit before the custom game>`
