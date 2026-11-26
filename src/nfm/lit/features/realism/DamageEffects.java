package nfm.lit.features.realism;

import nfm.lit.features.CarLooks;
import nfm.lit.features.RaceView;

/**
 * Damage as it shows (realism: the radiator, the engine, the glass, the
 * wheels, the lights, the scratches, bottoming out, rattling and dirt),
 * worked out from the race after each real tick: which
 * corner was hit, along the car or across it, and how hard.
 * <p>
 * A hit's push is along the world's x or z; turned into the car's own frame
 * it is along the car (a hit on its end) or across it (a hit on its side).
 * The force is the game's own, above what it takes to dent: from
 * {@link #HARD} the parts start to break, from {@link #HEAVY} (where the
 * game plays its loud crash) they give way.
 */
final class DamageEffects {

    /** A hit this hard breaks a light, crushes a radiator, bends a wheel. */
    static final float HARD = 90F;
    /** This hard shatters a side window. */
    static final float HEAVY = 170F;
    /** A wheel hit this hard goes flat. */
    static final float FLAT = HEAVY * 1.3F;
    /** The most a wheel bends, degrees. */
    static final float MOST_BENT = 9F;
    /** How fast debris falls, units a tick each tick (about the world's gravity). */
    static final float FALL = 0.6F;
    /** Landing faster than this, after this many ticks in the air, bottoms out. */
    static final float BOTTOM_OUT = 18F;
    static final int BOTTOM_OUT_AIR = 6;
    /** A touch lighter than this leaves no scratch. */
    static final float SCRAPE = 6F;
    /**
     * The engine smokes from this much damage (the car's own, or its rams): light wisps, blue-grey on the
     * throttle, dark puffs. It takes some effort: half the car's damage, or three hard rams on the engine's end.
     */
    static final float WISPS_FROM = 0.5F;
    static final float BLUE_FROM = 0.7F;
    static final float DARK_FROM = 0.88F;
    /** What one hard ram on the engine's end adds to its hurt, before the force above {@link #HARD}. */
    static final float RAM = 0.15F;

    private static final float DEG = (float) (Math.PI / 180D);
    private static final int STEAM = 0xe9edf0;
    private static final int WISPS = 0xbdbdbd;
    private static final int BLUE_SMOKE = 0x7b8696;
    private static final int DARK_SMOKE = 0x2c2c2e;
    private static final int GLINT = 0xfff6dc;
    private static final int SPIKE_SPARK = 0xffd070;
    /** A ram pushing at least this hard glints, and the ticks before the same car glints again. */
    static final float SPIKE_PUSH = 2F;
    static final int SPIKE_WAIT = 3;
    private static final int GLASS_BITS = 0xcfe4ea;
    private static final int GLASS_BITS_DARK = 0xa9c8d2;

    private final RealismFeature f;
    private final float[] p = new float[3];

    DamageEffects(RealismFeature f) {
        this.f = f;
    }

