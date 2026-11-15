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

1. Install a JDK 21 or newer (any vendor). `build.ps1` finds it via `JAVA_HOME` or `%USERPROFILE%\.jdks`, or pass `-Jdk <path>`.
2. Run `powershell -ExecutionPolicy Bypass -File build.ps1`. It produces `madness.jar` and a private Java runtime in `runtime\`.
3. Double-click `madness64.exe`. No system-wide Java install is needed to play once `runtime\` exists.

`madness.exe` is the 32-bit launcher and needs a 32-bit runtime, so use `madness64.exe` on 64-bit Windows.

The game no longer depends on the Applet API (removed in Java 26) or `Thread.stop()` (which throws since Java 20), so it builds and runs on current JDKs.

## The frame loop

Simulation and presentation are decoupled. The world is stepped at the original ~19 Hz cadence so physics, AI and every frame-counted timer behave exactly as before, while the 3D scene is redrawn at up to 120 FPS using car and camera transforms interpolated between the last two ticks. The HUD is rendered once per simulation tick into its own layer and composited on top, keeping its blink and pause timers on the legacy cadence.

Three system properties are available while developing:

| Property | Meaning |
| --- | --- |
| `-Dnfm.profile=true` | Print simulation rate, render rate and current phase every two seconds. |
| `-Dnfm.debug=false` | Do not open the developer console on the main menu. |
| `-Dnfm.autotest=<dir>` | Run the scripted smoke test and write screenshots to `<dir>`. The window is moved off-screen, cannot take focus and the test drives the game through its own controls, so it is safe to run while using the machine. |

For example:

```
runtime\bin\java.exe -Dnfm.profile=true -Dnfm.debug=false -Dnfm.autotest=C:\temp\nfm-shots -jar madness.jar
```
