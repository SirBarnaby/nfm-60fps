package nfm.lit.features.realism;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.Wipers;

/**
 * What every car is fitted with (realism, "Every car has wipers and an
 * exhaust pipe"): two simple wipers on its windscreen, and exhaust pipes
 * where its sheet says its exhaust ends, unless its model has pipes of its
 * own (the sheet's {@code pipe=own}). A pipe is a six-sided steel tube
 * that widens into a brighter tip with a rim and a dark mouth, eight
 * polygons at most as seen from any one side. Found a place on the model
 * when a race starts (or the car is shown in the car select) and drawn as
 * a part of the car: among its own polygons, by how far from the eye
 * ({@link nfm.lit.features.DrawQueue#within}), and never before the
 * polygon it comes out of or stands on ({@link #on}).
 * <p>
 * Three kinds, by the sheet: pipes out of the tail, upright stacks behind
 * the cab, and side pipes along the sills that end ahead of the rear
 * wheels ({@code pipes=side}).
 */
final class Fitted {

    static final int BACK = 0;
    static final int UP = 1;
    static final int SIDE = 2;
    /** A pipe's colours before the stage's light: steel, and the dark of its mouth. */
    static final int STEEL = 0x8b8e94;
    static final int MOUTH = 0x0e0e10;
    /** Half a pipe's width, of the car's width; and its least and most. */
    static final float OF_WIDTH = 0.025F;
    static final float LEAST = 1.2F;
    static final float MOST = 4F;

    final Wipers wipers = new Wipers();
    /** Pipes drawn on the car: none if the model has its own. */
    int pipes;
    int kind;
    /** Half a pipe's width. */
    float half;
    /** Each pipe from where it leaves the car to its mouth, in the car's own numbers. */
    final float[] fromX = new float[2];
    final float[] fromY = new float[2];
    final float[] fromZ = new float[2];
    final float[] toX = new float[2];
    final float[] toY = new float[2];
    final float[] toZ = new float[2];
    /** The polygon of the car each pipe comes out of or stands on, or -1. */
    final int[] on = {-1, -1};
    /** The polygon the last {@link #behind} or {@link #under} found. */
    private int found = -1;

    private final float[] normal = new float[3];
    private final float[] middle = new float[3];
    private final float[] x = new float[6];
    private final float[] y = new float[6];
    private final float[] z = new float[6];

    /**
     * Finds car shape {@code s}'s windscreen for its wipers and a place for
     * its pipes; the mouths become where {@code parts} says its exhaust
     * ends, so the flames and pops come out of them.
     */
    void build(CarShape s, CarParts parts) {
        pipes = 0;
        wipers.has = false;
        if (s == null || s.planes == 0) {
            return;
        }
        wipers.build(s);
        CarSheet sheet = parts.sheet;
        if ("own".equals(sheet.trait("pipe", "fitted"))) {
            return;
        }
        half = Math.max(LEAST, Math.min(MOST, OF_WIDTH * 2F * parts.halfWidth));
        if (sheet.exhausts == CarSheet.STACKS) {
            stacks(s, parts);
        } else if ("side".equals(sheet.trait("pipes", "back"))) {
            sides(s, parts);
        } else {
            tail(s, parts, sheet.exhausts);
        }
        for (int p = 0; p < pipes; p++) {
            parts.pipeX[p] = toX[p];
            parts.pipeY[p] = toY[p];
            parts.pipeZ[p] = toZ[p];
        }
    }

    /** What the car is fitted with, for the log: its wipers and its pipes. */
    String describe(CarParts parts) {
        String pipe = pipes == 0 ? "its model's own pipes"
                : kind == UP ? "2 stacks behind the cab"
                : kind == SIDE ? "2 side pipes along the sills"
                : pipes + (pipes == 1 ? " pipe" : " pipes") + " out of the tail";
        return (wipers.has ? "wipers on its windscreen" : "no windscreen to carry wipers") + ", " + pipe;
    }

    /** Pipes out of the tail: from the body's rear face where the exhaust ends, three widths out. */
    private void tail(CarShape s, CarParts parts, int count) {
        kind = BACK;
        pipes = Math.max(1, Math.min(2, count));
        for (int p = 0; p < pipes; p++) {
            float px = parts.pipeX[p];
            // low on the tail; higher if the tail is not there that low
            float py = parts.bottom - half * 1.2F;
            float hit = Float.NaN;
            on[p] = -1;
            for (int tries = 0; tries < 8; tries++) {
                hit = behind(s, px, py);
                if (!Float.isNaN(hit) && hit <= parts.rear + 0.25F * parts.length) {
                    on[p] = found;
                    break;
                }
                hit = Float.NaN;
                py -= half;
            }
            if (Float.isNaN(hit)) {
                py = parts.bottom - half;
                hit = parts.rear;
            }
            fromX[p] = px;
            fromY[p] = py;
            fromZ[p] = hit + 0.3F * half;
            toX[p] = px;
            toY[p] = py;
            toZ[p] = hit - 4.5F * half;
        }
    }

