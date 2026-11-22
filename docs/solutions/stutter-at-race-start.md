# Stutter at the race's start: what Java and the card do behind the game's back

*22 Nov 2026, from the live run that flagged a 251 ms freeze about a second into the race, a hitch as the countdown started, uneven races and a countdown under 250 fps with no limit (the change `stutter-free-races`).*

## The lessons

### 1. `Field.get` and `Field.set` change cost after 127 uses

Since Java 18 a reflected field is read and written through a method handle of its own. After 127 calls Java "customizes" that handle: it spins a class for it and compiles it. Code that reads many fields the same number of times (a state copy, a hash over a state, a HUD swap) makes them all reach 127 in the same tick, and the spin of a few thousand classes stands the game still.

- Seen as: one run-ahead tick of 184-248 ms about 1.1-1.5 s into the first race, and nothing like it after. The sampler shows method-handle code being generated and classes defined under `Field.get`/`Field.set`.
- Fix: `FieldSlot` (a `VarHandle` per field, called with the field's exact type: `(int) handle.get(o)`). Java links an exact-type call straight to the field and never customizes it. Do not call a `VarHandle` through `Object` or with boxed values: that is the generic path again.
- Look for it wherever the game reads fields by name every tick or frame: `StateCopy`, `RaceSnapshot`, `RaceState` (replays), `PlayerHud` (split screen).

### 2. `System.gc()` under ZGC holds the caller for a whole collection

With G1 or the serial collector `System.gc()` is a pause everyone expects. With ZGC the collection itself is concurrent, but the caller still waits until it is done: 61-157 ms here. The original calls it on Enter at the stage screen and while loading music.

- Seen as: a gap on the game thread with no stack of its own (`System.gc` under `musicomp` or `RadicalMod`), right before the countdown's first tick.
- Fix: `-XX:+DisableExplicitGC` in the runtime's options (`build.ps1`). ZGC collects on its own under the 512 MB soft limit.

### 3. The first race runs cold code, and the countdown does not warm it

The countdown ticks the race with the cars held: the physics, the AI and the collisions do not run until the start. Java interprets them, then compiles them in steps, so the first two seconds of the first race took 1.5-2.5 times as long a tick.

- Fix: play the race's next tick ahead and put the race back (the run-ahead's own `playAndPutBack`) before the race, unseen: while the music loads (the screen holds still then anyway, up to 200 ms) and in spare time between ticks. Never block a frame for it: a block of 96 ticks gave smooth races and a 1.3 s frame on the stage screen.
- The rollback self-test (`verify-races.ps1 -Rollback`) checks every warm-up tick leaves the race exactly as it found it.
- The race state is complete only from `SELECTEDCARSAVE` on: a warm-up tick earlier (at `loadstage` or `STAGESELECT`) plays a half-made race.

### 4. A cosmetic once-a-race effect can cost a whole software frame

The countdown portrait's tint (`blendude`) reads the picture behind it. With the GPU renderer that meant drawing the whole frame again in software first. Clip the redraw to what is read, skip what the clip cannot reach, and decode images ahead of time.

### 5. A big method compiled before a class it uses is initialized gets thrown away later

C2 cannot compile a use of a class that is not initialized yet; it puts a trap there. When the trap is reached, the whole compiled method is discarded ("made not entrant: uncommon trap") and runs slowly until it is compiled again. For `Plane.d` (7.6 KB, every polygon) that was 50-100 ms a tick for two or three ticks, about 1.7 s into the race, at the first chip of debris. It happened only when C2 finished `Plane.d` in the menus, before the countdown's first scene initialized `EffectTrails`: about half the runs.

- Seen as: a slow `Plane.d` in every phase of a few ticks (race step, run-ahead, kept scene), and in the JIT log `uninitialized reinterpret` with a `trap_bci`. `javap -c -p` on the class names the bytecode at that index.
- Fix: initialize such classes with the class whose hot method uses them (`Plane`'s static initializer). `-XX:PerMethodTrapLimit=0` does not cover this kind of trap.
- An intermittent stall at the same moment of the same seed is a timing race between Java's compiler threads and the game: log `-Xlog:jit+compilation=debug,deoptimization=debug` and compare a run with the stall against one without.

### 6. A part with light ticks and a low frame rate: count the draw calls

The countdown was the slowest part in every run (216 fps with no limit) though its ticks were lighter than the race's. The presenter, not the game thread, was slow: handing the card a frame took 5.3 ms against 4.3. The `[draw]` line's "last scene N draws, ... even-odd" said why: 216-387 draw calls against 130-160, because polygons that cross or touch themselves (wheels drawn as rings, faces seen edge-on, track pieces near the camera) went through the stencil, two draw calls each, and broke the batches around them.

- Fix: cut them in bands for the card too (`SceneGeometry.bandsOnCard`), with the points where bands meet edges placed by the card along the edge (`ROLE_ALONG`). 1-3 draw calls a scene.
- A cut fixed for a tick while the card moves its corners each frame must have no T-junction: every piece runs through every point on its cuts, or a corner of the next band comes off the cut and the picture cracks for a frame. The projection test (`-Dnfm.projectiontest=60`) caught the first version's crack; compare its worst frame against a run of the build before.
- A temporary count of why polygons took the stencil (crossing, touching, flat) in a scratch build pointed at the fix in one hidden run.

## How to find the next one

- `-Dnfm.framelog.csv=true` also writes `ticks-NN-<part>.csv`: per tick, when it was due, how late it began, the race step, the run-ahead, when the poses were handed to the presenter, the kept scene, the card and when it was done. A tick whose `done_ms` passes 53 makes frames stand still; an empty `handed_ms` is a hand-over skipped to catch up.
- `[framelog] <part> began at <time>` lines line the CSVs up with Java's own logs (`-Xlog:gc*`, `-Xlog:jit+compilation=debug,deoptimization=debug` with `time`).
- The PC is noisy: judge a change by interleaved A/B runs, several rounds, on the spread, never on one pair.
- To make drawing code cheaper without changing the picture, build every scene twice in a scratch build, with the committed class copied under another name and with the new one, and compare the bytes; alternate which goes first to time both on the same scenes (the band code: the same bytes in 4,800 scenes, 8% quicker). `-Dnfm.retained=false` sends every frame down the processor's path, which the default run never takes.
