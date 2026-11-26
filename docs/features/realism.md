# Damage and heat

Switch: **Damage and heat** (`realism`, cookie `xtrareal`), off by default
until 1.0. Requirements: `openspec\specs\realism` (all eighteen).

Code: `src\nfm\lit\features\realism\`. Checks: `test\extras-check.ps1 realism`
(one per requirement, on a made-up car).

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
  at 85%) and which side a panel faces (scratches);
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
  about 3 s at speed and 6 s standing. Drawn as a glowing octagon with a dark
  hub on the wheel's outer face, steered and spun with the wheel
  (`Canvas.onWheel`), only when that face is turned to the eye.
- **Exhaust** heat rises with throttle and speed and falls slowly. A stopped
  car whose exhaust is above 0.35 **ticks**: a quiet metallic tick, less often
  as it cools, for about 15 s after a long fast run.
- **Engine** heat rises the same way; above 0.45 a faint **haze** rises over
  the engine. The exhausts shimmer at idle, and behind them when hot.
- **Lift-off flames**: lifting off the throttle above 80% of top speed throws
  flames from the exhaust tips for 3 or 4 ticks (5 or 6 with a turbo, longer
  flames), flickering; **nitrous** (the powerups) burns a steady blue flame.

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

## The game's hooks

- `ContO.carSlot` (set by the bridge while an extra recolours the cars) tells
  `ContO.d` which race car it draws, and the bridge which polygon.
- `Plane.d`: the colour a race car's polygon is drawn in passes through the
  extra (`CarLook.color`, before the fog: the same on the processor and in the
  kept scene); `CarLook.lamp` lights or darkens a light; a bent wheel's
  polygon is drawn from tilted copies of its points.
- `Madness.regx/regz`: every touch goes to `FeatureBridge.touch` before the
  game decides whether it dents.
- `Sound3D.extraClip/carSound`: the extra's clips, made once
  (`RealismSounds`: decaying modes for the tick, the thud and the rattle,
  filtered noise with metallic modes for the scrape), played at the car by the
  3D sound only. Classic sound never hears them.
- `Particles.keep`: a particle stays as debris where it lands
  (`CarLooks.debris`).

## Cost

Off, nothing runs and every hook is one check. On, the state is a few dozen
numbers per car, fixed pools (900 particles, 400 pieces of debris) and a few
polygons per damaged car; nothing is allocated per frame or tick (the sounds'
commands excepted, a few a second at most).
