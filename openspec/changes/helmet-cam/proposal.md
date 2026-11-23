## Why

The owner, 23 Nov 2026, on the extras brief: "First person must be done well", then picked approach A of three, the helmet cam, and "Keep those in also. Implement." `first-person` holds two requirements written that day: a view from the driver's head on springs (braking throws it forward, acceleration back, corners to the side, landings jolt it; it looks into the corner ahead; the edges darken under g-force), and g-forces visible in first person in the spirit of NFS: Shift 2. This change builds them on the extras framework as one switch, **Helmet cam** (`helmetcam`), which the framework already shows in the Extras tab.

No requirement is MODIFIED or REMOVED and none is added. One scenario cannot be finished by this change alone: "a lap in first person" ends with the owner signing it off; the check reports it as waiting for the owner, and the report names it as an owner item.

## What Changes

- **`src\nfm\lit\features\helmetcam\`**:
  - `Head`: the driver's head on its neck, three springs (forward and back, side to side, up and down) driven by the car's acceleration in its own frame, with a little overshoot; the look into the corner; the g-force the head feels.
  - `Cockpit`: per car at race start, from its model: where the driver's eyes are, and which of the car's polygons are drawn from them (the bonnet, the front wings and wheels: what lies ahead of the eyes and below them), and a dashboard that hides the rest of the car below the bonnet's back edge.
  - `HelmetCam` (switch `helmetcam`): a head per player, worked out after each tick; the camera placed from the car's pose and the head, exactly (with the fractions past the game's whole numbers); the vignette's strength.
  - `HelmetCheck`: a check per scenario on a made-up car and race.
- **Hooks in the game** (each a line or a few; with the switch off nothing changes):
  - The view key: with the switch on the cycle is chase, around, watch, helmet, chase. In the helmet view the race's own view stays the chase view (recorded as such, so the race and its recording are as in the chase view); only the picture is drawn from the helmet.
  - `GameSparker`: where a frame's camera is taken (the frame drawn on the processor, the kept scene's span for the card, a split-screen pane's span, the run-ahead frame), the helmet's camera when it is shown; the helmet's frames sort by their own depths (`PaneDepths`), as split screen's panes do.
  - `PoseSpan`, `FramePose`: the camera's fractions past its whole numbers, so the head moves smoothly at any frame rate.
  - `ContO`: a car polygon the helmet does not show is not drawn (only in a frame drawn from that car's helmet).
  - `GpuDisplay`, `Shaders`: the vignette, drawn by the card after the scene and before the HUD (a small shader, true blending); in software, a gradient image.
- **README**: the extra in the Extras section, an options row for `-Dnfm.helmetcam` and the test property `-Dnfm.helmetview`. **`docs\features\helmetcam.md`**: how it works.

Libraries and references looked at (rule: before a new subsystem): Assetto Corsa's head physics and the "AC Head Physics" mod (the car's g-forces and torques sent to the head through a spring "neck", stiffness and damping per axis, with limits; the head never moves the car), the Rocket V2 head cam for DiRT Rally (camera, head, body, anchor with inertia, centring and damping), and what was written about Shift 2's helmet cam (the head swings in corners and lurches under braking, pivots on the neck, looks to the apex). None is open source code that fits a camera of five whole numbers placed once a tick; their design is followed: a damped spring per axis with limits, one way from the car to the head, a look toward where the car is going. Sources: https://www.overtake.gg/downloads/ac-head-physics.68266/, https://www.overtake.gg/downloads/rocket-v2-immersive-head-cam-for-dirt-rally.6566/, https://www.speedhunters.com/?p=6566, https://shacknews.com/article/66650/shift-2-unleashed-first-look, https://www.gamewatcher.com/reviews/need-for-speed-shift-2-unleashed-review/9358.

## Capabilities

### New Capabilities
<!-- none: the requirements exist in first-person -->

### Modified Capabilities
<!-- none -->

Specs touched: none changed. Read: `first-person` (both), `picture` (the race's view and its shape), `performance`, `settings`, `code-health`, `release-1-0`, `done-and-testing`, `realism` (its parts drawn on the player's own car are seen from the helmet).

## Impact

- New files in `src\nfm\lit\features\helmetcam\`, `docs\features\helmetcam.md`.
- Hooks: `GameSparker` (the view key, the cameras, the depths, the vignette's strength handed with the kept scene), `PoseSpan`, `FramePose`, `ContO` (one line), `FeatureBridge`, `GpuDisplay`, `Shaders`.
- With the switch off nothing changes: verify-races, golden frames, perf and sound as before. With it on and the helmet shown in every race (`-Dnfm.helmetview=on`), races are unchanged.
