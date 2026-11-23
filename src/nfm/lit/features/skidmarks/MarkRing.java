package nfm.lit.features.skidmarks;

import nfm.lit.features.Canvas;

/**
 * The skidmarks on the road: a fixed ring of mark pieces, the oldest making
 * way for the newest, and per wheel the mark it is drawing now.
 * <p>
 * A mark is a strip of four-cornered pieces, one a tick, each starting where
 * the last ended. Its width is the tyre's contact patch seen across the way
 * it moves: the tyre's width when it rolls along the mark, the patch's
 * length when it slides sideways. A wheel that slides on the spot (a
 * burnout) darkens its piece instead of adding one.
 */
final class MarkRing {

    static final int CAPACITY = 4096;
    /** A metal scrape: the body on the road. */
    static final int METAL = 5;

    /** How far apart a wheel's two places must be to make a new piece rather than darken the last. */
    private static final float SPOT = 3F;
    /** Half the length of a fresh mark's first piece, and of a tyre's contact patch. */
    private static final float PATCH = 9F;

    /* per piece: corners start-left, start-right, end-right, end-left */
    final float[] cornerX = new float[CAPACITY * 4];
    final float[] cornerZ = new float[CAPACITY * 4];
    final float[] startY = new float[CAPACITY];
    final float[] endY = new float[CAPACITY];
    /** Where the tyre pointed (for the striations), as a level direction. */
    final float[] headX = new float[CAPACITY];
    final float[] headZ = new float[CAPACITY];
    final int[] rgb = new int[CAPACITY];
    final float[] cover = new float[CAPACITY];
    final int[] kind = new int[CAPACITY];
    /** The wheel that made it (car * 4 + wheel), and the tick. */
    final int[] wheel = new int[CAPACITY];
    final int[] tick = new int[CAPACITY];
    /** On a ramp, which is drawn among the objects rather than with the flat road: drawn among them too. */
    final boolean[] raised = new boolean[CAPACITY];
    private int next;
    int count;
    /** Pieces made since the ring was cleared, the overwritten ones too. */
    int made;

    /* per wheel: the mark being drawn */
    private boolean[] open = new boolean[0];
    private int[] last = new int[0];
    private float[] lastX = new float[0];
    private float[] lastZ = new float[0];

    void clear() {
        next = 0;
        count = 0;
        made = 0;
        java.util.Arrays.fill(open, false);
    }

    void fit(int wheels) {
        if (open.length < wheels) {
            open = new boolean[wheels];
            last = new int[wheels];
            lastX = new float[wheels];
            lastZ = new float[wheels];
        }
    }

    boolean isOpen(int w) {
        return open[w];
    }

    /** Ends wheel {@code w}'s mark. */
    void close(int w) {
        open[w] = false;
    }

