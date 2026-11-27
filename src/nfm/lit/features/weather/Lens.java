package nfm.lit.features.weather;

import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;

/**
 * The drops on one player's screen in the rain (weather, "Wipers and
 * droplets in the rain"): they land, sit, and some run, pushed outwards
 * from the middle by the air at speed and down by their weight, then dry
 * away. Following close behind another car throws its spray at the screen:
 * many more drops, most of them fine. In snow a few flakes land and melt.
 * <p>
 * A drop is a few flat polygons fixed on the picture: a pale see-through
 * body, a bright glint at its upper left, a darker rim below.
 */
final class Lens {

    static final int MAX = 128;
    /** Drops landing a tick in clear air standing still, added at full speed, and added right behind another car. */
    static final float RAIN_RATE = 0.4F;
    static final float RAIN_SPEED_RATE = 0.6F;
    static final float RAIN_TAIL_RATE = 2.6F;
    static final float SNOW_RATE = 0.16F;
    static final float SNOW_SPEED_RATE = 0.2F;
    static final float SNOW_TAIL_RATE = 0.6F;
    /** The middle the air pushes drops away from, shares of the picture. */
    private static final float MIDDLE_X = 0.5F;
    private static final float MIDDLE_Y = 0.45F;

    private final float[] x = new float[MAX];
    private final float[] y = new float[MAX];
    private final float[] r = new float[MAX];
    private final float[] age = new float[MAX];
    private final float[] life = new float[MAX];
    /** How readily the drop runs: 0 for one that sits where it landed. */
    private final float[] runs = new float[MAX];
    private final float[] vx = new float[MAX];
    private final float[] vy = new float[MAX];
    private final boolean[] fine = new boolean[MAX];
    private final boolean[] alive = new boolean[MAX];
    private final Dice dice = new Dice(0L, "weather lens");
    private float owed;
    private int oldest;
    int count;
    private boolean snow;

    private final float[] px = new float[8];
    private final float[] py = new float[8];

    void reset(long seed, int player, boolean snowing) {
        dice.reset(seed + player, "weather lens");
        java.util.Arrays.fill(alive, false);
        count = 0;
        owed = 0F;
        oldest = 0;
        snow = snowing;
    }

    /**
     * One tick: the player at {@code speed} of their car's top speed (0 to
     * 1), {@code tail} close behind another car (0 not, 1 right behind).
     */
    void tick(float speed, float tail) {
        owed += snow ? SNOW_RATE + SNOW_SPEED_RATE * speed + SNOW_TAIL_RATE * tail
                : RAIN_RATE + RAIN_SPEED_RATE * speed + RAIN_TAIL_RATE * tail;
        while (owed >= 1F) {
            owed -= 1F;
            land(tail);
        }
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) {
                continue;
            }
            age[i]++;
            if (runs[i] > 0F) {
                // the air at speed pushes it out from the middle; its weight pulls it down
                float push = 0.011F * speed * runs[i];
                vx[i] += ((x[i] - MIDDLE_X) * push - vx[i]) * 0.3F;
                vy[i] += ((y[i] - MIDDLE_Y) * push + 0.0035F * runs[i] * r[i] / 0.02F - vy[i]) * 0.3F;
                x[i] += vx[i];
                y[i] += vy[i];
            }
            if (age[i] >= life[i] || x[i] < -0.05F || x[i] > 1.05F || y[i] < -0.05F || y[i] > 1.05F) {
                alive[i] = false;
                count--;
            }
        }
    }

    /** A splash of water on the screen (through a puddle): {@code drops} large drops at once. */
    void splash(int drops) {
        for (int k = 0; k < drops && !snow; k++) {
            land(0F);
        }
    }

    private void land(float tail) {
        int i = -1;
        for (int k = 0; k < MAX; k++) {
            if (!alive[k]) {
                i = k;
                break;
            }
        }
        if (i < 0) {
            i = oldest;
            oldest = (oldest + 1) % MAX;
        } else {
            count++;
        }
        alive[i] = true;
        x[i] = dice.between(0.02F, 0.98F);
        y[i] = dice.between(0.02F, 0.98F);
        age[i] = 0F;
        vx[i] = 0F;
        vy[i] = 0F;
        // spray from the car ahead is mostly fine drops that dry at once
        fine[i] = dice.next() < 0.3F + 0.5F * tail;
        if (snow) {
            r[i] = dice.between(0.005F, 0.012F);
            life[i] = dice.between(14F, 30F);
            runs[i] = 0F;
        } else if (fine[i]) {
            r[i] = dice.between(0.0035F, 0.007F);
            life[i] = dice.between(9F, 22F);
            runs[i] = 0F;
        } else {
            r[i] = dice.between(0.009F, 0.021F);
            life[i] = dice.between(35F, 95F);
            runs[i] = dice.next() < 0.45F ? dice.between(0.4F, 1F) : 0F;
        }
    }

    /**
     * Draws the drops on a picture {@code aspect} times as wide as high:
     * bodies in {@code body}, glints in {@code glint}, rims in {@code rim}
     * (0xRRGGBB).
     */
    void draw(Canvas canvas, float aspect, int body, int glint, int rim) {
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) {
                continue;
            }
            float left = life[i] - age[i];
            float fade = Math.min(1F, left / 6F);
            float size = snow ? r[i] * (1F - age[i] / life[i] * 0.7F) : r[i] * Math.min(1F, (age[i] + 1F) / 2F);
            float rx = size / aspect;
            if (snow) {
                canvas.color(glint);
                canvas.dither(0.85F * fade);
                round(x[i], y[i], rx, size, 6, 0F);
                canvas.screen(px, py, 6);
                continue;
            }
            // a running drop is drawn out along its way
            float stretch = runs[i] > 0F ? Math.min(1.2F, (Math.abs(vx[i]) * aspect + Math.abs(vy[i])) / size) : 0F;
            canvas.color(body);
            canvas.dither((fine[i] ? 0.42F : 0.3F) * fade);
            round(x[i], y[i], rx, size, fine[i] ? 6 : 8, stretch);
            canvas.screen(px, py, fine[i] ? 6 : 8);
            if (fine[i]) {
                continue;
            }
            // the rim: a dark sliver along the lower edge
            canvas.color(rim);
            canvas.dither(0.34F * fade);
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(25D + k * 43.3D);
                px[k] = x[i] + (float) Math.cos(a) * rx * 0.98F;
                py[k] = y[i] + (float) Math.sin(a) * size * 0.98F;
                px[7 - k] = x[i] + (float) Math.cos(a) * rx * 0.66F;
                py[7 - k] = y[i] + (float) Math.sin(a) * size * 0.6F;
            }
            canvas.screen(px, py, 8);
            // the glint: the sky's light, upper left
            canvas.color(glint);
            canvas.dither(0.8F * fade);
            float gx = x[i] - rx * 0.36F;
            float gy = y[i] - size * 0.38F;
            round(gx, gy, rx * 0.27F, size * 0.22F, 4, 0F);
            canvas.screen(px, py, 4);
        }
    }

    /** A rounded shape of {@code n} corners about {@code (mx, my)}, its trailing side drawn out by {@code stretch}. */
    private void round(float mx, float my, float rx, float ry, int n, float stretch) {
        for (int k = 0; k < n; k++) {
            double a = 2D * Math.PI * (k + 0.5D) / n;
            float c = (float) Math.cos(a);
            float s = (float) Math.sin(a);
            px[k] = mx + c * rx;
            py[k] = my + s * ry * (s < 0F ? 1F + stretch : 1F);
        }
    }
}
