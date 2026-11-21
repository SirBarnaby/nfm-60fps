## Why

The owner, 21 Nov 2026: "shadows in the select car menu are broken."

In the car select the car's shadow is drawn as a thin grey sliver floating well above the car instead of on the floor under it. This happens at any menu size other than 1280x720, so at 1920x1080 and 2560x1440 the owner always sees it.

It breaks the existing requirement "Up to 1.0, it looks like the original" (`release-1-0`). No requirement is MODIFIED or REMOVED, and none is added: the existing one and its golden-frame check cover it once the check also looks at the car select.

**Cause.**
- Since v1.0 (a62ae9c, "native-size UI") the 3D drawn in menus goes through `Medium.menuView()`. That moves the camera so the car keeps its own size in the middle of a menus' picture of any size.
- `Medium.setView` moves the camera's `x`/`y` with the picture's centre, but not `Medium.ground`.
- `ground` is measured from the camera, as `y` is, and the shadow is drawn at it (`Plane.s`). So in a picture taller than 720 the shadow stays where the floor was in the 1280x720 picture: 180 px too high at 1080, 360 px at 1440.
- Races never showed it: `Medium.d` sets the ground from the camera again every frame, after the view is set.

## What Changes

- **`Medium.setView` moves the ground with the camera** (`ground -= cy - centreY`), so anything drawn in a menu's, a pane's or the shown view has its floor where its camera is. This affects:
  - the car select's car, alone and in split screen (`SplitScreens`, `paneView`);
  - the unlocked-car box;
  - any other 3D in a menu.
- **No race changes, and no golden frame of a race changes.**
  - Races are verified at 1280x720, where the view's centre does not move.
  - In a race, `Medium.d` sets the ground before anything is drawn.
- **The car select's car is drawn by one method, `GameUi.drawSelectCar`,** which the car select and the new check share.
- **New check in `test\verify-races.ps1`.** After the races, the verifier draws three cars as the car select does:
  - at 1280x720, compared with new golden pictures in `test\golden\carselect\` (saved on the first run, then compared);
  - at 1920x1080, 2560x1080, 2560x1440 and 1280x960, where each must be the 1280x720 picture moved to the middle. Only a few edge pixels may come out a little lighter or darker there: moved, a corner is worked out in floats of another precision (see the design).

  A shadow left behind fails it. Shown failing before the fix (3,640-9,691 pixels off at every size) and passing after.
- **Screenshots before and after**, from hidden runs on the owner's PC, go in `docs\reviews\`, next to v1.0's parent (a62ae9c^, before the native-size UI) for comparison.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
<!-- none: release-1-0 "Up to 1.0, it looks like the original" already asks for this; its golden-frame check now covers the car select -->

Specs touched (from `openspec list --specs`): none changed. `release-1-0` is the requirement this restores, and `picture` is read.

## Impact

- `src\nfm\lit\Medium.java`: `setView` moves the ground.
- `src\nfm\lit\GameUi.java`: `drawSelectCar`, used by `carselect`.
- `src\nfm\lit\RaceSession.java`: `Host.carSelect` and `Verifier.checkCarSelect`, after the races, when golden frames are checked.
- `src\nfm\lit\GameSparker.java`: `SessionHost.carSelect` draws a car of the car select into a picture of a given size and puts everything back.
- `src\nfm\lit\GoldenFrames.java`: a named golden picture and `sameAs`, a picture moved inside a larger one.
- `test\golden\carselect\`: 3 new golden pictures. No existing golden frame changes.
