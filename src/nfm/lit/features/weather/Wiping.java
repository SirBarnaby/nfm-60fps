package nfm.lit.features.weather;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Features;
import nfm.lit.features.RaceView;
import nfm.lit.features.Wipers;
import nfm.lit.features.helmetcam.HelmetCam;

/**
 * The wipers in the rain (weather, "Wipers and droplets in the rain"):
 * every car's wipe its windscreen while it rains ({@link Wipers}, drawn on
 * the cars), each car at its own moment so they do not all wipe as one. A
 * wasted car's stop where they park.
 * <p>
 * From the helmet of a closed car the player's own are drawn as the car's
 * inside is: on a plane across the car beyond the dashboard, their places
 * given as slopes from the eyes, sweeping over the windscreen between its
 * pillars. There they wipe the drops off the screen as they pass
 * ({@link Lens#wipe}), and each wipe is heard, a recording of a blade over
 * wet glass, through the 3D sound.
 */
final class Wiping {

    /** Farther than this from the eye a car's wipers are not drawn: too thin to see. */
    static final float FAR = 4500F;
    /** From the helmet: the two pivots as slopes to the side, how far above the cowl they are, a blade's length at most. */
    static final float[] PIVOT_ASIDE = {-0.45F, 0.35F};
    static final float PIVOT_UP = 0.03F;
    static final float BLADE_MOST = 0.86F;
    static final float BLADE_OF_HEIGHT = 0.85F;
    static final float BLADE_HALF = 0.011F;
    /**
     * Degrees a blade is up from pointing right when parked, and at the top
     * of its wipe, seen from the helmet: past upright, so the left blade
     * clears the middle of the view and the two leave no strip between them.
     */
    static final float PARKED = 3F;
    static final float SWEPT = 110F;
    /** How far beyond the dashboard's plane the helmet's wipers are drawn, units. */
    static final float BEYOND_DASH = 8F;
    /** The slope from the eyes at the picture's upper and lower edge, and how far below level the helmet looks. */
    static final float HALF_PICTURE = 0.72F;
    static final float LOOK_DOWN = (float) Math.tan(Math.toRadians(3D));
    /** Each car's wipers are this many ticks behind the car before it. */
    static final int APART = 7;

