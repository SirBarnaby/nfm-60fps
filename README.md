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
2. Run `powershell -ExecutionPolicy Bypass -File build.ps1`. It produces `madness.jar` and a private Java runtime in `runtime\`. The runtime is made again only when the JDK or its settings change (`runtime\nfm-runtime.txt` records them); delete the folder to force it. Last it plays the hidden smoke test once (about two minutes) to train `madness.aot`, see below; `-Quick` skips that while working on the game.
3. Double-click `madness64.exe`. No system-wide Java install is needed to play once `runtime\` exists.

`madness.exe` is the 32-bit launcher and needs a 32-bit runtime, so use `madness64.exe` on 64-bit Windows.

The game no longer depends on the Applet API (removed in Java 26) or `Thread.stop()` (which throws since Java 20), so it builds and runs on current JDKs.

## Settings

**Settings** on the main menu, and on the pause menu during a race, opens the game's settings screen: the picture (renderer, fullscreen, the window's shape and size, the UI scale, frame rate and the cap on battery, MSAA, supersampling, dynamic resolution, smooth effects, and HDR with its paper white and peak), the sound (3D or Classic, stereo or mono, the five volumes, the music's width) and the controls (two keys for every action, game controllers, shaking on hits, run-ahead). Up and down choose a setting, left and right change it, Enter changes it too (or goes back from **Back**), and Escape goes back; the mouse chooses by pointing, and a click on a setting's arrows, or on a volume's bar, changes it. A setting that does not apply is greyed out, with the reason under it (MSAA with the software renderer, say). Every change takes effect at once and is saved, by the same code as the developer console's commands, which stay for everything else. The screen is drawn in the menus' own look: the pause box's frame around the main menu's brown, and the menus' lettering (`data\fonts\Adventure.ttf`, the face the menu pictures were drawn in). The menus gained their **Settings** item in the same lettering; on the pause menu it sits above Quit Game, so the box grew by one row. The main menu also gained **Exit Game** below it, its brown backdrop opened up by a row to hold it; it closes the game as the window's close button does.

## The frame loop

Simulation and presentation are decoupled. The world is stepped at the original cadence (one tick per 53 ms during races, replays and highlights, one per 40 ms in menus), so physics, AI and every frame-counted timer behave exactly as before. Races, instant replays and the post-race highlights are redrawn as often as the frame limit allows, using car, wheel and camera transforms interpolated between the last two ticks. The HUD and replay captions are rendered once per tick into their own layer and composited on top, keeping their blink and pause timers on the legacy cadence.

If drawing a frame takes longer than a tick, the loop runs the ticks it owes before drawing again, so a slow frame lowers the frame rate but never slows the race down.

Between frames the game thread sleeps on Windows' high-resolution waitable timer (Windows 10 1803 and later), which wakes it within a fraction of a millisecond. `Thread.sleep()` wakes on the system timer tick, up to 2 ms late, so the loop used to spin through the last 2 ms before every frame, keeping a core busy and taking turbo power from the thread that draws. Where the timer is not available the old way is used.

The car physics (`Madness.drive`) and the AI (`Control.preform`) are each over HotSpot's 8,000-byte limit for compiling a method, so by default they would only ever be interpreted. The runtime that `build.ps1` makes carries `-XX:-DontCompileHugeMethods`, which lets them be compiled: in a profile of two races their share of CPU time fell from about 9% to 2%. It also carries `-XX:PerMethodTrapLimit=0 -XX:PerBytecodeTrapLimit=0`, so the compiler keeps the branches it has not seen taken yet. Without them the first time something new happens in a race (a wreck, say) the compiled drawing code is thrown back to the interpreter, and for seconds ticks took 15-40 ms instead of 3-5 while it was compiled again. The race results are the same either way, since compiled and interpreted Java compute identical results.

The runtime collects garbage with generational ZGC, which does its work beside the game: the default collector (G1) stopped every thread for about 4 ms every half minute of racing, a missed frame at 300 frames a second, where ZGC's pauses stay under a tenth of a millisecond. Compact object headers (8 bytes per object instead of 12) leave a few percent less live heap after each collection. Left to itself ZGC lets the heap grow to a quarter of the machine's memory before it bothers to collect, so the game's memory climbed while it ran, as if it leaked, though a race keeps under 200 MB alive; `madness64.ini` gives it a soft limit of 512 MB, which it collects to stay under (still without pauses), and it hands memory back to Windows after 30 seconds. The 3D drawing also reuses the colours it draws with instead of making a new one for every polygon, some hundreds of thousands a second. The game also starts from an ahead-of-time cache (`madness.aot`, Java 25 and later), which `build.ps1` trains by playing the smoke test: the classes the game uses come already loaded and linked, and the JIT knows from the training which methods are hot, so they are compiled sooner on the first race. Loading to the menu takes about 15% less time. The game's files load side by side as well: the models on one thread, the sounds on another and the images on the game thread, while a fourth loads the graphics card's library and shaders, so the picture appears about 0.8 seconds sooner (2.3 instead of 3.1 seconds on the i7-8550U laptop; starting Direct3D itself, about 0.75 seconds, still waits for the window). The models stay on one thread in the archive's order, because sand planes take their shapes from a random stream as they are made. The cache belongs to that exact `madness.jar` and runtime; after any change Java ignores it (printing why), and the game runs as before.

Drawing is not free of side effects in this engine: it advances the repair hoop sequence, dust, wreck flames, flying debris and the checkpoint flicker, and the race reads the distances it works out (a car out of view is repaired at once, a car in view waits for the hoop). So every tick draws the scene once into an invisible surface, at the point where the original drew it, and every frame that reaches the screen is drawn and then rolled back. The frame rate, a minimised window or a run with no window at all therefore never speeds up an animation or changes how a race plays out. Cosmetic effects take their random numbers from a stream of their own, so they cannot shift the AI's choices either.

## Run-ahead: one tick less input lag

Drawing between the last two ticks is smooth, but it shows the race as it was up to one tick (53 ms) ago. With run-ahead, which is on by default, every race frame is drawn between the last tick and the next one instead: when a tick lands or a driving key changes, the game plays the next tick ahead of time with the keys held at that moment, notes where the cars and the camera end up, and puts the whole race back exactly as it was (see "Rewinding a race" below). The real tick then reads the same keys and lands on the same spot, because the race is deterministic, so a key press shows on screen a full tick sooner and nothing jumps.

The tick played ahead is never heard, never recorded and never drawn into the HUD, and it cannot change the race: races recorded in real time with run-ahead firing about 19 times a second replay tick for tick, sounds included. A run-ahead costs about 5 ms on a laptop i7-8550U (capture, one tick, restore), once per tick and once per key change. Only a press between two ticks that the prediction could not know (Enter, for example) makes a frame correct itself; in testing, 1 of 3,506 predictions missed.

It applies while racing, not in the intro, the countdown, replays or menus. Turn it off or on in the developer console with `runahead off` / `runahead on` (saved), or for one run with `-Dnfm.runahead=false`.

## Window size and fullscreen

The window can be resized freely, down to half of 1280x720, and the game fills it at any shape with no black bars, the way modern ports of old games do. The race is drawn by the graphics card at the window's own resolution, and the game's 1280x720 view is widened to the window's shape (it keeps its 720 height) or, for a window narrower than 16:9, made taller (it keeps its 1280 width), so everything seen at 16:9 is always there and a 21:9 window sees more to the sides, a 4:3 one more above and below: 2560x1080 sees 1706x720 of it, 1920x1200 sees 1280x800; beyond 5:4 and 32:9 the race keeps those shapes and is cut a little to fill the window, so there are never bars. The menus and the HUD keep their own size in pixels, sharp, like the original's on a 1280x720 screen: on a larger window they take the same space and there is more around them, the menus' grain backdrop tiling out to the edges at any shape and the car select and stage pictures going on past theirs, softening into the dark; the race's lap, damage and position readouts sit in its corners. **UI scale** in the settings shows them 1, 2 or 4 times their size, whole pixels each so they stay sharp; **Auto**, the default, takes the largest that leaves room for a 1280x720 screen: 1x up to 1440p, 2x from 2560x1440 (4K included), 4x from 5120x2880. A window smaller than 1280x720 shows the whole 1280x720 picture shrunk to fit, and so does the software renderer at any size. Races play out exactly the same in any window: the race camera is placed, and what is on screen read back for the race (a car out of view finishes a repair hoop at once), in the 1280x720 view, and only drawing a frame looks through the window's shape from the same place. **F11** or **Alt+Enter** switches between the window and fullscreen on the monitor the window is on, as does **Fullscreen** in the settings; the game starts the way it was left.

**Aspect ratio** and **Window size** in the settings size the window: 16:9, 16:10, 4:3, 5:4, 21:9 or 32:9, and for each its common sizes (1280x720 to 7680x4320 for 16:9) that fit on the monitor, or, if none does, the largest of that shape that fits. **Custom...** at the end of the sizes takes a size of your own: Enter (or a click on it) opens it for typing, the width, then `x` (or Tab, or the right arrow) and the height, Enter to set it and Escape to leave the window as it was; it can be from 640x360 to the monitor's own size. Sizes are the monitor's pixels, whatever Windows' display scaling, and the window keeps its centre, moved only as far as it takes to stay on the monitor. A window sized by hand shows its own size among the common ones, and **Other** for its shape. The game starts at the size it was left at (dragging an edge counts, once it has stayed a second). In fullscreen **Window size** becomes **Resolution**: the size the game draws at, always in the monitor's shape and scaled to fill it (a lower one is faster), with the monitor's own size the default; it is saved apart from the window's. **Aspect ratio** is greyed out in fullscreen, as a picture of another shape would have to be cut or barred to fill the monitor; a typed size of another shape is refused with the monitor's shape as an example. The race and the menus take the window's shape, see above.

Where the whole picture is scaled (the software renderer, or a window under 1280x720) it uses a smooth (bilinear) filter computed on all CPU cores: about 2 ms a frame for 1920x1080 and under 4 ms for 3840x2160 on a laptop i7-8550U. Java's own filter would do the same picture in about 25 ms, because its Direct3D path refuses Intel graphics and falls back to software.

## Drawing through the graphics card

Finished frames reach the screen through the graphics card: each frame is uploaded as a texture and drawn as one rectangle with Direct3D 11, using [bgfx](https://github.com/bkaradzic/bgfx) through [LWJGL](https://www.lwjgl.org/) 3.4.3 (the jars are in `lib\`). Scaling to the window is then free for the processor, and the frame skips GDI: showing a frame takes about 1.7 ms of the game thread at any window size, 1080p or 4K, on a laptop i7-8550U with Intel UHD 620 graphics, mostly copying the picture to the card. What reaches the screen is the same picture: read back from the card in hidden test runs, it is identical pixel for pixel at 1280x720 and within 3/255 of the software filter when scaled.

Races, instant replays and highlights are also drawn there. The world's drawing code runs as before, side effects and all, but its polygons go into a list instead of into Java2D, and the card draws the list in the same painter's order, with the HUD layer on top. Convex polygons are batched into a few draw calls; concave or self-crossing ones are filled with the even-odd rule through the stencil buffer, as Java2D fills them; fills are moved a quarter pixel to sample where Java2D does, outlines light one pixel per column or row, and the outlines Java2D antialiases (car edges) fade over the neighbouring pixels the same way. Read back in hidden test runs, a race frame drawn from the same whole-pixel corners differs from the software picture in about 0.1% of its pixels, all on polygon edges and mostly by a few shades. On the laptop above a 1080p race runs at about 70-85 frames a second this way against 42-66 in software.

The game's 3D code works in whole numbers: whole units, whole degrees, every rotation rounded and every corner on a whole 1280x720 pixel. Between two race ticks that rounding made corners jump back and forth by a pixel or more from frame to frame, and turning moved the picture in whole-degree steps, which looked like a slight shimmer or ghosting on a fast monitor. On the card each corner is now worked out a second time without rounding, from the cars' and the camera's exact positions and angles between the ticks: cars, track pieces, shadows, the ground, sky bands, mountains and clouds. The game's own whole-number pass still decides everything else (what is drawn, in what order and colour, as before). Measured with `-Dnfm.motiontest`, 3-9% of corner moves jumped by 1-6 pixels before; now none do. Edges therefore sit up to about a pixel from where the software picture rounds them, so a frame differs from it in 5-7% of its pixels, all along edges.

Running the game's whole 3D drawing code for every frame was most of a frame's cost, and a race tick only comes every 53 ms. So the scene is now drawn once per tick, halfway through it, and kept: every corner is kept as a point that knows where it sits on its car or in the world and how the game places it on screen. For each frame those points are placed again for the cars' and the camera's positions at that moment, with the same arithmetic, which takes about 0.3 ms. What is drawn, in which order and colour, is decided once per tick, which is also how often the original game decided it. The kept scene reaches past the edges of the picture (480 pixels to the sides, 270 above and below) and keeps every sky and ground band, so what comes into view during a tick is already there. Dust puffs and anything else the game works out only on screen move with the puff or the car they belong to.

Dust and flying debris also move between the ticks (**Smooth effects** in the settings, on by default; `effects smooth off` in the developer console, or `-Dnfm.smootheffects=false`). The game moves a puff or a chip of debris once a tick, while drawing it, so they used to step at the tick rate, 19 times a second, however fast the frame rate. Now each is drawn where it was shown a tick ago, the frame's share of the way to where it is now: a puff rises, drifts and grows on every frame, its ragged outline turning into the next tick's, and debris flies along its arc. At the tick itself the picture is exactly the original's, and the race, the effects' own state and their random numbers never see any of it. With the GPU renderer the kept scene moves them as it moves everything else, and in software they are drawn moved; measured with `-Dnfm.motiontest`, the kept scene still matches the frames drawn the whole way to 0.00 pixels. Hidden test runs keep the original stepping unless `-Dnfm.smootheffects` asks, as their pictures are compared with the golden frames. Measured with `-Dnfm.motiontest` (`python test\kept.py <dir>`), the kept scene's corners are within 0.005 pixels of the ones drawn the whole way at every moment. `-Dnfm.retained=false` draws the whole scene for every frame again.

With the graphics card, race frames are drawn on a thread of their own, which owns the card. After each tick (and each new run-ahead tick) the game thread hands it the cars' and the camera's poses at the start and the end of the tick first, then the scene recorded for the tick and the HUD; the drawing thread places the latest scene for the moment each frame is due and shows it, at the frame limit or the display's rate. So a tick, the run-ahead tick or recording the scene never holds a frame back, and the motion of a new tick reaches the frames as soon as the tick has run (about 3 ms in), not after its scene is recorded too. Until then frames carry the last tick's motion on, at most a quarter of a tick. With the motion handed over first, a frame at a new tick lands within about 2 world units of where the last one was heading, where waiting for the scene left it 60-400 units off: that was the small back-and-forth at each tick. The game thread runs at a higher priority than the drawing thread, as its ticks are what the frames wait for. Menus and every frame the game thread draws itself are still shown by the drawing thread, while the game thread waits. `-Dnfm.presenter=false` draws and shows everything on the game thread again.

Concave polygons (a car's side panel, say) used to be filled with the stencil buffer, two draw calls each. They are now cut into triangles one corner at a time, which covers exactly the same pixels in the same draw call as the shapes around them; a polygon that crosses itself is cut across at the height of every corner and every crossing, and filled between the first and second edge from the left in each band, the third and fourth and so on, which is the even-odd rule exactly. Only a kept scene placed on the card, whose corners move after it is cut, still uses the stencil. Draw calls per frame went from about 1000 to 300-400, and in split screen from 500-1100 to under 10: the stencil's draw calls had cost the card 9-17 ms of a split frame, now 1.3-1.8 ms. In the frames of one kept scene a polygon is cut again only when the triangles it was cut into last no longer fit: as long as it does not cross itself and none of them has turned over, they still cover each point inside it exactly once. Vertices are written into a plain array and copied to the card's buffer in one go, and the HUD is uploaded only when a new tick drew a new one. Together that took the drawing thread from 340-450 to 360-670 frames a second on a laptop with Intel UHD 620 graphics.

The graphics card now places the kept scene itself. Once a tick, the game thread cuts the scene into triangles whose corners name the points they sit at, rather than holding positions, and lays the points out in a float texture. The card gets both once, the first time a frame of that tick is drawn. Each frame then sends only the camera and the cars' poses, about 3 KB, and a vertex shader places every corner with the same arithmetic as before, wheel spin and steering included. The drawing thread no longer places about 10,000 points and rebuilds 25,000 vertices for every frame (0.5-0.8 ms of placing and 1.1-1.7 ms of building, down to 0.01-0.05 ms), and the bytes sent to the card fell from about 0.5 MB a frame to about 1 MB a tick. Measured with `-Dnfm.projectiontest=40`, which draws 40 frames both ways and compares them, at most 0.008% of the pixels differ, by rounding at a few edges. Split screen's panes are placed the same way, each its own scene for the card with its own camera, drawn in its rectangle; there 39 of 40 frames differ by at most 0.01%, and one by 0.6%, a large sand polygon touching the camera's near edge shaded a little differently. `-Dnfm.projection=off` places the scene on the processor again, as does a card that cannot read float textures in its vertex shader.

Polygon edges can be smoothed: choose **MSAA** in the settings (Off, 2x, 4x, 8x or 16x), or type `msaa 4` (or `2`, `8`, `16`, `off`) in the developer console (saved), or start with `-Dnfm.msaa=4`. The card then takes that many samples per pixel (multisampling), so a polygon's edge pixels are mixed from the colours on both sides instead of being one or the other. It is off by default, which keeps the game's own hard edges; hidden test runs always draw without it unless `-Dnfm.msaa` asks, since their pictures are compared pixel for pixel. A card that does fewer samples gets its most, and the console says so. On the laptop with Intel UHD 620, 4x at 1920x1080 still draws 250-330 frames a second.

For the cleanest picture, **Supersampling** in the settings (or `ssaa 2x`, `4x` or `8x`, `ssaa off`, or `-Dnfm.ssaa=4x`) supersamples: the race is drawn with that many times the pixels it is shown with and averaged down. 4x is twice the width and height, so a 1080p window draws the race at 3840x2160 (4K); 2x is 1.41 times each way and 8x 2.83 times (5431x3055 for 1080p). Outlines get as much thicker. Each pixel on screen is the exact average of what it covers of the larger picture, every texel counted by how much of it lies under the pixel, so 2x and 8x, which do not fall on whole texels, come out as clean as 4x; the same average now shows the scene in a window smaller than the picture. Every edge and outline is smoothed, not only polygon edges, and thin lines stop shimmering as the camera moves. A choice saved as `on` before the levels is 4x. It works with `msaa`, and a window too large for the card's biggest texture gets as much as fits. With dynamic resolution on, the scene scale counts from the doubled size, so when the card cannot keep up supersampling gives way first, down to the shown size and then below it as before. Off by default and in hidden test runs. On the laptop above a 1280x720 race takes the card about 4.5 ms a frame with it against 2 ms without.

Dynamic resolution holds the frame rate when the graphics card itself cannot keep up, as at 4K with smooth edges on a laptop's integrated graphics. The card's own clock says how long it took over each frame; when that passes 90% of the frame budget (the display's refresh rate, or the frame limit), the next races are drawn in a smaller part of the scene target and scaled up smoothly to the window, and when the card has stayed under 75% for a while they grow back a twentieth at a time. A step up that does not fit makes the next try wait twice as long, up to 16 seconds, so the picture never pumps; one slow frame is not taken for a slow card, and the first half second of a race does not count. Only the card's time counts: when the processor is what holds frames back, a smaller scene would only be blurrier, so nothing changes. The scene is never drawn smaller than the game's own 1280x720, the HUD stays sharp, and with no frame limit the scene is always drawn whole. Type `dynres off`, `dynres on` (the default, down to 50% of the window) or `dynres 25`-`100` for the lowest share in the developer console (saved), or start with `-Dnfm.dynres=off`; with `-Dnfm.profile=true` the `[draw]` lines show the card's time and the scale. On the UHD 620 at 3840x2160 with 4x smooth edges and a 120 fps limit, the card took 11.6 ms a frame drawing the scene whole; it settled at 90% (6.6 ms) and frames went from about 55 to about 70 a second. If the card cannot make a scene target that large with as many samples as asked, it takes fewer, rather than giving up on the card.

In a window larger than 1280x720 the card draws the world at the window's own resolution, so polygon edges stay sharp at 1080p or 4K instead of being scaled up from 1280x720. Positions are the game's own, so nothing moves; one-pixel outlines get as much thicker as the picture is larger, and the HUD is scaled up with the same filter as before. In a smaller window it draws at 1280x720 and scales down. `-Dnfm.gpuscene=picture` always draws at 1280x720.

Whatever reads the back buffer (the pause screen, the countdown portrait, the post-race fade, the credits) or draws over the last race frame (menus) first gets that frame drawn into it in software from the same list, so it finds exactly what it found before. Frames with the debug overlay, and any frame that draws something the list does not cover, are drawn in software. `-Dnfm.gpuscene=false` keeps scenes in software while still presenting through the card.

On a monitor with HDR turned on in Windows, frames go out in HDR (an scRGB swap chain). The game looks just as it does in SDR, at the brightness Windows shows SDR white at (the "SDR content brightness" slider), so menus and the HUD are not blinding; but what gives off light shines brighter than white, up to four times as bright: fire and the flash of a car blowing up, headlights and lamps at night, the repair hoop's flash and the electrified car's sparks, stars, and the sky in a lightning storm. The world's drawing code marks those polygons as it draws them, the card keeps how much each pixel glows in the scene's alpha, and only the final step to the screen reads it. The game asks Windows every two seconds whether HDR is on for the monitor the window is on and where SDR white is, so turning HDR on or off, changing the slider or moving the window to another monitor takes effect while the game runs. Type `hdr off` or `hdr auto` (the default) in the developer console to choose (saved), or start with `-Dnfm.hdr=off`. Read back in a hidden test run with HDR forced on, every frame turned back into SDR is identical to the frame the card drew, pixel for pixel; `<name>-glow.png` shows what glowed.

The game's white, paper white, follows Windows' slider by default; `hdr white 200` sets it in nits instead (80 to 500, `auto` to follow Windows again). The original palette and the HUD are drawn at it and never brighter. Glow goes up to four times paper white, but no further than the display can show: the game asks DXGI for the monitor's peak brightness (from its EDID, or the profile the Windows HDR Calibration app made), and when the brightest glow would pass it, highlights roll off into the peak along the ITU BT.2390 curve, in PQ, on the brightest of the three channels so their hue stays. Nothing at or below paper white changes, so the palette stays exact. `hdr peak 1000` sets the peak in nits for a monitor that reports it wrong (200 to 10000, `auto` for the monitor's). A monitor that reports no peak gets the full four times, as before; the console's `hdr` shows what is in use.

Its shaders are compiled when the game is built, by Windows' own shader compiler (`d3dcompiler_47.dll`), and go into madness.jar, each headed by a hash of the source it came from; the game packs them into bgfx's shader format, so the project needs no shader tools. Loading the compiler and compiling cost about half a second at every start before; a shader whose baked copy is missing or stale is still compiled at the start, and the log says so. Direct3D 11 has no pipeline objects to build, so nothing else waits for the first time an effect appears. With the frame limit on "match display" or "vrr" frames wait for the display's refresh, so they are never shown torn; with any other limit they are shown at once, through DXGI's tearing flag, so a variable-refresh monitor still follows them.

bgfx can also draw with Direct3D 12: start with `-Dnfm.backend=d3d12`. Everything works the same there, HDR included, and what it draws reads back identical to what reaches the screen; but on the laptop above it ran at about 220 frames a second in races against 350 with Direct3D 11, as the game sends many small draws each frame, which Direct3D 11's driver handles with less work. So Direct3D 11 stays the default.

If the graphics card cannot be used (no Direct3D 11, the libraries missing, or an error while drawing) the game says so in its log and draws in software as before, in a fresh window. Type `renderer software` or `renderer gpu` in the developer console to switch (saved), or start with `-Dnfm.renderer=software`. Menus and the HUD are still drawn by Java2D on the processor.

## Frame rate limit

By default the frame rate matches the refresh rate of the monitor the window is on. To change it, choose it under **Frame rate** in the settings, or open the developer console (it appears on the main menu) and type:

| Command | Effect |
| --- | --- |
| `fps` | Show the current limit. |
| `fps auto` | Match the monitor's refresh rate (default). |
| `fps vrr` | For G-Sync and FreeSync monitors: vsync on, frames held just under the refresh rate (138 at 144 Hz, 59 at 60 Hz), so they stay inside the variable-refresh range and never wait in a queue behind vsync. `gsync` and `freesync` work too. |
| `fps unlimited` | No limit: draw as fast as the CPU allows. |
| `fps 144` | Any fixed cap from 30 to 1000. |
| `fps battery 60` | On battery, hold frames to this many a second (default 60), or `off`. |

On a laptop running on its battery, frames are held to 60 a second (or the limit above, if lower) so the battery lasts and the laptop stays cool; plugged in again, the limit returns within a few seconds. The settings are saved with the rest of the game's settings. Adding `vm.arg.2=-Dnfm.fps=unlimited` (or `auto`, `vrr`, or a number) to `madness64.ini` overrides the saved value at every start.

How high the frame rate goes depends on the renderer. With the graphics card (the default) a fullscreen 1080p race runs at about 300 frames a second on a laptop i7-8550U with its integrated graphics, measured by the live test with no frame limit (half the frames within 3.3 ms, 99% within 5-7 ms), as the card places the kept scene for every frame; a split-screen race about 200-275 (half within 3.5-4.7 ms, 99% within 6-10 ms), the card placing each of its two kept panes the same way. In software, where Java2D draws everything on the processor, it is roughly 42-66 at 1080p (more at smaller windows), and 25-40 during the opening fly-over of the whole track. Menus are drawn once a game tick, 25 times a second, as the original's were: their pictures only change on a tick.

## Windows scheduling and power

The thread that runs the race and the one that draws it join the Games task of Windows' Multimedia Class Scheduler (MMCSS), so a Windows update, a virus scan or a browser in the background waits for them rather than the other way round; MMCSS still keeps a fifth of the processor for everything else. The game also asks Windows not to throttle it to save power (EcoQoS), which otherwise moves programs it thinks unimportant to slower clocks or efficiency cores. Neither needs administrator rights. Hidden test runs do neither, so they stay out of the way of whatever the machine is doing.

Frames wait in at most one place on their way to the display (the swap chain's frame latency is 1), so with the limit on "match display" or "vrr" what is drawn is shown at the next refresh rather than queued behind two or three older frames. Without vsync, frames go out at once, torn if they must.

The graphics card is the one Windows picks for the game, which Windows' graphics settings can change per program. On a laptop with Intel UHD 620 and an NVIDIA Quadro P500, drawing on the discrete card was no faster (the game is limited by the processor) and costs battery, so the game does not override Windows; `-Dnfm.adapter=fast` does.

## Keyboard and mouse

The game was written for AWT 1.0's input methods (`keyDown(Event, int)` and the like), deprecated since 1997, which Java still fed by converting every event back into its 1.0 form. It now listens to the keyboard, mouse and focus events themselves (`GameInput`) and hands the game the same key numbers AWT 1.0 did, so every key does exactly what it did: checked against Java's own conversion for every key code, with and without Shift and Alt, pressed and released (28,458 events, none different). Each key carries the time Windows saw it, so the profile's key-to-screen time starts there, not when the game got round to it.

## Game controllers

Any game controller Windows knows (Xbox, PlayStation, Switch Pro and most others) can drive the game, read through SDL 3 (`lib\lwjgl-sdl-*.jar`). A pad presses the game's own keys, so it works everywhere the keyboard does and a race driven with one is recorded and replays as if typed:

| In a race | |
| --- | --- |
| Right trigger or A | Accelerate (Up) |
| Left trigger or X | Brake and reverse (Down) |
| Left stick or d-pad | Steer (Left, Right) |
| B | Handbrake (Space) |
| LB, RB | Look back (Z, X) |
| Y | Change the view (V) |
| Start, Back | Pause (Enter, Esc) |

In the menus the left stick and d-pad are the arrow keys, repeating while held as keys do, and A or Start is Enter. Hard hits shake the pad, harder for harder hits. Pads can be plugged in and out at any time; several work at once, and the keyboard keeps working beside them. The pads only count while the game's window has the focus.

`gamepad` in the developer console says which pads are connected; `gamepad off` / `gamepad on` and `gamepad rumble off` / `gamepad rumble on` are saved. Hidden test runs never read pads, so a pad on the desk cannot drive them.

## Split screen

Split Screen on the main menu races two players on one screen. Both choose a car at once on its own car select: each steers to choose (only the cars unlocked) and accelerates when ready, the brake takes it back, and the mouse does it all too. The same screen sets the race's computer cars (none to five), how the window is shared and where the players are heard; **Keys and controllers...** opens the settings' **Split screen** tab, which also has each player's controller and keys. Escape goes back, from the car select to the main menu and from the stage select to the cars.

How the window is shared: **Auto** picks side by side or top and bottom, whichever gives each player a picture nearer 16:9 (a 16:9 window splits top and bottom, a 32:9 one side by side), and changes at once when the window is resized; or always **Side by side**, or always **Top and bottom**. Each pane sees as much as its shape lets it, as a wide window does, never stretched, and each player has the whole HUD in their own pane, with their own minimap arrow, at full size in a pane at least 640x360 and smaller in a smaller one.

| Keys | Player 1 | Player 2 |
| --- | --- | --- |
| Accelerate, brake | W, S | Up, Down |
| Steer | A, D | Left, Right |
| Handbrake | Space or Left Shift | Right Ctrl or Num 0 |
| Look back | Q, E | Right Shift or Num 1, / or Num 3 |
| Change the view, arrow | C, F | . or Num ., `,` or Num + |

Every key can be changed on the **Split screen** tab (two each), and Left and Right Shift, Ctrl and Alt are told apart, as are the number pad's keys. Controllers are shared out by themselves (**Auto**: the first connected one is Player 1's, the next Player 2's, a player with **None** leaves theirs to the other), or each player is given one by number. Enter or Escape pauses for both.

The sound, with a second speaker chosen on the Audio tab: **One speaker** plays both players on the main one; **Race events** puts the countdown, checkpoints, repair hoops and wrecks on the second speaker, as outside split screen; **A speaker each** gives Player 2 the second speaker for their car and announcements, and Player 1 the main one. Without a second speaker everything plays on the main one. Each player's announcements are their own: two players through checkpoints at once each hear theirs.

The first car over the finish line wins, a player or a computer car. Otherwise it is down to wrecks: a player whose car is the last one left wins, and with both players wasted nobody does; a player wasted while the other races on watches the wreck until it ends. The results show every car in the order they stood, then **Race Again**, **Choose Stage**, **Change Cars** or **Main Menu**. Instant replay works from the pause menu, each pane round its own player's car. A split-screen race unlocks nothing and is not kept in `replays` (a recording holds one player's input).

The choices, both players' cars and their keys are saved. `split` in the developer console shows them and changes them: `split view <auto|side|stacked>`, `split cars <0-5>`, `split sound <one|events|each>`, `split pad <1|2> <auto|none|1-4>`, `split keys <1|2> <action> <key> [second key]`, `split keys reset`. Split screen goes through the graphics card too: each pane is recorded once a game tick from its own camera, with a margin round it, and the card moves it to where that camera is for every frame in between, as it does one player's race, each pane clipped to its rectangle. That is about 200-275 fps at 1920x1080 here (in software, about 55 at 1280x720).

## Custom game

Custom Game on the main menu sets up a race your way, then goes on to the car and stage select as Play Game does. **Computer cars** is how many race you, from none (a practice race) up to 32. **Their cars** is which cars they drive:

- **Default**: the cars the classic game brings to the stage, with the stage's boss at the front; in a bigger race each of them comes round again, shuffled. Default with six computer cars is the classic race.
- **Novice**: Tornado Shark up to Drifter X, shuffled.
- **Pro**: only the cars after Drifter X: DR Monstaa at the front, then Radical One, M A S H E E N and on down to Sword of Justice, then DR Monstaa again.
- **Custom**: the **Pick cars** tab sets how many of each car race, up to 32 in all, and the computer cars are the cars picked, the best of them at the front.

You always start last, in the middle behind everyone. Up to nine computer cars stand on the classic grid and start together. With more, they stand in groups of six across and start a group at a time, a second apart: the front group at the countdown's Go!, and yours, with the last group, after them. Until then you stand with the handbrake on, and the HUD says which group is off and counts you down to your own start with the countdown's numbers and voice. In a race of more than ten cars the standings show the top three, the car your arrow points at and the cars nearest you.

A custom race unlocks no stages or cars, and after it the custom game's page comes back, to race again or change it. The choices are saved; `custom` in the developer console shows them and changes them: `custom cars <0-32>`, `custom lineup <default|novice|pro|custom>`, `custom pick <car 1-16> <how many>`, `custom pick clear`. Custom races are kept in `replays` with their choices, so they play back exactly.

## Sound

Race sound is placed in 3D, mixed by OpenAL Soft (`lib\lwjgl-openal-*.jar`). Every car's engine is heard from where the car is, not only yours: the game's five recordings per engine play together, cross-faded and bent in pitch as the car rises through its speed bands, so the engine climbs smoothly instead of jumping between loops, and a car off the throttle sounds duller. Crashes and skids come from the car that made them, for every car, and the wreck and the repair from your car. Cars passing are heard passing (a gentle Doppler), far cars are quieter and lose their treble, and with headphones the sound is rendered for the ears (HRTF), so a car behind is heard behind. Your car, at the chase camera's distance, is as loud as Classic plays it; menus, the countdown and the wind are heard as they are; an output limiter keeps a pile-up from clipping. When Windows' default output changes (headphones plugged in, a Bluetooth headset connected), the sound moves to it without stopping, and an unplugged device is left for the default one within a second.

The track itself shapes the sound, worked out by Steam Audio (`lib\dlls\win64\phonon.dll`) from the stage's own pieces, about 7,000 to 11,000 triangles built in some 40 ms when the stage loads. A car behind a hill, a ramp or a wall is muffled, not cut: rays from you to each car say how much of the stage is in the way, and a car fully hidden is 9 dB quieter and loses most of its treble, less behind foliage or glass, which let sound through. Sound also bends over a crest and round the end of a wall, and Steam Audio finds that way round too: it places probes every 16 m, 2 m over the stage, and works out which of them see each other. When a car is hidden and a way round is found, the car is as loud and as bright as that path lets it be (if that is more than the muffling alone) and is heard from where the path comes from, over the crest or round the corner, rather than through the hill. The probes are worked out the first time a stage is raced, on a quarter of the processor's cores while the race starts (about 10 seconds for a 1.5 km stage; until then cars are only muffled), and kept, compressed, in `cache\sound-paths` (2 to 4 MB a stage, the 100 stages used most recently), so after that a stage is ready in a third of a second. Where the track closes in around you (walls, a half-pipe), it rings, with reverb times traced from its shape; out on open ground the sound stays dry. Your own car is never muffled. This costs about 0.1 ms every 30 ms on the sound thread, and about 2 ms every quarter second for the reverb and 0.2 ms every tenth of a second for the paths on a low-priority thread of its own. Without the DLL the sound plays on without it.

What is heard follows what the game decides: an engine plays only while the game would play one, with the same speed bands, wheelspin and full-power rules, and the sound reads the race without changing it, so recordings, replays and the determinism check are untouched. Instant replays and highlights are heard from the replay camera too: a replay keeps where each car was rather than how it was driven, so each engine follows how fast its car moves. A big crash close by pulls the engines and the music down for a moment, so the hit lands.

The music plays through the 3D sound as well, without BASS (which is free only for non-commercial use). The stage and menu tunes are tracker modules, decoded by libopenmpt (`lib\dlls\win64\libopenmpt.dll` and its helpers, BSD licensed): the same notes at the same level BASS played them, through an 8-tap interpolation filter rather than an Amiga's, looping at each module's own loop point. Their stereo separation is softened from an Amiga's hard left and right, which tires the ears on headphones, to 70%; `music separation <0-200>` in the developer console changes it at once and saves it. The menu MP3s are decoded by the mpg123 that ships with libopenmpt, Ogg Vorbis by its vorbisfile and WAV by Java Sound, all at the level BASS played them. The music goes straight to the left and right speakers, without HRTF, reverb or ducking. If the 3D sound stops, or Classic is chosen while music plays, BASS takes the music over, and gives it back when the 3D sound starts again. Without libopenmpt, or in Classic, the music plays through BASS as before.

The new sound has a volume for each part of the mix: `volume <master|engines|effects|music|interface> <0-100>` in the developer console, saved (effects are the crashes, skids, wreck, repair and wind; interface is the countdown, checkpoints and menus). `sound mono on` mixes everything to one channel, the same in both ears, for one ear or a single speaker (HRTF is off then). Output latency is about 30 ms (OpenAL Soft on WASAPI with 10 ms periods, the shortest Windows' shared mode gives).

`sound classic` in the developer console goes back to the original sound, exactly as it was (Java Sound, your car's engine only); `sound 3d` (the default) brings the new one back, at once and saved. If no sound device can be opened for it, Classic plays.

## Replays

Every race you play is kept in the `replays` folder next to the game, as the same kind of recording the determinism check uses (see below): the set-up, your input on every tick and where your car was after it. Each file is named for when the race was played, its stage and, if you finished it, in how many ticks (`race-20261116-221500-123-stage08-t01234.nfmr`, 1234 ticks of 53 ms). The 50 newest races are kept, and on every stage the fastest finish however old, so a best run is never lost. A race is about 200 KB. Hidden test runs and the smoke test keep nothing, unless `-Dnfm.replays=<folder>` names a folder to keep them in (which also moves a normal game's replays).

## Mods

Until 1.0 races play exactly as the original. What 1.0 is, the `classic` branch and what may change after it are in `openspec\specs\release-1-0\spec.md`.

The `mods` folder holds mods: each is a folder that mirrors `data`, its files replacing the game's files of the same name. The archives become folders (`models`, `images` and `sounds` for `models.radq`, `images.radq` and `sounds.radq`), and stages and music keep their paths, a mod's stage music replacing the game's whatever format either is in. Mods apply in the order `mods\load-order.txt` lists them, a later one winning where two replace the same file; `-name` turns one off, and folders it does not list come after, alphabetically. A file the game does not have is reported in the log, not added. `mods` in the developer console lists what applies; `mods reload` reads the folder again while the game runs: models the next time it is at the main menu, stages and music with the next race (they are read for every race), images and sounds at the next start. A replay notes the mods it was recorded with. Test runs (`nfm.verify`, `nfm.record`, `nfm.autotest`) leave mods out, so the checks always test the game itself, unless `-Dnfm.mods=on`; `-Dnfm.mods=off` leaves them out of any run. `mods\README.txt` explains it for mod makers.

## Extras

Additions to the original, each with a switch in the **Extras** tab of the settings, a `-Dnfm.<switch>` property for one run, and the developer console's `extras` command. Each changes the look, the sound or the races, so until 1.0 each is off by default (only the explosions' screen flash, part of the powerups, is on). Hidden test runs keep them off unless a property asks; `-Dnfm.features=<looks, races, all, none or switches>` turns groups on for one run, and `test\verify-races.ps1 -Features looks -NoGolden` proves the look and sound extras leave every recorded race as it was. The live test plays your saved switches, as your game does.

Each extra lives in its own folder under `src\nfm\lit\features\` behind one small `Feature` interface; `docs\features\` has a page for each. They read the race through `RaceView`, a copy filled after every real tick, draw through `Canvas` into the scene the graphics card draws (smooth between ticks, see-through by dithering), and take their place in the game's back-to-front order through `DrawQueue`. Race extras keep their state in the race state, so run-ahead and the recorded races cover them.

## Developer options

| Property | Meaning |
| --- | --- |
| `-Dnfm.profile=true` | Print simulation rate, render rate, time per tick and per frame, run-ahead ticks (their cost, and how many real ticks were checked against the prediction and missed it), the frame limit and the current phase every two seconds, including how long putting each frame on screen took and whether the GPU or software did it. With race frames on their own thread a `[draw]` line gives that thread's frame rate, the gaps between frames, and how far frames had to correct themselves at each new tick, and how long a change of the driving keys took to reach the screen ("key to screen": from the key event to the first frame handed to the display that shows it, so the display's own delay comes on top). |
| `-Dnfm.runahead=<true or false>` | Run-ahead for this run, see above. |
| `-Dnfm.smootheffects=<true or false>` | Smooth effects for this run, see above (hidden test runs: off unless this asks). |
| `-Dnfm.debug=false` | Do not open the developer console on the main menu. |
| `-Dnfm.fps=<auto, vrr, unlimited or a number>` | Frame rate limit for this run, see above. |
| `-Dnfm.uizoom=<auto, 1, 2 or 4>` | The UI scale for this run, see above. |
| `-Dnfm.renderer=<gpu or software>` | How frames reach the screen for this run, see above. Hidden test runs use software unless this asks for the GPU; they then also save `<name>-gpu.png`, the window read back from the graphics card, and in races `<name>-gpuscene.png`, the frame the card drew (at the window's size when that is larger than 1280x720), to compare with `<name>.png`, the same frame drawn in software. |
| `-Dnfm.motiontest=<dir>` | With `-Dnfm.autotest`: at race ticks 200 to 450 records the scene at 49 points between two ticks into `<dir>\motion-<tick>.bin`, to check that every corner moves smoothly (`python test\motion.py <dir>` measures how far each corner strays from smooth motion), and the scene kept for the tick moved to the same points into `kept-<tick>.bin` (`python test\kept.py <dir>` compares the two: how far apart their corners are, and halfway, where the kept scene was recorded, any polygon in another colour). With `-Dnfm.autotest.split` it does the same for both split-screen panes. |
| `-Dnfm.mods=<on or off>` | Use the mods folder in this run, see above (default: on, but off in test runs). |
| `-Dnfm.msaa=<off, 2, 4, 8 or 16>` | Smooth polygon edges with the GPU renderer for this run, see above. |
| `-Dnfm.ssaa=<off, 2x, 4x or 8x>` | Supersample races with the GPU renderer for this run, see above (`on` is 4x). |
| `-Dnfm.dynres=<on, off or 25-100>` | Dynamic resolution with the GPU renderer for this run, see above: the lowest share of the window, in percent, races may be drawn at. Hidden test runs draw the scene whole unless this asks. |
| `-Dnfm.batteryfps=<off or a number>` | Frame cap on battery for this run, see above. |
| `-Dnfm.mmcss=false` | Leave the game's threads out of Windows' Games scheduling, and let Windows throttle it to save power, see below. |
| `-Dnfm.backend=d3d12` | With the GPU renderer, draw with Direct3D 12 instead of 11, see above. |
| `-Dnfm.adapter=fast` | With the GPU renderer, draw with the graphics card Windows calls high-performance (the discrete one on a laptop with two) instead of the one Windows picks for the game, see below. |
| `-Dnfm.hdr.white=<auto or 80-500>` | Paper white in nits for this run, see above. |
| `-Dnfm.hdr.peak=<auto or 200-10000>` | The display's peak in nits for this run, see above. |
| `-Dnfm.hdr=<auto, off or force>` | HDR output with the GPU renderer for this run, see above; hidden test runs are SDR unless this asks. `force` sends HDR even to an SDR monitor, where Windows shows it as SDR again, and then also saves `<name>-glow.png` in races: how much each pixel of the scene glows. |
| `-Dnfm.sound=<3d or classic>` | Race sound for this run, see above. Hidden test runs make no sound; with `-Dnfm.sound.render` they start the 3D sound only to render it. |
| `-Dnfm.sound.render=<file.wav>` | With the 3D sound: mix into this file (48 kHz stereo, rendered for headphones) instead of a sound device, in step with the game, to listen to a hidden run afterwards. |
| `-Dnfm.sound.debug=true` | With the 3D sound: print every car's engine state once a second (running, throttle, on the ground, speed, where it is from the listener, and the five recordings' gains), and from Steam Audio how much of each car gets through the stage, the reverb times, how closed in the listener is, the stage's path probes (how many, baked or loaded, how long) and what the tracing costs. |
| `-Dnfm.music.separation=<0-200>` | The music's stereo separation in percent for this run, see above (default 70, or what `music separation` saved; hidden test runs use 70). |
| `-Dnfm.openmpt=<folder>` | Load libopenmpt and its helpers from this folder instead of `lib\dlls\win64`; a folder without them leaves the music to BASS. |
| `-Dnfm.steamaudio=<phonon.dll>` | Load Steam Audio from this file instead of `lib\dlls\win64\phonon.dll`; a name that is not there turns the muffling, paths and reverb off. |
| `-Dnfm.gamepad=<on or off>` | Read game controllers for this run, see above; `on` also in hidden test runs. |
| `-Dnfm.gamepad.test=true` | With `-Dnfm.gamepad=on`: a made-up pad, which SDL presents as any other, holds the right trigger and the stick to the right in races, so the smoke test shows a car driven by a pad. |
| `-Dnfm.presenter=false` | With the GPU renderer, draws and shows race frames on the game thread, between ticks, instead of on a drawing thread of their own. |
| `-Dnfm.projection=off` | With the GPU renderer, places the kept scene's corners on the processor for every frame instead of on the graphics card. |
| `-Dnfm.projectiontest=<N>` | Draws N race frames (one in 30) both ways, placed on the card and on the processor, and logs how many pixels differ; the pair that differs most is saved into the autotest folder as `projection-here.png` and `projection-card.png`. |
| `-Dnfm.retained=false` | With the GPU renderer, draws each race frame's whole scene again instead of moving the scene kept for the tick. |
| `-Dnfm.gpuscene=<false or picture>` | `false` draws races in software even with the GPU renderer, which then only puts frames on screen; `picture` draws them on the card at 1280x720 rather than the window's resolution. |
| `-Dnfm.window=<width>x<height>` | Size of the window's inside at start, for example `1920x1080` (default: the size it was left at, else 1280x720). Also sets the size of hidden test runs, which then save each screenshot twice when the size is not 1280x720: the picture itself and `<name>-window.png`, the window as shown. |
| `-Dnfm.custom.rivals=<0-32>`, `-Dnfm.custom.lineup=<default, novice, pro or custom>`, `-Dnfm.custom.picks=<16 counts, comma separated>` | The custom game's computer cars, their cars and the Custom lineup's picks for this run (default: as saved; hidden test runs: 6 and Default). |
| `-Dnfm.autotest.custom=<computer cars or saved>[,<default, novice, pro or custom>]` | With `-Dnfm.autotest`: plays a custom game instead, from the main menu through its page, the car and stage select, a race (screenshots every second through a staggered start, each group's start logged) that ends in the post-race highlight 30 seconds after your start, the results and the page again; `saved` keeps your custom game's choices. |
| `-Dnfm.autotest.split=<auto, side, stacked or saved>[,<computer cars>]` | With `-Dnfm.autotest`: plays split screen instead, from the main menu through its car select and settings tab, a race driven by both players (paused for an instant replay), its results, Race Again and a second race, with screenshots throughout; `saved` keeps the split screen settings as you left them. `-Dnfm.autotest.resize` resizes the window during the first race. |
| `-Dnfm.autotest.shots=false` | With `-Dnfm.autotest`: take no screenshots. Each screenshot holds the frames up for a moment, so this is how to time every frame of a run. |
| `-Dnfm.live=true` | With `-Dnfm.autotest`: play it your way, with your saved settings and mods, fullscreen switched off and on again as F11 does, but still off screen, silent and saving nothing (see the live test below). |
| `-Dnfm.features=<looks, races, all, none or switches>` | Extras for this run, see Extras: `looks` the look and sound ones, `races` the ones that change races, or switches by name separated by commas (`skidmarks,wingtrail`). A switch's own `-Dnfm.<switch>=<on, off or a value>` wins over it. Hidden test runs: off unless asked. |
| `-Dnfm.seed=<number>` | Every race of this run starts from this seed, so two runs race alike: the same opponents, effects and camera shake. The live test gives all its runs one. Without it each race gets a fresh random seed; a recorded race always replays its own. The game's log names each race's seed (`Race seed ...`). |
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

The race code (`Madness` for a car's physics, `Control` for a driver, `CheckPoints`, `Trackers`, `Stat`, `Wheels`) no longer reaches into the renderer's globals. Its random stream lives in a `World` object that the cars and drivers are given, and it reports what the HUD and the sound code react to (crashes, skids, a repair, the next checkpoint, the run to the finish) through `RaceEvents`, which `GameUi` (the menus and HUD, `xtGraphics` in the original source) implements with the original decision code. So the race can be captured, run ahead or rewound without the screen or the speakers taking part. The camera is still shared: the race reads back which cars are on screen.

### The corpus

`test\races` holds 20 races: one on each of the 17 stages, two long ones that end on their own, and one recorded at the real pace with frames drawn. Run the check after any change that could touch gameplay:

```
powershell -ExecutionPolicy Bypass -File build.ps1
powershell -ExecutionPolicy Bypass -File test\verify-races.ps1
```

`verify-races.ps1 -Fresh` runs each race in a game of its own, which is slower but shows nothing depends on the race before. `test\record-races.ps1` records more (`-Count`, `-Seed`, `-Ticks`, `-RealTime`).

### The performance gate

`.\test\perf.ps1` defends speed the way the race corpus defends gameplay. It plays the hidden smoke test three times with the GPU renderer and no frame limit, and takes the best run's median frames a second and median 99th-percentile frame gap. The first run on a machine saves them as its baseline in `test\perf-baseline.json` (kept per machine and per power source, and not in git, since other machines are not comparable). Later runs fail, with exit code 1, when frames a second drop more than 5% or the frame gap grows more than 20%. On battery a laptop's speed wanders by more than that, so there the numbers are reported but not judged. `-UpdateBaseline` saves a new baseline after a change that is meant to cost speed; `-Runs` sets how many runs to take.

### The live test

The other checks run the game as it ships, ignoring your settings, so they cannot tell you why it runs badly with yours. `.\test\live.ps1` can. It plays the game with your saved settings and mods and starts Java as `madness64.exe` does: the main menu, fullscreen switched off and on again (a new window and the graphics card started again, as F11 does), the settings screen, car and stage select, a race, the pause menu, the instant replay, the race again and the highlight. The window is real and the card presents to it as it does to yours, in your monitor's shape, but it sits off every monitor, never takes the keyboard or the mouse, a game controller can neither drive it nor shake, sound goes to a file and nothing is saved, so it can run while you use the PC. A run takes about three minutes.

The specs ask for 250 frames a second at 1920x1080 fullscreen, so a run plays there on top of your other settings. The game has no command-line setting for it (it keeps fullscreen and its resolution with your saved settings, in `data\cookies.radq`), so the run plays from a folder in `%TEMP%` of hard links to the files the game only reads, a copy of its `cache` (which the game writes to, so the run never touches yours) and its own copy of your saved settings, fullscreen at 1920x1080 put in, and the folder goes when the run ends. `-AsSaved` plays at your own resolution instead. `report.md` says which resolution each run had.

It lists each part with its frames a second, the frame times half and 99% of frames came within, the longest frame, late frames, the card's time per frame and how the frames were drawn, and flags what is wrong: a race drawn in software with the GPU renderer, a race part under 250 frames a second (`-MinFps`), a race under the frame limit (or the monitor's refresh rate without one; frames the card held up are not counted), uneven frames (99% of them taking over 2.5 times the median and longer than a frame of the limit or the refresh: quicker than that they look even on the screen) or late ones, a hitch in a race over 100 ms or a stall in a menu over 250 ms, Java starting without the startup cache `madness.aot` (a `-Quick` build has none, so the run does not show the game as it ships: build without `-Quick`; Java's own log of the cache is `aot.log` in the run's folder), the card near its time per frame. Two things a window off every monitor cannot show are notes instead, which do not count: with VSync its frames can wait for Windows' own clock rather than your monitor's refresh, which shows as half of them coming quicker than the refresh allows (the note gives their median against the refresh, about 15.9 ms against 16.7 at 60 Hz, and the late frames that clock makes; frames at the refresh or slower stay flags), and now and then the card holds a frame up for about half a second (the game was ready, the card, its driver or Windows took that long to take it), which Windows does now and then to a window off every monitor, so the test cannot tell whether your screen would show it. The same goes to `report.md` with your settings, the slowest frames of each race part and a race frame read back from the card, which is what your screen shows. Exit code 0 means nothing was flagged, 1 something was, 2 the game could not be run, 3 the run was inconclusive: on battery or with Battery saver on, when Windows slows the PC, or not at 1920x1080 fullscreen (`-AsSaved` with another resolution), when it cannot prove the 250 frames a second; plug in, or run without `-AsSaved`, and run again.

| Option | Meaning |
| --- | --- |
| `-Split` | Split screen with your split screen settings instead. |
| `-Custom <cars>[,<lineup>]` | A custom game instead (`32,pro` is the biggest field), or `saved` for your custom game's choices. |
| `-AsSaved` | At your saved window or fullscreen resolution instead of 1920x1080 fullscreen. The 250 frames a second are then not judged unless that is 1920x1080 fullscreen. |
| `-MinFps <n>` | The frames a second a race part must reach (default 250; 0 turns it off). |
| `-Set '<name=value> ...'` | One run more on top of yours with these `-D` settings, to compare: `-Set 'nfm.overlay=off','nfm.msaa=off'` makes two. The report then compares the races. |
| `-NoShots` | Take no screenshots, so no frame is held up by one. Without it a race is shot once every 10 seconds and the settings screen once a visit, read back from the card, as writing a screenshot takes a processor core from the frames being measured. |
| `-Out <dir>` | Where the runs go (default `%TEMP%\nfm-live\<time>`). |
| `-Seed <n>` | The race seed all runs play (`-Dnfm.seed`), so they race alike and the no-limit and 120 fps runs can be compared (default: one picked at random for the test). `report.md` names it, so a run can be played again with the same race. |

The game itself also sums up every part as you play: the log in `logs` gets a line such as `Frames in race: 287 a second over 41.2 s, half within 3.3 ms, 99% within 6.2 ms, longest 9 ms, kept scene 100%` for each, so a session that ran badly says where and how. A frame the card takes a tenth of a second or more to accept gets a line of its own, `The graphics card held a frame up 480 ms in main menu`, with what changed on the card just before it (a new swap chain, a new texture) when something did; the live test's `frames.json` keeps the same holds per part, under `cardHeld`.

### The sound check

`.\test\verify-sound.ps1` does for the 3D sound what the corpus does for gameplay. It plays three recorded races (stages 1, 8 and 14) hidden, with the 3D sound mixing into a file in step with the race rather than with the clock: the game's sound commands and ticks queue up in order and each tick is mixed for exactly its own length, so the same race gives the same samples every time, however fast it was played (about 85 seconds of race in 15 seconds). The stage's music is rendered with it, so the music path is checked too. Each render's SHA-256 is compared with this machine's baseline in `test\sound-baseline.json` (per machine and not in git, as OpenAL Soft and Steam Audio choose their code by processor). A change to what the new sound does, even a crash one decibel louder, fails with exit code 1; one that is meant is listened to with `-Keep` (the renders stay in `test\sound-renders`) and saved with `-UpdateBaseline`. Each race is also checked tick for tick, so the sound is seen not to touch it. Two things are held still for this: Steam Audio draws its reflection rays at random, so in step the reverb rings for a fixed 0.8 seconds, still as loud as the place is closed in, and the paths round hills (which wait for a bake that takes its own time) are left out; occlusion is traced as always.

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
