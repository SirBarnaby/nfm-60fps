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

### Requirement: Magnet is the third powerup
Magnet SHALL be the third powerup: it pulls the car toward the car ahead for 3 seconds. (Owner, 2026-11-23: picked Magnet of three candidates; the choice read "Pulls you toward the car ahead for 3 seconds".)

#### Scenario: Check: magnet
- **WHEN** a car uses the magnet with another car ahead of it
- **THEN** it is pulled toward that car for 3 seconds, then the pull stops

### Requirement: Stylised explosions
Explosions SHALL be stylised to suit the game's old look, using dithering. An explosion SHALL spawn a cloud of smoke, which may be physics-based (like CS2's), and the screen SHALL flash bright for 1 to 2 frames. (Owner, 2026-11-23, extras brief.)

#### Scenario: Check: an explosion
- **WHEN** a dynamite stick explodes
- **THEN** 1 or 2 drawn frames are flashed bright, the fireball and smoke are drawn dithered, and a smoke cloud stays for a while
