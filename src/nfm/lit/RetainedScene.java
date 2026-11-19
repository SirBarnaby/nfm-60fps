package nfm.lit;

import java.util.Arrays;

/**
 * A race scene recorded once per tick, then moved to each frame's moment.
 * <p>
 * Between two race ticks (53 ms) the presentation pass shows the race at
 * many moments, and running the game's whole 3D drawing code for every one
 * of them is most of what a frame costs. Instead the scene is drawn once per
 * tick, halfway through it, with the picture's edges widened so what comes
 * into view during the tick is there already. Every corner the game draws
 * is kept as a point: where it is on the car or in the world and how the
 * game places it ({@link Precise} works this out). For each frame those
 * points are placed again for the cars' and the camera's positions at that
 * moment ({@link FramePose}), with the same arithmetic as {@link Precise},
 * and written into the recorded scene's exact corners. What is drawn, in
 * which order and in which colour stays as the game decided it for the
 * tick, which is also how often the original decided it.
 * <p>
 * Kinds of point:
 * <ul>
 * <li>{@link #CAMERA}: fixed in the world, or moving with the camera divided
 * by some number (clouds and mountains), kept relative to the camera;
 * <li>{@link #CAR}: a corner of a car, turned by its wheels and the car;
 * <li>{@link #SHADOW}: a corner of a car's shadow on the ground;
 * <li>{@link #HORIZON}: the edge of a sky or ground band;
 * <li>{@link #SCREEN}: a corner the game worked out on screen only (dust,
 * sparks, a car breaking up), moved and scaled with the point it belongs to.
 * </ul>
 * Points are named in the recorded corners by {@link #handle} values, which
 * no real corner can have. Built on the game thread; once built, read by
 * whichever thread draws it.
 */
final class RetainedScene {

    static final int CAMERA = 0;
    static final int CAR = 1;
    static final int SHADOW = 2;
    static final int HORIZON = 3;
    static final int SCREEN = 4;
    /** A SCREEN corner that also knows where it was shown a tick ago (smooth effects). */
    static final int SCREEN_TRAIL = 5;

    private static final int STRIDE = 8;
    /* handles are floats near -1.3e30, the point's number in the low 23 bits */
    private static final int HANDLE_TAG = 0xF1800000;
    private static final int HANDLE_MASK = 0xFF800000;
    private static final int MAX_POINTS = 1 << 23;
    private static final float DEGREES = (float) (Math.PI / 180D);

    /** Where the scene was recorded: the moment the points are relative to. */
    final FramePose recorded = new FramePose();

    /* the projection, as it was when recording */
    private int cx;
    private int cy;
    private int cz;
    private float focus;
    /** How far from where effects were a tick ago to where they are now the frame being placed is. */
    private float trailShare = 1F;
    private float top;
    private float bottom;

    private int[] kinds = new int[4096];
    private float[] points = new float[STRIDE * 4096];
    private int pointCount;
    /* per polygon: where its exact x corners start in the scene's data, its corner count, its first code */
    private int[] polygons = new int[3 * 1024];
    private int polygonCount;
    /* per corner coordinate: point * 2 (+ 1 for y), or -1 to keep the recorded value */
    private int[] codes = new int[8192];
    private int codeCount;

    /* each point placed at the last moment prepared */
    private float[] placedX = new float[4096];
    private float[] placedY = new float[4096];
    private float[] placedZ = new float[4096];
    private int placedCount;

    /* the moment prepared, worked out once for all points */
    private float sinYaw;
    private float cosYaw = 1F;
    private float sinPitch;
    private float cosPitch = 1F;
    private float moveX;
    private float moveY;
    private float moveZ;
    private final float[] carX = new float[FramePose.SLOTS];
    private final float[] carY = new float[FramePose.SLOTS];
    private final float[] carZ = new float[FramePose.SLOTS];
    /* per car: sin and cos of roll, pitch, yaw, steering and wheel spin */
    private final float[][] carTurn = new float[FramePose.SLOTS][10];

    /** The value standing for point {@code id} in a recorded corner. */
    static float handle(int id) {
        return Float.intBitsToFloat(HANDLE_TAG | id);
    }

    /** The point a recorded corner stands for, or -1 if it is a real value. */
    static int pointOf(float value) {
        int bits = Float.floatToRawIntBits(value);
        return (bits & HANDLE_MASK) == HANDLE_TAG ? bits & ~HANDLE_MASK : -1;
    }

