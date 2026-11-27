package nfm.lit.features.weather;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Explosions;
import nfm.lit.features.Features;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceView;
import nfm.lit.features.Weather;

/**
 * What the rain leaves on the ground and what the cars make of it
 * (weather, "Wet roads spray", "Puddles"): puddles along the track, small
 * ones filling the craters explosions left, a mist thrown up behind a car
 * at speed and water thrown from a sliding or spinning tyre, and a splash
 * when a wheel goes through a puddle. Only a wheel that touches the ground
 * throws water: none comes off a car in the air.
 */
final class Wet {

    static final int PUDDLES = 96;
    /** Puddles lie at least this far apart along the track, up to this far aside of its line; a longer step between two points is no stretch of road. */
    private static final float APART = 900F;
    private static final float ASIDE = 420F;
    private static final float LONGEST = 14000F;
    /** The spray's particles; they are the items from the base given to {@link #queue}. */
    static final int SPRAY = 900;
    /** How fast a car must go before its rear wheels raise a mist, units a tick, and the speed at which it is fullest. */
    static final float MIST_FROM = 12F;
    static final float MIST_FULL = 60F;
    /** How fast a tyre must slide before it throws water, units a tick, and the slide at which it throws most. */
    static final float SLIDE_FROM = 2.5F;
    static final float SLIDE_FULL = 14F;
    /** Ticks of rain that fill a crater as far as it fills, and how far up the bowl that is. */
    static final int CRATER_FILLS = 19 * 12;
    static final float CRATER_FULL = 0.72F;
    /** Corners of a puddle. */
    private static final int ROUND = 10;
    /** The flat ground's height. */
    private static final float GROUND = 250F;
    /** Farther than this from the eye a puddle is not drawn. */
    private static final float SEEN = 7000F;

    final Particles spray = new Particles(SPRAY);
    final float[] puddleX = new float[PUDDLES];
    final float[] puddleZ = new float[PUDDLES];
    final float[] puddleR = new float[PUDDLES];
    private final float[] puddleTurn = new float[PUDDLES];
    private final float[] puddleLong = new float[PUDDLES];
    int puddles;
    private final Dice dice = new Dice(0L, "weather wet");
    private float windX;
    private float windZ;
    private int tick;
    private int waterRgb;
    private int mistRgb;
    /** Per car: the tick its last splash was heard. */
    private int[] heard = new int[0];
    /** Water thrown in the last tick: mist puffs, drops from sliding tyres, and wheels in puddles. */
    int misted;
    /** Rooster tails behind rear wheels and bow spray off front wheels in the last tick. */
    int tails;
    int bows;
    int thrown;
    int splashed;

    private float eyeX;
    private float eyeZ;
    private final float[] cx = new float[ROUND];
    private final float[] cy = new float[ROUND];
    private final float[] cz = new float[ROUND];

    /** A race starts in the rain: the puddles are laid along the track. */
    void start(RaceView r, float windX, float windZ) {
        this.windX = windX;
        this.windZ = windZ;
        tick = r.tick;
        dice.reset(r.seed, "weather wet");
        spray.clear();
        waterRgb = Air.mix(r.skyColor, 0xffffff, 0.42F); // the sky in it: paler than the wet road around it
        mistRgb = Air.mix(r.skyColor, 0xffffff, 0.6F);
        if (heard.length < r.cars) {
            heard = new int[r.cars];
        }
        java.util.Arrays.fill(heard, -100);
        puddles = 0;
        // along the track from point to point, where both lie on the flat ground: one every so often, a little aside
        float length = 0F;
        for (int i = 0; i < r.points; i++) {
            length += stretch(r, i);
        }
        float apart = Math.max(APART, length / PUDDLES);
        for (int i = 0; i < r.points && puddles < PUDDLES; i++) {
            float stretch = stretch(r, i);
            int j = (i + 1) % r.points;
            int here = Math.round(stretch / apart + dice.between(-0.4F, 0.4F));
            for (int k = 0; k < here && puddles < PUDDLES; k++) {
                float t = (k + dice.next()) / here;
                float ax = (r.pointX[j] - r.pointX[i]) / stretch;
                float az = (r.pointZ[j] - r.pointZ[i]) / stretch;
                float aside = dice.between(-ASIDE, ASIDE);
                int p = puddles++;
                puddleR[p] = dice.between(55F, 150F);
                puddleX[p] = r.pointX[i] + (r.pointX[j] - r.pointX[i]) * t - az * aside;
                puddleZ[p] = r.pointZ[i] + (r.pointZ[j] - r.pointZ[i]) * t + ax * aside;
                puddleTurn[p] = dice.between(0F, 6.2832F);
                puddleLong[p] = dice.between(1F, 1.7F);
            }
        }
    }

