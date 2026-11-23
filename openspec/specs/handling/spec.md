# Handling

## Purpose
How the cars drive after 1.0. The owner's brief: "a more realistic suspension, and better skidding and handling ... still arcadey, but the cars have suspensions", with GTA VI as the inspiration.

## Requirements

### Requirement: After 1.0, arcade raycast suspension at 120 Hz
After 1.0, on the normal branches, the cars SHALL drive on approach B, "Arcade raycast suspension", ticking 120 times a second: a spring and damper at each wheel, smooth tyre grip shared between braking and turning, weight moving to the outside wheels in corners and forward under braking, and a drift assist that keeps slides catchable. It stays arcade, and stunts stay. (Owner, 2026-11-23: picked B of three approaches.)

#### Scenario: Check: tick rate
- **WHEN** a race runs on the normal branches after 1.0
- **THEN** the race steps 120 times per second of game time

#### Scenario: Check: the owner drives it
- **WHEN** the owner drives a race with corners, bumps, a jump and a stunt
- **THEN** the body visibly rides on its springs, slides can be held, stunts still work, and the owner signs it off
