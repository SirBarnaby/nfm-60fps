# Controls

## Purpose
How players drive the game: keyboard and controllers, every control rebindable, with as little input lag as possible.

## Requirements

### Requirement: Keyboard and controllers
The game SHALL be playable with a keyboard and with game controllers. A steering wheel is not needed for 1.0 and may come later. (Owner, 2026-11-21, R1 Q17, R2 A7.)

#### Scenario: Check: a controller drives a race
- **WHEN** a controller is connected and the player starts a race
- **THEN** the controller steers, accelerates and brakes the car, as the keyboard does

### Requirement: Every control can be rebound
The player SHALL be able to rebind the controls in the game, for one player and for every split-screen player. (Owner, 2026-11-21, R1 Q17.)

#### Scenario: Check: rebind a key
- **WHEN** the player rebinds accelerate to another key in the settings and restarts the game
- **THEN** the new key accelerates the car and the choice is kept
