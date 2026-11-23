package nfm.lit.features;

/**
 * A fixed pool of particles the extras share the code of: smoke, dust, clods
 * of earth, sparks, vapour. Each moves on its own once a tick (drag,
 * gravity or lift, the wind, the ground) and is drawn smoothly between the
 * ticks ({@link Canvas#moving}). A puff of smoke, dust or vapour is a
 * dithered hexagon facing the camera that turns slowly as it grows; a clod
 * is a small tumbling square; a fast spark is a short glowing streak along
 * its path. Each corner is given its own move to the next tick, so growing
 * and turning are as smooth as the flight. When the pool is full the oldest
 * particle makes way. Nothing is allocated after it is made.
 */
public final class Particles {

    /** The ground's height where a particle falls onto it (the flat ground). */
    private static final float GROUND = 250F;
    /** Corners of a puff and of a clod. */
    private static final int PUFF = 6;
    private static final int CLOD = 4;
    /** How far a puff and a clod turn a tick, radians. */
    private static final float PUFF_TURN = 0.05F;
    private static final float CLOD_TURN = 0.45F;
    /** Spreads the particles' first angles evenly (the golden angle). */
    private static final float GOLDEN = 2.3999631F;

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
    private final float[] spin;
    private final float[] turn;
    /** The height a particle lands at (the flat ground unless told otherwise). */
    private final float[] floor;
    /** Stays as debris where it lands ({@link CarLooks#debris}), if something keeps debris. */
    private final boolean[] stays;
    private int next;
    private int count;

    /* the camera's eye for the frame being queued, for the shapes that face it */
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    private final float[] cx = new float[PUFF];
    private final float[] cy = new float[PUFF];
    private final float[] cz = new float[PUFF];
    private final float[] mx = new float[PUFF];
    private final float[] my = new float[PUFF];
    private final float[] mz = new float[PUFF];

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
        spin = new float[capacity];
        turn = new float[capacity];
        floor = new float[capacity];
        stays = new boolean[capacity];
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

    /** How many particles the pool holds at most. */
    public int capacity() {
        return capacity;
    }

    /** Particle {@code i}'s place into {@code out[0..2]}; false if it is not alive. */
    public boolean position(int i, float[] out) {
        if (i < 0 || i >= capacity || !alive[i]) {
            return false;
        }
        out[0] = x[i];
        out[1] = y[i];
        out[2] = z[i];
        return true;
    }

    /** Particle {@code i}'s colour, 0xRRGGBB, or -1 if it is not alive. */
    public int color(int i) {
        return i >= 0 && i < capacity && alive[i] ? rgb[i] : -1;
    }

    /** Particle {@code i}'s move a tick into {@code out[0..2]}; false if it is not alive. */
    public boolean velocity(int i, float[] out) {
        if (i < 0 || i >= capacity || !alive[i]) {
            return false;
        }
        out[0] = vx[i];
        out[1] = vy[i];
        out[2] = vz[i];
        return true;
    }

    /** True if particle {@code i} is alive and drawn as a glowing streak (a spark). */
    public boolean isSpark(int i) {
        return i >= 0 && i < capacity && alive[i] && streak[i];
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
        // a first angle and a way of turning from the slot alone: no random needed, and the same every run
        spin[i] = i * GOLDEN;
        float way = (i & 1) == 0 ? 1F : -1F;
        turn[i] = spark ? 0F : way * (rise < 0F ? CLOD_TURN : PUFF_TURN);
        floor[i] = GROUND;
        stays[i] = false;
        return i;
    }

    /** Particle {@code i} lands at height {@code y} rather than the flat ground. */
    public void floor(int i, float y) {
        if (i >= 0 && i < capacity) {
            floor[i] = y;
        }
    }

    /** Particle {@code i} stays where it lands, as debris, while something keeps debris. */
    public void keep(int i) {
        if (i >= 0 && i < capacity) {
            stays[i] = true;
        }
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
            spin[i] += turn[i];
            float keep = 1F - drag[i];
            vx[i] = vx[i] * keep + (streak[i] ? 0F : windX * drag[i]);
            vz[i] = vz[i] * keep + (streak[i] ? 0F : windZ * drag[i]);
            vy[i] = vy[i] * keep - lift[i];
            if (y[i] > floor[i] && vy[i] > 0F) {
                Debris debris = stays[i] ? CarLooks.debris() : null;
                if (debris != null) {
                    // it stays where it fell
                    debris.drop(x[i], floor[i], z[i], size0[i] * 2F, rgb[i], lift[i] < 0F ? CLOD : 3, spin[i]);
                    alive[i] = false;
                    count--;
                    continue;
                }
                // on the ground: a falling clod or spark bounces a little and skids
                y[i] = floor[i];
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
            canvas.moving(cx, cy, cz, vx[i], vy[i], vz[i], 4);
        } else {
            float next = size0[i] + (size1[i] - size0[i]) * Math.min(1F, (age[i] + 1F) / life[i]);
            int n = lift[i] < 0F ? CLOD : PUFF;
            facing(i, s, next, n);
            canvas.moving(cx, cy, cz, mx, my, mz, n);
        }
    }

    /**
     * A polygon of {@code n} corners, radius {@code s}, facing the eye and
     * turned by the particle's spin; each corner's move takes it to where it
     * is at the next tick (radius {@code next}, turned a tick on, the
     * particle moved on), so the shape grows and turns smoothly.
     */
    private void facing(int i, float s, float next, int n) {
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
        float step = (float) (2D * Math.PI / n);
        for (int k = 0; k < n; k++) {
            float a = spin[i] + k * step;
            float across = (float) Math.cos(a) * s;
            float up = (float) Math.sin(a) * s;
            float b = a + turn[i];
            float acrossNext = (float) Math.cos(b) * next;
            float upNext = (float) Math.sin(b) * next;
            cx[k] = x[i] + ax * across + ux * up;
            cy[k] = y[i] + uy * up;
            cz[k] = z[i] + az * across + uz * up;
            mx[k] = vx[i] + ax * (acrossNext - across) + ux * (upNext - up);
            my[k] = vy[i] + uy * (upNext - up);
            mz[k] = vz[i] + az * (acrossNext - across) + uz * (upNext - up);
        }
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
