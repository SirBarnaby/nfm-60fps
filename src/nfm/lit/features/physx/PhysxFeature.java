package nfm.lit.features.physx;

import fallk.logmaster.HLogger;
import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.RaceView;
import nfm.lit.features.Weather;

/**
 * PhysX (physx, "PhysX to try out", "PhysX is only visual"): loose pieces
 * in the race that NVIDIA PhysX moves. A hard hit knocks panels off a car
 * in its colours, a wasted car and every explosion throw chunks, and in
 * snow the lumps the cars break out of the bed tumble as blocks. They fall,
 * bounce off the ground, the stage's walls, floors and ramps, come to rest,
 * and a car that drives into them shoves them away.
 * <p>
 * Only a look: it reads the race's copy after real ticks and never writes
 * the race, so a race plays the same with it on. What the pieces do need
 * not come out the same twice ("its results of identicalness are not 100%
 * important"). Where PhysX's library is missing or will not load, the
 * extra says so once in the log and does nothing.
 */
public final class PhysxFeature implements Feature {

    public static final String ID = "physx";
    /** The one instance, in the extras' list. */
    public static final PhysxFeature FEATURE = new PhysxFeature();

    /** A hit this hard knocks a panel off, and one more for every this much harder; at most this many a hit. */
    static final float HIT_FROM = 60F;
    static final float HIT_MORE = 45F;
    static final int HIT_MOST = 5;
    /** Chunks a wasted car throws, and an explosion. */
    static final int WRECK_CHUNKS = 9;
    static final int BLAST_CHUNKS = 12;
    /** Ticks a piece lies before it goes, a lump of snow, and the last ticks over which either thins out. */
    static final int LIES = 19 * 28;
    static final int SNOW_LIES = 19 * 14;
    static final int FADES = 28;
    /** Farther than this from the eye a piece is not drawn. */
    static final float SEEN = 7000F;
    private static final int BURNT = 0x2b2a28;
    private static final int SNOW = 0xf2f5fa;
    private static final int BODIES = PhysWorld.BODIES;

    /** Why PhysX is off for good in this run, or null. */
    private static String unavailable;

