## Why

The owner, 23 Nov 2026, on the extras brief: "Keep those in also. Implement." The specs gained 35 requirements that day (commits d8f49d9 and 201142a): skidmarks, the Radical One's wing trail, cosmetic damage and heat, a helmet cam, powerups, a rebuilt engine sound and arcade suspension. `code-health` "Each feature lives in its own folder" asks for them to live in `src\nfm\lit\features\<feature>\` behind one small `Feature` interface, one `Features` list and one switch each (`FeatureSettings`), with only one-line hooks in the big files. This change builds that frame; the features themselves come in the changes after it, each on its own.

No requirement is MODIFIED or REMOVED and none is added: the requirements are already in `openspec\specs`. No feature the owner did not ask for is added.

## What Changes

- **`src\nfm\lit\features\`**, a package of its own:
  - `Feature`: an id, whether it changes races, and calls at the start of a race, after each real race tick (with a read-only view of the race), when the race ends, and for each frame drawn (to queue what it draws, then to draw it).
  - `Features`: the list of features the game knows, which of them are on, and the calls the game makes into them. With every feature off each call is one check of a boolean.
  - `FeatureSettings`: one switch per feature (id, label, help line, cookie, property, values, default), read and saved as every other setting is.
  - `RaceView`: what a feature may read of the race after a tick (each car's place, angles, speed, damage, inputs, its wheels' places, contact, surface and slip, and the hits of the tick), filled from the race by a bridge in `nfm.lit`. The race's own classes stay package-private, so a feature cannot reach them: look and sound features can only read the race.
  - `Canvas`: how a feature draws: polygons fixed in the world, moving through it (particles) or on a car, a colour with the stage's fog, and see-through by dithering.
  - `DrawQueue`: where in the game's back-to-front drawing a feature's polygons go: on the road (after the flat road pieces), at a depth among the objects, or right after a car.
- **Hooks in the existing files, one line each**: the switches loaded and saved with the other settings, a race starting, each real race tick ending, the scene's drawing (`renderScene`, `drawWorldObjects`), and the hits in `Madness.regx/regy/regz`.
- **Drawing on the graphics card**:
  - A new kind of kept point, `MOVING` (a point and how far it moves in a tick), placed for each frame in both `RetainedScene.placeOne` and `Shaders.PROJECTED_VS`, so particles move smoothly between ticks on the card as on the processor.
  - A new scene command, `SceneList.DITHER`: the polygons after it cover only a share of their pixels, in a 4x4 ordered pattern (the fragment shader drops the rest), so see-through smoke and fading marks stay in the opaque kept scene. The white start flash moves to a blend state with which its pixels come out exactly as before.
- **Settings**: a new **Extras** tab in the in-game settings, with one row per feature saying what it does and that until 1.0 it is off by default because it changes the look (or the sound, or the races); a row appears with its feature. The tabs share the box's width when there are six.
- **Developer console**: `extras` lists the switches; `extras <id> <value>` sets one.
- **`-Dnfm.features=<looks, races, all or ids>`** turns features on for one run, hidden runs included; each switch also has its own `-Dnfm.<id>` property.
- **`test\verify-races.ps1 -Features <list>`** plays the recorded races with those features on, to prove that look and sound features leave races alone (`code-health`, "look and sound features leave races alone").
- **README**: an Extras section and a row per switch in the options table.

Libraries looked at (rule: before a new subsystem):
- Feature switches: Togglz, FF4J and Unleash are made for servers (web consoles, stores, rollout strategies) and would add megabytes for ten booleans; the game's own settings pattern (property, cookie, settings row, console command) does what is needed and keeps every setting alike. Not used.
- Particles and decals: no Java library draws into this game's painter's-order scene and its kept-scene card path; LibGDX's `ParticleEffect` and jMonkeyEngine's particle emitter need their own scene graph and renderer. The pooled-particle and decal-ring designs they and Unity's skidmark scripts use (one fixed buffer, the oldest piece replaced) are followed. Not used as code.

## Capabilities

### New Capabilities
<!-- none: the requirements exist (code-health "Each feature lives in its own folder") -->

### Modified Capabilities
<!-- none -->

Specs touched (from `openspec list --specs`): none changed. Read: `code-health` (the folder, the switches, the two checks), `release-1-0` (off by default until 1.0), `settings` (the settings pattern), `performance`, `picture`, `done-and-testing`.

## Impact

- New: `src\nfm\lit\features\Feature.java`, `Features.java`, `FeatureSettings.java`, `RaceView.java`, `Canvas.java`, `DrawQueue.java`, `Dice.java`; `src\nfm\lit\FeatureBridge.java`, `FeatureCanvas.java`.
- `src\nfm\lit\GameSparker.java`: one-line hooks (settings, race start, tick end, scene drawing, run-ahead pose).
- `src\nfm\lit\Madness.java`: one line in each of `regx`, `regy`, `regz` (the hit, for the view; never on speculative ticks).
- `src\nfm\lit\ContO.java`: `flat()` (a flat road piece, `grounded` of 10 or more).
- `src\nfm\lit\Precise.java`, `RetainedScene.java`, `SceneRecorder.java`, `gpu\SceneList.java`, `gpu\SceneGeometry.java`, `gpu\Shaders.java`, `gpu\GpuDisplay.java`: the `MOVING` point and the `DITHER` command.
- `src\nfm\lit\SettingsScreen.java`: the Extras tab, tab spacing; `src\nfm\lit\DevTool.java`: `extras`.
- `test\verify-races.ps1`: `-Features`.
- `README.md`, `docs\features\README.md`.
- With every feature off (the default, and every hidden run that does not ask): no race, golden frame or sound changes.
