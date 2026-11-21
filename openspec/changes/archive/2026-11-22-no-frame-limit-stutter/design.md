## Context

**The presenter thread** (`nfm-presenter`) draws race frames from kept scenes and holds `presentLock` while it hands a frame to the card (`showOnGpu`). The lock makes sure the window and the card are not destroyed or remade while the presenter uses them, and it guards `windowShown`, `windowGeneration` and the picture's sizes.

With no frame limit, `pace()` only calls `Thread.yield()`, so the presenter holds the lock about 94% of the time and takes it again straight away.

**The game thread** runs a tick every 53 ms. It started each tick with `fitPicture`, which took `presentLock` to write the drawing size, the split-screen flags and, on a change, the picture sizes. It did this every tick, even when nothing changed. Java's monitors are not fair: a waiting thread is woken when the lock is let go, but a thread that is running and asks again usually wins.

Measured in hidden runs with `-Dnfm.profile=true` and a temporary count of the game thread's waits:

| | Waits over 1 ms per 2 s, in a race | Longest wait |
|---|---|---|
| No limit | 8-37 | 6-298 ms |
| 120 fps | 0-3 (after the first second) | 0.4-5 ms |

## Goals / Non-Goals

**Goals:** With no frame limit, the race moves as evenly as at 120 fps (the live test's motion measure). Frame rate, input lag and races stay the same.

**Non-Goals:**
- The slow start of the first race. For about 3 s at both limits, ticks take 23-47 ms plus 15-38 ms of run-ahead, so motion jumps. That is the race's own work and run-ahead's, which this change must not touch; the report gives it with its numbers.
- The frame rate under 250 (see Risks).

## Decisions

1. **Skip the lock when nothing changes** (`fitPicture`).
   - `drawWidthNow`, `drawHeightNow`, `SplitRace.pictureSplit`/`pictureSide` and the picture sizes are written only by the game thread, in `fitPicture`. So the game thread can compare them with what it wants without the lock.
   - When they match, there is nothing to write and the lock is not needed. That is every tick except the ones where the window or the split changes.
   - *Alternative:* a fair `ReentrantLock`. Rejected: `presentLock` also uses `wait`/`notifyAll` (`removeNotify`, `closeGpu`), and fairness would cost the presenter on every frame for a lock nobody else wants.
2. **A courtesy gate for the rare times others need it.**
   - Threads other than the presenter increment `presentLockWanted` right before `synchronized (presentLock)` and decrement it as the first thing inside. Entering a monitor cannot be interrupted, so every increment is matched.
   - Before taking the lock for a race frame, the presenter yields while the count is above 0, for at most `PRESENT_LOCK_COURTESY_NANOS` (20 ms). A count that somehow stayed up can never stop drawing.
   - Nobody waits for the lock in a normal frame, so the cost there is one atomic read.
3. **The motion carry-on stays at 0.25 of a tick (13 ms).**
   - With the lock fixed, some ticks still reach the presenter 13-28 ms after they were due, when the processor is busy: the owner's PC is a 4-core i7-8550U laptop chip, and drawing with no limit takes some of its power budget.
   - Frames then stand still for a few ms at a tick.
   - An A/B of four hidden runs at 13 ms and at 26 ms carry-on showed no clear difference, the noise between runs being larger. Longer carry-on also means larger corrections when the tick comes, so it is left as it was.

### 250+ fps and no stutter
- Nothing new is drawn and the presenter's frame is unchanged.
- Measured: with no limit the replay, the race after it and the highlight went from 99% of frames within 203-306 ms of even to 2.5-5.5 ms. At 120 fps they are at 2.4-3.2 ms.
- The frame rate with no limit stayed at 281-306 fps in hidden runs.

### Races stay identical
No race code changes. `test\verify-races.ps1`: 20 matched, golden frames unchanged.

## Risks / Trade-offs

- **The race start still stutters at both limits** (Non-Goals). The live test flags it, so it is not hidden.
- **On a busy or hot laptop, some ticks still come late** and motion stands still for a few ms at a tick, more with no limit than at 120 fps. The live test judges that too.
- **Frame rate under 250 in live runs.**
  - The live test's no-limit runs on this PC show 190-206 fps in race parts, under the 250 floor.
  - That was so before this change and is flagged separately.
