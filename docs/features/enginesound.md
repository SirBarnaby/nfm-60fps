# The rebuilt engine sound

Switch: **Rebuilt engine sound** (`enginesound`, cookie `xtraengine`), off by
default until 1.0, in the Extras tab's Sound group. It works with the 3D
sound only (Audio, Sound system: Steam Audio); Classic sound stays the
original's. Requirement: `openspec\specs\sound`, "The improved sound is
rebuilt from the game's own recordings" (approach A, "Rebuilt originals").

Code: the sound in `src\nfm\lit\audio\EngineGrains.java` (plain Java) and
`Sound3D` (`RebuiltVoices`, the streamed sources); the extra in
`src\nfm\lit\features\enginesound\`, with its hooks in `nfm.lit.FeatureBridge`
(`soundCar`) and `GameSparker.publishSound`.

## What changes

Nothing about which recording plays when: the 3D sound's mix of each
engine's five recordings stays (idle off the throttle or in the air, three
rising through each speed band, the scream for wheelspin and full power). How
they are played changes, for every car the switch plays:

- **One engine cycle at a time.** Each recording is a short loop of a few
  engine cycles (8000 samples a second; the idle one about 15 cycles of 48 ms,
  the rising ones about 8 of 64 to 82 ms). It is cut into its cycles once,
  the period found where the loop is most like itself (its autocorrelation,
  the shortest lag nearly as good as the best, refined between samples). A
  voice lays one cycle at each firing under a raised-cosine window that
  overlaps its neighbours (pitch-synchronous overlap-add): higher revs lay
  them closer instead of playing them faster, so the firing rate rises while
  each cycle keeps its sound. Below the recorded rate the windows widen to the
  gap, so no silence opens. The cycles are walked in order with now and then a
  step to another cycle of the loop (12% of firings), so the engine breathes
  instead of repeating a loop. Made at 16000 samples a second in 20 ms blocks,
  four queued ahead, 80 ms (six in the sound test's lockstep).
- **The front: engine and intake.** The voices' sum with its lowest end
  thinned (a high-pass at 120 Hz) and the intake's resonance (a band at
  700 Hz) louder the more the throttle is open. Played from the car's nose: a
  fifth of the way in from the front of the body, at the middle of its height.
- **The back: the exhaust.** The body below a kilohertz plus a fuller low end
  under load, its peaks rounded off as the throttle opens (a growl: the
  rounding adds harmonics). Lift the throttle high in the band (above half)
  on the ground and it pops for 0.8 s: short bursts at random, about five a
  second. Played from the car's tail, low on the body.
- **Skids per wheel.** After each tick the extra takes each wheel's slide from
  the skidmarks' sharing out of the car's slide (`SkidSlip`: the front in
  understeer, the rear in a power slide or under the handbrake, the outside
  wheels harder, a locked wheel along its mark, a wheel in the air nothing):
  silent below 4 units a tick, full at 24. Each wheel has a source at its
  tread playing the game's skid recording (the dust skid on loose ground; a
  different one of the three at each wheel) in overlapping 80 ms grains taken
  at random from its steady middle, so it lasts as long as the slide without
  a seam. It comes in over 20 ms and dies away over 120 ms, a little higher
  the harder it slides. A rolling wheel makes no skid sound, and the car's
  one-shot skid is left out.

Every source is placed by the car's pose (as the game turns its points: roll,
pitch, yaw), carried on between ticks with the car, muffled by what is in the
way (Steam Audio) and bent by Doppler as the engines are. The engine sources
take the engines' level and the crash ducking; the skids the effects' level.

Replays and highlights keep the cars' places, not their slides: they play the
rebuilt engines, with no skids.

## Switching

Turned on during a race, a car's five recordings stop and the rebuilt sound
fades in over 0.15 s; turned off, it fades out and the recordings come back.
The streamed sources stop when they have nothing to play.

## Cost

Per car heard: one to three recordings in play, each a few grains a sample,
16000 samples a second, two shaping filters, and a grain voice per sliding
wheel; windows come from a table. Everything (sources, blocks, grains,
filters) is made the first time the extra plays a car; nothing after. A car
far off plays its loudest recording only, as before. With the switch off
nothing new runs.

## Checks

`test\extras-check.ps1 enginesound` on a made-up recording (a loop of
firings, each a knock ringing at 900 and 250 Hz) and a made-up car: the loop
cut at its firings; at 1.25 times the revs the firings 1.25 times as often
while the sound of each stays (its spectrum's centre within 12%, where
playing faster would move it by a quarter); the level held; the front
brighter than the back (its spectrum's centre at least 1.5 times higher), each
at its end of the car; one locked wheel skidding alone, at that wheel's
tread, and no skids after the race. Hearing a car go by, front and back,
waits for the owner, and so does `test\verify-sound.ps1`'s baseline: it is
renewed only after the owner has listened. With the switch off, as in every
hidden run, the sound test hears what it always did.

Looked at first: Jonas Hack's granular engine audio for Unity, the per-cycle
synthesis of Jagla, Maillard and Martin, the per-cycle segment patents (US
7606374, US 8325932), FMOD on Forza 5 (intake, engine and exhaust apart) and
BeamNG's engine and exhaust layers; see the change's proposal.
