## Need For Madness: LIT

Need For Madness: LIT (NFML) is a decompilation and subsequent improvement of Need For Madness 2, developed by Radicalplay.com.

It is designed to provide a smooth gameplay experience for the vanilla game (with tweaks - see below) as well as a solid foundation for modifying and building upon for prospective hackers.
In order to achieve this, much of the original source code needs rewriting. While some work has been done, much more needs doing, so please feel free to contribute!

Some of the work already done includes:
 - Providing a complete, playable, version of NFM2.
 - Improving 2D graphics rendering to not rely on a hardcoded screen resolution (however, dynamic scaling is not yet supported).
 - Improving the fluency and look & feel of the overall game.
 - Rendering at 720p by default.
 - Providing a much more reasonable source tree, grouping the source files into subfolders where appropriate.

However, much more work needs doing. There are still hardcoded values all over the code relating to rendering, AI, stages, cars, and more. 
Ideally, all of these subsystems should be decoupled from each other in order to facilitate a much smoother hacking experience.

Special thanks to:
 - cravxx (Kaffeinated) for the initial decompilation and tidy-up of NFM2.
 - G6 for providing some helpful hacks to improve stage select visuals.
 - stants12 (oteek) for further building upon and improving the original decompilation.
 - FlameCharge (Jacherr) for fixing UI to work properly at 720p resolutions.
 - Everyone else who offered improvements, fixes, and anything else!

Need for Madness 2 (c) Radicalplay.com.
All changes and improvements (c) their original authors. See @author documentation fields or other comments where appropriate.
## Building and running on Windows

