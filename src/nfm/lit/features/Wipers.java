package nfm.lit.features;

/**
 * A car's windscreen wipers: two simple blades, each a blade and its arm,
 * found a place on the car's own windscreen when a race starts and drawn on
 * the car ({@link Canvas#onCar}), parked along the foot of the glass or
 * somewhere on their way across it. The weather runs them in the rain; a
 * car with no windscreen (an open car) has none.
 * <p>
 * Both wipe the same way (in tandem), from lying along the foot of the
 * glass to standing up on it. Drawing only; nothing is allocated after
 * {@link #build}.
 */
public final class Wipers {

    /** Ticks a wipe takes, there and back. */
    public static final int CYCLE = 24;
    /** A blade's colour, before the stage's light. */
    public static final int RGB = 0x17191c;
    /** Degrees a blade is up from the foot of the glass when parked, and at the top of its wipe. */
    public static final float PARKED = 4F;
    public static final float SWEPT = 86F;
    /** How far the blades stand off the glass, so it never covers them. */
    static final float STAND_OFF = 0.5F;
    /** A blade's length, of the glass's width and of its height at most. */
    static final float OF_WIDTH = 0.42F;
    static final float OF_HEIGHT = 0.85F;
    private static final int MOST_PANES = 8;

    /** The car has a windscreen, and wipers on it. */
    public boolean has;
    /** The windscreen: its middle, across it, down it and out of it, in the car's own numbers. */
    public final float[] mid = new float[3];
    public final float[] across = new float[3];
    public final float[] down = new float[3];
    public final float[] out = new float[3];
    /** The glass from its middle: to the left and right across it, to its top and foot down it. */
    float left;
    float right;
    float top;
    float foot;
    /** Each wiper's pivot across the glass, where both are down it, and a blade's length. */
    final float[] pivot = new float[2];
    float pivotDown;
    public float length;