    /** Stacks: upright behind the cab, from what they stand on to a little above the roof. */
    private void stacks(CarShape s, CarParts parts) {
        kind = UP;
        pipes = 2;
        // behind the cab: the rearmost of what stands in the top fifth of the body
        float cabBack = parts.front;
        for (int i = 0; i < s.planes; i++) {
            if (s.wheel[i] >= 0) {
                continue;
            }
            for (int v = s.first[i]; v < s.first[i] + s.count[i]; v++) {
                if (s.y[v] < parts.top + 0.2F * parts.height) {
                    cabBack = Math.min(cabBack, s.z[v]);
                }
            }
        }
        float pz = Math.max(parts.rear + half, cabBack - 1.5F * half);
        for (int p = 0; p < 2; p++) {
            float px = (p == 0 ? -1F : 1F) * parts.halfWidth * 0.6F;
            float stand = under(s, px, pz);
            on[p] = Float.isNaN(stand) ? -1 : found;
            if (Float.isNaN(stand)) {
                stand = parts.bottom;
            }
            fromX[p] = px;
            fromY[p] = stand + 0.3F * half;
            fromZ[p] = pz;
            toX[p] = px;
            toY[p] = Math.min(stand - 4F * half, parts.top - 2F * half);
            toZ[p] = pz;
        }
    }

    /** Side pipes: along each sill, ending just ahead of the rear wheel. */
    private void sides(CarShape s, CarParts parts) {
        kind = SIDE;
        pipes = 2;
        float wheel = parts.rear + 0.25F * parts.length;
        for (int w = 0; w < s.hubZ.length; w++) {
            if (s.hubZ[w] < 0F) {
                wheel = Math.max(parts.rear, s.hubZ[w] + s.wheelRadius[w]);
            }
        }
        for (int p = 0; p < 2; p++) {
            float px = (p == 0 ? -1F : 1F) * (parts.halfWidth + 0.4F * half);
            on[p] = -1;
            fromX[p] = px;
            fromY[p] = parts.bottom - half;
            fromZ[p] = wheel + 9F * half;
            toX[p] = px;
            toY[p] = parts.bottom - half;
            toZ[p] = wheel + 1.5F * half;
        }
    }

    /** Where a line along the car through {@code (px, py)} first meets its body from behind: the smallest z, or NaN. */
    private float behind(CarShape s, float px, float py) {
        float best = Float.NaN;
        for (int i = 0; i < s.planes; i++) {
            if (s.wheel[i] >= 0 || s.count[i] < 3 || s.normal(i, normal) <= 0F || Math.abs(normal[2]) < 0.05F) {
                continue;
            }
            if (!inside(s, i, px, py, false)) {
                continue;
            }
            s.middle(i, middle);
            float hit = middle[2] - (normal[0] * (px - middle[0]) + normal[1] * (py - middle[1])) / normal[2];
            if (Float.isNaN(best) || hit < best) {
                best = hit;
                found = i;
            }
        }
        return best;
    }

    /** What stands highest at {@code (px, pz)}: the smallest y of the body there, or NaN. */
    private float under(CarShape s, float px, float pz) {
        float best = Float.NaN;
        for (int i = 0; i < s.planes; i++) {
            if (s.wheel[i] >= 0 || s.count[i] < 3 || s.normal(i, normal) <= 0F || Math.abs(normal[1]) < 0.05F) {
                continue;
            }
            if (!inside(s, i, px, pz, true)) {
                continue;
            }
            s.middle(i, middle);
            float hit = middle[1] - (normal[0] * (px - middle[0]) + normal[2] * (pz - middle[2])) / normal[1];
            if (Float.isNaN(best) || hit < best) {
                best = hit;
                found = i;
            }
        }
        return best;
    }

    /** True if {@code (a, b)} is in polygon {@code i} seen along the car (x and y), or with {@code plan} from above (x and z). */
    private static boolean inside(CarShape s, int i, float a, float b, boolean plan) {
        boolean in = false;
        int n = s.count[i];
        float[] other = plan ? s.z : s.y;
        for (int k = 0, j = n - 1; k < n; j = k++) {
            int p = s.first[i] + k;
            int q = s.first[i] + j;
            if (other[p] > b != other[q] > b
                    && a < (s.x[q] - s.x[p]) * (b - other[p]) / (other[q] - other[p]) + s.x[p]) {
                in = !in;
            }
        }
        return in;
    }

    /** The middle of pipe {@code p}, in the car's own numbers, into {@code out}. */
    void middle(int p, float[] out) {
        out[0] = (fromX[p] + toX[p]) / 2F;
        out[1] = (fromY[p] + toY[p]) / 2F;
        out[2] = (fromZ[p] + toZ[p]) / 2F;
    }

    /** How much wider than the tube the tip's mouth is. */
    static final float FLARE = 1.4F;
    private static final float COS30 = 0.8660254F;

