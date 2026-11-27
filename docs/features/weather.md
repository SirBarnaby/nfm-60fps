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
  across at any distance. It stays a flake at any speed: at most 3.2 units
  long in the nearest layer (what it is seen from a standing car), never
  drawn out into a streak. Driving thins the snowfall instead of thickening
  it: the faster the camera moves the fewer flakes are shown, down to 45%
  of them from 70 units a tick ("Snowfall stays light while driving",
  Owner, 2026-11-27).
- Nothing falls nearer than 40 units ahead of the eye (the game leaves out
  a polygon that reaches the eye), below the flat ground, or outside the
  picture. The drops are queued by distance (one item per value of the
  game's distance measure), so cars, ramps and trees nearer than a drop
  cover it.
- **Splashes**: in rain, sites spread over the flat ground in front of the
  camera each make a small ring that spreads and fades over three ticks,
  with a little crown standing in the first. They are a ground item:
  everything that stands on the ground covers them.

## Gusts and squalls (`Gusts`)

"Rain and snow have more oomph" (Owner, 2026-11-27). The wind rises and
falls through a race: two slow waves, one of 9 to 14 seconds and one of 23
to 37, both from the race's seed, multiplied, so most of the time it is
calm and every half minute or so a squall comes through for a few seconds.
It is a formula of the tick alone (`Gusts.at`, 0 to 1, a quarter on
average; `Gusts.sum` is the same added up from the start, in closed form),
so what falls stays a pattern that follows from the tick and a race
replayed gusts the same.

- **What falls**: in the calm 70% of the drops are shown, in a squall all
  of them (4350 rain drops near, 5400 and 3900 farther; 3000 flakes), and
  they fall a quarter faster. At its peak a squall carries rain four times
  as far sideways as the steady wind does, snow six times: a blizzard
  blows nearly level. Rain's streaks are wider and denser in a gust.
- **Sound**: the rain is a quarter louder in a squall and the wind is
  heard in it (rain: only above a third of a squall; snow: always, 0.4
  louder at the peak).
- **Puffs**: the snow's and the spray's puffs drift 2 to 10 times the
  steady wind.

## The thunderstorm (`Storm`)

In rain, lightning strikes 5200 to 13000 units from the player: the first
6 to 22 seconds into the race, then every 13 to 40 seconds (96 strikes at
most), when and where from the race's seed. A bolt is nine joints from the
cloud to the ground, each a step aside of the last, with one branch from a
joint a third of the way down that ends in the air; a pale glow around a
white core, drawn facing the eye and only when it stands ahead of it. It
flickers for five ticks (there, there, gone, back, fading) and the whole
picture pales with it for three of them (34%, 16%, 24%), unless the
**Explosion flash** switch is off. The thunder is a recording
(`sounds\CREDITS.md`), heard through the 3D sound from the way the bolt
struck, a tick later for every 420 units (two thirds of a second at the
nearest, a second and a half at the farthest), quieter the farther off. None in
snow.

## The wet road's shine (`Reflections`)

In rain, a car with two wheels or more on the ground is mirrored in the
wet road for an eye within 3400 units: a smear of the body's main colour
stretched towards the eye in two bands, and two streaks of light, red from
the tail lights (longer and glowing while the car brakes) and pale from the
headlights. Drawn with the ground, under the cars.

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
- **Rooster tails and bow spray**: above a quarter of that mist each rear
  wheel also throws a tail of spray up and back, higher with speed, and
  above a third the front wheels throw spray out to the sides (up to 900
  puffs live at once).
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

## Wipers (`Wiping`, `features/Wipers`)

In rain (with or without fog; not in snow) every car with a windscreen
wipes it. `Wipers.build` finds the windscreen on the car's model as the
damage extra does (the glass that faces forward most over the biggest area,
with every pane in its plane), and places two blades on it that wipe the
same way: pivots at 47% and 93% of the way across the foot of the glass,
each blade 42% of the glass's width or 85% of its height at most, shortened
until its end stays on the glass through the whole wipe (a windscreen
narrows towards the roof). A blade and its arm are two flat polygons
standing half a unit off the glass, four polygons a car. A wipe takes 24
ticks there and back, from 4 to 86 degrees up from the foot; each car is 7
ticks behind the one before, so they do not wipe as one. A wasted car's stop
once parked. They are drawn right after their car, for the cars whose
windscreen faces the eye within 4500 units. An open car has none.

From the helmet of a closed car the player's own are drawn as the car's
inside is: on a plane across the car 8 units beyond the dashboard's, their
places slopes from the eyes, under the inside so their pivots hide in the
scuttle. They sweep from 3 to 110 degrees, past upright, so the left blade
clears the middle of the view. The drops on the screen that a blade crosses
are wiped off (`Lens.wipe`), and each wipe is heard quietly from inside: a
recording of a blade over wet glass, through the 3D sound.

## Rain washes the car

Built into the damage extra's dirt (`DamageEffects.dirt`), which reads the
weather through `features/Weather`. In rain the dust on a car washes off,
all of it in about twenty seconds, and wet soft ground does not dust the
car. Instead a wheel sliding, spinning or locked on soft ground in the rain
throws mud: a second stain (`CarState.mud`), the ground's colour 45%
darker, laid in splashes (`RealismFeature.mud`): some polygons thick with
it and their neighbours clean, the low ones first, reaching well above
where dust stops. The rain washes mud off twenty times slower than dust.
With the damage extra off there is no dirt to wash.

## The snow and the cars (`Snow`)

Looks and sound only, from the race's copy; in snow, not in rain.

- **Trails**: a wheel on the flat ground presses a track into the snow: a
  strip of flat pieces as wide as the tyre, the packed snow a little darker
  (42% of a grey-blue over the ground as drawn), drawn with the ground so
  everything stands on them. A player's car leaves all four wheels', a
  computer car its rear wheels' while within 9000 units of a player; none
  from a wheel in the air or on a ramp. A piece is laid every two ticks'
  way (30 to 160 units) and the snowfall fills it in again within a minute.
  The 8192 newest pieces are kept; those within 7000 units of the eye are
  drawn.
- **Heaps**: low mounds with sides and a top (six sides, a ring and a peak;
  the sides turned to the light paler than the others), up to 62 units wide
  and 24 high, 160 at most. A wheel sliding sideways faster than 4 units a
  tick throws snow to the outside of the slide, where it lands 55 to 80
  units beyond the car's side and piles up (snow that lands within 110
  units of a heap grows it). A wheel through a settled heap flattens it.
