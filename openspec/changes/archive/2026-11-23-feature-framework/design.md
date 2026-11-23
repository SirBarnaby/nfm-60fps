## Context

Every extra the owner asked for on 23 Nov 2026 needs the same few things: to know what the cars did in the last tick, to draw polygons into the race's scene in the right place in the game's back-to-front order, to be smooth between ticks on the graphics card, to be see-through without breaking the opaque kept scene, and a switch that is off by default. The game has no z-buffer: `drawWorldObjects` sorts its objects by `ContO.dist` and draws the farthest first; flat road pieces have `grounded` of 1000 to 40000 in their models, so their distances are far larger than anything else's and they are drawn before every ramp, tree and car.

On the graphics card a race is recorded once per tick (`RETAINED_AT` = 0.5) into a `SceneList`, its corners kept as points in a `RetainedScene` and placed again for each frame by the card (`Shaders.PROJECTED_VS`) or the processor (`RetainedScene.placeOne`). A colour that is not opaque makes the recording unsupported (the frame falls back to software).

## Goals / Non-Goals

**Goals:**
- One package, `nfm.lit.features`, that the later changes fill one folder at a time.
- Look and sound features cannot write the race: they get copies, through `RaceView`.
- What features draw goes through the same kept scene as the rest, on the card, smooth between ticks, with no allocation per frame.
- Every switch off: the game runs the code it runs today, apart from a boolean check per hook.

