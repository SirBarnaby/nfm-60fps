# Picture

## Purpose
How the picture fits any screen: no black bars, everything sharp and in shape at every size, as in a very modern game.

## Requirements

### Requirement: No black bars
The game SHALL never show black bars, at any window or screen shape: everything scales to fill, as in modern games. (Owner, 2026-11-21, R1 Q10.)

#### Scenario: Check: screenshots at many shapes
- **WHEN** autotest screenshots are taken at 1920x1080, 2560x1080, 3840x1080 (32:9), 1920x1200 and 1720x720
- **THEN** none shows a black bar or an unfilled area, in a race or in a menu

### Requirement: Everything stays sharp and in shape at any size
At any screen size and shape, nothing SHALL get blurry or be pulled out of shape. The menus and the HUD SHALL be scaled only in whole steps (1x, 2x, 4x) so their pixels stay crisp, and the race SHALL be drawn at the screen's full resolution. (Owner, 2026-11-21, R1 Q11, R2 A9.)

#### Scenario: Check: sharp at 4K
- **WHEN** an autotest screenshot is taken at 3840x2160
- **THEN** the menus and HUD are drawn at a whole-number scale with crisp pixels, and the race fills the screen at 3840x2160

#### Scenario: Check: nothing skewed
- **WHEN** autotest screenshots are taken at 16:9, 21:9 and 32:9, in a race and in split screen
- **THEN** the 3D view is not skewed or stretched: round wheels stay round and the horizon is level

### Requirement: Screen size changes behave logically
Changing the window size, the shape or going fullscreen SHALL keep the game the same, only scaled correctly, like a very modern game. (Owner, 2026-11-21, R1 Q11.)

#### Scenario: Check: live fullscreen switch
- **WHEN** `test\live.ps1` switches fullscreen off and on again
- **THEN** the game keeps running with no flag, and the screenshots after the switch fill the screen