- **A car on its side or roof** pushes a growing heap along (drawn moving
  with it) and leaves it where it stops, a full heap after about two
  seconds' slide; the lump is heard (a recording, through the 3D sound).
- **Wheel arches**: snow packs into them while a car drives on the ground,
  full after thirty seconds: a white crescent over each wheel on the side
  towards the eye.
- **Open backs**: DR Monstaa's bed and M A S H E E N's rear deck (found on
  the model: the biggest level panel behind all the glass) are two fifths
  full when a race starts in snow and full after 45 seconds: a mound whose
  foot stands at the top of the bed's walls. A landing after twelve ticks
  or more in the air knocks all of it out: lumps that stay on the ground as
  a heap, puffs of snow, and it is heard. A ram pushing 3 units a tick or
  more shakes snow off both cars, in puffs: three fifths of what is in
  the arches, two fifths of what is in an open back.
- **Landing**: any car landing after a jump throws up puffs of snow.
- **Bare ground**: an explosion (`Weather.blast`) clears the snow 300 units
  around it at once: trails and heaps there are gone, snow is thrown
  outwards, and no snow heaps on it; the snowfall covers it again in forty
  seconds. Around a wasted (burning) car the snow melts back to 190 units
  in twelve seconds and stays melted. Bare ground is a round dark patch
  over the ground as drawn.
- **Sound**: the player's tyres crunch on the snow, louder with speed,
  while the car touches the ground (a recording, a bed of the 3D sound).

For a look at them in a screenshot, `-Dnfm.weather.show=true` (with
`-Dnfm.weather=snow`) starts the race with six heaps beside the player's
car and every arch and open back full; with `-Dnfm.weather=rain` lightning
strikes every three seconds ahead of the player's car and stands for two.

## The snow bed (`SnowBed`, `Snow`)

