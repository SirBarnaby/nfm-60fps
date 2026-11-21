# Release 1.0 and the classic branch

## Purpose
What NFM:LIT 1.0 is: the original game ported to today's standards with split screen, playing and looking exactly like the original, kept on its own classic branch before the owner's own version starts changing everything.

## Requirements

### Requirement: 1.0 is the port plus split screen
1.0 SHALL be the original game ported to the newest standards, plus split screen. It is released only when split screen is finished and the owner is confident in it. Then the game is developed further on top of it. (Owner, 2026-11-21, interview R1 Q1, R2 A1.)

#### Scenario: Check: the owner's sign-off
- **WHEN** every requirement in these specs passes its check and split screen is finished
- **THEN** the owner plays it, and 1.0 is released only on the owner's word

### Requirement: The 1.0 tag marks the finished classic game
The git tag `v1.0` SHALL point at the commit the owner signs off as 1.0. The existing `v1.0` tag (19 Nov 2026, made before split screen existed) SHALL be moved there at release. (Owner, 2026-11-21, R2 A1.)

#### Scenario: Check: the tag at release
- **WHEN** the owner signs off 1.0
- **THEN** `git tag -f v1.0 <commit>` moves the tag to that commit, and `git describe v1.0` shows it

### Requirement: The classic game lives on its own branch
At 1.0 the classic game, split screen included, SHALL be kept on a git branch of its own named `classic`, kept as original as possible, so it can be played at any time. The owner's own version continues on the normal branches. (Owner, 2026-11-21, R2 A1.)

#### Scenario: Check: classic branch at release
- **WHEN** 1.0 is released
- **THEN** a `classic` branch exists at the `v1.0` commit, and later work on the normal branches does not change it

### Requirement: Up to 1.0, races play exactly like the original
Until 1.0, races SHALL play exactly like the original Need for Madness 2. After 1.0, on the normal branches, races may differ: the tick rate, the physics, the maps or anything else may change. (Owner, 2026-11-21, R1 Q6.)

#### Scenario: Check: recorded races
- **WHEN** `test\verify-races.ps1` runs on any commit before 1.0, or on the `classic` branch
- **THEN** it ends with `20 matched, 0 differed, 0 could not be run`

### Requirement: Up to 1.0, it looks like the original
Until 1.0, the game SHALL look like the original, improved only in a technical sense (sharpness, resolution, screen shapes, frame rate). After 1.0 the look may change completely. (Owner, 2026-11-21, R1 Q9.)

#### Scenario: Check: golden frames
- **WHEN** `test\verify-races.ps1` compares the golden frames before 1.0
- **THEN** it reports `0 changed`, unless the owner approved a technical picture change and its golden frames were redone after the owner looked at them
