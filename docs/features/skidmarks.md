# Skidmarks and last race's skid zones

Switches: **Skidmarks** (`skidmarks`, cookie `xtramarks`), its **Detail**
(`skiddetail`: simple or full, cookie `xtradetail`) and **Last race's skid
zones** (`skidzones`, cookie `xtrazones`). All off by default until 1.0.
Requirements: `openspec\specs\effects` ("Skidmarks are per wheel", "show each
maneuver", "carry the surface's colour", "Skidding leaves particles", "Last
race's skid zones can be shown", "The track is littered by the end of a
race").

Code: `src\nfm\lit\features\skidmarks\`.

## How a wheel slides (`SkidSlip`)

The game's cars do not have tyres: all four wheels chase one target velocity
(the engine's speed along the car's heading), each tick by at most the car's
grip. While the grip is enough the car goes exactly where it points, and the
velocity has no sideways part at all after the tick. A slide is when the grip
is not enough: the handbrake, loose ground, a hit, a landing. So the sideways
slide (`RaceView.slipSide`) and the lengthwise one (`slipLong`, the engine's
speed less the speed along the heading) are the game's own measure of
sliding, and there are no marks in a turn the car holds.

That one slide is shared out over the four wheels as a real car's would be:

- the body's angle to its path (`bodySlip`) says whose slide it is: under 6
  degrees the steered fronts (understeer), over 18 the rear (a power slide),
  and between both; the handbrake always the rear, the fronts rolling;
- in a slide the rear also swings round with the car's turn, and as a car
  comes down turning every wheel does;
- the fronts' own angle to the path counts their steering, so countersteering
  straightens them and changes their traces alone;
- the outside wheels carry up to 35% more load;
- the handbrake locks the rear (lengthwise slide the whole speed); the fronts
  never drive, so they never spin.

The slip is lengthwise as a share of the speed and sideways twice the slip
angle's sine (3 degrees count as a tenth). A mark starts when a wheel on the
ground slides more than 4 units a tick with a slip over 0.12 (in a landing's
first two ticks 2.5), and ends when it slides less than 2.5 or the slip drops
under 0.1, so a mark ends the tick its wheel rolls cleanly again.

## The marks (`MarkRing`, `SkidmarksFeature`)

- A fixed ring of 4,096 pieces; the oldest makes way. Each wheel draws a
  strip, one piece a tick, each starting where the last ended; its width is
  the tyre's contact patch across the way it moves (the tyre's 16 units
  rolling along, the patch's 18 sliding sideways).
- A wheel that slides on the spot (a burnout) darkens its piece instead.
- A faint onset (cover 0.17) that deepens by 0.3 a tick towards a depth set by
  how hard it slides.
- Shade by kind: locked darkest, spinning darker still, sideways lightest,
  both between; a body scrape is metal grey.
- On loose ground a mark is a rut in the ground's own colour. Off it, a tyre
  carries the ground's colour (up 0.2 a tick on loose ground) and loses it
  with the distance it rolls, e-folding every 10 m, so the tint is gone within
  30 m.
- A hard hit across a car (a hit along a world axis that is mostly across the
  car) scrubs all four wheels for 4 ticks from the hit.
- Rolled more than 50 degrees, the body's low side edge on the road scrapes a
  metal mark beside the rubber; on its roof, the roof does. Both spark.
- Full detail adds two darker streaks along locked marks and fine striations
  along the tyre on sideways ones, within 30 m of the camera.

Drawing: every piece on the road is one `ground` item (right after the flat
road pieces, under ramps, trees and cars), oldest first; a piece on a ramp is
an `at` item, its point pulled 15% towards the eye so the ramp under it,
measured from its middle, is drawn first. Pieces farther than 200 m are left
to the fog. On the card they are part of the kept scene; in software they
are drawn each frame.

## Particles

From the shared pool (640 here): smoke from tyres sliding hard on the road,
dust and falling clods off loose ground, sparks from a scraping body. Each
moves on its own (drag, lift or gravity, a little wind) and is drawn
smoothly between ticks. A puff is a low-poly hexagon facing the camera,
turning slowly as it grows; a clod is a small square tumbling as it falls; a
spark is a short glowing streak. Every corner gets its own move to the next
tick, so the growing and turning are as smooth as the flight. The simple
detail throws half as many.

## Litter (`Litter`)

With the skidmarks on, a race litters the track. A wheel that leaves a mark
also sheds, by how hard it slides: on the road a crumb of rubber (dark, 4.5
to 10 units across, about one tick in seven at a full slide), on loose
ground a stone of the ground's own colour, paler or darker (6 to 15 units,
about one tick in five). A crumb lands up to 30 units behind the wheel and
38 to either side, a stone up to 60 behind and 80 aside, so stones lie on
the road beside the gravel too. A bit stays where it landed for the rest of
the race; the newest 8192 are kept. Nothing of a car's damage is among them.

A car going at least 8 units a tick stirs the bits within 170 units of its
middle, the more the faster it goes (fullest from 50) and the nearer it
passes: a bit rocks (one side lifts and falls back, settling within a
second), and the light ones shift a little, along with the car and out of
its way, a crumb some 5 to 10 units a pass, a stone less than half of that.
A bit on a ramp only rocks.

Within 1100 units of the eye a bit is a small lump: its flat foot and three
to five sides up to a peak, the sides turned to the light paler. Farther
off it is its foot alone; beyond 1800 units every other bit is left out and
beyond 3600 all of them (they are less than a pixel). A moving or rocking
bit gives each corner its own move, so it goes smoothly between ticks. Bits
on the flat ground are drawn with the road, after the marks.

Looks only, from the race's copy, with dice of its own seeded from the
race. For a look in a screenshot, `-Dnfm.litter.show=true` scatters some
two hundred bits around the player's car at the start.

## The zones (`SkidZonesFeature`)

A grid of 4 m squares (open addressing, 16,384 squares) adds up the slide of
every wheel that would mark. When the race ends it is written to
`cache\skid-heat\stage-<number>-<name>.bin` (a temporary file moved into
place), unless the run keeps no files (`RaceView.keepsFiles`: test runs,
recording, verifying). At the next race on that stage the squares with at
least 35% of the most-slid square's sliding (at most 600) show as dark
squares on the road, dithered at 8 to 25% cover.

## Checks

`test\extras-check.ps1 skidmarks` plays each maneuver on a made-up Radical
One and measures the marks: one wheel locking, hard braking, understeer, a
power slide (wider rear arc, stronger outside wheel, countersteering), a
handbrake 180, a stationary burnout, a T-bone, landing turning (first wheel
first, a bounce's gap), landing backwards, two wheels and the metal scrape,
mud onto tarmac, particles, the litter (three laps of a drift through the
same corner: the first lap's bits all still there, none from a car rolling
cleanly, stones of the ground's colour on loose ground; a car driving past
at speed rocks and shifts a crumb, a slow car and a car far off do not),
the drawing, and the zones (second race, the toggle, no file from a test
run). `test\verify-races.ps1 -Features
skidmarks,skidzones -NoGolden` proves the races stay as recorded with them on.
