## Why

The owner, 22 Nov 2026: "All stutters should be fixed." The live run at 07:14 (seed 2110048692, stage 4) flagged four:

- the race froze early on: 251 ms at about 1.2 s with no frame limit, 183 ms at about 1.5 s at 120 fps;
- the countdown hitched as it started at 120 fps (232 ms from 0.07 s) and was uneven at both limits (about 2% of frames);
- races were uneven: 10.1% of frames with no limit, 1.9% at 120 fps;
- the frame rate with no limit stayed under 250 (countdown 216, race 238).

This change touches no spec: it makes the game meet the existing requirements "No stuttering, ever" and "250 to 350 frames a second at 1080p fullscreen" (`performance`). Nothing is MODIFIED or REMOVED, and no feature the owner did not ask for is added. Races do not change (`test\verify-races.ps1`).

**Causes, measured** with a tick trace (when each tick was due, ran, played ahead and was handed to the presenter) and a stack sampler, in hidden runs of the same seed:

- **Java rebuilt its reflection accessors about a second into the first race.** After every tick the game plays the next one ahead and puts the race back (run-ahead). It copied the race state field by field through `Field.get`/`Field.set`. Since Java 18 each reflected field is read through a method handle of its own, which Java rebuilds as a class of its own after 127 uses; the copy reads and writes about 1,600 fields, all of which reached 127 uses together: one run-ahead tick took 184-248 ms. The replay recorder (on in real play, off in the live test) and the split-screen HUD read fields the same way.
- **The race's code is cold when the first race starts.** The physics, the AI and the collisions never run before the race (not in the countdown either), so Java runs them slowly until it has compiled them: for about two seconds a tick took 18 ms and a run-ahead tick 28 ms, against 11 and 12 later.
- **The card starts cold too.** The countdown's first scene is the first the card draws for the stage: making the first kept scene took 45-62 ms, and the presenter's first eight frames 17-50 ms each while the card created its buffers, targets and shaders.
- **System.gc() holds the game thread.** The original calls it on Enter at the stage screen and while loading the music. With ZGC each call waits for a whole collection: 61-157 ms, the last right before the countdown's first tick.
- **The portrait tint at "3".** At the countdown's 36th tick from the end the game tints the player's portrait over the picture behind it. To read that picture it drew the whole frame again in software (45 ms or more) and blended through `PixelGrabber` and `Color` (about 25 ms): 67-137 ms in one tick.
- **Frames stand still while a tick is worked out.** Frames carry the last motion on for at most 13 ms past a tick (`PoseSpan.MAX_CARRY_ON`), then stand still until the tick and its run-ahead are done. They took 10-17 ms on a quiet PC, more when it was busy, so with no frame limit many frames fell in that gap: 10% against 2% at 120 fps.
- **Replay recording in real play** (not seen by the live test, which records no replays): 5-7 ms a tick, 29 ms every 40th tick and 125 ms at "3", from the same reflection plus walking the state's names twice.
- **The countdown's frames held the presenter under 250 a second.** Its ticks were lighter than the race's, yet it was the slowest part in every run: the frames cost more, not the ticks. With the cars side by side near the camera, a countdown scene had 216-387 draw calls against 130-160 in the race. A polygon whose outline crosses or touches itself (the wheels are rings drawn as one outline that goes round twice; faces seen edge-on cross) was filled through the stencil, two draw calls of its own, and ended the batch of plain polygons before it: 140-200 of them a countdown scene, 60-90 in a race, replay or highlight. Handing a frame to the card took 5.3 ms against 4.3 (hidden runs).
- **The polygon drawing was compiled again mid-race.** In about half the runs, about 1.7 s in, two or three ticks took 50-100 ms each. The JIT log: Java had compiled `Plane.d` in the menus before the class it uses for flying debris (`EffectTrails`) was initialized, so the compiled code held a trap there, and the first chip of debris threw the whole method away while it was compiled again.

