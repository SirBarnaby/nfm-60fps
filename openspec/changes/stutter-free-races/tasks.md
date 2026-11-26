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
- [x] 4.5 The countdown's frame rate: polygons that cross or touch themselves cut in bands for the card (`cutLines`, `bandPieces`, `ROLE_ALONG`), no stencil; flat ones keep it. Projection test as before (worst 0.0129% against 0.0103%); draw calls a scene 1-3 against 130-387
- [x] 4.6 The countdown's start: a race's frames timed from its first scene, the ticks due from there (decision 9, 3a8fab1)
- [x] 4.7 The countdown at 120 fps: frames placed for the moment they reach the screen, apart for frames that send the card a new scene (decision 10, 3a8fab1)
- [x] 4.8 No warm-up ticks in the countdown; up to 500 ms while the music loads, once a run (decision 2, 3a8fab1)
- [x] 4.9 Split screen's results took the item under a resting mouse every tick, so the live split test raced again instead of going to Main Menu; hovering now counts only when the mouse moves, as in the pause menu (907de35)
- [x] 4.10 The warm-up's unseen draw with the HUD and the white flash, as a race frame draws it; split screen's both panes too (decision 11, 12febb0)
- [x] 4.11 Frames placed for the screen by their place after a new kept scene: split screen's two frames after one waited on the card (decision 10, 12febb0)
- [x] 4.12 Split screen's countdown portrait tint redrew both panes whole: a pane replays inside the clip it is given (decision 12, 12febb0)
- [x] 4.13 Split screen's results and the pause menu came up with a 56-98 ms tick: the greyed race read from the picture's buffer, greyed with whole numbers (decision 13, ba816fd)
- [x] 4.14 Blind review finding 1: a screenshot mid-race restarted the presenter and moved the tick clock; a race's first scene is now one after a menu's frame (decision 14)
- [x] 4.15 Blind review finding 2: band crossings drifted apart on the card; placed where the edges cross in each frame (`ROLE_CROSS`, decision 15)
- [x] 4.16 Second blind review finding 1: a crossing that left its band punched holes, and a piece could fold; a polygon whose bands would not hold until the next tick goes to the stencil, pieces in strips (decision 16), checked by `test\geometry-check.ps1`
- [x] 4.17 Third blind review finding 2: frames placed for the next tick's span before its scene comes drew the last scene's bands, checked for its own span only; they fill band polygons with the stencil (decision 17), checked by `geometry-check.ps1`'s scenes placed for another span
- [x] 4.18 Third blind review finding 1: resuming from the pause menu moved the tick clock on the card; a race going on after its pause menu keeps its ticks' times (decision 18)

## 5. Gates

- [x] 5.1 Interleaved A/B, warm-up on against none, 3 rounds each, hidden: the countdown's first scene and card part quicker in 3 of 3 pairs (kept 38-68 against 56-72 ms, card 29-38 against 44-52, on a busy PC); the rest within the noise
- [x] 5.2 `build.ps1` (full: the runtime rebuilt with the new option, AOT trained), again from bc50baa
- [x] 5.3 `test\verify-races.ps1`: 20 matched, golden frames 95 same; `-Rollback 50`: 20 matched, every rewind put back
- [ ] 5.4 `test\perf.ps1` on a quiet PC with an interleaved A/B against afa93ec (baseline left as it is; at 17:20 the gate ran beside another game's test runs, 74% processor: 92-181 fps, not judged); `test\spec-check.ps1`: 11 passed
- [ ] 5.5 `test\live.ps1` (no limit and 120 fps compared, supersampling off) and `test\live.ps1 -Split`, on a quiet PC, until no flag is left
- [x] 5.6 `test\blind-review.ps1 -Base afa93ec` (docs\reviews\2026-11-23-1533-ddc18c0.md, needs-attention): findings 1 and 2 fixed (4.14, 4.15); 3 is 5.4 and 5.5; 4 (split results and the pause menu drawn on the processor) was so before this change and is no stutter any more (4.13); 5 is the extras framework's specs, another change. Again at 1098c71 (docs\reviews\2026-11-23-1629-1098c71.md, needs-attention): finding 1 fixed (4.16); 2 is 5.4 and 5.5; 3 is the first review's 4; 4 (outputs not tied to the source) is answered by 5.9. Again at ec9064e (docs\reviews\2026-11-23-1734-ec9064e.md, needs-attention, 9 findings): 1 and 2 fixed (4.18, 4.17); 3 is the first review's 4; 4 is 5.4 and 5.5, and the gates from this change's last commit (5.10); 5-9 are the extras' and the realism change's, not this change's
- [x] 5.7 Cleanup pass (four reviews: reuse, simpler code, wasted work, depth; applied where the behaviour stays, 13c3f7a): the card's bands built 8% quicker with the same bytes (every scene built both ways in the same hidden runs: 1,000+ for the card, 3,800 on the processor); again `build.ps1`, `verify-races.ps1` 20 matched and golden frames 95 same, `-Rollback 50` 20 matched with every rewind put back, `spec-check.ps1` 11 passed. The lessons in `docs\solutions\stutter-at-race-start.md`
- [x] 5.8 After 4.14 and 4.15, from the working tree then committed as 1098c71 (the record named 8caf7b6, which lacks them): `build.ps1` (shaders baked, AOT trained), `verify-races.ps1` 20 matched and golden frames 95 same, `-Rollback 50` 20 matched with every rewind put back, `verify-sound.ps1` all 3 renders match, `spec-check.ps1` 16 areas, 78 requirements. Their outputs were not kept (the second review's finding 4)
- [x] 5.9 After 4.16, from its commit (56f86b5) with nothing changed beside it: `build.ps1` (shaders baked, AOT trained), `verify-races.ps1` 20 matched and golden frames 95 same, `-Rollback 50` 20 matched with every rewind put back, `verify-sound.ps1` all 3 renders match, `spec-check.ps1` 16 areas, 78 requirements, `geometry-check.ps1` passed with the game's polygons from twelve scenes; their whole outputs, with `build-info.json` (`dirty` false), in `evidence\56f86b5\`
- [ ] 5.10 After 4.17 and 4.18, from their commit with nothing changed beside it: the gates of 5.9, their whole outputs in `evidence\<commit>\` with `build-info.json`

## 6. Workflow follow-up

- [ ] 6.1 Archive the change after `test\spec-check.ps1`; the project files' copy of `openspec\specs` is refreshed by the owner
