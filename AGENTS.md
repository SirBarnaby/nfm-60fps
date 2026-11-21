# AGENTS.md

Instructions for AI coding agents (and people) changing Need For Madness: LIT.
The README explains every system in depth; this file is the short list of what
must never break and how to prove it did not. What the owner wants the game to
be lives in `openspec\specs`, written only from the owner's own answers; read
the specs that cover a system before changing it (see **Specs** below).

## The one command

Build, then prove gameplay is unchanged, the speed kept and the specs whole:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 -Quick
powershell -NoProfile -ExecutionPolicy Bypass -File test\verify-races.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File test\perf.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File test\spec-check.ps1
```

Expected last lines: `20 matched, 0 differed, 0 could not be run` and
`golden frames: 80 same, 0 changed, 0 saved`, then `[perf] as fast as the
baseline`, then `spec check: ... every one checked`. Exit code 0 is a pass;
anything else is a failure to fix, never a baseline to update. These four are
the gates `openspec\specs\done-and-testing` names; a report of done work gives
their results.

Before a release or a large change, also run:

| Check | Command | Pass |
| --- | --- | --- |
| Full build (retrains the AOT cache, about 2 minutes) | `build.ps1` | ends with `Done.` |
| AOT cache valid | `runtime\bin\java --enable-native-access=ALL-UNNAMED -XX:AOTCache=madness.aot -XX:+PrintSharedArchiveAndExit -jar madness.jar` | no line starting `[` and containing `error` |
| Rollback | `test\verify-races.ps1 -Rollback 50` | as above |
| Sound | `test\verify-sound.ps1` | every race `same` (the baseline is per machine) |
| Your settings | `test\live.ps1`, then `-Split` (on the owner's PC) | no flag: race parts at 250 fps or more (`-MinFps`), races and menus drawn by the card; read `report.md` |
| Blind review (last, before reporting) | `test\blind-review.ps1 -Base <commit before the work>` | `approve`; the review is saved in `docs\reviews\` |

## Invariants

1. **Gameplay never changes.** Not the handling, not the AI, not the quirks
   (boundary walls that inherit the previous stage's surface, values that carry
   over between races). The recorded races in `test\races` are the definition
   of correct. A change that makes a race differ is a bug, even if the new
   behaviour looks better.
2. **Never update a baseline to make a check pass.** `-UpdateGolden` and
   `verify-sound.ps1 -UpdateBaseline` are only for a change that is meant to
   alter the picture or the sound, after a person has looked at or listened to
   it. Gameplay has no baseline to update.
3. **The race is deterministic.** Physics and AI use `StrictMath`; the race,
   effects and camera shake each draw from their own random stream
   (`RaceRandom`); input is latched once per tick. Do not call `Math.sin`,
   `Math.random`, `System.nanoTime` or anything else machine- or
   timing-dependent from race code.
4. **All race state is capturable.** Run-ahead and rollback capture and restore
   the whole race (`RaceState`). A new field that a race tick reads or writes
   must be added there, or run-ahead silently corrupts races; `verify-races.ps1
   -Rollback 50` catches a missing one.
5. **Drawing and sound never touch the race.** The renderer, the presenter
   thread, the 3D sound and the HUD read race state; they never write it or
   draw from the race's random stream.
6. **Classic sound is exact.** With the sound set to Classic the game must
   sound exactly as the original did.
7. **Test runs stay hidden and silent.** `nfm.verify`, `nfm.record` with
   `nfm.autopilot`, and `nfm.autotest` runs open no visible window, play no
   sound and ignore saved settings and mods, so they test the game itself and
   never disturb whoever is at the PC. Keep it that way for every new setting:
   its hidden-run default must be the one the golden frames were made with.
   The live test (`nfm.live`, `test\live.ps1`) is the one run that reads the
   saved settings and mods, as the player's game does; it is still silent, off
   screen, never focused, a controller cannot drive it, and it saves nothing.
8. **Never take over the user's mouse, keyboard or screen** to test. Use the
   hidden runs above; `nfm.autotest` saves screenshots of every phase.
9. **The race is placed in the game's own 1280x720 view.** The race's
   picture takes the window's shape (`GameFacts.sceneWidth/sceneHeight`, from
   `GameFacts.pictureShape`), but the race camera and what is on screen for
   the race (`ContO.dist`, which repair hoops and chip clearing read) are
   worked out at 1280x720, so a race plays the same in any window. Only
   drawing looks through the shown shape, from the same place: wrap race
   drawing in `try (var _ = Medium.shownView())`, and 3D drawn in a menu (the
   car select's car, the stage select's stage) in `Medium.menuView()`. The
   menus and HUD have their own picture, `GameFacts.screenWidth/screenHeight`,
   which with the UI at native size is the window divided by the UI scale and
   is not the race's: lay out screens from it and `marginX/marginY`, the race
   from `sceneMarginX/sceneMarginY`, never from a fixed 1280x720. Check with
   `JAVA_TOOL_OPTIONS=-Dnfm.window=1720x720` and `verify-races.ps1 -NoGolden`,
   and look at hidden autotest screenshots at 1920x1080 and 2560x1080.

10. **Split screen leaves one player's race alone.** Player 1 is the
   statics (`Medium`, `GameUi`'s HUD fields, car 0); Player 2's camera is
   `SplitRace.cam2`, swapped in with `Medium.swap`, and their HUD state
   `hud2`, swapped in with `GameUi.asPlayer`. Every split path is behind
   `SplitRace.active != null` (the race) or `SplitRace.on` (its screens), and
   `GameFacts.humans` is 1 outside it, so the computer cars, `paceCar` and
   every recorded race are as before. Each pane is kept once a tick from its
   own camera (`GameSparker.KeptPanes`, a `RetainedScene` each) and placed for
   every frame like one player's race: by the card, each pane a
   `ProjectedScene` of its own drawn in its rectangle. On the game thread
   (no presenter) the two are joined into one scene, each after a
   `SceneList.PANE` (its rectangle, clipped), and placed there. The game sorts objects and their polygons by the
   depths the last drawing measured, so each pane keeps its own
   (`PaneDepths`), and `Medium.crs` (the car select's showroom shadows) is
   cleared by `loadstage`, so no way into a race keeps them. Check
   a change to the race or the HUD with `-Dnfm.autotest.split=auto,3`, with
   and without `-Dnfm.renderer=gpu`, as well as the gates.

1.0, the `classic` branch and what may change after 1.0 are in
`openspec\specs\release-1-0`. Until 1.0 the invariants above hold as written.

## Specs

`openspec\specs\<area>\spec.md` holds the owner's requirements (OpenSpec
format, https://openspec.dev). They come only from the owner's answers
(interview of 21 Nov 2026); never add a requirement inferred from the code.

- Start a change with `/openspec-propose <name>` (Codex: `$openspec-propose`);
  it writes `openspec\changes\<name>\` with the proposal, the spec edits, the
  design and the tasks. `/openspec-apply-change` builds it and
  `/openspec-archive-change` merges the spec edits into `openspec\specs`.
  `openspec\config.yaml` holds the rules every step follows.
- Only the owner changes or removes a requirement, or adds a feature nobody
  asked for. Everything else goes ahead without stopping: the owner works
  hands-off.
- A requirement that cannot be met is never quietly dropped or called a limit:
  finish the work, leave the requirement and its failing check as they are, and
  open the report with the gap and its numbers.
- Every requirement has a scenario named `Check: ...`; `test\spec-check.ps1`
  fails when one is missing. A new owner decision goes into the specs in the
  same commit, tagged "(Owner, YYYY-MM-DD.)", and the copy in the project
  files is refreshed.
- Before reporting work done, run `test\blind-review.ps1 -Base <commit before
  the work>`: Codex rates the commits against the specs with no word from you.
  Never pass it your own explanation. Fix findings that show a requirement
  unmet or a bug, commit, and run it once more; the report gives the last
  verdict and every finding left, with the path of its file in `docs\reviews\`.

## How things are done here

- **Settings** follow one pattern (see `loadMultisample` in `GameSparker`): a
  `-Dnfm.<name>` property for one run, else the saved cookie, else the default,
  with hidden runs pinned; a row in `SettingsScreen` (the in-game settings,
  with a line on what it does and when it does not apply); a developer
  console command in `DevTool` with a line in `commandDescriptions`; and a
  row in the README's options table.
- **Cookie names are matched by substring** (`readcookie` takes the last
  entry whose name *contains* the name asked for), so a new cookie's name must
  not contain an existing one's, nor be contained in one.
- **Data files are read through `GameFiles`**, so mods apply. Do not open
  `data\*.radq` or `data\stages` directly.
- **Shaders** live in `gpu\Shaders.java` and are compiled at build time; a
  changed shader is recompiled by the next build.
- **Kept scenes are placed on the graphics card** (`gpu\ProjectedScene.java`,
  `Shaders.PROJECTED_VS`), which mirrors `RetainedScene.placeOne` and
  `project`. A change to how a point kind is placed goes into both, and
  `-Dnfm.projectiontest=40` proves they still agree (well under 0.1% of
  pixels), with `-Dnfm.autotest.split=auto,3` for split screen's panes.
- **Every frame shown goes through `FrameLog.shown`**, which sums up each
  part of a session (the race, a menu) in the game's log and feeds the live
  test. A new way of putting frames on screen must call it too.
- **Natives** (bgfx, OpenAL Soft, SDL, libopenmpt, Steam Audio) are called
  through LWJGL or Java's foreign function interface; no JNI of our own.
- **Commits** go to `staging`, one change per commit, with a message that says
  what changed and how it was checked.
