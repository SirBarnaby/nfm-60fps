## Why

The owner, 23 Nov 2026, on the extras brief: "make it only in a cosmetic way", then "Keep those in also. Implement." `realism` holds eighteen requirements written that day: damage and heat that only change what is seen and heard (glowing brakes, a steaming radiator, a smoking engine, the exhaust's shimmer, lift-off flames and nitrous flame, ticking hot metal, a flapping bonnet, a dragging bumper, cracked and shattered glass, a wobbling bent wheel and a flat tyre's sparks, dirt, scratches, bottoming-out sparks with a thud and a scrape, debris that stays, brake lights and broken lights, heat haze, rattling loose parts) and a short sheet per car. This change builds them on the extras framework as one switch, **Damage and heat** (`realism`).

No requirement is MODIFIED or REMOVED and none is added; no feature the owner did not ask for is added. One requirement cannot be finished by this change alone: "Each car has a short sheet" needs the owner to look the suggested values over (its check says so); the sheets are written and read, and the report names it as an owner item.

## What Changes

- **`src\nfm\lit\features\realism\`**:
  - `CarSheet`, `CarSheets`: each car's engine layout, where the engine sits, its cooling, turbo or not, brake type and exhausts, read from `data\extras\car-sheets.txt` (through the game's files, so a mod can replace it), the values suggested from each car's look for the owner to correct.
  - `CarParts`: per car at race start, from its model: the body's size, wheel hubs and sizes, the windscreen and side windows, lights, bonnet, lower body and sides; the engine, radiators and exhausts placed from the sheet.
  - `CarState`: per car the temperatures (each brake, the exhaust, the engine), coolant, and the damage each effect shows (radiator, engine, bonnet, bumper, glass, each wheel, lights, dirt, scratches), all worked out from the race after each tick.
  - `RealismFeature` (switch `realism`): the effects, drawn on the car (`Canvas.onCar`), as particles, or as debris; the car's own polygons recoloured for dirt, the open bonnet's bay, lights and broken glass.
  - `Debris`: a fixed ring of pieces that stay where they fell (bumper bits, glass, clods from the skidmarks), the oldest going first.
  - `RealismSounds`: the ticking, thud, scrape and rattle, made once from a few lines of synthesis and played at the car through the 3D sound.
  - `RealismCheck`: a check per requirement on a made-up car.
- **Hooks in the game** (each a line or a few, each a check when the switch is off):
  - `ContO` knows which race car it is (`carSlot`, set by the bridge while the extra is on) and tells the bridge which car and polygon it draws.
  - `Plane.d`: the colour a car's polygon is drawn in passes through the extra (dirt, bay, lights, glass) before the fog, on the processor and in the kept scene alike; a bent wheel's polygons are drawn from tilted copies of their points, so the card's own wheel spin makes them wobble.
  - `Sound3D`: generated clips registered by name and played at a car (`extraClip`, `carSound`), never with Classic sound.
  - `RaceView`: each car's model (`CarShape`), a way to read the game's files, and the nitrous flag (filled by the powerups).
  - `Particles`: a particle can stay as debris where it lands.
  - The skidmarks read a bent wheel's wobble from the shared `CarLooks`, so its marks are wavy.
- **Data**: `data\extras\car-sheets.txt`. **README**: the extra in the Extras section, an options row. **`docs\features\realism.md`**: how each effect works; **`docs\features\car-sheets.md`**: the sheets for the owner to look over.

Libraries looked at (rule: before a new subsystem): brake glow in BeamNG mods (a texture swap), Assetto Corsa's Custom Shaders Patch "Brake Disc FX" (a shader on the disc mesh, sized from the mesh) and a GTA V script (heat and cooling rates, front and rear balance); BeamNG's fluid-simulated afterfire and Cyberpunk 2077's backfire effect files; for sound, Clatter (C#, impact and scrape synthesis from collisions), garjan (Rust, modal metal impacts and friction scraping), JASS (pure Java unit generators, 2001) and the modal synthesis papers (van den Doel). None fits this game's painter's-order scene and its kept scene on the card, or its OpenAL 3D sound, so none is used as code. Their design is followed: heat and cooling per axle, the front doing most of the braking; flames driven by the engine's own state; impacts as decaying modes and scrapes as filtered noise, made once. Sources: https://github.com/ac-custom-shaders-patch/acc-extension-config/wiki/Cars-%E2%80%93-Brake-Disc-FX, https://beamng.com/resources/fluid-simulated-afterfire.40054/, https://www.gta5-mods.com/scripts/glowing-brake-discs, https://alters-mit.github.io/clatter, https://github.com/MacCracken/garjan, https://www.cs.ubc.ca/labs/lci/papers/docs2001/kvdoel-icad01.pdf.

## Capabilities

### New Capabilities
<!-- none: the requirements exist in realism -->

### Modified Capabilities
<!-- none -->

Specs touched (from `openspec list --specs`): none changed. Read: `realism` (all eighteen), `effects` (the skidmarks a bent wheel makes wavy), `powerups` (nitrous's blue flame), `sound` (Classic exact, extras through the 3D sound), `code-health`, `release-1-0`, `settings`, `performance`, `picture`, `done-and-testing`.

## Impact

- New files in `src\nfm\lit\features\realism\`, `features\CarLooks.java`, `data\extras\car-sheets.txt`, `docs\features\realism.md`, `docs\features\car-sheets.md`.
- Hooks: `ContO` (a field, three lines), `Plane` (the colour, the wheel's points), `Sound3D` (two calls), `FeatureBridge` (the model, the slot, the files), `RaceView`, `Particles`, `SkidmarksFeature` (the wobble).
- With the switch off nothing changes: verify-races, golden frames, perf and sound as before. With it on, races are unchanged (`verify-races -Features realism -NoGolden`).
