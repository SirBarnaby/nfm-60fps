package nfm.lit.features;

/**
 * A fixed pool of particles the extras share the code of: smoke, dust, clods
 * of earth, sparks, vapour. Each moves on its own once a tick (drag,
 * gravity or lift, the wind, the ground) and is drawn smoothly between the
 * ticks ({@link Canvas#moving}), as a dithered square facing the camera or,
 * for a fast spark, a short glowing streak along its path. When the pool is
 * full the oldest particle makes way. Nothing is allocated after it is made.
 */
public final class Particles {

    /** The ground's height where a particle falls onto it (the flat ground). */
    private static final float GROUND = 250F;

    private final int capacity;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] vx;
    private final float[] vy;
    private final float[] vz;
    private final float[] age;
    private final float[] life;
    private final float[] size0;
    private final float[] size1;
    private final float[] cover;
    private final float[] drag;
    private final float[] lift;
    private final int[] rgb;
    private final boolean[] streak;
    private final boolean[] alive;
    private int next;
    private int count;

    /* the camera's eye for the frame being queued, for squares that face it */
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];

    public Particles(int capacity) {
        this.capacity = capacity;
        x = new float[capacity];
        y = new float[capacity];
        z = new float[capacity];
        vx = new float[capacity];
        vy = new float[capacity];
        vz = new float[capacity];
        age = new float[capacity];
        life = new float[capacity];
        size0 = new float[capacity];
        size1 = new float[capacity];
        cover = new float[capacity];
        drag = new float[capacity];
        lift = new float[capacity];
        rgb = new int[capacity];
        streak = new boolean[capacity];
        alive = new boolean[capacity];
    }

    /** Forgets every particle. */
    public void clear() {
        java.util.Arrays.fill(alive, false);
        count = 0;
        next = 0;
    }

    /** Particles alive now. */
    public int count() {
        return count;
    }

    /**
     * A new particle at {@code (px, py, pz)} moving {@code (dx, dy, dz)} a
     * tick, living {@code ticks} ticks, growing from {@code from} to
     * {@code to} across, colour {@code color}, covering {@code coverage} of
     * its pixels at first (fading out), losing {@code friction} of its speed
     * a tick, rising {@code rise} units a tick faster each tick (negative:
     * falls), and drawn as a glowing streak if {@code spark}. Returns its
     * slot.
     */
    public int add(float px, float py, float pz, float dx, float dy, float dz, float ticks, float from, float to,
            int color, float coverage, float friction, float rise, boolean spark) {
        int i = next;
        next = (next + 1) % capacity;
        if (!alive[i]) {
            count++;
        }
        alive[i] = true;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        vx[i] = dx;
        vy[i] = dy;
        vz[i] = dz;
        age[i] = 0F;
        life[i] = Math.max(1F, ticks);
        size0[i] = from;
        size1[i] = to;
        rgb[i] = color;
        cover[i] = coverage;
        drag[i] = friction;
        lift[i] = rise;
        streak[i] = spark;
        return i;
    }

    /** One tick: every particle moves, ages, and those whose time is up go; {@code windX/Z} pushes the light ones. */
    public void tick(float windX, float windZ) {
        if (count == 0) {
            return;
        }
        for (int i = 0; i < capacity; i++) {
            if (!alive[i]) {
                continue;
            }
            if (++age[i] >= life[i]) {
                alive[i] = false;
                count--;
                continue;
            }
            x[i] += vx[i];
            y[i] += vy[i];
            z[i] += vz[i];
            float keep = 1F - drag[i];
            vx[i] = vx[i] * keep + (streak[i] ? 0F : windX * drag[i]);
            vz[i] = vz[i] * keep + (streak[i] ? 0F : windZ * drag[i]);
            vy[i] = vy[i] * keep - lift[i];
            if (y[i] > GROUND && vy[i] > 0F) {
                // on the ground: a falling clod or spark bounces a little and skids
                y[i] = GROUND;
                vy[i] = -vy[i] * 0.3F;
                vx[i] *= 0.6F;
                vz[i] *= 0.6F;
            }
        }
    }

    /** Queues every live particle at its place, seen from the queue's eye. */
    public void queue(DrawQueue queue, int base) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        if (count == 0) {
            return;
        }
        for (int i = 0; i < capacity; i++) {
            if (alive[i]) {
                queue.at(x[i], y[i], z[i], base + i);
            }
        }
    }

    /** Draws particle {@code i}. */
    public void draw(Canvas canvas, int i) {
        if (i < 0 || i >= capacity || !alive[i]) {
            return;
        }
        float t = age[i] / life[i];
        float fade = t < 0.1F ? t / 0.1F : 1F - (t - 0.1F) / 0.9F;
        float coverage = cover[i] * Math.max(0F, fade);
        if (coverage <= 0.02F) {
            return;
        }
        canvas.color(canvas.fog(rgb[i], x[i], y[i], z[i]));
        canvas.dither(coverage);
        float s = size0[i] + (size1[i] - size0[i]) * t;
        if (streak[i]) {
            canvas.glow(1F);
            streak(i, s);
        } else {
            square(i, s);
        }
        canvas.moving(cx, cy, cz, vx[i], vy[i], vz[i], 4);
    }

    /** A square of half size {@code s} facing the eye. */
    private void square(int i, float s) {
        float dx = x[i] - eyeX;
        float dy = y[i] - eyeY;
        float dz = z[i] - eyeZ;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1F) {
            len = 1F;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        // across: level and square to the view; up: square to both
        float ax = dz;
        float az = -dx;
        float al = (float) Math.sqrt(ax * ax + az * az);
        if (al < 1e-3F) {
            ax = 1F;
            az = 0F;
            al = 1F;
        }
        ax /= al;
        az /= al;
        float ux = dy * az;
        float uy = dz * ax - dx * az;
        float uz = -dy * ax;
        cx[0] = x[i] - ax * s - ux * s;
        cy[0] = y[i] - uy * s;
        cz[0] = z[i] - az * s - uz * s;
        cx[1] = x[i] + ax * s - ux * s;
        cy[1] = y[i] - uy * s;
        cz[1] = z[i] + az * s - uz * s;
        cx[2] = x[i] + ax * s + ux * s;
        cy[2] = y[i] + uy * s;
        cz[2] = z[i] + az * s + uz * s;
        cx[3] = x[i] - ax * s + ux * s;
        cy[3] = y[i] + uy * s;
        cz[3] = z[i] - az * s + uz * s;
    }

    /** A streak from the particle back along its path, {@code w} wide. */
    private void streak(int i, float w) {
        float bx = x[i] - vx[i] * 0.7F;
        float by = y[i] - vy[i] * 0.7F;
        float bz = z[i] - vz[i] * 0.7F;
        // across the streak, level
        float dx = x[i] - bx;
        float dz = z[i] - bz;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float ax = len < 1e-3F ? w : -dz / len * w;
        float az = len < 1e-3F ? 0F : dx / len * w;
        cx[0] = bx - ax;
        cy[0] = by - w;
        cz[0] = bz - az;
        cx[1] = x[i] - ax;
        cy[1] = y[i] - w;
        cz[1] = z[i] - az;
        cx[2] = x[i] + ax;
        cy[2] = y[i] + w;
        cz[2] = z[i] + az;
        cx[3] = bx + ax;
        cy[3] = by + w;
        cz[3] = bz + az;
    }
}
