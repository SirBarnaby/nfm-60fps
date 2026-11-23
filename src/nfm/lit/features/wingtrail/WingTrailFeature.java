package nfm.lit.features.wingtrail;

import java.util.Arrays;
import nfm.lit.features.Canvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.RaceView;

/**
 * The Radical One's wing trail (effects, "The Radical One's wing trail" and
 * "The wing trail looks like vapour"): while the car has been off the
 * ground for 0.15 s and its driver steers it in the air, thin white streams
 * of vapour come off both wing tips, like wing-tip vortices, spreading and
 * fading within about a second. Letting go of the keys fades the trail out;
 * landing fades it quickly.
 * <p>
 * Each stream is a short ring of points left in the air, one a tick, drawn
 * as a ribbon facing the camera; its newest stretch reaches the tip where
 * the car is drawn in each frame (the tip's corners move with it between
 * ticks), so the trail never steps. It only reads the race.
 */
public final class WingTrailFeature implements Feature {

    public static final String ID = "wingtrail";

    /** Ticks off the ground before a trail may show: 0.15 s at 19 ticks a second. */
    static final int AIRBORNE = 3;
    /** How long a point of vapour lasts, ticks (about a second). */
    static final int LIFE = 19;
    /** Points kept per stream. */
    static final int POINTS = LIFE + 1;
    /** How fast the trail comes and goes, a tick: steering, letting go, landing. */
    static final float RISE = 0.34F;
    static final float RELEASE = 0.2F;
    static final float LANDING = 0.5F;
    /** A stream's half width as it leaves the tip and as it fades, world units. */
    static final float THIN = 0.9F;
    static final float SPREAD = 7F;
    /** How fast a point drifts out from the car's line, units a tick, and how much of the car's speed it keeps. */
    private static final float DRIFT = 0.35F;
    private static final float CARRY = 0.08F;
    private static final int WHITE = 0xf4f6fa;

    private int cars;
    /* per car */
    float[] level = new float[0];
    private boolean[] radical = new boolean[0];
    /* per stream (car * 2 + side): a ring of points */
    float[] px = new float[0];
    float[] py = new float[0];
    float[] pz = new float[0];
    private float[] dx = new float[0];
    private float[] dy = new float[0];
    private float[] dz = new float[0];
    int[] age = new int[0];
    float[] strength = new float[0];
    int[] head = new int[0];
    int[] count = new int[0];
    /** The tip's move over the last tick (how the newest stretch's tip end moves between ticks). */
    float[] tipVX = new float[0];
    float[] tipVY = new float[0];
    float[] tipVZ = new float[0];
    /** Where the tip is after the last tick. */
    float[] tipX = new float[0];
    float[] tipY = new float[0];
    float[] tipZ = new float[0];
    private boolean[] tipKnown = new boolean[0];