    /** Car {@code c}'s damage after the tick: its hits and touches, then what its damage gives off. */
    void tick(RaceView race, int c, CarState s) {
        float yaw = race.xz[c] * DEG;
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);
        if (race.repaired[c]) {
            s.repair(); // a repair hoop fixes everything
            for (int w = 0; w < RaceView.WHEELS; w++) {
                CarLooks.bend(c, w, 0F, s.parts.shape == null ? 0F : s.parts.shape.wheelRadius[w]);
            }
        }
        for (int h = 0; h < race.hits; h++) {
            if (race.hitCar[h] != c) {
                continue;
            }
            float force = race.hitForce[h];
            int w = race.hitWheel[h];
            if (race.hitAxis[h] == 1) {
                if (Math.abs(force) >= HEAVY) {
                    crack(s); // a roll onto the roof
                }
            } else if (w >= 0 && w < RaceView.WHEELS) {
                float lx = race.hitAxis[h] == 2 ? force * sin : force * cos;
                float lz = race.hitAxis[h] == 2 ? force * cos : -force * sin;
                hit(race, c, s, w, lx, lz, Math.abs(force));
            }
        }
        touches(race, c, s, sin, cos);
        spikes(race, c, s);
        float share = RealismFeature.speedShare(race, c);
        steam(race, c, s, share);
        smoke(race, c, s);
        sparks(race, c, s);
        rattle(race, c, s, share);
        landing(race, c, s);
        dirt(race, c, s, share);
    }

    /** A hit of force {@code f} on corner {@code w}, pushing {@code (lx, lz)} in the car's own frame. */
    void hit(RaceView race, int c, CarState s, int w, float lx, float lz, float force) {
        CarParts parts = s.parts;
        CarSheet sheet = parts.sheet;
        boolean across = Math.abs(lx) > Math.abs(lz);
        boolean front = parts.frontWheel[w];
        int side = race.keyX[c * RaceView.WHEELS + w] < 0 ? CarParts.LEFT : CarParts.RIGHT;
        if (force >= HARD) {
            boolean radiator = parts.radiators && (sheet.cooling == CarSheet.SIDE_PODS ? across : front && !across);
            if (radiator) {
                s.crush = Math.min(1F, s.crush + 0.35F + (force - HARD) / 200F);
                s.leaking[side] = true;
            }
            boolean engineEnd = sheet.sits == CarSheet.FRONT ? front : !front;
            if (!across && engineEnd) {
                s.ram = Math.min(1F, s.ram + RAM + (force - HARD) / 400F);
            }
            if (!across) {
                if (front) {
                    s.frontBroken = true;
                } else {
                    s.rearBroken = true;
                }
            }
            float degrees = Math.min(MOST_BENT, 3F + (force - HARD) / 25F);
            if (degrees > s.bent[w]) {
                s.bent[w] = degrees;
                CarLooks.bend(c, w, degrees, parts.shape.wheelRadius[w]);
            }
            if (force >= FLAT) {
                s.flat[w] = true;
            }
            if (front || force >= HEAVY) {
                crack(s);
            }
        }
        if (force >= HEAVY && across && parts.hasWindow[side] && !s.windowBroken[side]) {
            shatter(race, c, s, side);
        }
    }

    /**
     * A car with spikes (Sword of Justice) rams another: a light metal glint and sparks where they touch,
     * every time (at most one burst a corner each {@link #SPIKE_WAIT} ticks, so a long push keeps glinting).
     */
    private void spikes(RaceView race, int c, CarState s) {
        if (s.spikeWait > 0) {
            s.spikeWait--;
        }
        if (!s.parts.sheet.is("spikes") || s.spikeWait > 0) {
            return;
        }
        for (int r = 0; r < race.rams; r++) {
            if (race.ramCar[r] != c || Math.abs(race.ramPush[r]) < SPIKE_PUSH) {
                continue;
            }
            int mine = c * RaceView.WHEELS + race.ramWheel[r];
            int theirs = race.ramOther[r] * RaceView.WHEELS + race.ramOtherWheel[r];
            if (race.ramOther[r] < 0 || race.ramOther[r] >= race.cars) {
                continue;
            }
            // between the two corners that met, at the height of the body's middle
            float x = (race.wheelX[mine] + race.wheelX[theirs]) / 2F;
            float z = (race.wheelZ[mine] + race.wheelZ[theirs]) / 2F;
            float y = (race.wheelY[mine] + race.wheelY[theirs]) / 2F - s.parts.height * 0.5F;
            float vx = (race.vx[c] + race.vx[race.ramOther[r]]) / 2F;
            float vz = (race.vz[c] + race.vz[race.ramOther[r]]) / 2F;
            // the glint: one bright point that flares and is gone in two ticks
            f.particles.add(x, y, z, vx, 0F, vz, 2.2F, 5F, 0.8F, GLINT, 1F, 0.3F, 0F, true);
            int sparks = 5 + f.dice.below(4);
            for (int k = 0; k < sparks; k++) {
                f.particles.add(x, y, z, vx * 0.5F + f.dice.between(-2.6F, 2.6F), -f.dice.between(0.4F, 2.8F),
                        vz * 0.5F + f.dice.between(-2.6F, 2.6F), f.dice.between(4F, 8F), f.dice.between(0.3F, 0.6F),
                        0.25F, f.dice.next() < 0.5F ? GLINT : SPIKE_SPARK, 1F, 0.08F, -0.5F, true);
            }
            f.sound(c, RealismSounds.GLINT, 0.35F + 0.2F * f.dice.next(), 0.9F + 0.3F * f.dice.next());
            s.spikeWait = SPIKE_WAIT;
            return;
        }
    }

    /** Light touches along a side, a wall scraped: scratches on that side where it touched. */
    private void touches(RaceView race, int c, CarState s, float sin, float cos) {
        if (race.groundSpeed(c) < 3F) {
            return;
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            float tx = race.touchX[at];
            float tz = race.touchZ[at];
            if (tx == 0F && tz == 0F) {
                continue;
            }
            float lx = tz * sin + tx * cos;
            float lz = tz * cos - tx * sin;
            if (Math.abs(lx) > SCRAPE && Math.abs(lx) > Math.abs(lz)) {
                scratch(race, c, s, race.keyX[at] < 0 ? CarParts.LEFT : CarParts.RIGHT, race.keyZ[at], Math.abs(lx));
            }
        }
    }

    /** A scratch on side {@code side} near length {@code z}; a scrape that goes on lengthens the last one. */
    private void scratch(RaceView race, int c, CarState s, int side, float z, float force) {
        CarParts parts = s.parts;
        int plane = parts.sideFace(side, z);
        if (plane < 0) {
            return;
        }
        int base = side * CarState.SCRATCHES;
        if (s.scratches[side] > 0) {
            int last = base + (s.scratchNext[side] - 1 + CarState.SCRATCHES) % CarState.SCRATCHES;
            if (s.scratchPlane[last] == plane && race.tick - s.scratchTick[side] < 8) {
                // the same scrape going on: it reaches further along the panel
                s.scratchFrom[last] = Math.max(parts.z0[plane] + 0.5F, s.scratchFrom[last] - 2F);
                s.scratchTick[side] = race.tick;
                return;
            }
        }
        int k = base + s.scratchNext[side];
        s.scratchNext[side] = (s.scratchNext[side] + 1) % CarState.SCRATCHES;
        s.scratches[side] = Math.min(CarState.SCRATCHES, s.scratches[side] + 1);
        s.scratchTick[side] = race.tick;
        float lo = parts.y0[plane] + (parts.y1[plane] - parts.y0[plane]) * 0.3F;
        float hi = parts.y0[plane] + (parts.y1[plane] - parts.y0[plane]) * 0.7F;
        float zLo = parts.z0[plane] + 0.5F;
        float zHi = parts.z1[plane] - 0.5F;
        float to = Math.max(zLo, Math.min(zHi, z));
        float reach = Math.min(zHi - zLo, 4F + force / 6F);
        s.scratchPlane[k] = plane;
        s.scratchY[k] = f.dice.between(lo, hi);
        s.scratchTo[k] = to;
        s.scratchFrom[k] = Math.max(zLo, to - reach);
        for (int j = 0; j < CarState.CHIPS; j++) {
            int chip = k * CarState.CHIPS + j;
            s.chipY[chip] = f.dice.between(lo, hi);
            s.chipZ[chip] = f.dice.between(s.scratchFrom[k], s.scratchTo[k]);
            s.chipSize[chip] = f.dice.between(0.3F, 0.8F);
        }
    }

    /** A crack star on the windscreen, at a random place on it. */
    private void crack(CarState s) {
        CarParts parts = s.parts;
        if (parts.screen < 0 || s.stars >= CarState.STARS) {
            return;
        }
        int k = s.stars++;
        float ru = parts.screenReachU;
        float rv = parts.screenReachV;
        float u = f.dice.between(-0.7F, 0.7F) * ru;
        float v = f.dice.between(-0.6F, 0.6F) * rv;
        s.starU[k] = u;
        s.starV[k] = v;
        float turn = f.dice.next() * 6.2831855F;
        for (int r = 0; r < CarState.RAYS; r++) {
            float a = turn + r * (6.2831855F / CarState.RAYS) + f.dice.between(-0.35F, 0.35F);
            float len = f.dice.between(0.35F, 0.85F) * Math.min(ru, rv);
            float eu = u + (float) Math.cos(a) * len;
            float ev = v + (float) Math.sin(a) * len;
            s.rayU[k * CarState.RAYS + r] = Math.max(-ru * 1.25F, Math.min(ru * 1.25F, eu));
            s.rayV[k * CarState.RAYS + r] = Math.max(-rv * 1.25F, Math.min(rv * 1.25F, ev));
        }
    }

    /** Side {@code side}'s window shatters: bits fly out and fall to the road, where they stay. */
    private void shatter(RaceView race, int c, CarState s, int side) {
        s.windowBroken[side] = true;
        CarParts parts = s.parts;
        float out = side == CarParts.LEFT ? -1F : 1F;
        float floor = RealismFeature.groundUnder(race, c, s);
        int bits = 12 + f.dice.below(5);
        for (int k = 0; k < bits; k++) {
            float lz = parts.windowZ[side] + f.dice.between(-6F, 6F);
            float ly = parts.windowY[side] + f.dice.between(-2F, 2F);
            s.world(parts.windowX[side], ly, lz, p);
            float push = f.dice.between(0.6F, 2F) * out;
            int slot = f.particles.add(p[0], p[1], p[2],
                    race.vx[c] * 0.8F + s.right[0] * push + f.dice.between(-0.3F, 0.3F),
                    race.vy[c] * 0.5F - f.dice.between(0.3F, 1.6F),
                    race.vz[c] * 0.8F + s.right[2] * push + f.dice.between(-0.3F, 0.3F),
                    f.dice.between(30F, 60F), f.dice.between(0.4F, 0.9F), 0.7F,
                    f.dice.next() < 0.5F ? GLASS_BITS : GLASS_BITS_DARK, 0.95F, 0.04F, -FALL, false);
            f.particles.floor(slot, floor);
            f.particles.keep(slot);
        }
    }

    /** A leaking radiator steams from its corner: back over the bonnet at speed, straight up when stopped. */
    private void steam(RaceView race, int c, CarState s, float share) {
        CarParts parts = s.parts;
        if (s.crush <= 0F || s.coolant <= 0F || !parts.radiators) {
            return;
        }
        s.coolant = Math.max(0F, s.coolant - 0.0012F * s.crush * (0.6F + share));
        float strength = Math.min(1F, s.crush * 1.4F) * (float) Math.sqrt(s.coolant);
        for (int side = 0; side < 2; side++) {
            if (!s.leaking[side]) {
                continue;
            }
            float chance = strength * 1.6F;
            int puffs = (int) chance + (f.dice.next() < chance - (int) chance ? 1 : 0);
            for (int k = 0; k < puffs; k++) {
                f.puff(s, parts.radiatorX[side] + f.dice.between(-1.5F, 1.5F), parts.radiatorY[side] - 1F,
                        parts.radiatorZ[side], race.vx[c] * 0.9F + f.dice.between(-0.2F, 0.2F),
                        race.vy[c] * 0.5F - f.dice.between(0.5F, 0.9F), race.vz[c] * 0.9F + f.dice.between(-0.2F, 0.2F),
                        f.dice.between(18F, 26F), 2.5F, 9F + 6F * strength, STEAM, 0.15F + 0.5F * strength, 0.18F,
                        0.06F);
            }
        }
    }

    /** A hurt engine smokes from where it sits: wisps, then blue-grey on throttle, then dark puffs. */
    private void smoke(RaceView race, int c, CarState s) {
        float level = Math.max(race.damage[c], s.ram);
        if (level <= WISPS_FROM) {
            return;
        }
        boolean throttle = race.up[c] && !race.wasted[c];
        int rgb;
        float cover;
        float size;
        float chance;
        if (level > DARK_FROM) {
            rgb = DARK_SMOKE;
            cover = 0.6F;
            size = throttle ? 15F : 11F;
            chance = 0.9F;
        } else if (level > BLUE_FROM && throttle) {
            rgb = BLUE_SMOKE;
            cover = 0.45F;
            size = 11F;
            chance = 0.8F;
        } else {
            rgb = WISPS;
            cover = 0.25F;
            size = 7F;
            chance = 0.25F;
        }
        if (f.dice.next() >= chance) {
            return;
        }
        CarParts parts = s.parts;
        f.puff(s, f.dice.between(-0.3F, 0.3F) * parts.halfWidth, parts.engine[1] - 1F,
                parts.engine[2] + f.dice.between(-3F, 3F), race.vx[c] * 0.85F, race.vy[c] * 0.5F - 0.6F,
                race.vz[c] * 0.85F, f.dice.between(22F, 34F), size * 0.22F, size, rgb, cover, 0.15F, 0.05F);
    }

    /** Sparks where a flat tyre's rim drags on the road. */
    private void sparks(RaceView race, int c, CarState s) {
        if (race.groundSpeed(c) < 3F || !race.grounded[c]) {
            return;
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (s.flat[w] && race.contact[at] && f.dice.next() < 0.7F) {
                f.spark(race, c, race.wheelX[at], race.wheelY[at], race.wheelZ[at]);
            }
        }
    }

    /** A damaged car rattles over bumps, more often at speed; a whole one never does. */
    private void rattle(RaceView race, int c, CarState s, float share) {
        if (s.rattleWait > 0) {
            s.rattleWait--;
        }
        if (race.damage[c] <= 0.25F || race.groundSpeed(c) < 2F || !race.grounded[c] || s.rattleWait > 0) {
            return;
        }
        boolean bumpy = Math.abs(race.vy[c] - s.lastVy) > 1.5F;
        for (int w = 0; w < RaceView.WHEELS && !bumpy; w++) {
            int at = c * RaceView.WHEELS + w;
            bumpy = race.contact[at] && race.surface[at] >= 3;
        }
        if (bumpy && f.dice.next() < 0.15F + 0.6F * share) {
            f.sound(c, RealismSounds.RATTLE, 0.15F + 0.35F * race.damage[c], 0.9F + 0.25F * share);
            s.rattleWait = 2 + (int) (4F * (1F - share));
        }
    }

    /** A hard landing after a big jump: sparks from under the car, a thud and a scrape. */
    private void landing(RaceView race, int c, CarState s) {
        if (race.grounded[c] && s.lastAir >= BOTTOM_OUT_AIR && s.lastVy > BOTTOM_OUT) {
            CarParts parts = s.parts;
            int count = Math.min(20, 8 + (int) ((s.lastVy - BOTTOM_OUT) / 2F));
            for (int k = 0; k < count; k++) {
                s.world(f.dice.between(-0.6F, 0.6F) * parts.halfWidth, parts.ground,
                        parts.rear + parts.length * f.dice.between(0.25F, 0.75F), p);
                f.spark(race, c, p[0], p[1], p[2]);
            }
            float loud = Math.min(1F, 0.4F + (s.lastVy - BOTTOM_OUT) / 30F);
            f.sound(c, RealismSounds.THUD, loud, 0.9F + 0.15F * f.dice.next());
            f.sound(c, RealismSounds.SCRAPE, loud * 0.6F, 0.9F + 0.2F * f.dice.next());
        }
        s.lastAir = race.airTicks[c];
        s.lastVy = race.vy[c];
    }

    /** Soft ground dusts the lower body and the wheels in its colour; tarmac slowly wears it off. */
    private void dirt(RaceView race, int c, CarState s, float share) {
        if (race.groundSpeed(c) < 2F) {
            return;
        }
        int loose = 0;
        int road = 0;
        int rgb = s.dirtRgb;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (!race.contact[at]) {
                continue;
            }
            if (race.surface[at] == 1 || race.surface[at] == 2) {
                loose++;
                rgb = race.groundRgb[at];
            } else if (race.surface[at] == 0) {
                road++;
            }
        }
        if (loose > 0) {
            s.dirtRgb = s.dirt < 0.02F ? rgb : RealismFeature.mix(s.dirtRgb, rgb, 0.08F);
            s.dirt = Math.min(1F, s.dirt + 0.004F * loose / 4F * Math.min(1F, share * 2F + 0.2F));
        } else if (road > 0) {
            s.dirt = Math.max(0F, s.dirt - 0.0006F * (0.3F + share));
        }
    }
}
