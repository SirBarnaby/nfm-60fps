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
After a lot of braking, the brake discs SHALL turn red: dull red after long hard braking, orange at the extreme. The front discs glow first, since they do most of the braking, and the glow fades over a few seconds, faster at speed. (Owner, 2026-11-23: extras brief, then "Keep those in also".)

#### Scenario: Check: repeated hard braking
- **WHEN** a car brakes hard from top speed several times in a row
- **THEN** its front discs glow red, more than the rear ones, and fade over a few seconds once the braking stops

### Requirement: The brake disc sits inside the wheel
Glowing discs do not work well on wheels that are 100% opaque, so the cars SHALL get new wheel models with a brake disc hidden behind the wheel, in the game's own style. The brake SHALL be inside the wheel, never outside of it (as it showed on Sword of Justice). (Owner, 2026-11-26.)

#### Scenario: Check: where the disc sits
- **WHEN** a glowing disc is looked at from the side and from behind, on every car
- **THEN** it is seen through the openings of the wheel, inside it, and no part of it shows outside the wheel

### Requirement: A crushed radiator steams
When a front corner crushes the radiator, pale steam SHALL escape from that corner. At speed it streams backward over the bonnet; when the car stops it rises almost vertically. It thins out as the coolant runs out. On mid-engined cars, whose radiators sit in the side pods, a hard side hit does it instead. (Owner, 2026-11-23: the owner's own example in the extras brief, then "Keep those in also".)

#### Scenario: Check: front-corner hit
- **WHEN** a front-engined car takes a hard hit on a front corner, drives on, then stops
- **THEN** steam comes from that corner, streams back over the bonnet while it drives, and rises almost straight up once it stands

### Requirement: A damaged engine smokes
A damaged engine SHALL smoke from where the engine sits: light grey wisps first, then blue-grey smoke on throttle, then dark puffs as the damage grows. A ram into the front of a front-engined car, or the back of a mid-engined one, can start it. (Owner, 2026-11-23.) Engine smoke SHALL NOT appear as easily as in the first build: it must take a bit more effort. (Owner, 2026-11-26.)

#### Scenario: Check: growing damage
- **WHEN** a car's damage grows from light to heavy
- **THEN** its smoke goes from light wisps to blue-grey on throttle to dark puffs, coming from the engine's position

#### Scenario: Check: light damage
- **WHEN** a car has taken a few knocks, as cars do in the first laps of a race
- **THEN** its engine does not smoke yet

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

### Requirement: No bonnets or bumpers
The damage SHALL NOT try to emulate bonnets and bumpers: they do not look good. (Owner, 2026-11-26.)

#### Scenario: Check: a heavy front hit
- **WHEN** a car takes a heavy hit at the front or the back
- **THEN** no bonnet stands up or flaps and no bumper hangs or drags

### Requirement: Repair hoops fix all damage
A repair hoop SHALL fix ALL of a car's damage, the cosmetic damage and faults included. (Owner, 2026-11-26.)

#### Scenario: Check: through a hoop
- **WHEN** a car with every kind of damage drives through a repair hoop
- **THEN** none of its damage is left: no smoke, steam, cracks, broken lights, bent wheels, leaks or faults

### Requirement: Sword of Justice sparks when it rams
When Sword of Justice rams a car, there SHALL be a light metal glint and spark effect, every time, because it has spikes. (Owner, 2026-11-26.)

#### Scenario: Check: a ram
- **WHEN** Sword of Justice rams another car
- **THEN** a short glint and sparks show where they touched, and another car ramming the same way shows none

### Requirement: Every car has wipers and an exhaust pipe
All cars SHALL have windscreen wipers, at least simple low-poly ones, and an exhaust pipe if they do not have one already (around 8 polygons). (Owner, 2026-11-26.)

#### Scenario: Check: looking at each car
- **WHEN** each car is looked at in the car select
- **THEN** it has wipers on its windscreen and an exhaust pipe

### Requirement: Electrical damage sets the wipers off
If a car is badly damaged and is hit near the windscreen, electrical damage SHALL be able to happen: the wipers start working and water sprays onto the windscreen. After 2 to 3 minutes the water runs out. Both cases have a sound: a normal wipe on wet glass, and a quieter one once the water has run out. (Owner, 2026-11-26.)

#### Scenario: Check: hit near the windscreen
- **WHEN** a badly damaged car is hit near its windscreen
- **THEN** its wipers start wiping, water sprays on the glass and the wet wipe sound plays; 2 to 3 minutes later the spray stops and the wipe sounds quieter and dry

### Requirement: An oil leak
If a car with low HP is hit underneath, an oil leak SHALL be able to occur: bits of oil spawn on the track (merely visual). After 2 to 3 minutes the engine develops a bit more of a raspy sound. (Owner, 2026-11-26.)

#### Scenario: Check: hit underneath
- **WHEN** a car with low HP is hit from underneath and drives on
- **THEN** it leaves spots of oil on the track, and after 2 to 3 minutes its engine sounds raspier

### Requirement: A broken exhaust
If the exhaust of a car with low HP is hit, it SHALL break, and the car sounds a bit more raspy from then on. Each car has a different sound. (Owner, 2026-11-26.)

#### Scenario: Check: hit on the exhaust
- **WHEN** a car with low HP is hit where its exhaust is
- **THEN** it sounds raspier afterwards, in that car's own way

### Requirement: A damaged suspension squeaks
If the suspension of a car with low HP is damaged, turning the car SHALL be able to start making a faint metallic squeak. (Owner, 2026-11-26.)

#### Scenario: Check: turning after the damage
- **WHEN** a car with low HP and a damaged suspension turns
- **THEN** a faint metallic squeak is heard as it turns, and none from a car whose suspension is whole
