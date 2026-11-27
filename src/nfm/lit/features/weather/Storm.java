package nfm.lit.features.weather;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Features;
import nfm.lit.features.RaceView;

/**
 * The rain's thunderstorm (weather, "Rain and snow have more oomph"):
 * every now and then lightning strikes far off. The bolt stands in the
 * world for a few ticks, flickering, the whole picture pales with it for
 * a moment (unless the flash switch is off), and its thunder, a recording,
 * rolls in a second or so later from where it struck.
 * <p>
 * When and where it strikes follows from the race's seed, so a race
 * replayed storms the same. Only a look and a sound.
 */
final class Storm {

    static final int STRIKES = 96;
    /** Ticks a bolt is seen: there, there, gone, back, fading. */
    static final int SHOWS = 5;
    /** How far from the player it strikes, units. */
    static final float NEAREST = 5200F;
    static final float FARTHEST = 13000F;
    /** The thunder takes a tick for every this many units. */
    static final float THUNDER_PACE = 420F;
    private static final int JOINTS = 9;
    private static final float GROUND = 250F;
    private static final int CORE = 0xffffff;
    private static final int HALO = 0xc9c4ff;
    private static final int WASH = 0xeef0ff;

    private final int[] strikeTick = new int[STRIKES];
    private final float[] bearing = new float[STRIKES];
    private final float[] far = new float[STRIKES];
    private int strikes;
    private boolean show;

