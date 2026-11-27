package nfm.lit.features.weather;

import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;

/**
 * The rain or the snow falling: every drop and flake has a place in the
 * world, so driving, turning and jumping move you through it (it is not a
 * picture laid over the screen).
 * <p>
 * Nothing is kept from tick to tick. The drops are a pattern that repeats
 * through the world in three layers, fine and close, wider, and far and
 * coarse; where each drop is follows from the tick alone (its place in the
 * pattern, plus its fall and the wind's push for that many ticks), so a
 * race replayed rains the same. For a frame, the drops of each layer inside
 * the picture and that layer's stretch of distance are found, sorted into
 * the game's back-to-front order by distance, and drawn as thin streaks
 * along the way they move past the camera (so they slant back at speed), or
 * as small flakes. The rain also makes splashes on the flat ground.
 */
final class Fall {

    static final int LAYERS = 3;
    /** Nothing falls nearer the eye than this along the view: the game leaves out a polygon that reaches the eye. */
    static final float NEAR = 40F;
    /** Each layer's stretch of distance ends here (and the next begins). */
    static final float[] FAR = {350F, 950F, 2600F};
    /** Each layer's pattern: how wide it is (it repeats beyond), how high, and how far ahead of the eye its middle is kept. */
    private static final float[] SIDE = {1204F, 3270F, 8950F};
    private static final float[] HIGH = {600F, 1000F, 2200F};
    private static final float[] MIDDLE = {217F, 590F, 1610F};
    /** Drops in each layer's pattern; about one in sixteen is inside the picture at a time. */
    private static final int[] DROPS = {4350, 5400, 3900};
    /** The game's distance measure (the root of the distance) goes this high for the farthest drop. */
    static final int SHELLS = 64;
    /** The flat ground's height. */
    static final float GROUND = 250F;
    /** At most this many drops are drawn in a frame. */
    static final int SHOWN = 3000;

    /** How fast rain and snow fall, units a tick, and how many times the wind's speed they drift. */
    static final float RAIN_FALL = 46F;
    static final float SNOW_FALL = 6.5F;
    private static final float RAIN_DRIFT = 6F;
    private static final float SNOW_DRIFT = 2.5F;
    /** How far a flake sways to each side, units, in the nearest layer. */
    private static final float SWAY = 9F;
    /**
     * The gusts: at a squall's peak the rain drifts this many times farther
     * with the wind than in the calm, the snow this many, and what falls
     * comes down this much faster; in the calm this share of the drops
     * falls, at the peak all of them.
     */
    static final float RAIN_GUSTS = 3F;
    static final float SNOW_GUSTS = 5F;
    private static final float GUST_FALLS = 0.25F;
    static final float CALM = 0.7F;

    /** A flake is at most this long, units, in the nearest layer: what it is in the calm seen from a standing car. */
    static final float FLAKE_LONG = 3.2F;
    /** Driving thins the snowfall out: at this camera speed and above only {@link #SNOW_AT_SPEED} of the flakes are shown. */
    static final float THIN_FULL = 70F;
    static final float SNOW_AT_SPEED = 0.45F;

    /** Splash sites in the pattern, how wide it is, and how many ticks a splash lasts. */
    private static final int SITES = 600;
    private static final float SITE_SIDE = 2600F;
    private static final float SITE_MIDDLE = 760F;
    private static final float SITE_FAR = 1500F;
    static final int SPLASH_TICKS = 3;

    private final float[][] bx = new float[LAYERS][];
    private final float[][] by = new float[LAYERS][];
    private final float[][] bz = new float[LAYERS][];
    /** Each drop's own share of the fall's speed, and where in its sway a flake starts. */
    private final float[][] pace = new float[LAYERS][];
    private final float[][] phase = new float[LAYERS][];

    private boolean snow;
    private Gusts gusts;
    private float reach;
    /** How hard it gusts in the frame being drawn, 0 to 1. */
    float gust;
    /** How far what falls slants in that frame: its drift a tick over its fall a tick. */
    float slant;
    private float fall;
    private float driftX;
    private float driftZ;
    private int rgb;
    private int fogRgb;

