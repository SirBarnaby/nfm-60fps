# Plan status

Every item of the modernisation plan, checked against `staging` on 17 Nov 2026.
✅ done, ❌ not done. Proof is a commit, file or command to look at. Items the plan
itself said to skip (raw Vulkan, DLSS/FSR/TAA, Vector API, Valhalla, PhysX for cars,
hand assembly, fast inverse square root, occlusion culling, LOD, frame generation,
thread pinning, large pages, GraalVM) are left out.

## Safety net (Phase 0)

| Item | | Proof |
| --- | --- | --- |
| Separate random streams for race and drawing | ✅ | `RaceRandom.java`, f278979 |
| StrictMath in the physics | ✅ | `Madness.java`, `Control.java` use StrictMath |
| Record inputs, not positions | ✅ | `RaceLog.java`, `test\races\*.nfmr` |
| Hash the state every tick | ✅ | bd9ca19, `test\verify-races.ps1` |
| Run races without a window | ✅ | `verify-races.ps1` (hidden, ~200 s for 20 races) |
| Corpus of about 30 races on two PCs | ❌ | 20 races, one PC only |
| Golden screenshots | ✅ | `test\golden`, e023195 |

## Stack

| Item | | Proof |
| --- | --- | --- |
| GPU rendering through bgfx | ✅ | `src\nfm\lit\gpu\`, c8b45c7 |
| Direct3D 12 backend | ✅ | 39a2ab3, `-Dnfm.backend=d3d12` (D3D11 stays default: faster) |
| Vulkan / Linux / Steam Deck build | ❌ | |
| MSAA | ✅ | a38bd5f, console `msaa 4` |
| Supersampling | ✅ | f7c3fa2, console `ssaa on` |
| SDL3 gamepads and rumble | ✅ | `Gamepads.java`, 3af9371 |
| SDL3 for keyboard and window | ❌ | keyboard moved to AWT listeners instead (`GameInput.java`, be7f27a) |
| Steam Audio | ✅ | `audio\SteamAcoustics.java`, `lib\dlls\win64\phonon.dll` |
| miniaudio | ❌ | OpenAL Soft used as the mixer instead (`audio\Sound3D.java`) |
| libopenmpt music, BASS gone from the 3D sound | ✅ | `audio\MusicCodecs.java`, 4652561 |
| Generational ZGC | ✅ | 06ab760, `build.ps1` jlink options |
| AOT cache | ✅ | `madness.aot`, `madness64.ini` |
| Jolt cosmetic physics | ❌ | |
| Rollback netcode | ❌ | world is copyable (a0e4fd6), no networking |

## Optimisations

| # | Item | | Proof |
| --- | --- | --- | --- |
| 1 | GPU renderer | ✅ | 456a240, f588bbb, d8febce |
| 2 | Multithreaded CPU rasteriser | ❌ | GPU came first |
| 3 | No allocation per polygon | ✅ | 2d9b024, `Plane.java` reused arrays |
| 4 | Physics compiled by the JIT | ✅ | 2d9b024, `-XX:-DontCompileHugeMethods` (flag, not a split) |
| 5 | A slow frame never delays a tick | ✅ | 8d82520, drawing on thread `nfm-presenter` |
| 6 | No per-tick allocations | ✅ | 3e4833e packed-long sorts |
| 7 | ZGC + AOT cache | ✅ | 06ab760 |
| 7 | Parallel asset loading | ✅ | 33232d8 (picture at 2.2–2.5 s, was 3.0–3.4 s) |
| 8 | Rename decompiler names | ✅ | e3c72f5 … a5cc623 (bytecode unchanged); `xtGraphics` is now `GameUi` |
| 8 | Split run() and xtGraphics | ❌ | only `advanceRace()` extracted |
| 9 | Stable O(n log n) painter's sorts | ✅ | 3e4833e |
| 10 | View test above and below | ❌ | |
| 11 | Grid culling | ❌ | |
| 12 | Transform models on worker threads | ❌ | done on the graphics card instead (row 23) |
| 13 | Instanced draws per track piece | ❌ | scene recorded once per tick instead (d8febce) |
| 14 | High-resolution frame timer | ✅ | `PreciseSleep.java` |
| 15 | MMCSS for the game threads | ✅ | `WindowsScheduling.java` ("Games") |
| 15 | MMCSS Pro Audio for the audio thread | ❌ | OpenAL Soft's own thread |
| 16 | Out of EcoQoS throttling | ✅ | `WindowsScheduling.java` |
| 17 | Pick the fast GPU | ✅ | `gpu\GpuAdapter.java`, `-Dnfm.adapter=fast` (no native launcher) |
| 18 | Compact object headers | ✅ | `build.ps1` jlink options |
| 19 | Shaders compiled ahead of time | ✅ | d213fb9, `gpu\Shaders.java` |
| 20 | Spare time goes to quality | ❌ | only dynamic resolution steps up |
| 21 | Battery mode: 60 fps cap | ✅ | console `fps battery`, cookie `batteryfps` |
| 21 | Battery mode: sound reflections pause | ❌ | |
| 22 | Performance gate | ✅ | `test\perf.ps1` |
| 23 | Corners placed on the graphics card | ✅ | `gpu\ProjectedScene.java`, `Shaders.PROJECTED_VS`; `-Dnfm.projectiontest=40` at most 0.008% of pixels differ |
| | Quake-style span rasteriser | ❌ | GPU came first, as the plan allowed |
| | Flat data layout for models | ❌ | |

## Input lag

| Item | | Proof |
| --- | --- | --- |
| Speculative tick (run-ahead) | ✅ | 3e4833e, console `runahead on` |
| "Smoothing: off" stopgap | ❌ | not needed once run-ahead landed |
| Max frame latency 1 | ✅ | d009d0f |
| Cap below refresh for G-Sync/FreeSync | ✅ | 299a4c8, console `fps vrr` |
| Input timestamps | ✅ | `GameInput.java` (age from the event's time) |
| Latency readout in the profile | ✅ | 299a4c8, `[draw]` "key to screen" |
| Flash test / 240 fps video sign-off | ❌ | needs you and a camera |

## Image quality

| Item | | Proof |
| --- | --- | --- |
| Window scaling | ✅ | 3e4833e, `FrameScaler.java` |
| Dynamic resolution | ✅ | 1f036c2, `gpu\DynamicResolution.java` |
| Resolution-aware outlines | ✅ | f588bbb (lines widen with scale) |
| Fullscreen (F11, Alt+Enter) | ✅ | 3e4833e; resolution choice in the settings (v1.0 feedback); never tested on your screen |

## HDR

| Item | | Proof |
| --- | --- | --- |
| scRGB output | ✅ | a533ff1 |
| HDR10 fallback | ❌ | |
| Detect HDR and SDR white per monitor | ✅ | `gpu\DisplayColor.java` |
| Paper white | ✅ | 5c5380f, console `hdr white` |
| Highlights roll off into the peak (BT.2390) | ✅ | 5c5380f, `HDR_FS` in `gpu\Shaders.java` |
| HUD at paper white | ✅ | a533ff1 |
| HUD brightness slider | ❌ | |
| Calibration screen | ❌ | |
| HDR screenshots (JPEG XR) | ❌ | |
| Proof the look is kept | ✅ | `-Dnfm.hdr=force` capture matches the SDR frames |

## 3D sound

| Item | | Proof |
| --- | --- | --- |
| Every car audible | ✅ | 6380ac0 |
| Smooth engines from the five recordings | ✅ | `audio\Sound3D.java` (gain mix + pitch bend) |
| Granular engines | ❌ | |
| Distant engines cheaper | ✅ | `Sound3D.java` `loudestOnly()` |
| HRTF | ✅ | OpenAL Soft HRTF |
| Your own SOFA HRTF file | ❌ | |
| Occlusion and transmission | ✅ | c062809 |
| Reverb from the track | ✅ | c062809 (live parametric, not baked) |
| Pathing over crests | ✅ | 89ce968 |
| Distance, air absorption, Doppler | ✅ | `Sound3D.java` `AIR_ABSORPTION`, `DOPPLER` |
| Doppler as a delay line (late far crashes) | ❌ | pitch only |
| Voice pool | ✅ | `Sound3D.java` `EFFECT_VOICES = 24` |
| Impacts by force | ✅ | `Sound3D.carCrash(car, force, kind)` |
| New surface sounds (rolling, landings, scrapes) | ❌ | need new samples |
| Buses and ducking | ✅ | ed84d49, console `volume engines 80` |
| -24 LUFS and night mode | ❌ | |
| Music stereo separation | ✅ | 4652561, console `music separation 70` |
| Adaptive music | ❌ | |
| Replays heard from the replay camera | ✅ | 90bd25d |
| Follows the default device | ✅ | `Sound3D.java` `reopen()` |
| About 10 ms output latency | ❌ | about 30 ms (Windows shared mode) |
| Mono and a slider per bus | ✅ | ed84d49, console `sound mono on` |
| Markers for heard cars, announcer captions | ❌ | |
| Sound renders checked by hash | ✅ | `test\verify-sound.ps1` |
| Seven-car stress run counting underruns | ❌ | |
| Core reports sound events | ✅ | `RaceEvents.java`, f278979 |
| Sound command log checked tick for tick | ✅ | 68654a2 |
| Classic sound switch | ✅ | console `sound classic` |
| Classic through the new mixer, proven by null test and ABX | ❌ | Classic kept as the untouched original path |

## Features

| Item | | Proof |
| --- | --- | --- |
| Saved replays | ✅ | edd0da3, `replays\` |
| Ghost cars, time trial | ❌ | you said not yet |
| Online play | ❌ | |
| Split-screen | ✅ | 836c8b7: two players, each with a camera, HUD, keys, pad and sound; each pane kept once a tick and placed by the graphics card for every frame, as one player's race is (about 200-275 fps at 1080p), `-Dnfm.autotest.split=auto,3` |
| Widescreen, 4K, MSAA, HDR | ✅ | true widescreen: the race takes the window's shape from 5:4 to 32:9 with no bars, races unchanged (77b1adb, AGENTS.md invariant 9); menus and HUD at native size with UI scale Auto/1x/2x/4x (v1.0 feedback) |
| Shadows, bloom, motion blur | ❌ | |
| Weather and time of day | ❌ | |
| GPU particles | ❌ | |
| Debris and breakable props | ❌ | |
| Photo mode, TV cameras | ❌ | |
| Gamepad and rumble | ✅ | 3af9371 |
| Key rebinding | ✅ | 38aa629: two keys per action on the Controls tab, split screen's on its own tab |
| Analog steering | ❌ | |
| Settings screen in the game | ✅ | main menu and pause menu, every console setting; smooth effects added |
| Bot chat | ❌ | |
| Stage editor | ❌ | |
| VR, adaptive triggers, path-traced photos, video export, leaderboards, rewind, trained bots, telemetry, retro filters, colour-blind palettes | ❌ | |

## AI-moddable

| Item | | Proof |
| --- | --- | --- |
| World object for the race | ✅ | f278979 |
| Sealed core with no static state | ❌ | statics remain in Medium and GameUi |
| Drawing and sound never write the race | ✅ | AGENTS.md invariant 5, checked by verify-races |
| Content as data files | ❌ | |
| Mods folder with load order and reload | ✅ | eaf584d, `mods\README.txt`, console `mods reload` |
| Event hooks | ✅ | `RaceEvents.java` (crash, skid, repair, checkpoints; no lap or race-end yet) |
| AGENTS.md | ✅ | c3122a6 |

## v1.0

Tagged `v1.0` on `staging`, 19 Nov 2026: true widescreen, the HUD sent to the
graphics card by changed rows only (about 147 MB down to 1–2 MB every 2 s),
and the cleanup after them. The 20 recorded races match tick for tick, with
and without `-Rollback 50` and in 21:9 and 5:4 windows; the 80 golden frames
are the same. It is the last version whose races are the original's: what
comes after (tick rate, handling, deformable stages) is planned in
`AFTER-1.0.md`.

The tag was moved the same day to take in the first feedback on it: the menus
and HUD at their own size in pixels with a UI scale (Auto, 1x, 2x, 4x) in
place of the UI resolution setting, which drew 1080p at 4 frames a second;
the race drawn at the window's resolution behind them; no black areas at any
shape; a fullscreen resolution; pictures only beside the settings that change
what is seen or heard; a soft heap limit and reused colours, so memory no
longer climbs; and a fix for a crash when Windows asked for a paint before
the first picture. The first race's stutter was the old UI resolution's Auto
drawing every menu and HUD frame scaled in Java2D. Races still match as above.
