package nfm.lit.features.skidmarks;

import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.RaceView;

/**
 * What a race leaves lying on the track (effects, "The track is littered by
 * the end of a race"): a sliding tyre sheds crumbs of rubber on the road and
 * kicks stones out of loose ground, and they stay where they land for the
 * rest of the race. A car driving past stirs them: they rock, and the light
 * ones shift a little in its wind. Nothing of a car's damage is among them.
 * <p>
 * A fixed ring: past its capacity the oldest bit goes first. Each bit is a
 * small lump on the ground: near the eye a low shape with sides and a peak,
 * farther off its flat foot alone. Looks only: it reads the race's copy and
 * throws its own dice, seeded from the race, so a race replayed is littered
 * the same. Nothing is allocated after it is made.
 */
final class Litter {

    static final int BITS = 8192;
    /** Bits farther than this from the eye are not drawn (they are less than a pixel), and half of them beyond half of it. */
    static final float SEEN = 3600F;
    /** Nearer than this a bit is drawn as a lump with sides; farther off it is too small to tell from its flat foot. */
    static final float LUMP_WITHIN = 1100F;
    /** A car's wind reaches a bit this far from the car's middle. */
    static final float WIND_REACH = 170F;
    /** The car must go this fast for its wind to stir a bit, units a tick; its wind is at its fullest from {@link #WIND_FULL}. */
    static final float WIND_FROM = 8F;
    static final float WIND_FULL = 50F;
    /** How fast the fullest wind sets the lightest bit moving, units a tick each tick. */
    static final float WIND_PUSH = 2.4F;
    /** What is left of a bit's own speed a tick later: the ground stops it. */
    static final float GRIP = 0.55F;
    /** What is left of a bit's rocking a tick later. */
    static final float SETTLES = 0.8F;
    /** A wheel sliding at the full throws a crumb of rubber, and a stone from loose ground, this often a tick. */
    static final float RUBBER_RATE = 0.14F;
    static final float STONE_RATE = 0.22F;
    /** The flat ground. */
    private static final float GROUND = 250F;

    final float[] x = new float[BITS];
    final float[] y = new float[BITS];
    final float[] z = new float[BITS];
    final float[] size = new float[BITS];
    final float[] turn = new float[BITS];
    /** How easily the wind moves the bit: 1 the lightest crumb, near 0 a stone. */
    final float[] light = new float[BITS];
    final int[] rgb = new int[BITS];
    final byte[] corners = new byte[BITS];
    /** How high the bit's peak stands over the ground, units. */
    final float[] high = new float[BITS];
    /** Lies above the flat ground (on a ramp): drawn among the objects, and it only rocks. */
    final boolean[] raised = new boolean[BITS];
    final float[] vx = new float[BITS];
    final float[] vz = new float[BITS];
    /** How far the bit went in the last tick: a frame between two ticks shows it on its way. */
    final float[] wentX = new float[BITS];
    final float[] wentZ = new float[BITS];
    /** How much the bit rocks now, 0 to 1; how high its lifted side stands now, and stood a tick ago. */
    final float[] rock = new float[BITS];
    final float[] lift = new float[BITS];
    final float[] liftWas = new float[BITS];
    private int next;
    int count;
    /** Bits dropped this race, kept or since gone; and bits a car's wind reached in the last tick. */
    int dropped;
    int stirred;
    /** For the checks: bits drawn in the last frame. */
    int drawn;

    private final Dice dice = new Dice(0L, "litter");
    private int tick;
    private float eyeX;
    private float eyeZ;
    private final float[] sx = new float[3];
    private final float[] sy = new float[3];
    private final float[] sz = new float[3];
    private final float[] smx = new float[3];
    private final float[] smy = new float[3];
    private final float[] smz = new float[3];
    private final float[] cx = new float[6];
    private final float[] cy = new float[6];
    private final float[] cz = new float[6];
    private final float[] mx = new float[6];
    private final float[] my = new float[6];
    private final float[] mz = new float[6];

