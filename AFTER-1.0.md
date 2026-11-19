# After v1.0: tick rate, handling and deformable stages

v1.0 (git tag `v1.0`) is the last version whose races play exactly as the
original's. Everything it does is proven against the recorded races in
`test\races`: same ticks, same physics, same AI, same quirks. The next big
changes cross that line on purpose:

- a higher **tick rate** than the original's 53 ms (about 19 a second),
- a new **handling model**,
- **dynamite**, or anything else that deforms the stage during a race.

Each of these changes what a race *is*, so the old recordings can no longer
be the definition of correct for them. This file is how to make those changes
without losing the proof that v1.0 has.

## The rule: rulesets, not replacements

Do not change the classic race in place. Add the new behaviour beside it as a
**ruleset**, and keep the classic one working:

| Ruleset | What it is | Proven by |
| --- | --- | --- |
| `classic` | v1.0 exactly: 53 ms ticks, the original handling, fixed stages | the 20 races in `test\races` and their golden frames, as today |
| a new one, e.g. `lit` | the higher tick rate, the new handling, deformation | its own recorded races, in a folder of their own |

A race log (`.nfmr`, `RaceLog`) then says which ruleset it was played with,
and `verify-races.ps1` plays each race under its own. Classic races keep
passing forever; the new ruleset gets the same tick-for-tick proof once its
own races are recorded. Recording is `test\record-races.ps1`; the autopilot
makes the races without anyone at the keyboard.

The first step, before any gameplay change, is therefore plumbing only:

1. Add a ruleset field to `RaceLog` (a new `VERSION`, with older files read
   as `classic`) and to the race start in `RaceSession`.
2. Give the game a current ruleset (a setting, default `classic` until the
   new one is ready) and pass it to everything below.
3. Run `verify-races.ps1` and `-Rollback 50`: still 20 matched, 80 same.

## Tick rate

The tick is `GameSparker.SCENE_TICK_NANOS` (53 ms). The frame loop is already
independent of it: frames are drawn between ticks (`renderIngameScene`
interpolates, kept scenes are placed on the graphics card), so a faster tick
does not cost frame rate by itself; it costs the game thread the tick's work
more often. Measure with `test\perf.ps1` (`[tick]` lines with
`-Dnfm.profile=true`).

What counts in ticks today, and must count in time instead under a faster
ruleset, so the race keeps its speed:

- **physics and AI** (`Madness`, `Stat`): speeds, accelerations, damage and
  every counter are per tick. Scale by the tick length, or keep the per-tick
  formulas and run several sub-steps per 53 ms.
- **the camera** (`Medium.follow`, `around`, `watch`, `getaround`): moves a
  fixed step per tick.
- **race flow and HUD timers** (`GameUi`: countdown `starcnt`, `holdcnt`,
  stunt captions, the blink phase `aflk`).
- **replays and highlights** (`Record`): buffers of 300 ticks, and the
  highlight cuts.
- **sound timing** (`RaceEvents`, the skid and crash rotations in `GameUi`).
- **the race clock** (`Replays.seconds` hard-codes 53 ms; `RaceSession` reports times from `SCENE_TICK_NANOS`).
- **run-ahead**: speculates exactly one tick; a shorter tick means less lag
  to win, so it may become unnecessary.

The cheapest exact first step is **sub-stepping**: keep the classic per-tick
code and call it N times per 53 ms with inputs latched per sub-step. That is
a faster tick in input terms without retuning anything, and `classic` is
simply N = 1.

## Handling

The handling is `Madness` (per car) and `Stat` (the car's numbers). A new
model should be a new class behind one interface, chosen by the ruleset, not
edits inside `Madness`. Everything it reads or writes during a tick must be
captured by `RaceState` (AGENTS.md invariant 4), or run-ahead and rollback
silently break; `verify-races.ps1 -Rollback 50` on the new ruleset's races
catches a missing field.

Keep it deterministic (AGENTS.md invariant 3): `StrictMath`, the race's own
random stream (`World.random`), no wall-clock time.

## Deformable stages (dynamite)

Today the stage is fixed for the whole race, and three things lean on that:

- **collision and the track** (`Trackers`, `CheckPoints`): built once when the
  stage loads.
- **drawing**: stage pieces are `ContO` objects drawn every frame; kept scenes
  (`RetainedScene`, `gpu\ProjectedScene`) record their polygons once per tick,
  so a piece that changes shape is picked up on the next tick with no extra
  work, as long as the change happens in the tick.
- **race state**: a deformation is race state. It must be in `RaceState`
  (so rollback can undo it) and drawn from the race's random stream if it is
  random.

So a deformation is: a race event (`RaceEvents`, so sound and effects can
react), a change to the piece's polygons and to the collision data for that
area, both made inside the tick, and both captured by `RaceState`. Do it in
a ruleset other than `classic`.

## What stays the same after v1.0

- AGENTS.md's invariants hold for every ruleset. "Gameplay never changes"
  becomes "a ruleset's gameplay never changes once its races are recorded".
- Widescreen is not a gameplay change: the race camera and what is on screen
  for the race are worked out in the 1280x720 view whatever the window's
  shape (`Medium.shownView` for the race, `Medium.menuView` in menus). Keep it that way when the camera
  changes: place the camera in the game's own view, draw in the shown one.
- Test runs stay hidden and silent; nobody's mouse, keyboard or screen is
  taken over to test.
