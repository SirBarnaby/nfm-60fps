# Done and testing

## Purpose
What "done" means for a change and how it is proven: built as described, nothing else broken, and tested the way the owner actually plays, so "the tests passed" matches what the owner sees.

## Requirements

### Requirement: Done means built as described and nothing broken
A change SHALL count as done only when it does what the owner described, works together with the rest of the game as you would expect, and breaks nothing else. (Owner, 2026-11-21, R1 Q26, R2 B14.)

#### Scenario: Check: the gates
- **WHEN** a change is reported done
- **THEN** `build.ps1`, `test\verify-races.ps1`, `test\perf.ps1` and `test\spec-check.ps1` have passed, and the report names their results

### Requirement: Some tests are realistic
Hidden test runs alone SHALL NOT be enough to call a change done. The live test SHALL run the real game on the owner's PC with the owner's own settings, because stutters and low frame rates show up there. A report SHALL NOT claim the game runs well unless the live test says so. (Owner, 2026-11-21, R2 B14, B15.)

#### Scenario: Check: live test in every report
- **WHEN** a change that touches the game is reported done
- **THEN** the report includes `test\live.ps1` results from the owner's PC (and `-Split` for anything in a race), with frames a second and flags per part

### Requirement: Every requirement has a check
Every requirement in these specs SHALL have at least one scenario whose name starts with `Check:` and says how it is checked: a command in `test\`, a test property, or a step for the owner. Checks SHALL be automatic where possible. (Owner, 2026-11-21, R2 B23.)

#### Scenario: Check: spec check
- **WHEN** `test\spec-check.ps1` runs
- **THEN** it exits 0: every spec is valid and every requirement has a `Check:` scenario whose `test\` files exist

### Requirement: Specs a blind reviewer can judge
Specs SHALL be written in plain language, so a reviewer with no context (such as the Codex adversarial review, which only sees the result) can judge the game against them. (Owner, 2026-11-21, R1 Q32, R2 A13.)

#### Scenario: Check: a review with no context
- **WHEN** a reviewer is given only the specs and the built game
- **THEN** they can tell for each requirement whether it is met, without reading code or chat history

### Requirement: A blind Codex review rates every change
Before a change is reported done, a Codex review SHALL rate it with no context from whoever made it: it sees only the owner's specs and the repo, never the agent's explanation or chat. The report SHALL give its verdict and its findings. (Owner, 2026-11-21, R1 Q32, R2 B13; set up at the owner's request the same day.)

#### Scenario: Check: blind review
- **WHEN** a change is reported done
- **THEN** `test\blind-review.ps1` has run on its commits, its review is in `docs\reviews\`, and the change's report gives the verdict and findings

### Requirement: Stutter with no frame limit is caught
The live test SHALL catch the stutter the player sees, not only frames that come late. A race part that shows the race standing still and then jumping SHALL fail it, even when the frames themselves come evenly. The live test SHALL play the race with no frame limit and at a 120 fps limit. A stutter with no frame limit that the 120 fps limit does not have SHALL fail it. (Owner, 2026-11-21: smooth when locked at 120 fps, "crazy stutters" unlocked, and the test harness did not detect it.)

#### Scenario: Check: no frame limit and 120 fps compared
- **WHEN** `test\live.ps1` runs on the owner's PC (by default it plays the owner's settings with no frame limit and at 120 fps)
- **THEN** a race part that shows the race moving unevenly with no limit but evenly at 120 fps is flagged `stutters with no frame limit, not at 120 fps`, the report compares the two per race part, and the test exits 1

#### Scenario: Check: only the frame limit differs between the two runs
- **WHEN** `test\live.ps1` compares the run with no frame limit and the one at 120 fps
- **THEN** both runs race the same seed (`-Seed`, `-Dnfm.seed`), and it compares them only when each is held by its own limit alone, vsync is the same in both and both raced the same stage and seed; otherwise the report says `not comparable` and why, and the run is inconclusive

#### Scenario: Check: uneven motion fails a race part
- **WHEN** `test\live.ps1` runs and a race part has over 1% of its frames more than 4 ms off even progress, or frames in a row off it for 100 ms or longer, its first frames included
- **THEN** that part is flagged `motion uneven` or `motion hitch` and the test exits 1, whatever its frame times were

#### Scenario: Check: a race part with no motion data fails
- **WHEN** `test\live.ps1` runs and `frames.json` has no motion for a race part (a build from before it was measured, or a frame log that lost it)
- **THEN** that part is flagged `motion not measured` and the test exits 1, and the no-limit comparison says the part was not compared
