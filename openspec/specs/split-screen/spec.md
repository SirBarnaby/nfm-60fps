# Split screen

## Purpose
Local multiplayer on one screen, the way players expect it from games: up to four players in 1.0, built so more could be added.

## Requirements

### Requirement: Up to four players in 1.0
Split screen SHALL support 2, 3 and 4 players in 1.0, working the way normal split screen works in games: each player has their own view, HUD and controls. (Owner, 2026-11-21, R1 Q18, R2 A2.)

#### Scenario: Check: four-player race
- **WHEN** four players start a split-screen race
- **THEN** the screen is divided into four views, each following its own player's car with its own HUD

#### Scenario: Check: three-player race
- **WHEN** three players start a split-screen race
- **THEN** each player gets a view, and the screen has no black or unused area

### Requirement: Split screen is modular
Split screen SHALL be built so the number of players is not hard-coded: adding more than four players later SHALL need no rewrite of the split-screen code. (Owner, 2026-11-21, R1 Q18.)

#### Scenario: Check: code review
- **WHEN** the split-screen code is reviewed
- **THEN** players are handled as a list sized at the start of a race, not as fixed Player 1 and Player 2 code paths

### Requirement: Split screen is as fast and smooth as one player
Split screen SHALL meet the same frame-rate and no-stutter requirements as a one-player race, drawn by the graphics card. (Owner, 2026-11-21, R2 A3, A4, A10.)

#### Scenario: Check: live test
- **WHEN** `test\live.ps1 -Split` runs on the owner's PC
- **THEN** no split race part is flagged (software drawing, under 250 fps, uneven, hitch)
