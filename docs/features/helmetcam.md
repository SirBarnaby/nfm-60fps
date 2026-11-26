# The helmet cam

Switch: **Helmet cam** (`helmetcam`, cookie `xtrahelmet`), off by default
until 1.0. Requirements: `openspec\specs\first-person` ("Helmet-cam
first-person view", "G-forces are visible in first person").

Code: `src\nfm\lit\features\helmetcam\`, with its hooks in the game in
`nfm.lit.FeatureBridge` (the camera, the view key, the polygons left out) and
`nfm.lit.GameSparker` (where frames place their camera, the vignette).

## Choosing it

With the switch on, the view key cycles chase, around, watch, then the helmet
cam, then chase again; in split screen each player has their own. A race
starts in the chase view, as the game's own views do (`-Dnfm.helmetview=on`
starts a test run in the helmet cam). It shows from the start of the
countdown, not during the fly-by before it, and not while watching another
car after the race. Switching the extra off during a race goes back to the
chase view.

**Drawing only.** While the helmet shows, the race's own camera is the chase
view, placed, recorded (`RaceLog` view 0) and read by the race as always
(what the draw step measures, repair hoops, the HUD's arrows); only the
frames are drawn from the helmet. So a race plays the same whichever view is
shown, and a replay knows nothing of it.

## The head (`Head`)

Three damped springs, one per axis of the car's own frame (side to side, up
and down, forward and back), driven by the car's acceleration: the change of
its speed over a race tick, turned into the car's frame, in g (the game's
gravity is 7 units a tick a tick). Settled, one g moves the head 7 units
sideways, 5 up or down, 9 forward or back; at most 12, 9 and 16. The neck's
natural frequency is about 1.6 Hz with a damping ratio of 0.45: when a force
ends the head swings back past the middle by about a fifth, then settles.
Each tick is worked out in four steps; more than 3 g (5 up and down, for a
landing's jolt) is felt as that.

- **Braking** throws the head forward, **accelerating** back.
- **A corner** throws it to the outside.
- **A landing** jolts it down; **in the air** it floats up a little (gravity
  no longer presses it down); leaving a ramp presses it down.
- **Into the corner**: the head turns toward where the car turns (2.6 degrees
  for each degree a tick) and against where it slides, up to 20 degrees,
  following a third of the way each tick.
- It nods down 0.3 degrees for each unit it is thrown forward.
- A car put back on the road (a jump of 400 units in a tick) starts it again.

Values come from the published head-physics settings of racing simulators
(Assetto Corsa's head physics and Rocket's camera: stiffness, damping and
limits per axis) and the Shift 2 articles on the helmet cam (the look into
the corner, the blur and darkening under g); see the change's proposal.

## The eyes and the car around them (`Cockpit`)

Worked out from the car's model when a race starts. With glass, the eyes are
in the middle of the glasshouse along the car, a third of its height down
from the roof line; with none (an open car), 6 units over the body at its
middle. Either way they are over the top of the bonnet by at least as much,
so a wedge whose bonnet is high still shows it below the eyes.

The game does not cut a polygon where it passes the eyes: it squeezes
anything nearer than 50 units to 50. So only the car's own polygons wholly
ahead of the eyes and below them, and not glass, are drawn from them: the
bonnet, the front wings, the front wheels. The car's **inside** hides the
rest (below). The log names each player's car, its eyes and how many of its
polygons are drawn from them.

## The inside

A basic cockpit that fits any car (`Interior`), about 140 flat parts in 25
colours: a dashboard (a dark scuttle at the foot of the windscreen, a lit
top, a face with a trim line, an air vent each side, three switches on the
left and a radio on the right, the footwell below), an instrument hood with
two dials, a steering wheel, a console between the seats, a door with a lit
sill each side and, in a car with glass, the windscreen's two pillars and the
roof's edge. An open car (no glass) has no pillars and no roof. The top of
the dashboard is seen where the top of the bonnet meets the foot of the
windscreen, so each car's bonnet shows over it as before.

What moves: the steering wheel turns up to 100 degrees each way with the
steering keys (a third of the way to where they hold it each tick; a yellow
mark at the top of the rim shows the turn); the left dial's needle follows
the speed against the car's top speed, the right one's the power bar.

The game cannot draw anything nearer than 50 units to the eyes, and leaves
out, whole, an extra's polygon with a corner behind them. So the cockpit is
not built at its true size around the head. Every part lies on a plane
across the car ahead of the eyes: the dashboard, doors and roof 102 units
ahead (or at the foot of the windscreen if that is farther), the instruments
6 nearer, the steering wheel 16 nearer (86 at least), each as large as it
has to be to look right from there. The parts shift against each other as
the head moves on its springs, the wheel most. Places are given as slopes
from the eyes at rest. The parts that reach to the sides, up and down are
cut into strips (to the side at slopes 0.55, 1.05, 1.6, 2.4, 3.4 and 8, 83
degrees), so a strip that has a corner behind the eyes when the head turns
away is wholly outside the picture, and nothing vanishes in view.

Drawn through `Canvas.onCar`, right after the car, so it moves with the
car's smooth pose, rolls and pitches with the car while the horizon stays
level, and goes into the scene the graphics card places. Its colours fade
into the stage's fog as the car's do. It is drawn for the player only, in
each player's pane in split screen: the race does not know it is there.

## The camera

The game's camera has a place, a yaw and a pitch, in whole units and
degrees, and turns about a point 50 units ahead of its eye. For each frame
the helmet works out, from the car's pose at the tick before and after the
last one (or the run-ahead one) and the head at those ticks, where the eyes
are and where they look: the car's heading plus the head's turn (180 degrees
more with the look-back key); a pitch of 55 degrees times the sine of the
car's, so a car upside down never turns the picture over, 3 degrees down and
the head's nod. There is no roll: the horizon stays level, as a driver's
eyes keep it. It hands the game the camera with its fractions: on the
graphics card frames are placed exactly between the ticks (`PoseSpan`'s
camera fractions, `Precise` on a frame recorded for the card), so the bonnet
does not shake by a unit or a degree. Software frames are placed in whole
units.

Its frames sort the objects and polygons by the depths its own last frame
measured (`PaneDepths`, one per player), as a split-screen pane does.

## The vignette

Under g-force the edges darken: from 0.35 g felt by the head (how far it is
thrown on each axis, pressing down only up and down) to 1.8 g, the corners
darkening up to 55%. On the graphics card it is a pass of its own over the
scene and under the HUD (`Shaders.VIGNETTE_FS`), in each player's pane in
split screen, its strength placed between the ticks like everything else; in
software a cached picture drawn in 64 shades.

## Cost

Off: one check of a field per frame and per polygon of a race car (a car's
polygons ask only while a polygon-changing extra or the helmet is on). On:
the camera worked out twice a frame, the inside's 140 or so small polygons,
one more draw for the vignette. Arrays are made at race start; nothing per frame.

## Checks

`test\extras-check.ps1 helmetcam` drives a made-up car: braking (forward,
then back past the middle by 5 to 35%), accelerating (back), a corner each
way (to the outside, looking in), a flight and a landing (up a little, then
jolted down, settled), the vignette (none at a steady speed or in the air,
more the harder the braking), the eyes and the polygons drawn from them, and
the camera lined up with the car (a far point ahead in the middle, 3 degrees
down; looking back, one behind). The inside: its parts and colours counted,
none nearer than 86 ahead of the eyes, a roof only over glass; every point
of a 16:9 and a 21:9 picture below the dashboard's top on a part, with the
head at rest and at the ends of its travel and turned 20 degrees either
way, and no part more than 30% of the picture's lower half; over 5,346 views
(the head thrown and turned, the car pitched 12 degrees up to 18 down
against the view) no part the game leaves out is in the picture and no
corner in view is drawn more than 5% out of place; the wheel's mark and the
needles move with the steering, the speed and the power. A 32:9 picture
with the head turned all the way is not covered by the check: its far edge
looks past 83 degrees to the side. The lap in first person waits for the
owner. `test\verify-races.ps1 -Features looks -NoGolden` with
`JAVA_TOOL_OPTIONS=-Dnfm.helmetview=on` shows the races are unchanged in it,
and a hidden autotest with `-Dnfm.helmetcam=on -Dnfm.helmetview=on
-Dnfm.projectiontest=40` (and `-Dnfm.autotest.split=auto,3`) shows the card
places it as the processor does.