    /** A race starts: nothing lies on the track. */
    void start(RaceView race) {
        next = 0;
        count = 0;
        dropped = 0;
        stirred = 0;
        tick = 0;
        dice.reset(race.seed, "litter");
        if (Boolean.getBoolean("nfm.litter.show") && race.cars > 0) {
            show(race);
        }
    }

    /**
     * For a hidden test run's screenshots ({@code -Dnfm.litter.show=true}):
     * what a few laps of drifting would leave, around the first car at the
     * start: crumbs of rubber and stones of the ground's colour.
     */
    private void show(RaceView race) {
        for (int k = 0; k < 400; k++) {
            float wx = race.x[0] + dice.between(-350F, 350F);
            float wz = race.z[0] + dice.between(-600F, 1500F);
            // shed's own dice decide; asked hard enough that most land
            shed(wx, GROUND, wz, dice.between(-20F, 20F), 40F, 4F, k % 2 == 0, race.groundColor, false);
        }
    }

    /** The slot of the {@code k}-th oldest bit lying now. */
    int slot(int k) {
        return (next - count + k + BITS) % BITS;
    }

    /**
     * A wheel sliding at {@code (wx, wy, wz)}, moving {@code (wvx, wvz)} a
     * tick, {@code level} of the way to a full slide (0 to 1), on loose
     * ground of colour {@code ground} or on the road: what it sheds.
     */
    void shed(float wx, float wy, float wz, float wvx, float wvz, float level, boolean loose, int ground,
            boolean onRamp) {
        if (dice.next() >= level * (loose ? STONE_RATE : RUBBER_RATE)) {
            return;
        }
        float speed = (float) Math.sqrt(wvx * wvx + wvz * wvz);
        float ax = speed > 0.01F ? wvx / speed : 1F;
        float az = speed > 0.01F ? wvz / speed : 0F;
        // behind the wheel along its way and to either side of it; a stone flies farther than a crumb
        float back = dice.between(0F, loose ? 60F : 30F);
        float aside = loose ? dice.between(-80F, 80F) : dice.between(-38F, 38F);
        if (onRamp || Math.abs(wy - GROUND) > 8F) {
            back *= 0.1F; // a ramp slopes: only right where the wheel is, at its height
            aside *= 0.1F;
        }
        int i = next;
        next = (next + 1) % BITS;
        count = Math.min(BITS, count + 1);
        dropped++;
        x[i] = wx - ax * back - az * aside;
        z[i] = wz - az * back + ax * aside;
        raised[i] = onRamp || Math.abs(wy - GROUND) > 8F;
        y[i] = raised[i] ? wy : GROUND;
        turn[i] = dice.between(0F, 6.2832F);
        if (loose) {
            // a stone of the ground's own colour, paler or darker
            size[i] = dice.between(6F, 15F);
            rgb[i] = shade(ground, dice.between(0.62F, 1.2F));
            corners[i] = (byte) (4 + dice.below(2));
            light[i] = dice.between(0.12F, 0.4F);
            high[i] = size[i] * dice.between(0.3F, 0.55F);
        } else {
            size[i] = dice.between(4.5F, 10F);
            high[i] = size[i] * dice.between(0.15F, 0.35F);
            int grey = 0x1a + dice.below(0x16);
            rgb[i] = grey << 16 | grey << 8 | grey + 2;
            corners[i] = (byte) (3 + dice.below(3));
            light[i] = dice.between(0.5F, 1F);
        }
        vx[i] = 0F;
        vz[i] = 0F;
        wentX[i] = 0F;
        wentZ[i] = 0F;
        rock[i] = 0F;
        lift[i] = 0F;
        liftWas[i] = 0F;
    }

