# Damage and heat

Switch: **Damage and heat** (`realism`, cookie `xtrareal`), off by default
until 1.0. Requirements: `openspec\specs\realism`.

Code: `src\nfm\lit\features\realism\`. Checks: `test\extras-check.ps1 realism`
(one per requirement, on a made-up car; "every car has wipers and an exhaust
pipe" also reads the game's own sixteen models).

Cars heat up, get damaged and show it, in a cosmetic way only. After each real
race tick the extra reads the race (speed, keys, which corner was hit, along
the car or across it, and how hard, every light touch of a wall) and keeps a
small state per car (`CarState`): temperatures, coolant, the glass, the lights, each wheel, dirt and scratches. It never
writes the race: `test\verify-races.ps1 -Features looks -NoGolden` plays every
recorded race with it on.

## The car

`CarParts` reads each car's model once a race (the `CarShape` the bridge copies
when the race starts) and its sheet (`CarSheets`, see
[the car sheets](car-sheets.md)):

- each polygon's role: body, **bonnet** (body panels facing up, ahead of the
  windscreen, in the upper part), **windscreen** (glass facing forward; the
  biggest such polygon carries the cracks), **side window** (glass facing
  sideways), other glass, front or rear **light** (the model's `lightF` and
  `lightB`), **wheel**;
- how low each polygon sits (dirt sticks below 45% of the body's height, fully
  at 85%; in the rain the dust washes off and a slide over wet soft ground
  throws mud, darker and in splashes, from 15% down: see
  [the weather](weather.md)) and which side a panel faces (scratches);
- the engine, from the sheet: under the bonnet (front), behind the cab (mid),
  over the rear (rear); the radiators at the front corners or in the side pods
  (none when air-cooled); the exhaust tips at the back (1 or 2) or as stacks
  behind the cab.

A model's polygons are not all wound one way, so each normal is turned to face
away from the body's middle.

## Hits

The game reports each hit with its corner, its world axis and its force above
what it takes to dent (`FeatureBridge.hit`, from `Madness.regx/regy/regz`).
Turned by the car's yaw into its own frame, the push is along the car (a hit on
its end) or across it (a hit on its side).

| Force | Along the car (an end) | Across (a side) |
| --- | --- | --- |
| any, while moving | | a scratch where it touched |
| from 90 (**hard**) | radiator crushed (front-cooled, front end), the engine hurt (its own end), that end's lights break, the wheel at that corner bends 3 to 9 degrees, the windscreen cracks (front end) | radiator crushed (side pods), the wheel bends |
| from 170 (**heavy**, the loud crash) | the windscreen cracks | that side's window shatters; the windscreen cracks |
| from 221 | the wheel at that corner goes flat | the same |

A roll onto the roof (a vertical hit from 170) cracks the windscreen.

The further damage (below) reads the same hits and the car's damage as the
game counts it (0 whole, 1 wasted): **badly damaged** is 0.6 or more, **low
HP** 0.7 or more.

| What is hit | When | What may happen |
| --- | --- | --- |
| By the windscreen: a hard hit on a front corner, or a vertical hit from 45 on a car on its roof | badly damaged | one time in two the electrics go |
| Underneath: a vertical hit from 30 on a car on its wheels, or bottoming out | low HP | one time in two an oil leak; one time in two the suspension is hurt |
| Where the exhaust is: a hard hit on a rear corner along the car (pipes out of the tail), across it on a rear corner (side pipes), or a vertical hit from 45 on its roof (stacks) | low HP | the exhaust breaks |
| Any corner, hard | low HP | the suspension is hurt |

Light touches, below a dent, still reach the extra (`FeatureBridge.touch`, the
strongest push per corner each tick, `RaceView.touchX/touchZ`): a side touch
while moving leaves a pale streak with three bare-metal chips on the panel
nearest the corner; a scrape that goes on lengthens the last streak. Eight
scratches are kept per side.

## Heat

- **Brake discs** take heat in proportion to the car's speed while it brakes
  (the work they do): the fronts 1.3 times the share, the rears 0.7. Each tick
  a disc loses 0.6% of its heat standing, 1.4% at top speed. Drums keep heat
  (1.3 times the heating, 0.7 the cooling), carbon sheds it (0.85, 1.3). A disc
  shows from 0.45: one hard stop from top speed leaves the fronts a dull red,
  four close together take them to about 0.9, orange; the glow is gone in
  about 3 s at speed and 6 s standing. It glows inside the wheel, seen
  through the wheel's openings (below).
- **The wheels** (`WheelFace`). The game's wheel has a closed face: six
  triangles from the rim's corners to the hub. With the extra on each is
  drawn as the game draws it and then, right on it, an opening (the dark
  inside of the wheel) and in it the brake disc, steel grey cold (dark iron
  for a drum, which also glows half as much) or glowing with its heat. What
  is left of the face is a hub cap (the inner 32%), six spokes (18% of a
  triangle's width at each edge) and the rim's lip (the outer 12%), in the
  rim's own colour and the game's own shading and outlines. Every corner of
  an opening and a disc is a point of the game's triangle, so from any side
  none of it is outside the wheel, and it is drawn at the triangle's place
  in the car's back-to-front order (`CarLook.onPlane`), so a wing or a spike
  in front of the wheel covers it as it covers the wheel. They steer, spin
  and wobble with the wheel. Four cars' wheels have a hub that stands out of
  the wheel (Nimi and Kool Kat a little, EL KING more, Sword of Justice a
  spike 39 units long): an opening painted on that would be out in front of
  the wheel, so their face is drawn flat, dished one unit (through the
  extra's `bend`), and the point is put back on the hub, from the hub cap's
  edge (up to 50% out under a long spike) to where the game's point was.
  Beyond the distance where the game stops drawing a wheel's face there are
  no openings either.
- **Exhaust** heat rises with throttle and speed and falls slowly. A stopped
  car whose exhaust is above 0.35 **ticks**: a quiet metallic tick, less often
  as it cools, for about 15 s after a long fast run.
- **Engine** heat rises the same way; above 0.45 a faint **haze** rises over
  the engine. The exhausts shimmer at idle, and behind them when hot.
- **Lift-off flames**: lifting off the throttle above 80% of top speed throws
  flames from the exhaust tips for 3 or 4 ticks (5 or 6 with a turbo, longer
  flames), flickering; **nitrous** (the powerups) burns a steady blue flame.
- **Exhaust pops** (sound, "Exhaust pops are heard and seen"): a car that
  pops (its sheet: a charged engine, or `pops=yes`) bangs with the first
  flame of a lift-off and pops on while the flames last, 45% of the ticks.
  Each pop is a recorded pop from the exhaust and, in the same tick, a
  near-white burst at one of the pipes, wider than the flame and glowing in
  HDR; a charged engine's first bang is a backfire 35% of the time, louder,
  with a bigger flash. A car that does not pop throws its flames in silence.
  With the rebuilt engine sound the pop is mixed into the exhaust's own
  stream; without it, played at the car.

## Damage

- **Steam**: a crushed radiator leaks from its corner: puffs leave with the
  car's speed and fall behind (back over the bonnet at speed), and rise almost
  straight up when the car stands. The coolant runs out over 30 s to a minute,
  and the steam thins with it.
- **Smoke**: the engine's damage is the car's damage, or more after rams on
  its own end. Above 0.5 light grey wisps, above 0.7 blue-grey smoke on the
  throttle, above 0.88 dark puffs, from where the engine sits. A ram on the
  engine's end adds 0.15 and more with the force, so one knock no longer
  smokes.
- **No bonnets, no bumpers**: nothing stands up, hangs or tears off (Owner,
  2026-11-26).
- **Repair hoops**: a car the game repairs (`Madness`, the hoop's seventh
  tick) is repaired here too, all of it: coolant, engine, glass, lights, bent
  and flat wheels, scratches. Heat and dirt stay; they are not damage.
- **Rams with spikes**: a car whose sheet says `spikes=yes` (Sword of
  Justice) throws a metal glint and 5 to 8 sparks from where it touches a car
  it rams, with a ring, once every three ticks at most.
- **Glass**: each crack is a star of six rays and a ring across them, standing
  just off the windscreen (three stars at most). A shattered side window shows
  its empty frame; 12 to 16 bits fly out and fall to the road.
- **Wheels**: a bent wheel's polygons are drawn tilted about the upright
  through the hub (`CarLook.bend`, on copies: the model's own points, which
  the game dents, are never written); spun with the wheel, the tilt goes round
  and the wheel wobbles. The skidmarks read the same bend (`CarLooks`), so its
  marks are wavy. A flat tyre throws sparks from its rim.
- **Lights**: braking (the brake key while moving forward, or the handbrake)
  lights the rear lights with a lamp's glow; a hard hit on an end leaves that
  end's lights dark. A car with no rear lights in its model (M A S H E E N)
  gets two drawn lamps.
- **Dirt**: on soft ground the lower body and the wheels take the ground's
  colour, full in about 13 s; on tarmac it wears off slowly (about a minute and
  a half at speed). The game's own shading is kept.
- **Bottoming out**: landing faster than 18 a tick after six ticks in the air
  throws 8 to 20 sparks from under the car, with a thud and a scrape.
- **Rattle**: a car more than a quarter damaged rattles over bumps (bumpy
  ground, or a jolt), more often at speed; a whole car never does.
- **Debris**: glass and the skidmarks' clods
  stay where they fall for the rest of the race, 400 at most, the oldest going
  first. On a ramp or a raised road they are drawn among the objects.

## Wipers and pipes on every car

`Fitted` finds a place for them on the model when a race starts, and the car
select does the same for the car it shows (`FeatureBridge.showroom`, around
`GameUi.drawSelectCar`'s `model.d`).

- **Wipers**: two blades parked at the foot of the windscreen (`Wipers`, the
  weather's; in the rain the weather draws them wiping). A model with no
  windscreen has nothing to carry them: of the game's sixteen that is
  Formula 7, and the requirement's check fails on it until the owner says
  what it should have.
- **Pipes**: a square tube in steel with a dark mouth, five polygons (the
  sides turned to the eye and the mouth are drawn). Its half width is 2.5% of
  the body's width (1.2 to 4 units). Where, by the sheet: **out of the tail**
  (1 or 2, low on the rear face where the exhaust ends, three widths out),
  **stacks** (upright behind the cab, from what they stand on to a little
  above the roof) or **side pipes** (`pipes=side`: along each sill, ending
  ahead of the rear wheel). A car whose sheet says `pipe=own` has pipes in
  its model and gets none: Lead Oxide, EL KING, M A S H E E N. The flames,
  pops and shimmer come out of the drawn mouths.
- A pipe the car would hide is drawn right before the car
  (`DrawQueue.under`), one seen from behind where it comes out right after it.
- The log names what each model got when it first races (`[extras] damage:
  nimi is fitted with wipers on its windscreen, 1 pipe out of the tail`).

## The further damage

- **Electrical damage**: the wipers wipe on by themselves, a wipe every 24
  ticks, and the washer sprays the glass at the start of each (two jets of
  pale drops up the glass). The water lasts 2 to 3 minutes (the dice say how
  long); after it the blades rub over dry glass. Heard from the car: a wipe
  over wet glass with the spray, then a quieter dry one (0.15 against 0.34).
  From the helmet of a closed car the player sees their own blades sweep. A
  car with no wipers has no such damage.
- **An oil leak**: a spot of oil under the engine every 90 to 210 units driven
  (a drip every 3 s standing), flat on the track, kept for the race (the 600
  latest). Only seen: the race never reads them. After 2 to 3 minutes the
  engine runs harsh: a crackling loop under its own sound.
- **A broken exhaust**: a blatting loop under the engine's sound from then
  on, louder for a loud engine (`loud`), its pitch following the revs. Each
  car has its own pitch (`CarSheets.raspPitch`): by how its engine revs and
  its cylinders, a little by its name, and then set at least 2% from the
  next car's, from 0.85 for a truck to 1.41 for a screamer.
- **A damaged suspension**: while the car turns 1.5 degrees a tick or more on
  the ground at 4 units a tick or more, a faint metallic squeak every 7 to 13
  ticks (gain 0.1 to 0.18; a crash's thud is up to 1).
- A repair hoop mends all four; a wasted car's wipers stop.

The two loops are laid under the car's engine by the 3D sound
(`Sound3D.RoughVoice`, `Car.rasp/harsh/raspPitch`), so they are heard with
the 3D sound only, from the car, and from the computer's cars too.

## The game's hooks

- `ContO.carSlot` (set by the bridge while an extra recolours the cars) tells
  `ContO.d` which race car it draws, and the bridge which polygon.
- `Plane.d`: the colour a race car's polygon is drawn in passes through the
  extra (`CarLook.color`, before the fog: the same on the processor and in the
  kept scene); `CarLook.lamp` lights or darkens a light; a bent wheel's
  polygon is drawn from tilted copies of its points (`CarLook.bend`), as is
  the face of a wheel whose hub stood out.
- `ContO.d`: right after it draws a race car's polygon it asks the extra
  what to draw on it (`FeatureBridge.onPlane`, `CarLook.onPlane`, with the
  colour `Plane.d` just drew it in): a wheel's openings and disc. Not asked
  for a polygon the game left out, is breaking up or draws black.
- `Madness.regx/regz`: every touch goes to `FeatureBridge.touch` before the
  game decides whether it dents.
- `Sound3D.extraClip/carSound/carPop`: the extra's clips, each a recording
  (`RealismSounds` gives them from `features\sounds`: a metal ping for the
  tick, a collision for the thud, metal on concrete for the scrape, loose
  metal for the rattle, swords for the spikes' ring, exhaust pops and a
  backfire, wipers over wet and dry glass, a washer, a squeak;
  `sounds\README.md`), played at the car by the 3D sound only.
  Classic sound never hears them.
- `Particles.keep`: a particle stays as debris where it lands
  (`CarLooks.debris`).

## Cost

Off, nothing runs and every hook is one check. On, the state is a few dozen
numbers per car, fixed pools (900 particles, 400 pieces of debris, 600 spots of oil) and a few
polygons per damaged car; nothing is allocated per frame or tick (the sounds'
commands excepted, a few a second at most).
