package nfm.lit.features.weather;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.RaceView;

/**
 * The wet road as a mirror (weather, "Rain and snow have more oomph"):
 * under each car near the eye its colour lies smeared on the road towards
 * whoever looks, and its lights draw long streaks on the wet: red from its
 * tail seen from behind (longer and glowing while it brakes), pale from
 * its headlights seen from ahead. All on the ground, so the cars and
 * everything that stands cover it. Only a look.
 */
final class Reflections {

    /** Farther than this from the eye a car's reflection is not drawn. */
    static final float SEEN = 3400F;
    private static final int TAIL = 0xff2a1a;
    private static final int HEAD = 0xfff0c4;

    private int cars;
    private float[] x = new float[0];
    private float[] y = new float[0];
    private float[] z = new float[0];
    private float[] headX = new float[0];
    private float[] headZ = new float[0];
    private float[] vx = new float[0];
    private float[] vz = new float[0];
    private float[] halfWide = new float[0];
    private float[] front = new float[0];
    private float[] back = new float[0];
    private float[] high = new float[0];
    private int[] rgb = new int[0];
    private boolean[] braking = new boolean[0];
    private boolean[] shows = new boolean[0];
    private int roadRgb;

    private float eyeX;
    private float eyeZ;
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];

    /** Drawn in the last frame: cars mirrored, and streaks of light. */
    int drawnCars;
    int drawnLights;
    /** The last frame's streaks glowed (a car braking). */
    boolean glowed;

    /** A race starts in the rain: each car's size and its body's colour. */
    void start(RaceView r) {
        cars = r.cars;
        if (x.length < cars) {
            x = new float[cars];
            y = new float[cars];
            z = new float[cars];
            headX = new float[cars];
            headZ = new float[cars];
            vx = new float[cars];
            vz = new float[cars];
            halfWide = new float[cars];
            front = new float[cars];
            back = new float[cars];
            high = new float[cars];
            rgb = new int[cars];
            braking = new boolean[cars];
            shows = new boolean[cars];
        }
        roadRgb = Air.mix(r.skyColor, 0x000000, 0.55F);
        float[] normal = new float[3];
        for (int c = 0; c < cars; c++) {
            CarShape s = r.shape[c];
            boolean sized = s != null && s.maxX > s.minX && s.maxZ > s.minZ;
            halfWide[c] = sized ? (s.maxX - s.minX) / 2F : 40F;
            front[c] = sized ? s.maxZ : 80F;
            back[c] = sized ? s.minZ : -80F;
            high[c] = sized ? Math.max(40F, s.maxY - s.minY) : 60F;
            // the body's colour: the one most of its panels have, by their size
            int best = 0x808890;
            float most = 0F;
            for (int i = 0; s != null && i < s.planes; i++) {
                if (s.glass[i] || s.wheel[i] >= 0 || s.light[i] != 0 || s.count[i] < 3) {
                    continue;
                }
                float area = 0F;
                for (int j = 0; j < s.planes; j++) {
                    if (s.rgb[j] == s.rgb[i] && !s.glass[j] && s.wheel[j] < 0) {
                        area += s.normal(j, normal);
                    }
                }
                if (area > most) {
                    most = area;
                    best = s.rgb[i];
                }
                if (i > 40) {
                    break; // the first panels are the body's: enough to know its colour
                }
            }
            rgb[c] = best;
            shows[c] = false;
        }
    }

    /** One tick: where each car is, and whether it brakes. */
    void tick(RaceView r) {
        if (r.cars != cars) {
            start(r);
        }
        for (int c = 0; c < cars; c++) {
            float sum = 0F;
            int n = 0;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                int at = c * RaceView.WHEELS + w;
                if (r.contact[at] && !r.onRamp[at]) {
                    sum += r.wheelY[at];
                    n++;
                }
            }
            shows[c] = n >= 2 && !r.wasted[c];
            if (!shows[c]) {
                continue;
            }
            double yaw = Math.toRadians(r.xz[c]);
            x[c] = r.x[c];
            y[c] = sum / n;
            z[c] = r.z[c];
            headX[c] = (float) -Math.sin(yaw);
            headZ[c] = (float) Math.cos(yaw);
            vx[c] = r.vx[c];
            vz[c] = r.vz[c];
            braking[c] = (r.down[c] || r.handbrake[c]) && r.groundSpeed(c) > 3F;
        }
    }

    /** Queues the reflections as one ground item, if any car is near enough. */
    void queue(DrawQueue queue, int item) {
        eyeX = queue.eyeX();
        eyeZ = queue.eyeZ();
        drawnCars = 0;
        drawnLights = 0;
        glowed = false;
        for (int c = 0; c < cars; c++) {
            if (shows[c] && near(c)) {
                queue.ground(item);
                return;
            }
        }
    }

    private boolean near(int c) {
        float dx = x[c] - eyeX;
        float dz = z[c] - eyeZ;
        return dx * dx + dz * dz < SEEN * SEEN;
    }

    void draw(Canvas canvas) {
        for (int c = 0; c < cars; c++) {
            if (shows[c] && near(c)) {
                car(canvas, c);
            }
        }
        canvas.glow(0F);
        canvas.dither(1F);
    }

    private void car(Canvas canvas, int c) {
        // towards the eye, level, and across that
        float ex = eyeX - x[c];
        float ez = eyeZ - z[c];
        float e = (float) Math.sqrt(ex * ex + ez * ez);
        if (e < 60F) {
            return;
        }
        ex /= e;
        ez /= e;
        float ax = ez;
        float az = -ex;
        // the car's footprint seen from there: how far it reaches towards the eye, and from where to where across
        float nearest = -Float.MAX_VALUE;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            float lx = (k & 1) == 0 ? -halfWide[c] : halfWide[c];
            float lz = k < 2 ? front[c] : back[c];
            float wx = lx * headZ[c] + lz * headX[c];
            float wz = lx * -headX[c] + lz * headZ[c];
            nearest = Math.max(nearest, wx * ex + wz * ez);
            float across = wx * ax + wz * az;
            left = Math.min(left, across);
            right = Math.max(right, across);
        }
        float yy = y[c] - 0.6F;
        float fade = 1F - e / SEEN;
        int smear = canvas.fog(Air.mix(rgb[c], roadRgb, 0.5F), x[c], yy, z[c]);
        canvas.glow(0F);
        canvas.color(smear);
        // the car upside down on the road: strongest right under it, thinning towards the eye
        float reach = high[c] * 1.5F;
        for (int band = 0; band < 2; band++) {
            float from = band == 0 ? nearest - 30F : nearest + reach * 0.5F;
            float to = band == 0 ? nearest + reach * 0.5F : nearest + reach;
            float pinch = band == 0 ? 1F : 0.82F;
            float tip = band == 0 ? 0.82F : 0.55F;
            canvas.dither((band == 0 ? 0.36F : 0.18F) * fade);
            corner(0, c, ex, ez, ax, az, from, left * pinch, yy);
            corner(1, c, ex, ez, ax, az, from, right * pinch, yy);
            corner(2, c, ex, ez, ax, az, to, right * tip, yy);
            corner(3, c, ex, ez, ax, az, to, left * tip, yy);
            canvas.moving(cx, cy, cz, vx[c], 0F, vz[c], 4);
        }
        drawnCars++;
        // its lights: the tail's from behind, the headlights' from ahead
        float facing = ex * headX[c] + ez * headZ[c];
        if (Math.abs(facing) < 0.2F) {
            return;
        }
        boolean tail = facing < 0F;
        float lz = tail ? back[c] : front[c];
        float length = tail ? braking[c] ? 300F : 170F : 240F;
        boolean glows = tail && braking[c];
        canvas.color(canvas.fog(tail ? TAIL : HEAD, x[c], yy, z[c]));
        canvas.glow(glows ? 0.8F : tail ? 0.2F : 0.35F);
        canvas.dither((glows ? 0.7F : tail ? 0.42F : 0.34F) * fade);
        glowed |= glows;
        for (int side = -1; side <= 1; side += 2) {
            float lx = side * halfWide[c] * 0.66F;
            float wx = lx * headZ[c] + lz * headX[c];
            float wz = lx * -headX[c] + lz * headZ[c];
            float along = wx * ex + wz * ez;
            float across = wx * ax + wz * az;
            float wide = halfWide[c] * 0.2F;
            corner(0, c, ex, ez, ax, az, along - 6F, across - wide, yy);
            corner(1, c, ex, ez, ax, az, along - 6F, across + wide, yy);
            corner(2, c, ex, ez, ax, az, along + length, across + wide * 0.4F, yy);
            corner(3, c, ex, ez, ax, az, along + length, across - wide * 0.4F, yy);
            canvas.moving(cx, cy, cz, vx[c], 0F, vz[c], 4);
            drawnLights++;
        }
    }

    private void corner(int k, int c, float ex, float ez, float ax, float az, float along, float across, float yy) {
        cx[k] = x[c] + ex * along + ax * across;
        cy[k] = yy;
        cz[k] = z[c] + ez * along + az * across;
    }
}
