package nfm.lit.features.realism;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.Wipers;

/**
 * What every car is fitted with (realism, "Every car has wipers and an
 * exhaust pipe"): two simple wipers on its windscreen, and exhaust pipes
 * where its sheet says its exhaust ends, unless its model has pipes of its
 * own (the sheet's {@code pipe=own}). A pipe is a square tube with a dark
 * mouth, five polygons. Found a place on the model when a race starts (or
 * the car is shown in the car select) and drawn on the car.
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

    private final float[] normal = new float[3];
    private final float[] middle = new float[3];
    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];

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
            for (int tries = 0; tries < 8; tries++) {
                hit = behind(s, px, py);
                if (!Float.isNaN(hit) && hit <= parts.rear + 0.25F * parts.length) {
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
            toZ[p] = hit - 3F * half;
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

    /**
     * True if pipe {@code p} is drawn over the car, seen from the eye at
     * {@code (ex, ey, ez)} in the car's own numbers: from behind where it
     * comes out. Else the car hides what of it the car is in front of, and
     * it is drawn under the car.
     */
    boolean over(int p, float ex, float ey, float ez) {
        if (kind == SIDE) {
            return fromX[p] < 0F ? ex < fromX[p] : ex > fromX[p];
        }
        return ez < fromZ[p];
    }

    /**
     * Draws pipe {@code p} on car {@code car} seen from the eye at
     * {@code (ex, ey, ez)} in the car's own numbers: the sides turned to
     * the eye in {@code steel}, lighter above and darker below, and its
     * mouth in {@code mouth}. Returns the polygons drawn, five at most.
     */
    int draw(Canvas canvas, int car, int p, float ex, float ey, float ez, int steel, int mouth) {
        if (p < 0 || p >= pipes) {
            return 0;
        }
        int drawn = 0;
        boolean up = kind == UP;
        for (int face = 0; face < 4; face++) {
            // the tube's four sides: left, right, and the two across them (above and below, or ahead and behind)
            float nx = face == 0 ? -1F : face == 1 ? 1F : 0F;
            float ny = !up && face == 2 ? -1F : !up && face == 3 ? 1F : 0F;
            float nz = up && face == 2 ? 1F : up && face == 3 ? -1F : 0F;
            float mx = (fromX[p] + toX[p]) / 2F + nx * half;
            float my = (fromY[p] + toY[p]) / 2F + ny * half;
            float mz = (fromZ[p] + toZ[p]) / 2F + nz * half;
            if (nx * (ex - mx) + ny * (ey - my) + nz * (ez - mz) <= 0F) {
                continue;
            }
            // across the face, square to the tube and to the face's normal
            float wx = face >= 2 ? half : 0F;
            float wy = !up && face < 2 ? half : 0F;
            float wz = up && face < 2 ? half : 0F;
            corner(0, fromX[p] + nx * half - wx, fromY[p] + ny * half - wy, fromZ[p] + nz * half - wz);
            corner(1, fromX[p] + nx * half + wx, fromY[p] + ny * half + wy, fromZ[p] + nz * half + wz);
            corner(2, toX[p] + nx * half + wx, toY[p] + ny * half + wy, toZ[p] + nz * half + wz);
            corner(3, toX[p] + nx * half - wx, toY[p] + ny * half - wy, toZ[p] + nz * half - wz);
            canvas.color(RealismFeature.scale(steel, 0.8F - 0.27F * ny + 0.07F * nx + 0.1F * nz));
            canvas.onCar(car, x, y, z, 4);
            drawn++;
        }
        float ax = toX[p] - fromX[p];
        float ay = toY[p] - fromY[p];
        float az = toZ[p] - fromZ[p];
        if (ax * (ex - toX[p]) + ay * (ey - toY[p]) + az * (ez - toZ[p]) > 0F) {
            float ux = half;
            float vy = up ? 0F : half;
            float vz = up ? half : 0F;
            corner(0, toX[p] - ux, toY[p] - vy, toZ[p] - vz);
            corner(1, toX[p] + ux, toY[p] - vy, toZ[p] - vz);
            corner(2, toX[p] + ux, toY[p] + vy, toZ[p] + vz);
            corner(3, toX[p] - ux, toY[p] + vy, toZ[p] + vz);
            canvas.color(mouth);
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
