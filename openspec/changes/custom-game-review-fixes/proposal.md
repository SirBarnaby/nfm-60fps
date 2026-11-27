## Why

The blind review of the custom game (`docs\reviews\2026-11-23-1604-81640e3.md`, Codex, verdict needs-attention) found eight things to fix. No requirement is MODIFIED or REMOVED, none is added, and no feature the owner did not ask for is added: this change makes the custom game meet requirements already in `openspec\specs`.

- `code-health`, "Each feature lives in its own folder": the custom game was spread through `GameUi`, `GameSparker`, `SettingsScreen` and `DevTool`, with its class in `nfm.lit`, outside the extras framework and with no switch of its own.
- `release-1-0`, "Up to 1.0, races play exactly like the original" and "it looks like the original": Custom Game was on the main menu for everyone. As a race extra it is off by default until 1.0, as the powerups and the suspension are; the owner turns it on in the Extras tab.
- `custom-game`, "Launches at most 2 seconds apart" and "The player always starts last": a computer car's AI ran only from its group's start tick, and the AI sets controls for the tick after, so the last group's computer cars drove their first tick with the handbrake still on while the player, latched fresh, was already off.
- `done-and-testing`: the hidden custom test could pass with a wrong field, the player not last, a skipped highlight, or group starts that came from the same formula that held the cars; the archived design called the full field measured when task 5.4 was open; the tasks left out `test\live.ps1 -Split`.
- `performance` (the live test sees what the player sees): the player's wait in a staggered start was summed up as part of the race.
- `settings`: a choice given with `-Dnfm.custom.*` for one run was saved over the player's own when any other choice was edited.
- `test\live.ps1 -Custom 32,pro` only worked through `powershell -File`: through `&` or `-Command` PowerShell bound it as the array `32 pro`, which the game then read as `32 pro`.

The second blind review (`docs\reviews\2026-11-23-1744-ce5cd34.md`) found more, under the same requirements: a practice race ran the AI for an empty slot outside the race state (`code-health`, races deterministic and capturable); a console change during the car or stage select made a recording hold other choices than its field's; the start's check sampled from the test's thread, where a run-ahead tick can be read, and did not hold every car of a group or the player to its tick (`custom-game`, `done-and-testing`); leaving Settings moved the classic menu's choice (`release-1-0`); the hidden test's driver sat in `GameSparker` (`code-health`); the design stated gate results before they were run.

## What Changes

- **`src\nfm\lit\features\customgame\`**, the custom game's own folder:
  - `CustomGameFeature`, in `Features.ALL`, with the switch `customgame` (Settings, Extras: Custom game; `-Dnfm.customgame`; cookie `xtracustom`), a race extra, off by default until 1.0. With it off the main menu is the classic one, pixel for pixel.
  - `CustomGame`: the choices, their saving (only the ones changed) and properties, the lineups, the recorded string, the result line and the `custom` console command.
  - `StaggeredStart`: the big grid, the groups, the held cars, the start's clock and what the HUD shows of it.
  - `CustomGamePage`: the page's rows, through a new `nfm.lit.features.Page` that the settings screen lays out (`SettingsScreen.page`), so a feature can have a page of its own in the settings' look.
  - `CustomGameCheck`: what the hidden test holds a custom race to, the start watched on the game thread.
  - `CustomGameTest`: the hidden test itself, through a small host in `GameSparker`.
- **The calls in the existing files** stay one call each where the game hands over (`carspergame`, `sortcars`, the grid, the race tick, the fly-by, the HUD's countdown, the results, the console, the recordings, loading and saving). The main menu shows Custom Game only with the switch on.
- **The start.** A held computer car's AI runs from the tick before its group's start (`StaggeredStart.drivesNext`), so the whole group, the player's included, sets off on the same tick. A held car stays a car on the road: hit, it is pushed (said in the README).
- **The hidden test fails** (exit code 1, `custom test FAILED: <why>`) on a field the lineup does not bring, a player not last, a computer car with its controls back before its group's start (a push is not a start), a group setting off more than 2 seconds (38 ticks) after the one before, a skipped highlight or results, or a run that does not get through. Each group's first movement is logged from what the race shows.
- **The frame log** names the player's wait in a staggered start `staggered start`, a part of its own; `test\live.ps1` grades it as a race part and asks for it with 19 or more computer cars (a wait of 3 seconds or more).
- **`test\live.ps1 -Custom`** takes the value as one string or several and checks it.
- **The second review:** no AI for an empty slot; the choices fixed with the field (`CustomGame.fixField`); the start watched on the game thread; Split Screen chosen again after Settings, as in the classic game; the hidden test in the folder; the design's gate results stated as expected. With 9 or fewer computer cars the classic start is kept as it is (design decision 10).
- **The archived `custom-game` change**: its design no longer says the full field was measured (task 5.4 is open), and task 5.4 names `test\live.ps1 -Split`.
- **README, `docs\features\customgame.md`, `docs\solutions\custom-game-big-fields.md`**.

Libraries looked at: none needed; the extras framework is the game's own.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: the requirements are as the owner wrote them -->

Specs touched (from `openspec list --specs`): none changed. Read: `custom-game`, `code-health`, `release-1-0`, `settings`, `performance`, `done-and-testing`.

## Impact

- Code: the new folder; `Features`, `FeatureSettings`, `Page` in `nfm.lit.features`; calls in `GameUi`, `GameSparker`, `SettingsScreen`, `DevTool`, `RaceSession`; `test\live.ps1`.
- Classic races: unchanged. Every new branch is behind `CustomGame.on` or a staggered race (`World.startGroups` > 0), as before; the switch only shows the menu item.
- Recorded custom races made before this change play their last group's computer cars a tick later than now, so they no longer match; none of them is in `test\races`.
