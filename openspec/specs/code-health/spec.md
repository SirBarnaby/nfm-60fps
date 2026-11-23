# Code health

## Purpose
Keeps the code easy for Claude Code to change, since every future change to the game is made with Claude Code rather than through mods.

## Requirements

### Requirement: Easy to modify with Claude Code
The code SHALL be kept easy for Claude Code to read and change. Before 1.0, the leftover decompiler names SHALL be replaced with real names and the giant files and methods split up, along with any other cleanup that makes later work faster. (Owner, 2026-11-21, R1 Q1, R2 A5.)

#### Scenario: Check: no decompiler names left
- **WHEN** the source is searched before 1.0 for decompiler-style names (`i1`, `l12`, `aflk` and the like)
- **THEN** none are left in fields, methods or classes

#### Scenario: Check: no giant files
- **WHEN** the source files are measured before 1.0
- **THEN** no class or method is so large that it must be read in pieces, and the races still match (`test\verify-races.ps1`)

### Requirement: No new mod support
Explicit mod support SHALL NOT be extended: changes are made with Claude Code, not through mods. The existing mods folder stays as it is, untouched, unless it ever gets in the way. (Owner, 2026-11-21, R1 modding, R2 A6.)

#### Scenario: Check: a change touches loading
- **WHEN** a change touches how data files are loaded
- **THEN** it adds no new mod features, and the existing mods folder keeps working as before

### Requirement: Cleanup pass after bigger features
When a bigger feature is finished, the code it touched SHALL get a cleanup pass (reuse, simplification, dead code) before it is reported done. SonarQube scans are not required for this game. (Owner, 2026-11-21, R2 B16.)

#### Scenario: Check: report of a bigger feature
- **WHEN** a bigger feature is reported done
- **THEN** the report says a cleanup pass ran and what it changed

### Requirement: Lessons are written down
What is learned while working on the game (gotchas, causes of bugs, what worked) SHALL be written down in the repo, so the knowledge compounds. (Owner, 2026-11-21, R2 B16.)

#### Scenario: Check: after a hard bug
- **WHEN** a bug took real digging to find
- **THEN** its cause and fix are written into `docs\solutions\` in the same commit as the fix

### Requirement: Each feature lives in its own folder
Each new feature SHALL keep its code in its own folder, `src\nfm\lit\features\<feature>\`, behind one small `Feature` interface and one `Features` list that the game calls, with one on/off switch per feature (`FeatureSettings`). The big existing files only get one-line hooks. Features that change only the look or the sound read the race and never write it; features that change races keep their state in the race state. (Owner, 2026-11-23: extras brief "separate files", then "Keep those in also".)

#### Scenario: Check: a new feature
- **WHEN** a new feature is added
- **THEN** its code is in its own folder under `src\nfm\lit\features\`, it has its own switch, and the existing files gained only the calls into it

#### Scenario: Check: look and sound features leave races alone
- **WHEN** the recorded races are played again with every look and sound feature switched on
- **THEN** every race still matches tick for tick