    /* the panes of the windscreen (a model's may be several polygons), for what is on the glass */
    private final int[] panes = new int[MOST_PANES];
    private int paneCount;
    private CarShape shape;
    private final float[] normal = new float[3];
    private final float[] middle = new float[3];
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];

    /** Finds car shape {@code s}'s windscreen and places the wipers on it; false if it has none. */
    public boolean build(CarShape s) {
        shape = s;
        has = false;
        paneCount = 0;
        float cx = (s.minX + s.maxX) / 2F;
        float cy = (s.minY + s.maxY) / 2F;
        float cz = (s.minZ + s.maxZ) / 2F;
        // the windscreen: the glass that faces forward most over the biggest area
        int screen = -1;
        float best = 0F;
        for (int i = 0; i < s.planes; i++) {
            if (!s.glass[i] || s.wheel[i] >= 0 || s.count[i] < 3) {
                continue;
            }
            float area = outward(s, i, cx, cy, cz);
            if (Math.abs(normal[0]) <= 0.6F && normal[2] > 0.3F && area * normal[2] > best) {
                best = area * normal[2];
                screen = i;
            }
        }
        if (screen < 0) {
            return false;
        }
        outward(s, screen, cx, cy, cz);
        System.arraycopy(normal, 0, out, 0, 3);
        s.middle(screen, mid);
        // across: the car's x laid into the glass; down: square to both, towards the bonnet
        float d = out[0];
        across[0] = 1F - d * out[0];
        across[1] = -d * out[1];
        across[2] = -d * out[2];
        unit(across);
        down[0] = out[1] * across[2] - out[2] * across[1];
        down[1] = out[2] * across[0] - out[0] * across[2];
        down[2] = out[0] * across[1] - out[1] * across[0];
        if (down[1] < 0F) {
            down[0] = -down[0];
            down[1] = -down[1];
            down[2] = -down[2];
        }
        left = Float.MAX_VALUE;
        right = -Float.MAX_VALUE;
        top = Float.MAX_VALUE;
        foot = -Float.MAX_VALUE;
        for (int i = 0; i < s.planes && paneCount < MOST_PANES; i++) {
            if (!s.glass[i] || s.wheel[i] >= 0 || s.count[i] < 3) {
                continue;
            }
            outward(s, i, cx, cy, cz);
            s.middle(i, middle);
            float off = dot(out, middle[0] - mid[0], middle[1] - mid[1], middle[2] - mid[2]);
            if (dot(out, normal[0], normal[1], normal[2]) < 0.9F || Math.abs(off) > 3F) {
                continue;
            }
            panes[paneCount++] = i;
            for (int k = s.first[i]; k < s.first[i] + s.count[i]; k++) {
                float u = dot(across, s.x[k] - mid[0], s.y[k] - mid[1], s.z[k] - mid[2]);
                float v = dot(down, s.x[k] - mid[0], s.y[k] - mid[1], s.z[k] - mid[2]);
                left = Math.min(left, u);
                right = Math.max(right, u);
                top = Math.min(top, v);
                foot = Math.max(foot, v);
            }
        }
        float wide = right - left;
        float high = foot - top;
        if (wide < 6F || high < 3F) {
            return false;
        }
        pivot[0] = left + 0.47F * wide;
        pivot[1] = left + 0.93F * wide;
        pivotDown = foot - 0.05F * high;
        length = Math.min(OF_WIDTH * wide, OF_HEIGHT * high);
        // shorter, until the blades' ends stay on the glass all the way up (a windscreen narrows towards the roof)
        for (int tries = 0; tries < 6 && !staysOn(); tries++) {
            length *= 0.9F;
        }
        has = true;
        return true;
    }

    /** True if both blades' ends are on the glass halfway up and at the top of the wipe. */
    private boolean staysOn() {
        for (int k = 0; k < 2; k++) {
            for (float sweep = 0.5F; sweep <= 1F; sweep += 0.5F) {
                double a = Math.toRadians(PARKED + (SWEPT - PARKED) * sweep);
                if (!onGlass(pivot[k] - (float) Math.cos(a) * length, pivotDown - (float) Math.sin(a) * length)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if the point {@code (u, v)} across and down the glass from its middle is on one of its panes. */
    private boolean onGlass(float u, float v) {
        CarShape s = shape;
        for (int p = 0; p < paneCount; p++) {
            int i = panes[p];
            int n = s.count[i];
            boolean in = false;
            for (int k = 0, j = n - 1; k < n; j = k++) {
                int a = s.first[i] + k;
                int b = s.first[i] + j;
                float ua = dot(across, s.x[a] - mid[0], s.y[a] - mid[1], s.z[a] - mid[2]);
                float va = dot(down, s.x[a] - mid[0], s.y[a] - mid[1], s.z[a] - mid[2]);
                float ub = dot(across, s.x[b] - mid[0], s.y[b] - mid[1], s.z[b] - mid[2]);
                float vb = dot(down, s.x[b] - mid[0], s.y[b] - mid[1], s.z[b] - mid[2]);
                if (va > v != vb > v && u < (ub - ua) * (v - va) / (vb - va) + ua) {
                    in = !in;
                }
            }
            if (in) {
                return true;
            }
        }
        return false;
    }

    /** Polygon {@code i}'s normal turned out of the car into {@link #normal}, and its area. */
    private float outward(CarShape s, int i, float cx, float cy, float cz) {
        float area = s.normal(i, normal);
        s.middle(i, middle);
        if (normal[0] * (middle[0] - cx) + normal[1] * (middle[1] - cy) + normal[2] * (middle[2] - cz) < 0F) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
            normal[2] = -normal[2];
        }
        return area;
    }

    /**
     * How far through its wipe a blade is, 0 parked to 1 at the top,
     * {@code ticks} (a real number) into a wipe of {@link #CYCLE} ticks:
     * up and back down smoothly.
     */
    public static float sweep(float ticks) {
        float phase = ticks / CYCLE;
        phase -= (float) Math.floor(phase);
        return 0.5F - 0.5F * (float) Math.cos(2D * Math.PI * phase);
    }

    /** Draws both wipers on car {@code car}, {@code sweep} through their wipe, in the canvas's colour: 4 polygons. */
    public void draw(Canvas canvas, int car, float sweep) {
        if (!has) {
            return;
        }
        double a = Math.toRadians(PARKED + (SWEPT - PARKED) * Math.max(0F, Math.min(1F, sweep)));
        // along the blade from its pivot (towards the left when parked, up the glass as it wipes), and across it
        float du = (float) -Math.cos(a);
        float dv = (float) -Math.sin(a);
        float half = Math.max(0.35F, 0.03F * length);
        float pu = -dv * half;
        float pv = du * half;
        for (int k = 0; k < 2; k++) {
            float u0 = pivot[k];
            float v0 = pivotDown;
            // the arm: from the pivot to the middle of the blade
            corner(0, u0 + pu * 0.9F, v0 + pv * 0.9F, STAND_OFF * 1.4F);
            corner(1, u0 + du * length * 0.6F, v0 + dv * length * 0.6F, STAND_OFF * 1.4F);
            corner(2, u0 - pu * 0.9F, v0 - pv * 0.9F, STAND_OFF * 1.4F);
            canvas.onCar(car, x, y, z, 3);
            // the blade
            float from = 0.16F * length;
            corner(0, u0 + du * from + pu, v0 + dv * from + pv, STAND_OFF);
            corner(1, u0 + du * length + pu, v0 + dv * length + pv, STAND_OFF);
            corner(2, u0 + du * length - pu, v0 + dv * length - pv, STAND_OFF);
            corner(3, u0 + du * from - pu, v0 + dv * from - pv, STAND_OFF);
            canvas.onCar(car, x, y, z, 4);
        }
    }

    /** How far down the glass from its middle its foot is. */
    public float foot() {
        return foot;
    }

    /** Where blade {@code k}'s end is {@code sweep} through its wipe, across and down the glass from its middle, into {@code uv}. */
    public void end(int k, float sweep, float[] uv) {
        double a = Math.toRadians(PARKED + (SWEPT - PARKED) * sweep);
        uv[0] = pivot[k] - (float) Math.cos(a) * length;
        uv[1] = pivotDown - (float) Math.sin(a) * length;
    }

    /** True if {@code (u, v)}, across and down the glass from its middle, is on the windscreen. */
    public boolean covers(float u, float v) {
        return has && onGlass(u, v);
    }

    private void corner(int k, float u, float v, float off) {
        x[k] = mid[0] + across[0] * u + down[0] * v + out[0] * off;
        y[k] = mid[1] + across[1] * u + down[1] * v + out[1] * off;
        z[k] = mid[2] + across[2] * u + down[2] * v + out[2] * off;
    }

    private static float dot(float[] a, float x, float y, float z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    private static void unit(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 1e-6F) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }
}
