# Settings

## Purpose
Where settings live: in the game, like a normal game, with the developer console kept for testing.

## Requirements

### Requirement: Settings live in the game
Every setting a player needs SHALL be on the in-game settings screen, like in a normal game. (Owner, 2026-11-21, R1 Q20.)

#### Scenario: Check: a new setting
- **WHEN** a change adds a player-facing setting
- **THEN** it can be changed on the in-game settings screen, and the change is kept after a restart

### Requirement: The developer console stays
The hidden developer console SHALL be kept beside the settings screen, for testing. (Owner, 2026-11-21, R2 A11, read as "keep both".)

#### Scenario: Check: console opens
- **WHEN** the developer opens the console in the game
- **THEN** its commands still work

### Requirement: Description pictures come later
Settings SHALL NOT need description pictures for now; pictures may be added later. (Owner, 2026-11-21, R1 Q21.)

#### Scenario: Check: a new setting without a picture
- **WHEN** a change adds a setting
- **THEN** it works and is complete without a description picture
