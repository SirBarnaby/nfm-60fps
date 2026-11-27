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

### Requirement: The new handling steers as far and answers as fast as the old
The new handling SHALL NOT cap the steering radius. Turning SHALL have an immediate action, at least a perceivable one, especially when countersteering (with EL KING for example): somewhat instant, as the old handling model was. (Owner, 2026-11-27: "New handling seems to have steering radius capped. Also new handling seems a little less responsive.")

#### Scenario: Check: full lock
- **WHEN** a car holds full lock at speed for three seconds on the new handling, and the same on the old
- **THEN** it turns as far on the new as on the old

#### Scenario: Check: turning in and countersteering
- **WHEN** EL KING turns in, and countersteers out of a slide, on the new handling
- **THEN** in the first half second its path follows its nose as closely as on the old handling

### Requirement: A car never refuses to go forward
On the new handling a car SHALL NOT glitch and refuse to go forward. (Owner, 2026-11-27: "I had a bug when I was racing, that the car glitched a bit and wouldnt go forward. New handling bug?")

#### Scenario: Check: after a stunt landing
- **WHEN** a car lands a stunt turned over both ways, so that it faces the other way from its heading, and drives on
- **THEN** it drives forward as before the stunt, and the drift assist never turns it against its steering

### Requirement: Tailwhip
With the handbrake and the normal brake held and left or right pressed, the car SHALL rotate a bit more easily. This can be used to tailwhip enemies, which gives the enemy some extra spin. A tailwhip SHALL be called out in the global chat for all players: X tailwhipped Y, "TAILWHIP!". It probably changes the handling model, so it SHALL be a toggle in the settings. (Owner, 2026-11-26.)

#### Scenario: Check: the turn
- **WHEN** a car holds the handbrake and the brake and presses left with the toggle on
- **THEN** it rotates more easily than with the toggle off

#### Scenario: Check: the hit
- **WHEN** its swinging tail hits an enemy
- **THEN** the enemy gets extra spin and every player sees that X tailwhipped Y, "TAILWHIP!"
