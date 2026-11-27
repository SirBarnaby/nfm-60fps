# Sound

## Purpose
The game's two sounds in 1.0: the original sound exactly as it was, and the improved sound beside it.

## Requirements

### Requirement: Classic sound exactly like the original
The game SHALL keep a Classic sound that sounds exactly like the original. (Owner, 2026-11-21, R1 Q15, R2 A8.)

#### Scenario: Check: switch to Classic
- **WHEN** the player chooses the Classic sound
- **THEN** only the original sound plays, through the original's untouched sound path

### Requirement: The improved sound stays in 1.0
The improved sound SHALL be part of 1.0, beside Classic, and the player SHALL be able to choose between them. (Owner, 2026-11-21, R1 Q15, R2 A8.)

#### Scenario: Check: sound renders
- **WHEN** `test\verify-sound.ps1` runs
- **THEN** every race reports `same` against this PC's baseline

### Requirement: The improved sound is rebuilt from the game's own recordings
The improved sound SHALL be built with approach A, "Rebuilt originals": the game's own engine recordings played one engine cycle at a time, the engine and intake heard from the car's front and the exhaust from its back, and the original skid sounds played per wheel, so the front sounds more like an engine, the back more like an exhaust, and the sources sound rich. (Owner, 2026-11-23: extras brief, then picked A of three approaches.)

#### Scenario: Check: front and back
- **WHEN** a car drives past in a race with the improved sound
- **THEN** the engine and intake are heard from the car's front and the exhaust from its back, and the owner agrees after listening

#### Scenario: Check: one wheel skids
- **WHEN** one wheel locks while the other three roll
- **THEN** that wheel's skid sound plays from that wheel's position, and the rolling wheels add no skid sound

#### Scenario: Check: the owner listens first
- **WHEN** the new improved sound changes what `test\verify-sound.ps1` hears
- **THEN** its baseline is renewed only after the owner has listened and agreed

### Requirement: Engines keep their own voice under the real layers
Each car SHALL keep its original NFM engine voice, from the game's own recordings. What is laid over it SHALL come from real recordings: the turbo, the supercharger, the exhaust pops, the gear whine, the road and the suspension. (Owner, 2026-11-26: picked "Game engines plus real layers".)

#### Scenario: Check: a car with a turbo drives past
- **WHEN** a car with a turbo drives past with the improved sound
- **THEN** its engine is recognisably that car's engine from the original game, and the turbo over it is a recording, not a synthesized stand-in

### Requirement: Sound effects come from real recordings
The sound effects SHALL NOT sound synthesized or fake. Sources SHALL be found for at least some of them, and the sound system SHALL be built over these sources. A recording that is used SHALL be sampled down to the game's sample rate, so it sounds more like the game, and its license SHALL be respected (the creator and source credited and the edits named where the license asks for it). (Owner, 2026-11-26.)

#### Scenario: Check: a sound effect
- **WHEN** an extra plays a sound effect that has a recording behind it
- **THEN** what plays is that recording, sampled down to the game's sample rate, not a synthesized stand-in

#### Scenario: Check: the credits
- **WHEN** a recording under a license that asks for credit is in the game
- **THEN** its creator, source and license are listed in the game's credits file, with what was changed

### Requirement: Cars are heard in an environment
Cars SHALL NOT sound as if they drive in a void. They drive in an environment, where loud cars create echoes, reflections and reverb, the player's own car and the other cars alike. If 8 cars drive at once, each one SHALL make actual sound. (Owner, 2026-11-26.)

#### Scenario: Check: next to a wall
- **WHEN** a car drives past a wall or between buildings with the 3D sound
- **THEN** its sound comes back from them as echoes and reverb, and in the open it does not

#### Scenario: Check: eight cars
- **WHEN** eight cars race at once
- **THEN** each of the eight is heard as a sound of its own

### Requirement: Cars are heard from far off, by how loud each one is
Cars far off SHALL be audible. Cars with really strong engines SHALL be audible further out. Cars that are by definition more quiet SHALL NOT be as easily locatable with audio. (Owner, 2026-11-26.)

#### Scenario: Check: a strong and a quiet car far away
- **WHEN** a strong-engined car and a quiet car are the same long distance from the player
- **THEN** the strong one is still heard and can be placed by ear, and the quiet one is fainter or not heard

### Requirement: The engine sound has layers
The player SHALL be able to hear, per car: the suspension at times; surface noise; the engine's growl, and perhaps the radiator; a turbo or a supercharger, depending on the car; the rear of the car sounding different since it has the exhaust, and a car whose exhaust is at the side instead of the rear sounding a bit different again; rev ranges for the cars; and skid sounds that can change with the car's wheel size. (Owner, 2026-11-26.)

#### Scenario: Check: listening around one car
- **WHEN** a car drives by, then the camera goes round it
- **THEN** the engine is heard from its front, the exhaust from where its pipes are, its turbo or supercharger if it has one, the surface under its wheels, and its suspension over bumps

#### Scenario: Check: wheel size
- **WHEN** a car with big wheels and one with small wheels skid the same way
- **THEN** their skids sound different

### Requirement: Exhaust pops are heard and seen
Some cars SHALL have exhaust pops. A pop makes a sound and SHALL be visible (in HDR). (Owner, 2026-11-26.)

#### Scenario: Check: lifting off
- **WHEN** a car that pops lifts off the throttle at high revs
- **THEN** each pop is heard and a short bright flash shows at its exhaust at the same moment; a car that does not pop does neither

### Requirement: Reversing is heard
Reversing SHALL have a sound: a transmission whine. Trucks can beep as well. (Owner, 2026-11-26.)

#### Scenario: Check: reversing
- **WHEN** a car reverses, then a truck does
- **THEN** a transmission whine is heard that rises with the speed backwards, and the truck beeps as well

### Requirement: In mid-air the revs can max out
In mid-air, with the wheels off the ground, the engine's revs SHALL be able to max out. (Owner, 2026-11-26.)

#### Scenario: Check: a jump on full throttle
- **WHEN** a car leaves a ramp with the throttle held
- **THEN** its engine revs up to its limit while it is in the air, and comes back to the road's revs after it lands

### Requirement: No car is overly loud
No car's audio SHALL be overly loud beside the others', also with a speaker for each player in split screen. (Owner, 2026-11-27: "Car audio seems like some cars are overly loud. In split screen with 2 speakers, this was very noticeable.")

#### Scenario: Check: two cars, a speaker each
- **WHEN** a strong-engined car and a quiet one are each heard from their own chase camera
- **THEN** both engines play at the same level
