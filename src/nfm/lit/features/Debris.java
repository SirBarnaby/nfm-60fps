package nfm.lit.features;

/**
 * Pieces that stay where they fell for the rest of the race (realism,
 * "Debris stays"): bumper bits, glass, clods. A fixed ring: past its
 * capacity the oldest piece goes first. Each piece is a small flat polygon
 * on the ground, turned its own way. Nothing is allocated after it is made.
 */
public final class Debris {

    private final int capacity;
    final float[] x;
    final float[] y;
    final float[] z;
    final float[] size;
    final float[] turn;
    final int[] rgb;
    final int[] corners;
    /** Lies on something above the flat ground (a ramp, a bridge), drawn among the objects rather than with the road. */
    final boolean[] raised;
    private int next;
    private int count;
    private int dropped;

    private final float[] cx = new float[6];
    private final float[] cy = new float[6];
    private final float[] cz = new float[6];

    public Debris(int capacity) {
        this.capacity = capacity;
        x = new float[capacity];
        y = new float[capacity];
        z = new float[capacity];
        size = new float[capacity];
        turn = new float[capacity];
        rgb = new int[capacity];
        corners = new int[capacity];
        raised = new boolean[capacity];
    }

    /** Forgets every piece. */
    public void clear() {
        next = 0;
        count = 0;
        dropped = 0;
    }

    /** Pieces lying now. */
    public int count() {
        return count;
    }

    /** How many it holds at most. */
    public int capacity() {
        return capacity;
    }

    /** Pieces dropped this race, kept or since gone. */
    public int dropped() {
        return dropped;
    }

    /**
     * A piece lands at {@code (px, py, pz)}: {@code across} wide, colour
     * {@code color}, {@code sides} corners (3 to 6), turned {@code angle}
     * radians. Returns its slot.
     */
    public int drop(float px, float py, float pz, float across, int color, int sides, float angle) {
        int i = next;
        next = (next + 1) % capacity;
        count = Math.min(capacity, count + 1);
        dropped++;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        size[i] = across;
        rgb[i] = color;
        corners[i] = Math.max(3, Math.min(6, sides));
        turn[i] = angle;
        raised[i] = Math.abs(py - 250F) > 8F;
        return i;
    }

    /** The slot of the {@code k}-th oldest piece lying now. */
    public int slot(int k) {
        return (next - count + k + capacity) % capacity;
    }

    /** True if the piece in slot {@code i} lies above the flat ground (on a ramp or a raised road). */
    public boolean raised(int i) {
        return raised[i];
    }

    /** The place of the piece in slot {@code i}, into {@code out}. */
    public void position(int i, float[] out) {
        out[0] = x[i];
        out[1] = y[i];
        out[2] = z[i];
    }

    /** Draws the piece in slot {@code i}: flat on the ground, just above it. */
    public void draw(Canvas canvas, int i) {
        int n = corners[i];
        float r = size[i] / 2F;
        for (int k = 0; k < n; k++) {
            // a slightly irregular polygon: every other corner a little nearer
            float a = turn[i] + k * (float) (2D * Math.PI / n);
            float reach = (k & 1) == 0 ? r : r * 0.7F;
            cx[k] = x[i] + (float) Math.cos(a) * reach;
            cy[k] = y[i] - 0.4F;
            cz[k] = z[i] + (float) Math.sin(a) * reach;
        }
        canvas.color(canvas.fog(rgb[i], x[i], y[i], z[i]));
        canvas.dither(1F);
        canvas.world(cx, cy, cz, n);
    }
}