    private final float[] tip = new float[3];
    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];
    private final float[] vx = new float[4];
    private final float[] vy = new float[4];
    private final float[] vz = new float[4];

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void raceStarted(RaceView race) {
        cars = race.cars;
        if (level.length < cars) {
            level = new float[cars];
            radical = new boolean[cars];
            int streams = cars * 2;
            px = new float[streams * POINTS];
            py = new float[streams * POINTS];
            pz = new float[streams * POINTS];
            dx = new float[streams * POINTS];
            dy = new float[streams * POINTS];
            dz = new float[streams * POINTS];
            age = new int[streams * POINTS];
            strength = new float[streams * POINTS];
            head = new int[streams];
            count = new int[streams];
            tipVX = new float[streams];
            tipVY = new float[streams];
            tipVZ = new float[streams];
            tipX = new float[streams];
            tipY = new float[streams];
            tipZ = new float[streams];
            tipKnown = new boolean[streams];
        }
        Arrays.fill(level, 0F);
        Arrays.fill(count, 0);
        Arrays.fill(tipKnown, false);
        for (int c = 0; c < cars; c++) {
            radical[c] = WingTips.of(race.model[c]) != null;
        }
    }

    @Override
    public void afterTick(RaceView race) {
        if (race.cars != cars) {
            raceStarted(race);
        }
        for (int c = 0; c < cars; c++) {
            if (radical[c]) {
                car(race, c);
            }
        }
    }

    /** True if car {@code c} is in the air long enough and its driver steers it there. */
    static boolean steeringInAir(RaceView race, int c) {
        boolean keys = race.up[c] || race.down[c] || race.left[c] || race.right[c];
        return race.airTicks[c] >= AIRBORNE && keys && !race.wasted[c];
    }

    private void car(RaceView race, int c) {
        if (steeringInAir(race, c)) {
            level[c] = Math.min(1F, level[c] + RISE);
        } else if (race.grounded[c]) {
            level[c] = Math.max(0F, level[c] - LANDING);
        } else {
            level[c] = Math.max(0F, level[c] - RELEASE);
        }
        float[] wing = WingTips.of(race.model[c]);
        float scale = WingTips.scale(race, c);
        for (int side = 0; side < 2; side++) {
            int s = c * 2 + side;
            race.toWorld(c, (side == 0 ? -1F : 1F) * wing[0] * scale, wing[1] * scale, wing[2] * scale, tip);
            tipVX[s] = tipKnown[s] ? tip[0] - tipX[s] : 0F;
            tipVY[s] = tipKnown[s] ? tip[1] - tipY[s] : 0F;
            tipVZ[s] = tipKnown[s] ? tip[2] - tipZ[s] : 0F;
            tipX[s] = tip[0];
            tipY[s] = tip[1];
            tipZ[s] = tip[2];
            tipKnown[s] = true;
            age(s, race, c, side);
            if (level[c] > 0F) {
                emit(s, race, c, side);
            }
        }
    }

    /** Every point of stream {@code s} ages a tick and drifts; those a second old go. */
    private void age(int s, RaceView race, int c, int side) {
        int base = s * POINTS;
        for (int k = 0; k < count[s]; k++) {
            int i = base + (head[s] - k + POINTS) % POINTS;
            age[i]++;
            px[i] += dx[i];
            py[i] += dy[i];
            pz[i] += dz[i];
            dx[i] *= 0.9F;
            dy[i] *= 0.9F;
            dz[i] *= 0.9F;
        }
        while (count[s] > 0) {
            int oldest = base + (head[s] - count[s] + 1 + POINTS) % POINTS;
            if (age[oldest] < LIFE) {
                break;
            }
            count[s]--;
        }
    }

    /** A new point of vapour at stream {@code s}'s tip. */
    private void emit(int s, RaceView race, int c, int side) {
        int base = s * POINTS;
        head[s] = (head[s] + 1) % POINTS;
        count[s] = Math.min(POINTS, count[s] + 1);
        int i = base + head[s];
        px[i] = tipX[s];
        py[i] = tipY[s];
        pz[i] = tipZ[s];
        // out from the car's line, the way a vortex spreads, and a little of the car's way
        float yaw = race.xz[c] * (float) (Math.PI / 180D);
        float out = side == 0 ? -1F : 1F;
        dx[i] = (float) Math.cos(yaw) * out * DRIFT + race.vx[c] * CARRY;
        dy[i] = race.vy[c] * CARRY;
        dz[i] = (float) Math.sin(yaw) * out * DRIFT + race.vz[c] * CARRY;
        age[i] = 0;
        strength[i] = level[c];
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        for (int s = 0; s < cars * 2; s++) {
            int base = s * POINTS;
            for (int k = 1; k < count[s]; k++) {
                // stretch k runs from point k - 1 (newer) to point k; queued at its middle
                int i = base + (head[s] - k + POINTS) % POINTS;
                int j = base + (head[s] - k + 1 + POINTS) % POINTS;
                queue.at((px[i] + px[j]) / 2F, (py[i] + py[j]) / 2F, (pz[i] + pz[j]) / 2F, s * POINTS + k);
            }
        }
    }

    @Override
    public void draw(Canvas canvas, int item) {
        int s = item / POINTS;
        int k = item % POINTS;
        if (s < 0 || s >= cars * 2 || k < 1 || k >= count[s]) {
            return;
        }
        int base = s * POINTS;
        int i = base + (head[s] - k + POINTS) % POINTS;
        int j = base + (head[s] - k + 1 + POINTS) % POINTS;
        float coverage = Math.min(shade(age[j], strength[j]), shade(age[i], strength[i]) + 0.1F);
        if (coverage <= 0.02F || !ribbon(px[j], py[j], pz[j], width(age[j]), px[i], py[i], pz[i], width(age[i]))) {
            return;
        }
        canvas.color(canvas.fog(WHITE, (px[i] + px[j]) / 2F, (py[i] + py[j]) / 2F, (pz[i] + pz[j]) / 2F));
        canvas.dither(coverage);
        if (k == 1 && level[s / 2] > 0F) {
            // the newest stretch: its newer end is at the tip, and between ticks moves with it, as the car is
            // drawn; the older end stays where it was left in the air
            vx[0] = vx[1] = tipVX[s];
            vy[0] = vy[1] = tipVY[s];
            vz[0] = vz[1] = tipVZ[s];
            vx[2] = vx[3] = 0F;
            vy[2] = vy[3] = 0F;
            vz[2] = vz[3] = 0F;
            canvas.moving(cx, cy, cz, vx, vy, vz, 4);
        } else {
            canvas.world(cx, cy, cz, 4);
        }
    }

    /** A stream's half width at {@code age} ticks: thin at the tip, spreading. */
    static float width(int age) {
        float t = Math.min(1F, age / (float) LIFE);
        return THIN + (SPREAD - THIN) * t;
    }

    /** How much of its pixels a stretch covers at {@code age} ticks, made at {@code strength}: fading to nothing in a second. */
    static float shade(int age, float strength) {
        float t = Math.min(1F, age / (float) LIFE);
        return 0.8F * strength * (1F - t) * (1F - t * 0.3F);
    }

    /** The corners of a ribbon from {@code a} to {@code b}, half widths {@code wa} and {@code wb}, facing the eye. */
    private boolean ribbon(float ax, float ay, float az, float wa, float bx, float by, float bz, float wb) {
        float lx = bx - ax;
        float ly = by - ay;
        float lz = bz - az;
        float mx = (ax + bx) / 2F - eyeX;
        float my = (ay + by) / 2F - eyeY;
        float mz = (az + bz) / 2F - eyeZ;
        // across = along x view
        float ox = ly * mz - lz * my;
        float oy = lz * mx - lx * mz;
        float oz = lx * my - ly * mx;
        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-4F) {
            return false;
        }
        ox /= len;
        oy /= len;
        oz /= len;
        cx[0] = ax - ox * wa;
        cy[0] = ay - oy * wa;
        cz[0] = az - oz * wa;
        cx[1] = ax + ox * wa;
        cy[1] = ay + oy * wa;
        cz[1] = az + oz * wa;
        cx[2] = bx + ox * wb;
        cy[2] = by + oy * wb;
        cz[2] = bz + oz * wb;
        cx[3] = bx - ox * wb;
        cy[3] = by - oy * wb;
        cz[3] = bz - oz * wb;
        return true;
    }
}
