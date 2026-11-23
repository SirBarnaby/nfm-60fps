## 1. Measure

- [x] 1.1 Tick trace in `FrameLog` (`ticks-NN-part.csv` with `-Dnfm.framelog.csv=true`): due, late, race step, tick, run-ahead, handed over, kept scene, card, done
- [x] 1.2 Hidden runs of the live test's seed with a stack sampler: causes found for the early freeze, the countdown's start, the uneven race and "3"

## 2. Field access

- [x] 2.1 `FieldSlot`: a `VarHandle` per field, exact-type calls, primitive values as raw bits
- [x] 2.2 `StateCopy` and `RaceSnapshot` (the run-ahead copy) through `FieldSlot`
- [x] 2.3 `RaceState` (the replay recorder's per-tick values) and `PlayerHud` (split screen's HUD swap) through `FieldSlot`
- [x] 2.4 The recorder's layout from the start state's names made once (`RaceState.layout(view, names)`)

## 3. Before the race

- [x] 3.1 `playAndPutBack` shared by the run-ahead and the warm-up
- [x] 3.2 Warm-up while the music loads (`warmUpWhileLoading`): the countdown's scene drawn unseen once a stage, the first race ticks for up to 200 ms once a run
- [x] 3.3 The rest in measured spare time (`warmUpInSpareTime`), never in split screen or a race
- [x] 3.4 The rollback self-test checks every warm-up tick (`RaceSession.checksWarmUp`, `warmUpLeftovers`)
- [x] 3.5 `-XX:+DisableExplicitGC` in `build.ps1`'s runtime options

## 4. The countdown and the race

- [x] 4.1 The portrait tint: `syncBackBuffer(region)`, polygons outside the clip skipped, portraits decoded while the music loads, integer blend
- [x] 4.2 `PoseSpan.MAX_CARRY_ON` 0.25 to 0.5 after an A/B
- [x] 4.3 The stall about 1.7 s in: `Plane.d` compiled before `EffectTrails` was initialized (JIT log: `uninitialized` trap at the debris path); `Plane` initializes `EffectTrails` and `Glow`
- [x] 4.4 Temporary A/B and check code removed (`nfm.warmup`, `[warmup]` lines, `portraitCheck`); a log line when a race begins before the warm-up is done
- [x] 4.5 The countdown's frame rate: polygons that cross or touch themselves cut in bands for the card (`bandsOnCard`, `ROLE_ALONG`), no stencil; flat ones keep it. Projection test as before (worst 0.0129% against 0.0103%); draw calls a scene 1-3 against 130-387
- [x] 4.6 The countdown's start: a race's frames timed from its first scene, the ticks due from there (decision 9, 3a8fab1)
- [x] 4.7 The countdown at 120 fps: frames placed for the moment they reach the screen, apart for frames that send the card a new scene (decision 10, 3a8fab1)
- [x] 4.8 No warm-up ticks in the countdown; up to 500 ms while the music loads, once a run (decision 2, 3a8fab1)
- [x] 4.9 Split screen's results took the item under a resting mouse every tick, so the live split test raced again instead of going to Main Menu; hovering now counts only when the mouse moves, as in the pause menu (907de35)
- [x] 4.10 The warm-up's unseen draw with the HUD and the white flash, as a race frame draws it; split screen's both panes too (decision 11, 12febb0)
- [x] 4.11 Frames placed for the screen by their place after a new kept scene: split screen's two frames after one waited on the card (decision 10, 12febb0)
- [x] 4.12 Split screen's countdown portrait tint redrew both panes whole: a pane replays inside the clip it is given (decision 12, 12febb0)

## 5. Gates

- [x] 5.1 Interleaved A/B, warm-up on against none, 3 rounds each, hidden: the countdown's first scene and card part quicker in 3 of 3 pairs (kept 38-68 against 56-72 ms, card 29-38 against 44-52, on a busy PC); the rest within the noise
- [x] 5.2 `build.ps1` (full: the runtime rebuilt with the new option, AOT trained), again from bc50baa
- [x] 5.3 `test\verify-races.ps1`: 20 matched, golden frames 95 same; `-Rollback 50`: 20 matched, every rewind put back
- [ ] 5.4 `test\perf.ps1` on a quiet PC with an interleaved A/B against afa93ec (baseline left as it is; at 17:20 the gate ran beside another game's test runs, 74% processor: 92-181 fps, not judged); `test\spec-check.ps1`: 11 passed
- [ ] 5.5 `test\live.ps1` (no limit and 120 fps compared, supersampling off) and `test\live.ps1 -Split`, on a quiet PC, until no flag is left
- [ ] 5.6 `test\blind-review.ps1 -Base afa93ec`
- [x] 5.7 Cleanup pass (four reviews: reuse, simpler code, wasted work, depth; applied where the behaviour stays, 13c3f7a): the card's bands built 8% quicker with the same bytes (every scene built both ways in the same hidden runs: 1,000+ for the card, 3,800 on the processor); again `build.ps1`, `verify-races.ps1` 20 matched and golden frames 95 same, `-Rollback 50` 20 matched with every rewind put back, `spec-check.ps1` 11 passed. The lessons in `docs\solutions\stutter-at-race-start.md`

## 6. Workflow follow-up

- [ ] 6.1 Archive the change after `test\spec-check.ps1`; the project files' copy of `openspec\specs` is refreshed by the owner
