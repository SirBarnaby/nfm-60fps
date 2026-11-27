package nfm.lit;

import fallk.logmaster.HLogger;
import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;

/**
 * A car model as the extras see it ({@link CarShape}): made from the game's
 * model for every race car and the car select's, and read straight from the
 * game's files, with no race and no window, for the extras' checks that
 * look at each real car (every one has wipers and an exhaust pipe). Read
 * through {@link GameFiles}, as the game reads them. It touches nothing of
 * the game but the model.
 */
public final class ModelShapes {

    private ModelShapes() {
    }

    /** Car model {@code model}'s shape and how far its wheels reach down, or null (logged) if it cannot be read. */
    public static Loaded car(String model) {
        try {
            for (GameFiles.Entry entry : GameFiles.archive("models")) {
                if (entry.name().startsWith(model + ".")) {
                    ContO o = new ContO(entry.data(), new Trackers());
                    CarShape shape = new CarShape();
                    shape(shape, o);
                    return new Loaded(shape, o.grat);
                }
            }
            HLogger.warn("[extras] no model " + model + " in the game's files");
        } catch (java.io.IOException | RuntimeException e) {
            HLogger.warn("[extras] model " + model + " could not be read: " + e);
        }
        return null;
    }

    /** Car model {@code o}'s polygons into {@code s}: corners, colours, glass, lights, wheels; the body's extent. */
    static void shape(CarShape s, ContO o) {
        int corners = 0;
        for (int i = 0; i < o.npl; i++) {
            corners += o.p[i].n;
        }
        s.fit(o.npl, corners);
        float[] sumX = new float[RaceView.WHEELS];
        int[] pointsOf = new int[RaceView.WHEELS];
        float[] lowX = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] highX = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        java.util.Arrays.fill(s.wheelRadius, 0F);
        java.util.Arrays.fill(s.steerX, 0);
        s.minX = s.minY = s.minZ = Float.MAX_VALUE;
        s.maxX = s.maxY = s.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < o.npl; i++) {
            Plane p = o.p[i];
            s.first[i] = at;
            s.count[i] = p.n;
            s.rgb[i] = (p.c[0] & 0xff) << 16 | (p.c[1] & 0xff) << 8 | p.c[2] & 0xff;
            s.glass[i] = p.glass;
            s.light[i] = p.light;
            int wheel = p.wz != 0 ? nearestWheel(o, p) : -1;
            s.wheel[i] = wheel;
            for (int k = 0; k < p.n; k++, at++) {
                s.x[at] = p.ox[k];
                s.y[at] = p.oy[k];
                s.z[at] = p.oz[k];
                if (wheel >= 0) {
                    sumX[wheel] += p.ox[k];
                    pointsOf[wheel]++;
                    lowX[wheel] = Math.min(lowX[wheel], p.ox[k]);
                    highX[wheel] = Math.max(highX[wheel], p.ox[k]);
                    s.hubY[wheel] = p.wy;
                    s.hubZ[wheel] = p.wz;
                    s.steerX[wheel] = p.wx;
                    s.wheelRadius[wheel] = Math.max(s.wheelRadius[wheel],
                            (float) Math.hypot(p.oy[k] - p.wy, p.oz[k] - p.wz));
                } else {
                    s.minX = Math.min(s.minX, p.ox[k]);
                    s.maxX = Math.max(s.maxX, p.ox[k]);
                    s.minY = Math.min(s.minY, p.oy[k]);
                    s.maxY = Math.max(s.maxY, p.oy[k]);
                    s.minZ = Math.min(s.minZ, p.oz[k]);
                    s.maxZ = Math.max(s.maxZ, p.oz[k]);
                }
            }
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            s.hubX[w] = pointsOf[w] > 0 ? sumX[w] / pointsOf[w] : o.keyx[w];
            s.wheelHalfWidth[w] = pointsOf[w] > 0 ? (highX[w] - lowX[w]) / 2F : 0F;
        }
    }

    /** Which of {@code o}'s wheels wheel polygon {@code p} belongs to: the nearest to its hub. */
    private static int nearestWheel(ContO o, Plane p) {
        float mx = 0F;
        for (int k = 0; k < p.n; k++) {
            mx += p.ox[k];
        }
        mx /= Math.max(1, p.n);
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            float d = Math.abs(o.keyx[w] - mx) + Math.abs(o.keyz[w] - p.wz);
            if (d < bestDistance) {
                bestDistance = d;
                best = w;
            }
        }
        return best;
    }

    /** A car model's shape and its wheels' drop below its middle. */
    public record Loaded(CarShape shape, int wheelDrop) {
    }
}
