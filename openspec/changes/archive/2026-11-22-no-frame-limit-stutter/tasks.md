## 1. The lock

- [x] 1.1 `fitPicture`: return before `presentLock` when the sizes, the split and its side it would write are what they are already
- [x] 1.2 `presentLockWanted` with `wantPresentLock`/`gotPresentLock` around `synchronized (presentLock)` in `fitPicture`, `openGpu` (both), `addNotify`, `removeNotify`
- [x] 1.3 `letPresentLockWaitersIn` before the presenter takes the lock for a race frame (`showOnGpu`), at most 20 ms

## 2. Measure

- [x] 2.1 Hidden runs at 1920x1080 with no limit and at 120 fps (`-Dnfm.profile=true -Dnfm.framelog.csv=true`): replay, race after it and highlight within 2.5-5.5 ms of even with no limit (were 200-430 ms)
- [x] 2.2 A/B of the motion carry-on (13 ms against 26 ms), 2 runs each: no clear gain, left at 13 ms

## 3. Gates

- [x] 3.1 `build.ps1` (full), `test\verify-races.ps1` (20 matched, golden frames unchanged), `test\spec-check.ps1` (11 passed); `test\perf.ps1` flagged its 99% gap (9.8 ms against the 17 Nov baseline's 6.8), but an interleaved A/B with a5ab851, 4 runs each, showed no slowdown (equal or better in 3 of 4 pairs; this laptop swings about 25% run to run); baseline left as it is
- [ ] 3.2 `test\live.ps1` (no limit and 120 fps compared) and `test\live.ps1 -Split` on the owner's PC
- [ ] 3.3 `test\blind-review.ps1 -Base a5ab851` (once, over all of this work's changes)

## 4. Workflow follow-up

- [ ] 4.1 Archive the change after `test\spec-check.ps1`; the project files' copy of `openspec\specs` is refreshed by the owner
