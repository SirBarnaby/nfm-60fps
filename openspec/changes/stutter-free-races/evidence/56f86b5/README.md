# Gates for 56f86b5

Every gate below ran on 23 Nov 2026, 17:10-17:22, from commit `56f86b5f35e4ba0bbcff0c1482fdc7523d5ee5fe` checked out with nothing changed beside it (`git status --porcelain` empty before the build; `build-info.json` is the one `build.ps1` wrote: `dirty` false, the jar's SHA-256). One after another, each a PowerShell of its own, its whole standard output in `<gate>.log` and its error output in `<gate>.err.log`. The PC was in use (another session's hidden test ran beside them); none of these gates measures time.

| Gate | Command | Result (its own last lines) |
| --- | --- | --- |
| Build | `build.ps1` | 7 shaders baked, AOT cache trained (38 MB); the error log holds only javac's three deprecation warnings and the JVM's note of its training options |
| Races | `test\verify-races.ps1` | `20 matched, 0 differed, 0 could not be run`; `golden frames: 95 same, 0 changed, 0 saved` |
| Races with rewinds | `test\verify-races.ps1 -Rollback 50` | `20 matched, 0 differed`; all 20 races `every value put back`; golden frames 95 same |
| Sound | `test\verify-sound.ps1` | `all 3 renders match the baseline` |
| Specs | `test\spec-check.ps1` | `16 areas, 78 requirements, every one checked` |
| Card's bands | `test\geometry-check.ps1 -Motion <smooth1-motion>,<motion1>` | `Band check: passed`: random polygons and the game's own 1,425 from twelve `-Dnfm.motiontest` scenes, no sample wrong away from the outline |

The motion scenes (103 MB) are not in the repository; `-Dnfm.motiontest=<dir>` writes them again. The live test (task 5.5) and the performance gate (5.4) are not here: they need a quiet PC with the display on.
