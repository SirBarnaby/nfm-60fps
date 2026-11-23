# First person

## Purpose
A first-person view done well, for a game with a lot of stunting.

## Requirements

### Requirement: Helmet-cam first-person view
The game SHALL have a first-person view from the driver's head, approach A, "Helmet cam": the head sits on springs, so braking throws it forward, acceleration back, corners to the side, and landings jolt it; it looks into the corner ahead; and the edges of the view darken under g-force. (Owner, 2026-11-23: extras brief "First person must be done well", then picked A of three approaches.)

#### Scenario: Check: hard braking
- **WHEN** the car brakes hard in first person
- **THEN** the view moves forward, and settles back with a little overshoot when the braking ends

### Requirement: G-forces are visible in first person
In first person, appropriate g-forces SHALL be visible, in the spirit of NFS: Shift 2. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: a lap in first person
- **WHEN** the owner drives a lap with hard braking, a fast corner, a jump and a landing in first person
- **THEN** each one moves the view the way the force pushes the driver, and the owner signs it off
