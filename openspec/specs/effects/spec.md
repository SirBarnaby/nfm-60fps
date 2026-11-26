# Effects

## Purpose
Effects that show what the cars are doing: skidmarks and the Radical One's wing trail. They change the look, so until 1.0 they stay switched off by default (see Release 1.0).

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

### Requirement: Last race's skid zones can be shown
A toggle SHALL make the places where there were the most skidmarks in the last race somewhat visible. While a race runs, each stage keeps a coarse grid of how much sliding happened where, saved when the race ends; the next time on that stage, the heaviest zones show as a faint darker tone on the road. The toggle is off by default. (Owner, 2026-11-23: extras brief "Perhaps a toggle also", then "Keep those in also".)

#### Scenario: Check: second race on a stage
- **WHEN** the toggle is on and the player races a stage they raced before
- **THEN** the zones where most sliding happened last time show faintly darker, and with the toggle off they do not show

### Requirement: The wing trail looks like vapour
The Radical One's wing trail SHALL look like vapour: thin white streams from both wing tips, like wing-tip vortices, spreading and fading within about a second. It follows the car's smooth drawn position, so it does not step. (Owner, 2026-11-23: kept the suggested "Vapour" look with "Keep those in also".)

#### Scenario: Check: the trail's look
- **WHEN** the Radical One's wing trail shows
- **THEN** it is thin white streams from both wing tips that spread and fade within about a second, without steps

### Requirement: The track is littered by the end of a race
A race SHALL litter the map, so the world feels reactive, even if it costs a little performance: drifting in gravel and on the tarmac leaves bits and particles that stay on the track, and these particles sway in the wind of cars driving past them. Car damage is left out of the litter (mere polygons would look bad on the track). (Owner, 2026-11-26.)

#### Scenario: Check: after some laps
- **WHEN** cars have drifted through the same corner for a few laps
- **THEN** bits lie on the track there and are still there on the next lap

#### Scenario: Check: driving past
- **WHEN** a car drives past bits lying on the track
- **THEN** they sway or shift in its wind

### Requirement: Finishing a stunt into soft ground puffs
Finishing a stunt into snow SHALL create a particle effect, a puff of flying into the snow. The same SHALL happen on regular dirt: a small puff of that dirt. (Owner, 2026-11-26.)

#### Scenario: Check: landing a stunt
- **WHEN** a car lands a stunt on snow, then on dirt
- **THEN** a puff of snow flies up on the snow and a small puff of dirt on the dirt
