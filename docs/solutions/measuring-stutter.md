# Measuring stutter: frame times are not enough

*21 Nov 2026, from the owner's report that the game stuttered with no frame limit and was smooth at 120 fps, while `test\live.ps1` passed both.*

## The lesson

Even frame times do not mean even motion.

With no frame limit the presenter thread put frames on screen every 4-5 ms. Meanwhile the game thread, starved of a lock the presenter held almost all the time, ran no ticks for 0.3-0.5 s. Then it ran 5 at once.

Every frame came on time and showed the same moment of the race. Then the race jumped 265 ms. A test that judges only frame times (FrameLog, PresentMon's `MsBetweenPresents`, any fps counter) calls that smooth.

## What the game measures now

Every frame the game shows during a race notes two times: when it went on screen, and which moment of the race it shows.

- **That moment** = the tick's start + `alpha` × the tick's length, plus one tick with run-ahead (`PoseSpan.motionAt`).
- **The lag** = shown − moment. It stays constant when the race moves evenly.
- **Off even progress** = the lag − the median lag over the frames within ±0.5 s.
  - Off by over 4 ms means the race stopped or jumped for that frame.
  - A part is judged from its first frame, so a race that stands still as it starts or resumes counts. Only its last 100 ms are not judged: the race holds its last tick still there for the cut.
- **Where it goes.** `frames.json` → `parts[].motion`: `p99Ms`, `strays`, `judged`, `maxMs`/`maxAt`, `stretchMs`/`stretchAt` (the longest stretch off in a row). The game's log also gets a line per part.
- **Every frame, for a closer look.** Use `-Dnfm.framelog.csv=true`, which writes `frames-NN-<part>.csv` (shown, moment, chosen, lag, off, in ms) next to `frames.json`.

`test\live.ps1` flags a race part `motion uneven` when over 1% of its judged frames are off, and `motion hitch` when frames are off in a row for 100 ms or longer.
- The share is counted from `strays` and `judged`, never read off `p99Ms`, which is rounded: 4 off in 320 frames is 1.25%, uneven, even when `p99Ms` says 4.00.
- A measured hitch always counts. Only the share needs 100 judged frames; with fewer it is a note saying so.

It plays your settings with no frame limit and at 120 fps (`-Limits`) and flags a part that stutters only with no limit.

## Reading a CSV

A freeze then a jump looks like this: `motion_ms` stays the same for many lines while `shown_ms` goes on, then `motion_ms` steps by several ticks (53 ms each in a race). `lag_ms` climbs during the freeze and drops at the jump.

A one-off frame far off right at a part's start is a resume after a pause. It is judged: one frame adds a stray, not a hitch. Frames off in a row as the race starts or resumes are a hitch like any other.

## Tools for the screen side (none used yet)

| Tool | Needs | Notes |
|---|---|---|
| PresentMon 2.6.0 console | admin, or the Performance Log Users group (it reads Windows' graphics event trace, ETW) | `DisplayedTime`, `MsBetweenDisplayChange` mean something only for a window on a monitor. `AnimationError` needs the app's simulation times (`--track_app_timing`: Intel-PresentMon AppSimulationStart events; or `--track_pc_latency`), otherwise it uses the processor's frame start, which is even here |
| CapFrameX 1.9.2 | install, admin | a GUI on PresentMon |
| OCAT 1.6.3 | install, admin | PresentMon inside, last release Sept 2023 |
| NVIDIA FrameView | install, admin | PresentMon-based |

The live test stays off screen and runs without admin, so these wait for the owner. See the design of `live-test-sees-real-stutter` (openspec changes archive) for the exact steps.

## Pitfalls met on the way

- **Hidden runs.** They show the same stutter as the live test (`-Dnfm.autotest=<dir> -Dnfm.renderer=gpu -Dnfm.window=1920x1080 -Dnfm.autotest.shots=false -Dnfm.fps=unlimited`), so the cause can be chased without the live test's 2.5 minutes a run.
- **`-Dnfm.profile=true` lines.** `[loop]` shows sim Hz and ms a tick, `[draw]` "the motion came N ms after the tick was due". A large "after the tick was due" with even frame gaps is this kind of stutter.
- **Passing `-Set` arrays.** Call `.\test\live.ps1 -Set 'a','b'` from PowerShell itself. Through `powershell -File` the array arrives as one string.