    /**
     * Wheel {@code w} slides at {@code (x, y, z)}, its tyre pointing
     * {@code (hx, hz)} (level, length 1), {@code tyre} wide: the mark goes
     * on there, in {@code color}, covering {@code coverage} of the road.
     */
    void mark(int w, float x, float y, float z, float hx, float hz, float tyre, int color, float coverage,
            int markKind, boolean onRamp, int now) {
        if (open[w]) {
            float dx = x - lastX[w];
            float dz = z - lastZ[w];
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            int p = last[w];
            if (len < SPOT) {
                if (kind[p] != METAL) {
                    // sliding on the spot: the piece darkens
                    cover[p] = Math.min(1F, cover[p] + 0.12F);
                    rgb[p] = color;
                }
                return;
            }
            int q = piece();
            // starts where the last ended
            cornerX[q * 4] = cornerX[p * 4 + 3];
            cornerZ[q * 4] = cornerZ[p * 4 + 3];
            cornerX[q * 4 + 1] = cornerX[p * 4 + 2];
            cornerZ[q * 4 + 1] = cornerZ[p * 4 + 2];
            startY[q] = endY[p];
            float mx = dx / len;
            float mz = dz / len;
            float half = halfWidth(mx, mz, hx, hz, tyre);
            // across the way it moves, to the left of it
            cornerX[q * 4 + 3] = x - mz * half;
            cornerZ[q * 4 + 3] = z + mx * half;
            cornerX[q * 4 + 2] = x + mz * half;
            cornerZ[q * 4 + 2] = z - mx * half;
            finish(q, w, y, hx, hz, color, coverage, markKind, onRamp, now);
            last[w] = q;
        } else {
            int q = piece();
            float half = tyre / 2F;
            // a fresh mark: one patch long, along the tyre, ending under the wheel, where the next piece starts
            float sx = x - hx * 2F * PATCH;
            float sz = z - hz * 2F * PATCH;
            float ex = x;
            float ez = z;
            cornerX[q * 4] = sx - hz * half;
            cornerZ[q * 4] = sz + hx * half;
            cornerX[q * 4 + 1] = sx + hz * half;
            cornerZ[q * 4 + 1] = sz - hx * half;
            cornerX[q * 4 + 2] = ex + hz * half;
            cornerZ[q * 4 + 2] = ez - hx * half;
            cornerX[q * 4 + 3] = ex - hz * half;
            cornerZ[q * 4 + 3] = ez + hx * half;
            startY[q] = y;
            finish(q, w, y, hx, hz, color, coverage, markKind, onRamp, now);
            open[w] = true;
            last[w] = q;
        }
        lastX[w] = x;
        lastZ[w] = z;
    }

    /** The patch's half width across a way {@code (mx, mz)}, for a tyre pointing {@code (hx, hz)}. */
    private static float halfWidth(float mx, float mz, float hx, float hz, float tyre) {
        float along = Math.abs(mx * hx + mz * hz);
        float across = Math.abs(mx * hz - mz * hx);
        return (tyre / 2F) * along + PATCH * across;
    }

    private void finish(int q, int w, float y, float hx, float hz, int color, float coverage, int markKind,
            boolean onRamp, int now) {
        endY[q] = y;
        headX[q] = hx;
        headZ[q] = hz;
        rgb[q] = color;
        cover[q] = coverage;
        kind[q] = markKind;
        wheel[q] = w;
        tick[q] = now;
        raised[q] = onRamp;
    }

    private int piece() {
        made++;
        int q = next;
        next = (next + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        } else {
            // the oldest piece goes: a wheel still drawing from it starts afresh
            for (int w = 0; w < open.length; w++) {
                if (open[w] && last[w] == q) {
                    open[w] = false;
                }
            }
        }
        return q;
    }

    /** The newest piece of wheel {@code w}'s mark (valid while open). */
    int last(int w) {
        return last[w];
    }

    /** The ring's pieces from the oldest: the {@code i}-th oldest piece's slot. */
    int slot(int i) {
        return (next - count + i + CAPACITY) % CAPACITY;
    }

    /* ------------------------------------------------------------ drawing */

    private final float[] px = new float[4];
    private final float[] py = new float[4];
    private final float[] pz = new float[4];

