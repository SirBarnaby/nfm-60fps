# custom-game Specification

## Purpose

Lets the player customise a race: how many computer cars race, up to 32, and what cars they have, as flexibly as possible.

## Requirements

### Requirement: The game can be customised
A custom game SHALL let the player choose how many computer cars race and which cars they drive. (Owner, 2026-11-23: "Allow game to be customized. Amount of AI + what cars ai have.")

#### Scenario: Check: the custom game's page
- **WHEN** Custom Game is chosen on the main menu (the hidden run `-Dnfm.autotest.custom=12,novice` chooses it and races)
- **THEN** its page sets the computer cars and their cars, and the race after it has that many computer cars driving those cars (the run's log: `custom race: 13 cars, Novice`)

### Requirement: Up to 32 computer cars, chosen flexibly
A custom game SHALL allow any number of computer cars from none up to 32, chosen as freely as possible. (Owner, 2026-11-23: "This feature should be very flexible. We should have up to 32 enemies.")

#### Scenario: Check: a full field
- **WHEN** `test\live.ps1 -Custom 32,pro` runs on the owner's PC
- **THEN** its race has 33 cars (the log: `custom race: 33 cars`), races into the post-race highlight, comes back to the custom game's page and ends with `custom test done`

#### Scenario: Check: any count
- **WHEN** the Computer cars row on the custom game's page is stepped through
- **THEN** it offers every count from none (a practice race) to 32, and the Custom lineup's Pick cars tab takes any mix of the 16 cars up to 32 in all

### Requirement: Presets for the computer cars' cars
The computer cars' cars SHALL be chosen by one of four lineups: Default; Novice, the cars up to Drifter X; Pro, pro cars only, starting from DR Monstaa and going down from there; Custom, where the player picks the opponents. (Owner, 2026-11-23: "We should have 3 presets for ai cars. 1.) Default 2.) Novice (up until drifter x) 3.) Pro (Fill the race with pro cars only, starting from dr. monstaa, and going down from there) 4.) Custom. Allows picking our enemies.")

#### Scenario: Check: the cars each lineup brings
- **WHEN** hidden custom runs race with the Novice, Pro and Custom lineups
- **THEN** the cars in each run's log are: Novice, only Tornado Shark to Drifter X (cars 0 to 8); Pro, DR Monstaa (15) at the front, then 14, 13 and on down, only cars after Drifter X; Custom, exactly the cars picked on the Pick cars tab

### Requirement: Staggered starts above 9 computer cars
With more than 9 computer cars the start SHALL be staggered: the computer cars start in groups of 6, one group after another. With 9 or fewer, every car starts at once. (Owner, 2026-11-23: "This might need starts to be more staggered. So groups of 6 AI start at once. But this feature only starts at like >9 AI.")

#### Scenario: Check: groups at the start
- **WHEN** hidden custom runs race with 9 and with 32 computer cars
- **THEN** the 9-car race logs `all at once`; the 32-car race logs 6 groups, and at each group's start only the groups gone so far are moving

### Requirement: The player always starts last
The player SHALL always start in last place. (Owner, 2026-11-23: "We will always keep our last place")

#### Scenario: Check: the grid
- **WHEN** any hidden custom run's race starts
- **THEN** the player's car stands behind every computer car (the log: `player starts last`), and in a staggered start the player goes with the last group

### Requirement: Launches at most 2 seconds apart
In a staggered start, each group SHALL launch at most 2 seconds after the group before it. (Owner, 2026-11-23: "launches should be max 2secs apart.")

#### Scenario: Check: launch times
- **WHEN** a hidden custom run races with 32 computer cars
- **THEN** the group starts in its log are at most 38 ticks apart (2 seconds of 53 ms ticks)
