# The weather

Switch: **Weather** (`weather`, cookie `xtraweather`): Off, Rain, Rain and
fog, or Snow; off by default until 1.0, in the Extras tab's Looks group.
Requirements: `openspec\specs\weather`.

Code: `src\nfm\lit\features\weather\` (`WeatherFeature` the extra, `Air`
what a stage's sky, light, fog and ground become, `Fall` the falling rain
and snow and the rain's splashes, `Lens` the drops on the screen,
`WeatherSounds`, `WeatherCheck`), `features\Weather` (what the weather is,
for the other extras) and `features\StageAir` (the lines of a stage's file
an extra may change). Its hooks in the game: `nfm.lit.FeatureBridge`
(`air`, `cover`, `Hooks.ground`, `Hooks.bed`), the stage loader in
`nfm.lit.GameSparker`, `nfm.lit.Plane` (what lies on upward faces),
`nfm.lit.audio.Sound3D` (looping beds around the listener).

**Only the look and the sound.** The weather reads the race through its
copy (`RaceView`) and never writes it; the road grips in rain and snow as
in the dry. The sky, the fog's distance and the ground's colour are read by
drawing only. The recorded races replayed with the weather on prove it:
`cloud/check.sh looks` plays them in snow, `cloud/check.sh looks
-Dnfm.weather=rain` and `"-Dnfm.weather=rain and fog"` in rain; each must
give 20 matched.

## The stage's air (`Air`)

As a stage's file is read, each line about its air goes through the extras
whose switches are on (`Feature.air`, `StageAir`): `snap` (the light),
`sky`, `fog`, `clouds`, `ground`, `polys` (the ground's patches),
`fadefrom` (where the fog starts) and `density`. The weather changes the
numbers before the game uses them, so everything built from them (the
models' colours, the sky's bands, the clouds, the HUD's tint) is made for
the weather. Every colour is worked out from the stage's own: a night stays
a night, a red evening keeps a little of its red.

| | Rain | Rain and fog | Snow |
| --- | --- | --- | --- |
| Light (`snap`) | 16, 13, 8 percent less red, green, blue | as rain | 3 and 9 percent more green and blue |
| Sky | four fifths of the way to a grey 0.72 times as bright | as rain | four fifths of the way to a pale grey |
| Fog colour | to a grey 0.8 times as bright | to a grey 0.84 times as bright, more of it | as the sky |
| Clouds | dark grey | as rain | pale |
| Ground and patches | a fifth darker | as rain | half hidden by snow at once, more as it deepens |
| Fog from | 0.7 of the stage's distance | a quarter of it, 1200 to 1800 units | 0.6 of it |
| Fog density | one step thicker | the thickest (2) | two steps thicker |

The sky is the stage's, so a switch turned during a race changes it with the
next race; what falls and what is heard follow the switch at once.

## What falls (`Fall`)

Every drop and flake has a place in the world: driving, turning and jumping
move you through it. Nothing is kept from tick to tick. The drops are a
pattern that repeats through the world in three layers (out to 350, 950 and
2600 units; 2900, 3600 and 2600 drops in each layer's pattern, about a
tenth of them in the picture at a time), and where each drop is follows
from the tick alone, so a race replayed rains the same.

- **Rain** falls 46 units a tick (each drop a little faster or slower),
  pushed by the race's wind (`Wind`, six times its speed). A drop is a thin
  see-through streak lying back along the way it moves past the camera, so
  at speed the rain slants towards you; its corners are given the drop's own
  move, so it falls smoothly between ticks. Farther drops are drawn wider
  and longer (or they would be less than a pixel), fainter, and sink into
  the fog's colour.
- **Snow** falls 6.5 units a tick, drifts two and a half times the wind and
  sways about its line; a flake is a small white diamond, a few pixels
  across at any distance, drawn out a little at speed.
- Nothing falls nearer than 40 units ahead of the eye (the game leaves out
  a polygon that reaches the eye), below the flat ground, or outside the
  picture. The drops are queued by distance (one item per value of the
  game's distance measure), so cars, ramps and trees nearer than a drop
  cover it.
- **Splashes**: in rain, sites spread over the flat ground in front of the
  camera each make a small ring that spreads and fades over three ticks,
  with a little crown standing in the first. They are a ground item:
  everything that stands on the ground covers them.

## On the roads and the cars

The game mixes one colour into every upward-facing polygon (`Hooks.cover`,
`Plane.flatTop`: a polygon less than half as high as it is long and wide),
by one share for the stage's pieces and another for the cars; glass and
wheels are left alone.

- **Rain**: a sheen of the sky's colour, a fifth on the roads and an eighth
  on the cars.
- **Snow**: white, on the roads from 36% when a race starts to 58% at the
  cap, on the cars' roofs and bonnets from 23% to 38%. The road stays
  readable against the whiter ground.

## Snow gets deeper

The snow's depth runs from 0.3 when a race starts to 1 after four minutes
of race, and stays there (`snowAt`). Once a second the ground's and its
patches' colours are worked out again from the stage file's own
(`Air.groundUnder`: 64% hidden at the start, 95% at the cap) and handed to
the game (`Hooks.ground`), with the roads' and cars' shares above.
`Weather.snow()` gives the depth to the other extras.

## Drops on the screen (`Lens`)

In rain, drops land on each player's screen: 0.4 a tick standing still, 0.6
more at top speed. A drop grows in, sits for 2 to 5 seconds and dries
away; nearly half of the large ones run, pushed outwards from the middle
by the air at speed and down by their weight, drawn out along their way. A
drop is a few polygons fixed on the picture (`Canvas.screen`): a pale
see-through body, a bright glint at its upper left and a darker rim below.

**Behind another car** (within 1200 units ahead, in a cone that widens with
the distance, the car on the ground and moving) its spray reaches the
screen: up to 2.6 more drops a tick right behind it, most of them fine ones
that dry within a second.

In the chase views the drops lie over the whole picture, under the HUD. In
the helmet cam they are on the windscreen: over the bonnet, under the car's
inside (`DrawQueue.afterFirst`). In snow a few flakes land and melt.

## The wet road (`Wet`)

- **Mist**: a car going faster than 12 units a tick raises pale puffs
  behind its rear wheels, fullest from 60; they spread, drift with the wind
  and thin out within a second.
- **Water off a sliding tyre**: a wheel that slides sideways, spins or is
  locked by more than 2.5 units a tick throws drops outwards from the car
  and up, with a puff of mist, the most at 14.
- **Only on the ground**: a wheel throws water only while it touches the
  ground (`RaceView.contact`); a car in the air throws none.
- **Puddles**: up to 96 lie along the track, placed when the race starts
  from the track's own points (`RaceView.pointX/Y/Z`, where they lie on the
  flat ground), at least 900 units apart and up to 420 aside of the line
  between two points. A puddle is three flat polygons on the ground, drawn
  right after the road: a dark wet edge, the water (the sky's colour, paler
  than the wet road) and a paler sheen off its middle. The rain's splashes
  are drawn over them.
- **Through a puddle**: a wheel faster than 5 units a tick throws drops and
  a burst of mist, a splash is heard at the car (the 3D sound; at most one
  every six ticks a car), and a player's screen gets two large drops.
- **Craters**: every explosion's look reports where it went off
  (`Weather.blast`, from `Explosions.explode`). A crater fills over twelve
  seconds of rain to 72% of its bowl: a rounder small puddle, lying as deep
  and as wide as the bowl is at that height.

The spray's particles are the extras' shared `Particles`, marked as water so
the rain does not beat them down as it does smoke.

## Smoke in the rain

In rain every puff of the extras' particles (smoke, dust, steam; not sparks,
not clods, not the spray) ages 1.4 ticks a tick, keeps a quarter of its lift
and is pushed down 0.16 units a tick more each tick (`Particles.tick`,
`RAIN_AGES`, `RAIN_LIFT`, `RAIN_SINKS`): an explosion's smoke is thinner
after five seconds (26 puffs against 42) and hangs about 19 units over the
ground instead of 70.

## The grid's dry spots (`DrySpots`)

A race that starts in rain or snow leaves a spot the shape of each car
where it stood: paler than the wet road in rain, the darker bare ground in
snow (a thin layer over the ground as drawn, so it suits any road). Once
its car has driven off a spot fades: wet in twelve seconds, snowed over in
forty. Weather switched on more than two seconds into a race lays none.

## Sound (`WeatherSounds`)

Real recordings (`sounds\CREDITS.md`), looping around the listener through
the 3D sound's beds (`Sound3D.bed`: a flat source whose loudness follows
over a third of a second): rain all around, louder at speed; from the
helmet cam of a car with a roof the rain is quieter and its drumming on the
roof is heard; the player's tyres hiss on the wet road, louder with speed,
while the car touches the ground; in snow, wind. A splash is heard where a
car goes through a puddle. Classic sound hears none of it.

## Still to come

The rest of the weather's requirements are built in later steps and are not
in the game yet: wipers, rain washing dirt off and the wet stain; snow
trails, heaps beside drifts, snow on the cars and in two cars' trunks,
explosions and fire clearing snow.

## Checks

`cloud/check.sh extras weather` (`test\extras-check.ps1 weather`): rain,
rain with nearer and thicker fog, snow and nothing in the dry; the sky,
light and ground each weather makes of a blue day, and of a night; the
drops are in the world (seen again from a step aside) and the same in a
race replayed; nothing falls at the eye in 120 frames of a camera looking
and moving every way; splashes on the ground in rain only; drops on the
screen, more behind another car, none beside it or in the dry; the snow's
depth after 0, 2, 4 and 8 minutes; what is heard, and nothing through
Classic sound; the same explosion's smoke in the dry and in the rain; a
slide at speed on the ground (drops and mist), the same slide in the air
(none) and slow rolling (none); puddles laid along a made-up track, a wheel
through one, a crater right after its blast and after twelve seconds of
rain; the grid's spots under the cars, a second after they left and after
the time each weather takes to hide them.

Seen so far only in the software renderer's hidden screenshots; not yet on
the graphics card, and nobody has driven it.
