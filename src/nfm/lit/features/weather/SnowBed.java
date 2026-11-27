package nfm.lit.features.weather;

import java.util.HashSet;
import java.util.Set;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.RaceView;
import nfm.lit.features.Weather;

/**
 * The snow as a bed over the whole map (weather, "Snow is a bed over the
 * whole map"): it lies everywhere, a few units deep at the start and
 * deeper as the race goes on, and the cars change it. Looks only.
 * <ul>
 * <li>A car's shape pressed into it: where a car lands, comes to rest on
 * its side or roof, is wasted, or stood a while as the snow fell around
 * it, its own outline stays as a hollow with walls.
 * <li>Drifts: low ridges the wind has blown up, all over the map, on the
 * road and far off it, higher as the bed deepens. A car through one
 * flattens it; the snowfall builds it up again.
 * <li>The snow about a car: its wheels stand in the bed, snow banked
 * around each, and a car whose floor is lower than the bed is deep pushes
 * a wave of snow ahead of its nose.
 * <li>Spindrift: streamers of loose snow the wind drives low over the
 * bed, more of them in a gust.
 * </ul>
 * The trails and the channel a body cuts are {@link Snow}'s.
 */
final class SnowBed {

    /** How deep the bed is, units: thin when a race starts, and as much deeper at the snow's cap. */
    static final float THIN = 8F;
    static final float DEEPENS = 30F;

    /** The bed's depth in units for the snow's depth 0 to 1. */
    static float deep(float depth) {
        return THIN + DEEPENS * Math.max(0F, Math.min(1F, depth));
    }

    /* ---------------------------------------------------------- imprints */

    static final int IMPRINTS = 192;
    /** Corners of a car's outline. */
    static final int CORNERS = 12;
    /** Ticks of snowfall that fill a car's imprint in again. */
    static final int IMPRINT_FILLS = 19 * 90;
    /** A landing after this many ticks in the air presses the car's shape into the bed. */
    static final int LANDING = 8;
    /** A car that stood this long leaves its shape where the snow fell around it. */
    static final int STOOD = 19 * 3;
    private static final float GROUND = 250F;

    private final float[] printX = new float[IMPRINTS * CORNERS];
    private final float[] printZ = new float[IMPRINTS * CORNERS];
    private final int[] printN = new int[IMPRINTS];
    private final float[] printY = new float[IMPRINTS];
    private final float[] printCut = new float[IMPRINTS];
    private final int[] printTick = new int[IMPRINTS];
    private final boolean[] printLive = new boolean[IMPRINTS];
    private int printNext;
    int imprints;

    /* ------------------------------------------------------------ drifts */

    /** One drift, or none, in every square of this size all over the map. */
    static final float CELL = 900F;
    /** Farther than this from the eye a drift is not drawn; at most this many in a frame. */
    static final float DRIFTS_SEEN = 4300F;
    static final int DRIFTS = 96;
    /** A drift's height at the snow's cap, units, and the ticks of snowfall that build a flattened one up again. */
    static final float DRIFT_HIGH = 30F;
    static final int REGROWS = 19 * 45;
    private static final int FLAT = 256;
    private static final int DEEP_SHADE = 0x93a2ba;

    private final Set<Long> blocked = new HashSet<>();
    private final long[] flatKey = new long[FLAT];
    private final int[] flatTick = new int[FLAT];
    private int flats;
    /** Drifts cars have flattened since the race started. */
    int flattened;

    /* the drifts of the frame being drawn */
    private final float[] dX = new float[DRIFTS];
    private final float[] dZ = new float[DRIFTS];
    private final float[] dAX = new float[DRIFTS];
    private final float[] dAZ = new float[DRIFTS];
    private final float[] dLong = new float[DRIFTS];
    private final float[] dWide = new float[DRIFTS];
    private final float[] dHigh = new float[DRIFTS];
    private int queuedDrifts;

    /* --------------------------------------------------------- spindrift */

    private static final int STREAMERS = 150;
    private static final float STREAM_SIDE = 4200F;
    private static final float STREAM_MIDDLE = 1300F;
    private static final float STREAM_FAR = 3000F;

    /* ----------------------------------------------------------- the cars */

    /** Nearer than this to the eye the snow about a car's wheels and nose is drawn. */
    static final float ABOUT_CARS = 3000F;
    /** Items per car: one for each wheel, one for the wave at its nose. */
    static final int PER_CAR = 8;
    private static final int NOSE = 4;

