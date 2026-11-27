# Car personalities

Switch: **Car personalities** (`personality`, cookie `xtraquirks`), off by
default until 1.0. Requirements: `openspec\specs\car-personality`, one for
each of the nine cars.

Code: `src\nfm\lit\features\personality\` (`PersonalityFeature`, and `Quirk`
for one car's state and the part of its model that moves).

One small thing each that only that car does, seen and heard, never part of
the race: the extra reads `RaceView` after each tick, draws through `Canvas`
and takes its dice from its own stream, so a recorded race plays the same
with it on (`test\verify-races.ps1 -Features looks -NoGolden`). A car is
known by its model's file name, so the computer's cars have theirs too.

| Car (model) | What it does |
| --- | --- |
| Tornado Shark (`2000tornados`) | It has stood unused: at the start its paint is dulled by dust (a third of the way to a pale dust colour, not the glass, wheels or lights). The takeoff (the first time it passes 4 units a tick) takes two fifths of it off in a second and a half of pale puffs from the roof, bonnet and tail; each of its first two turns (2 degrees a tick or more above 12 units a tick, after at least half a second straight) takes the rest in two halves. After the second turn it is clean, and it sheds none later. |
| Lead Oxide (`leadoxide`) | Held on the throttle above three fifths of its top speed it heats through in six seconds (and cools in twelve). While it is over three fifths hot, every 4 to 9 seconds a pale haze rises off the front of its bonnet for two seconds. |
| Kool Kat (`koolkat`) | A landing (three ticks or more in the air) with all four wheels down within three ticks pushes its body down on its springs, harder the faster it fell, and it comes back up in under a second; with the 3D sound a low thud is heard from the car. The body's polygons are drawn lower, the wheels stay on the road, and the body never goes lower than seven tenths of its clearance (7 units at most). |
| Mighty Eight (`audir8`) | Lifting off the throttle after a second or more on it, above 15 units a tick, blows pale puffs out of its radiators for half a second: sideways from its side pods (the car sheet's cooling says where a car's radiators are). While it burns nitrous a blue flame stands out of each of its two pipes, 36 units long (the game's own is 15), flickering, in two layers that glow in HDR. |
| MAX Revenge (`maxrevenge`) | Its turbocharger is the blower that stands through the bonnet. On the throttle it shakes sideways and up and down at two paces of its own, by a tenth of its width (a little over a unit at least), and stops within a few ticks of letting go. |
| M A S H E E N (`masheen`) | With its front wheels on anything but road, above 4 units a tick, its plow fills with dirt of the ground's colour in four seconds: a wedge of three strips drawn in the plow, seen from ahead and above. It rides along on the road for as long as you like. In the air for four ticks or more with the nose 12 degrees down or more, it tumbles out: three chunks (two when the plow is under three fifths full) that fall, land with a little dust and stay on the track (the 96 latest in a race). |
| DR Monstaa (`drmonster`) | A landing on exactly one wheel flattens that tyre against the ground: the wheel sinks a third of its radius, what would be under the ground lies flat on it, and the tyre bulges out beside the flat. It is round again in half a second. The other three are untouched. |
| Wow Caninaro (`canyenaro`) | Something loose lies in the luggage area. A hard corner (turning times speed of 1.8 or more) slides it to the outside, heard sliding and then knocking against the side; already there, a second corner the same way is silent; the other way it is heard travelling all the way across. Nothing is drawn: what it is stays a mystery. |
| EL KING (`king`) | Braking that slows it 1.2 units a tick or more rocks its cab forward about its foot at the nose, up to 4.5 degrees, on a spring. Letting the brake go within three seconds lets one short breath of compressed air out as the cab settles back. |

## The sounds

All four are recordings the game already carried (`sounds\CREDITS.md`),
played through the 3D sound from the car, so with the sound set to Classic
nothing is heard and Classic stays exact: the thonk is the `thud` clip played
lower, the knock `knock`, the slide `scrape` played quietly and higher, the
breath of air `airbrake`. A computer car's is heard from where it is, which
is what makes EL KING pulling up behind you audible.

## Finding the part (`Quirk.find`)

Three of the nine move a part of the car's own model, found from its shape
when the race starts and written to the log (`personality: maxrevenge's part
is 8 polygons, ...`):

- **MAX Revenge's blower**: the polygons, not glass or wheels, that lie
  wholly within the middle fifth of the car's width between a quarter and
  three fifths of the way from its middle to its nose, less the lowest ones
  (the hole in the bonnet it stands in). On the game's model: 8 polygons.
- **EL KING's cab**: everything that lies on average in the upper three
  quarters of the model's height, from the cab's back wall forward, so the bonnet, the
  grille and the stacks rock with it and the chassis does not. 58 polygons.
- **M A S H E E N's plow**: the polygons wholly in the last fifth before the
  nose. 6 polygons.

A changed or modded model with no such part simply has no moving part.

## How the car's own polygons are moved

- `CarBody.body` is asked for every body polygon of a race car as it is
  drawn; two extras may answer one after the other (`Features.carBody`: the
  suspension handling's springs first, then the personality on what they
  gave). Any number of extras may recolour and bend the cars' polygons in
  turn (`Features.carColor`, `carBend`: the damage, the personality, then the
  weather over both).
- A wheel's polygon is bent before the game spins and steers it, and the kept
  scene spins it again for every frame, so a tyre flat against the *ground*
  cannot be given that way. `CarLooks.spin` and `turn` tell a `CarLook.bend`
  how the wheel stands; DR Monstaa's `bend` spins and steers the corners
  itself, flattens them and sets `CarLooks.baked`, and `Plane.d` then leaves
  them as given. Nothing else uses it, so every other wheel is drawn as
  before.
- In the kept scene (the graphics card) Kool Kat's dip, the blower's shake
  and the cab's rock change once a tick (19 times a second), because a car's
  own polygons are kept once a tick; the flames, the dirt, the chunks and all
  puffs move with every frame.

## Looking at it

`-Dnfm.personality.show=true` holds every personality at its fullest all the
time (dusted, dipped, shaking, a full plow, a flat front tyre, the cab
rocked, the flame lit), for hidden screenshots with `-Dnfm.personality=on`
and `-Dnfm.autotest.car=<car>`.

## Checks

`test\extras-check.ps1 personality` (cloud: `cloud/check.sh extras
personality`) drives made-up cars, one check for each requirement and named
after it:

- Tornado Shark: no dust comes off standing; puffs at the takeoff, none
  driving straight on, puffs in the first and second turn and none in the
  third; its paint dusted before and clean after; none from another model.
- Lead Oxide: driven hard for 40 seconds its heat shows several times, never
  more than 11 seconds apart, ahead of the car's middle; driven gently never.
- Kool Kat: a landing on all four gives one thonk, the body drawn lower and
  the wheels not, back up after two seconds; a landing on two wheels none.
- Mighty Eight: puffs on lifting off after a second and a half, none after a
  blip; eight flame polygons while burning nitrous, none without, more than
  half again as long as another car's; nothing for another model.
- MAX Revenge: the blower is found without its hole; at full throttle it is
  drawn off its place in most ticks by one to three units while the bonnet
  stays; off the throttle it is still.
- M A S H E E N: the plow is found; empty at the start, full after five
  seconds off-road, still full after ten on the road, seen from ahead and not
  from behind; nose up in the air nothing falls, nose down three chunks
  tumble out, land, lie on the road and are still there ten seconds later.
- DR Monstaa: landing on one wheel, that tyre has three of eight corners flat
  on the ground, the flat wider than a round tyre's, its top lower; spun on,
  the flat is still on the ground; the other three round; round again half a
  second later; a landing on all four squashes none.
- Wow Caninaro: a gentle corner no knock; a hard left one slide and one
  knock; a second hard left nothing; a hard right a slide at once and the
  knock some ticks later.
- EL KING: the cab is found without the chassis; a gentle stop does nothing;
  a hard one rocks the cab 4.5 degrees with the chassis still, no air while
  braking, one breath on letting go, the cab back within two seconds; braking
  again gently gives no more.

Not checked by a machine: how each looks and sounds in a race. That is the
owner's to judge by hand.
