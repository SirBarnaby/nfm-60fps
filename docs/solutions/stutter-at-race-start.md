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

- Fix: play the race's next tick ahead and put the race back (the run-ahead's own `playAndPutBack`) before the race, unseen: while the music loads (the screen holds still then anyway, up to 500 ms) and in spare time on the screen before the race. Never block a frame for it: a block of 96 ticks gave smooth races and a 1.3 s frame on the stage screen.
- Not in the countdown's spare time either: a warm-up tick is the coldest code there is, and one that ran long there made the next tick 17-42 ms late and the motion jump 43 ms. "Fits in the spare time" is judged by the last tick's length, which says nothing about the next one while Java is still compiling.
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

- Fix: cut them in bands for the card too (`SceneGeometry.cutLines`, `bandPieces`), with the points where bands meet edges placed by the card along the edge (`ROLE_ALONG`). 1-3 draw calls a scene.
- A cut fixed for a tick while the card moves its corners each frame must have no T-junction: every piece runs through every point on its cuts, or a corner of the next band comes off the cut and the picture cracks for a frame. The projection test (`-Dnfm.projectiontest=60`) caught the first version's crack; compare its worst frame against a run of the build before.
- A temporary count of why polygons took the stencil (crossing, touching, flat) in a scratch build pointed at the fix in one hidden run.

### 7. A part's first frame: start its clock when its first scene is ready

