## Context

**How a race frame is made.** The game thread runs the race in fixed ticks (53 ms in races, replays and highlights; 40 ms in menus) and catches up when it falls behind. The presenter thread (`nfm-presenter`) owns the graphics card. For every frame it takes the scene kept from the last tick and places the cars between that tick and the next by `alpha = (now - tick start) / tick length`, letting it run on to 1.25 if the next tick is late. With run-ahead the next tick has already been worked out, so the frame shows one tick further on.

**The frame limit.** "Unlimited" makes the presenter only `Thread.yield()` between frames. A number makes it sleep on a high-resolution waitable timer (`PreciseSleep`). Vsync is used only for Match display and Variable refresh.

**What was measured.** `FrameLog.shown` notes when the call that hands the frame to the card returned, so the live test judged the time between frames. It never looked at what the frames showed.

**The owner's report.** Smooth at a 120 fps limit, "crazy stutters" unlocked, and the live test passed both. The report is in proposal.md.

## Goals / Non-Goals

**Goals:**
- Measure what the player sees move, from the game's own clocks, in every live run and every session's log.
- Fail the live test on that stutter. Play no frame limit and 120 fps by default and compare them.
- Keep hidden runs, races and golden frames exactly as they are. Keep the test off screen.

**Non-Goals:**
- Fixing the stutter. That is its own change, `no-frame-limit-stutter`.
- Measuring what reaches the monitor. That needs a window on screen and admin rights; see "Waiting for the owner".

## Decisions

### The four suspects, tested before building anything
All runs were off screen at 1920x1080 fullscreen on the owner's PC, with the owner's settings: live runs and hidden `-Dnfm.profile=true` runs, no limit against 120 fps.

1. **Uneven motion: confirmed.**
   - Frames came evenly with no limit: 99% within 7-15 ms.
   - What they showed did not. With no limit the race stood still for 0.3-0.5 s, then jumped on by 5 ticks (265 ms) at once.
   - 12-90% of race frames were more than 4 ms off even progress, and the longest stretch off was 0.4-1.0 s.
   - At 120 fps, 0.0-0.7% of frames in the replay, the race after it and the highlight were off, and the longest stretch was 0-12 ms.
2. **The card queueing frames: ruled out.**
   - With vsync off a queue holds at most a few frames (about 12 ms at these rates), not 265 ms.
   - The card held no frame up in these runs.
3. **The processor's threads getting in each other's way: confirmed, as a lock.**
   - With no limit the presenter holds the lock that guards the card for about 4.5 ms of every 4.8 ms frame.
   - The game thread takes the same lock every tick (`fitPicture`), and Java's locks are not fair.
   - With no limit it waited over 1 ms 8-37 times every 2 seconds, longest 6-298 ms. At 120 fps it waited about 0 times.
   - Ticks pile up behind the lock and then run 5 at once: the freeze and the jump.
4. **Timer granularity: ruled out.**
   - With no limit the presenter uses no timer at all.
   - At 120 fps the high-resolution timer paces frames within 8.3 ± 0.5 ms.

A second, smaller stutter shows at **both** limits: for about 3 s at the start of the first race, ticks take longer than 53 ms. At 120 fps the profile reads 46.5 ms a tick plus 38 ms of run-ahead each, while the cars are bunched, so motion jumps 5 ticks at a time. That is in the race's own work and run-ahead, outside this change. The new flags catch it at both limits, and the report gives it with its numbers.

### The measure
- **What each frame shows.**
  - Each frame shows the race at a moment: the presenter's span start, plus `alpha` × the tick length, plus one tick with run-ahead (`PoseSpan.motionAt`). The game thread's own frames use the same formula.
  - Its *lag* is the time it went on screen minus that moment.
- **Even progress.** Moving evenly, the lag stays the same. A frame's distance from even progress is its lag minus the median lag of the frames shown within 0.5 s of it. The median, so that a moment's stutter does not move the reference.
- **Off.** A frame more than 4 ms off shows the race stopped or jumped. That is a quarter of a 60 Hz frame of movement.
- **Judged frames.** The frames within 100 ms of a part's start and end are not judged. The race pauses, resumes or cuts to the replay there: a resume shows one frame far off, and a replay's last tick holds still before the cut. All frames go in the CSV.
- **The flags.**
  - `motion uneven`: over 1% of judged frames are off (99% are not within 4 ms).
  - `motion hitch`: frames in a row are off for over 100 ms, the race hitch limit.
  - Thresholds from the runs above: the good parts at 120 fps are at 0-0.7% and 0-12 ms; no limit is at 12-90% and 400-1,000 ms.
- **The comparison.** `stutters with no frame limit, not at 120 fps` flags a race part that has a motion flag with no limit and none at 120 fps, the same part matched by name and by which time it came up.

