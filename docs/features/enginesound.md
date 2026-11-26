# The rebuilt engine sound

Switch: **Rebuilt engine sound** (`enginesound`, cookie `xtraengine`), off by
default until 1.0, in the Extras tab's Sound group. It works with the 3D
sound only (Audio, Sound system: Steam Audio); Classic sound stays the
original's. Requirements: `openspec\specs\sound`, "The improved sound is
rebuilt from the game's own recordings" (approach A, "Rebuilt originals"),
"Engines keep their own voice under the real layers", "The engine sound has
layers", "Reversing is heard", "In mid-air the revs can max out" and "Cars
are heard from far off, by how loud each one is".

Code: the sound in `src\nfm\lit\audio\EngineGrains.java` and
`EngineLayers.java` (plain Java) and `Sound3D` (`RebuiltVoices`, the streamed
sources and the layers' looping ones); the extra in
`src\nfm\lit\features\enginesound\`, with its hooks in `nfm.lit.FeatureBridge`
(`soundCar`) and `GameSparker.publishSound`. The layers' recordings are in
`src\nfm\lit\features\sounds\` (`sounds\README.md` says where each came
from).

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
  rounding adds harmonics). Played from where the car's sheet says its
  exhaust ends: the tail, low on the body; with `pipes=side` its left side,
  low, a little behind the middle; with stacks the top of the body behind
  the middle. Exhaust pops are mixed into it (below).
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

## The layers over the engine

The engine stays the game's own. What is laid over it is recorded, each a
loop that follows the car (`EngineLayers` reckons its level and how it is
bent after every tick's news of the car; `Sound3D` plays it), and each car's
sheet (`car-sheets.md`) says what it is fitted with:

- **A turbo** (`turbo yes`): a whistle from the front that takes a moment to
  spin up under throttle (0.45 s), rises in pitch as it does, and falls away
  off the throttle. Lifting off with the turbo spinning (over 55%) lets its
  air go: a blow-off, louder the faster it span.
- **A supercharger** (`charger=blower`): driven by the engine, so its whine
  is there at once, follows the revs up and down, and howls under load. No
  blow-off.
- **Reverse**: a straight-cut gear whine, higher the faster the car backs.
  **A truck** (`beeps=yes`) beeps as well.
- **The tyres rolling**: road noise by the car's speed, from under the car:
  one recording on a hard road, another on loose ground, shared out by how
  many wheels are on each. Silent in the air.
- **The radiator fan** of a car with a front radiator, heard when it stands
  or creeps (under a quarter of its top speed).
- **The suspension**: a creak from the car as it comes down after four ticks
  or more in the air, louder after a longer flight, not again within ten
  ticks, one of three recordings.
- **Exhaust pops** come from the Damage and heat extra, so that each is seen
  as it is heard (`realism.md`): recorded pops, and a backfire for a charged
  engine now and then, mixed into the exhaust's stream so they come from the
  pipes. The sound makes none on a count of its own.

And three things the sheet sets for the whole car:

- **How loud it is** (`loud=`, 0.5 to 1.6): its engine's level, and the
  distance within which it is at full level (15 m times `loud`), so a strong
  engine is still heard and placed from far off where a quiet car has faded.
  A car with `loud=1.5` is 7 dB over a usual one at any distance beyond that;
  one with `loud=0.6` is 9 dB under.
- **Its rev range** (`revs=`, semitones its recordings bend across a third of
  a speed band, 4 for most): a screamer climbs further through a gear, a
  truck less.
- **Its wheels' size** bends its skids: a wheel of the usual size (28 units)
  skids as recorded, one twice as big about a fifth lower, a small one
  higher (`(28 / radius) ^ 0.35`, kept between 0.75 and 1.25).

**In the air with the throttle held** nothing holds the engine back: over a
third of a second the mix runs up from wherever the revs were to the engine's
top recording at the top of its bend, the limiter, and comes back within a
few tenths of a second of landing. A turbo or supercharger follows.

## Switching

Turned on during a race, a car's five recordings stop and the rebuilt sound
fades in over 0.15 s; turned off, it fades out and the recordings come back.
The streamed sources stop when they have nothing to play.

## Cost

Per car heard: one to three recordings in play, each a few grains a sample,
16000 samples a second, two shaping filters, and a grain voice per sliding
wheel; windows come from a table. Everything (sources, blocks, grains,
filters) is made the first time the extra plays a car; nothing after. A car
far off plays its loudest recording only, as before. A layer is one looping
source, made the first time that car needs it and paused while silent: two
or three play per car at most times (a charger, the tyres, sometimes the
fan). With the switch off nothing new runs.

## Checks

`test\extras-check.ps1 enginesound` on a made-up recording (a loop of
firings, each a knock ringing at 250, 900 and 2600 Hz) and a made-up car: the
loop cut at its firings; at 1.25 times the revs the firings 1.25 times as
often (the lag at which the played sound is most like itself, looked for
around the firing) while the sound of each stays (its spectrum's centre
within 12%, where playing faster would move it by a quarter); the level held;
the front brighter than the back (its spectrum's centre at least 1.5 times
higher), each at its end of the car; one locked wheel skidding alone, at that
wheel's tread, and no skids after the race. The layers, on a made-up car and
without a sound device: a turbo slow to spool, bent upwards as it does and
blowing off on a lift; a supercharger there within 0.3 s and following the
revs, with no blow-off; a car with neither silent; reverse whining higher
with speed, a truck beeping and a car not, nothing forwards; the tyres on the
road, on loose ground and half on each, silent in the air; the revs running
up in the air with the throttle held and back after landing; a sheet's
traits fitting a car out; big wheels skidding lower; the exhaust heard from
the tail, the side or the stacks; a creak after a jump, none after a bump,
none twice at once, none from a wreck. `test\extras-check.ps1 sounds` checks
the recordings themselves (`sounds\README.md`). Hearing a car go by waits
for the owner, and so does `test\verify-sound.ps1`'s baseline: it is renewed
only after the owner has listened. With the switch off, as in every hidden
run, the sound test hears what it always did.

Two of these checks failed from the day they were written until 26 Nov 2026,
and it was the measures that were wrong, not the sound: the firing rate was
read with the recording's cycle finder, which on a played sound took the
whole loop's length (719 samples for 103), and the made-up recording had
nothing above 900 Hz for the front to be brighter with.

Looked at first: Jonas Hack's granular engine audio for Unity, the per-cycle
synthesis of Jagla, Maillard and Martin, the per-cycle segment patents (US
7606374, US 8325932), FMOD on Forza 5 (intake, engine and exhaust apart) and
BeamNG's engine and exhaust layers; see the change's proposal.
