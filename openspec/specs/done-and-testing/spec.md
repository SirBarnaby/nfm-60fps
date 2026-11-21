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
