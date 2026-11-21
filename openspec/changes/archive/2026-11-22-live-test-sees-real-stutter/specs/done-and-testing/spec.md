## ADDED Requirements

### Requirement: Stutter with no frame limit is caught
The live test SHALL catch the stutter the player sees, not only frames that come late. A race part that shows the race standing still and then jumping SHALL fail it, even when the frames themselves come evenly. The live test SHALL play the race with no frame limit and at a 120 fps limit. A stutter with no frame limit that the 120 fps limit does not have SHALL fail it. (Owner, 2026-11-21: smooth when locked at 120 fps, "crazy stutters" unlocked, and the test harness did not detect it.)

#### Scenario: Check: no frame limit and 120 fps compared
- **WHEN** `test\live.ps1` runs on the owner's PC (by default it plays the owner's settings with no frame limit and at 120 fps)
- **THEN** a race part that shows the race moving unevenly with no limit but evenly at 120 fps is flagged `stutters with no frame limit, not at 120 fps`, the report compares the two per race part, and the test exits 1

#### Scenario: Check: uneven motion fails a race part
- **WHEN** `test\live.ps1` runs and a race part has over 1% of its frames more than 4 ms off even progress, or frames in a row off it for over 100 ms
- **THEN** that part is flagged `motion uneven` or `motion hitch` and the test exits 1, whatever its frame times were
