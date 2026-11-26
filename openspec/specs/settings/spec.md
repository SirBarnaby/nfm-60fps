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

### Requirement: Gameplay switches cannot be changed during a race
Toggles that are not meant to be changed during live gameplay SHALL NOT be changeable during it, since they add needless complexity: all gameplay-changing things, such as the Extras tab. (Owner, 2026-11-26.)

#### Scenario: Check: settings opened in a race
- **WHEN** the player opens the settings during a race
- **THEN** the gameplay-changing switches cannot be changed there, and can from the menus

### Requirement: A cheat code unlocks everything
A cheat code SHALL unlock everything. (Owner, 2026-11-26.)

#### Scenario: Check: entering the code
- **WHEN** the player enters the cheat code
- **THEN** every car and every stage is unlocked
