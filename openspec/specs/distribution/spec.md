# Distribution

## Purpose
Who gets the game and how: Windows only for now, downloaded from GitHub and run straight away.

## Requirements

### Requirement: For the owner, friends and maybe the public
The game SHALL be good enough to hand to friends, and later perhaps the public: it runs on another Windows PC without setup. (Owner, 2026-11-21, R1 Q2.)

#### Scenario: Check: a clean PC
- **WHEN** the release download is unpacked on a Windows PC with no Java installed
- **THEN** the game starts and a race can be played

### Requirement: Windows only, for now
The game SHALL target Windows only for now. (Owner, 2026-11-21, R1 Q24.)

#### Scenario: Check: platform
- **WHEN** a change adds a platform-specific feature
- **THEN** it only has to work on Windows

### Requirement: Download from GitHub and run
A release SHALL be a download from GitHub that runs straight away: unpack, run the exe, play. An installer is optional. The repo SHALL have a `.gitignore` that keeps build output, the bundled runtime, logs, replays, caches and personal files out of git. (Owner, 2026-11-21, R1 Q25, R2 A12.)

#### Scenario: Check: what git tracks
- **WHEN** `git status --ignored` runs after a full build and a played race
- **THEN** `madness.jar`, `madness.aot`, `runtime\`, `logs\`, `replays\` and `cache\` are listed as ignored, not as untracked
