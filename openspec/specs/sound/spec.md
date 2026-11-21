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