    /** A tick ends: the bits near a car going fast enough are stirred by its wind; the others come to rest. */
    void tick(RaceView race) {
        tick = race.tick;
        stirred = 0;
        for (int k = 0; k < count; k++) {
            int i = slot(k);
            if (rock[i] != 0F) {
                rock[i] = rock[i] < 0.02F ? 0F : rock[i] * SETTLES;
            }
            wentX[i] = vx[i];
            wentZ[i] = vz[i];
            if (vx[i] != 0F || vz[i] != 0F) {
                x[i] += vx[i];
                z[i] += vz[i];
                vx[i] *= GRIP;
                vz[i] *= GRIP;
                if (vx[i] * vx[i] + vz[i] * vz[i] < 0.0025F) {
                    vx[i] = 0F;
                    vz[i] = 0F;
                }
            }
        }
        for (int c = 0; c < race.cars; c++) {
            float cvx = race.vx[c];
            float cvz = race.vz[c];
            float speed = (float) Math.sqrt(cvx * cvx + cvz * cvz);
            if (speed < WIND_FROM) {
                continue;
            }
            wind(race.x[c], race.y[c] + race.wheelDrop[c], race.z[c], cvx / speed, cvz / speed,
                    Math.min(1F, (speed - WIND_FROM) / (WIND_FULL - WIND_FROM)));
        }
        for (int k = 0; k < count; k++) {
            int i = slot(k);
            liftWas[i] = lift[i];
            // rocking: one side lifts and falls back, a little differently for every bit
            lift[i] = rock[i] == 0F ? 0F
                    : rock[i] * size[i] * 0.7F * Math.abs((float) Math.sin(tick * 1.7F + i * 2.4F));
        }
    }

    /** The wind of a car at {@code (px, py, pz)} (its wheels' height) going along {@code (ax, az)}, {@code force} of the fullest. */
    private void wind(float px, float py, float pz, float ax, float az, float force) {
        for (int k = 0; k < count; k++) {
            int i = slot(k);
            float dx = x[i] - px;
            float dz = z[i] - pz;
            if (dx > WIND_REACH || dx < -WIND_REACH || dz > WIND_REACH || dz < -WIND_REACH) {
                continue;
            }
            float d2 = dx * dx + dz * dz;
            if (d2 >= WIND_REACH * WIND_REACH || Math.abs(y[i] - py) > 120F) {
                continue;
            }
            float gust = force * (1F - (float) Math.sqrt(d2) / WIND_REACH);
            stirred++;
            rock[i] = Math.max(rock[i], gust);
            if (raised[i]) {
                continue;
            }
            // along with the car, and out of its way to the side the bit lies on
            float side = dx * az - dz * ax;
            float out = side >= 0F ? 1F : -1F;
            float push = gust * WIND_PUSH * light[i];
            vx[i] += (ax * 0.55F + az * out * 0.45F) * push;
            vz[i] += (az * 0.55F - ax * out * 0.45F) * push;
        }
    }

    /* ------------------------------------------------------------ drawing */

    /**
     * Queues the bits for a frame: those on the flat ground as the ground
     * item {@code groundItem}, each one on a ramp near enough as
     * {@code base} + its slot.
     */
    void queue(DrawQueue queue, int groundItem, int base) {
        eyeX = queue.eyeX();
        eyeZ = queue.eyeZ();
        float eyeY = queue.eyeY();
        drawn = 0;
        boolean road = false;
        for (int k = 0; k < count; k++) {
            int i = slot(k);
            if (!raised[i]) {
                road = true;
            } else if (near(i)) {
                // pulled a little towards the eye, so the ramp under it is drawn first
                queue.at(eyeX + (x[i] - eyeX) * 0.85F, eyeY + (y[i] - eyeY) * 0.85F, eyeZ + (z[i] - eyeZ) * 0.85F,
                        base + i);
            }
        }
        if (road) {
            queue.ground(groundItem);
        }
    }

    /** Draws every bit on the flat ground that is near enough to be seen. */
    void drawGround(Canvas canvas) {
        for (int k = 0; k < count; k++) {
            int i = slot(k);
            if (!raised[i] && near(i)) {
                draw(canvas, i);
            }
        }
    }

