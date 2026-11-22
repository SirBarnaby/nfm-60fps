## Context

**A race tick** runs every 53 ms on the game thread: the race's own step (physics, AI, collisions, and the drawing the original does as part of it), then, for the presenter, the run-ahead (the next tick played ahead and the race put back), the poses handed over (`handOverKept`), the kept scene recorded and made ready for the card. **The presenter thread** draws frames from the kept scene in between, placing the cars from the poses (`PoseSpan`).

**How it was measured.** A tick trace (`-Dnfm.framelog.csv=true`, `ticks-NN-part.csv`) gives each tick's due time, how late it began, its race step, the run-ahead, when the poses were handed over, the kept scene's recording and the card's part. A stack sampler (scratch tool, not shipped) gives the game thread's stack every few ms. Hidden runs (`-Dnfm.autotest`, 1920x1080, GPU renderer, the live test's seed 2110048692, stage 4), judged with the frame log's own motion measure (`frames.json`, the share of frames off even by over 4 ms, "strays", and the longest stretch off in a row).

**The PC is noisy.** The owner's i7-8550U laptop chip (4 cores, 8 threads) ran other test runs (Sunset Racer 2) at the same time, and runs of the same build swing by 2x. Every before/after is therefore an interleaved A/B: the two builds or settings take turns, several rounds each, and the A/B is judged on the spread, not one pair.

## Goals / Non-Goals

**Goals:**
- None of the four flags of the 07:14 live run: no freeze in the race, no hitch at the countdown, races moving evenly at both limits, 250 fps or more with no limit.
- Races exactly as they are: every recorded race plays out tick for tick as before (`test\verify-races.ps1`, also with `-Rollback 50`), golden frames unchanged.
- No input lag added, no check loosened, `test\perf.ps1`'s baseline untouched.

**Non-Goals:**
- Changing the race's own code or how often it ticks, even where a cost sits there (see Risks).
- New settings or options: everything here is on for everyone.

## Decisions

### 1. Field access through VarHandles (`FieldSlot`)

- The run-ahead captures and restores about 1,600 fields a tick (`StateCopy`, `RaceSnapshot`). Through `Field.get`/`Field.set`, Java 18 and later reads each field through a method handle of its own and, after 127 uses, spins a class for it ("customizing" the handle). All 1,600 reached that count in the same tick, about 1.1-1.5 s into the first race: 184-248 ms.
- `FieldSlot` holds a `VarHandle` per field, made once through `MethodHandles.privateLookupIn`, and calls it with the field's exact type (`(int) handle.get(o)`), which Java links straight to the field and never customizes. Primitive values travel as raw bits in a `long` (floats and doubles by their bit patterns), so nothing is boxed.
- The same field lists in the same order, read and written the same way: the copy and the replay's hashes come out bit for bit as before.
- *Alternatives:* `-Djdk.reflect.useDirectMethodHandle=false` (removed in Java 22); `Unsafe` field offsets (deprecated for removal, and its warnings would reach the owner's console); generating copy code per class (much more code for the same result).
- Also moved: `RaceState` (the replay recorder reads its fields every tick in real play; the live test records no replays, so it never saw this) and `PlayerHud` (split screen swaps the HUD's fields every frame).

### 2. Warm-up while the music loads, the rest in spare time

- **Why at all.** The first race's code is cold: Java interprets it, then compiles it in steps. For about two seconds a tick took 18 ms and a run-ahead 28 ms, against 11 and 12 later; with the kept scene and the card on top, the game thread needed more than the 53 ms a tick has, and frames stood still.
- **What.** `playAndPutBack` (shared with the run-ahead) plays the race's next tick and puts the race back exactly; the warm-up calls it 96 times, about as many ticks as the first two seconds of a race run, once a run of the game. And once a stage it records the countdown's first scene and has the card draw it unseen behind the next picture shown, so the card has its buffers, targets and shaders when the countdown starts.
- **When.** The race state is complete only from `SELECTEDCARSAVE` on, so warm-up ticks can run from `PREGAMEMUSIC`. The first, slowest ticks run while the music loads, up to 200 ms on top of it (the screen holds still then anyway: the stage picture with "Loading"). The rest run in measured spare time: before sleeping until the next tick, a warm-up tick runs only if the last one, plus a quarter, fits before the next tick is due less 6 ms. No frame or tick waits for one.
- **Never in split screen** (its second camera and HUD are not put back by `playAndPutBack`), and never during a race (only `PREGAME`, or the countdown while `starcnt > 0`).
- **Safety.** Under `verify-races.ps1 -Rollback` every warm-up tick is played and the whole race state compared before and after (`RaceSnapshot.captureComplete`); any difference fails the verify.
- *Alternatives tried:* the warm-up in the stage screen's ticks with a time slice (v1): its first cold tick took 108 ms and pregame frames 56-168 ms, so menus hitched; all 96 ticks in one block in `PREGAME`: smooth races, but a 1.1-1.3 s frame on the stage screen.

### 3. `-XX:+DisableExplicitGC`

- The original calls `System.gc()` on Enter at the stage screen (`musicomp`) and while loading the music (`RadicalMod`). With ZGC each call blocks its caller until a whole collection ends: 61-157 ms, the last right before the countdown's first tick; menus also showed 520 ms frames when one fell on a busy moment.
- ZGC collects on its own, concurrently, keeping under the 512 MB soft limit, so the calls are not needed. With the option the heap stayed under 452 MB and the number of collections was about the same.
- In `build.ps1`'s jlink `--add-options`; the runtime is rebuilt once, as its options' stamp changes.
- *Alternative:* removing the calls from the original's code: two places, and any other library call would still block.

### 4. The portrait tint at "3" reads only what is behind the portrait

- At `starcnt == 36` the original blends the player's portrait with the picture behind it (`blendude`), reading the back buffer. With the GPU renderer the back buffer is stale, so the whole frame was drawn again in software first (`syncBackBuffer`, 45 ms or more) and the blend went through `PixelGrabber` and `Color` objects (about 25 ms): 67-137 ms in that tick.
- Now `syncBackBuffer(region)` draws only `PORTRAIT_BEHIND` (217-453 x 0-160, both places the portrait can go, in the back buffer's pixels): the clip is set in device space, and `SceneRecorder.replayCommands` skips a polygon whose bounds miss the clip (grown by 3 device pixels for antialiasing and lines). The back buffer stays marked stale, so the next whole sync still draws the rest.
- The portrait's own pixels are decoded once while the music loads (`readPortraits`), and the blend is integer arithmetic: `(portrait + 3 × behind) / 4` per channel, as `Color`'s did.
- *Checked:* a temporary comparison of the clipped sync against a whole sync, both in the same tick: 0 of 37,760 pixels differed.
- *Alternative:* read the pixels back from the card. That would wait for the card mid-frame and is a new path for one tick a race.

### 5. Carry-on 0.5 of a tick

- Between ticks, frames place the cars along the last tick's motion; past its end they carry it on for at most `MAX_CARRY_ON` of a tick, then stand still until the next poses come. At 0.25 (13 ms), any tick that took more than 13 ms to reach the presenter made frames stand still and then jump.
- The previous change (no-frame-limit-stutter) kept 0.25 after an A/B of two runs each that showed no clear difference. This time, with the tick trace and three variants: race strays 2.9-3.3% at 0.5 against 4.9-8.7% at 0.25, and the corrections when the poses came (median of the runs' medians) 6 units against 15. Carrying on further means fewer frames stand still, and the correction, when it comes, is from a closer guess.
- **Input lag.** Unchanged by construction: new poses are used the moment they are handed over, whatever the carry-on; it only decides what frames show while none have come. The `[draw]` line's "key to screen" counts from the key to the first frame showing its effect, which the carry-on never delays.

### 6. The replay recorder walks the names once

- At the countdown (`countdownStarting`) the recorder took the start state, the start state's names and the layout, which walked the names again: 125 ms at "3". The layout now folds the names already made (`RaceState.layout(view, names)`), giving the same hash.

### 7. The effect classes initialized with `Plane`

- About 1.7 s into the race, in about half the runs, two or three ticks took 50-100 ms each (the race step, the run-ahead and the kept scene alike), always in `Plane.d`, the polygon drawing.
- The JIT log showed why: `Plane.d ... trap_bci=3206 uninitialized reinterpret`, then `made not entrant: uncommon trap`. Bytecode 3206 reads `EffectTrails.on`, on the path for flying debris. Java compiles `Plane.d` fully while the menus draw cars; when it finishes before the countdown's first scene has initialized `EffectTrails`, the compiled method holds a trap there, and the first chip of debris throws the whole 7.6 KB method away while it is compiled again. In the runs where the compile finished after the countdown began, there was no stall.
- `Plane`'s static initializer now initializes `EffectTrails` and `Glow` (the other class `d()` only reaches on rare paths). Neither has anything to set up that a race sees.
- No JVM option avoids it: the trap limits the runtime already sets (`-XX:PerMethodTrapLimit=0`) do not cover a class that is not initialized. *Alternative:* initializing them in the game's start-up code; the reason is `Plane.d`'s, so the fix sits next to it.

### 250+ fps and no stutter

TODO after the live test.

### Races stay identical

- No race code changes. The warm-up and run-ahead put the race back through the same copy as before; the copy reads and writes the same fields with the same values.
- `test\verify-races.ps1`: 20 matched, golden frames unchanged; with `-Rollback 50` every warm-up tick is checked to leave the state as it found it.

## Risks / Trade-offs

- **Other classes compiled before they are ready.** The same kind of trap was seen once more, in `GameUi.statPlayer` at its first "wasted" message (a string joined for the first time), with no slow tick around it. Only `Plane.d` is large and hot enough for its recompile to show.
- **Stalls from a busy PC.** In hidden runs while other test runs loaded the processor, some ticks began 100-400 ms late with no collection, compile or trap anywhere near: the game thread simply did not get the processor. The live test waits for a quiet PC.
- **The countdown is the heaviest part.** Its camera sees most of the track: the kept scene takes 13-22 ms and the card's part 7-11 ms, against 8 and 4 in the race. On a quiet PC a tick fits in 53 ms (half within 32 ms); when other programs load the processor it does not, and ticks are handed over late.
- **Warm-up costs up to 200 ms more on the loading screen**, once a run of the game. The screen holds still then anyway.
- **The frame rate with no limit.** The presenter is bound by the card's frame submission (`bgfx_frame`, one frame in flight). Letting more frames queue would raise the frame rate and add input lag, which the owner forbids.
