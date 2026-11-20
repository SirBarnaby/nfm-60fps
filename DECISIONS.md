# Decisions

The choices already made for this game, so a later change does not quietly
undo one. Each says what was decided and how to check it still holds. A change
that breaks one is a bug, unless the owner decides otherwise; then change this
file in the same commit. AGENTS.md has the invariants that keep races exact;
this file is about everything else.

## What the work is

- **Technical engine work only, until the owner asks for more.** The game is
  the original: its cars, stages, handling, AI and look. New gameplay (ghost
  cars, time trial, new modes) waits until asked for.
- **v1.0's races are the original's.** A higher tick rate, new physics or
  deformable stages come after v1.0 as a new ruleset beside the classic one,
  never in its place: see `AFTER-1.0.md`.
- **Moddable.** Data files are read through `GameFiles`, so a mod in `mods\`
  replaces any of them; nothing reads `data\` directly.

## The picture

- **The graphics card draws every race frame.** With the GPU renderer, one
  player's race, split screen, replays, highlights and the race behind the
  pause menu are all drawn by the card. Software drawing is only the fallback
  when the card cannot be used, and for frames with the debug overlay. Check:
  `test\live.ps1` (and `-Split`) flags a race part drawn in software, and the
  game's log says how every part was drawn ("Frames in race: ... kept scene
  100%").
- **Race frames are placed, not recorded.** Each game tick records the race's
  scene once, with a margin, and every frame until the next tick moves it to
  where the camera is now: one player's race, each split-screen pane, replays.
  Recording every frame is what made split screen slow. Check: the game's log
  says "kept scene 100%" for every race part, split races too.
- **No black bars.** The race and the menus take the window's shape, from 5:4
  to 32:9; past those shapes the race is cut a little to fill. Check: hidden
  autotest screenshots at 1920x1080, 2560x1080 and 1720x720.
- **Fullscreen fills the monitor.** It always draws in the monitor's shape:
  the aspect ratio is the window's, greyed out in fullscreen.
- **Fullscreen lets you pick the resolution** it draws at (in the monitor's
  shape, scaled to fill it), saved apart from the window's size; the
  monitor's own is the default.
- **The menus and HUD are drawn at native pixels**, 1x, 2x or 4x (Auto takes
  the largest that leaves a 1280x720 screen), never scaled by a fraction, so
  they stay sharp.
- **Menus are drawn once a game tick** (25 a second), as the original's: their
  pictures only change on a tick.

## Settings

- **Every setting follows one pattern**: a `-Dnfm.<name>` property, else the
  saved cookie, else the default; a row on the settings screen, a console
  command, a README line (see AGENTS.md).
- **Pictures on the settings screen only for picture and sound settings**
  (`SettingsScreen.PICTURED`); the controls and split screen have none.

## Testing

- **Tests never disturb whoever is at the PC.** No window on screen, no sound
  from the speakers, no mouse, keyboard or controller taken, nothing saved.
- **Hidden test runs ignore saved settings and mods**, so they test the game
  itself against the golden frames and recorded races.
- **The live test plays it your way.** `test\live.ps1` reads the saved
  settings and mods and starts Java as `madness64.exe` does, switches
  fullscreen the way F11 does, and reports frames a second and frame times per
  part, but stays off screen, silent and unsaved. What an off-screen window
  cannot show (frames paced by VSync, frames Windows holds up) is a note, not a
  flag, so exit code 0 means nothing is wrong.
- **Every frame shown is counted.** A new way of putting frames on screen
  must call `FrameLog.shown`, or the game's log and the live test cannot see
  it.
- **Never update a baseline to make a check pass.**