    /* the drops shown in the frame being drawn: each one's low end, its move a tick, its streak back and its half width */
    private final float[] sx = new float[SHOWN];
    private final float[] sy = new float[SHOWN];
    private final float[] sz = new float[SHOWN];
    private final float[] svx = new float[SHOWN];
    private final float[] svy = new float[SHOWN];
    private final float[] svz = new float[SHOWN];
    private final float[] tx = new float[SHOWN];
    private final float[] ty = new float[SHOWN];
    private final float[] tz = new float[SHOWN];
    private final float[] half = new float[SHOWN];
    private final int[] next = new int[SHOWN];
    private final int[] first = new int[SHELLS];
    int shown;
    /** Of the nearest layer's pattern, how many drops fall in the frame being drawn. */
    int fallingNow;
    /** The longest drop or flake shown in the last frame, units. */
    float longest;
    /** The nearest a drop shown in the last frame is to the eye along the view, and how many were looked at. */
    float nearest;

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] look = new float[9];
    private int frameTick;

    private final float[] cx = new float[6];
    private final float[] cy = new float[6];
    private final float[] cz = new float[6];
    private final float[] mx = new float[6];
    private final float[] my = new float[6];
    private final float[] mz = new float[6];

    /** Rain (or snow, {@code snowing}) for the race started from {@code seed}, in wind {@code windX, windZ}. */
    void start(long seed, boolean snowing, float windX, float windZ, int skyRgb, int fog, Gusts blowing) {
        snow = snowing;
        gusts = blowing;
        reach = snowing ? SNOW_GUSTS : RAIN_GUSTS;
        fall = snowing ? SNOW_FALL : RAIN_FALL;
        driftX = windX * (snowing ? SNOW_DRIFT : RAIN_DRIFT);
        driftZ = windZ * (snowing ? SNOW_DRIFT : RAIN_DRIFT);
        fogRgb = fog;
        // rain catches the sky's light; snow is white, a little of the sky in it
        rgb = snowing ? Air.mix(0xffffff, skyRgb, 0.1F) : Air.mix(skyRgb, 0xffffff, 0.5F);
        Dice dice = new Dice(seed, "weather fall");
        for (int l = 0; l < LAYERS; l++) {
            int n = DROPS[l];
            if (bx[l] == null) {
                bx[l] = new float[n];
                by[l] = new float[n];
                bz[l] = new float[n];
                pace[l] = new float[n];
                phase[l] = new float[n];
            }
            for (int i = 0; i < n; i++) {
                bx[l][i] = dice.next() * SIDE[l];
                by[l][i] = dice.next() * HIGH[l];
                bz[l][i] = dice.next() * SIDE[l];
                pace[l][i] = dice.between(0.86F, 1.14F);
                phase[l][i] = dice.between(0F, 6.2832F);
            }
        }
    }

    /**
     * Finds what falls in the frame {@code queue} is being filled for,
     * {@code tick} real ticks into the race, the camera moving
     * {@code (camVX, camVY, camVZ)} a tick, and queues it: one item per
     * distance from {@code base} on.
     */
    void queue(DrawQueue queue, int tick, float camVX, float camVY, float camVZ, int base) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        queue.look(look);
        frameTick = tick;
        shown = 0;
        longest = 0F;
        nearest = Float.MAX_VALUE;
        java.util.Arrays.fill(first, -1);
        // the picture's edges as slopes, widened: between ticks the camera turns a little further
        float slopeX = 0.72F * queue.aspect() * 1.15F;
        float slopeY = 0.72F * 1.15F;
        for (int l = 0; l < LAYERS; l++) {
            layer(l, tick, camVX, camVY, camVZ, slopeX, slopeY);
        }
        for (int k = SHELLS - 1; k >= 0; k--) {
            if (first[k] >= 0) {
                float d = (k + 0.5F) * (k + 0.5F);
                queue.at(eyeX + look[0] * d, eyeY + look[1] * d, eyeZ + look[2] * d, base + k);
            }
        }
    }

    private void layer(int l, int tick, float camVX, float camVY, float camVZ, float slopeX, float slopeY) {
        float side = SIDE[l];
        float high = HIGH[l];
        float from = l == 0 ? NEAR : FAR[l - 1];
        float to = FAR[l];
        // the pattern is looked at around a point ahead of the eye, and from the ground up (or around a high eye)
        float midX = eyeX + look[0] * MIDDLE[l];
        float midZ = eyeZ + look[2] * MIDDLE[l];
        float top = Math.min(GROUND - high, eyeY - high * 0.6F);
        float[] x0 = bx[l];
        float[] y0 = by[l];
        float[] z0 = bz[l];
        float[] p = pace[l];
        // where the wind has carried the drops by now, and how fast it carries them: more in a gust
        gust = gusts.at(tick);
        double t = tick + reach * gusts.sum(tick);
        double fell = tick + GUST_FALLS * gusts.sum(tick);
        float blow = 1F + reach * gust;
        float falls = 1F + GUST_FALLS * gust;
        slant = (float) Math.sqrt(driftX * driftX + driftZ * driftZ) * blow / (fall * falls);
        // in the calm fewer fall: the last of the pattern only come down in a squall
        int falling = (int) (x0.length * (CALM + (1F - CALM) * Math.min(1F, gust * 1.6F)));
        if (snow) {
            // flakes rush at a moving camera: fewer of them the faster it goes, so the picture stays as calm as at rest
            float cam = (float) Math.sqrt(camVX * camVX + camVY * camVY + camVZ * camVZ);
            falling = (int) (falling * (1F - (1F - SNOW_AT_SPEED) * Math.min(1F, cam / THIN_FULL)));
        }
        fallingNow = falling;
        float fx = look[0];
        float fy = look[1];
        float fz = look[2];
        for (int i = 0; i < falling && shown < SHOWN; i++) {
            float vx = driftX * p[i];
            float vy = fall * p[i];
            float vz = driftZ * p[i];
            float x = midX + wrap((float) ((x0[i] + vx * t - midX) % side), side);
            float z = midZ + wrap((float) ((z0[i] + vz * t - midZ) % side), side);
            vx *= blow;
            vz *= blow;
            float rx = x - eyeX;
            float rz = z - eyeZ;
            // level distance first: most drops are out of the layer's stretch whatever their height
            float level = rx * fx + rz * fz;
            if (level < from - high || level > to + high) {
                continue;
            }
            float down = (float) ((y0[i] + vy * fell - top) % high);
            vy *= falls;
            float y = top + (down < 0F ? down + high : down);
            float ry = y - eyeY;
            float d = level + ry * fy;
            if (d < from || d >= to || y > GROUND) {
                continue;
            }
            float across = rx * look[3] + rz * look[5];
            if (Math.abs(across) > d * slopeX + 40F) {
                continue;
            }
            float below = rx * look[6] + ry * look[7] + rz * look[8];
            if (Math.abs(below) > d * slopeY + 60F) {
                continue;
            }
            if (snow) {
                // a flake sways about its line: where it is now, and its move from where it was a tick ago
                float sway = SWAY * (1F + l * 1.5F);
                float w = 0.11F + 0.05F * p[i];
                float a = (float) (tick * (double) w) + phase[l][i];
                float nowX = (float) Math.sin(a) * sway;
                float nowZ = (float) Math.cos(a * 0.83F) * sway;
                x += nowX;
                z += nowZ;
                vx += nowX - (float) Math.sin(a - w) * sway;
                vz += nowZ - (float) Math.cos((a - w) * 0.83F) * sway;
            }
            show(x, y, z, vx, vy, vz, d, p[i], camVX, camVY, camVZ);
        }
    }

    /** {@code v} (already within one {@code side} of 0 either way) into the half side either way of 0. */
    private static float wrap(float v, float side) {
        float halfSide = side / 2F;
        return v < -halfSide ? v + side : v >= halfSide ? v - side : v;
    }

    /** One drop in the picture, {@code d} from the eye along the view: its streak, and its place in the order. */
    private void show(float x, float y, float z, float vx, float vy, float vz, float d, float size, float camVX,
            float camVY, float camVZ) {
        int i = shown++;
        nearest = Math.min(nearest, d);
        sx[i] = x;
        sy[i] = y;
        sz[i] = z;
        svx[i] = vx;
        svy[i] = vy;
        svz[i] = vz;
        // the way it moves past the camera: its streak lies back along that
        float rx = vx - camVX;
        float ry = vy - camVY;
        float rz = vz - camVZ;
        float speed = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (speed < 1e-3F) {
            rx = 0F;
            ry = 1F;
            rz = 0F;
            speed = 1F;
        }
        float length;
        if (snow) {
            // a flake a few pixels across at any distance. It stays a flake however fast the camera passes
            // it: as long as it is standing still in the snowfall, never drawn out into a streak
            half[i] = (0.25F + 0.32F * (size - 0.86F) / 0.28F) * (1F + d / 190F);
            length = Math.max(2F * half[i], Math.min(FLAKE_LONG, speed * 0.45F) * (1F + d / 1400F));
        } else {
            half[i] = Math.max(0.22F, d * 0.0014F) * (1F + 0.5F * gust);
            length = Math.max(16F, Math.min(130F, speed * 0.8F)) * (1F + d / 1100F);
        }
        longest = Math.max(longest, length);
        tx[i] = -rx / speed * length;
        ty[i] = -ry / speed * length;
        tz[i] = -rz / speed * length;
        // the tail must not reach the eye either
        float tail = d + tx[i] * look[0] + ty[i] * look[1] + tz[i] * look[2];
        if (tail < NEAR) {
            float keep = Math.max(0F, (d - NEAR) / Math.max(1e-3F, d - tail));
            tx[i] *= keep;
            ty[i] *= keep;
            tz[i] *= keep;
        }
        int shell = Math.min(SHELLS - 1, (int) Math.sqrt(distance(x, y, z)));
        next[i] = first[shell];
        first[shell] = i;
    }

    private float distance(float x, float y, float z) {
        float dx = x - eyeX;
        float dy = y - eyeY;
        float dz = z - eyeZ;
        return (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Draws the drops at distance {@code shell}. */
    void draw(Canvas canvas, int shell) {
        if (shell < 0 || shell >= SHELLS || first[shell] < 0) {
            return;
        }
        // farther off they sink into the fog and thin out
        float far = Math.min(1F, shell * shell / FAR[LAYERS - 1]);
        canvas.color(Air.mix(rgb, fogRgb, 0.75F * far));
        canvas.dither(snow ? 0.95F - 0.4F * far : 0.55F + 0.15F * gust - 0.3F * far);
        for (int i = first[shell]; i >= 0; i = next[i]) {
            streak(i);
            canvas.moving(cx, cy, cz, svx[i], svy[i], svz[i], 4);
        }
    }

    /** Drop {@code i}'s four corners: from its low end back along its streak, facing the eye. */
    private void streak(int i) {
        float lx = tx[i];
        float ly = ty[i];
        float lz = tz[i];
        float ex = sx[i] - eyeX;
        float ey = sy[i] - eyeY;
        float ez = sz[i] - eyeZ;
        float ax = ly * ez - lz * ey;
        float ay = lz * ex - lx * ez;
        float az = lx * ey - ly * ex;
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-4F) {
            // seen end on: any way across will do
            ax = look[3];
            ay = look[4];
            az = look[5];
            len = 1F;
        }
        float w = half[i] / len;
        ax *= w;
        ay *= w;
        az *= w;
        if (snow) {
            // a flake: widest at its middle, a point at each end
            float hx = sx[i] + lx / 2F;
            float hy = sy[i] + ly / 2F;
            float hz = sz[i] + lz / 2F;
            cx[0] = sx[i];
            cy[0] = sy[i];
            cz[0] = sz[i];
            cx[1] = hx + ax;
            cy[1] = hy + ay;
            cz[1] = hz + az;
            cx[2] = sx[i] + lx;
            cy[2] = sy[i] + ly;
            cz[2] = sz[i] + lz;
            cx[3] = hx - ax;
            cy[3] = hy - ay;
            cz[3] = hz - az;
            return;
        }
        // a streak tapers to its tail
        float taper = 0.35F;
        cx[0] = sx[i] - ax;
        cy[0] = sy[i] - ay;
        cz[0] = sz[i] - az;
        cx[1] = sx[i] + ax;
        cy[1] = sy[i] + ay;
        cz[1] = sz[i] + az;
        cx[2] = sx[i] + lx + ax * taper;
        cy[2] = sy[i] + ly + ay * taper;
        cz[2] = sz[i] + lz + az * taper;
        cx[3] = sx[i] + lx - ax * taper;
        cy[3] = sy[i] + ly - ay * taper;
        cz[3] = sz[i] + lz - az * taper;
    }

    /* ---------------------------------------------------------- splashes */

    /** Splashes drawn by the last {@link #splashes}. */
    int splashed;

    /**
     * Draws the rain's splashes on the flat ground in front of the eye:
     * each a ring that spreads and fades over {@link #SPLASH_TICKS} ticks,
     * with a little crown standing in its first. A ground item: everything
     * that stands on the ground covers them.
     */
    void splashes(Canvas canvas) {
        splashed = 0;
        if (snow || eyeY >= GROUND - 4F) {
            return;
        }
        float midX = eyeX + look[0] * SITE_MIDDLE;
        float midZ = eyeZ + look[2] * SITE_MIDDLE;
        float slope = 0.72F * 16F / 9F * 1.6F;
        for (int s = 0; s < SITES; s++) {
            int period = 5 + s % 8;
            int since = frameTick + s * 7;
            int age = since % period;
            if (age >= SPLASH_TICKS) {
                continue;
            }
            // a new place every time the site comes round
            int round = since / period;
            float x = midX + wrap(hash(s, round, 1) * SITE_SIDE - mod(midX, SITE_SIDE), SITE_SIDE);
            float z = midZ + wrap(hash(s, round, 2) * SITE_SIDE - mod(midZ, SITE_SIDE), SITE_SIDE);
            float rx = x - eyeX;
            float rz = z - eyeZ;
            float d = rx * look[0] + (GROUND - eyeY) * look[1] + rz * look[2];
            if (d < NEAR + 30F || d > SITE_FAR || Math.abs(rx * look[3] + rz * look[5]) > d * slope) {
                continue;
            }
            splash(canvas, x, z, age, d, 0.7F + 0.6F * hash(s, round, 3));
            splashed++;
        }
    }

    private void splash(Canvas canvas, float x, float z, int age, float d, float size) {
        // farther off a splash is drawn larger, or it would be less than a pixel
        float scale = size * (1F + d / 500F);
        float grows = 2.2F * scale;
        float r = 1.2F * scale + grows * age;
        float y = GROUND - 0.5F;
        float fade = 1F - age / (float) SPLASH_TICKS;
        canvas.color(Air.mix(rgb, fogRgb, 0.5F * Math.min(1F, d / SITE_FAR)));
        canvas.dither(0.5F * fade);
        for (int k = 0; k < 6; k++) {
            float a = k * 1.0472F;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            cx[k] = x + c * r;
            cy[k] = y;
            cz[k] = z + s * r;
            mx[k] = c * grows;
            my[k] = 0F;
            mz[k] = s * grows;
        }
        canvas.moving(cx, cy, cz, mx, my, mz, 6);
        if (age == 0) {
            // the crown: a little spike of water standing where the drop hit, facing the eye, falling back
            float w = 1.1F * scale;
            float h = 6F * scale;
            canvas.dither(0.7F);
            cx[0] = x - look[3] * w;
            cy[0] = y;
            cz[0] = z - look[5] * w;
            cx[1] = x + look[3] * w;
            cy[1] = y;
            cz[1] = z + look[5] * w;
            cx[2] = x;
            cy[2] = y - h;
            cz[2] = z;
            mx[0] = mx[1] = mx[2] = 0F;
            mz[0] = mz[1] = mz[2] = 0F;
            my[0] = my[1] = 0F;
            my[2] = h * 0.8F;
            canvas.moving(cx, cy, cz, mx, my, mz, 3);
        }
    }

    private static float mod(float v, float side) {
        float m = v % side;
        return m < 0F ? m + side : m;
    }

    /** A number from 0 to 1 from three whole numbers, the same every time. */
    static float hash(int a, int b, int c) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77 + c * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }
}
