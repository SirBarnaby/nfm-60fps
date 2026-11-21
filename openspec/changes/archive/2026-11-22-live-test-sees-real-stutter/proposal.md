## Why

The owner, 21 Nov 2026: "If I lock my fps at 120fps, then the game is smooth. If I ran it at unlocked, I got crazy stutters. Somehow, our test harness does not detect this. I feel like the test harness architecture should be changed."

The live test (`test\live.ps1`) judges only *when* frames went out: frame times, late frames, hitches. With no frame limit the frames do come evenly (99% within 7-13 ms at 175-270 fps), so it passed. What the frames *show* does not move evenly. Measured off screen at 1920x1080 fullscreen on the owner's PC, with no limit the race stands still for 0.3-0.5 s and then jumps 5 ticks (265 ms) at once. 13-90% of race frames are more than 4 ms off even progress. At a 120 fps limit, 0.0-0.7% of replay, race and highlight frames are. The harness never looked at this.

Nothing is MODIFIED or REMOVED. One requirement is ADDED to `done-and-testing`, from the owner's words above. No feature the owner did not ask for.

## What Changes

- **The game logs what each frame shows, not only when it went out.** `FrameLog` records, for every race frame, the race moment it shows (the tick it was placed from and how far along) next to when it went on screen.
  - From these it works out how evenly the race moved: each frame's distance from even progress, the share over 4 ms, and the longest stretch of such frames in a row.
  - The results go in `frames.json` (`motion`) and in the game's log line for each part.
  - `-Dnfm.framelog.csv=true` writes every frame to a CSV for a closer look.
  - Off by default and free when off. Hidden runs are unchanged.
- **`test\live.ps1` flags uneven motion.**
  - A race part is flagged `motion uneven` when over 1% of its frames are more than 4 ms off even progress.
  - It is flagged `motion hitch` when the race stood still or jumped for over 100 ms in a row, the same 100 ms as the existing race hitch.
- **`test\live.ps1` plays with no frame limit and at 120 fps.** By default (`-Limits 'unlimited','120'`) it adds a run of the owner's settings at each limit the owner's own run did not have.
  - It compares each race part across the two runs.
  - It flags a part that `stutters with no frame limit, not at 120 fps`: exactly what the owner saw.
  - `-Limits none` plays the owner's settings alone.
- **Tools that read Windows' record of frames on the monitor were checked, and none is used yet.** PresentMon, CapFrameX, OCAT and FrameView all need admin rights, or membership of the Performance Log Users group, to read Windows' graphics event trace. Their display timing also means something only for a window on a monitor, and the live test must stay off screen.
  - That part waits for the owner (see design.md).
  - The game's own motion measure catches this stutter without them.

Existing libraries and tools looked at before building the measure: PresentMon 2.6.0 (console and service), CapFrameX 1.9.2, OCAT 1.6.3, NVIDIA FrameView. Why none is used is in design.md. Lessons go in `docs\solutions\measuring-stutter.md`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `done-and-testing`: ADDED requirement "Stutter with no frame limit is caught". Nothing existing changes.

Specs touched (from `openspec list --specs`): `done-and-testing` (ADDED). `performance` is read, not changed: its "No stuttering, ever" pacing check runs through `test\live.ps1`, which now also flags uneven motion.

## Impact

- `src\nfm\lit\FrameLog.java`: the motion record, measure, JSON, log line and CSV.
- `src\nfm\lit\PoseSpan.java`: `motionAt(alpha)` and the run-ahead flag `ahead`.
- `src\nfm\lit\GameSparker.java`: passes each frame's race moment to `FrameLog.shown` from the presenter and the game thread. Race code is untouched.
- `test\live.ps1`: motion columns and flags, `-Limits`, the comparison.
- `AGENTS.md`: the live test row.
- The live test now fails with no frame limit until the stutter is fixed. That is the point: the fix is its own change (`no-frame-limit-stutter`).
- Each live test run takes one game run longer (about 2.5 minutes) by default.