**Non-Goals:**
- No feature here (they are the next changes). No sound path yet: the first feature that sounds (realism's hiss, the engine) adds it, through `nfm.lit.audio.Sound3D` only.
- Race features' state in `RaceState`: designed below, built with powerups.

## Decisions

1. **The race view is a copy, filled after each real tick.** `FeatureBridge` (in `nfm.lit`, where `Madness`, `ContO` and `Control` are package-private) copies each car's pose, speed, damage share, contact flags, inputs and air state into `RaceView`'s arrays, and works out each wheel's place on the ground (`keyx`, `grat`, `keyz` turned by the car's roll, pitch and yaw, as `Madness` does), whether it touches (within 8 units of the flat ground at 250 or of a tracker's top), the surface under it (`trackers.skd`) and its slip (`scx`, `scz` against the car's heading). Features see only `RaceView`; they could only write a copy. Rejected: handing features the race objects behind interfaces (more code in the big files, and nothing stops a cast).
2. **Only real ticks.** The hook sits after `raceTicks++` in the game loop, which run-ahead's speculative ticks (`playAndPutBack`) never reach. Hits come from `Madness.regx/regy/regz`, which also run on speculative ticks; the bridge drops them while `SoundManager.isDiscarding()` (the flag run-ahead sets) and keeps at most 32 a tick.
3. **Draw queue merged into the game's order.** Before `drawWorldObjects`, each feature that is on queues items: `ground` (drawn right after the flat road pieces: the first object in the sorted walk that is not `flat()`), `at(depth)` (drawn just before the first object nearer than `depth`, the `ContO.dist` formula with `grounded` 1, so a puff behind a car is covered by it), or `after(car)` (drawn right after car `car` itself, for things on its body). Items are kept in fixed arrays and sorted by one `long` key each, like `drawKeys`. The queue is only filled while `renderScene` draws, never in the invisible simulation step (`drawSimulationStep`), which the race reads back.
4. **Smooth between ticks.** Things fixed in the world are `CAMERA` points; things on a car are `CAR` points of its slot, so they follow the car's per-frame pose; moving things (particles) are a new kind, `MOVING` (7): the point after the last real tick and how far it moves in a tick, placed at `point + velocity * (share - base) + camera move`, where `share` is the frame's place between the two ticks (the kept scene's trail share) and `base` is 1 normally and 0 when run-ahead shows the tick ahead (`renderAheadScene`: there 0 is the last real tick). The same formula is in `RetainedScene.placeOne` and `PROJECTED_VS`, and `-Dnfm.projectiontest=40` checks they agree. In software (and for the recording itself) `Precise` works the corners out the same way.
5. **See-through by dithering.** `SceneList.DITHER coverage` (0-255) sets how much of each polygon after it is drawn; `SceneGeometry` puts it in the vertices' alpha (kept across colours until the next `DITHER`), and `COLOR_FS` drops a pixel where the coverage is not above a 4x4 Bayer threshold of its position. Opaque polygons have alpha 255 and are drawn as before. The only other user of that shader with an alpha below 255 was the white start flash (alpha 0, so nothing glows under it); it now has alpha 255 and draws with `COVER_BLEND` and a blend factor of 0, which writes the same white and the same alpha 0. In software `FeatureCanvas` uses a colour with that alpha instead (cached per colour and level). The look is the owner's "dithered" style (`powerups` explosions), and it keeps the scene opaque, so nothing needs sorting.
6. **Colours with the stage's fog.** `Canvas.fog` applies `Plane`'s eight fade bands to a colour by the point's distance from the camera, so a mark on the road fades into the distance as the road does.
7. **Switches.** `FeatureSettings` lists every switch once, with a cookie name starting `xtra` (no existing cookie contains it; none of the new names contains another). Read as every setting is: `-Dnfm.<id>`, else `-Dnfm.features` (`looks` = the look and sound features, `races`, `all`, or a list of ids), else the cookie (never in hidden runs, `GameSparker.readcookie` returns nothing there), else the default (off; the explosions' screen flash on). Saved when changed, from the game loop. The live test reads the saved switches, as the player's game does (AGENTS.md invariant 7); for a run with them on, `test\live.ps1 -Set nfm.features=looks`. A race reads its switches when it starts and keeps them to its end.
8. **Race features (designed now, built with powerups and handling).** They get a `raceTick` call inside `advanceRace` (so run-ahead and rollback run them), keep their state in fields that `RaceState` visits only while they are on (so every recorded race's hashes stay as they are), take their dice from `RaceRandom.derive(seed, STREAM_EXTRAS)`, and a recording made with any of them on stores which ones (`RaceLog` format 7); played back, a race uses what its file says, whatever the switches.
9. **Settings tab.** The Extras tab lists a row per registered feature. With six tabs, `tabSpacing()` is the box's width shared out (150 instead of 200), the same for drawing and clicking.

## Performance and the picture

- Off: each hook is `if (Features.anyOn)` or the like; the queue is not filled, nothing is drawn, nothing allocated.
- On: everything new is polygons in the kept scene, placed by the card like the rest; the per-frame cost is the card's. Fixed budgets live in each feature (a skidmark ring of about 4,000 pieces, pooled particles, capped debris), in arrays made at race start; the queue and canvas reuse theirs. 250+ fps at 1080p fullscreen is checked with `test\live.ps1` at the end of the work, with the features off (the default) and on.
- Dithering is one compare and a `discard` in the fragment shader for polygons below full coverage; opaque ones take the same path as now.

## Races stay identical

- With the features off nothing a race reads changes: `test\verify-races.ps1` (20 matched, golden frames all the same), `-Rollback 50`, and `verify-sound.ps1`.
- With look features on: `test\verify-races.ps1 -Features looks -NoGolden` (20 matched), proving they only read.
- The white flash's new state is checked by the golden frames that hold the start (unchanged) and on the card by the autotest's `-gpuscene` screenshots.

## Risks / Trade-offs

- [The dithered pattern shimmers when a polygon moves across it] → the pattern is fixed to the screen, so a still mark stays still; moving smoke shimmers a little, which reads as dithering, the look the owner asked for.
- [`ground` items on a ramp] → a mark above the flat ground is queued `at` its depth instead, among the objects.
- [Hits from `regx/regy/regz` in the rollback self-test's re-simulated ticks] → only in verify runs with features on, where the drawing is not checked; races stay the same either way.
