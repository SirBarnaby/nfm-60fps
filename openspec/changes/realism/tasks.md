## 1. Hooks in the game

- [x] 1.1 `CarShape` and `RaceView.shape`, `RaceView.modelName`, `RaceView.dataFile`, filled by the bridge when a race starts
- [x] 1.2 `CarLook` (`color`, `lamp`, `bend`), `ContO.carSlot`, the colour and the wheel's points in `Plane.d`
- [x] 1.3 `FeatureBridge.touch` from `Madness.regx/regz`, `RaceView.touchX/touchZ`
- [x] 1.4 `Canvas.onWheel` (`Precise.featureOnWheel`, the card's own wheel spin and steering)
- [x] 1.5 `Sound3D.extraClip`, `carSound`
- [x] 1.6 `CarLooks`, `Debris`, `Particles.keep`; the skidmarks' wavy marks from a bent wheel

## 2. The extra

- [x] 2.1 `CarSheet`, `CarSheets`, `data\extras\car-sheets.txt`
- [x] 2.2 `CarParts`: roles, outward normals, the bonnet, windscreen, windows, engine, radiators, exhausts
- [x] 2.3 `HeatEffects`: brakes, exhaust, engine, flames, haze, ticking
- [x] 2.4 `DamageEffects`: hits, touches, steam, smoke, bonnet, bumpers, glass, wheels, lights, dirt, landing, rattle
- [x] 2.5 `RealismDrawing`, `RealismFeature` (queue, draw, `CarLook`), `RealismSounds`

## 3. Checks, settings, docs

- [x] 3.1 `RealismCheck`: one check per requirement; the sheets' owner review reported as waiting
- [x] 3.2 `SkidmarkCheck`: a bent wheel's marks are wavy
- [x] 3.3 Register in `Features.ALL` and `ExtrasCheck` (switch, row and console command came with the framework)
- [x] 3.4 README Extras line and options row; `docs\features\realism.md`, `docs\features\car-sheets.md`

## 4. Gates

- [x] 4.1 `test\extras-check.ps1`: every check passes (the sheets wait for the owner)
- [x] 4.2 `build.ps1 -Quick`, `test\verify-races.ps1` (20 matched, golden frames all the same), `test\perf.ps1`, `test\spec-check.ps1`, `test\verify-sound.ps1`
- [x] 4.3 `test\verify-races.ps1 -Features looks -NoGolden`: 20 matched
- [x] 4.4 Hidden GPU autotest with it on and `-Dnfm.projectiontest=40`, single and split screen

The live test and the blind review run once at the end of the extras work.
