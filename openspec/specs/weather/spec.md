# Weather

## Purpose
Weather for the races: rain, with atmospheric fog, and snow. Until now there was only the time of day. The inspiration for the look of the rain is Driveclub. It changes the look and the sound, never how the cars drive.

## Requirements

### Requirement: Rain and snow
The game SHALL have weather: rain and snow. With rain can come atmospheric fog. (Owner, 2026-11-26.)

#### Scenario: Check: a race in each weather
- **WHEN** a race is started in rain, and another in snow
- **THEN** rain falls in the first, with fog when it is set, and snow in the second

### Requirement: Weather never changes how the cars drive
Rain and snow SHALL change the looks and the sound only. They SHALL NOT change how the cars drive: the road is no more slippery in rain or snow than in the dry. (Owner, 2026-11-26: picked "Looks and sound only".)

#### Scenario: Check: the same race in every weather
- **WHEN** a recorded race is replayed in the dry, in rain and in snow
- **THEN** every car ends where it ended in the recording, in all three

### Requirement: Rain looks good and intensive
Rain SHALL look good and intensive, and SHALL react with the environment as much as possible. The inspiration for its look is Driveclub. (Owner, 2026-11-26.)

#### Scenario: Check: the owner looks
- **WHEN** the owner drives a race in heavy rain
- **THEN** the rain reads as heavy rain that touches the world (the road, the cars, the screen), and the owner signs it off

### Requirement: Rain changes fire and smoke
In rain, particles like fire and smoke SHALL change: smoke fades a bit quicker and sinks to the ground faster. (Owner, 2026-11-26.)

#### Scenario: Check: smoke in rain
- **WHEN** the same explosion happens in dry weather and in rain
- **THEN** its smoke is gone sooner and hangs lower in the rain

### Requirement: Wet roads spray
In rain, drifting SHALL throw up water particles as well. The rear tyres SHALL NOT throw water while the car is airborne. (Owner, 2026-11-26.)

#### Scenario: Check: a drift and a jump
- **WHEN** a car drifts in the rain, then jumps
- **THEN** water sprays from its sliding tyres on the road and none while it is in the air

### Requirement: Puddles
Puddles SHALL look like they belong in the game. Small ones SHALL also form inside the foxholes that explosions made. (Owner, 2026-11-26.)

#### Scenario: Check: a crater in the rain
- **WHEN** it rains on a stage with a crater
- **THEN** puddles lie on the ground and a small one fills the crater

### Requirement: Wipers and droplets in the rain
In rain the windscreen wipers SHALL be on by default, and the screen SHALL have droplets on it. When tailing another car the droplets are intensified. (Owner, 2026-11-26.)

#### Scenario: Check: behind another car
- **WHEN** the player follows closely behind another car in the rain
- **THEN** the wipers work, droplets sit on the screen, and there are more of them than in clear air

### Requirement: Rain washes the car
If rain happens during a race, it SHALL wash the car's dirt off. Sliding in wet dirt SHALL give a different type of stain on the car. (Owner, 2026-11-26.)

#### Scenario: Check: a dirty car in the rain
- **WHEN** a dirty car drives on in the rain, then slides through wet dirt
- **THEN** the dry dirt washes off, and the wet dirt leaves a different, wet stain

### Requirement: Snow is 3D and collects
Snow SHALL be actually 3D. It can collect on the sides of the track, for example where cars drift. A car that is rammed onto its side or roof drags some snow with it, leaving a visible snow puddle on the track. (Owner, 2026-11-26.)

#### Scenario: Check: a drift in the snow
- **WHEN** cars drift through a snowy corner
- **THEN** snow piles up at the side of their path

#### Scenario: Check: sliding on the roof
- **WHEN** a car slides on its side or roof through snow
- **THEN** it pushes snow along and leaves a heap where it stops

### Requirement: Snow gets deeper during the race
If snow falls during a race, the track SHALL be covered dynamically: as the race progresses the snow gets deeper, capped at a reasonable amount. (Owner, 2026-11-26.)