## What Changes

- **The race state is copied and read without reflection's per-field accessors** (`FieldSlot`). Each field is read and written through a `VarHandle` of its exact type, which Java never rebuilds; primitive values stay unboxed. The run-ahead copy, the replay recorder's per-tick hashes and the split-screen HUD swap use it. The same fields in the same order, so the race is put back and recorded exactly as before.
- **The game warms up before the race, unseen.** While the music loads (the screen holds still then anyway), it records the countdown's first scene and has the card draw it once behind the next picture, once a stage, and plays the race's first tick ahead and puts the race back for up to 200 ms, once a run of the game. The rest of 96 such ticks it plays only in measured spare time before the next tick is due, so no frame waits for them. The rollback self-test (`verify-races.ps1 -Rollback`) checks that every warm-up tick leaves the race exactly as it found it.
- **`-XX:+DisableExplicitGC`** in the runtime's options: the original's System.gc() calls return at once; ZGC collects on its own.
- **The portrait tint reads only the picture behind the portrait**: the scene is drawn again clipped to that area, polygons outside it are skipped, the portrait's pixels are decoded while the music loads, and the blend is integer arithmetic. Same pixels (a temporary check compared all 37,760 of them with a whole redraw: none differed).
- **Frames carry the motion on for up to half a tick** (`MAX_CARRY_ON` 0.25 to 0.5): measured, it halves the frames off even without larger corrections and cannot add input lag (see the design).
- **The replay recorder walks the state's names once** at the countdown, not twice.
- **`Plane` initializes the effect classes its drawing uses** (`EffectTrails`, `Glow`) when it is first loaded, so its compiled drawing never holds a trap for them.
- **Polygons that cross or touch themselves are cut in bands for the card too**, as split screen's drawing on the processor already was: plain triangles, drawn in the same call as the rest, so a race scene is 1-3 draw calls. The points where a band meets an edge are placed by the card as a share of the way along that edge, so they move with the polygon between ticks, and each band runs through every point on its cuts, so neighbouring bands meet without a crack. A polygon flat when it is cut keeps the stencil.
- **The tick trace stays** (`-Dnfm.framelog.csv=true` writes `ticks-NN-part.csv` next to the frame log) for the next stutter hunt.
- **Not changed:** the race code, the frame limit, the frame log's checks and thresholds, `test\perf.ps1`'s baseline.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: performance "No stuttering, ever" and "250 to 350 frames a second" already ask for this; checked by test\live.ps1 -->

Specs touched (from `openspec list --specs`): none changed. `performance` holds the requirements this meets; `done-and-testing` holds how they are checked.

## Impact

- `src\nfm\lit\FieldSlot.java` (new), `StateCopy.java`, `RaceSnapshot.java`, `RaceState.java`, `PlayerHud.java`: field access.
- `src\nfm\lit\GameSparker.java`: the warm-up, the run-ahead sharing its play-and-put-back with it, the unseen draw in `showOnGpu`, the clipped redraw for the portrait, the tick trace's timings.
- `src\nfm\lit\GameUi.java`: the portrait tint.
- `src\nfm\lit\SceneRecorder.java`: skipping polygons outside a clip.
- `src\nfm\lit\RaceSession.java`: the rollback self-test checks the warm-up; the names walked once.
- `src\nfm\lit\FrameLog.java`: the tick trace.
- `src\nfm\lit\PoseSpan.java`: the carry-on.
- `src\nfm\lit\Plane.java`: the effect classes initialized with it.
- `src\nfm\lit\gpu\SceneGeometry.java`, `Shaders.java`: bands for the card, a vertex placed along an edge (the shader is baked again by `build.ps1`).
- `build.ps1`: `-XX:+DisableExplicitGC` (the runtime is rebuilt once, as its options change).
- Race code untouched: `test\verify-races.ps1` must match all 20 races, with no golden frame changed, also with `-Rollback 50`.
