## 1. The fix

- [x] 1.1 `Medium.setView` moves `ground` with the camera's `y`
- [x] 1.2 `GameUi.drawSelectCar`, used by `carselect`

## 2. The check

- [x] 2.1 `Host.carSelect` (`SessionHost`): a car of the car select drawn into a picture of a given size, everything put back after
- [x] 2.2 `GoldenFrames.check(group, name, picture)` and `sameAs`, with the edge rule (both pictures have an edge there, at most 1 pixel in 1,000)
- [x] 2.3 `Verifier.checkCarSelect` after the races: cars 0, 6 and 12 at 1280x720 (golden, `test\golden\carselect\`) and at 1920x1080, 2560x1080, 2560x1440 and 1280x960
- [x] 2.4 Shown failing without the fix (12 of 12 sizes, 3,640-9,691 pixels) and passing with it; 20 races matched, no race golden frame changed

## 3. Screenshots

- [x] 3.1 Before (a5ab851) and after, from hidden runs, and v1.0's parent (a62ae9c^, worktree, run hidden) in `docs\reviews\2026-11-21-car-select-shadow*.png`

## 4. Gates

- [x] 4.1 `build.ps1` (full), `test\verify-races.ps1` (20 matched, golden frames 95 same), `test\spec-check.ps1` (11 passed); `test\perf.ps1` flagged its 99% gap (9.8 ms against the 17 Nov baseline's 6.8), but an interleaved A/B with a5ab851, 4 runs each, showed no slowdown (equal or better in 3 of 4 pairs; this laptop swings about 25% run to run); baseline left as it is
- [ ] 4.2 `test\live.ps1` and `test\live.ps1 -Split` on the owner's PC
- [ ] 4.3 `test\blind-review.ps1 -Base a5ab851` (once, over all of this work's changes)

## 5. Workflow follow-up

- [ ] 5.1 Archive the change after `test\spec-check.ps1`; the project files' copy of `openspec\specs` is refreshed by the owner
