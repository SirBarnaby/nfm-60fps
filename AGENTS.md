# AGENTS.md

Instructions for AI coding agents (and people) changing Need For Madness: LIT.
The README explains every system in depth; this file is the short list of what
must never break and how to prove it did not.

## The one command

Build, then prove gameplay is unchanged:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 -Quick
powershell -NoProfile -ExecutionPolicy Bypass -File test\verify-races.ps1
```

Expected last lines: `20 matched, 0 differed, 0 could not be run` and
`golden frames: 80 same, 0 changed, 0 saved`. Exit code 0 is a pass; anything
else is a failure to fix, never a baseline to update.

Before a release or a large change, also run:

| Check | Command | Pass |
| --- | --- | --- |
| Full build (retrains the AOT cache, about 2 minutes) | `build.ps1` | ends with `Done.` |
| AOT cache valid | `runtime\bin\java --enable-native-access=ALL-UNNAMED -XX:AOTCache=madness.aot -XX:+PrintSharedArchiveAndExit -jar madness.jar` | no line starting `[` and containing `error` |
| Rollback | `test\verify-races.ps1 -Rollback 50` | as above |
| Sound | `test\verify-sound.ps1` | every race `same` (the baseline is per machine) |
| Speed | `test\perf.ps1` | no regression against this machine's baseline |

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
8. **Never take over the user's mouse, keyboard or screen** to test. Use the
   hidden runs above; `nfm.autotest` saves screenshots of every phase.

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
- **Natives** (bgfx, OpenAL Soft, SDL, libopenmpt, Steam Audio) are called
  through LWJGL or Java's foreign function interface; no JNI of our own.
- **Commits** go to `staging`, one change per commit, with a message that says
  what changed and how it was checked.