    /**
     * Draws pipe {@code p} on car {@code car} seen from the eye at
     * {@code (ex, ey, ez)} in the car's own numbers: a six-sided tube in
     * {@code steel}, its last stretch widening into a brighter tip, the
     * tip's rim and its dark mouth in {@code mouth}; only the sides turned
     * to the eye, each lit by how it faces up. Returns the polygons drawn,
     * eight at most.
     */
    int draw(Canvas canvas, int car, int p, float ex, float ey, float ez, int steel, int mouth) {
        if (p < 0 || p >= pipes) {
            return 0;
        }
        float ax = toX[p] - fromX[p];
        float ay = toY[p] - fromY[p];
        float az = toZ[p] - fromZ[p];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 0.01F) {
            return 0;
        }
        ax /= len;
        ay /= len;
        az /= len;
        // where the tip starts: its last stretch, two widths long at most
        float tip = Math.min(0.45F * len, 2.2F * half);
        float tx = toX[p] - ax * tip;
        float ty = toY[p] - ay * tip;
        float tz = toZ[p] - az * tip;
        int dull = RealismFeature.scale(steel, 0.82F);
        int bright = RealismFeature.scale(steel, 1.35F);
        boolean fromMouth = ax * (ex - tx) + ay * (ey - ty) + az * (ez - tz) > 0F;
        int drawn = 0;
        // the farther of the two stretches first
        if (!fromMouth) {
            drawn += tube(canvas, car, p, tx, ty, tz, half, toX[p], toY[p], toZ[p], half * FLARE, ex, ey, ez, bright);
        }
        drawn += tube(canvas, car, p, fromX[p], fromY[p], fromZ[p], half, tx, ty, tz, half, ex, ey, ez, dull);
        if (fromMouth) {
            drawn += tube(canvas, car, p, tx, ty, tz, half, toX[p], toY[p], toZ[p], half * FLARE, ex, ey, ez, bright);
        }
        if (ax * (ex - toX[p]) + ay * (ey - toY[p]) + az * (ez - toZ[p]) > 0F) {
            ring(p, toX[p], toY[p], toZ[p], half * FLARE);
            canvas.color(RealismFeature.scale(steel, 1.15F));
            canvas.onCar(car, x, y, z, 6);
            ring(p, toX[p], toY[p], toZ[p], half * FLARE * 0.72F);
            canvas.color(mouth);
            canvas.onCar(car, x, y, z, 6);
            drawn += 2;
        }
        return drawn;
    }

    /** The two directions across pipe {@code p}: the car's right, and its down (its ahead for a stack). */
    private float acrossY() {
        return kind == UP ? 0F : 1F;
    }

    /** Six corners of a ring of radius {@code r} about {@code (cx, cy, cz)} across the pipe, into the corners. */
    private void ring(int p, float cx, float cy, float cz, float r) {
        float vy = acrossY();
        float vz = 1F - vy;
        for (int k = 0; k < 6; k++) {
            double a = Math.PI / 3D * k;
            float c = (float) Math.cos(a) * r;
            float d = (float) Math.sin(a) * r;
            corner(k, cx + c, cy + d * vy, cz + d * vz);
        }
    }

    /**
     * The sides turned to the eye of a six-sided tube from
     * {@code (x0, y0, z0)}, {@code r0} thick, to {@code (x1, y1, z1)},
     * {@code r1} thick; returns how many were drawn (three at most).
     */
    private int tube(Canvas canvas, int car, int p, float x0, float y0, float z0, float r0, float x1, float y1,
            float z1, float r1, float ex, float ey, float ez, int rgb) {
        float vy = acrossY();
        float vz = 1F - vy;
        int drawn = 0;
        for (int k = 0; k < 6; k++) {
            double mid = Math.PI / 3D * k + Math.PI / 6D;
            float nx = (float) Math.cos(mid);
            float nd = (float) Math.sin(mid);
            float ny = nd * vy;
            float nz = nd * vz;
            float reach = (r0 + r1) / 2F * COS30;
            float mx = (x0 + x1) / 2F + nx * reach;
            float my = (y0 + y1) / 2F + ny * reach;
            float mz = (z0 + z1) / 2F + nz * reach;
            if (nx * (ex - mx) + ny * (ey - my) + nz * (ez - mz) <= 0F) {
                continue;
            }
            double a = Math.PI / 3D * k;
            double b = Math.PI / 3D * (k + 1);
            float ca = (float) Math.cos(a);
            float sa = (float) Math.sin(a);
            float cb = (float) Math.cos(b);
            float sb = (float) Math.sin(b);
            corner(0, x0 + ca * r0, y0 + sa * r0 * vy, z0 + sa * r0 * vz);
            corner(1, x0 + cb * r0, y0 + sb * r0 * vy, z0 + sb * r0 * vz);
            corner(2, x1 + cb * r1, y1 + sb * r1 * vy, z1 + sb * r1 * vz);
            corner(3, x1 + ca * r1, y1 + sa * r1 * vy, z1 + sa * r1 * vz);
            // lit from above (y grows downwards), a little from one side and from behind
            canvas.color(RealismFeature.scale(rgb, 0.74F - 0.3F * ny + 0.07F * nx - 0.08F * nz));
            canvas.onCar(car, x, y, z, 4);
            drawn++;
        }
        return drawn;
    }

    private void corner(int k, float cx, float cy, float cz) {
        x[k] = cx;
        y[k] = cy;
        z[k] = cz;
    }
}