    private PhysWorld world;
    private boolean show;
    private final Dice dice = new Dice(0L, "physx");
    private int tick;
    private int next;
    /** Loose pieces lying or flying now. */
    int count;
    /** Since the race started: pieces thrown by hits, by wrecks and blasts, and lumps of snow. */
    int panels;
    int chunks;
    int snowLumps;
    private final boolean[] live = new boolean[BODIES];
    private final int[] born = new int[BODIES];
    private final int[] lasts = new int[BODIES];
    private final int[] rgb = new int[BODIES];
    private final float[] halfX = new float[BODIES];
    private final float[] halfY = new float[BODIES];
    private final float[] halfZ = new float[BODIES];
    /** Each piece's place and turn after the last tick, and after the one before. */
    private final float[] now = new float[BODIES * 7];
    private final float[] was = new float[BODIES * 7];
    private boolean[] wasted = new boolean[0];
    private int[] bodyRgb = new int[0];
    private int[] trimRgb = new int[0];
    private int seenBlasts;
    private int seenLumps;

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] out = new float[7];
    private final float[] p = new float[3];
    private final float[] cornerX = new float[8];
    private final float[] cornerY = new float[8];
    private final float[] cornerZ = new float[8];
    private final float[] moveX = new float[8];
    private final float[] moveY = new float[8];
    private final float[] moveZ = new float[8];
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];
    private final float[] mx = new float[4];
    private final float[] my = new float[4];
    private final float[] mz = new float[4];
    /** Faces drawn in the last frame. */
    int drawnFaces;

    private PhysxFeature() {
    }

    /** A PhysX extra of its own, for the checks. */
    static PhysxFeature forCheck() {
        return new PhysxFeature();
    }

    @Override
    public String id() {
        return ID;
    }

    /** True while a PhysX scene stands for the race. */
    boolean running() {
        return world != null;
    }

    /** Why PhysX could not be started in this run, or null if it could (or was never asked). */
    static String unavailable() {
        return unavailable;
    }

    PhysWorld world() {
        return world;
    }

    @Override
    public void raceStarted(RaceView race) {
        close();
        tick = race.tick;
        next = 0;
        count = 0;
        panels = 0;
        chunks = 0;
        snowLumps = 0;
        java.util.Arrays.fill(live, false);
        dice.reset(race.seed, "physx");
        show = Boolean.getBoolean("nfm.physx.show");
        seenBlasts = Weather.blasts();
        seenLumps = Weather.lumps();
        if (wasted.length < race.cars) {
            wasted = new boolean[race.cars];
            bodyRgb = new int[race.cars];
            trimRgb = new int[race.cars];
        }
        for (int c = 0; c < race.cars; c++) {
            wasted[c] = race.wasted[c];
            colours(race, c);
        }
        if (unavailable != null) {
            return;
        }
        try {
            world = new PhysWorld(race);
            HLogger.info("[extras] physx: PhysX " + PhysWorld.version() + " runs the loose pieces: " + race.cars
                    + " cars and " + world.pieces + " of the stage's " + race.pieces + " pieces stand in its scene");
        } catch (Throwable e) { // the library or its natives missing: the game goes on without
            unavailable = String.valueOf(e);
            world = null;
            HLogger.info("[extras] physx: PhysX could not be started, so this extra does nothing: " + e);
        }
    }

    /** Car {@code c}'s two colours: the one most of its panels have, and the next. */
    private void colours(RaceView race, int c) {
        bodyRgb[c] = 0x8a8f96;
        trimRgb[c] = 0x4a4d52;
        var s = race.shape[c];
        if (s == null) {
            return;
        }
        int best = 0;
        int second = 0;
        for (int i = 0; i < s.planes && i < 60; i++) {
            if (s.glass[i] || s.wheel[i] >= 0 || s.light[i] != 0) {
                continue;
            }
            int same = 0;
            for (int j = 0; j < s.planes && j < 60; j++) {
                same += s.rgb[j] == s.rgb[i] && !s.glass[j] && s.wheel[j] < 0 ? 1 : 0;
            }
            if (same > best) {
                if (bodyRgb[c] != s.rgb[i]) {
                    second = best;
                    trimRgb[c] = bodyRgb[c];
                }
                best = same;
                bodyRgb[c] = s.rgb[i];
            } else if (same > second && s.rgb[i] != bodyRgb[c]) {
                second = same;
                trimRgb[c] = s.rgb[i];
            }
        }
    }

    @Override
    public void afterTick(RaceView race) {
        if (world == null) {
            return;
        }
        tick = race.tick;
        try {
            hits(race);
            if (show && tick % 38 == 5 && race.cars > 0) {
                shown(race);
            }
            wrecks(race);
            blasts(race);
            lumps();
            for (int i = 0; i < BODIES; i++) {
                if (live[i] && tick - born[i] >= lasts[i]) {
                    world.drop(i);
                    live[i] = false;
                    count--;
                }
            }
            System.arraycopy(now, 0, was, 0, now.length);
            world.step(race);
            for (int i = 0; i < BODIES; i++) {
                if (live[i] && world.pose(i, out)) {
                    System.arraycopy(out, 0, now, i * 7, 7);
                }
            }
        } catch (Throwable e) {
            unavailable = String.valueOf(e);
            HLogger.info("[extras] physx: PhysX stopped, so this extra does nothing more: " + e);
            world = null;
        }
    }

    /** Each hard hit knocks panels off the corner hit, in the car's colours. */
    private void hits(RaceView race) {
        for (int h = 0; h < race.hits; h++) {
            int c = race.hitCar[h];
            float force = Math.abs(race.hitForce[h]);
            if (c < 0 || c >= race.cars || c >= bodyRgb.length || force < HIT_FROM) {
                continue;
            }
            int n = Math.min(HIT_MOST, 1 + (int) ((force - HIT_FROM) / HIT_MORE));
            int w = Math.max(0, Math.min(RaceView.WHEELS - 1, race.hitWheel[h]));
            int at = c * RaceView.WHEELS + w;
            race.toWorld(c, race.keyX[at], -18F, race.keyZ[at], p);
            float ox = p[0] - race.x[c];
            float oz = p[2] - race.z[c];
            float o = (float) Math.sqrt(ox * ox + oz * oz) + 1F;
            for (int k = 0; k < n; k++) {
                float out = dice.between(3F, 9F);
                // from just outside the corner: PhysX has the car as a box, and a piece born in it would end on its roof
                float clear = dice.between(38F, 52F);
                piece(p[0] + ox / o * clear + dice.between(-6F, 6F), p[1] - dice.between(0F, 20F),
                        p[2] + oz / o * clear + dice.between(-6F, 6F),
                        dice.between(11F, 30F), dice.between(1.6F, 3.2F), dice.between(8F, 20F),
                        race.vx[c] * 0.7F + ox / o * out + dice.between(-2F, 2F), -dice.between(4F, 12F),
                        race.vz[c] * 0.7F + oz / o * out + dice.between(-2F, 2F),
                        dice.next() < 0.7F ? bodyRgb[c] : trimRgb[c], LIES, false);
                panels++;
            }
        }
    }

    /** For a screenshot ({@code -Dnfm.physx.show=true}): panels and chunks thrown up ahead of the player's car. */
    private void shown(RaceView race) {
        race.toWorld(0, 0F, -30F, 420F, p);
        float x = p[0];
        float y = p[1];
        float z = p[2];
        for (int k = 0; k < 7; k++) {
            boolean panel = k < 4;
            piece(x + dice.between(-90F, 90F), y - dice.between(0F, 40F), z + dice.between(-60F, 60F),
                    dice.between(11F, panel ? 30F : 17F), panel ? dice.between(1.6F, 3.2F) : dice.between(8F, 15F),
                    dice.between(8F, 19F), race.vx[0] * 0.8F + dice.between(-3F, 3F), -dice.between(5F, 14F),
                    race.vz[0] * 0.8F + dice.between(-3F, 3F),
                    k == 5 ? BURNT : k % 2 == 0 ? bodyRgb[0] : trimRgb[0], LIES, false);
        }
    }

    /** A car wasted this tick throws chunks of itself, some burnt. */
    private void wrecks(RaceView race) {
        for (int c = 0; c < race.cars && c < wasted.length; c++) {
            if (race.wasted[c] && !wasted[c]) {
                CarShape s = race.shape[c];
                boolean sized = s != null && s.maxX > s.minX && s.maxZ > s.minZ;
                float wide = sized ? (s.maxX - s.minX) / 2F : 40F;
                float len = sized ? (s.maxZ - s.minZ) / 2F : 80F;
                for (int k = 0; k < WRECK_CHUNKS; k++) {
                    double way = dice.between(0F, 6.2832F);
                    float out = dice.between(3F, 11F);
                    boolean panel = dice.next() < 0.5F;
                    // from all round the car, outside the box PhysX has it as
                    race.toWorld(c, (float) Math.cos(way) * (wide * 1.42F + 22F), -dice.between(10F, 40F),
                            (float) Math.sin(way) * (len * 1.42F + 22F), p);
                    way = Math.atan2(p[2] - race.z[c], p[0] - race.x[c]);
                    piece(p[0], p[1], p[2], dice.between(10F, panel ? 32F : 17F),
                            panel ? dice.between(1.6F, 3.2F) : dice.between(8F, 15F), dice.between(8F, 19F),
                            race.vx[c] * 0.6F + (float) Math.cos(way) * out, -dice.between(6F, 16F),
                            race.vz[c] * 0.6F + (float) Math.sin(way) * out,
                            dice.next() < 0.4F ? BURNT : dice.next() < 0.7F ? bodyRgb[c] : trimRgb[c], LIES, false);
                    chunks++;
                }
            }
            wasted[c] = race.wasted[c];
        }
    }

    /** Each explosion since the last tick throws burnt chunks all round. */
    private void blasts(RaceView race) {
        int all = Weather.blasts();
        if (all < seenBlasts) {
            seenBlasts = all;
        }
        for (int n = Math.max(seenBlasts, all - Weather.BLASTS); n < all; n++) {
            int slot = n % Weather.BLASTS;
            float size = Math.max(0.5F, Weather.blastSize(slot));
            for (int k = 0; k < BLAST_CHUNKS; k++) {
                double way = 2D * Math.PI * (k + dice.next()) / BLAST_CHUNKS;
                float out = dice.between(6F, 18F) * size;
                piece(Weather.blastX(slot) + (float) Math.cos(way) * 30F,
                        Weather.blastGround(slot) - dice.between(8F, 30F),
                        Weather.blastZ(slot) + (float) Math.sin(way) * 30F, dice.between(8F, 17F),
                        dice.between(6F, 13F), dice.between(8F, 17F), (float) Math.cos(way) * out,
                        -dice.between(8F, 22F) * size, (float) Math.sin(way) * out,
                        dice.next() < 0.5F ? BURNT : race.groundColor, LIES, false);
                chunks++;
            }
        }
        seenBlasts = all;
    }

    /** The lumps the cars broke out of the snow since the last tick tumble as blocks. */
    private void lumps() {
        int all = Weather.lumps();
        if (all < seenLumps) {
            seenLumps = all;
        }
        for (int n = Math.max(seenLumps, all - Weather.LUMPS); n < all; n++) {
            int slot = n % Weather.LUMPS;
            float size = Weather.lump(slot, 6) / 2F;
            piece(Weather.lump(slot, 0), Weather.lump(slot, 1), Weather.lump(slot, 2), size,
                    size * dice.between(0.6F, 0.9F), size * dice.between(0.7F, 1F), Weather.lump(slot, 3),
                    Weather.lump(slot, 4), Weather.lump(slot, 5), SNOW, SNOW_LIES, true);
            snowLumps++;
        }
        seenLumps = all;
    }

    /** A new loose piece; the oldest makes way when all are out. */
    void piece(float x, float y, float z, float hx, float hy, float hz, float vx, float vy, float vz, int color,
            int ticks, boolean snow) {
        int i = next;
        next = (next + 1) % BODIES;
        world.spawn(i, x, y, z, hx, hy, hz, vx, vy, vz, dice.between(-0.4F, 0.4F), dice.between(-0.4F, 0.4F),
                dice.between(-0.4F, 0.4F), snow);
        if (!live[i]) {
            count++;
        }
        live[i] = true;
        born[i] = tick;
        lasts[i] = ticks;
        rgb[i] = color;
        halfX[i] = hx;
        halfY[i] = hy;
        halfZ[i] = hz;
        world.pose(i, out);
        System.arraycopy(out, 0, now, i * 7, 7);
        // it was where it came from a tick ago: no streak from elsewhere
        System.arraycopy(out, 0, was, i * 7, 7);
        was[i * 7] -= vx;
        was[i * 7 + 1] -= vy;
        was[i * 7 + 2] -= vz;
    }

    /** Loose piece {@code n} of those live (in no order): its place into {@code out[0..2]}, true if it rests; null past the last. */
    Boolean pieceAt(int n, float[] out) {
        for (int i = 0; i < BODIES; i++) {
            if (live[i] && n-- == 0) {
                out[0] = now[i * 7];
                out[1] = now[i * 7 + 1];
                out[2] = now[i * 7 + 2];
                out[3] = Math.max(halfX[i], Math.max(halfY[i], halfZ[i]));
                out[4] = Math.min(halfX[i], Math.min(halfY[i], halfZ[i]));
                out[5] = Math.abs(now[i * 7 + 6]);
                return world != null && world.resting(i);
            }
        }
        return null;
    }

    @Override
    public void raceEnded(RaceView race) {
        close();
    }

    private void close() {
        if (world != null) {
            try {
                world.close();
            } catch (Throwable e) {
                unavailable = String.valueOf(e);
            }
            world = null;
        }
        java.util.Arrays.fill(live, false);
        count = 0;
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        drawnFaces = 0;
        if (world == null || count == 0) {
            return;
        }
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        for (int i = 0; i < BODIES; i++) {
            if (!live[i]) {
                continue;
            }
            float dx = now[i * 7] - eyeX;
            float dz = now[i * 7 + 2] - eyeZ;
            if (dx * dx + dz * dz < SEEN * SEEN) {
                queue.near(now[i * 7], now[i * 7 + 1], now[i * 7 + 2], i); // by a car: a part of it, not through it
            }
        }
    }

    private static final int[][] FACES = {
        {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3},
    };
    /** Each face's outward side, in the box's own numbers. */
    private static final float[][] OUT = {
        {0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0},
    };

    @Override
    public void draw(Canvas canvas, int i) {
        if (i < 0 || i >= BODIES || !live[i]) {
            return;
        }
        corners(i, now, cornerX, cornerY, cornerZ);
        corners(i, was, moveX, moveY, moveZ);
        for (int k = 0; k < 8; k++) {
            moveX[k] = cornerX[k] - moveX[k];
            moveY[k] = cornerY[k] - moveY[k];
            moveZ[k] = cornerZ[k] - moveZ[k];
        }
        int left = lasts[i] - (tick - born[i]);
        canvas.dither(left >= FADES ? 1F : Math.max(0.05F, left / (float) FADES));
        int at = i * 7;
        for (int f = 0; f < 6; f++) {
            // the face's outward side in the world, and whether it is turned to the eye
            turn(now, at, OUT[f][0], OUT[f][1], OUT[f][2], p);
            int first = FACES[f][0];
            if ((eyeX - cornerX[first]) * p[0] + (eyeY - cornerY[first]) * p[1] + (eyeZ - cornerZ[first]) * p[2]
                    <= 0F) {
                continue;
            }
            // lit from above and from one side, as the snow's heaps are
            float lit = Math.max(0F, Math.min(1F, 0.55F - 0.35F * p[1] + 0.25F * (p[0] * 0.6F - p[2] * 0.8F)));
            canvas.color(canvas.fog(shade(rgb[i], 0.5F + 0.5F * lit), now[at], now[at + 1], now[at + 2]));
            for (int k = 0; k < 4; k++) {
                int c = FACES[f][k];
                cx[k] = cornerX[c];
                cy[k] = cornerY[c];
                cz[k] = cornerZ[c];
                mx[k] = moveX[c];
                my[k] = moveY[c];
                mz[k] = moveZ[c];
            }
            canvas.moving(cx, cy, cz, mx, my, mz, 4);
            drawnFaces++;
        }
        canvas.dither(1F);
    }

    private static int shade(int rgb, float share) {
        int r = (int) ((rgb >> 16 & 0xff) * share);
        int g = (int) ((rgb >> 8 & 0xff) * share);
        int b = (int) ((rgb & 0xff) * share);
        return r << 16 | g << 8 | b;
    }

    /** Piece {@code i}'s eight corners in the world, by the poses in {@code poses}. */
    private void corners(int i, float[] poses, float[] x, float[] y, float[] z) {
        int at = i * 7;
        for (int k = 0; k < 8; k++) {
            turn(poses, at, (k & 4) == 0 ? -halfX[i] : halfX[i], (k & 2) == 0 ? -halfY[i] : halfY[i],
                    (k & 1) == 0 ? -halfZ[i] : halfZ[i], p);
            x[k] = poses[at] + p[0];
            y[k] = poses[at + 1] + p[1];
            z[k] = poses[at + 2] + p[2];
        }
    }

    /** {@code (x, y, z)} turned by the turn at {@code poses[at + 3..at + 6]}, into {@code out}. */
    private static void turn(float[] poses, int at, float x, float y, float z, float[] out) {
        float qx = poses[at + 3];
        float qy = poses[at + 4];
        float qz = poses[at + 5];
        float qw = poses[at + 6];
        // v + 2 q x (q x v + w v)
        float tx = qy * z - qz * y + qw * x;
        float ty = qz * x - qx * z + qw * y;
        float tz = qx * y - qy * x + qw * z;
        out[0] = x + 2F * (qy * tz - qz * ty);
        out[1] = y + 2F * (qz * tx - qx * tz);
        out[2] = z + 2F * (qx * ty - qy * tx);
    }
}
