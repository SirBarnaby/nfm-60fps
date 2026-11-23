## Context

- The extras framework (`nfm.lit.features`) gives each extra a folder, an entry in `Features.ALL` and a switch in `FeatureSettings`; the Extras tab shows a switch only for a feature in that list. The race's own classes are package-private, but `GameFacts`, `GameUi`, `World` and `Control` are public, so a feature can read and set what it needs through them.
- The race tick: before 1.0 untouched for every classic race. In a tick the game latches the player's keys, drives every car with the controls it has, then runs each computer car's AI (`Control.preform`), which sets its controls for the next tick. During the countdown no AI runs, so at the classic Go! every computer car drives one tick with released controls.
- The hidden tests live in `GameSparker` next to each other (`runAutoTest`, `runSplitTest`, `runCustomTest`) and share its helpers (screenshots, Enter, the held keys, the end of a run).

## Goals / Non-Goals

**Goals:** every finding of the blind review that shows a requirement unmet or a bug, fixed; the custom game in its own folder behind its switch; the classic game, its races and golden frames unchanged.

**Non-Goals:** changing what the owner asked for; moving the engine's big-field support (in-between positions, shadows, replay sound, occlusion for the 8 nearest cars, the standings past ten cars) into the feature, since any big field needs it; moving the hidden test's driving loop out of `GameSparker`, where every hidden test lives.

## Decisions

1. **Off by default until 1.0, as a race extra.** `release-1-0` holds races and the look to the original until 1.0, and the framework turns every race extra off by default until then, the owner's own powerups included. Custom Game shows on the main menu once the owner turns on Custom game in the Extras tab. *Alternative:* on by default, as the first build had it. Rejected: the default game would then not be the original.
2. **The menu keeps its indices.** Custom Game stays item 3; with the switch off it is skipped by the drawing, the mouse and the keys, the items after it move up a step (31 pixels) and the backdrop closes by as much, so the menu is the classic one. The items are placed every frame, since the switch can change while the menu is up. Leaving Settings puts the choice on Split Screen, as the classic game does (its `3` was Split Screen), not on Settings.
3. **A page through `Page`.** The settings screen's rows are private to it; `Page` (tab, group, button, choice, only-when) lets a feature lay out a page of its own, and `SettingsScreen.page` makes the screen. *Alternative:* the page's rows kept in `SettingsScreen`. Rejected: feature code in a big file.
4. **The AI of a held computer car runs from the tick before its group starts** (`drivesNext`: not held on the next tick). Its controls are then ready on the start tick, which `beforeDrive` no longer overrides, so the group goes on the same tick as the player. The first group and every classic race are as before: `drivesNext` is always true when the race starts at once.
5. **A held car is not frozen.** It stands with the handbrake on; another car hitting it pushes it, as any car standing on the road. Freezing it would mean a new physics rule. With the front group going first and the player behind everyone, only a car spinning back or turning to attack (the AI's own attack, which a Pro field does a lot) reaches a held one. A push is not a start, so the hidden test tells them apart by the controls: a held car's are the hold (handbrake on, nothing else) until its AI takes over.
6. **The test checks what the race shows, where the race decides it.** The field against what each lineup brings (Novice cars 0 to 8, each once before any twice; Pro 15 then down to 9; Custom the picks exactly; Default the stage's span with its boss in front), and the grid's z against the player's. The start is watched on the game thread, in the real ticks only: just before the cars drive, the controls each car will drive with, and after the tick, which cars are moving. Every car, the player's included, must drive with the hold on each tick before its group's start and with controls of its own on that tick; so the cars of a group all go on one tick, in order, at most 38 ticks apart, and the player with the last group; each group must then be seen moving. The run-ahead and the warm-up play ticks and put them back: what was seen before the drive is only taken in after a real tick, and only when the race's clock has moved on from it by one. The first version went by speed alone and failed on a car of the last group pushed while it waited; the second sampled from the test's thread, where a speculative tick could be read.
7. **Saving only what changed.** A changed flag per choice (computer cars, lineup, picks), as `FeatureSettings` keeps one per switch.
8. **A race's choices are fixed with its field.** `GameUi.carspergame` fixes the field's size; `CustomGame.fixField` copies the choices there, and the cars (`pickCars`, `classicPicks`), the recording (`recorded`) and the hidden test's check use that copy. A change from the developer console during the car or stage select is for the next race, so a recording always holds the choices its field was made from.
9. **A practice race has no computer car.** With none, slot 1 is no car of the race and is outside the race state, so the AI is not run for it (the classic loop ran it once whatever the count, which no classic race reaches), and the wasted count and the HUD's wasted messages leave it out. Every race with computer cars runs the same loops as before.
10. **Nine or fewer computer cars: the classic start, exactly.** Every car starts on Go!, as the classic game starts them, including its quirk: no AI runs during the countdown, so the computer cars drive Go!'s tick with released controls and the player with the keys held. The spec asks for every car at once with 9 or fewer, and its check is the `all at once` line; Default with six is the classic race, so this start is not changed. The staggered start's later groups have no such tick: their AI runs from the tick before (decision 4), so they go on the same tick as the player.
11. **The hidden test lives in the folder.** `CustomGameTest` drives the run through a small `Host` that `GameSparker` fills with its own test helpers (screenshots, Enter, the held keys, the end of a run); the race tick has two calls that feed the start's watch. `GameUi` keeps only the calls into the page (open it, draw a frame of it, its mouse), as it does for the settings screen.

## Performance: 250 fps at 1080p

Nothing new is drawn; the HUD's countdown is the same layer as before. The menu's placement is seven additions a frame. With the switch on, the extras' per-tick copy of the race (`FeatureBridge.afterTick`) runs for every race, as for any extra that is on; the custom game queues nothing to draw. The race tick gains no work: `drivesNext` is one check per computer car, as `held` was.

## Races stay identical

What the gates should show (their actual lines are in tasks.md, from the revision they ran on):

- `test\verify-races.ps1`: every race matched, no golden frame changed. No classic race reaches a custom branch: `World.startGroups` is 0, so `beforeDrive`, `afterDrive` return at once and `drivesNext` is true; the loops from car 1 behave as before with any computer car; the switch only shows a menu item.
- `test\verify-races.ps1 -Features races -NoGolden`: the switch on changes no recorded race (a recording sets the custom game from its own string).
- `test\verify-sound.ps1`: unchanged.

## Risks / Trade-offs

- A held car can be pushed, by a car spinning back or attacking; the race shows it and the README says so. The test does not count it as a start: it reads the controls.
- The start's watch is two calls in the race tick, each one check of a field that is null outside the hidden custom test.
- Recorded custom races from the first build no longer match (decision 4); they were only ever in temporary folders.