    /** Starts a new recording at {@link #recorded}, with the projection as {@code Medium} has it now. */
    void begin() {
        pointCount = 0;
        polygonCount = 0;
        codeCount = 0;
        placedCount = 0;
        cx = Medium.cx;
        cy = Medium.cy;
        cz = Medium.cz;
        focus = Medium.focus_point;
        top = Medium.ih;
        bottom = Medium.h;
        prepare(recorded);
    }

    int points() {
        return pointCount;
    }

    int polygons() {
        return polygonCount;
    }

    /* ------------------------------------------------------------ recording */

    private int add(int kind, float a, float b, float c, float d, float e, float f, float g) {
        if (pointCount == MAX_POINTS) {
            throw new IllegalStateException("too many points in one scene");
        }
        if (pointCount == kinds.length) {
            kinds = Arrays.copyOf(kinds, pointCount * 2);
            points = Arrays.copyOf(points, pointCount * 2 * STRIDE);
        }
        int at = pointCount * STRIDE;
        kinds[pointCount] = kind;
        points[at] = a;
        points[at + 1] = b;
        points[at + 2] = c;
        points[at + 3] = d;
        points[at + 4] = e;
        points[at + 5] = f;
        points[at + 6] = g;
        return pointCount++;
    }

    /**
     * A point at {@code (x, y, z)} from the camera as recorded, before the
     * camera turns it, which moves with the camera divided by
     * {@code divisor} and is projected with {@code Utility.ys}'s near limit
     * {@code nearZ}.
     */
    int camera(float x, float y, float z, int divisor, int nearZ) {
        return add(CAMERA, x, y, z, 1F / divisor, nearZ, 0F, 0F);
    }

    /**
     * Corner {@code (x, y, z)} of car {@code slot} in its own frame
     * (already mirrored where the game mirrors it), on a wheel turning
     * about {@code (wx, wy, wz)} unless those are 0.
     */
    int car(int slot, float x, float y, float z, int wx, int wy, int wz) {
        int id = add(CAR, slot, x, y, z, wx, wy, wz);
        return id;
    }

    /** Corner {@code (x, z)} of car {@code slot}'s shadow, laid on the ground {@code y} from the camera as recorded. */
    int shadow(int slot, float x, float y, float z, float groundY) {
        return add(SHADOW, slot, x, y, z, groundY, 0F, 0F);
    }

    /**
     * The edge of a sky or ground band: a level line {@code height} below
     * the camera as recorded and {@code depth} ahead, which follows the
     * camera up and down when {@code follows}.
     */
    int horizon(float height, int depth, boolean follows) {
        return add(HORIZON, height, depth, follows ? 1F : 0F, 0F, 0F, 0F, 0F);
    }

    /**
     * A corner the game put at {@code (x, y)} on screen, belonging to point
     * {@code anchor}: it keeps its place next to that point, nearer or
     * farther as the point is.
     */
    int screen(int anchor, float x, float y) {
        place(anchor);
        return add(SCREEN, anchor, x, y, placedX[anchor], placedY[anchor], placedZ[anchor], 0F);
    }

    /**
     * A corner as {@link #screen(int, float, float)} that was at
     * {@code (trailX, trailY)} a tick ago: each frame puts it the frame's
     * share of the way from there to {@code (x, y)}.
     */
    int screen(int anchor, float x, float y, float trailX, float trailY) {
        place(anchor);
        int id = add(SCREEN_TRAIL, anchor, x, y, placedX[anchor], placedY[anchor], placedZ[anchor], trailX);
        points[id * STRIDE + 7] = trailY;
        return id;
    }

    /**
     * The polygon whose exact corners start at {@code at} in the scene's
     * data, recorded as {@code ex/ey}: handles, or values kept as they are.
     */
    void polygon(int at, int n, float[] ex, float[] ey) {
        if (polygonCount * 3 == polygons.length) {
            polygons = Arrays.copyOf(polygons, polygons.length * 2);
        }
        if (codeCount + 2 * n > codes.length) {
            codes = Arrays.copyOf(codes, Math.max(codes.length * 2, codeCount + 2 * n));
        }
        int p = polygonCount++ * 3;
        polygons[p] = at;
        polygons[p + 1] = n;
        polygons[p + 2] = codeCount;
        for (int k = 0; k < n; k++) {
            int point = pointOf(ex[k]);
            codes[codeCount + k] = point < 0 ? -1 : point * 2;
        }
        for (int k = 0; k < n; k++) {
            int point = pointOf(ey[k]);
            codes[codeCount + n + k] = point < 0 ? -1 : point * 2 + 1;
        }
        codeCount += 2 * n;
    }

