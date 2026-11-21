## 1. The game logs what each frame shows

- [x] 1.1 `PoseSpan.motionAt(alpha)`: the race moment a placed frame shows (tick start + alpha × tick, plus a tick with run-ahead); `ahead` copied with the span
- [x] 1.2 `GameSparker`: the presenter and the game thread's own frames pass that moment, and when the frame was chosen, to `FrameLog.shown`; menus and pictures pass `NO_MOTION`
- [x] 1.3 `FrameLog`: a motion ring per part; `Motion` works out each frame's distance from even progress (median lag over ±0.5 s), judged away from the part's edges (100 ms): 99%, worst, frames off by over 4 ms, longest stretch off in a row
- [x] 1.4 `frames.json` gets `motion`, the log line per part says it, `-Dnfm.framelog.csv=true` writes each race part's frames to a CSV
- [x] 1.5 Part sums run on a daemon thread (`nfm-framelog`), in order, so a long part ending stalls no tick; the game's close waits for its last

## 2. The live test judges it

- [x] 2.1 `test\live.ps1`: `motion uneven` (over 1% of judged frames off by over 4 ms) and `motion hitch` (off in a row over 100 ms) on race parts; motion columns in the tables
- [x] 2.2 `-Limits 'unlimited','120'` by default: a run of your settings at each limit your own run did not have; `-Limits none` for yours alone
- [x] 2.3 "No frame limit and 120 fps compared": each race part side by side, flagged `stutters with no frame limit, not at 120 fps`
- [x] 2.4 `AGENTS.md` live test row; `docs\solutions\measuring-stutter.md` (the measure, the tools, what waits for the owner)

## 3. Prove it

- [x] 3.1 `test\live.ps1` before the fix: the run with no limit is flagged on motion, the 120 fps run differs clearly (numbers in the report of this work)
- [ ] 3.2 After `no-frame-limit-stutter`: no limit and 120 fps both even outside the race start (that change's tasks)

## 4. Gates

- [ ] 4.1 `build.ps1` (full), `test\verify-races.ps1` (20 matched, golden frames unchanged), `test\perf.ps1`, `test\spec-check.ps1`
- [ ] 4.2 `test\live.ps1` and `test\live.ps1 -Split` on the owner's PC
- [ ] 4.3 `test\blind-review.ps1 -Base a5ab851` (once, over all of this work's changes)

## 5. Workflow follow-up

- [ ] 5.1 Archive the change (`openspec archive live-test-sees-real-stutter`) after `test\spec-check.ps1`; the project files' copy of `openspec\specs` is refreshed by the owner (this session cannot write the project files)