"Snow is a bed over the whole map" (Owner, 2026-11-27): the snow lies as
a bed everywhere, on the roads too (up to 92% of the road's colour is
snow), and the cars shape it. The bed is `SnowBed.deep`: 8 units deep when
a race starts and 38 after four minutes (the same depth "Snow gets deeper"
has). Nothing of it is part of the race: grip and the cars' way are as in
the dry.

- **Trails are cut into the bed**: within 2600 units of the eye a wheel's
  trail is drawn as a cut with a floor (darker, the ground showing through
  thin snow), a lit and a shaded wall and a pale lip on each side; farther
  off as the flat strip it was. Deeper snow gives a deeper, darker cut.
- **The body's channel**: where the bed is more than a unit deeper than
  the car's clearance, the body ploughs a channel as wide as the car (94%
  of it) between the wheel trails, drawn the same way, and breaks lumps
  out of the bed (`Weather.lump`; with PhysX on they tumble as blocks).
- **The car's shape**: a car leaves its outline (the hull of its body's
  corners, at most twelve) pressed into the bed where it lands after eight
  ticks or more in the air (deeper the longer it flew), where it comes to
  rest on its side or roof, where it is wasted, and where it stood for
  three seconds or more. The 192 newest stay; the snowfall fills one in
  within a minute and a half.
- **Drifts**: low dunes lie over the open ground, one in about seven of
  ten cells of 900 units, 240 to 500 long and up to 30 high, lying across
  the wind; none on or beside the stage's pieces. A car through one
  flattens it in a burst of powder and lumps, heard as a soft hit; it has
  drifted up again after 45 seconds.
- **Snow about the car**: within 3000 units of the eye a car standing in
  the bed has a mound against each wheel on the ground, and while it
  ploughs, a bow ridge pushed along at its nose.
- **Snow never shows through a car** ("Snow does not show through
  things", Owner, 2026-11-27): what lies on or against a car (the mound in
  an open back, the heap a car on its side pushes, the mound at a wheel,
  the bow ridge) is queued with `DrawQueue.within`, so the game draws it
  among that car's own polygons by how far it is from the eye and the car
  covers it as it covers its own parts. A heap on the ground and a PhysX
  block are queued with `DrawQueue.near`: beside or under a car they are
  drawn the same way, as a part of that car, elsewhere by their distance
  as before.
- **Powder**: above 30 units a tick a car trails a plume of powder,
  fullest from 130, thin and low so the chase camera looks through it (it
  began at 14 and stood twice as high and thick until 27 Nov 2026: "wayy
  too strong when driving, even slowly"); it drifts with the gusts.
- **Spindrift**: 150 streamers of snow run low over the ground with the
  wind, more in a gust.

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
the time each weather takes to hide them; the wipers of two closed cars
through a wipe, their ends on the glass, none in snow, in the dry or on a
car without glass; from the helmet, the blades beyond the dashboard and
below the roof's edge, drops wiped off the screen and the wipes heard; in
snow, the trails of thirty ticks' driving, still there after ten seconds
and filled in after a minute, none from a car in the air or in rain; the
heaps of a slide, to the outside of its path and drawn as shapes; a car
sliding on its roof pushing a heap and leaving it, heard; the arches after
35 seconds; DR Monstaa's back filling, a ram and a hard landing emptying
it, and no such back on another model; a blast on a trail baring the
ground and the snow covering it again, a burning car melting it.
Of the round of 2026-11-27: the gusts (calm most of the time, a squall
now and then, more falling and farther aslant in one, the same in a race
replayed); a strike (the bolt's pieces ahead of the eye, the picture's wash
and none with the flash off, the thunder later by its distance, none in
snow); the wet road (rooster tails and bow spray at speed, none slow or in
the air; a car's smear and lights, the tail lights glowing under braking);
the bed anywhere on the map, a car's shape in it after a landing, lying
and standing, the same marks deeper later in the race, drifts flattened
and grown back, and the mounds at the wheels and the nose.
`cloud/check.sh extras realism` has the dust washing off in the rain and
the mud of a slide over wet soft ground. One check waits for the owner:
whether rain and snow now have the oomph asked for.

Seen so far only in the software renderer's hidden screenshots; not yet on
the graphics card, and nobody has driven it.