    /* ---------------------------------------------- placing on the card */

    /** Hands the recording to {@code out}, for the graphics card to place its points (no copy). */
    void export(nfm.lit.gpu.ScenePoints out) {
        out.kinds = kinds;
        out.points = points;
        out.pointCount = pointCount;
        out.polygons = polygons;
        out.polygonCount = polygonCount;
        out.codes = codes;
    }

    /**
     * Works out what the points share at moment {@code pose}, as
     * {@link #realize} does, into {@code out}'s constants for the card,
     * instead of placing every point here.
     */
    void frameConstants(FramePose pose, nfm.lit.gpu.ScenePoints out) {
        prepare(pose);
        float[] c = out.camera;
        c[0] = cx;
        c[1] = cy;
        c[2] = cz;
        c[3] = focus;
        c[4] = sinYaw;
        c[5] = cosYaw;
        c[6] = sinPitch;
        c[7] = cosPitch;
        c[8] = moveX;
        c[9] = moveY;
        c[10] = moveZ;
        c[11] = trailShare;
        c[12] = top;
        c[13] = bottom;
        int slots = Math.min(pose.slots, nfm.lit.gpu.ScenePoints.CAR_SLOTS);
        float[] cars = out.cars;
        for (int s = 0; s < slots; s++) {
            int at = s * 16;
            float[] t = carTurn[s];
            cars[at] = carX[s];
            cars[at + 1] = carY[s];
            cars[at + 2] = carZ[s];
            System.arraycopy(t, 0, cars, at + 4, 8);
            cars[at + 12] = t[8];
            cars[at + 13] = t[9];
        }
        out.carCount = slots;
    }

    /* -------------------------------------------------------------- placing */

    /**
     * Places every point for moment {@code pose} and writes the corners into
     * {@code data}, the recorded scene's commands.
     */
    void realize(FramePose pose, int[] data) {
        prepare(pose);
        for (int id = 0; id < pointCount; id++) {
            place(id);
        }
        for (int p = 0; p < polygonCount * 3; p += 3) {
            int at = polygons[p];
            int n2 = polygons[p + 1] * 2;
            int code = polygons[p + 2];
            for (int k = 0; k < n2; k++) {
                int c = codes[code + k];
                if (c >= 0) {
                    float v = (c & 1) == 0 ? placedX[c >> 1] : placedY[c >> 1];
                    data[at + k] = Float.floatToRawIntBits(v);
                }
            }
        }
    }

    /** Works out what all points share at moment {@code pose}; the points are then placed in order. */
    private void prepare(FramePose pose) {
        trailShare = Math.max(0F, Math.min(1.25F, pose.alpha));
        float yaw = pose.camXZ * DEGREES;
        float pitch = pose.camZY * DEGREES;
        sinYaw = (float) Math.sin(yaw);
        cosYaw = (float) Math.cos(yaw);
        sinPitch = (float) Math.sin(pitch);
        cosPitch = (float) Math.cos(pitch);
        moveX = (float) (recorded.camX - pose.camX);
        moveY = (float) (recorded.camY - pose.camY);
        moveZ = (float) (recorded.camZ - pose.camZ);
        for (int s = 0; s < pose.slots; s++) {
            carX[s] = (float) (pose.x[s] - pose.camX);
            carY[s] = (float) (pose.y[s] - pose.camY);
            carZ[s] = (float) (pose.z[s] - pose.camZ);
            float[] t = carTurn[s];
            angle(t, 0, pose.xy[s]);
            angle(t, 2, pose.zy[s]);
            angle(t, 4, pose.xz[s]);
            angle(t, 6, pose.wxz[s]);
            angle(t, 8, pose.wzy[s]);
        }
        if (placedX.length < pointCount) {
            int size = Math.max(pointCount, placedX.length * 2);
            placedX = new float[size];
            placedY = new float[size];
            placedZ = new float[size];
        }
        placedCount = 0;
    }

    private static void angle(float[] t, int at, float degrees) {
        float r = degrees * DEGREES;
        t[at] = (float) Math.sin(r);
        t[at + 1] = (float) Math.cos(r);
    }

    /** Places point {@code id} (and, while recording, every point before it not yet placed). */
    private void place(int id) {
        if (placedX.length <= id) {
            int size = Math.max(id + 1, placedX.length * 2);
            placedX = Arrays.copyOf(placedX, size);
            placedY = Arrays.copyOf(placedY, size);
            placedZ = Arrays.copyOf(placedZ, size);
        }
        while (placedCount <= id) {
            placeOne(placedCount++);
        }
    }