    /** How long the track is from point {@code i} to the next, if both lie on the flat ground and it is one stretch of road; else 0. */
    private static float stretch(RaceView r, int i) {
        int j = (i + 1) % r.points;
        if (j == i || !onGround(r.pointY[i]) || !onGround(r.pointY[j])) {
            return 0F;
        }
        float dx = r.pointX[j] - r.pointX[i];
        float dz = r.pointZ[j] - r.pointZ[i];
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        return d < 1F || d > LONGEST ? 0F : d;
    }

    /** True if a track point of height {@code y} lies on the flat ground: a stage's file gives those 0 (no height) or the ground's own. */
    private static boolean onGround(float y) {
        return y == 0F || y >= GROUND - 1F;
    }

    /** One tick: the cars throw water, the spray drifts and falls. */
    void tick(RaceView r, Lens[] lens, int players) {
        tick = r.tick;
        misted = 0;
        tails = 0;
        bows = 0;
        thrown = 0;
        splashed = 0;
        for (int c = 0; c < r.cars; c++) {
            if (!r.wasted[c]) {
                car(r, c, c < players ? lens[c] : null);
            }
        }
        spray.tick(windX, windZ);
    }

    private void car(RaceView r, int c, Lens lens) {
        float speed = r.groundSpeed(c);
        float mist = Math.max(0F, Math.min(1F, (speed - MIST_FROM) / (MIST_FULL - MIST_FROM)));
        boolean inPuddle = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (!r.contact[at]) {
                continue; // in the air a tyre throws nothing
            }
            float x = r.wheelX[at];
            float y = r.wheelY[at];
            float z = r.wheelZ[at];
            // the mist behind the rear wheels at speed
            if (w >= 2 && mist > 0F && dice.next() < 0.35F + 0.65F * mist) {
                int i = spray.add(x, y - 4F, z, r.vx[c] * 0.3F + dice.between(-1.5F, 1.5F), -(2F + 5F * mist),
                        r.vz[c] * 0.3F + dice.between(-1.5F, 1.5F), dice.between(9F, 15F), 7F, 26F + 26F * mist,
                        mistRgb, 0.16F + 0.2F * mist, 0.1F, 0F, false);
                spray.water(i);
                misted++;
                if (mist > 0.25F && dice.next() < mist) {
                    // faster still: a rooster tail, thrown high behind the wheel and hanging there a second
                    i = spray.add(x, y - 6F, z, r.vx[c] * 0.42F + dice.between(-2F, 2F), -(6F + 11F * mist),
                            r.vz[c] * 0.42F + dice.between(-2F, 2F), dice.between(13F, 20F), 12F, 50F + 60F * mist,
                            mistRgb, 0.14F + 0.16F * mist, 0.07F, 0F, false);
                    spray.water(i);
                    tails++;
                }
            }
            // and off the front wheels, out to the sides: the bow of a car through standing water
            if (w < 2 && mist > 0.35F && dice.next() < 0.5F * mist) {
                float ox = x - r.x[c];
                float oz = z - r.z[c];
                float o = (float) Math.sqrt(ox * ox + oz * oz) + 1F;
                float out = 3F + 6F * mist;
                int i = spray.add(x, y - 3F, z, r.vx[c] * 0.3F + ox / o * out, -(2F + 4F * mist),
                        r.vz[c] * 0.3F + oz / o * out, dice.between(8F, 13F), 8F, 24F + 30F * mist, mistRgb,
                        0.16F + 0.14F * mist, 0.1F, 0F, false);
                spray.water(i);
                bows++;
            }
            // water off a tyre that slides, spins or is locked
            float slide = Math.abs(r.slipSide[at]) + Math.max(0F, Math.abs(r.slipLong[at]) - 2F) * 0.6F;
            float throwing = Math.max(0F, Math.min(1F, (slide - SLIDE_FROM) / (SLIDE_FULL - SLIDE_FROM)));
            if (throwing > 0F && speed > 2F) {
                // outwards from the car and up, with a little of the car's way
                float ox = x - r.x[c];
                float oz = z - r.z[c];
                float o = (float) Math.sqrt(ox * ox + oz * oz) + 1F;
                int drops = 1 + (int) (throwing * 2.2F);
                for (int k = 0; k < drops; k++) {
                    float out = dice.between(2F, 4F + 5F * throwing);
                    drop(x, y, z, r.vx[c] * 0.45F + ox / o * out + dice.between(-1.5F, 1.5F),
                            -dice.between(3F, 5F + 6F * throwing),
                            r.vz[c] * 0.45F + oz / o * out + dice.between(-1.5F, 1.5F));
                }
                int i = spray.add(x, y - 3F, z, r.vx[c] * 0.35F + ox / o * 2F, -(2F + 3F * throwing),
                        r.vz[c] * 0.35F + oz / o * 2F, dice.between(8F, 13F), 6F, 22F + 20F * throwing, mistRgb,
                        0.2F + 0.2F * throwing, 0.1F, 0F, false);
                spray.water(i);
                thrown += drops;
            }
            if (speed > 5F && puddleAt(x, z) >= 0) {
                inPuddle = true;
                float hard = Math.min(1F, speed / 45F);
                for (int k = 0; k < 3; k++) {
                    drop(x, y, z, r.vx[c] * 0.4F + dice.between(-4F, 4F) * (0.5F + hard),
                            -dice.between(4F, 6F + 9F * hard), r.vz[c] * 0.4F + dice.between(-4F, 4F) * (0.5F + hard));
                }
                int i = spray.add(x, y - 4F, z, r.vx[c] * 0.3F, -(3F + 4F * hard), r.vz[c] * 0.3F,
                        dice.between(8F, 12F), 10F, 40F + 30F * hard, mistRgb, 0.3F, 0.12F, 0F, false);
                spray.water(i);
                splashed++;
            }
        }
        if (inPuddle) {
            if (tick - heard[c] >= 6 && Features.hooks().sound3d()) {
                heard[c] = tick;
                Sound3D.pointSound(r.x[c], r.y[c], r.z[c], WeatherSounds.SPLASH,
                        0.35F + 0.6F * Math.min(1F, speed / 45F), dice.between(0.9F, 1.12F));
            }
            if (lens != null) {
                lens.splash(2); // through a puddle: its water on the screen
            }
        }
    }

    /** A drop of water thrown from {@code (x, y, z)}: a small tumbling piece that falls back. */
    private void drop(float x, float y, float z, float vx, float vy, float vz) {
        int i = spray.add(x, y - 2F, z, vx, vy, vz, dice.between(7F, 13F), dice.between(1.2F, 2.4F), 0.8F, mistRgb,
                0.8F, 0.03F, -0.9F, false);
        spray.water(i);
    }

    /** The puddle that {@code (x, z)} lies in, or -1. */
    int puddleAt(float x, float z) {
        for (int p = 0; p < puddles; p++) {
            float dx = x - puddleX[p];
            float dz = z - puddleZ[p];
            float r = puddleR[p] * 0.8F;
            if (dx * dx + dz * dz < r * r) {
                return p;
            }
        }
        return -1;
    }

    /** How far up its bowl the rain has filled the crater an explosion left {@code ticks} ago: 0 to {@link #CRATER_FULL}. */
    static float filled(int ticks) {
        return CRATER_FULL * Math.max(0F, Math.min(1F, ticks / (float) CRATER_FILLS));
    }

    /* ------------------------------------------------------------ drawing */

    /** Queues the puddles (one ground item, {@code puddleItem}) and the spray (from {@code sprayBase}). */
    void queue(DrawQueue queue, int puddleItem, int sprayBase) {
        eyeX = queue.eyeX();
        eyeZ = queue.eyeZ();
        if (puddles > 0 || Weather.blasts() > 0) {
            queue.ground(puddleItem);
        }
        spray.queue(queue, sprayBase);
    }

    /** Puddles drawn by the last {@link #drawPuddles}: along the track, and in craters. */
    int drawnPuddles;
    int drawnCraters;

    /** Draws the puddles near enough to see, and the water in the craters. */
    void drawPuddles(Canvas canvas) {
        drawnPuddles = 0;
        drawnCraters = 0;
        for (int p = 0; p < puddles; p++) {
            float dx = puddleX[p] - eyeX;
            float dz = puddleZ[p] - eyeZ;
            if (dx * dx + dz * dz > SEEN * SEEN) {
                continue;
            }
            puddle(canvas, puddleX[p], GROUND - 0.4F, puddleZ[p], puddleR[p], puddleLong[p], puddleTurn[p], p, 1F);
            drawnPuddles++;
        }
        int all = Weather.blasts();
        for (int n = Math.max(0, all - Weather.BLASTS); n < all; n++) {
            int slot = n % Weather.BLASTS;
            if (!Weather.blastCrater(slot)) {
                continue;
            }
            float fill = filled(tick - Weather.blastTick(slot));
            if (fill <= 0.05F) {
                continue;
            }
            float s = Weather.blastSize(slot);
            float r = (Explosions.FLOOR + (Explosions.MOUTH - Explosions.FLOOR) * fill) * s;
            float y = Weather.blastGround(slot) + Explosions.DEPTH * s * (1F - fill);
            // rounder than a puddle on the road, and its wet edge no wider than the bowl is at that height
            puddle(canvas, Weather.blastX(slot), y, Weather.blastZ(slot), r / 1.13F, 1F, n * 1.7F, 1000 + n, 0.25F);
            drawnCraters++;
        }
    }

    /**
     * A puddle about {@code (x, y, z)}, {@code r} across its narrow way and
     * {@code stretch} times that along {@code turn}: a darker wet edge, the
     * water holding the sky, and a paler sheen off its middle; its outline
     * as uneven as {@code rough} says (1 for one on the road).
     */
    private void puddle(Canvas canvas, float x, float y, float z, float r, float stretch, float turn, int seed,
            float rough) {
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        int water = canvas.fog(waterRgb, x, y, z);
        for (int pass = 0; pass < 3; pass++) {
            // the edge a little wider, the sheen much smaller and off to one side
            float size = pass == 0 ? 1.1F : pass == 1 ? 0.94F : 0.42F;
            float off = pass == 2 ? r * 0.22F : 0F;
            for (int k = 0; k < ROUND; k++) {
                double a = 2D * Math.PI * k / ROUND;
                float wobble = 1F - rough * 0.26F + rough * 0.36F * Fall.hash(seed, k, 5);
                float along = (float) Math.cos(a) * r * stretch * wobble * size + off;
                float across = (float) Math.sin(a) * r * wobble * size + off * 0.4F;
                cx[k] = x + along * cos - across * sin;
                cy[k] = y;
                cz[k] = z + along * sin + across * cos;
            }
            if (pass == 0) {
                canvas.color(Air.mix(water, 0x000000, 0.6F));
                canvas.dither(0.5F);
            } else if (pass == 1) {
                canvas.color(water);
                canvas.dither(0.86F);
            } else {
                canvas.color(Air.mix(water, 0xffffff, 0.45F));
                canvas.dither(0.5F);
            }
            canvas.world(cx, cy, cz, ROUND);
        }
    }

    void drawSpray(Canvas canvas, int item) {
        spray.draw(canvas, item);
    }
}