1. Install a JDK 25 or newer (any vendor). `build.ps1` finds it via `JAVA_HOME` or `%USERPROFILE%\.jdks`, or pass `-Jdk <path>`.
2. Run `powershell -ExecutionPolicy Bypass -File build.ps1`. It produces `madness.jar` and a private Java runtime in `runtime\`. The runtime is made again only when the JDK or its settings change (`runtime\nfm-runtime.txt` records them); delete the folder to force it.
3. Double-click `madness64.exe`. No system-wide Java install is needed to play once `runtime\` exists.

`madness.exe` is the 32-bit launcher and needs a 32-bit runtime, so use `madness64.exe` on 64-bit Windows.

The game no longer depends on the Applet API (removed in Java 26) or `Thread.stop()` (which throws since Java 20), so it builds and runs on current JDKs.

## The frame loop

Simulation and presentation are decoupled. The world is stepped at the original cadence (one tick per 53 ms during races, replays and highlights, one per 40 ms in menus), so physics, AI and every frame-counted timer behave exactly as before. Races, instant replays and the post-race highlights are redrawn as often as the frame limit allows, using car, wheel and camera transforms interpolated between the last two ticks. The HUD and replay captions are rendered once per tick into their own layer and composited on top, keeping their blink and pause timers on the legacy cadence.

If drawing a frame takes longer than a tick, the loop runs the ticks it owes before drawing again, so a slow frame lowers the frame rate but never slows the race down.

Between frames the game thread sleeps on Windows' high-resolution waitable timer (Windows 10 1803 and later), which wakes it within a fraction of a millisecond. `Thread.sleep()` wakes on the system timer tick, up to 2 ms late, so the loop used to spin through the last 2 ms before every frame, keeping a core busy and taking turbo power from the thread that draws. Where the timer is not available the old way is used.

The car physics (`Madness.drive`) and the AI (`Control.preform`) are each over HotSpot's 8,000-byte limit for compiling a method, so by default they would only ever be interpreted. The runtime that `build.ps1` makes carries `-XX:-DontCompileHugeMethods`, which lets them be compiled: in a profile of two races their share of CPU time fell from about 9% to 2%. The race results are the same either way, since compiled and interpreted Java compute identical results.

Drawing is not free of side effects in this engine: it advances the repair hoop sequence, dust, wreck flames, flying debris and the checkpoint flicker, and the race reads the distances it works out (a car out of view is repaired at once, a car in view waits for the hoop). So every tick draws the scene once into an invisible surface, at the point where the original drew it, and every frame that reaches the screen is drawn and then rolled back. The frame rate, a minimised window or a run with no window at all therefore never speeds up an animation or changes how a race plays out. Cosmetic effects take their random numbers from a stream of their own, so they cannot shift the AI's choices either.

## Run-ahead: one tick less input lag

Drawing between the last two ticks is smooth, but it shows the race as it was up to one tick (53 ms) ago. With run-ahead, which is on by default, every race frame is drawn between the last tick and the next one instead: when a tick lands or a driving key changes, the game plays the next tick ahead of time with the keys held at that moment, notes where the cars and the camera end up, and puts the whole race back exactly as it was (see "Rewinding a race" below). The real tick then reads the same keys and lands on the same spot, because the race is deterministic, so a key press shows on screen a full tick sooner and nothing jumps.

The tick played ahead is never heard, never recorded and never drawn into the HUD, and it cannot change the race: races recorded in real time with run-ahead firing about 19 times a second replay tick for tick, sounds included. A run-ahead costs about 5 ms on a laptop i7-8550U (capture, one tick, restore), once per tick and once per key change. Only a press between two ticks that the prediction could not know (Enter, for example) makes a frame correct itself; in testing, 1 of 3,506 predictions missed.

It applies while racing, not in the intro, the countdown, replays or menus. Turn it off or on in the developer console with `runahead off` / `runahead on` (saved), or for one run with `-Dnfm.runahead=false`.

## Window size and fullscreen

The window can be resized freely, down to half of 1280x720. The game still draws at 1280x720 and the picture is scaled to the largest size that fits the window, keeping its shape, with black bars where the shape differs. The mouse follows the scaled picture. **F11** or **Alt+Enter** switches between the window and borderless fullscreen on the monitor the window is on.

Scaling uses a smooth (bilinear) filter computed on all CPU cores: about 2 ms a frame for 1920x1080 and under 4 ms for 3840x2160 on a laptop i7-8550U. Java's own filter would do the same picture in about 25 ms, because its Direct3D path refuses Intel graphics and falls back to software. To show the picture pixel for pixel in the middle of the window instead, type `scale off` in the developer console (`scale fit` to undo; saved), or start with `-Dnfm.scale=off`.

## Frame rate limit

By default the frame rate matches the refresh rate of the monitor the window is on. To change it, open the developer console (it appears on the main menu) and type:

| Command | Effect |
| --- | --- |
| `fps` | Show the current limit. |
| `fps auto` | Match the monitor's refresh rate (default). |
| `fps unlimited` | No limit: draw as fast as the CPU allows. |
| `fps 144` | Any fixed cap from 30 to 1000. |

The setting is saved with the rest of the game's settings. Adding `vm.arg.2=-Dnfm.fps=unlimited` (or `auto`, or a number) to `madness64.ini` overrides the saved value at every start.

All drawing is done on the CPU by Java2D, so the ceiling depends on the processor: on a laptop i7-8550U it is roughly 100-170 FPS mid-race and 25-40 FPS during the opening fly-over of the whole track.

## Developer options

| Property | Meaning |
| --- | --- |
| `-Dnfm.profile=true` | Print simulation rate, render rate, time per tick and per frame, run-ahead ticks (their cost, and how many real ticks were checked against the prediction and missed it), the frame limit and the current phase every two seconds. |
| `-Dnfm.runahead=<true or false>` | Run-ahead for this run, see above. |
| `-Dnfm.debug=false` | Do not open the developer console on the main menu. |
| `-Dnfm.fps=<auto, unlimited or a number>` | Frame rate limit for this run, see above. |
| `-Dnfm.scale=<fit or off>` | Window scaling for this run, see above. |
| `-Dnfm.window=<width>x<height>` | Size of the window's inside at start, for example `1920x1080` (default 1280x720). Also sets the size of hidden test runs, which then save each screenshot twice when the size is not 1280x720: the picture itself and `<name>-window.png`, the window as shown. |
| `-Dnfm.autotest=<dir>` | Run the scripted smoke test and write screenshots to `<dir>`. The window is never shown and the test drives the game through its own controls, so it is safe to run while using the machine. |

For example:

```
runtime\bin\java.exe -Dnfm.profile=true -Dnfm.debug=false -Dnfm.autotest=C:\temp\nfm-shots -jar madness.jar
```

## Recorded races and the determinism check

A race plays out the same way every time it is given the same start and the same input. The game can record races and play them back with no player, checking every tick, so any change to the code can be tested against races that are known to be right.

What makes that possible:

- **Three random streams.** The race (AI and opponent cars), the cosmetic effects and the camera shake each draw from their own stream, using the original generator. A race gets a seed when its stage loads; the streams are derived from it, so drawing more or fewer frames, or adding an effect, cannot change the race.
- **Bit-exact maths.** Every angle, square root and power in physics and AI goes through `StrictMath`, which gives the same bits on every processor and Java version.
- **Input read once per tick.** Keys pressed during a race are collected as they arrive and handed to the race at the start of each tick, so a tick always sees one consistent set of keys. That is the input a recording stores.
- **A record of the state after every tick.** One hash per car, one for the race, one for the camera and one for the random streams, plus a canonical hash (see below); every 40 ticks the values themselves are stored too, so a mismatch can be traced to the field.
- **A record of the sounds of every tick.** Every sound the race tick starts, loops or stops (crashes, skids, power, checkpoints, fixes, the countdown) is logged in order, so the check also proves the race sounds exactly as it did. The engine and wind loops are chosen after the tick, and which wind clip plays draws from the cosmetic random stream that drawing shares, so they are not logged yet. Recordings made before this (format 3) are still checked, without the sounds.

### Recording

| Property | Meaning |
| --- | --- |
| `-Dnfm.record=<dir>` | Record every race to `<dir>` as a `.nfmr` file (gzip; a race of one or two minutes is 50-160 KB). Recording starts at the countdown and ends when the race is won, lost or left. Works while playing normally. |
| `-Dnfm.autopilot=<count>` | Play `<count>` races with scripted input and no window or sound, then exit. Stages are shuffled and cars picked at random; every third race watches an instant replay part way, half skip the intro. Use with `nfm.record`. |
| `-Dnfm.autopilot.seed=<n>` | Seed for the autopilot's choices (default: the clock). |
| `-Dnfm.autopilot.ticks=<n>` | Leave a race from the pause menu after this many ticks if it has not ended (default 2400, about two minutes). |

To add races you played yourself to the corpus, add `vm.arg.2=-Dnfm.record=test\races` to `madness64.ini` (or the next free `vm.arg` number if `vm.arg.2` is taken) and play.

### Verifying

| Property | Meaning |
| --- | --- |
| `-Dnfm.verify=<file or dir>` | Play each recorded race again from its input alone, with no window or sound, and compare every tick. Exits with 0 if every race matched, 1 if one differed, 2 if one could not be run. |
| `-Dnfm.verify.canonical=true` | Compare only the canonical state, even if the full state would line up. |
| `-Dnfm.verify.perturb=<tick>` | Self-test: flip the left key on that tick, so the check must report a mismatch there. |
| `-Dnfm.verify.rollback=<n>` | Rollback self-test: every `n` ticks, capture the whole race, play 8 ticks ahead with the steering and throttle reversed, rewind and carry on. Every value the race can reach must be back as it was, and the race must still match (`verify-races.ps1 -Rollback <n>`). |
| `-Dnfm.verify.trace=<tick>` | Write every named value after that tick to a text file in the temp folder. Run the same race two ways and compare the files to see exactly which value went wrong first. |
| `-Dnfm.unpaced=false` | Record or verify at the real pace with frames drawn, instead of as fast as the processor allows (the default for autopilot and verify runs). |

A mismatch is reported as the first differing tick (and how many seconds into the race), the state groups that differed, and up to 25 fields with the recorded and actual values from the next stored dump.

Before checking, the verifier sets the race back to the state the recording started from. The original game does not reset everything between races, and that behaviour is kept as it is:

- A few hundred AI and physics values carry over from the race before.
- A stage's boundary walls never set their surface type, wall flag or dust colour. Each keeps what the track piece in the same slot had in the stage loaded before, which is zero in a freshly started game. So how cars grip and how the AI recovers along the boundary depends on the stages played earlier in the session.
- An AI driver remembers the last wall it hit, and that memory survives into the next race. Until it hits a wall again it reads that slot's damage and surface, which can lie past the end of the new stage's pieces and still hold values from an earlier stage.
- The skid and crash sounds each rotate through three clips, and the rotation is only reset when the game starts. So which clip a race's first skid plays depends on the races before it.
- The HUD's captions alternate colour every tick. The stage's loading screen runs on its own thread and flips the same blink phase every 20 ms while its cartoon animates, so the phase a race starts on depends on how long the stage took to load. Only the pictures depend on it.

Recordings store all five, and the verifier restores them and lists what it had to restore. It also checks that the track's collision boxes and the race's random stream are exactly as recorded, so a change to how stages load is reported, not covered up.

If the game crashes during an autopilot or verify run, the run prints the error with the top of its stack trace and exits with 2 instead of hanging.

One original bug is fixed: when stage 17 was the first stage of a session, the game froze at the start of the race, because the replay buffer copied more objects than that stage has.

### Rewinding a race

`RaceSnapshot` captures everything a race tick reads and writes and puts it back exactly: the cars' physics, drivers and models down to the dents in every polygon, the standings, the replay buffer, every object on the track with what drawing advanced on it, the camera, the sky and all of the random streams. A speculative tick (drawing the next frame from where the race will probably be, for lower input lag) and online play with rollback both need this.

It is fast enough to use on every tick. On the biggest stage (262 objects, 7,212 polygons) a capture takes about 1.5 ms and a restore about 1.3 ms. Capturing leaves out what a race never changes: the track's collision boxes, the corners of every polygon that is not on a car, and the stage's ground, cloud and mountain geometry. The objects on the track, their polygons and the replay buffer each save and restore themselves through a single method, which writes and reads the same values in the same order. The rest is copied by reflection. A snapshot keeps its memory between captures.

Nothing that is undone may be heard either. While a tick is played only to be rewound, every sound command is thrown away before it reaches the speakers or the log (`SoundManager.setDiscarding`); the rollback self-test plays its ticks ahead that way, and the real ticks after each rewind must give exactly the recorded sounds.

The rollback self-test proves nothing is left out. At every rewind it also takes a complete copy of everything the race can reach, by reflection. After rewinding, it compares every field with that copy and reports any value that came back different by class and field, for example `Plane.n in 2 objects`. Leave the drivers out of the snapshot, and the race check fails at the first rewind after the start.

### The race and the rest of the game

The race code (`Madness` for a car's physics, `Control` for a driver, `CheckPoints`, `Trackers`, `Stat`, `Wheels`) no longer reaches into the renderer's globals. Its random stream lives in a `World` object that the cars and drivers are given, and it reports what the HUD and the sound code react to (crashes, skids, a repair, the next checkpoint, the run to the finish) through `RaceEvents`, which `xtGraphics` implements with the original decision code. So the race can be captured, run ahead or rewound without the screen or the speakers taking part. The camera is still shared: the race reads back which cars are on screen.

### The corpus

`test\races` holds 20 races: one on each of the 17 stages, two long ones that end on their own, and one recorded at the real pace with frames drawn. Run the check after any change that could touch gameplay:

```
powershell -ExecutionPolicy Bypass -File build.ps1
powershell -ExecutionPolicy Bypass -File test\verify-races.ps1
```

`verify-races.ps1 -Fresh` runs each race in a game of its own, which is slower but shows nothing depends on the race before. `test\record-races.ps1` records more (`-Count`, `-Seed`, `-Ticks`, `-RealTime`).

### Golden screenshots

While it checks the races, `verify-races.ps1` also renders four frames of each one (just after the start, then spread evenly to the end) and compares them pixel by pixel with the frames in `test\golden`. A race replays exactly, so a frame only changes when drawing changes: this proves that rendering work leaves the picture as it was, and measures how far a new renderer is from the original. A changed frame is reported with the number of pixels, how far they are off and where, and the new frame plus a picture with every changed pixel marked are written to the temp folder.

| Option | Meaning |
| --- | --- |
| `-UpdateGolden` | Save every frame again, after a change that is meant to alter the picture. Missing frames are always saved. |
| `-NoGolden` | Check the races only. |
| `-Golden <dir>` | Check the frames of other races (`-Races`) against another folder. |

The same check is available directly with `-Dnfm.verify.golden=<dir>`, `-Dnfm.verify.golden.update=true` and `-Dnfm.verify.golden.tolerance=<n>` (how far a colour channel may be off before a pixel counts as changed; default 0).

Golden frames are taken with every race in one game, in name order, so `-Fresh` checks the races only. Some of what is on screen carries over from the races before, as in the original: which hint the HUD shows, and some dust and sparks. Two separate runs of the corpus produce identical frames.

A few things only change how a race looks, and they come from the session rather than the race. The original picks the distant mountains at random when the game starts, for every stage that does not set its own, so recordings store that choice too. The jittered shape of the sand pieces and the checkpoint flicker draw from seeded streams, so the same race looks the same every time.

### Full and canonical state

The full state is every field of the race classes, read by reflection, so it catches anything. It depends on how those classes are laid out: once fields are renamed or moved, old recordings can no longer be compared field for field. The canonical state is a short list of what a race actually is (car positions, angles, speeds, wheels, damage, checkpoints, laps, input, race position, the camera and the race random stream) under stable names. When a recording's layout no longer matches the code, the verifier compares the canonical state only and says so. When code is reorganised, keep `RaceState.visitCanonical` pointing at the same quantities and the existing corpus stays usable.