    private void placeOne(int id) {
        int at = id * STRIDE;
        float[] p = points;
        switch (kinds[id]) {
            case CAMERA -> {
                float move = p[at + 3];
                project(id, p[at] + moveX * move, p[at + 1] + moveY * move, p[at + 2] + moveZ * move, p[at + 4]);
            }
            case CAR -> {
                int slot = (int) p[at];
                float[] t = carTurn[slot];
                float x = p[at + 1];
                float y = p[at + 2];
                float z = p[at + 3];
                float wx = p[at + 4];
                float wy = p[at + 5];
                float wz = p[at + 6];
                if (wz != 0F) { // the wheel's spin about its axle
                    float da = y - wy;
                    float db = z - wz;
                    y = wy + da * t[9] - db * t[8];
                    z = wz + da * t[8] + db * t[9];
                }
                if (wx != 0F) { // steering
                    float da = x - wx;
                    float db = z - wz;
                    x = wx + da * t[7] - db * t[6];
                    z = wz + da * t[6] + db * t[7];
                }
                float x1 = x * t[1] - y * t[0]; // roll
                float y1 = x * t[0] + y * t[1];
                float y2 = y1 * t[3] - z * t[2]; // pitch
                float z2 = y1 * t[2] + z * t[3];
                float x3 = x1 * t[5] - z2 * t[4]; // yaw
                float z3 = x1 * t[4] + z2 * t[5];
                project(id, x3 + carX[slot], y2 + carY[slot], z3 + carZ[slot], cz);
            }
            case SHADOW -> {
                int slot = (int) p[at];
                float[] t = carTurn[slot];
                float x = p[at + 1];
                float y = p[at + 2];
                float z = p[at + 3];
                float x1 = x * t[1] - y * t[0];
                float y1 = x * t[0] + y * t[1];
                float z2 = y1 * t[2] + z * t[3];
                float x3 = x1 * t[5] - z2 * t[4];
                float z3 = x1 * t[4] + z2 * t[5];
                project(id, x3 + carX[slot], p[at + 4] + moveY, z3 + carZ[slot], cz);
            }
            case HORIZON -> {
                float height = p[at] + (p[at + 2] != 0F ? moveY : 0F);
                float dy = height - cy;
                float dz = p[at + 1] - cz;
                float y = cy + dy * cosPitch - dz * sinPitch;
                float z = cz + dy * sinPitch + dz * cosPitch;
                float zy = Math.max(z, 10F);
                float screen = (zy - focus) * (cy - y) / zy + y;
                placedX[id] = 0F;
                placedY[id] = Math.max(top, Math.min(bottom, screen));
                placedZ[id] = z;
            }
            case SCREEN, SCREEN_TRAIL -> {
                int anchor = (int) p[at];
                float x = p[at + 1];
                float y = p[at + 2];
                if (kinds[id] == SCREEN_TRAIL) {
                    x = p[at + 6] + (x - p[at + 6]) * trailShare;
                    y = p[at + 7] + (y - p[at + 7]) * trailShare;
                }
                float recordedZ = p[at + 5];
                float z = placedZ[anchor];
                if (recordedZ < 10F || z < 10F) {
                    placedX[id] = x; // behind the camera: left where it was
                    placedY[id] = y;
                } else {
                    float scale = Math.max(recordedZ, cz) / Math.max(z, cz);
                    placedX[id] = placedX[anchor] + (x - p[at + 3]) * scale;
                    placedY[id] = placedY[anchor] + (y - p[at + 4]) * scale;
                }
                placedZ[id] = z;
            }
            default -> throw new IllegalStateException("point kind " + kinds[id]);
        }
    }

    /** Turns by the camera and projects like {@code Utility.xs/ys}, as {@code Precise.project} does. */
    private void project(int id, float px, float py, float pz, float nearZ) {
        float dx = px - cx;
        float dz = pz - cz;
        float x = cx + dx * cosYaw - dz * sinYaw;
        float z = cz + dx * sinYaw + dz * cosYaw;
        float dy = py - cy;
        dz = z - cz;
        float y = cy + dy * cosPitch - dz * sinPitch;
        z = cz + dy * sinPitch + dz * cosPitch;
        float zx = Math.max(z, cz);
        float zy = Math.max(z, nearZ);
        placedX[id] = (zx - focus) * (cx - x) / zx + x;
        placedY[id] = (zy - focus) * (cy - y) / zy + y;
        placedZ[id] = z;
    }
}
