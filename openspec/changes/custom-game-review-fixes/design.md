## Context

- The extras framework (`nfm.lit.features`) gives each extra a folder, an entry in `Features.ALL` and a switch in `FeatureSettings`; the Extras tab shows a switch only for a feature in that list. The race's own classes are package-private, but `GameFacts`, `GameUi`, `World` and `Control` are public, so a feature can read and set what it needs through them.
- The race tick: before 1.0 untouched for every classic race. In a tick the game latches the player's keys, drives every car with the controls it has, then runs each computer car's AI (`Control.preform`), which sets its controls for the next tick. During the countdown no AI runs, so at the classic Go! every computer car drives one tick with released controls.
- The hidden tests live in `GameSparker` next to each other (`runAutoTest`, `runSplitTest`, `runCustomTest`) and share its helpers (screenshots, Enter, the held keys, the end of a run).

## Goals / Non-Goals

**Goals:** every finding of the blind review that shows a requirement unmet or a bug, fixed; the custom game in its own folder behind its switch; the classic game, its races and golden frames unchanged.

**Non-Goals:** changing what the owner asked for; moving the engine's big-field support (in-between positions, shadows, replay sound, occlusion for the 8 nearest cars, the standings past ten cars) into the feature, since any big field needs it; moving the hidden test's driving loop out of `GameSparker`, where every hidden test lives.

## Decisions

1. **Off by default until 1.0, as a race extra.** `release-1-0` holds races and the look to the original until 1.0, and the framework turns every race extra off by default until then, the owner's own powerups included. Custom Game shows on the main menu once the owner turns on Custom game in the Extras tab. *Alternative:* on by default, as the first build had it. Rejected: the default game would then not be the original.
2. **The menu keeps its indices.** Custom Game stays item 3; with the switch off it is skipped by the drawing, the mouse and the keys, the items after it move up a step (31 pixels) and the backdrop closes by as much, so the menu is the classic one. The items are placed every frame, since the switch can change while the menu is up.
3. **A page through `Page`.** The settings screen's rows are private to it; `Page` (tab, group, button, choice, only-when) lets a feature lay out a page of its own, and `SettingsScreen.page` makes the screen. *Alternative:* the page's rows kept in `SettingsScreen`. Rejected: feature code in a big file.
4. **The AI of a held computer car runs from the tick before its group starts** (`drivesNext`: not held on the next tick). Its controls are then ready on the start tick, which `beforeDrive` no longer overrides, so the group goes on the same tick as the player. The first group and every classic race are as before: `drivesNext` is always true when the race starts at once.
5. **A held car is not frozen.** It stands with the handbrake on; another car hitting it pushes it, as any car standing on the road. Freezing it would mean a new physics rule. With the front group going first and the player behind everyone, only a car spinning back reaches a held one; the hidden test fails if a car is seen moving before its group's start, so such a push shows up.
6. **The test checks what the race shows.** The field against what each lineup brings (Novice cars 0 to 8, each once before any twice; Pro 15 then down to 9; Custom the picks exactly; Default the stage's span with its boss in front); the grid's z against the player's; each group's first movement from the cars' speeds, sampled every 100 ms with the speeds read before the clock, so a car is never seen early by mistake.
7. **Saving only what changed.** A changed flag per choice (computer cars, lineup, picks), as `FeatureSettings` keeps one per switch.

## Performance: 250 fps at 1080p

Nothing new is drawn; the HUD's countdown is the same layer as before. The menu's placement is seven additions a frame. With the switch on, the extras' per-tick copy of the race (`FeatureBridge.afterTick`) runs for every race, as for any extra that is on; the custom game queues nothing to draw. The race tick gains no work: `drivesNext` is one check per computer car, as `held` was.

## Races stay identical

- `test\verify-races.ps1`: 20 matched, 0 changed. No classic race reaches a custom branch: `World.startGroups` is 0, so `beforeDrive`, `afterDrive` return at once and `drivesNext` is true; the switch only shows a menu item.
- `test\verify-races.ps1 -Features races -NoGolden`: the switch on changes no recorded race (a recording sets the custom game from its own string).
- `test\verify-sound.ps1`: unchanged.

## Risks / Trade-offs

- A held car pushed by a car spinning back fails the hidden test; that is a real event the test should show, not noise.
- Recorded custom races from the first build no longer match (decision 4); they were only ever in temporary folders.
