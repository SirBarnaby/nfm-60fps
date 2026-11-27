# Extras

One page per extra. Each lives in `src\nfm\lit\features\<feature>\`, has a switch in the settings' Extras tab (off by default until 1.0) and is listed once in `Features.ALL`.

## How an extra plugs in

- **`Feature`**: `raceStarted`, `afterTick` (every real race tick, never a run-ahead one), `raceEnded`, and per frame `queue` then `draw`.
- **`RaceView`**: a copy of the race after each tick: each car's place, angles, speed, damage, keys, how long it has been in the air, and per wheel where it meets the ground, whether it touches, the surface and how it slides; plus the tick's hits (car, corner, axis, force). Filled by `nfm.lit.FeatureBridge`; the race's own classes are package-private, so an extra cannot reach them.
- **`DrawQueue`**: where each thing goes in the game's back-to-front order: `ground` (right after the flat road pieces), `at(x, y, z)` (among the objects by distance) or `after(car)` (right after that car; `afterFirst(car)` before the others there; `under(car)` right before the car, for what the car hides; `within(car, plane, x, y, z)` as a part of the car, among its own polygons by distance and never before polygon `plane`, so the car covers it exactly as it covers its own parts; `near(x, y, z)` as `at`, but as a part of a car where it lies next to one, so a thing under or against a car does not show through it). It also says whose frame it is and where the camera looks (`player`, `look`, `aspect`).
- **`Canvas`**: polygons fixed in the world (`world`), moving through it (`moving`: placed for every frame from the last tick and a velocity, one for the whole polygon or one per corner so a shape can grow, turn or stretch smoothly, on the graphics card as well), or on a car (`onCar`: follows the car's smooth pose); `fog` for the stage's light and fog, `dither` for see-through (a 4x4 pattern on the card, alpha in software), `glow` for HDR.
- **The stage's air**: `Feature.air` is asked for each line of a stage's file about its sky, light, fog and ground as it loads (`StageAir`) and may change the numbers; `GameHooks.ground` and `cover` change the ground's colour and what lies on upward faces during a race, `GameHooks.bed` loops a recording around the listener, `Canvas.screen` draws fixed on the picture. `Weather` says what the weather is.
- **`Dice`**: random numbers from the race's seed and the extra's name.
- **The cars' own polygons**: `RaceView.shape` holds each car's model as loaded (`CarShape`: corners, colours, glass, lights, wheels with their hubs and radii); a feature that is also a `CarLook` is asked, for every polygon of a race car drawn, what colour it is (`color`, before the fog, on the processor and in the kept scene alike), whether a light is lit (`lamp`) and whether a wheel's polygon is drawn tilted (`bend`, on copies of its points); `CarLooks.spin` and `turn` say how the wheel stands, and a `bend` that gives the corners already spun and steered sets `CarLooks.baked`). Every `CarLook` that is on is asked in turn, each given what the one before made of the colour and the corners. A `CarBody` is asked the same for a body polygon (`body`); two may answer one polygon, the second on what the first gave. `Canvas.onWheel` draws on a wheel, steered and spun with it.
- **Shared between extras**: `CarLooks` (how far each wheel is bent, which the skidmarks read for wavy marks; where debris that stays is kept) and `Debris` (a fixed ring of pieces lying on the road). A particle marked with `Particles.keep` becomes debris where it lands.
- **Recordings**: `sounds.Recordings` gives the 3D sound an extra's clip from a recording in `src\nfm\lit\features\sounds\` (16-bit mono WAVs at the game's rates, cut by `sounds\make.py` from the sources `sounds\sources.tsv` names with their makers and licenses). An extra's sound is a recording, not a synthesized stand-in.
- **Touches**: `RaceView.touchX/touchZ`, the strongest push on each corner from a wall or a car in the tick, however light (a scrape), besides the hits.
- **Explosions**: `BlastPush` (in the race: throws and damages the cars near one), `Explosions` (the look: fireball, crater, flash, sound, and smoke that the wind, the ground, the cars and later explosions move) and `Wind` (one wind a race, from its seed) are shared by every extra that explodes. `CheckRace` is the made-up race their checks play on.
- **`FeatureSettings`**: the switches: `-Dnfm.<switch>`, `-Dnfm.features`, the cookies (`xtra...`), the settings rows and `extras` in the developer console.

## Looking closer

- `-Dnfm.extras.cost=true`: how long each extra's work takes, summed up in the log when a race ends (`[extras] cost of weather: ...`): per tick its part of the race tick and what it does after one, per scene drawn its queueing, its drawing and its share of the cars' own polygons, each with the worst and how many took over 2 ms.
- `-Dnfm.autotest.keys=<tick>:<keys>,...` drives the hidden test race's player from a script (U D L R H, ticks counted from the race's start; `120:UL,180:U` holds accelerate and left from tick 120, then accelerate alone), and `-Dnfm.autotest.telemetry=<file>` writes a line a tick for every car (place, heading, speed, velocity, where it goes, skid, ground, steering, wheels touching, wasted), also from the autopilot's races and from replayed ones (`-Dnfm.verify`). The owner's saved races (`replays\race-*.nfmr`) replay in the cloud this way: a handling bug can be read off the car's own numbers.
- Each race part's line in the log ends with the game thread's side: how long after a tick was due the presenter had its frames, and the longest tick, tick ahead, kept scene and making ready for the card (`FrameLog.ticks`). Uneven motion with these short is the presenter's or the card's; with these long it is the game thread's, the extras' work among it.
- `-Dnfm.weather.show`, `-Dnfm.physx.show`, `-Dnfm.personality.show`, `-Dnfm.litter.show`: a race that starts with the thing to look at.

## Rules

- Look and sound extras only read the race: `test\verify-races.ps1 -Features looks -NoGolden` must give 20 matched.
- Nothing allocated per frame or per tick once a race runs: fixed rings and pools, made at race start.
- Everything drawn goes through `Canvas`, so the graphics card draws it in the kept scene.
- Race extras (powerups, exploding wrecks, the suspension, the tailwhip) keep their state in the race state and take their dice from a race stream of their own; a recording notes which were on.

## Pages

- [Skidmarks and last race's skid zones](skidmarks.md)
- [The Radical One's wing trail](wingtrail.md)
- [Damage and heat](realism.md), with [the car sheets](car-sheets.md)
- [The helmet cam](helmetcam.md)
- [The rebuilt engine sound](enginesound.md)
- [Powerups](powerups.md)
- [Exploding wrecks](wrecks.md)
- [Tailwhip](tailwhip.md)
- [Suspension handling](handling.md)
- [The weather](weather.md)
- [Car personalities](personality.md)
- [PhysX debris](physx.md): loose pieces moved by NVIDIA PhysX, a look only
- [Custom game](customgame.md): a way into a race, not a part of one (`Feature.playsInRaces` is false), so a race with only its switch on runs, and is recorded, as one with no extra
