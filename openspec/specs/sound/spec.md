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
