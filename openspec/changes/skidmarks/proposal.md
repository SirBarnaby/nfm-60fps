## Why

The owner, 23 Nov 2026, on the extras brief: "Keep those in also. Implement." `effects` gained the skidmark requirements that day: marks made per wheel, the maneuver table, the surface's colour carried onto the road, particles, and a toggle for last race's skid zones. This change builds them on the extras framework (`feature-framework`).

No requirement is MODIFIED or REMOVED and none is added; no feature the owner did not ask for is added.

## What Changes

- **`src\nfm\lit\features\skidmarks\`**:
  - `SkidSlip`: per wheel, whether it slides, how (locked, sideways, both, spinning) and how much, from the copy of the race after each tick. The game's single slide is shared over the four wheels the way a real car's would be (understeer on the fronts, a power slide and the handbrake on the rear, the outside wheels loaded, the turn swinging the rear and a landing car's wheels, countersteering straightening the fronts).
  - `MarkRing`: a fixed ring of 4,096 mark pieces, a strip per wheel, a spot that darkens for a wheel sliding on the spot, streaks and striations for the full detail.
  - `SkidmarksFeature` (switch `skidmarks`, detail `skiddetail`): starts, continues and ends each wheel's mark, its shade by kind, the mud a tyre carries onto the road, scrubs after a hard hit from the side, a metal scrape and sparks from a body on the road, smoke, dust and clods from the shared particle pool.
  - `SkidZonesFeature` (switch `skidzones`): a coarse grid of how much sliding happened where, kept per stage in `cache\skid-heat` when a race ends (never in test runs), shown faintly darker on the road in the next race there.
  - `SkidmarkCheck`, run by the new `test\extras-check.ps1` through `nfm.lit.features.ExtrasCheck`: each maneuver in the owner's table played on a made-up car, the marks measured.
- **`RaceView`** gains each wheel's ground colour (the piece's colour, or the stage's ground) and whether it is on a ramp; `Particles` gains read access for the checks.
- **README**: the two extras in the Extras section, three option rows. **`docs\features\skidmarks.md`**: how it works.

Libraries looked at (rule: before a new subsystem): Nition's UnitySkidmarks (C#, Unity; a shared mesh of quads, each wheel passing its last mark's index, zero garbage), Highroad Engine's SkidmarksManager (the same, with a shared cap), Rigs of Rods' `Skidmark` (fixed segment buckets per wheel). All are tied to their engines' meshes and wheel colliders, and none draws into this game's painter's-order scene or its kept scene on the card, so none is used as code; their shared design (one fixed ring of quads, a last index per wheel, the oldest replaced, nothing allocated) is followed. No Java skidmark library was found. Sources: https://github.com/Nition/UnitySkidmarks, https://highroad-engine-docs.moremountains.com/API/class_more_mountains_1_1_highroad_engine_1_1_skidmarks_manager.html, https://developer.rigsofrods.org/d5/d70/class_ro_r_1_1_skidmark.html.

## Capabilities

### New Capabilities
<!-- none: the requirements exist in effects -->

### Modified Capabilities
<!-- none -->

Specs touched (from `openspec list --specs`): none changed. Read: `effects` (the five skidmark requirements), `code-health` (own folder, look features leave races alone), `release-1-0` (off until 1.0), `settings`, `performance`, `picture`, `done-and-testing`.

## Impact

- New files in `src\nfm\lit\features\skidmarks\`, `features\ExtrasCheck.java`, `features\CheckCanvas.java`, `test\extras-check.ps1`, `docs\features\skidmarks.md`.
- Small edits: `Features.ALL` (two lines), `RaceView`, `FeatureBridge` (two fields filled), `Particles`, README.
- With the switches off nothing changes: verify-races, golden frames, perf and sound as before. With them on, races are unchanged (`verify-races -Features skidmarks,skidzones -NoGolden`).
- Writes `cache\skid-heat\*.bin` (a few KB per stage) only with the zones on, in the player's game.
