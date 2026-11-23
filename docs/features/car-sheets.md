# The car sheets

Requirement: `openspec\specs\realism`, "Each car has a short sheet". The
values are Claude's suggestions from each car's look; the owner corrects them.
They live in `src\nfm\lit\features\realism\car-sheets.txt`, next to the extra's
code, and go into `madness.jar` with it: read as a class-path resource, not
through the game's files, so no mod replaces them. When they are right, change the file's line
`# Looked over by the owner: no` to `yes`: until then `test\extras-check.ps1`
reports the sheets as waiting for the owner.

| Car | Model | Engine | Sits | Cooling | Turbo | Brakes | Exhausts |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Tornado Shark | `2000tornados` | V6 | front | front | no | disc | 2 |
| Formula 7 | `formula7` | V10 | mid | sides | no | carbon | 1 |
| Wow Caninaro | `canyenaro` | V8 | front | front | no | disc | 2 |
| La Vite Crab | `lescrab` | V12 | mid | sides | no | disc | 2 |
| Nimi | `nimi` | I4 | front | front | no | drum | 1 |
| MAX Revenge | `maxrevenge` | V8 | front | front | yes | disc | 2 |
| Lead Oxide | `leadoxide` | V8 | front | front | yes | drum | 2 |
| Kool Kat | `koolkat` | V8 | front | front | no | drum | 2 |
| Drifter X | `drifter` | I6 | front | front | yes | disc | 1 |
| Sword of Justice | `policecops` | V8 | front | front | no | disc | 2 |
| High Rider | `mustang` | V8 | front | front | no | disc | 2 |
| EL KING | `king` | V8 | front | front | yes | disc | stacks |
| Mighty Eight | `audir8` | V8 | mid | sides | no | carbon | 2 |
| M A S H E E N | `masheen` | V8 | rear | air | yes | disc | stacks |
| Radical One | `radicalone` | V12 | mid | sides | yes | carbon | 2 |
| DR Monstaa | `drmonster` | V8 | front | front | yes | disc | stacks |

## What each column changes

- **Engine**: the layout (I4 to V12, flat, rotary); kept for the rebuilt
  engine sound, which reads it.
- **Sits**: where smoke comes from, and which end's rams hurt the engine
  (the front for a front engine, the back for a mid or rear one).
- **Cooling**: `front` steams from a crushed front corner, `sides` from a side
  pod after a hard side hit, `air` never steams.
- **Turbo**: longer, longer-lasting lift-off flames.
- **Brakes**: drums heat 1.3 times as fast and cool 0.7 times as fast as
  discs; carbon heats 0.85 times and cools 1.3 times.
- **Exhausts**: 1 or 2 pipes at the back (flames and shimmer point back), or
  stacks behind the cab (they point up).

A car with no line (a mod's own car) gets a front-engined V8 with a radiator,
discs and two pipes.
