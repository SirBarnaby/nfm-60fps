package nfm.lit.features.weather;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;

/**
 * The dry spots on the starting grid (weather, "Dry spots under the cars at
 * the start"): where each car stood when a race started in rain or snow the
 * ground is as it is in the dry, the car's own shape. Once the car has
 * driven off, the rain wets the spot (it is gone in twelve seconds) or the
 * snow covers it (in forty).
 */
final class DrySpots {

    /** Ticks after its car has left that a spot is gone, in rain and in snow. */
    static final int WET_IN = 19 * 12;
    static final int SNOWED_IN = 19 * 40;
    /** A race that the weather joins later than this many ticks in has no spots: the grid is long wet. */
    static final int AT_THE_START = 19 * 2;
    private static final int CORNERS = 8;
    /** What is laid thinly over the ground of a spot: pale for dry tarmac in the wet, dark for bare ground in the snow. */
    private static final int DRY = 0xe6e0d2;
    private static final int BARE = 0x4c5056;

    private int cars;
    private float[] x = new float[0];
    private float[] z = new float[0];
    private float[] y = new float[0];
    private float[] cos = new float[0];
    private float[] sin = new float[0];
    private float[] halfWide = new float[0];
    private float[] halfLong = new float[0];
    private int[] rgb = new int[0];
    /** The tick the car left its spot, or -1 while it stands on it. */
    int[] left = new int[0];
    private boolean snow;
    private int tick;

    private final float[] cx = new float[CORNERS];
    private final float[] cy = new float[CORNERS];
    private final float[] cz = new float[CORNERS];

    void start(RaceView r, boolean snowing) {
        snow = snowing;
        tick = r.tick;
        cars = r.tick <= AT_THE_START ? r.cars : 0;
        if (x.length < cars) {
            x = new float[cars];
            y = new float[cars];
            z = new float[cars];
            cos = new float[cars];
            sin = new float[cars];
            halfWide = new float[cars];
            halfLong = new float[cars];
            rgb = new int[cars];
            left = new int[cars];
        }
        for (int c = 0; c < cars; c++) {
            x[c] = r.x[c];
            z[c] = r.z[c];
            y[c] = 250F;
            double yaw = Math.toRadians(r.xz[c]);
            cos[c] = (float) Math.cos(yaw);
            sin[c] = (float) Math.sin(yaw);
            CarShape s = r.shape[c];
            float wide = Math.max(Math.abs(s.minX), Math.abs(s.maxX));
            float longer = Math.max(Math.abs(s.minZ), Math.abs(s.maxZ));
            if (wide < 10F || longer < 10F) {
                // no model to go by: the car's radius
                wide = r.radius[c] * 0.45F;
                longer = r.radius[c];
            }
            halfWide[c] = wide * 1.05F;
            halfLong[c] = longer * 1.05F;
            rgb[c] = -1;
            left[c] = -1;
        }
    }

    void tick(RaceView r) {
        tick = r.tick;
        for (int c = 0; c < cars && c < r.cars; c++) {
            if (rgb[c] < 0) {
                // the ground it stands on, from the first wheel that touches it
                for (int w = 0; w < RaceView.WHEELS; w++) {
                    int at = c * RaceView.WHEELS + w;
                    if (r.contact[at]) {
                        rgb[c] = r.groundRgb[at];
                        y[c] = r.wheelY[at];
                        break;
                    }
                }
            }
            if (left[c] < 0) {
                float dx = r.x[c] - x[c];
                float dz = r.z[c] - z[c];
                if (dx * dx + dz * dz > halfLong[c] * halfLong[c]) {
                    left[c] = tick;
                }
            }
        }
    }

    /** How much of spot {@code c} still shows, 1 (all) to 0 (gone). */
    float shows(int c) {
        if (c < 0 || c >= cars || rgb[c] < 0) {
            return 0F;
        }
        return left[c] < 0 ? 1F : Math.max(0F, 1F - (tick - left[c]) / (float) (snow ? SNOWED_IN : WET_IN));
    }

    /** True while some spot shows. */
    boolean any() {
        for (int c = 0; c < cars; c++) {
            if (shows(c) > 0F) {
                return true;
            }
        }
        return false;
    }

    /** Spots drawn by the last {@link #draw}. */
    int drawn;

    void draw(Canvas canvas) {
        drawn = 0;
        for (int c = 0; c < cars; c++) {
            float shows = shows(c);
            if (shows <= 0.02F) {
                continue;
            }
            // laid over the ground as it is drawn: dry tarmac is paler than wet; under snow the bare ground is darker
            canvas.color(canvas.fog(snow ? BARE : DRY, x[c], y[c], z[c]));
            canvas.dither((snow ? 0.5F : 0.3F) * shows);
            float w = halfWide[c];
            float l = halfLong[c];
            float cut = Math.min(w, l) * 0.35F;
            corner(c, 0, -w + cut, -l);
            corner(c, 1, w - cut, -l);
            corner(c, 2, w, -l + cut);
            corner(c, 3, w, l - cut);
            corner(c, 4, w - cut, l);
            corner(c, 5, -w + cut, l);
            corner(c, 6, -w, l - cut);
            corner(c, 7, -w, -l + cut);
            canvas.world(cx, cy, cz, CORNERS);
            drawn++;
        }
    }

    /** Corner {@code k} of spot {@code c}: {@code across} and {@code along} the car as it stood. */
    private void corner(int c, int k, float across, float along) {
        cx[k] = x[c] + across * cos[c] - along * sin[c];
        cy[k] = y[c] - 0.3F;
        cz[k] = z[c] + across * sin[c] + along * cos[c];
    }
}
