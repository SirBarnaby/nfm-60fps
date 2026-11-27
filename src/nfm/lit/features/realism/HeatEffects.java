package nfm.lit.features.realism;

import nfm.lit.features.RaceView;

/**
 * Heat (realism, "Brake discs glow", "The exhaust shows the engine's
 * state", "Hot metal ticks", "Heat haze"): each brake disc, the exhaust and
 * the engine warm with work and cool with time and air, a little after each
 * real tick.
 * <p>
 * A disc takes heat in proportion to the car's speed while it brakes with
 * its wheel on the road (the work it does; none in the air), the fronts 1.3
 * times the share and the rears 0.7, as cars brake mostly on the front. A
 * drift heats the rears: while a rear tyre slides sideways over the road,
 * not for the handbrake key alone. A disc loses a part of its heat every tick, more
 * at speed. One hard stop from top speed leaves the fronts a dull red;
 * stops close together take them to orange; the glow is gone in about 3 s
 * at speed and 6 s standing. The brakes in the car's sheet change both
 * rates (drums keep heat, carbon sheds it).
 */
final class HeatEffects {

    /** A disc's heat taken per tick of braking at top speed, before the axle's share. */
    static final float BRAKE_HEAT = 0.028F;
    /** A disc's heat lost per tick, standing and more at top speed. */
    static final float BRAKE_COOL = 0.006F;
    static final float BRAKE_COOL_SPEED = 0.008F;
    /** The fronts' and rears' share of the braking. */
    static final float FRONT_SHARE = 1.3F;
    static final float REAR_SHARE = 0.7F;
    /** A rear tyre sliding sideways this fast, units a tick, is a drift that heats its disc; from this fast as much as braking does. */
    static final float DRIFT_FROM = 6F;
    static final float DRIFT_FULL = 18F;
    /** A disc shows from this heat. */
    static final float VISIBLE = 0.45F;
    /** The exhaust and the engine: heat taken flat out at top speed, and lost, per tick. */
    static final float EXHAUST_HEAT = 0.006F;
    static final float EXHAUST_COOL = 0.003F;
    static final float ENGINE_HEAT = 0.004F;
    static final float ENGINE_COOL = 0.003F;
    /** The exhaust ticks while it is hotter than this. */
    static final float TICKING = 0.35F;
    /** Lifting off above this share of top speed throws flames. */
    static final float LIFT_OFF = 0.8F;
    /** A charged engine's first flame is a backfire this often; a flame after the first is heard this often. */
    static final float BACKFIRES = 0.35F;
    static final float POPS = 0.45F;
    /** A backfire's flash against a pop's. */
    static final float FLASH_BACKFIRE = 1.6F;
    /** The haze shows over an engine hotter than this. */
    static final float HAZE = 0.45F;

    private static final int SHIMMER = 0xdfe3e6;

    private final RealismFeature f;

    HeatEffects(RealismFeature f) {
        this.f = f;
    }

