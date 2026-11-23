package nfm.lit.features.realism;

import java.util.Arrays;
import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;

/**
 * Where a car's parts are, found once a race from its model and its sheet:
 * what each polygon is (body, bonnet, windscreen, side window, light,
 * wheel), how low it sits (where dirt sticks) and which side it faces
 * (where scratches go); the bonnet's panel and hinge; the windscreen's
 * plane; where the engine, the radiators and the exhaust tips are. All in
 * the car's own numbers: x to the right, y down, z forward.
 * <p>
 * A model's polygons are not all wound the same way, so each normal is
 * turned to face away from the body's middle.
 */
final class CarParts {

    static final byte BODY = 0;
    static final byte BONNET = 1;
    static final byte SCREEN = 2;
    static final byte WINDOW_LEFT = 3;
    static final byte WINDOW_RIGHT = 4;
    static final byte GLASS = 5;
    static final byte LIGHT_FRONT = 6;
    static final byte LIGHT_REAR = 7;
    static final byte WHEEL = 8;

    static final int LEFT = 0;
    static final int RIGHT = 1;

    CarShape shape;
    CarSheet sheet = CarSheet.DEFAULT;

    /* per polygon */
    byte[] role = new byte[0];
    /** How low on the body: 0 at the top, 1 at the sills (1 for a wheel), where dirt sticks. */
    float[] low = new float[0];
    /** -1 if it is no side face, else the side it faces ({@link #LEFT}, {@link #RIGHT}). */
    byte[] side = new byte[0];
    /** The outward normal and the middle. */
    float[] nx = new float[0];
    float[] ny = new float[0];
    float[] nz = new float[0];
    float[] mx = new float[0];
    float[] my = new float[0];
    float[] mz = new float[0];
    /** The polygon's extent along y and z. */
    float[] y0 = new float[0];
    float[] y1 = new float[0];
    float[] z0 = new float[0];
    float[] z1 = new float[0];

    /* the body */
    float halfWidth;
    float top;
    float bottom;
    float front;
    float rear;
    float height;
    float length;
    /** Where the ground is below the car's middle (the game's {@code grat}). */
    float ground;

    /* the windscreen: its biggest polygon, or -1; its plane's axes and middle */
    int screen = -1;
    final float[] screenN = new float[3];
    final float[] screenU = new float[3];
    final float[] screenV = new float[3];
    final float[] screenMid = new float[3];
    /** How far a crack may reach from the windscreen's middle across it and up it. */
    float screenReachU;
    float screenReachV;

    /* the bonnet: the up-facing panels ahead of the windscreen */
    boolean hasBonnet;
    float bonnetLeft;
    float bonnetRight;
    float hingeZ;
    float bonnetFront;
    float bonnetY;
    int bonnetRgb;

    /* the parts the sheet places */
    final float[] engine = new float[3];
    /** Each side's radiator (left, right), none on an air-cooled car. */
    final float[] radiatorX = new float[2];
    final float[] radiatorY = new float[2];
    final float[] radiatorZ = new float[2];
    boolean radiators;
    int pipes;
    final float[] pipeX = new float[2];
    final float[] pipeY = new float[2];
    final float[] pipeZ = new float[2];
    /** The pipes point up (stacks) rather than back. */
    boolean stacks;

    /* the rest */
    /** The model has no rear lights of its own: the brake lights are drawn. */
    boolean drawnLamps;
    int bumperFrontRgb;
    int bumperRearRgb;
    /** Each side window's middle, if the side has one. */
    final boolean[] hasWindow = new boolean[2];
    final float[] windowX = new float[2];
    final float[] windowY = new float[2];
    final float[] windowZ = new float[2];
    /** The wheel steers (a front wheel). */
    final boolean[] frontWheel = new boolean[RaceView.WHEELS];

    private final float[] normal = new float[3];
    private final float[] middle = new float[3];

