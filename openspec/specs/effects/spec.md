# Effects

## Purpose
Effects that show what the cars are doing: skidmarks and the Radical One's wing trail.

## Requirements

### Requirement: Skidmarks are per wheel
Skidmarks SHALL be made per wheel. Each wheel answers two questions: is it touching something, and how is its tread moving over that surface? A wheel can slide lengthwise (locked, or spinning too fast), sideways (cornering) or both. On a crooked landing only the wheel that hits first marks first. Locked-wheel marks can have streaks along the mark, and a rolling tyre sliding sideways can leave fine diagonal striations. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: one wheel locks
- **WHEN** one wheel locks while the other three roll
- **THEN** only that wheel leaves a mark, and the mark ends where that wheel rolls cleanly again

### Requirement: Skidmarks show each maneuver
The skidmarks SHALL show, for each maneuver in the owner's table, what the player should see, as the scenarios below list. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: hard braking
- **WHEN** a car brakes hard
- **THEN** a faint onset deepens if a wheel locks, and each mark ends when its wheel rolls cleanly again, even while the player still brakes

#### Scenario: Check: understeer
- **WHEN** the player turns hard and the nose keeps pushing wide
- **THEN** the front tyres scrub two arcs

#### Scenario: Check: power slide
- **WHEN** the rear swings out
- **THEN** the rear draws a wider arc than the front, the more heavily loaded outside wheel leaves the stronger trace, and countersteering changes the angle of the front traces on their own

#### Scenario: Check: handbrake 180
- **WHEN** the handbrake locks the rear axle through a 180
- **THEN** the rear tyres draw two sweeping hooks, and the fronts roll fairly cleanly through much of the rotation

#### Scenario: Check: stationary burnout
- **WHEN** the driven wheels spin with the car standing still, then the car hooks up
- **THEN** they darken two spots, which stretch into streaks as the car moves

#### Scenario: Check: getting T-boned
- **WHEN** a car is hit from the side and its driver never touches the brake
- **THEN** it leaves four short sideways scrub marks that start at the point of impact

#### Scenario: Check: landing while rotating
- **WHEN** a car lands while rotating
- **THEN** the first wheel down draws a little hook, a bouncing wheel leaves gaps, and landing backwards with the tyres still spinning forwards leaves a dramatic scrub

#### Scenario: Check: two-wheel driving
- **WHEN** a car drives on two wheels
- **THEN** only the loaded side marks the road, and if the body rolls further a separate metal scrape appears beside the rubber

### Requirement: Skidmarks carry the surface's colour
Coming from mud onto tarmac, the skidmarks SHALL be coloured slightly in the colour of the surface the tyre was on. (Owner, 2026-11-23, extras brief: "lets try this also".)

#### Scenario: Check: mud onto tarmac
- **WHEN** a car slides from mud onto tarmac
- **THEN** its first marks on the tarmac are tinted with the mud's colour, fading to plain rubber

### Requirement: Skidding leaves particles
Skidding SHALL leave fancy physics-style particles. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: particles
- **WHEN** a car skids
- **THEN** particles come off the sliding wheels and move on their own

### Requirement: The Radical One's wing trail
The Radical One SHALL show a slight trail from its wing while it is in mid-air and the player is actively steering it in mid-air. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: steering in the air
- **WHEN** the Radical One is in the air and the player steers it
- **THEN** its wing leaves a slight trail, and there is none on the ground or in the air without steering