    /** Car {@code c}'s heat after the tick. */
    void tick(RaceView race, int c, CarState s) {
        CarParts p = s.parts;
        CarSheet sheet = p.sheet;
        float share = RealismFeature.speedShare(race, c);
        float ground = race.groundSpeed(c);
        boolean alive = !race.wasted[c];
        boolean footBrake = alive && race.down[c] && race.speed[c] > 2F;
        // (the brake lamps follow the keys, as before)
        s.braking = footBrake || alive && race.handbrake[c] && ground > 2F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            // a disc only works against the road: in the air a brake stops the wheel and takes no heat
            boolean onRoad = race.contact[c * RaceView.WHEELS + w];
            float input = 0F;
            if (footBrake && onRoad) {
                input = BRAKE_HEAT * (p.frontWheel[w] ? FRONT_SHARE : REAR_SHARE) * share;
            }
            // a drift heats the rears: not the handbrake key by itself, the tyre sliding sideways over the road
            float slide = Math.abs(race.slipSide[c * RaceView.WHEELS + w]);
            if (alive && onRoad && !p.frontWheel[w] && ground > 2F && slide >= DRIFT_FROM) {
                input = Math.max(input, BRAKE_HEAT * share * Math.min(1F, slide / DRIFT_FULL));
            }
            float t = s.brake[w] + input * sheet.brakeHeating();
            t -= t * (BRAKE_COOL + BRAKE_COOL_SPEED * share) * sheet.brakeCooling();
            s.brake[w] = Math.min(1.2F, Math.max(0F, t));
        }
        boolean throttle = alive && race.up[c];
        float work = (throttle ? 1F : 0.25F) * (0.2F + share);
        s.exhaust = Math.min(1F, Math.max(0F, s.exhaust + EXHAUST_HEAT * work
                - s.exhaust * (EXHAUST_COOL + 0.002F * share)));
        s.engine = Math.min(1F, Math.max(0F, s.engine + ENGINE_HEAT * work - s.engine * ENGINE_COOL));
        flames(race, c, s, share, alive);
        haze(race, c, s, share, alive);
        ticking(race, c, s, ground);
    }

    /**
     * Lift-off flames at high revs, and the blue flame of nitrous. A car
     * that pops (its sheet) bangs with the first flame and pops as the
     * flames go on: each pop a sound and a flash at a pipe in the same tick.
     */
    private void flames(RaceView race, int c, CarState s, float share, boolean alive) {
        boolean up = race.up[c];
        boolean pops = s.parts.sheet.pops();
        java.util.Arrays.fill(s.popFlash, 0F);
        if (alive && s.wasUp && !up && share > LIFT_OFF) {
            s.flame = (s.parts.sheet.turbo ? 5 : 3) + f.dice.below(2);
            if (pops) {
                // a charged engine now and then lets go with a backfire
                boolean backfire = s.parts.sheet.turbo && f.dice.next() < BACKFIRES;
                pop(c, s, backfire, 0.7F + 0.3F * f.dice.next());
            }
        } else if (pops && s.flame > 0 && f.dice.next() < POPS) {
            pop(c, s, false, 0.35F + 0.4F * f.dice.next());
        }
        s.wasUp = up;
        boolean nitrous = alive && race.nitrous[c];
        float reach = s.parts.sheet.turbo ? 9F : 6.5F;
        for (int pipe = 0; pipe < 2; pipe++) {
            if (nitrous) {
                s.flameReach[pipe] = 15F * f.dice.between(0.8F, 1.1F);
            } else if (s.flame > 0) {
                s.flameReach[pipe] = reach * f.dice.between(0.55F, 1.2F);
            } else {
                s.flameReach[pipe] = 0F;
            }
        }
        if (s.flame > 0) {
            s.flame--;
        }
    }

    /** One pop from car {@code c}'s exhaust: heard, and seen as a flash at one of its pipes this tick. */
    private void pop(int c, CarState s, boolean backfire, float gain) {
        f.sound(c, backfire ? RealismSounds.BACKFIRE : RealismSounds.POP, gain, 1F);
        s.popFlash[f.dice.below(Math.max(1, Math.min(s.popFlash.length, s.parts.pipes)))] = backfire ? FLASH_BACKFIRE : 1F;
    }

    /** The shimmer behind the exhausts (at idle, and when they are hot) and over a hot engine. */
    private void haze(RaceView race, int c, CarState s, float share, boolean alive) {
        if (!alive) {
            return;
        }
        CarParts p = s.parts;
        boolean idle = share < 0.08F;
        if ((idle || s.exhaust > 0.3F) && f.dice.next() < 0.5F) {
            int pipe = p.pipes > 1 ? f.dice.below(2) : 0;
            float cover = idle ? 0.1F : 0.06F + 0.1F * s.exhaust;
            float bx = p.stacks ? 0F : -s.ahead[0] * 0.3F;
            float by = p.stacks ? -0.5F : -0.2F;
            float bz = p.stacks ? 0F : -s.ahead[2] * 0.3F;
            f.puff(s, p.pipeX[pipe], p.pipeY[pipe], p.pipeZ[pipe], race.vx[c] * 0.7F + bx,
                    race.vy[c] * 0.5F + by, race.vz[c] * 0.7F + bz, 10F, 1.2F, idle ? 4F : 6F, SHIMMER, cover,
                    0.12F, 0.03F);
        }
        if (s.engine > HAZE && f.dice.next() < 0.45F) {
            float cover = 0.05F + 0.1F * (s.engine - HAZE) / (1F - HAZE);
            f.puff(s, f.dice.between(-0.4F, 0.4F) * p.halfWidth, p.engine[1] - 1F, p.engine[2],
                    race.vx[c] * 0.8F, race.vy[c] * 0.5F - 0.4F, race.vz[c] * 0.8F, 9F, 2F, 7F, SHIMMER, cover,
                    0.15F, 0.04F);
        }
    }

    /** A stopped car with a hot exhaust ticks quietly as it cools, less often as it cools. */
    private void ticking(RaceView race, int c, CarState s, float ground) {
        boolean stopped = ground < 0.6F && Math.abs(race.speed[c]) < 1.5F;
        s.idleTicks = stopped ? s.idleTicks + 1 : 0;
        if (stopped && s.idleTicks > 10 && s.exhaust > TICKING
                && f.dice.next() < 0.04F + 0.3F * (s.exhaust - TICKING)) {
            f.sound(c, RealismSounds.TICK, 0.16F + 0.12F * f.dice.next(), 0.85F + 0.35F * f.dice.next());
        }
    }

    /** The colour of a disc at heat {@code t}: dull red, red, orange, almost yellow. */
    static int discColor(float t) {
        if (t < 0.7F) {
            return RealismFeature.mix(0x5c1408, 0xc82a0c, (t - VISIBLE) / (0.7F - VISIBLE));
        }
        if (t < 0.95F) {
            return RealismFeature.mix(0xc82a0c, 0xff7a1c, (t - 0.7F) / 0.25F);
        }
        return RealismFeature.mix(0xff7a1c, 0xffb04a, (t - 0.95F) / 0.25F);
    }

    /** How much a disc at heat {@code t} glows. */
    static float discGlow(float t) {
        return CarParts.clamp((t - VISIBLE) / 0.6F);
    }
}
