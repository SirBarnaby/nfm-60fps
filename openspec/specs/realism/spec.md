# Realism

## Purpose
Realism in the world, in the spirit of GTA IV: cars that heat up, get damaged and show it, all in a cosmetic way. The owner's brief named brake discs turning red, engine smoke after a ram, the exhaust, and a crushed radiator steaming; the rest is the brainstorm the owner kept. They change the look, so until 1.0 they stay switched off by default (see Release 1.0).

## Requirements

### Requirement: Damage and heat are cosmetic only
Damage and heat effects SHALL be cosmetic only: they change what is seen and heard, never how a car drives or how a race plays. Each one reads the race (speed, braking, which corner was hit and how hard) and keeps a small state of its own, such as a temperature or how much coolant is left. (Owner, 2026-11-23: extras brief "make it only in a cosmetic way", then "Keep those in also".)

#### Scenario: Check: races with every effect on
- **WHEN** the recorded races are played again with every realism effect switched on
- **THEN** every race still matches tick for tick

### Requirement: Brake discs glow
After a lot of braking, the brake discs SHALL turn red: dull red after long hard braking, orange at the extreme, seen through the wheels. The front discs glow first, since they do most of the braking, and the glow fades over a few seconds, faster at speed. (Owner, 2026-11-23: extras brief, then "Keep those in also".)

#### Scenario: Check: repeated hard braking
- **WHEN** a car brakes hard from top speed several times in a row
- **THEN** its front discs glow red, more than the rear ones, and fade over a few seconds once the braking stops

### Requirement: A crushed radiator steams
When a front corner crushes the radiator, pale steam SHALL escape from that corner. At speed it streams backward over the bonnet; when the car stops it rises almost vertically. It thins out as the coolant runs out. On mid-engined cars, whose radiators sit in the side pods, a hard side hit does it instead. (Owner, 2026-11-23: the owner's own example in the extras brief, then "Keep those in also".)

#### Scenario: Check: front-corner hit
- **WHEN** a front-engined car takes a hard hit on a front corner, drives on, then stops
- **THEN** steam comes from that corner, streams back over the bonnet while it drives, and rises almost straight up once it stands

### Requirement: A damaged engine smokes
A damaged engine SHALL smoke from where the engine sits: light grey wisps first, then blue-grey smoke on throttle, then dark puffs as the damage grows. A ram into the front of a front-engined car, or the back of a mid-engined one, can start it. (Owner, 2026-11-23: extras brief "my engine can start blowing smoke", then "Keep those in also".)

#### Scenario: Check: growing damage
- **WHEN** a car's damage grows from light to heavy
- **THEN** its smoke goes from light wisps to blue-grey on throttle to dark puffs, coming from the engine's position

### Requirement: The exhaust shows the engine's state
The exhausts SHALL show the engine's state: a heat shimmer at idle, flames on lift-off at high revs, and a blue flame while nitrous burns. (Owner, 2026-11-23: extras brief "exhaust can do something similar", then "Keep those in also".)

#### Scenario: Check: lift-off and nitrous
- **WHEN** a car lifts off the throttle at top speed, and later uses nitrous
- **THEN** flames come from its exhausts on the lift-off, and a blue flame while the nitrous burns

### Requirement: Hot metal ticks
After a hard run, a stopped car SHALL tick quietly as its exhaust cools. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: stopping after a fast run
- **WHEN** a car stops after a long run at speed
- **THEN** a quiet ticking comes from it for a while and dies away

### Requirement: The bonnet flaps
After a heavy front hit, the bonnet SHALL pop up and flap in the wind, faster with speed, and it can tear off. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: heavy front hit
- **WHEN** a car takes a heavy front hit and drives on
- **THEN** its bonnet stands up and flaps, faster as the car speeds up

### Requirement: A bumper drags
After a hit at the front or the back, a bumper SHALL hang down and scrape the road, with sparks while it touches. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: hanging bumper
- **WHEN** a car takes a hard hit at the front or the back and drives on
- **THEN** a bumper hangs down and throws sparks where it scrapes the road

### Requirement: Glass cracks and shatters
The windscreen SHALL crack after hits, and side windows SHALL shatter into glass bits on heavy hits. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: hits on the glass
- **WHEN** a car takes a hit, then a heavy side hit
- **THEN** its windscreen shows cracks, and that side's window shatters into bits that fall to the road

### Requirement: A bent wheel wobbles
After a hard hit on a wheel, that wheel SHALL wobble and its skidmarks be wavy. A flat tyre SHALL throw sparks from the rim. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: hit wheel
- **WHEN** one wheel takes a hard hit and the car drives on
- **THEN** that wheel visibly wobbles and its skidmarks are wavy, while the other wheels run true

### Requirement: Dirt builds up
On soft ground, a car's lower body and wheels SHALL get dusty in the ground's colour, and the dirt SHALL slowly fade on tarmac. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: soft ground then tarmac
- **WHEN** a car drives a while on soft ground, then on tarmac
- **THEN** its lower body and wheels take the ground's colour, then slowly lose it

### Requirement: Scrapes leave scratches
The side of a car that scraped a wall SHALL get pale streaks and bare-metal chips where it touched. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: wall scrape
- **WHEN** a car scrapes a wall along one side
- **THEN** that side shows pale streaks and chips, and the other side does not

### Requirement: Bottoming out sparks
Big landings SHALL throw sparks from under the car, with a thud and a scrape. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: big landing
- **WHEN** a car lands hard from a big jump
- **THEN** sparks come from under it, and a thud and a scrape are heard

### Requirement: Debris stays
Bumper bits, glass and clods SHALL stay where they fell for the rest of the race, up to a cap, with the oldest pieces going first. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: after a crash
- **WHEN** a car crashes and the race goes on
- **THEN** its debris is still on the road on the next lap, and past the cap the oldest pieces go first

### Requirement: Lights work and break
Brake lights SHALL light up when a car brakes, and its lights SHALL break after front or rear hits. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: braking and a rear hit
- **WHEN** a car brakes, and later takes a hard rear hit
- **THEN** its brake lights light up while it brakes, and after the hit its rear lights stay dark

### Requirement: Heat haze
A faint shimmer SHALL show behind the exhausts and over hot engines. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: hot engine
- **WHEN** a car has been driven hard
- **THEN** a faint shimmer shows behind its exhausts and over its engine

### Requirement: Loose parts rattle
A damaged car SHALL rattle over bumps, faster at speed. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: damaged car over bumps
- **WHEN** a damaged car drives over bumps
- **THEN** a rattle is heard from it, faster at higher speed, and an undamaged car does not rattle

### Requirement: Each car has a short sheet
Each car SHALL have a short sheet saying its engine layout, where the engine sits, its cooling, turbo or not, and its brake type, which the effects read (where steam and smoke come from, how hot the brakes run). Claude suggests the values from each car's look, and the owner corrects them. (Owner, 2026-11-23: brainstorm kept with "Keep those in also".)

#### Scenario: Check: the sheets
- **WHEN** the realism effects are built
- **THEN** every car has a sheet, and the owner has looked the values over