    /** Reads car shape {@code s} with sheet {@code sh}, the ground {@code wheelDrop} below its middle. */
    void build(CarShape s, CarSheet sh, int wheelDrop) {
        shape = s;
        sheet = sh;
        int n = s.planes;
        fit(n);
        halfWidth = Math.max(1F, Math.max(Math.abs(s.minX), Math.abs(s.maxX)));
        top = s.minY;
        bottom = s.maxY;
        front = s.maxZ;
        rear = s.minZ;
        height = Math.max(1F, bottom - top);
        length = Math.max(1F, front - rear);
        ground = wheelDrop;
        if (top > bottom) { // no body at all: a box around the wheels
            halfWidth = 20F;
            top = -10F;
            bottom = wheelDrop;
            front = 30F;
            rear = -30F;
            height = bottom - top;
            length = front - rear;
        }
        float cx = (s.minX + s.maxX) / 2F;
        float cy = (top + bottom) / 2F;
        float cz = (front + rear) / 2F;
        for (int i = 0; i < n; i++) {
            s.normal(i, normal);
            s.middle(i, middle);
            if (normal[0] * (middle[0] - cx) + normal[1] * (middle[1] - cy) + normal[2] * (middle[2] - cz) < 0F) {
                normal[0] = -normal[0];
                normal[1] = -normal[1];
                normal[2] = -normal[2];
            }
            nx[i] = normal[0];
            ny[i] = normal[1];
            nz[i] = normal[2];
            mx[i] = middle[0];
            my[i] = middle[1];
            mz[i] = middle[2];
            extent(s, i);
        }
        classify(s, n);
        findScreen(s, n);
        findBonnet(s, n);
        place(s, n);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            frontWheel[w] = s.steerX[w] != 0;
        }
        if (!frontWheel[0] && !frontWheel[1] && !frontWheel[2] && !frontWheel[3]) {
            for (int w = 0; w < RaceView.WHEELS; w++) {
                frontWheel[w] = s.hubZ[w] > 0F;
            }
        }
    }

    private void fit(int n) {
        if (role.length < n) {
            role = new byte[n];
            low = new float[n];
            side = new byte[n];
            nx = new float[n];
            ny = new float[n];
            nz = new float[n];
            mx = new float[n];
            my = new float[n];
            mz = new float[n];
            y0 = new float[n];
            y1 = new float[n];
            z0 = new float[n];
            z1 = new float[n];
        }
        Arrays.fill(role, BODY);
        Arrays.fill(side, (byte) -1);
    }

    private void extent(CarShape s, int i) {
        float a = Float.MAX_VALUE;
        float b = -Float.MAX_VALUE;
        float c = Float.MAX_VALUE;
        float d = -Float.MAX_VALUE;
        for (int k = 0; k < s.count[i]; k++) {
            int v = s.first[i] + k;
            a = Math.min(a, s.y[v]);
            b = Math.max(b, s.y[v]);
            c = Math.min(c, s.z[v]);
            d = Math.max(d, s.z[v]);
        }
        y0[i] = a;
        y1[i] = b;
        z0[i] = c;
        z1[i] = d;
    }

    /** What each polygon is, how low it sits and which side it faces. */
    private void classify(CarShape s, int n) {
        for (int i = 0; i < n; i++) {
            float t = (my[i] - top) / height;
            low[i] = clamp((t - 0.45F) / 0.4F);
            if (s.wheel[i] >= 0) {
                role[i] = WHEEL;
                low[i] = 1F;
            } else if (s.light[i] == 1) {
                role[i] = LIGHT_FRONT;
            } else if (s.light[i] == 2) {
                role[i] = LIGHT_REAR;
            } else if (s.glass[i]) {
                if (Math.abs(nx[i]) > 0.6F) {
                    role[i] = mx[i] < 0F ? WINDOW_LEFT : WINDOW_RIGHT;
                } else {
                    role[i] = nz[i] > 0.3F ? SCREEN : GLASS;
                }
            }
            if (role[i] == BODY && Math.abs(nx[i]) > 0.55F) {
                side[i] = (byte) (mx[i] < 0F ? LEFT : RIGHT);
            }
        }
    }

    /** The windscreen: the forward-facing glass that faces forward most over the biggest area. */
    private void findScreen(CarShape s, int n) {
        screen = -1;
        float best = 0F;
        for (int i = 0; i < n; i++) {
            if (role[i] == SCREEN) {
                float area = s.normal(i, normal) * nz[i];
                if (area > best) {
                    best = area;
                    screen = i;
                }
            }
        }
        if (screen < 0) {
            return;
        }
        screenN[0] = nx[screen];
        screenN[1] = ny[screen];
        screenN[2] = nz[screen];
        screenMid[0] = mx[screen];
        screenMid[1] = my[screen];
        screenMid[2] = mz[screen];
        // across: the car's x laid into the plane; up the glass: square to both
        float d = screenN[0];
        screenU[0] = 1F - d * screenN[0];
        screenU[1] = -d * screenN[1];
        screenU[2] = -d * screenN[2];
        normalize(screenU);
        screenV[0] = screenN[1] * screenU[2] - screenN[2] * screenU[1];
        screenV[1] = screenN[2] * screenU[0] - screenN[0] * screenU[2];
        screenV[2] = screenN[0] * screenU[1] - screenN[1] * screenU[0];
        float reachU = Float.MAX_VALUE;
        float reachV = Float.MAX_VALUE;
        float spanU = 0F;
        float spanV = 0F;
        for (int k = 0; k < s.count[screen]; k++) {
            int v = s.first[screen] + k;
            float u = dot(screenU, s.x[v] - screenMid[0], s.y[v] - screenMid[1], s.z[v] - screenMid[2]);
            float w = dot(screenV, s.x[v] - screenMid[0], s.y[v] - screenMid[1], s.z[v] - screenMid[2]);
            spanU = Math.max(spanU, Math.abs(u));
            spanV = Math.max(spanV, Math.abs(w));
            reachU = Math.min(reachU, Math.abs(u));
            reachV = Math.min(reachV, Math.abs(w));
        }
        screenReachU = Math.max(0.5F, 0.7F * Math.max(reachU, spanU * 0.5F));
        screenReachV = Math.max(0.5F, 0.7F * Math.max(reachV, spanV * 0.5F));
    }

    /** The bonnet: up-facing body panels ahead of the windscreen, in the car's upper half. */
    private void findBonnet(CarShape s, int n) {
        float from = screen >= 0 ? z1[screen] - 1F : rear + length * 0.62F;
        hasBonnet = false;
        bonnetLeft = Float.MAX_VALUE;
        bonnetRight = -Float.MAX_VALUE;
        hingeZ = Float.MAX_VALUE;
        bonnetFront = -Float.MAX_VALUE;
        bonnetY = Float.MAX_VALUE;
        float biggest = 0F;
        for (int i = 0; i < n; i++) {
            if (role[i] != BODY || ny[i] > -0.6F || mz[i] < from || (my[i] - top) / height > 0.6F) {
                continue;
            }
            role[i] = BONNET;
            hasBonnet = true;
            for (int k = 0; k < s.count[i]; k++) {
                int v = s.first[i] + k;
                bonnetLeft = Math.min(bonnetLeft, s.x[v]);
                bonnetRight = Math.max(bonnetRight, s.x[v]);
                hingeZ = Math.min(hingeZ, s.z[v]);
                bonnetFront = Math.max(bonnetFront, s.z[v]);
                bonnetY = Math.min(bonnetY, s.y[v]);
            }
            float area = s.normal(i, normal);
            if (area > biggest) {
                biggest = area;
                bonnetRgb = s.rgb[i];
            }
        }
        if (hasBonnet && (bonnetFront - hingeZ < 2F || bonnetRight - bonnetLeft < 2F)) {
            hasBonnet = false;
        }
    }

    /** The engine, radiators, exhausts, lamps, bumpers and side windows. */
    private void place(CarShape s, int n) {
        engine[0] = 0F;
        switch (sheet.sits) {
            case CarSheet.MID -> {
                engine[1] = top + height * 0.3F;
                engine[2] = rear + length * 0.3F;
            }
            case CarSheet.REAR -> {
                engine[1] = top + height * 0.3F;
                engine[2] = rear + length * 0.12F;
            }
            default -> {
                engine[1] = hasBonnet ? bonnetY - 0.5F : top + height * 0.3F;
                engine[2] = hasBonnet ? (hingeZ + bonnetFront) / 2F : rear + length * 0.8F;
            }
        }
        radiators = sheet.cooling != CarSheet.AIR;
        for (int sd = 0; sd < 2; sd++) {
            float out = sd == LEFT ? -1F : 1F;
            if (sheet.cooling == CarSheet.SIDE_PODS) {
                radiatorX[sd] = out * (halfWidth - 1F);
                radiatorY[sd] = top + height * 0.55F;
                radiatorZ[sd] = rear + length * 0.45F;
            } else {
                radiatorX[sd] = out * halfWidth * 0.55F;
                radiatorY[sd] = hasBonnet ? bonnetY : top + height * 0.5F;
                radiatorZ[sd] = front - length * 0.06F;
            }
        }
        stacks = sheet.exhausts == CarSheet.STACKS;
        pipes = stacks ? 2 : sheet.exhausts;
        for (int p = 0; p < 2; p++) {
            float out = p == 0 ? -1F : 1F;
            if (stacks) {
                pipeX[p] = out * halfWidth * 0.6F;
                pipeY[p] = top - 1F;
                pipeZ[p] = rear + length * 0.55F;
            } else {
                boolean middle = pipes == 1 && sheet.sits == CarSheet.MID;
                pipeX[p] = middle ? 0F : pipes == 1 ? halfWidth * 0.4F : out * halfWidth * 0.45F;
                pipeY[p] = bottom - height * 0.18F;
                pipeZ[p] = rear - 0.5F;
            }
        }
        drawnLamps = true;
        float frontMost = -Float.MAX_VALUE;
        float rearMost = Float.MAX_VALUE;
        bumperFrontRgb = 0x303032;
        bumperRearRgb = 0x303032;
        float[] windowArea = new float[2];
        Arrays.fill(windowX, 0F);
        Arrays.fill(windowY, 0F);
        Arrays.fill(windowZ, 0F);
        for (int i = 0; i < n; i++) {
            if (role[i] == LIGHT_REAR) {
                drawnLamps = false;
            }
            if (role[i] == BODY && low[i] > 0.3F) {
                if (mz[i] > frontMost) {
                    frontMost = mz[i];
                    bumperFrontRgb = s.rgb[i];
                }
                if (mz[i] < rearMost) {
                    rearMost = mz[i];
                    bumperRearRgb = s.rgb[i];
                }
            }
            if (role[i] == WINDOW_LEFT || role[i] == WINDOW_RIGHT) {
                int sd = role[i] == WINDOW_LEFT ? LEFT : RIGHT;
                float area = s.normal(i, normal);
                windowX[sd] += mx[i] * area;
                windowY[sd] += my[i] * area;
                windowZ[sd] += mz[i] * area;
                windowArea[sd] += area;
            }
        }
        for (int sd = 0; sd < 2; sd++) {
            hasWindow[sd] = windowArea[sd] > 0F;
            if (hasWindow[sd]) {
                windowX[sd] /= windowArea[sd];
                windowY[sd] /= windowArea[sd];
                windowZ[sd] /= windowArea[sd];
            }
        }
    }

    /** The side face nearest to height {@code y}, length {@code z} on side {@code sd}, or -1. */
    int sideFace(int sd, float z) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < shape.planes; i++) {
            if (side[i] != sd || z1[i] - z0[i] < 2F || y1[i] - y0[i] < 1F) {
                continue;
            }
            float off = z < z0[i] ? z0[i] - z : z > z1[i] ? z - z1[i] : 0F;
            // the lower body is where a car rubs a wall
            float score = off + Math.abs((my[i] - top) / height - 0.65F) * length * 0.3F;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** The x on polygon {@code i}'s plane at height {@code y}, length {@code z} (its normal is mostly sideways). */
    float xOn(int i, float y, float z) {
        float a = nx[i];
        if (Math.abs(a) < 0.2F) {
            return mx[i];
        }
        return mx[i] - (ny[i] * (y - my[i]) + nz[i] * (z - mz[i])) / a;
    }

    static float clamp(float v) {
        return v < 0F ? 0F : Math.min(v, 1F);
    }

    private static float dot(float[] a, float x, float y, float z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    private static void normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-4F) {
            v[0] = 1F;
            v[1] = 0F;
            v[2] = 0F;
            return;
        }
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }
}
