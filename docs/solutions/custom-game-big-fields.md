# A bigger field: what was sized for the classic seven

*23 Nov 2026, from building the custom game (up to 32 computer cars, `openspec\specs\custom-game`).*

## The lesson

The race itself already holds 51 cars: every per-car array in `Madness`, `Control`, `CheckPoints`, `Record` and `GameUi.sc` is sized 51, the original's own limit. What breaks a bigger field is the code around the race that was sized for the cars the classic game happens to have.

- **Arrays sized from `GameFacts.numberOfPlayers` when an object is made.** `GameSparker.replayPlace` and `replaySpeed` were `new float[GameFacts.numberOfPlayers]`, built while the field was still 7. A replay of a 33-car race would have run off their end. Size per-car arrays to `FramePose.SLOTS` (51), never to the count at construction time.
- **Fixed caps that silently drop the rest.** `Precise` kept the in-between positions of 16 cars (the rest were drawn at whole positions, jerky); `Medium.addSp` kept 7 shadows from overlapping (the rest shaded the ground twice); `Sound3D` muffled 8 cars (the rest were never muffled). None of them failed; they just did less. Grep for the numbers 7, 8 and 16 near per-car code.
- **The classic grid fits about 10 cars.** Its rows are 760 apart and every stage's straight runs from z -1866 to 1983 (stage 16's ramp starts at 2437, stage 4's at -2200). A bigger grid has to squeeze its rows to fit, not grow past the ramps.

## Keeping classic races exact while adding to them

- New race state goes in `World`, which `RaceSnapshot` captures for the run-ahead and rollback but which is not in the recording's state layout: a field there changes no recorded race. A field in `Madness`, `Control`, `CheckPoints` or a named `GameUi` flow field would change the layout fingerprint of every recording.
- Decide a race's special rules once, when the grid is laid out, and keep them with the race (`World.startGroups`), rather than reading a menu flag from the race tick.
- When a sound path is checked sample for sample (`test\verify-sound.ps1`), a general loop must run the same float operations on the same operands in the small case: car i in slot i, every slot eased every update.
- Pull shared drawing out of the classic block (`GameUi.standingRow`, `drawCountdown`) rather than copying it: the classic call then draws the same pixels, and the new feature cannot drift from it.

## The race runs before its countdown

The warm-up (`GameSparker.warmUpWhileLoading`) plays real race ticks on the screen before the race, with `starcnt` 0, and the HUD draws them into a clipped layer. So race-tick drawing in `GameUi.statPlayer` also runs before the countdown has made what it draws with: the countdown portrait (`dudeb`, made by `blendude`) is null then, and `dudo` starts at 0, so the classic `dudo != -1` guard does not catch it. The staggered start's own countdown drew the portrait there with 10 to 24 computer cars and the game thread died. Whatever the race tick draws that the countdown sets up must check that it is in the race (`fase == Phase.INGAME`). Test the first race after a fresh start, at every size the new rule changes: the warm-up runs once per game run, so a second race never shows it.

## Driving a hidden test run

A test thread that writes `u[0].up` directly races the game: `latchRaceInput` sets the controls from the held keys at the start of every tick and overwrites it. Hold keys the way the keyboard does, in `live[0]` under `inputLock` (`GameSparker.holdTestKeys`), and the next tick latches them.

A hidden test that runs out of time should not exit with code 0: a dead game thread leaves the game on one screen until the time runs out, and code 0 then reads as a pass. The custom test exits with 1.