    private boolean near(int i) {
        float dx = x[i] - eyeX;
        float dz = z[i] - eyeZ;
        float d2 = dx * dx + dz * dz;
        return d2 < SEEN * SEEN / 4F || d2 < SEEN * SEEN && (i & 1) == 0;
    }

    /**
     * Draws the bit in slot {@code i}: flat on the ground, one side lifted
     * while it rocks, on its way while the wind shifts it (each corner with
     * its own move, so it goes smoothly between two ticks).
     */
    void draw(Canvas canvas, int i) {
        if (i < 0 || i >= BITS) {
            return;
        }
        int n = corners[i];
        float r = size[i] / 2F;
        boolean still = wentX[i] == 0F && wentZ[i] == 0F && lift[i] == 0F && liftWas[i] == 0F;
        for (int k = 0; k < n; k++) {
            float a = turn[i] + k * (float) (2D * Math.PI / n);
            float reach = (k & 1) == 0 ? r : r * 0.72F;
            // the side turned one way is the one that lifts
            float lifts = 0.5F + 0.5F * (float) Math.cos(k * (float) (2D * Math.PI / n));
            cx[k] = x[i] + (float) Math.cos(a) * reach;
            cy[k] = y[i] - 0.4F - lift[i] * lifts;
            cz[k] = z[i] + (float) Math.sin(a) * reach;
            mx[k] = wentX[i];
            my[k] = -(lift[i] - liftWas[i]) * lifts;
            mz[k] = wentZ[i];
        }
        float ex = eyeX - x[i];
        float ez = eyeZ - z[i];
        boolean lump = ex * ex + ez * ez < LUMP_WITHIN * LUMP_WITHIN;
        // the foot, in the shade of the lump over it
        canvas.color(canvas.fog(lump ? shade(rgb[i], 0.7F) : rgb[i], x[i], y[i], z[i]));
        canvas.dither(1F);
        if (still) {
            canvas.world(cx, cy, cz, n);
        } else {
            canvas.moving(cx, cy, cz, mx, my, mz, n);
        }
        if (lump) {
            // the sides up to the peak: those turned away from the eye first, so the near ones cover them
            float peak = y[i] - 0.4F - high[i] - lift[i] * 0.5F;
            float peakMove = -(lift[i] - liftWas[i]) * 0.5F;
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < n; k++) {
                    int j = (k + 1) % n;
                    float midX = (cx[k] + cx[j]) / 2F - x[i];
                    float midZ = (cz[k] + cz[j]) / 2F - z[i];
                    boolean facing = midX * ex + midZ * ez >= 0F;
                    if (facing != (pass == 1)) {
                        continue;
                    }
                    sx[0] = cx[k];
                    sy[0] = cy[k];
                    sz[0] = cz[k];
                    sx[1] = cx[j];
                    sy[1] = cy[j];
                    sz[1] = cz[j];
                    sx[2] = x[i];
                    sy[2] = peak;
                    sz[2] = z[i];
                    // lit from one side of the world: the sides turned to it paler
                    float lit = 0.95F + 0.25F * (midX * 0.6F - midZ * 0.8F) / Math.max(0.01F, r);
                    canvas.color(canvas.fog(shade(rgb[i], lit), x[i], y[i], z[i]));
                    if (still) {
                        canvas.world(sx, sy, sz, 3);
                    } else {
                        smx[0] = mx[k];
                        smy[0] = my[k];
                        smz[0] = mz[k];
                        smx[1] = mx[j];
                        smy[1] = my[j];
                        smz[1] = mz[j];
                        smx[2] = wentX[i];
                        smy[2] = peakMove;
                        smz[2] = wentZ[i];
                        canvas.moving(sx, sy, sz, smx, smy, smz, 3);
                    }
                }
            }
        }
        drawn++;
    }

    private static int shade(int color, float f) {
        int r = Math.min(255, (int) ((color >> 16 & 0xff) * f));
        int g = Math.min(255, (int) ((color >> 8 & 0xff) * f));
        int b = Math.min(255, (int) ((color & 0xff) * f));
        return r << 16 | g << 8 | b;
    }
}
