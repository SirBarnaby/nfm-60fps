# Performance

## Purpose
How fast and how smoothly the game must run: a very high frame rate with no stutter at all, on the owner's PC, kept that way after every change.

## Requirements

### Requirement: 250 to 350 frames a second at 1080p fullscreen
On the owner's PC, at 1920x1080 fullscreen with the owner's settings and no frame limit, every part of a race (countdown, race, replays, highlights) SHALL run at 250 to 350 frames a second or more, in one-player races and in split screen alike. The monitor's 60 Hz is not the target. (Owner, 2026-11-21, R2 A3.)

#### Scenario: Check: live test, one player
- **WHEN** `test\live.ps1` runs on the owner's PC with the owner's settings
- **THEN** no race part is flagged as under 250 fps (it flags any race part under `-MinFps`, 250 by default)

#### Scenario: Check: live test, split screen
- **WHEN** `test\live.ps1 -Split` runs on the owner's PC
- **THEN** no split race part is flagged as under 250 fps

### Requirement: No stuttering, ever
The game SHALL NOT stutter: not at the start of a race, not during it, not in menus. Frames SHALL come evenly. (Owner, 2026-11-21, R2 A4.) The one exception: switching fullscreen on or off MAY pause for up to half a second while the window and the graphics card restart. (Owner, 2026-11-21, decision card.)

#### Scenario: Check: live test pacing
- **WHEN** `test\live.ps1` and `test\live.ps1 -Split` run on the owner's PC
- **THEN** no part is flagged `uneven`, `frames late`, `hitch` or `stall` (the fullscreen switch is flagged only for a pause over 500 ms)

#### Scenario: Check: the owner plays
- **WHEN** the owner starts a race at 1080p fullscreen
- **THEN** the countdown and the first seconds of the race run without a visible stutter

### Requirement: The game always stays optimised
Every change, graphical ones included, SHALL keep the frame rate high. A change that lowers it below the target is a defect to fix, not a trade-off to accept. Up to 1.0 there are no exceptions; after 1.0, complex changes may lower the frame rate. (Owner, 2026-11-21, R1 Q3, Q13.)

#### Scenario: Check: performance gate
- **WHEN** `test\perf.ps1` runs after a change
- **THEN** it reports no regression against the PC's baseline

### Requirement: The graphics card does the drawing
Drawing SHALL be done the way modern games do it: the graphics card draws the game, menus and every race view, split screen included. Drawing on the processor is used only when the card cannot be used at all. (Owner, 2026-11-21, R1 Q14, R2 A10.)

#### Scenario: Check: nothing drawn in software
- **WHEN** `test\live.ps1` and `test\live.ps1 -Split` run with the GPU renderer
- **THEN** no race part is flagged `drawn in software`

### Requirement: Minimal input lag
Input lag SHALL be kept as low as it is now or lower; no change may add lag. (Owner, 2026-11-21, R1 Q17.)

#### Scenario: Check: latency readout
- **WHEN** the game runs with `-Dnfm.profile=true` before and after a change
- **THEN** the `[draw]` line's "key to screen" time is no longer than before
