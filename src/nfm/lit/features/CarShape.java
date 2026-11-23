package nfm.lit.features;

/**
 * A car's model as the game loaded it, for the extras that draw on it or
 * recolour it: each polygon's corners in the car's own numbers (x to the
 * right, y down, z forward), its own colour, and what it is (glass, a light,
 * part of a wheel). Filled by the game once a race, when it starts.
 */
public final class CarShape {

    /** Polygons, in the model's order (the game's polygon numbers). */
    public int planes;
    /** Polygon {@code i}'s first corner in {@link #x}, {@link #y}, {@link #z}, and how many it has. */
    public int[] first = new int[0];
    public int[] count = new int[0];
    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] z = new float[0];
    /** Polygon {@code i}'s own colour, 0xRRGGBB. */
    public int[] rgb = new int[0];
    public boolean[] glass = new boolean[0];
    /** 0 none, 1 a front light, 2 a rear light. */
    public int[] light = new int[0];
    /** The wheel (0 to 3, as {@link RaceView#keyX}) the polygon is part of, or -1. */
    public int[] wheel = new int[0];
    /** Each wheel's hub and radius. */
    public final float[] hubX = new float[RaceView.WHEELS];
    public final float[] hubY = new float[RaceView.WHEELS];
    public final float[] hubZ = new float[RaceView.WHEELS];
    public final float[] wheelRadius = new float[RaceView.WHEELS];
    /** Half the tyre's width. */
    public final float[] wheelHalfWidth = new float[RaceView.WHEELS];
    /** Where a front wheel steers about (its polygons' {@code wx}); 0 for a wheel that does not steer. */
    public final int[] steerX = new int[RaceView.WHEELS];
    /** The body's extent (the wheels left out). */
    public float minX;
    public float maxX;
    public float minY;
    public float maxY;
    public float minZ;
    public float maxZ;

    /** Room for {@code polygons} polygons and {@code corners} corners in all. */
    public void fit(int polygons, int corners) {
        planes = polygons;
        if (first.length < polygons) {
            first = new int[polygons];
            count = new int[polygons];
            rgb = new int[polygons];
            glass = new boolean[polygons];
            light = new int[polygons];
            wheel = new int[polygons];
        }
        if (x.length < corners) {
            x = new float[corners];
            y = new float[corners];
            z = new float[corners];
        }
    }

    /** Polygon {@code i}'s middle into {@code out[0..2]}. */
    public void middle(int i, float[] out) {
        float sx = 0F;
        float sy = 0F;
        float sz = 0F;
        int n = count[i];
        for (int k = 0; k < n; k++) {
            sx += x[first[i] + k];
            sy += y[first[i] + k];
            sz += z[first[i] + k];
        }
        out[0] = sx / Math.max(1, n);
        out[1] = sy / Math.max(1, n);
        out[2] = sz / Math.max(1, n);
    }

    /** Polygon {@code i}'s unit normal into {@code out[0..2]} (Newell's method; zero for a degenerate one), and its area. */
    public float normal(int i, float[] out) {
        float nx = 0F;
        float ny = 0F;
        float nz = 0F;
        int n = count[i];
        int f = first[i];
        for (int k = 0; k < n; k++) {
            int a = f + k;
            int b = f + (k + 1) % n;
            nx += (y[a] - y[b]) * (z[a] + z[b]);
            ny += (z[a] - z[b]) * (x[a] + x[b]);
            nz += (x[a] - x[b]) * (y[a] + y[b]);
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4F) {
            out[0] = out[1] = out[2] = 0F;
            return 0F;
        }
        out[0] = nx / len;
        out[1] = ny / len;
        out[2] = nz / len;
        return len / 2F;
    }
}
