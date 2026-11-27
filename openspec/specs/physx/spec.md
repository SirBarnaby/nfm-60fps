# PhysX

## Purpose
PhysX in the game, to try it out. It is merely a visual thing: what it moves is seen, and no race depends on it.

## Requirements

### Requirement: PhysX to try out
The game SHALL have PhysX built in, so the owner can try it out. (Owner, 2026-11-27.)

#### Scenario: Check: PhysX moves things in a race
- **WHEN** a race is played with PhysX switched on and cars crash
- **THEN** PhysX moves loose pieces through the world: they fall, tumble, come to rest, and a car that drives into them pushes them away

### Requirement: PhysX is only visual
PhysX SHALL be merely a visual thing. Its results need not be 100% identical from one run to the next. (Owner, 2026-11-27.)

#### Scenario: Check: the same race with PhysX on
- **WHEN** a recorded race is replayed with PhysX switched on
- **THEN** every car ends where it ended in the recording
