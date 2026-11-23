## Context

The extras framework hands each feature a copy of the race after every real tick (`RaceView`) and draws what it queues into the race's scene through `Canvas`, which the graphics card keeps and places for every frame. Damage and heat need three things the framework did not yet give: each car's model (which polygon is the bonnet, the windscreen, a light, a wheel), a way to recolour and reshape the car's own polygons as the game draws them (dirt, an open bonnet's bay, lights, broken glass, a bent wheel), and every light touch of a wall (scratches come from scrapes too light to dent). The game reports hits above what it takes to dent (`Madness.regx/regy/regz`), with the corner, a world axis and the force.

## Goals / Non-Goals

**Goals:** the eighteen requirements of `realism`, each with a check; only the look and the sound change (`verify-races -Features looks -NoGolden` gives 20 matched); the card draws everything new; 250+ fps; nothing allocated per frame or tick; nothing at all when the switch is off; Classic sound exact.

**Non-Goals:** damage that changes handling (the requirement says cosmetic only); a deformable bonnet mesh or texture-based cracks (the game has no textures); per-pixel heat shimmer (the game's scene has no post-processing pass), shown instead as faint dithered puffs.

## Decisions

1. **The car's own polygons.** `ContO` gets a drawing-only `carSlot` (the race car it is, set by the bridge while a `CarLook` feature is on, -1 otherwise) and tells the bridge which polygon `Plane.d` is drawing. `Plane.d` passes the colour it is about to draw through `CarLook.color` before the fog, which is where the kept scene records it too, so the processor and the card agree. `CarLook.lamp` says whether a light is lit (the game's lamp glow) or dark. With the switch off `carSlot` is -1 and each hook is one comparison.
2. **A bent wheel without a shader change.** A bent wheel's polygons are drawn from copies of their points tilted about the upright through the hub. The game (and the card's `PROJECTED_VS`) then spins them about the axle and steers them as always, so the tilt goes round with the wheel: a wobble. The model's own points are never written (the game dents them).
3. **Touches.** `Madness.regx/regz` hand every push to `FeatureBridge.touch` before the game decides whether it dents; the bridge keeps the strongest per corner and world axis for the tick (`RaceView.touchX/touchZ`). It records only, after the race's own arithmetic has the value and before it draws from the race's random stream, so races are unchanged.
4. **Hits in the car's frame.** The game pushes along world x or z; the extra turns the push by the car's yaw as the game's own dent code does. Along the car is a hit on an end; across, a side. Thresholds: 90 (hard: lights, bumpers, radiators, wheels, cracks), 170 (heavy, where the game plays its loud crash: the bonnet, side windows), 221 (a flat).
5. **The parts.** `CarParts` reads each model once a race: roles from the model's own flags (glass, `lightF`, `lightB`, wheel planes) and from each polygon's outward normal (normals are turned away from the body's middle, since models are not wound one way) and place (the bonnet: up-facing panels ahead of the windscreen in the upper body). The engine, radiators and exhausts are placed from the car's sheet.
6. **The sheets.** `data\extras\car-sheets.txt`, read through the game's files so a mod's own wins, re-read when it changes. Claude's suggestions from each car's look; the file says whether the owner has looked them over, and the check reports it as waiting until the owner says yes. The requirement is not met until then.
7. **Heat rates** follow the GTA V script and Assetto Corsa's brake disc FX: heat in proportion to speed while braking, the fronts taking 1.3 shares and the rears 0.7; cooling a share of the heat each tick, more at speed. Tuned on the check's four hard stops from top speed: fronts about 0.9 (orange), rears about 0.5; gone in about 3 s at speed, 6 s standing.
8. **What moves.** Particles (steam, smoke, haze, sparks, glass, bumper bits, the bonnet torn off) use the shared pool; a particle marked `keep` becomes debris where it lands (`CarLooks.debris`, a fixed ring of 400). Parts on the car are `onCar` polygons (they follow the car's smooth pose); the discs are `onWheel` (steered and spun with the wheel); the flapping bonnet is a `moving` polygon with each corner's move over the last tick, so it is smooth at any frame rate.
9. **Order.** Discs, cracks, scratches and drawn lamps are queued right after their car (they are on its near face, checked against the eye). The bonnet, flames and bumpers are queued at their own place, so the car covers them when they are behind it. Debris lies with the road, or among the objects on a ramp or raised road.
10. **Sounds.** Four clips made once by synthesis (`RealismSounds`), registered with the 3D sound by name and played at the car (`Sound3D.extraClip`, `carSound`): only the 3D sound plays them, never for a run-ahead tick. Classic sound never hears them.

## Performance

Per tick and car: a few dozen operations, a handful of particles. Per frame: up to about 20 polygons per damaged car, the live particles and the debris (each a small polygon). Per car polygon drawn: one call into the extra (array reads). Pools and arrays are made at race start. With the switch off nothing runs; the hooks cost a comparison each.

## Races stay identical

The feature reads `RaceView` only, draws from its own dice, and the hooks in the race code (`FeatureBridge.touch`, the existing `hit`) record values the race has already worked out. `test\verify-races.ps1 -Features looks -NoGolden` plays the 20 recorded races with it on.