    private Wipers[] wipers = new Wipers[0];
    private int cars;
    /** Each car's windscreen in the world after the last tick: its middle and which way it faces. */
    private float[] glass = new float[0];
    private boolean[] parked = new boolean[0];
    private int tick;
    private final float[] p = new float[3];
    private final float[] q = new float[3];
    private final float[] screen = new float[5];
    private final float[] before = new float[WeatherFeature.PLAYERS];
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];
    /** For the checks: cars with wipers, drops wiped off the screen and wipes heard since the race started. */
    int fitted;
    int wiped;
    int heard;

    /** A race starts in the rain: each car's wipers are found a place on its windscreen. */
    void start(RaceView r) {
        cars = r.cars;
        if (wipers.length < cars) {
            Wipers[] grown = new Wipers[cars];
            for (int c = 0; c < cars; c++) {
                grown[c] = c < wipers.length ? wipers[c] : new Wipers();
            }
            wipers = grown;
            glass = new float[cars * 6];
            parked = new boolean[cars];
        }
        fitted = 0;
        wiped = 0;
        heard = 0;
        tick = r.tick;
        for (int c = 0; c < cars; c++) {
            if (r.shape[c] != null && wipers[c].build(r.shape[c])) {
                fitted++;
            }
            parked[c] = false;
        }
        place(r);
    }

    /** No race in the rain: nothing is drawn. */
    void stop() {
        cars = 0;
    }

    /** How many ticks into its wipe car {@code c} is, the frame drawn {@code share} of the way from the tick before to the last. */
    private float into(int c, float share) {
        return parked[c] ? 0F : tick - 1 + share + c * APART;
    }

    private void place(RaceView r) {
        for (int c = 0; c < cars; c++) {
            Wipers w = wipers[c];
            if (!w.has) {
                continue;
            }
            r.toWorld(c, w.mid[0], w.mid[1], w.mid[2], p);
            r.toWorld(c, w.mid[0] + w.out[0], w.mid[1] + w.out[1], w.mid[2] + w.out[2], q);
            int at = c * 6;
            glass[at] = p[0];
            glass[at + 1] = p[1];
            glass[at + 2] = p[2];
            glass[at + 3] = q[0] - p[0];
            glass[at + 4] = q[1] - p[1];
            glass[at + 5] = q[2] - p[2];
        }
    }

    /**
     * One tick of rain: the wipers move on; each player seeing the race
     * from inside a closed car ({@code inside}) has the drops under their
     * blades wiped off {@code lens}, on a picture {@code aspect} times as
     * wide as high, and hears a wipe begin.
     */
    void tick(RaceView r, Lens[] lens, int players, boolean[] inside, float aspect) {
        if (r.cars != cars) {
            start(r);
        }
        for (int c = 0; c < players && c < cars; c++) {
            before[c] = Wipers.sweep(into(c, 1F));
        }
        tick = r.tick;
        for (int c = 0; c < cars; c++) {
            // a wasted car's wipers stop once they are parked
            parked[c] = r.wasted[c] && (parked[c] || (tick + c * APART) % Wipers.CYCLE == 0);
        }
        place(r);
        for (int c = 0; c < players && c < cars; c++) {
            if (!inside[c] || parked[c] || !HelmetCam.FEATURE.windscreen(c, screen)) {
                continue;
            }
            float now = Wipers.sweep(into(c, 1F));
            float cowl = screen[4];
            float length = bladeLength(cowl);
            for (int k = 0; k < 2; k++) {
                float px = 0.5F + PIVOT_ASIDE[k] / (2F * HALF_PICTURE * aspect);
                float py = 0.5F + (cowl - PIVOT_UP - LOOK_DOWN) / (2F * HALF_PICTURE);
                wiped += lens[c].wipe(px, py, length / (2F * HALF_PICTURE), PARKED + (SWEPT - PARKED) * before[c],
                        PARKED + (SWEPT - PARKED) * now, aspect);
            }
            if (c == 0 && (tick + c * APART) % Wipers.CYCLE == 0 && Features.hooks().sound3d()) {
                Sound3D.carSound(c, WeatherSounds.WIPE, 0.2F, 1.12F);
                heard++;
            }
        }
    }

    /** A helmet blade's length as a slope, under a cowl at slope {@code cowl}: most of the glass's height, never past the roof. */
    static float bladeLength(float cowl) {
        return Math.min(BLADE_MOST, BLADE_OF_HEIGHT * (cowl - PIVOT_UP - HelmetCam.GLASS_TOP));
    }

    /**
     * Queues the wipers of every car near enough whose windscreen faces
     * the eye, as items {@code base + car}; with {@code helmet} a player
     * (not -1) seeing the race from their helmet, theirs as item
     * {@code helmetItem + helmet} under the car's inside instead.
     */
    void queue(DrawQueue queue, int base, int helmet, int helmetItem) {
        float ex = queue.eyeX();
        float ey = queue.eyeY();
        float ez = queue.eyeZ();
        for (int c = 0; c < cars; c++) {
            if (!wipers[c].has) {
                continue;
            }
            if (c == helmet) {
                if (HelmetCam.FEATURE.closed(c)) {
                    queue.afterFirst(c, helmetItem + c);
                }
                continue;
            }
            int at = c * 6;
            float dx = ex - glass[at];
            float dy = ey - glass[at + 1];
            float dz = ez - glass[at + 2];
            if (dx * dx + dy * dy + dz * dz < FAR * FAR
                    && dx * glass[at + 3] + dy * glass[at + 4] + dz * glass[at + 5] > 0F) {
                queue.after(c, base + c);
            }
        }
    }

    /** Draws car {@code c}'s wipers on its windscreen, the frame {@code share} of the way between the ticks. */
    void draw(Canvas canvas, int c, float share) {
        if (c < 0 || c >= cars) {
            return;
        }
        int at = c * 6;
        canvas.color(canvas.fog(Wipers.RGB, glass[at], glass[at + 1], glass[at + 2]));
        wipers[c].draw(canvas, c, Wipers.sweep(into(c, share)));
    }

    /** Draws player {@code c}'s wipers as their helmet sees them: beyond the dashboard, over the windscreen. */
    void drawHelmet(Canvas canvas, int c, float share) {
        if (c < 0 || c >= cars) {
            return;
        }
        int at = c * 6;
        canvas.color(canvas.fog(Wipers.RGB, glass[at], glass[at + 1], glass[at + 2]));
        helmetBlades(canvas, c, Wipers.sweep(into(c, share)), screen, x, y, z);
    }

    /**
     * Player {@code c}'s wipers as their helmet sees them, {@code sweep}
     * through their wipe (0 parked to 1), in the canvas's colour: four
     * polygons on the car, or none if the helmet has no windscreen ahead.
     * The arrays are scratch: five numbers, and four corners each.
     */
    static int helmetBlades(Canvas canvas, int c, float sweep, float[] screen, float[] x, float[] y, float[] z) {
        if (!HelmetCam.FEATURE.windscreen(c, screen)) {
            return 0;
        }
        float ahead = screen[3] + BEYOND_DASH;
        float cowl = screen[4];
        float length = bladeLength(cowl);
        double a = Math.toRadians(PARKED + (SWEPT - PARKED) * sweep);
        float du = (float) Math.cos(a);
        float dv = (float) -Math.sin(a);
        float pu = -dv * BLADE_HALF;
        float pv = du * BLADE_HALF;
        for (int k = 0; k < 2; k++) {
            float u0 = PIVOT_ASIDE[k];
            float v0 = cowl - PIVOT_UP;
            corner(x, y, z, screen, 0, ahead, u0 + pu * 0.9F, v0 + pv * 0.9F);
            corner(x, y, z, screen, 1, ahead, u0 + du * length * 0.6F, v0 + dv * length * 0.6F);
            corner(x, y, z, screen, 2, ahead, u0 - pu * 0.9F, v0 - pv * 0.9F);
            canvas.onCar(c, x, y, z, 3);
            float from = 0.16F * length;
            corner(x, y, z, screen, 0, ahead, u0 + du * from + pu, v0 + dv * from + pv);
            corner(x, y, z, screen, 1, ahead, u0 + du * length + pu, v0 + dv * length + pv);
            corner(x, y, z, screen, 2, ahead, u0 + du * length - pu, v0 + dv * length - pv);
            corner(x, y, z, screen, 3, ahead, u0 + du * from - pu, v0 + dv * from - pv);
            canvas.onCar(c, x, y, z, 4);
        }
        return 4;
    }

    /** Corner {@code k} at slopes {@code (sx, sy)} from the eyes, on the plane {@code ahead} of them. */
    private static void corner(float[] x, float[] y, float[] z, float[] screen, int k, float ahead, float sx,
            float sy) {
        x[k] = screen[0] + sx * ahead;
        y[k] = screen[1] + sy * ahead;
        z[k] = screen[2] + ahead;
    }

    /** Car {@code c}'s wipers, for the checks. */
    Wipers of(int c) {
        return wipers[c];
    }

    /** How far through its wipe car {@code c} is after the last tick, 0 to 1, for the checks. */
    float sweep(int c) {
        return Wipers.sweep(into(c, 1F));
    }
}
