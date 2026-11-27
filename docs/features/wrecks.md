# Exploding wrecks

Switch: **Exploding wrecks** (`wrecks`, cookie `xtrawreck`), off by default
until 1.0, in the Extras tab's Races group. It follows the powerups'
**Explosion flash** switch (`flash`). Requirements:
`openspec\specs\powerups` (wasted cars can explode; an exploding wreck hurts
like a small dynamite).

Code: `src\nfm\lit\features\wrecks\` (`WreckRace` the race half,
`WrecksFeature` the look, `WrecksCheck` the checks), with the
explosions' shared parts: `features\BlastPush` (what an explosion does to
the cars) and `features\Explosions` (its look, sound and smoke; see
[Powerups](powerups.md)).

## Playing

- A car that is wasted burns as it always did. With the switch on, 2 seconds
  (38 ticks) after it was wasted it explodes, once.
- The explosion is a small dynamite (`BlastPush.wreck`): every car that still
  races within 400 units is hit by `s = 1 - d/400`: each wheel pushed away by
  35·s and up by 20·s to 30·s (the nearer wheels more, so the car tips),
  damaged by 15% of its maximum times s, its driver's screen shaken. A
  dynamite stick reaches 520 units with 55, 30 to 45 and 25%. A car 200
  units away is pushed 86 and takes 7.5%, where a stick would push it 165
  and do 15%.
- The wreck itself is kicked off the ground (each wheel 24 up, two of them
  34), so it hops and tips. It stays a wreck.
- A wreck that explodes within 150 units of the ground under it leaves a
  crater; one in the air does not.
- With the switch off the extra does not play in the race at all: nothing
  of it runs in the race tick and a wasted car is never touched.

## In the race state

It changes races, so it is a race extra like the powerups: `WreckRace.sync`
holds how long each car has been wasted and where each wreck exploded;
run-ahead, rollback and the verifier cover it; a recording notes it among
the extras that played (format 7). It throws no dice.

## The look

After real ticks only. While a wreck waits, sparks spit from it, more of
them as the two seconds run out. The explosion is `Explosions` at 0.75 of a
dynamite stick's size: the dithered fireball, clods and sparks, the crater,
the white flash for a player near it (the Explosion flash switch) and the
smoke, which drifts with the race's wind, stays on the ground and is moved
by the cars and by later explosions. It has no sound of its own (owner,
27 Nov 2026: no new sound effects for wrecking cars); a wasted car sounds
as it always did. The powerups' dynamite and bomb keep their blasts.

## Checking

`test\extras-check.ps1 wrecks` (`cloud/check.sh extras wrecks`): a wasted car
explodes 38 ticks on, once, with its fireball, crater and lasting smoke,
and without a sound of its own;
with the switch off ten seconds wasted touch nothing; a car 200 units away
is pushed and damaged less than a dynamite stick there would, one 2000 away
not at all; and the state saved, played on and restored plays on the same.
Six autopilot races recorded in the cloud with powerups and wrecks on (five
wrecks exploded in them) matched tick for tick with
`-Dnfm.verify.rollback=50` on 26 Nov 2026.