    /* the bolt standing now: where, and its joints from the cloud down, the branch's after them */
    private int current = -1;
    private float boltX;
    private float boltZ;
    private float boltFar;
    private final float[] jx = new float[JOINTS * 2];
    private final float[] jy = new float[JOINTS * 2];
    private final float[] jz = new float[JOINTS * 2];
    private int branchFrom;
    private int branchJoints;
    private int thunderAt = -1;
    private float thunderGain;
    private int tick;

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] look = new float[9];
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];
    private final float[] sx = new float[4];
    private final float[] sy = new float[4];

    /** Since the race started: bolts that struck, thunder that rolled. */
    int struck;
    int thundered;
    /** Drawn in the last frame: the bolt's pieces, and the picture's wash (0 or 1). */
    int drawnBolt;
    int drawnWash;

    /** The storm of the race started from {@code seed}; {@code any} false for none (snow). */
    void start(long seed, boolean any, int fromTick) {
        strikes = 0;
        current = -1;
        thunderAt = -1;
        struck = 0;
        thundered = 0;
        tick = fromTick;
        show = Boolean.getBoolean("nfm.weather.show");
        if (!any) {
            return;
        }
        Dice dice = new Dice(seed, "weather storm");
        float at = show ? 19F : 19F * dice.between(6F, 22F);
        while (strikes < STRIKES) {
            strikeTick[strikes] = (int) at;
            bearing[strikes] = dice.between(0F, 6.2832F);
            far[strikes] = dice.between(NEAREST, FARTHEST);
            strikes++;
            at += show ? 19F * 3F : 19F * dice.between(13F, 40F);
        }
    }

    /** One tick: a bolt strikes when its time has come, {@code ahead} of player 0 when shown for a screenshot; thunder follows. */
    void tick(RaceView r) {
        tick = r.tick;
        for (int s = 0; s < strikes; s++) {
            if (strikeTick[s] == tick) {
                strike(r, s);
            }
        }
        if (thunderAt >= 0 && tick >= thunderAt) {
            thunderAt = -1;
            thundered++;
            if (Features.hooks().sound3d()) {
                // from the way it struck, near enough to be heard as loud as it was far
                float dx = boltX - r.x[0];
                float dz = boltZ - r.z[0];
                float d = (float) Math.sqrt(dx * dx + dz * dz) + 1F;
                Sound3D.pointSound(r.x[0] + dx / d * 500F, r.y[0] - 250F, r.z[0] + dz / d * 500F,
                        WeatherSounds.THUNDER, thunderGain, 0.82F + 0.3F * Fall.hash(tick, 7, 1));
            }
        }
    }

    private void strike(RaceView r, int s) {
        current = s;
        struck++;
        float way = bearing[s];
        if (show) {
            // for a screenshot: ahead of the player's car, a little to one side
            way = (float) Math.toRadians(-r.xz[0]) + (s % 2 == 0 ? 0.25F : -0.3F);
        }
        boltFar = far[s];
        float dirX = (float) Math.sin(way);
        float dirZ = (float) Math.cos(way);
        boltX = r.x[0] + dirX * boltFar;
        boltZ = r.z[0] + dirZ * boltFar;
        // from the cloud down to the ground: each joint a step aside of the last, across the way it is seen from
        float high = boltFar * 0.6F;
        float step = boltFar * 0.035F;
        float aside = 0F;
        for (int j = 0; j < JOINTS; j++) {
            float t = j / (float) (JOINTS - 1);
            jx[j] = boltX + dirZ * aside;
            jy[j] = GROUND - high * (1F - t);
            jz[j] = boltZ - dirX * aside;
            aside += (Fall.hash(s, j, 11) - 0.5F) * 2F * step;
        }
        // one branch, from a joint a third of the way down, off to one side and ending in the air
        branchFrom = 2 + (int) (Fall.hash(s, 1, 12) * 3F);
        branchJoints = 4;
        float out = Fall.hash(s, 2, 12) < 0.5F ? -1F : 1F;
        float bx = jx[branchFrom];
        float by = jy[branchFrom];
        float bz = jz[branchFrom];
        for (int j = 0; j < branchJoints; j++) {
            int at = JOINTS + j;
            jx[at] = bx;
            jy[at] = by;
            jz[at] = bz;
            float sideways = out * step * (0.9F + Fall.hash(s, j, 13));
            bx += dirZ * sideways;
            bz -= dirX * sideways;
            by += high * 0.08F * (0.5F + Fall.hash(s, j, 14));
        }
        thunderAt = tick + Math.max(6, (int) (boltFar / THUNDER_PACE));
        thunderGain = 1F - 0.5F * (boltFar - NEAREST) / (FARTHEST - NEAREST);
    }

    /** Where the last bolt struck, and how far from the player that was. */
    float boltX() {
        return boltX;
    }

    float boltZ() {
        return boltZ;
    }

    float boltFar() {
        return boltFar;
    }

    /** How bright the bolt standing now is, 0 (none) to 1. */
    float bright() {
        if (current < 0) {
            return 0F;
        }
        int age = tick - strikeTick[current];
        if (show && age >= 0 && age < 19 * 2) {
            return 1F;
        }
        return switch (age) {
            case 0, 1 -> 1F;
            case 3 -> 0.85F;
            case 4 -> 0.4F;
            default -> 0F;
        };
    }

    /** How much the picture pales with it this tick, 0 to 1. */
    float wash() {
        if (current < 0) {
            return 0F;
        }
        return switch (tick - strikeTick[current]) {
            case 0 -> 0.34F;
            case 1 -> 0.16F;
            case 3 -> 0.24F;
            default -> 0F;
        };
    }

    /** Queues the bolt far off ({@code boltItem}) and, if {@code flash}, the picture's wash ({@code washItem}). */
    void queue(DrawQueue queue, int boltItem, int washItem, boolean flash) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        drawnBolt = 0;
        drawnWash = 0;
        queue.look(look);
        // only ahead of the eye: the game leaves out what reaches behind it
        if (bright() > 0F && (boltX - eyeX) * look[0] + (boltZ - eyeZ) * look[2] > boltFar * 0.3F) {
            queue.at(boltX, GROUND - boltFar * 0.2F, boltZ, boltItem);
        }
        if (flash && wash() > 0F) {
            queue.at(eyeX, eyeY, eyeZ, washItem);
        }
    }

    /** Draws the bolt: a pale glow around a white core, joint to joint, the branch thinner. */
    void drawBolt(Canvas canvas) {
        float bright = bright();
        if (bright <= 0F) {
            return;
        }
        canvas.glow(1F);
        for (int pass = 0; pass < 2; pass++) {
            canvas.color(pass == 0 ? HALO : CORE);
            canvas.dither(pass == 0 ? 0.3F * bright : Math.min(1F, 0.35F + 0.65F * bright));
            float wide = boltFar * (pass == 0 ? 0.0075F : 0.0026F);
            for (int j = 0; j + 1 < JOINTS; j++) {
                float taper = 1F - 0.45F * j / (JOINTS - 1F);
                piece(canvas, j, j + 1, wide * taper, wide * (taper - 0.05F));
            }
            piece(canvas, branchFrom, JOINTS + 1, wide * 0.5F, wide * 0.42F);
            for (int j = 1; j + 1 < branchJoints; j++) {
                float taper = 0.42F - 0.12F * j;
                piece(canvas, JOINTS + j, JOINTS + j + 1, wide * taper, wide * (taper - 0.1F));
            }
        }
        canvas.glow(0F);
        canvas.dither(1F);
    }

    /** One stretch of the bolt from joint {@code a} to joint {@code b}, {@code wa} and {@code wb} wide at them, facing the eye. */
    private void piece(Canvas canvas, int a, int b, float wa, float wb) {
        // across the way it is seen from: level, square to the line from the eye
        float ex = boltX - eyeX;
        float ez = boltZ - eyeZ;
        float e = (float) Math.sqrt(ex * ex + ez * ez) + 1F;
        float ax = ez / e;
        float az = -ex / e;
        cx[0] = jx[a] - ax * wa;
        cy[0] = jy[a];
        cz[0] = jz[a] - az * wa;
        cx[1] = jx[a] + ax * wa;
        cy[1] = jy[a];
        cz[1] = jz[a] + az * wa;
        cx[2] = jx[b] + ax * Math.max(0.2F, wb);
        cy[2] = jy[b];
        cz[2] = jz[b] + az * Math.max(0.2F, wb);
        cx[3] = jx[b] - ax * Math.max(0.2F, wb);
        cy[3] = jy[b];
        cz[3] = jz[b] - az * Math.max(0.2F, wb);
        canvas.world(cx, cy, cz, 4);
        drawnBolt++;
    }

    /** Draws the wash: the whole picture a little paler for the tick. */
    void drawWash(Canvas canvas) {
        float wash = wash();
        if (wash <= 0F) {
            return;
        }
        canvas.color(WASH);
        canvas.dither(wash);
        sx[0] = 0F;
        sy[0] = 0F;
        sx[1] = 1F;
        sy[1] = 0F;
        sx[2] = 1F;
        sy[2] = 1F;
        sx[3] = 0F;
        sy[3] = 1F;
        canvas.screen(sx, sy, 4);
        canvas.dither(1F);
        drawnWash++;
    }
}
