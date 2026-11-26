# Powerups

## Purpose
Powerups on the maps, with their own sounds, and explosions in a stylised look that suits the old game. They change races, so they come after 1.0 (see Release 1.0).

## Requirements

### Requirement: Powerups spawn on the maps, with a pickup sound and a hum
Powerups SHALL spawn on the maps. Picking one up SHALL play a sound effect, and while a car holds one it SHALL make a passive humming sound that depends on the powerup. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: pickup
- **WHEN** a car drives through a powerup
- **THEN** the pickup sound plays once, and that powerup's own hum plays from the car until the powerup is used

### Requirement: Nitrous
Nitrous SHALL be one of the powerups. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: nitrous
- **WHEN** a car holding nitrous uses it
- **THEN** the car gets a burst of speed

### Requirement: Dynamite
Dynamite SHALL be one of the powerups: 2 seconds after it is picked up, the car throws one dynamite stick forwards. When the stick blows up, the map SHALL deform a bit. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: throw timing
- **WHEN** a car picks up dynamite
- **THEN** one stick leaves the car, forwards, 2 seconds later

#### Scenario: Check: the map deforms
- **WHEN** the stick blows up
- **THEN** the ground where it blew up is dented afterwards

### Requirement: Stylised explosions
Explosions SHALL be stylised to suit the game's old look, using dithering. An explosion SHALL spawn a cloud of smoke, which may be physics-based (like CS2's), and the screen SHALL flash bright for 1 to 2 frames. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: an explosion
- **WHEN** a dynamite stick explodes
- **THEN** 1 or 2 drawn frames are flashed bright, the fireball and smoke are drawn dithered, and a smoke cloud stays for a while

### Requirement: Bomb is the third powerup
The third powerup SHALL be a bomb, instead of the magnet: a standard bomb-looking bomb. Driving onto it makes a bang, does around 30% damage and throws the car around slightly. (Owner, 2026-11-26.)

#### Scenario: Check: driving onto a bomb
- **WHEN** a car drives onto a bomb
- **THEN** there is a bang, the car takes around 30% damage and is thrown around slightly

### Requirement: Powerups are rare
Powerups SHALL be about 4 times more rare than in the first build (three boxes at each checkpoint, back after 4 seconds). (Owner, 2026-11-26.)

#### Scenario: Check: a race with powerups
- **WHEN** cars race a stage with powerups on
- **THEN** about a quarter as many powerups are picked up as in the first build on the same stage

### Requirement: Two nitros that overlap burn red
If a car launches a nitro, immediately collects another and launches it while the first still burns, the flame SHALL become red instead of blue, and a bit bigger. (Owner, 2026-11-26.)

#### Scenario: Check: overlapping nitros
- **WHEN** a car uses a nitro while its last one still burns
- **THEN** its flame is red and a bit bigger for as long as the two overlap, and blue again when only one burns

### Requirement: Nitro degrades the handling
While nitro burns, the car's handling SHALL be degraded: it is more slippery. (Owner, 2026-11-26.)

#### Scenario: Check: a corner on nitro
- **WHEN** a car takes the same corner with and without nitro burning
- **THEN** it slides more with the nitro

### Requirement: Nitro sounds different in the air and doubled
A nitro used in mid-air SHALL shift its sound slightly toward an afterburner. With ground or air, single or double, there are four sounds. (Owner, 2026-11-26.)

#### Scenario: Check: the four nitros
- **WHEN** a nitro burns on the ground, in the air, doubled on the ground and doubled in the air
- **THEN** each of the four sounds different, and the two in the air sound more like an afterburner

### Requirement: Dynamite aims slightly at the nearest enemy
Dynamite SHALL still be thrown after 2 seconds, but SHALL slightly auto-aim toward the nearest enemy: a couple of degrees. The player must still use their handling to aim it properly. (Owner, 2026-11-26.)

#### Scenario: Check: an enemy a little to one side
- **WHEN** a car throws dynamite with an enemy a few degrees off its nose
- **THEN** the stick leaves turned toward that enemy by a couple of degrees at most, and with the enemy far to one side it still misses

### Requirement: Every explosion leaves dynamic smoke
Explosions of all sorts SHALL leave smoke clouds, which react to the environment and to the cars that drive through them (dynamic smoke). (Owner, 2026-11-26.)

#### Scenario: Check: driving through the smoke
- **WHEN** a car drives through the cloud an explosion left
- **THEN** the smoke is pushed aside and along by the car, and the cloud drifts and settles with the surroundings

### Requirement: Wasted cars can explode
A new setting SHALL make a wasted car, instead of only being on fire, light on fire and then explode a couple of seconds later. This explosion SHALL deform the map as well. (Owner, 2026-11-26.)

#### Scenario: Check: a car is wasted with the setting on
- **WHEN** a car is wasted
- **THEN** it burns for a couple of seconds, then explodes, and the ground where it blew up is dented afterwards

#### Scenario: Check: the setting off
- **WHEN** a car is wasted with the setting off
- **THEN** it burns as it always did and does not explode