    private final Snow snow;
    private int cars;
    private float deep = THIN;
    private int tick;
    private float windX = 1F;
    private float windZ;
    private Gusts gusts;
    private float[][] hullX = new float[0][];
    private float[][] hullZ = new float[0][];
    private int[] hullN = new int[0];
    private int[] lastAir = new int[0];
    private int[] stood = new int[0];
    private float[] stoodX = new float[0];
    private float[] stoodY = new float[0];
    private float[] stoodZ = new float[0];
    private int[] stoodYaw = new int[0];
    private boolean[] lyingDone = new boolean[0];
    private boolean[] wastedDone = new boolean[0];
    /* each car after the last tick, for what is drawn about it */
    private float[] carX = new float[0];
    private float[] carY = new float[0];
    private float[] carZ = new float[0];
    private float[] carVX = new float[0];
    private float[] carVZ = new float[0];
    private float[] headX = new float[0];
    private float[] headZ = new float[0];
    private float[] halfWide = new float[0];
    private float[] noseZ = new float[0];
    private float[] tailZ = new float[0];
    private float[] wheelR = new float[0];
    /** How deep the car's body cuts into the bed while it ploughs, units; 0 while it does not. */
    float[] plough = new float[0];
    private float[] wheelX = new float[0];
    private float[] wheelY = new float[0];
    private float[] wheelZ = new float[0];
    private boolean[] wheelIn = new boolean[0];

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] look = new float[9];
    private final float[] p = new float[3];
    private final float[] cx = new float[CORNERS];
    private final float[] cy = new float[CORNERS];
    private final float[] cz = new float[CORNERS];
    private final float[] mx = new float[4];
    private final float[] my = new float[4];
    private final float[] mz = new float[4];
    private final float[] px = new float[16];
    private final float[] py = new float[16];
    private final float[] pz = new float[16];
    private final float[] hx = new float[16];
    private final float[] hz = new float[16];

    /** Drawn in the last frame: imprints, their walls, drifts, their faces, mounds about wheels, waves at noses, streamers. */
    int drawnPrints;
    int drawnWalls;
    int drawnDrifts;
    int drawnFaces;
    int drawnWheels;
    int drawnNoses;
    int drawnStreamers;

    SnowBed(Snow snow) {
        this.snow = snow;
    }

    /** A race starts in snow: each car's outline, and where the stage's pieces leave no room for a drift. */
    void start(RaceView r, float windX, float windZ, Gusts gusts) {
        float wind = (float) Math.sqrt(windX * windX + windZ * windZ);
        this.windX = wind < 1e-3F ? 1F : windX / wind;
        this.windZ = wind < 1e-3F ? 0F : windZ / wind;
        this.gusts = gusts;
        tick = r.tick;
        cars = r.cars;
        java.util.Arrays.fill(printLive, false);
        printNext = 0;
        imprints = 0;
        flats = 0;
        flattened = 0;
        queuedDrifts = 0;
        if (hullN.length < cars) {
            hullX = new float[cars][CORNERS];
            hullZ = new float[cars][CORNERS];
            hullN = new int[cars];
            lastAir = new int[cars];
            stood = new int[cars];
            stoodX = new float[cars];
            stoodY = new float[cars];
            stoodZ = new float[cars];
            stoodYaw = new int[cars];
            lyingDone = new boolean[cars];
            wastedDone = new boolean[cars];
            carX = new float[cars];
            carY = new float[cars];
            carZ = new float[cars];
            carVX = new float[cars];
            carVZ = new float[cars];
            headX = new float[cars];
            headZ = new float[cars];
            halfWide = new float[cars];
            noseZ = new float[cars];
            tailZ = new float[cars];
            wheelR = new float[cars];
            plough = new float[cars];
            wheelX = new float[cars * RaceView.WHEELS];
            wheelY = new float[cars * RaceView.WHEELS];
            wheelZ = new float[cars * RaceView.WHEELS];
            wheelIn = new boolean[cars * RaceView.WHEELS];
        }
        for (int c = 0; c < cars; c++) {
            CarShape s = r.shape[c];
            outline(c, s);
            lastAir[c] = 0;
            stood[c] = 0;
            lyingDone[c] = false;
            wastedDone[c] = r.wasted[c];
            plough[c] = 0F;
            boolean sized = s != null && s.maxX > s.minX && s.maxZ > s.minZ;
            halfWide[c] = sized ? (s.maxX - s.minX) / 2F : 40F;
            noseZ[c] = sized ? s.maxZ : 80F;
            tailZ[c] = sized ? s.minZ : -80F;
            wheelR[c] = s != null && s.wheelRadius[2] > 0F ? s.wheelRadius[2] : 26F;
        }
        java.util.Arrays.fill(wheelIn, false);
        block(r);
        place(r);
    }

    void stop() {
        cars = 0;
        imprints = 0;
        queuedDrifts = 0;
        java.util.Arrays.fill(printLive, false);
    }

    /* ----------------------------------------------------- a car's outline */

    /** Car {@code c}'s outline seen from above: the hull of its body's corners, cut down to {@link #CORNERS}. */
    private void outline(int c, CarShape s) {
        int n = 0;
        float[] xs = new float[0];
        float[] zs = new float[0];
        if (s != null) {
            int corners = 0;
            for (int i = 0; i < s.planes; i++) {
                corners += s.wheel[i] < 0 ? s.count[i] : 0;
            }
            xs = new float[corners];
            zs = new float[corners];
            for (int i = 0; i < s.planes; i++) {
                for (int k = s.first[i]; s.wheel[i] < 0 && k < s.first[i] + s.count[i]; k++) {
                    xs[n] = s.x[k];
                    zs[n++] = s.z[k];
                }
            }
        }
        if (n < 3) {
            // no model: a car's rough size
            xs = new float[] {-40F, 40F, 40F, -40F};
            zs = new float[] {-80F, -80F, 80F, 80F};
            n = 4;
        }
        float[][] hull = hull(xs, zs, n);
        hullN[c] = hull[0].length;
        System.arraycopy(hull[0], 0, hullX[c], 0, hullN[c]);
        System.arraycopy(hull[1], 0, hullZ[c], 0, hullN[c]);
    }

    /** The convex hull of {@code n} points, in order round it, with at most {@link #CORNERS} corners: the least bends go first. */
    static float[][] hull(float[] xs, float[] zs, int n) {
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> xs[a] != xs[b] ? Float.compare(xs[a], xs[b]) : Float.compare(zs[a], zs[b]));
        int[] h = new int[2 * n + 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(xs, zs, h[k - 2], h[k - 1], order[i]) <= 0F) {
                k--;
            }
            h[k++] = order[i];
        }
        for (int i = n - 2, low = k + 1; i >= 0; i--) {
            while (k >= low && cross(xs, zs, h[k - 2], h[k - 1], order[i]) <= 0F) {
                k--;
            }
            h[k++] = order[i];
        }
        k = Math.max(1, k - 1); // the last is the first again
        while (k > CORNERS) {
            int least = 0;
            float bend = Float.MAX_VALUE;
            for (int i = 0; i < k; i++) {
                float b = Math.abs(cross(xs, zs, h[(i + k - 1) % k], h[i], h[(i + 1) % k]));
                if (b < bend) {
                    bend = b;
                    least = i;
                }
            }
            System.arraycopy(h, least + 1, h, least, k - least - 1);
            k--;
        }
        float[][] out = new float[2][k];
        for (int i = 0; i < k; i++) {
            out[0][i] = xs[h[i]];
            out[1][i] = zs[h[i]];
        }
        return out;
    }

    private static float cross(float[] xs, float[] zs, int o, int a, int b) {
        return (xs[a] - xs[o]) * (zs[b] - zs[o]) - (zs[a] - zs[o]) * (xs[b] - xs[o]);
    }

    /* -------------------------------------------------------------- tick */

    /** One tick of a race in snow lying {@code deepNow} units deep. */
    void tick(RaceView r, float deepNow) {
        if (r.cars != cars) {
            return; // the snow starts over first
        }
        tick = r.tick;
        deep = deepNow;
        Dice dice = snow.dice();
        for (int c = 0; c < cars; c++) {
            if (snow.marks(r, c)) {
                car(r, c, dice);
            }
            lastAir[c] = r.airTicks[c];
        }
        place(r);
    }

    private void car(RaceView r, int c, Dice dice) {
        float speed = r.groundSpeed(c);
        boolean down = lying(r, c);
        // a landing: the harder, the deeper its shape goes in
        if (r.grounded[c] && lastAir[c] >= LANDING) {
            float hard = Math.min(1F, 0.35F + lastAir[c] / 28F);
            stamp(r, c, deep * hard, down);
            if (lastAir[c] >= Snow.HARD_LANDING) {
                for (int k = 0; k < 3; k++) {
                    Weather.lump(r.x[c] + dice.between(-60F, 60F), Snow.groundUnder(r, c) - 8F,
                            r.z[c] + dice.between(-60F, 60F), r.vx[c] * 0.4F + dice.between(-7F, 7F),
                            -dice.between(5F, 11F), r.vz[c] * 0.4F + dice.between(-7F, 7F), dice.between(8F, 14F));
                }
            }
        }
        // at rest on its side or roof: its shape as it lies
        if (!down) {
            lyingDone[c] = false;
        } else if (speed < 3F && !lyingDone[c]) {
            lyingDone[c] = true;
            stamp(r, c, deep * 0.8F, true);
        }
        if (r.wasted[c] && !wastedDone[c]) {
            stamp(r, c, deep, down);
        }
        wastedDone[c] = r.wasted[c];
        // it stood while the snow fell around it: its shape stays when it drives off (the grid has its dry spots)
        if (speed < 0.5F && r.grounded[c] && !down && r.tick > 19 * 8) {
            if (stood[c]++ == 0) {
                stoodX[c] = r.x[c];
                stoodY[c] = Snow.groundUnder(r, c);
                stoodZ[c] = r.z[c];
                stoodYaw[c] = r.xz[c];
            }
        } else if (speed > 3F) {
            if (stood[c] >= STOOD) {
                upright(c, stoodX[c], stoodY[c], stoodZ[c], stoodYaw[c], Math.min(deep, 3F + stood[c] / 40F));
            }
            stood[c] = 0;
        }
        if (r.grounded[c] && speed > 3F) {
            through(r, c, dice);
        }
    }

    /** True if car {@code c} lies on its side or roof on the ground. */
    private static boolean lying(RaceView r, int c) {
        double roll = Math.toRadians(r.xy[c]);
        double pitch = Math.toRadians(r.zy[c]);
        return (r.capsized[c] || Math.cos(roll) * Math.cos(pitch) < Snow.LYING) && r.y[c] > GROUND - 100F
                && Math.abs(r.vy[c]) < 9F;
    }

    /** Each car's place and what stands in the bed, after the tick. */
    private void place(RaceView r) {
        for (int c = 0; c < cars; c++) {
            double yaw = Math.toRadians(r.xz[c]);
            carX[c] = r.x[c];
            carY[c] = Snow.groundUnder(r, c);
            carZ[c] = r.z[c];
            carVX[c] = r.vx[c];
            carVZ[c] = r.vz[c];
            headX[c] = (float) -Math.sin(yaw);
            headZ[c] = (float) Math.cos(yaw);
            int touching = 0;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                int at = c * RaceView.WHEELS + w;
                wheelIn[at] = r.contact[at] && !r.onRamp[at] && !r.wasted[c];
                wheelX[at] = r.wheelX[at];
                wheelY[at] = r.wheelY[at];
                wheelZ[at] = r.wheelZ[at];
                touching += wheelIn[at] ? 1 : 0;
            }
            float cut = deep - snow.clearance(c);
            float speed = r.groundSpeed(c);
            plough[c] = touching >= 2 && cut > 1F && speed > 4F ? cut * Math.min(1F, speed / 20F) : 0F;
        }
    }

    /* ---------------------------------------------------------- imprints */

    /** Car {@code c}'s shape goes into the bed where it is, {@code cut} deep: as it stands, or as it lies. */
    void stamp(RaceView r, int c, float cut, boolean lies) {
        if (r.y[c] < GROUND - 220F && Snow.groundUnder(r, c) == GROUND) {
            return; // high up on something, no wheel on it: no bed under it to press
        }
        if (!lies) {
            upright(c, r.x[c], Snow.groundUnder(r, c), r.z[c], r.xz[c], cut);
            return;
        }
        // lying: the hull of its body's box as it lies, seen from above
        CarShape s = r.shape[c];
        boolean sized = s != null && s.maxX > s.minX && s.maxZ > s.minZ;
        for (int k = 0; k < 8; k++) {
            float lx = sized ? (k & 1) == 0 ? s.minX : s.maxX : (k & 1) == 0 ? -40F : 40F;
            float ly = sized ? (k & 2) == 0 ? s.minY : s.maxY : (k & 2) == 0 ? -40F : 0F;
            float lz = sized ? (k & 4) == 0 ? s.minZ : s.maxZ : (k & 4) == 0 ? -80F : 80F;
            r.toWorld(c, lx, ly, lz, p);
            hx[k] = p[0];
            hz[k] = p[2];
        }
        float[][] hull = hull(hx, hz, 8);
        print(hull[0], hull[1], hull[0].length, GROUND, cut);
    }

    private void upright(int c, float x, float y, float z, int yawDegrees, float cut) {
        double yaw = Math.toRadians(yawDegrees);
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);
        int n = hullN[c];
        for (int k = 0; k < n; k++) {
            hx[k] = x + hullX[c][k] * cos - hullZ[c][k] * sin;
            hz[k] = z + hullX[c][k] * sin + hullZ[c][k] * cos;
        }
        print(hx, hz, n, y, cut);
    }

    private void print(float[] xs, float[] zs, int n, float y, float cut) {
        if (n < 3 || cut < 1F || snow.cleared(xs[0], zs[0])) {
            return;
        }
        int i = printNext;
        printNext = (printNext + 1) % IMPRINTS;
        if (!printLive[i]) {
            imprints++;
        }
        printLive[i] = true;
        printN[i] = n;
        printY[i] = y;
        printCut[i] = cut;
        printTick[i] = tick;
        System.arraycopy(xs, 0, printX, i * CORNERS, n);
        System.arraycopy(zs, 0, printZ, i * CORNERS, n);
    }

    /** The imprints within {@code r} of {@code (x, z)} are gone (a blast, a fire). */
    void melt(float x, float z, float r) {
        for (int i = 0; i < IMPRINTS; i++) {
            float dx = printX[i * CORNERS] - x;
            float dz = printZ[i * CORNERS] - z;
            if (printLive[i] && dx * dx + dz * dz < r * r) {
                printLive[i] = false;
                imprints--;
            }
        }
    }

    /** Imprint {@code n} of those lying (in no order): its corners into {@code xs, zs}, its depth into {@code out[0]}; returns how many corners, 0 past the last. */
    int imprintAt(int n, float[] xs, float[] zs, float[] out) {
        for (int i = 0; i < IMPRINTS; i++) {
            if (printLive[i] && n-- == 0) {
                System.arraycopy(printX, i * CORNERS, xs, 0, printN[i]);
                System.arraycopy(printZ, i * CORNERS, zs, 0, printN[i]);
                out[0] = printCut[i];
                return printN[i];
            }
        }
        return 0;
    }

    /* ------------------------------------------------------------ drifts */

    private static long key(int ix, int iz) {
        return (long) ix << 32 ^ iz & 0xffffffffL;
    }

    /** The drift in square {@code (ix, iz)} into {@code out}: its middle x and z, half its length and width, its share of the full height, its turn from the wind; false if the square has none. */
    static boolean drift(int ix, int iz, float[] out) {
        if (Fall.hash(ix, iz, 21) > 0.72F) {
            return false;
        }
        out[0] = (ix + 0.2F + 0.6F * Fall.hash(ix, iz, 22)) * CELL;
        out[1] = (iz + 0.2F + 0.6F * Fall.hash(ix, iz, 23)) * CELL;
        out[2] = 120F + 130F * Fall.hash(ix, iz, 24);
        out[3] = 62F + 50F * Fall.hash(ix, iz, 25);
        out[4] = 0.55F + 0.45F * Fall.hash(ix, iz, 26);
        out[5] = (Fall.hash(ix, iz, 27) - 0.5F) * 0.7F;
        return true;
    }

    private final float[] d = new float[6];

    /** The squares whose drift would lie in or against one of the stage's pieces: no drift there. */
    private void block(RaceView r) {
        blocked.clear();
        float room = 340F;
        for (int i = 0; i < r.pieces; i++) {
            if (r.pieceXY[i] == 0 && r.pieceZY[i] == 0 && r.pieceY[i] == GROUND) {
                continue; // level with the ground: the bed lies over it
            }
            float x0 = r.pieceX[i] - r.pieceRadX[i] - room;
            float x1 = r.pieceX[i] + r.pieceRadX[i] + room;
            float z0 = r.pieceZ[i] - r.pieceRadZ[i] - room;
            float z1 = r.pieceZ[i] + r.pieceRadZ[i] + room;
            int ix1 = (int) Math.floor(x1 / CELL);
            int iz1 = (int) Math.floor(z1 / CELL);
            for (int ix = (int) Math.floor(x0 / CELL); ix <= ix1; ix++) {
                for (int iz = (int) Math.floor(z0 / CELL); iz <= iz1; iz++) {
                    if (drift(ix, iz, d) && d[0] > x0 && d[0] < x1 && d[1] > z0 && d[1] < z1) {
                        blocked.add(key(ix, iz));
                    }
                }
            }
        }
    }

    /** How much of its height the drift in square {@code key} has now: 0 just flattened, back to 1 as the snow builds it up. */
    private float grown(long key) {
        for (int n = flats - 1; n >= Math.max(0, flats - FLAT); n--) {
            if (flatKey[n % FLAT] == key) {
                return Math.min(1F, (tick - flatTick[n % FLAT]) / (float) REGROWS);
            }
        }
        return 1F;
    }

    /** How high a drift of {@code share} of the full height stands in a bed {@code deepNow} units deep. */
    static float driftHigh(float share, float deepNow) {
        return DRIFT_HIGH * share * (0.3F + 0.7F * (deepNow - THIN) / DEEPENS);
    }

    /** Car {@code c} drives on: the drift in its square, if it is on it, is flattened. */
    private void through(RaceView r, int c, Dice dice) {
        int ix = (int) Math.floor(r.x[c] / CELL);
        int iz = (int) Math.floor(r.z[c] / CELL);
        long key = key(ix, iz);
        if (!drift(ix, iz, d) || blocked.contains(key)) {
            return;
        }
        float turn = (float) Math.atan2(windZ, windX) + d[5];
        float ax = (float) Math.cos(turn);
        float az = (float) Math.sin(turn);
        float dx = r.x[c] - d[0];
        float dz = r.z[c] - d[1];
        float along = dx * ax + dz * az;
        float across = -dx * az + dz * ax;
        if (Math.abs(along) > d[2] + 70F || Math.abs(across) > d[3] + halfWide[c] + 30F) {
            return;
        }
        float had = grown(key);
        if (had < 0.25F) {
            return;
        }
        flatKey[flats % FLAT] = key;
        flatTick[flats % FLAT] = tick;
        flats++;
        flattened++;
        // it bursts: powder, and lumps thrown the way the car goes
        float high = driftHigh(d[4], deep) * had;
        float y = Snow.groundUnder(r, c);
        for (int k = 0; k < 6; k++) {
            float at = dice.between(-0.7F, 0.7F) * d[2];
            float x = d[0] + ax * at;
            float z = d[1] + az * at;
            snow.powder(x, y - high * 0.5F, z, r.vx[c] * 0.4F + dice.between(-4F, 4F), -dice.between(2F, 6F),
                    r.vz[c] * 0.4F + dice.between(-4F, 4F), 14F, 50F + high, 0.3F);
            if (k < 4 && high > 5F) {
                Weather.lump(r.x[c] + dice.between(-40F, 40F), y - high * 0.6F, r.z[c] + dice.between(-40F, 40F),
                        r.vx[c] * 0.6F + dice.between(-6F, 6F), -dice.between(4F, 10F),
                        r.vz[c] * 0.6F + dice.between(-6F, 6F), dice.between(8F, 15F));
            }
        }
        snow.sound(r.x[c], r.y[c], r.z[c], 0.2F + 0.012F * high);
    }

    /* ------------------------------------------------------------ drawing */

    /**
     * Queues the bed: the imprints and the spindrift as one ground item
     * ({@code groundItem}), the drifts by distance (from {@code driftBase}),
     * and about each car near the eye the snow at its wheels and nose (from
     * {@code wheelBase}, {@link #PER_CAR} a car): behind the car what is on
     * its far side, over it what is on the near side.
     */
    void queue(DrawQueue queue, int groundItem, int driftBase, int wheelBase) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        queue.look(look);
        drawnPrints = 0;
        drawnWalls = 0;
        drawnDrifts = 0;
        drawnFaces = 0;
        drawnWheels = 0;
        drawnNoses = 0;
        drawnStreamers = 0;
        queuedDrifts = 0;
        if (cars == 0) {
            return;
        }
        queue.ground(groundItem);
        float windTurn = (float) Math.atan2(windZ, windX);
        float slope = 0.72F * queue.aspect() * 1.25F;
        int ix1 = (int) Math.floor((eyeX + DRIFTS_SEEN) / CELL);
        int iz1 = (int) Math.floor((eyeZ + DRIFTS_SEEN) / CELL);
        for (int ix = (int) Math.floor((eyeX - DRIFTS_SEEN) / CELL); ix <= ix1 && queuedDrifts < DRIFTS; ix++) {
            for (int iz = (int) Math.floor((eyeZ - DRIFTS_SEEN) / CELL); iz <= iz1 && queuedDrifts < DRIFTS; iz++) {
                if (!drift(ix, iz, d)) {
                    continue;
                }
                float rx = d[0] - eyeX;
                float rz = d[1] - eyeZ;
                float far = rx * rx + rz * rz;
                float ahead = rx * look[0] + rz * look[2];
                if (far > DRIFTS_SEEN * DRIFTS_SEEN || ahead < -d[2]
                        || Math.abs(rx * look[3] + rz * look[5]) > Math.max(0F, ahead) * slope + d[2] + 100F) {
                    continue;
                }
                long key = key(ix, iz);
                if (blocked.contains(key) || snow.cleared(d[0], d[1])) {
                    continue;
                }
                float high = driftHigh(d[4], deep) * grown(key);
                if (high < 2.5F) {
                    continue;
                }
                int i = queuedDrifts++;
                dX[i] = d[0];
                dZ[i] = d[1];
                dAX[i] = (float) Math.cos(windTurn + d[5]);
                dAZ[i] = (float) Math.sin(windTurn + d[5]);
                dLong[i] = d[2];
                dWide[i] = d[3];
                dHigh[i] = high;
                // pulled a little towards the eye, so the road under it is drawn first
                queue.at(eyeX + rx * 0.97F, eyeY + (GROUND - eyeY) * 0.97F, eyeZ + rz * 0.97F, driftBase + i);
            }
        }
        for (int c = 0; c < cars; c++) {
            float rx = carX[c] - eyeX;
            float rz = carZ[c] - eyeZ;
            float far = (float) Math.sqrt(rx * rx + rz * rz);
            if (far > ABOUT_CARS || far < 1F) {
                continue;
            }
            // towards the eye from the car, level
            float ex = -rx / far;
            float ez = -rz / far;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                int at = c * RaceView.WHEELS + w;
                if (!wheelIn[at]) {
                    continue;
                }
                // a part of the car: among its polygons by how far it is, a tyre's width nearer than the
                // wheel's foot, so it banks up over its own tyre and the rest of the car covers it as it should
                float pull = wheelR[c] + 12F;
                queue.within(c, -1, wheelX[at] + ex * pull, wheelY[at] - 0.5F * wheelR[c], wheelZ[at] + ez * pull,
                        wheelBase + c * PER_CAR + w);
            }
            if (plough[c] > 1F) {
                float forwards = carVX[c] * headX[c] + carVZ[c] * headZ[c] >= 0F ? 1F : -1F;
                float reach = forwards > 0F ? noseZ[c] + 14F : tailZ[c] - 14F;
                queue.within(c, -1, carX[c] + headX[c] * reach, carY[c] - 8F, carZ[c] + headZ[c] * reach,
                        wheelBase + c * PER_CAR + NOSE);
            }
        }
    }

    private boolean near(float x, float z, float far) {
        float dx = x - eyeX;
        float dz = z - eyeZ;
        return dx * dx + dz * dz < far * far;
    }

    /** Draws the cars' shapes in the bed, and the spindrift over it. */
    void drawGround(Canvas canvas) {
        for (int i = 0; i < IMPRINTS; i++) {
            if (!printLive[i]) {
                continue;
            }
            int age = tick - printTick[i];
            if (age >= IMPRINT_FILLS) {
                printLive[i] = false;
                imprints--;
                continue;
            }
            int at = i * CORNERS;
            if (near(printX[at], printZ[at], Snow.SEEN)) {
                imprint(canvas, i, 1F - age / (float) IMPRINT_FILLS);
            }
        }
        spindrift(canvas);
        canvas.dither(1F);
    }

    /** Imprint {@code i}, {@code left} of it not yet snowed in: its floor, and near the eye a wall inside each edge. */
    private void imprint(Canvas canvas, int i, float left) {
        int at = i * CORNERS;
        int n = printN[i];
        float y = printY[i] - 0.35F;
        float midX = 0F;
        float midZ = 0F;
        for (int k = 0; k < n; k++) {
            cx[k] = printX[at + k];
            cy[k] = y;
            cz[k] = printZ[at + k];
            midX += cx[k] / n;
            midZ += cz[k] / n;
        }
        float cut = printCut[i] * (0.35F + 0.65F * left);
        float deepness = Math.min(1F, cut / 34F);
        canvas.color(canvas.fog(Air.mix(Snow.LIT, Snow.FLOOR, 0.45F + 0.55F * deepness), midX, y, midZ));
        canvas.dither(Math.min(1F, 0.8F * (0.25F + 0.75F * left)));
        canvas.world(cx, cy, cz, n);
        drawnPrints++;
        if (!near(midX, midZ, Snow.RELIEF)) {
            return;
        }
        float wall = Math.min(cut * 0.6F, 16F);
        canvas.dither(Math.min(1F, 0.3F + 0.7F * left));
        for (int k = 0; k < n; k++) {
            int next = (k + 1) % n;
            float x0 = printX[at + k];
            float z0 = printZ[at + k];
            float x1 = printX[at + next];
            float z1 = printZ[at + next];
            float ex = x1 - x0;
            float ez = z1 - z0;
            float len = (float) Math.sqrt(ex * ex + ez * ez);
            if (len < 2F) {
                continue;
            }
            // square to the edge, into the hollow
            float nx = -ez / len;
            float nz = ex / len;
            if (nx * (midX - x0) + nz * (midZ - z0) < 0F) {
                nx = -nx;
                nz = -nz;
            }
            float lit = 0.5F + 0.5F * (nx * Snow.LIGHT_X + nz * Snow.LIGHT_Z);
            canvas.color(canvas.fog(Air.mix(Snow.WALL, Snow.LIT, lit * lit), midX, y, midZ));
            mx[0] = x0;
            mz[0] = z0;
            mx[1] = x1;
            mz[1] = z1;
            mx[2] = x1 + nx * wall + (midX - x1) * 0.04F;
            mz[2] = z1 + nz * wall + (midZ - z1) * 0.04F;
            mx[3] = x0 + nx * wall + (midX - x0) * 0.04F;
            mz[3] = z0 + nz * wall + (midZ - z0) * 0.04F;
            my[0] = my[1] = my[2] = my[3] = y - 0.1F;
            canvas.world(mx, my, mz, 4);
            drawnWalls++;
        }
    }

    /**
     * Streamers of loose snow driven low over the bed with the wind: each
     * a thin winding strip that runs a few seconds and fades, where and
     * when from the tick alone; more show in a gust.
     */
    private void spindrift(Canvas canvas) {
        if (eyeY >= GROUND - 4F || gusts == null) {
            return;
        }
        float gust = gusts.at(tick);
        float blowing = 0.4F + 0.6F * Math.min(1F, gust * 2F);
        float midX = eyeX + look[0] * STREAM_MIDDLE;
        float midZ = eyeZ + look[2] * STREAM_MIDDLE;
        float slope = 0.72F * 16F / 9F * 1.6F;
        float y = GROUND - 0.8F;
        for (int s = 0; s < STREAMERS; s++) {
            int period = 50 + s % 37;
            int since = tick + s * 13;
            int age = since % period;
            int round = since / period;
            float pace = 22F + 16F * Fall.hash(s, round, 33);
            float x = midX + wrap(Fall.hash(s, round, 31) * STREAM_SIDE + windX * pace * age - mod(midX), STREAM_SIDE);
            float z = midZ + wrap(Fall.hash(s, round, 32) * STREAM_SIDE + windZ * pace * age - mod(midZ), STREAM_SIDE);
            float rx = x - eyeX;
            float rz = z - eyeZ;
            float far = rx * look[0] + (GROUND - eyeY) * look[1] + rz * look[2];
            if (far < 90F || far > STREAM_FAR || Math.abs(rx * look[3] + rz * look[5]) > far * slope) {
                continue;
            }
            float shows = (float) Math.sin(Math.PI * (age + 0.5F) / period) * blowing * (1F - far / STREAM_FAR);
            if (shows < 0.08F) {
                continue;
            }
            float length = 150F + 260F * Fall.hash(s, round, 34);
            float wide = (3.5F + 5F * Fall.hash(s, round, 35)) * (1F + far / 700F);
            canvas.color(canvas.fog(Snow.LIT, x, y, z));
            canvas.dither(0.3F * shows);
            // three stretches, winding a little from side to side
            float phase = s * 1.7F + tick * 0.21F;
            for (int k = 0; k < 3; k++) {
                float a0 = length * (k / 3F - 0.5F);
                float a1 = length * ((k + 1) / 3F - 0.5F);
                float s0 = (float) Math.sin(phase + k * 1.3F) * 16F;
                float s1 = (float) Math.sin(phase + (k + 1) * 1.3F) * 16F;
                float w0 = wide * (k == 0 ? 0.3F : 1F);
                float w1 = wide * (k == 2 ? 0.3F : 1F);
                streamer(0, x, z, a0, s0 - w0, y);
                streamer(1, x, z, a0, s0 + w0, y);
                streamer(2, x, z, a1, s1 + w1, y);
                streamer(3, x, z, a1, s1 - w1, y);
                canvas.moving(mx, my, mz, windX * pace, 0F, windZ * pace, 4);
            }
            drawnStreamers++;
        }
    }

    private void streamer(int k, float x, float z, float along, float across, float y) {
        mx[k] = x + windX * along - windZ * across;
        my[k] = y;
        mz[k] = z + windZ * along + windX * across;
    }

    private static float mod(float v) {
        float m = v % STREAM_SIDE;
        return m < 0F ? m + STREAM_SIDE : m;
    }

    private static float wrap(float v, float side) {
        float m = v % side;
        float half = side / 2F;
        return m < -half ? m + side : m >= half ? m - side : m;
    }

    /** Draws drift {@code i} of those queued for the frame. */
    void drawDrift(Canvas canvas, int i) {
        if (i < 0 || i >= queuedDrifts) {
            return;
        }
        ridge(canvas, dX[i], GROUND, dZ[i], dAX[i], dAZ[i], dLong[i], dWide[i], dHigh[i], 0F, 0F);
        drawnDrifts++;
    }

    /** Draws item {@code item} of a car's: the snow banked about one of its wheels, or the wave at its nose. */
    void drawAbout(Canvas canvas, int item) {
        int c = item / PER_CAR;
        int k = item % PER_CAR;
        if (c < 0 || c >= cars) {
            return;
        }
        float speed = (float) Math.sqrt(carVX[c] * carVX[c] + carVZ[c] * carVZ[c]);
        if (k < RaceView.WHEELS) {
            int at = c * RaceView.WHEELS + k;
            if (!wheelIn[at]) {
                return;
            }
            // banked about the tyre, a little ahead of it the way it goes: its foot stands in the bed
            float round = wheelR[c] * 0.7F + deep * 0.3F;
            float high = Math.min(deep * 0.85F, wheelR[c] * 0.75F);
            float lead = speed > 1F ? round * 0.3F * Math.min(1F, speed / 20F) / speed : 0F;
            float x = wheelX[at] + carVX[c] * lead;
            float z = wheelZ[at] + carVZ[c] * lead;
            int sides = snow.drawnSides;
            int pushed = snow.drawnPushed;
            snow.mound(canvas, -1, x, wheelY[at], z, round, round, high, eyeX - x, wheelY[at] - eyeY, eyeZ - z,
                    500 + at, carVX[c], 0F, carVZ[c]);
            // not one of the snow's own heaps: its counts stay theirs
            snow.drawnSides = sides;
            snow.drawnPushed = pushed;
            drawnWheels++;
        } else if (k == NOSE && plough[c] > 1F) {
            float forwards = carVX[c] * headX[c] + carVZ[c] * headZ[c] >= 0F ? 1F : -1F;
            float out = (forwards > 0F ? noseZ[c] : -tailZ[c]) + 8F;
            float x = carX[c] + headX[c] * forwards * out;
            float z = carZ[c] + headZ[c] * forwards * out;
            // across the nose: as wide as the car, higher the deeper it cuts
            ridge(canvas, x, carY[c], z, headZ[c], -headX[c], halfWide[c] * 1.05F, 14F + plough[c] * 0.9F,
                    Math.min(30F, plough[c] + 5F), carVX[c], carVZ[c]);
            drawnNoses++;
        }
    }

    /**
     * A ridge of snow: its middle at {@code (x, y, z)} on the ground, its
     * crest along {@code (ax, az)}, half its length {@code l} and width
     * {@code w}, {@code h} high; sides in three stretches to each side of the
     * crest (short ones: the game leaves out a polygon that reaches the
     * eye) and two ends running out to points. Only the faces turned to the eye are
     * drawn, each lit by the way it faces; it moves {@code (vx, 0, vz)} a
     * tick.
     */
    private void ridge(Canvas canvas, float x, float y, float z, float ax, float az, float l, float w, float h,
            float vx, float vz) {
        float nx = -az;
        float nz = ax;
        point(0, x, y, z, ax, az, nx, nz, -l, 0F, 0F);
        point(1, x, y, z, ax, az, nx, nz, l, 0F, 0F);
        // four stations along it: to each side its foot, between them its crest, highest off the middle
        for (int k = 0; k < STATIONS; k++) {
            point(2 + 3 * k, x, y, z, ax, az, nx, nz, ALONG[k] * l, WIDE[k] * w, 0F);
            point(3 + 3 * k, x, y, z, ax, az, nx, nz, ALONG[k] * l, -WIDE[k] * w, 0F);
            point(4 + 3 * k, x, y, z, ax, az, nx, nz, ALONG[k] * l, 0F, HIGH[k] * h);
        }
        for (int k = 0; k + 1 < STATIONS; k++) {
            face(canvas, 2 + 3 * k, 5 + 3 * k, 7 + 3 * k, 4 + 3 * k, vx, vz);
            face(canvas, 6 + 3 * k, 3 + 3 * k, 4 + 3 * k, 7 + 3 * k, vx, vz);
        }
        int last = 3 * (STATIONS - 1);
        face(canvas, 0, 2, 4, -1, vx, vz);
        face(canvas, 0, 4, 3, -1, vx, vz);
        face(canvas, 1, 4 + last, 2 + last, -1, vx, vz);
        face(canvas, 1, 3 + last, 4 + last, -1, vx, vz);
    }

    private static final int STATIONS = 4;
    private static final float[] ALONG = {-0.64F, -0.22F, 0.22F, 0.64F};
    private static final float[] WIDE = {0.8F, 1F, 1F, 0.76F};
    private static final float[] HIGH = {0.55F, 1F, 0.92F, 0.5F};

    private void point(int k, float x, float y, float z, float ax, float az, float nx, float nz, float along,
            float across, float up) {
        px[k] = x + ax * along + nx * across;
        py[k] = y - up;
        pz[k] = z + az * along + nz * across;
    }

    /** One face of a shape that bulges nowhere inwards, if it is turned to the eye: its corners {@code a, b, c} and {@code e} (-1 for three). */
    private void face(Canvas canvas, int a, int b, int c, int e, float vx, float vz) {
        float ux = px[b] - px[a];
        float uy = py[b] - py[a];
        float uz = pz[b] - pz[a];
        float wx = px[c] - px[a];
        float wy = py[c] - py[a];
        float wz = pz[c] - pz[a];
        float nx = uy * wz - uz * wy;
        float ny = uz * wx - ux * wz;
        float nz = ux * wy - uy * wx;
        if (ny > 0F) {
            // every face of these looks up (towards lesser y)
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        if ((eyeX - px[a]) * nx + (eyeY - py[a]) * ny + (eyeZ - pz[a]) * nz <= 0F) {
            return;
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) {
            return;
        }
        // the bed's own colour where it lies level; whiter turned to the light, in shade turned from it
        float bright = Math.max(0F, Math.min(1F, 0.6F + 1.5F * (nx * Snow.LIGHT_X + nz * Snow.LIGHT_Z) / len));
        int base = Air.mix(snow.groundRgb, Snow.LIT, 0.35F);
        int rgb = bright >= 0.6F ? Air.mix(base, 0xffffff, (bright - 0.6F) / 0.4F * 0.85F)
                : Air.mix(base, DEEP_SHADE, (0.6F - bright) / 0.6F * 0.75F);
        canvas.color(canvas.fog(rgb, px[a], py[a], pz[a]));
        canvas.dither(1F);
        int n = e < 0 ? 3 : 4;
        cx[0] = px[a];
        cy[0] = py[a];
        cz[0] = pz[a];
        cx[1] = px[b];
        cy[1] = py[b];
        cz[1] = pz[b];
        cx[2] = px[c];
        cy[2] = py[c];
        cz[2] = pz[c];
        if (e >= 0) {
            cx[3] = px[e];
            cy[3] = py[e];
            cz[3] = pz[e];
        }
        if (vx != 0F || vz != 0F) {
            canvas.moving(cx, cy, cz, vx, 0F, vz, n);
        } else {
            canvas.world(cx, cy, cz, n);
        }
        drawnFaces++;
    }
}