    /**
     * Draws piece {@code q}: its strip, and with {@code detail} a locked
     * mark's streaks along it or a sideways one's fine striations, seen from
     * {@code distance} away.
     */
    void draw(Canvas canvas, int q, boolean detail, float distance) {
        float c = cover[q];
        if (c <= 0.02F) {
            return;
        }
        float midX = (cornerX[q * 4] + cornerX[q * 4 + 2]) / 2F;
        float midZ = (cornerZ[q * 4] + cornerZ[q * 4 + 2]) / 2F;
        float midY = (startY[q] + endY[q]) / 2F;
        int base = canvas.fog(rgb[q], midX, midY, midZ);
        canvas.color(base);
        canvas.dither(c);
        for (int k = 0; k < 4; k++) {
            px[k] = cornerX[q * 4 + k];
            pz[k] = cornerZ[q * 4 + k];
            py[k] = (k < 2 ? startY[q] : endY[q]) - 0.5F;
        }
        canvas.world(px, py, pz, 4);
        if (!detail || distance > 1800F) {
            return;
        }
        if (kind[q] == SkidSlip.LOCKED) {
            // streaks along the mark: two darker threads
            canvas.color(darker(base, 0.6F));
            canvas.dither(Math.min(1F, c + 0.25F));
            thread(canvas, q, 0.3F, 0.08F);
            thread(canvas, q, 0.7F, 0.08F);
        } else if (kind[q] == SkidSlip.SIDEWAYS) {
            // fine diagonal striations, along the tyre
            canvas.color(darker(base, 0.75F));
            canvas.dither(Math.min(1F, c + 0.15F));
            striation(canvas, q, 0.33F);
            striation(canvas, q, 0.66F);
        }
    }

    /** A thin thread {@code width} of the strip wide, {@code at} across it, along the whole piece. */
    private void thread(Canvas canvas, int q, float at, float width) {
        float a0 = at - width / 2F;
        float a1 = at + width / 2F;
        across(q, 0, a0, a1, 0);
        across(q, 2, a1, a0, 2);
        canvas.world(px, py, pz, 4);
    }

    /** Corners {@code k} and {@code k + 1}: at shares {@code a} and {@code b} across the piece's start (k 0) or end (k 2). */
    private void across(int q, int end, float a, float b, int k) {
        int l = end == 0 ? 0 : 3;
        int r = end == 0 ? 1 : 2;
        float lx = cornerX[q * 4 + l];
        float lz = cornerZ[q * 4 + l];
        float rx = cornerX[q * 4 + r];
        float rz = cornerZ[q * 4 + r];
        float y = (end == 0 ? startY[q] : endY[q]) - 0.6F;
        px[k] = lx + (rx - lx) * a;
        pz[k] = lz + (rz - lz) * a;
        py[k] = y;
        px[k + 1] = lx + (rx - lx) * b;
        pz[k + 1] = lz + (rz - lz) * b;
        py[k + 1] = y;
    }

    /** A short line along the tyre's heading through the share {@code at} of the piece. */
    private void striation(Canvas canvas, int q, float at) {
        float sx = cornerX[q * 4] + (cornerX[q * 4 + 3] - cornerX[q * 4]) * at;
        float sz = cornerZ[q * 4] + (cornerZ[q * 4 + 3] - cornerZ[q * 4]) * at;
        float ex = cornerX[q * 4 + 1] + (cornerX[q * 4 + 2] - cornerX[q * 4 + 1]) * at;
        float ez = cornerZ[q * 4 + 1] + (cornerZ[q * 4 + 2] - cornerZ[q * 4 + 1]) * at;
        float mx = (sx + ex) / 2F;
        float mz = (sz + ez) / 2F;
        float hx = headX[q];
        float hz = headZ[q];
        float len = PATCH * 0.9F;
        float w = 0.9F;
        float y = (startY[q] + endY[q]) / 2F - 0.6F;
        px[0] = mx - hx * len - hz * w;
        pz[0] = mz - hz * len + hx * w;
        px[1] = mx - hx * len + hz * w;
        pz[1] = mz - hz * len - hx * w;
        px[2] = mx + hx * len + hz * w;
        pz[2] = mz + hz * len - hx * w;
        px[3] = mx + hx * len - hz * w;
        pz[3] = mz + hz * len + hx * w;
        py[0] = py[1] = py[2] = py[3] = y;
        canvas.world(px, py, pz, 4);
    }

    static int darker(int color, float f) {
        int r = (int) ((color >> 16 & 0xff) * f);
        int g = (int) ((color >> 8 & 0xff) * f);
        int b = (int) ((color & 0xff) * f);
        return r << 16 | g << 8 | b;
    }
}