Frames are placed between two ticks by the clock: tick start plus alpha times the tick's length. The race's first scene takes 60-90 ms to make (the countdown's camera sees most of the track), while the screen before stays up; its stretch was timed from when the menu tick that started the race was due. So the first frame showed the race 79 ms behind, frames stood at the end of a stretch already gone by, and ticks 1 and 2 ran 28-85 ms late to catch up, each with a scene of its own to make.

- Seen as: "the race stood still or jumped" at 0.1 s in every live run, and in the ticks CSV tick 1 late by about the first scene's time.
- Fix: the first scene a presenter shows of a part starts its stretch when it is handed over, a race tick long, and the next tick is due one tick later (`handOverKept`, `firstSceneNanos`). The same ticks with the same keys: only their moment on the clock moves, once, while the screen before is still up. Tick 1: 0.0-0.1 ms late.
- Where else: anything timed from a tick that started in another part, at another tick length (the menu's 40 ms against the race's 53).

### 8. Place a frame for when it reaches the screen, not when it is begun

With frames drawn on their own thread, one frame a tick does more than the others: it sends the card the tick's scene (1-3 MB) and HUD. At 120 fps that frame took 12-14 ms instead of 8.3 and showed the race 4-6.7 ms behind the screen against 0.7 for the rest: one countdown frame in eight was off even by over 4 ms, evenly spread, with no tick late.

- Seen as: in the frames CSV, `lag_ms` higher once a tick, on the frame after the tick's `kept_ms`; the "off" count flat across the part instead of in a burst.
- Fix: keep how long a frame takes from being placed to reaching the screen, smoothed, by the frame's place after a new scene (the one that sends it, the two after it, the rest), capped (16 ms) so one stall does not throw the next frames off, and place each frame for that moment (`Presenter.showNanos`). Countdown at 120 fps: 82 of 793 frames off even → 14 of 815. Split screen sends two panes' scenes, and there the two frames after also waited 3-5 ms on the card: 71 of 75 frames of a stretch off even, until they got places of their own.
- New poses are still used the moment they come: placing for the screen moves no input later.

### 9. The live test plays the menus too: a resting mouse can choose

The split-screen live test ran out of time: its results screen took the item under the mouse every tick, so with the mouse resting over "Race again" the keys' choice of "Main Menu" was undone a tick later and the test raced again and again. The pause menu already took a hover only when the mouse moved.

- Fix: a menu takes the item under the mouse only when the mouse has moved since it last chose (`SplitScreens`, `hoverX`/`hoverY`); the first tick takes it where it is, as the pause menu does.
- A live test that "times out" is worth a look at what was on the screen: here it was a real bug a player with a mouse at rest would have hit.

### 10. Old image calls are slow when they run rarely: read the picture's buffer

Split screen's results and the pause menu grey the race as it stopped, in their first tick. That tick took 56-98 ms (menu ticks are 40), so the results stood still 115-150 ms as they came up. Sampled, about half of it was `PixelGrabber` reading the 1920x1080 picture, and the rest the greying through two `Color` objects a pixel, the race replayed in software into the picture, and the box drawn. The grabber hands pixels over through the image producer and consumer interfaces, a call a row, and code that runs once a race never gets compiled: some 30 ms.

- Fix: read a `TYPE_INT_RGB` picture with `Raster.getDataElements` (rows copied at once, fast even cold), any other with `getRGB`, and grey with whole numbers (`GameUi.grabScreen`, `pauseimage`). The readers take only red, green and blue, so the grabber's alpha byte does not matter. The countdown portrait's tint already read its picture that way.
- Proved the same: a scratch harness greyed display-compatible, ARGB and byte pictures both ways (all pixels the same) and timed them in fresh runs: the first greying 59 → 20 ms, warm 9.6 → 6.7 ms. In the game with its AOT cache (interleaved hidden split-screen runs, two each, every results screen in them, beside another game using the processor): the results' longest frame, median 200 → 89 ms (126-264 against 67-133).
- What is left of that tick is the race replayed in software into the picture (15-35 ms for split screen's two panes) and the HUD over it: the grey picture must show the race as it ended.

### 11. A display that has gone to sleep makes every measurement wrong

Windows turns the display off after 10 idle minutes here. With it off, the card held frames up 400-500 ms over and over, now and then 2 s, in every part of every run, in either build, menus included. No player sees that: they are at the keyboard. Runs while the owner was away overnight all had it.

- Seen as: "the graphics card held a frame up" lines all through a run, at the same few hundred ms, in parts that never stall otherwise (main menu, settings).
- Asking for the display (`SetThreadExecutionState` with `ES_DISPLAY_REQUIRED`) keeps a display on, but does not wake one that is already off. A mouse move by nothing (`SendInput`, 0,0) does, without moving the pointer.
- In PowerShell 5.1, `0x80000003` is a negative `Int32` and a `uint` parameter refuses it: the watcher's hold threw and was never taken. Pass the flags in decimal as `[uint32]`.
- The watcher now waits for 5 minutes with nobody at the keyboard and a quiet processor, holds the display, wakes it if the screen-off time may have passed, and lets it go at the end.

### 12. A presenter that starts again is not a race that starts

- Decision 9's first scene was "the presenter draws nothing yet", and the presenter also stops for one frame mid-race (a screenshot, a scene that could not be recorded): each such frame moved the race's tick clock. Found by the blind review, not by a test: recorded races never use the presenter, and the live test leaves screenshot frames out of its counts.
- Fix: say what you mean. A race's first scene is one after a frame that was not a race's (`raceOnScreen`).
- Proved: a hidden run with screenshots on (`-Dnfm.autotest.shots=true` after the run script's own `false`): 90 screenshots in the race, its 1,999 ticks all due 53 ms apart (the ticks CSV's `due_ms`).

### 13. Check a geometry change on the game's own polygons, followed by continuity

- The band crossings (decision 15) looked better on random polygons only for small moves, and worse in the worst case for big ones. On the game's own polygons (`-Dnfm.motiontest` scenes: 49 moments between two ticks), the first count said the new code was worse.
- That count matched polygons between moments by kind, corners and order, and a scenery polygon that came or went shifted every later one: different polygons were compared. Followed moment by moment from the middle, each corner moving at most 6 px a step, the new code was better in 108 placements and worse in 13 by a pixel (1,775 → 135 pixels wrong).
- How: cut the polygon with the real `SceneGeometry.buildProjected` (a class in its package on the classpath, the recording's points as `CONSTANT` ones), place the vertices as `Shaders.PROJECTED_VS` does with the moved corners, and compare pixel centres with the even-odd fill.

### 14. Check that a guess holds where it is used, and count both ways it can be wrong

- Decision 15's crossing was clamped to its band, which bounded how wrong it could be but not whether it was: the second blind review moved one edge of a bow-tie 2 px and found holes. A limit worked out where the polygon is cut (how far an edge may move) flagged 43% of the game's polygons below a quarter pixel. Asking the scene where the corners actually go at both ends of a tick sends 8% to the stencil and leaves nothing wrong.
- Counted as one number, the fix looked nearly done; counted as filled outside and missed inside, the holes were gone and something still filled outside. One dumped case showed why: a fan triangle flat where it was cut, left out of the check, folding over the tip of a notch. A check with exceptions has holes: the pieces went to strips, whose triangles are never flat, and the check needed none.
- How: `test\geometry-check.ps1` (random polygons, and with `-Motion` the game's own from `-Dnfm.motiontest` scenes) fails on any sample wrong away from the outline; with `-NoProbe` it shows the card before the check.

## How to find the next one

- `-Dnfm.framelog.csv=true` also writes `ticks-NN-<part>.csv`: per tick, when it was due, how late it began, the race step, the run-ahead, when the poses were handed to the presenter, the kept scene, the card and when it was done. A tick whose `done_ms` passes 53 makes frames stand still; an empty `handed_ms` is a hand-over skipped to catch up.
- `test\geometry-check.ps1` checks the card's bands away from the game, with the game's own polygons from `-Dnfm.motiontest` scenes; run it after any change to `SceneGeometry` or the projected vertex shader.
- `[framelog] <part> began at <time>` lines line the CSVs up with Java's own logs (`-Xlog:gc*`, `-Xlog:jit+compilation=debug,deoptimization=debug` with `time`).
- Before believing a run, check it had the display on: card holds of a few hundred ms in menus that never stall say it slept (lesson 11).
- The PC is noisy: judge a change by interleaved A/B runs, several rounds, on the spread, never on one pair. Wait for a quiet PC (processor under 15% for 30 s, no game running) before a hidden run that is to be judged, and repeat a once-seen stall before chasing it.
- For one part's start, line its first ten ticks against its first two dozen frames (`shown`, `motion`, `lag`, `off`): a tick late at the start, a frame placed for a moment already gone, and a frame that took long to show each have their own look.
- To make drawing code cheaper without changing the picture, build every scene twice in a scratch build, with the committed class copied under another name and with the new one, and compare the bytes; alternate which goes first to time both on the same scenes (the band code: the same bytes in 4,800 scenes, 8% quicker). `-Dnfm.retained=false` sends every frame down the processor's path, which the default run never takes.