#### Scenario: Check: a long race in snowfall
- **WHEN** a race runs for several minutes while it snows
- **THEN** the snow on the track is deeper at the end than at the start, and stops growing at its cap

### Requirement: Snow collects on the cars
Snow SHALL collect slightly in the cars' wheel arches, and in some cases on the car as well. DR Monstaa and M A S H E E N can collect snow in their trunks, and a hard landing knocks it out. Ramming another car makes snow fly off. (Owner, 2026-11-26.)

#### Scenario: Check: driving in the snow
- **WHEN** a car drives a while in snowfall
- **THEN** snow sits in its wheel arches

#### Scenario: Check: a hard landing
- **WHEN** DR Monstaa or M A S H E E N lands hard with snow in its trunk, or a snowy car is rammed
- **THEN** the snow is knocked out or flies off

### Requirement: Dry spots under the cars at the start
If a race starts in rain or snow, the ground under each car on the starting grid SHALL be dry. (Owner, 2026-11-26.)

#### Scenario: Check: the grid in the rain
- **WHEN** a race starts in rain or snow and the cars drive off
- **THEN** a dry spot shows where each car stood

### Requirement: Snow trails
The trails cars leave in the snow SHALL be visible. (Owner, 2026-11-26.)

#### Scenario: Check: driving through fresh snow
- **WHEN** a car drives through snow
- **THEN** its wheels leave tracks that stay behind it

### Requirement: Explosions and fire clear snow
An explosion SHALL clear the snow around it. Burning cars are warm, so the snow SHALL melt around them. (Owner, 2026-11-26.)

#### Scenario: Check: a blast and a wreck in the snow
- **WHEN** a dynamite stick blows up on snow, and a car burns on snow
- **THEN** the ground is bare around the blast, and the snow melts back around the burning car

### Requirement: Rain and snow have more oomph
Rain and snow SHALL be enhanced beyond the first version: the weather effects need more "oomph". (Owner, 2026-11-27.)

#### Scenario: Check: the owner looks
- **WHEN** the owner drives a race in rain, and another in snow
- **THEN** both read as stronger weather than the first version did, and the owner signs it off

### Requirement: Snow is a bed over the whole map
Snow SHALL lie as a bed across the entire map, not only in places: everywhere there is a plain of snow. It can accumulate, and a car SHALL leave marks in it, a bit like the snow in Forza Horizon 3, where the car's shape actually changes the snow. (Owner, 2026-11-27.)

#### Scenario: Check: marks anywhere on the map
- **WHEN** a car drives through the snow far from the track
- **THEN** it leaves marks in the snow there as it does on the track

#### Scenario: Check: the car's shape in the snow
- **WHEN** a car lands in the snow, or comes to rest in it on its side
- **THEN** it leaves a mark of its own shape

#### Scenario: Check: the bed gets deeper
- **WHEN** a race runs for several minutes while it snows
- **THEN** the bed is deeper at the end, and the marks cars leave in it are deeper too

### Requirement: Snow does not show through things
Blocks of snow under a car, and snow as it accumulates, SHALL NOT render through things. (Owner, 2026-11-27: "Same bug with the blocks of snow, when they are under the car or when they accumulate, they render through things, and this should be fixed.")

#### Scenario: Check: snow under and beside a car
- **WHEN** a car stands or drives in the snow and is looked at from every side
- **THEN** the snow under it, at its wheels and heaped against it is hidden where the car stands in front of it

### Requirement: Snowfall stays light while driving
The snowfall SHALL look while driving, even slowly, as it does at a standstill: it SHALL NOT get stronger, and the snowflakes SHALL NOT get long at speed. (Owner, 2026-11-27: "Snow effect looked okay when on a standstill, but was wayy too strong when driving, even slowly. I see the snowflakes get very "long" when driving at speed, so this needs to be scaled down majorly.")

#### Scenario: Check: standing and at speed
- **WHEN** the same snowfall is seen from a standing car, a slow one and one at top speed
- **THEN** no more of the picture is covered by flakes while driving than standing, and no flake is drawn out into a streak
