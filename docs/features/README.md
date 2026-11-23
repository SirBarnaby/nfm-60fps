# Extras

One page per extra. Each lives in `src\nfm\lit\features\<feature>\`, has a switch in the settings' Extras tab (off by default until 1.0) and is listed once in `Features.ALL`.

## How an extra plugs in

- **`Feature`**: `raceStarted`, `afterTick` (every real race tick, never a run-ahead one), `raceEnded`, and per frame `queue` then `draw`.
- **`RaceView`**: a copy of the race after each tick: each car's place, angles, speed, damage, keys, how long it has been in the air, and per wheel where it meets the ground, whether it touches, the surface and how it slides; plus the tick's hits (car, corner, axis, force). Filled by `nfm.lit.FeatureBridge`; the race's own classes are package-private, so an extra cannot reach them.
- **`DrawQueue`**: where each thing goes in the game's back-to-front order: `ground` (right after the flat road pieces), `at(x, y, z)` (among the objects by distance) or `after(car)` (right after that car).
- **`Canvas`**: polygons fixed in the world (`world`), moving through it (`moving`: placed for every frame from the last tick and a velocity, one for the whole polygon or one per corner so a shape can grow, turn or stretch smoothly, on the graphics card as well), or on a car (`onCar`: follows the car's smooth pose); `fog` for the stage's light and fog, `dither` for see-through (a 4x4 pattern on the card, alpha in software), `glow` for HDR.
- **`Dice`**: random numbers from the race's seed and the extra's name.
- **The cars' own polygons**: `RaceView.shape` holds each car's model as loaded (`CarShape`: corners, colours, glass, lights, wheels with their hubs and radii); a feature that is also a `CarLook` is asked, for every polygon of a race car drawn, what colour it is (`color`, before the fog, on the processor and in the kept scene alike), whether a light is lit (`lamp`) and whether a wheel's polygon is drawn tilted (`bend`, on copies of its points). `Canvas.onWheel` draws on a wheel, steered and spun with it.
- **Shared between extras**: `CarLooks` (how far each wheel is bent, which the skidmarks read for wavy marks; where debris that stays is kept) and `Debris` (a fixed ring of pieces lying on the road). A particle marked with `Particles.keep` becomes debris where it lands.
- **Touches**: `RaceView.touchX/touchZ`, the strongest push on each corner from a wall or a car in the tick, however light (a scrape), besides the hits.
- **`FeatureSettings`**: the switches: `-Dnfm.<switch>`, `-Dnfm.features`, the cookies (`xtra...`), the settings rows and `extras` in the developer console.

## Rules

- Look and sound extras only read the race: `test\verify-races.ps1 -Features looks -NoGolden` must give 20 matched.
- Nothing allocated per frame or per tick once a race runs: fixed rings and pools, made at race start.
- Everything drawn goes through `Canvas`, so the graphics card draws it in the kept scene.
- Race extras (powerups, the suspension) keep their state in the race state and take their dice from a race stream of their own; a recording notes which were on.

## Pages

- [Skidmarks and last race's skid zones](skidmarks.md)
- [The Radical One's wing trail](wingtrail.md)
- [Damage and heat](realism.md), with [the car sheets](car-sheets.md)
- [The helmet cam](helmetcam.md)
- [The rebuilt engine sound](enginesound.md)
