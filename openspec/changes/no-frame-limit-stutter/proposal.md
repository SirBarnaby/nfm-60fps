## Why

The owner, 21 Nov 2026: "If I lock my fps at 120fps, then the game is smooth. If I ran it at unlocked, I got crazy stutters."

The live test now sees it (change `live-test-sees-real-stutter`). With no frame limit the race stands still for 0.3-0.9 s and then jumps 5 ticks at once, in every race part. At 120 fps it does not.

This change touches no spec: it makes the game meet the existing requirement "No stuttering, ever" (`performance`) with no frame limit. Nothing is MODIFIED or REMOVED, and no feature the owner did not ask for is added.

**Cause, measured** (see the design of `live-test-sees-real-stutter`):
- The presenter thread draws race frames and holds `presentLock`, the lock that guards the card and the window, for about 4.5 ms of every 4.8 ms frame. With no frame limit it takes the lock again at once.
- The game thread takes the same lock at the start of every tick (`fitPicture`), even when nothing it guards changes.
- Java's locks are not fair. With no limit the game thread waited over 1 ms 8-37 times every 2 seconds, up to 298 ms at a time, and ticks piled up and then ran 5 at once.
- At 120 fps the presenter sleeps between frames, the lock is free most of the time, and the game thread hardly ever waited.

## What Changes

- **`fitPicture` does not take `presentLock` when nothing it guards changes.**
  - Only the game thread writes those values, so it can tell without the lock.
  - It takes the lock only when the picture's size, the split or its side changes.
- **Other threads get the lock in turn.**
  - A thread other than the presenter counts itself (`presentLockWanted`) just before it takes `presentLock`, and stops counting the moment it has it: the game thread in `fitPicture` and `openGpu`, the window's own thread (AWT) in `addNotify` and `removeNotify`.
  - Before taking the lock for a race frame, the presenter lets such waiters in first, for at most 20 ms. A window resize or a fullscreen switch is no longer starved either.
- **Not changed:** the race, the frame limit, how frames are placed (the 13 ms carry-on was tried at 26 ms and showed no clear gain), and input lag.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: performance "No stuttering, ever" already asks for this; checked by test\live.ps1's motion flags -->

Specs touched (from `openspec list --specs`): none changed. `performance` is the requirement this meets; `done-and-testing` "Stutter with no frame limit is caught" is how it is checked.

## Impact

- `src\nfm\lit\GameSparker.java` only: `fitPicture`, `openGpu`, `addNotify`, `removeNotify`, `showOnGpu` for presenter frames, and the counter and its helpers.
- Race code untouched: `test\verify-races.ps1` must match all 20 races, with no golden frame changed.
