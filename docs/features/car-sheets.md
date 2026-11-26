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
  pod after a hard side hit, `air` never steams. A front radiator's fan is
  heard when the car stands (the rebuilt engine sound).
- **Turbo**: longer, longer-lasting lift-off flames, exhaust pops with them,
  and in the rebuilt engine sound a turbo's whistle (or a supercharger's
  whine, with `charger=blower`).
- **Brakes**: drums heat 1.3 times as fast and cool 0.7 times as fast as
  discs; carbon heats 0.85 times and cools 1.3 times.
- **Exhausts**: 1 or 2 pipes at the back (flames and shimmer point back), or
  stacks behind the cab (they point up).

A car with no line (a mod's own car) gets a front-engined V8 with a radiator,
discs and two pipes.

## Traits

After the eight columns a line can carry traits, `name=value`, for what only
some cars have. `CarSheet.is`, `trait`, `number` and `pops` read them; a
trait a car does not name is its fallback.

| Trait | What it does | Cars |
| --- | --- | --- |
| `spikes=yes` | A metal glint, a ring and sparks whenever the car rams another | Sword of Justice |
| `charger=blower` | Its "turbo yes" is a supercharger: a whine that follows the revs. Without it, a turbo: a whistle that spools up and blows off | MAX Revenge, Lead Oxide, DR Monstaa |
| `pipes=side` | The exhaust is heard from the car's side, not its tail | MAX Revenge, Lead Oxide |
| `beeps=yes` | A truck: it beeps while it reverses | EL KING, M A S H E E N, DR Monstaa |
| `pops=yes` or `no` | Its exhaust pops when the throttle lifts at high revs, each pop a sound and a flash. The fallback is its turbo column | yes: Formula 7, La Vite Crab, High Rider, Mighty Eight |
| `loud=0.5` to `1.6` | How loud its engine is and how far it carries (1 for most) | 0.6 Nimi to 1.5 M A S H E E N and DR Monstaa |
| `revs=2` to `7` | How far its engine's pitch climbs through a gear, semitones a third of the gear (4 for most) | 2.5 the trucks to 6 Formula 7 and Radical One |

The engine sound's traits are read by the rebuilt engine sound
(`enginesound.md`), `pops` by Damage and heat. All of them are Claude's
suggestions too.
