## Why

The owner, 23 Nov 2026, on the extras brief, then picked approach A of three, "Rebuilt originals", and "Keep those in also. Implement." `sound` holds the requirement written that day: the improved sound built from the game's own engine recordings played one engine cycle at a time, the engine and intake heard from the car's front and the exhaust from its back, and the original skid sounds played per wheel, so the front sounds more like an engine, the back more like an exhaust, and the sources sound rich. This change builds it on the extras framework as one switch, **Rebuilt engine sound** (`enginesound`), which the framework already lists for the Extras tab once a feature registers it.

No requirement is MODIFIED or REMOVED and none is added. Two scenarios end with the owner: "front and back" ("the owner agrees after listening") and "the owner listens first" (the sound check's baseline is renewed only after the owner has listened). With the switch off, as it is by default and in every hidden run, nothing changes, so the baseline stands; the check reports the listening as waiting for the owner, and the report names it as an owner item. From 16:07 UTC on 23 Nov the owner tests by hand: this change runs no test and lands when it compiles and reads right.

## What Changes

- **`src\nfm\lit\audio\EngineGrains.java`** (the synthesis, plain Java, no OpenAL): a recording cut into its engine cycles (the period found by autocorrelation); a voice that plays a recording one cycle at a time, each cycle a grain laid where the next firing falls (pitch-synchronous overlap-add), so the revs change without the recording's tone sliding up and down with them; the front's tone (the engine and the intake: the low end thinned, the intake's resonance louder under load) and the back's (the exhaust: the body below a kilohertz, a soft growl under load, pops and crackles when the throttle lifts at high revs); a skid voice that plays the original skid recordings in short overlapping grains from their steady middle, endless and without a seam.
- **`Sound3D`**: a car the extra plays (`Car.rebuilt`) is heard from two streamed sources, the front and the back, placed at the car's nose and tail through its pose and carried on between ticks as the engines are, and from a streamed source at each wheel while it skids; its five looping recordings fall silent and its one-shot skids are left out. The mix of the recordings (which plays at which speed, throttle, air and wheelspin) is the 3D sound's own; only how they are played changes. A car the extra does not play is exactly as before.
- **`src\nfm\lit\features\enginesound\`**: `EngineSound` (switch `enginesound`, the sound kind): when a race starts, each car's front, back and wheels from its model, handed to the sound; after each tick, how hard each wheel skids and on what (from the skidmarks' per-wheel slide, made public for it); `EngineCheck`: a check per scenario.
- **Hooks**: `GameSparker.publishSound` fills the car's pose and, through `FeatureBridge`, whether the extra plays it and its wheels' skids; `SkidSlip` becomes public.
- **README**: the extra in the Extras section and an options row. **`docs\features\enginesound.md`**: how it works.

Libraries and approaches looked at (rule: before a new subsystem): Jonas Hack's granular engine audio for Unity (an accelerating recording traversed by RPM in grains; https://awesome.ecosyste.ms/projects/github.com%2Fjonas-hack%2Fgranular-synthesis-for-engine-audio), the per-cycle method of Jagla, Maillard and Martin (each sample one engine cycle plus the overlap for smooth joins; https://hal.archives-ouvertes.fr/hal-00730614/file/JAGLA_MS12-11754_Final.pdf), the per-cycle segment patents (US 7606374, US 8325932), FMOD's account of Forza 5 (intake, engine and exhaust modelled apart, each reacting to the car in its own way; https://www.mcvuk.com/development-news/driven-by-sound-how-fmod-set-the-next-gen-benchmark-in-forza-5/) and BeamNG's engine and exhaust layers (https://documentation.beamng.com/modding/vehicle/sections/sounds/engine_audio/). None fits as code: the game's recordings are short loops of a few cycles, not a run from idle to the limiter, and the mixing runs in OpenAL Soft. Their design is followed: one cycle at a time, grains laid at the firing rate, intake and exhaust apart.

## Capabilities

### New Capabilities
<!-- none: the requirement exists in sound -->

### Modified Capabilities
<!-- none -->

Specs touched: none changed. Read: `sound` (all three), `settings`, `performance`, `code-health`, `release-1-0`, `done-and-testing`.

## Impact

- New files: `src\nfm\lit\audio\EngineGrains.java`, `src\nfm\lit\features\enginesound\`, `docs\features\enginesound.md`.
- Hooks: `Sound3D` (the rebuilt voices beside the five recordings), `GameSparker.publishSound`, `FeatureBridge`, `Features`, `ExtrasCheck`, `SkidSlip` (public).
- With the switch off nothing changes: Classic sound untouched, the 3D sound's engines as before, `verify-sound` as before. The race is never read from the sound thread and never written.
