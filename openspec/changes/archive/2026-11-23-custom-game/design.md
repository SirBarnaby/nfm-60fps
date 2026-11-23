## Context

- The race already holds 51 cars: every per-car array in `Madness`, `Control`, `CheckPoints`, `Record`, `GameUi.sc` and `FramePose` is sized 51, the original's own limit. The classic game sets `GameFacts.numberOfPlayers` to 7 in `GameUi.carspergame`, and split screen to 2 plus its computer cars.
- `GameUi.sortcars` picks the classic computer cars from the race seed's opponent stream, and the verifier checks the result against the recording.
- The classic grid (`GameSparker.loadstage`) is three columns, 760 apart lengthwise, the player in the middle at the back. It has room for about 10 cars before the stage's straight ends: on every stage the cars can stand between z -1866 and 1983 (stage 16's ramp starts at 2437, stage 4's at -2200).
- A few things were sized for the classic seven: `Medium.addSp` (7 shadows kept from overlapping), `Precise` (16 cars' in-between positions), the replay sound's arrays (sized by `numberOfPlayers` when the game started, 7), and `Sound3D`'s occlusion (8 cars).
- Race state that a speculative tick and the rollback self-test must put back lives in the objects `RaceSnapshot` copies; `World` is one of them and is not part of the recording's state layout, so a field there changes no recorded race.

## Goals / Non-Goals

**Goals:** everything the owner asked for (`specs/custom-game/spec.md`); classic races, split screen and every golden frame unchanged; a 33-car race held to the same 250 fps floor as any race.

**Non-Goals:** new AI driving (the computer cars drive as they always have), more than 32 computer cars, custom games in split screen, unlocking from custom races.

## Decisions

1. **One flag, `CustomGame.on`, gates everything new before the race; the race carries its own start.** The flag is set when the custom game's page opens and cleared at the main menu; it decides the car count (`carspergame`), the cars (`sortcars`, where `classicPicks` hands Default with six back to the classic choice) and the unlocks. When the grid is laid out, `loadstage` writes the start into the race itself, `World.startGroups` (0 for everyone at once, as in every other race), and the race tick, the fly-by and the HUD read that, not the menu flag. A staggered start also needs more than 9 computer cars, so Custom Game with its defaults is the classic race.
2. **The page is the settings screen.** `SettingsScreen.customGame` builds a titled page with its own rows instead of a tab in Settings: the rows, keys, mouse, scroll and look are the settings screen's, and the settings keep their tabs. *Alternative:* a hand-drawn menu like split screen's car select. Rejected: more code, and a second look.
3. **The lineups, as the owner worded them.**
   - "Default" for more than six computer cars keeps the classic choice's span of the car list (`stage / 2` and the six after it, all cars once every stage is unlocked) and the stage's boss at the front, dealt in reshuffled rounds so each car comes once before any twice.
   - "Pro … starting from dr. monstaa, and going down from there": DR Monstaa (15) at the front, then 14 down to 9; the pro cars are the ones after Drifter X, the last Novice car. Past Sword of Justice the line starts again at DR Monstaa.
   - "Custom. Allows picking our enemies": a count per car, up to 32 in all; the computer cars are then the cars picked, so Computer cars follows the picks.
   - All shuffling draws from the race seed's opponent stream, as the classic choice does, so a race replays with the same cars.
4. **The grid.** Groups of six across, 300 apart (the road is ±840), filled from the middle out; every other column half a row back, so each car's nearest neighbour is about as far as on the classic grid. The rows start 760 apart, as the classic grid's, and are squeezed (to about 618 with 32) only as far as the grid must to fit between -1860 and 1980; it is centred there. The highest car slot stands at the front, so the strongest car of a lineup starts first. The player stands 440 behind the last row, in the middle: last, as asked.
5. **The staggered start lives in `World`: `startGroups` and its clock, `ticksSinceGo`.** The clock counts race ticks from Go! in a staggered race; both are reset with the race seed. In `World`, they are captured and put back by the speculative tick and rollback, and they are not in the recording's state layout, so no recorded race changes. A group goes `group × 19` ticks after Go! (19 ticks of 53 ms is a second, inside the owner's 2 s); the player's group is the last. A held car has its controls let go and the handbrake on before the cars drive, and its AI is not run until it goes, so it sets off as a car does from the classic start.
6. **The player's own countdown** reuses the countdown's numbers, portrait and voice (three, two, one, go) for the last three seconds before the player goes, and says which group is off before that.
7. **Standings.** A race of up to 10 cars draws them exactly as before (the row code is one method now, the same pixels). A bigger race shows ten rows: the top three, the car the arrow points at, then the player and the cars nearest them, ahead first. Dots under a row mark the cars left out. The player's name is in the places' blue.
8. **Sound occlusion in a big race** traces the 8 cars nearest the listener (Steam Audio was opened for 8) and keeps each car's eased value while it is not among them. One loop eases every car through its traced slot; with 8 cars or fewer car i is slot i, every slot is eased each update as before, and each value goes through the same operations, so `test\verify-sound.ps1`'s renders do not change.
9. **Recordings** gain the custom game's choices at the end of the file (format 6). `RaceSession.setUpRace` sets them (or turns the custom game off) before every verified race.

## Performance: 250 fps at 1080p

- Everything new is drawn by the graphics card as before: the extra cars are more polygons in the kept scene the card places every frame; the HUD is the same layer.
- The game thread does more work a tick with 33 cars (collisions are pairs, 33 × 32; the AI for 32 cars), 19 times a second, off the drawing thread.
- Not measured yet: `test\live.ps1 -Custom 32,pro` on the owner's PC is task 5.4, open while the owner uses the PC (see the `custom-game-review-fixes` change).

## Races stay identical

- `test\verify-races.ps1` (20 recorded races, 80 golden frames) must stay `20 matched` and `0 changed`: classic races never reach a custom branch (decision 1), and the row method draws the same pixels.
- `test\verify-sound.ps1` renders stay the same (decision 8).

## Risks / Trade-offs

- 33 engines are 165 OpenAL sources with the effects and named sounds; OpenAL Soft allows 256.
- A custom race of stage 1 to 17 uses the stages' own start; a custom stage (loaded from a file) with a short straight could put the front rows of a 32-car grid on a slope.
