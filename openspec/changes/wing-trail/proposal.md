## Why

The owner, 23 Nov 2026, on the extras brief: "Keep those in also. Implement." `effects` gained two requirements for the Radical One that day: a slight trail from its wing while it is in the air and the player steers it there ("The Radical One's wing trail"), looking like thin white vapour from both wing tips that spreads and fades within about a second, fading when the keys are let go and quickly on landing ("The wing trail looks like vapour"). This change builds them on the extras framework (`feature-framework`).

No requirement is MODIFIED or REMOVED and none is added; no feature the owner did not ask for is added.

## What Changes

- **`src\nfm\lit\features\wingtrail\`**:
  - `WingTips`: which cars trail vapour (the Radical One, car 14) and where its wing tips are, in its model file's numbers (`radicalone.rad`, `p(±70,-55,-135)`), scaled as the game scales the model (found from the front wheel's place).
  - `WingTrailFeature` (switch `wingtrail`): per car a level that rises while the car has been off the ground for 0.15 s and its driver holds a steering, throttle or brake key, falls when the keys are let go and falls fast on landing; per wing tip a ring of 20 points left in the air, one a tick, each drifting out from the car's line like a vortex and keeping a little of the car's way; drawn as a ribbon facing the camera, thin at the tip and spreading, fading to nothing in a second.
  - `WingTrailCheck`: the requirements' checks on a made-up Radical One (steering in the air; on the ground, in the air without keys, another car, before 0.15 s: nothing; both tips; thin, white, spreading, fading; the newest stretch moving with the car; fading on letting go and landing).
- **`Features.ALL`** and **`ExtrasCheck`** gain the feature (its switch, settings row and console command came with the framework).
- **README**: the extra in the Extras section, an options row. **`docs\features\wingtrail.md`**: how it works.

Libraries looked at (rule: before a new subsystem): the Kerbal Space Program "Wingtip Vortex" mod (procedural tube meshes and trails, vortex strength from lift; CC BY-NC-SA, and tied to Unity), a Modrinth game mod with white streaks off the wing edges above a speed threshold, jMonkeyEngine 2's `TrailMesh` and jME1's `Ribbon` (camera-facing strips of the last positions; not in jME3, and jMonkeyEngine is not used here), and Babylon.js and Urho3D's camera-facing ribbon trails. None draws into this game's painter's-order scene or its kept scene on the card, so none is used as code; their shared design (a short history of positions per emitter, drawn as a strip facing the camera, width and opacity by age) is followed. Sources: https://spacedock.info/mod/4209/Wingtip%20Vortex%20Mod, https://modrinth.com/project/MuzFaOWv, https://hub.jmonkeyengine.org/t/ribbon/7820, https://babylonjs.medium.com/trail-effect-with-babylon-js-786053389fdc.

## Capabilities

### New Capabilities
<!-- none: the requirements exist in effects -->

### Modified Capabilities
<!-- none -->

Specs touched (from `openspec list --specs`): none changed. Read: `effects` (the two wing-trail requirements), `code-health` (own folder, look features leave races alone), `release-1-0` (off until 1.0), `settings`, `performance`, `picture`, `done-and-testing`.

## Impact

- New files in `src\nfm\lit\features\wingtrail\`, `docs\features\wingtrail.md`.
- Small edits: `Features.ALL` (one line), `ExtrasCheck` (one call), README.
- With the switch off nothing changes: verify-races, golden frames, perf and sound as before. With it on, races are unchanged (`verify-races -Features wingtrail -NoGolden`).