### Why the game measures it, not a tool
| Tool | State | What it needs | Fits? |
|---|---|---|---|
| PresentMon 2.6.0 (Intel, console and service) | active | Reads Windows' graphics event trace (ETW). Needs admin, or membership of the Performance Log Users group. Display times mean something only for a window on a monitor | Not off screen; a run here was refused without admin |
| CapFrameX 1.9.2 | active | A GUI on PresentMon, installed with admin rights | Same as PresentMon, plus an install |
| OCAT 1.6.3 (AMD GPUOpen) | last release Sept 2023 | PresentMon inside, with an injected overlay, admin | Dated; same limits |
| NVIDIA FrameView | active | PresentMon-based, admin, installer | Same limits |

All four measure *when* frames reach the screen. The stutter here has even frame times and uneven content, and only the game knows what moment of the race a frame shows.

PresentMon 2.6 does have `AnimationError`/`AnimationTime` columns. Without the game's help it takes the animation's time from when the processor started each frame, and here that comes evenly while the race stands still, so they would miss this too. It can take the application's own simulation times: `--track_app_timing` reads the `Intel-PresentMon` ETW provider's AppSimulationStart events, and `--track_pc_latency` reads NVIDIA's PC Latency stats. The game would have to send those events.

That would make an on-screen twin of this measure: the race's moment against what the monitor showed. It is left for when PresentMon can run (below). For now the game measures it itself.

### How the harness runs
- `-Limits` defaults to `'unlimited','120'`.
- Your own run counts for its own limit. With your saved "unlimited" that means one extra run, at 120 fps, the same plan with `-Dnfm.fps=120`.
- `-Limits none` plays your settings alone. `-Split` plays split screen at each limit.
- Exit codes are unchanged: 0 pass, 1 flagged, 2 could not run, 3 inconclusive.

### 250+ fps and no stutter
- Recording costs three `long` stores per frame into a ring that already exists for frame times.
- The sums (sorting frame times and motion) used to run on the game thread at every change of part. They now run on a daemon thread (`nfm-framelog`), one at a time so the log keeps its order. A long race ending no longer stalls a tick.
- Only the game's close waits for its last sum, at most 5 s.
- Nothing new is drawn.

### Races stay identical
- No race code changes: only the presenter's bookkeeping and the frame log.
- `test\verify-races.ps1` must still match all 20 races, with the golden frames unchanged.

### Proof: the new live test before the fix
`test\live.ps1` (the defaults: your settings, which have no frame limit, then 120 fps), off screen at 1920x1080 fullscreen on the owner's PC, 21 Nov 2026.

| Race part | No limit: motion 99% / off in a row | 120 fps: motion 99% / off in a row | Flag |
|---|---|---|---|
| countdown | 231 ms / 947 ms | 6.9 ms / 102 ms | both uneven (the race-start stutter) |
| race | 220 ms / 692 ms | 34.5 ms / 212 ms | both uneven (the race-start stutter at 3.6 s) |
| instant replay | 306 ms / 635 ms | 2.5 ms / 0 ms | stutters with no frame limit, not at 120 fps |
| race (2nd time) | 237 ms / 503 ms | 3.0 ms / 10 ms | stutters with no frame limit, not at 120 fps |
| highlight | 254 ms / 483 ms | 3.0 ms / 12 ms | stutters with no frame limit, not at 120 fps |

In the race, the replay, the race after it and the highlight, the frame times of the same no-limit run passed the old pacing rules: 99% of frames within 6.7-8.9 ms, longest 11-29 ms. Only their frame rate (189-206 fps, under the 250 floor) was flagged before. The countdown was also flagged before, for frames the card held up while the race started.

## Risks / Trade-offs

- **Each live test takes about 2.5 minutes longer** (one more game run). `-Limits none` skips it.
- **Thresholds come from one PC.** They are stated in `test\live.ps1` and here, so they can be tightened. A 4 ms stray is well under what a 60 Hz screen shows. The 100 ms hitch matches the existing race hitch.
- **The game's own clock, not the monitor's.**
  - The measure stops where the frame is handed to the card. A stutter that only the display chain adds (compositor, scan-out) would not show.
  - That needs PresentMon on screen. See "Waiting for the owner".

## Waiting for the owner

These need taking the screen or admin rights, which this change must not do:

- **Admin, or the Performance Log Users group.** Either admin rights for PresentMon or CapFrameX, or the owner adding their account to the group: `net localgroup "Performance Log Users" <user> /add` in an admin prompt, then sign out and in again.
- **One race on screen.** With PresentMon recording, one fullscreen race on the monitor, at no limit and at 120 fps: `PresentMon-2.6.0-x64.exe --process_name java.exe --output_file <csv> --timed 60 --terminate_after_timed`.
  - Its `DisplayedTime` and `MsBetweenDisplayChange` columns show what the monitor showed.
  - Compare those with the game's own `motion` for the same run.
- **Later, if wanted.** The game could send PresentMon its race moment per frame (AppSimulationStart events, read with `--track_app_timing`). Then PresentMon's `AnimationError` would be this measure as the monitor saw it.
- **Until then**, the game's motion measure is what the live test judges.
