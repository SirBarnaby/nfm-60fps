# PhysX debris

Switch: **PhysX debris** (`physx`, cookie `xtraphysx`), off by default until
1.0. Requirements: `openspec\specs\physx` ("PhysX to try out", "PhysX is
only visual"; Owner, 2026-11-27).

Code: `src\nfm\lit\features\physx\` (`PhysxFeature`, and `PhysWorld`, the
only class that touches PhysX). Library: NVIDIA PhysX 5 through
[physx-jni](https://github.com/fabmax/physx-jni) 2.9.0 (MIT; PhysX itself
BSD 3-clause): `lib\physx-jni-2.9.0.jar` and
`lib\physx-jni-2.9.0-natives-windows.jar`, which carries the DLL and loads
it itself; `lib\LICENSES-physx.txt` has both licences. Running on the
processor, on the game thread, with no worker threads.

## What it does

Loose pieces in the race that PhysX moves:

- **A hard hit** (60 or more of the game's hit force) knocks a panel off
  the corner that was hit, one more for every 45 harder, five at most:
  flat pieces 22 to 60 long in the car's two main colours.
- **A wasted car** throws nine chunks and panels from all round itself,
  some burnt black.
- **Every explosion** (`Weather.blast`: dynamite, bombs, exploding wrecks)
  throws twelve chunks, burnt or of the ground's colour.
- **In snow**, the lumps a car breaks out of the bed (`Weather.lump`)
  tumble as white blocks that hardly bounce.

They fall under the game's gravity (2.2 units a tick, a tick), bounce off
the ground and the stage (raised floors, walls and ramps, from the stage's
own collision boxes in `RaceView.piece*`), turn over, come to rest, and a
car that drives into them shoves them away: each car stands in PhysX's
scene as a box moved to where the game has it, reaching down to where its
wheels stand. A piece lies for 28 seconds (snow 14) and thins out over its
last second and a half; 160 at most, the oldest making way. Pieces within
7000 units of the eye are drawn as boxes, lit from above, their faces
moving through `Canvas.moving` so they are smooth between ticks.

## Only a look

PhysX never moves a car and the race never reads it. The extra reads the
race's copy (`RaceView`) after each real tick and steps PhysX twice by half
a tick, so a recorded race plays the same with it on (`cloud/check.sh
looks`, `test\verify-races.ps1 -Features looks -NoGolden`). What the
pieces do need not come out the same twice ("its results of identicalness
are not 100% important"): PhysX is not part of the race's deterministic
maths, and it is not in the race state, so run-ahead and rollback leave it
alone.

Where the library or its DLL is missing or does not load, the extra says so
once in the log (`[extras] physx: PhysX could not be started...`) and does
nothing; the game goes on.

`-Dnfm.physx.show=true` (with `-Dnfm.physx=on`) throws seven pieces up
ahead of the player's car every two seconds, for a screenshot.

## Checks

`cloud/check.sh extras physx` (`test\extras-check.ps1 physx`): PhysX
starts; a hit of 160 knocks three panels off, they fly, turn over, fall
and twelve seconds later lie at rest on the ground; a car driven through a
piece lying still shoves it away; a piece comes to rest on a raised floor,
one meets a ramp above the ground, a wall stops one; a wasted car, an
explosion and two lumps of snow throw 9, 12 and 2 pieces; they go after
14 and 28 seconds; never more than 160; a piece is drawn as the faces of a
box turned to the eye and not from too far; and with pieces flying about
the cars for 60 ticks the race's copy is as it was.

Seen so far only in the software renderer's hidden screenshots in the
cloud (Linux); not yet on Windows, on the graphics card, or driven.
