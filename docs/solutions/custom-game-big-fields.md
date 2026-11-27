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

## A computer car's controls are for the next tick

The race tick drives every car with the controls it has, then runs the AI (`Control.preform`), which sets each computer car's controls for the next tick. The player's controls are latched fresh at the start of the tick. So a computer car that is let go on a tick drives that tick with whatever its controls held: when the staggered start began running a held car's AI only from its group's start tick, the car spent that tick with the handbrake still on, and the last group set off a tick after the player. Anything that holds a computer car and lets it go must start its AI on the tick before (`StaggeredStart.drivesNext`). The classic start has the same one-tick lag for every computer car at Go!, and keeps it.

## A test thread reading the race

The hidden test samples the race from its own thread every 100 ms while the game thread runs ticks. To say when something first happened without ever saying it happened too early, read what you look for first (the cars' speeds) and the clock after (`World.ticksSinceGo`): the clock can only have moved on since. Check what the race shows, not what the rules say it should show: the first version logged each group's start from the same formula that held the cars, so it could never fail.

But what the race shows has to be the thing asked about, read where the race decides it. The next version took a car's first movement for its start, and the 32-car Pro run failed: a car of the last group was pushed while it waited (a Pro field attacks a lot, `Control.attack`). A push is not a start. The version after read the controls from the test's thread, and the blind review pointed out that the run-ahead plays ticks ahead and puts them back, so a sample can catch a tick that never happened. The start is now watched on the game thread: the controls each car will drive with, just before the drive (`CustomGameCheck.Launches.beforeDrive`), taken in after a real tick only and only if the race's clock has moved on from them by one, which a tick put back never does.

## A race with no computer car

The classic AI loop is a do-while from car 1, so it ran once even in a race of one car, which the classic game never plays; a custom practice race does, and slot 1 is then a car of no race, outside the race state, which the AI changed and the run-ahead never put back. Loops over the computer cars start at 1 and must check the count first (`CheckPoints.checkstat`'s wasted count and the HUD's wasted messages were the same). Grep `= 1;` followed by `do` when a count can be smaller than the classic one.

## The choices a race was made from

The field's size is fixed at the car select (`GameUi.carspergame`), its cars at the stage's loading and its recording after it. The developer console can change the custom game's choices in between, so a recording that wrote the choices as they were at the end held a different race from the one played. Copy the choices once, where the field is fixed (`CustomGame.fixField`), and make the cars, the recording and the test's check from the copy.

## A feature switch that changes a menu

A race extra stays off until 1.0, and the main menu with it off has to be the classic one, pixel for pixel. Keep the item's index (other code sets `opselect` to the items by name), skip it in the drawing, the mouse and the up and down keys, move the items after it up by its step, and close the backdrop up by the same amount (`GameUi.placeMenu`, `opbackGap`). Place the items every frame: the switch can change while the menu is up, from the settings or the developer console.
