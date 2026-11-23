package nfm.lit;

/**
 * Exact (fractional) screen positions for frames drawn on the graphics card.
 * <p>
 * The game's 3D code works in whole units: positions, whole-degree angles,
 * each rotation rounded, and corners on whole 1280x720 pixels. Between two
 * race ticks the presentation pass moves the cars and the camera a fraction
 * of the way, which that code can only round, so every corner wobbles by up
 * to a pixel or so from frame to frame, and turning moves the picture in
 * whole-degree steps. With this on, the drawing code still decides
 * everything as before (what is drawn, in which order, in which colour), but
 * it also works out each corner again without rounding, from the cars' and
 * the camera's fractional positions and angles, and hands those to the
 * scene recorder, which the graphics card draws instead.
 * <p>
 * When a scene is recorded once for a whole tick ({@link RetainedScene}),
 * each corner is instead kept as a point that frame can place again: where
 * it is on its car or in the world, and how the game places it. The
 * recorded corners then hold handles to those points.
 * <p>
 * Fractions are kept as offsets from the whole values (how far past
 * {@code ContO.x} a car really is), so code that moves a whole value keeps
 * it consistent. Used on the game thread only.
 */
final class Precise {

    private static final float DEGREES = (float) (Math.PI / 180D);

    /** True while a frame is recorded for the graphics card. */
    static boolean on;
    /** The recorder of the frame being drawn, which takes the exact corners. */
    static SceneRecorder recorder;
    /** While a scene is recorded for a whole tick, where its points go; null otherwise. */
    private static RetainedScene retained;

    /** How far the camera really is past {@code Medium.x/y/z/xz/zy}. */
    static float camX;
    static float camY;
    static float camZ;
    static float camXZ;
    static float camZY;

    /*
     * The object being drawn, as {@code ContO.d} hands it to its planes: its
     * fraction minus the camera's, and the fractions of its angles.
     */
    static float objX;
    static float objY;
    static float objZ;
    static float objXZ;
    static float objXY;
    static float objZY;
    static float objWXZ;
    static float objWZY;

    /* sin and cos of the camera's angles, worked out once a frame */
    private static float camSinXZ;
    private static float camCosXZ = 1F;
    private static float camSinZY;
    private static float camCosZY = 1F;

    private static float[] xs = new float[16];
    private static float[] ys = new float[16];
    private static float[] zs = new float[16];
    private static float[] outX = new float[16];
    private static float[] outY = new float[16];

    private Precise() {
    }

    /** Starts a frame recorded into {@code target}, or a software frame with null. */
    static void begin(SceneRecorder target) {
        begin(target, null);
    }

    /**
     * Starts recording into {@code target}; with {@code scene}, a scene kept
     * for a whole tick, its corners as points in {@code scene}.
     */
    static void begin(SceneRecorder target, RetainedScene scene) {
        trailCount = 0;
        recorder = target;
        on = target != null;
        retained = on ? scene : null;
        if (target != null) {
            target.retain(retained);
        }
        objectNone();
    }

    /** Ends the frame; until the next {@link #begin} nothing is exact. */
    static void end() {
        if (recorder != null) {
            recorder.retain(null);
        }
        on = false;
        recorder = null;
        retained = null;
        camX = camY = camZ = camXZ = camZY = 0F;
        clearCars();
        objectNone();
    }

    /** The camera's angles for this frame, once {@code Medium.xz/zy} and the fractions are final. */
    static void cameraAngles() {
        float yaw = (Medium.xz + camXZ) * DEGREES;
        float pitch = (Medium.zy + camZY) * DEGREES;
        camSinXZ = (float) Math.sin(yaw);
        camCosXZ = (float) Math.cos(yaw);
        camSinZY = (float) Math.sin(pitch);
        camCosZY = (float) Math.cos(pitch);
    }

    /* the cars placed between two ticks, and how far past their whole values each really is */
    static final int MAX_CARS = 16;
    private static final ContO[] cars = new ContO[MAX_CARS];
    private static final int[] carSlots = new int[MAX_CARS];
    private static final float[][] fractions = new float[MAX_CARS][8];
    private static int carCount;

    /* the car being drawn (its place among the cars) or -1, and what a corner worked out on screen moves with */
    private static int objectSlot = -1;
    private static ContO anchorObject;
    private static int anchorPoint = -1;

    /** Forgets the cars' fractions. */
    static void clearCars() {
        java.util.Arrays.fill(cars, 0, carCount, null);
        carCount = 0;
    }

    /**
     * Car {@code o}, number {@code slot} among the cars, is really
     * {@code x..wzy} past its whole position, angles and wheel angles in
     * this frame.
     */
    static void car(int slot, ContO o, float x, float y, float z, float xz, float xy, float zy, float wxz, float wzy) {
        if (carCount == MAX_CARS) {
            return;
        }
        carSlots[carCount] = slot;
        cars[carCount] = o;
        float[] f = fractions[carCount++];
        f[0] = x;
        f[1] = y;
        f[2] = z;
        f[3] = xz;
        f[4] = xy;
        f[5] = zy;
        f[6] = wxz;
        f[7] = wzy;
    }

    /** The object being drawn: scenery, at whole positions, has no fractions. */
    static void object(ContO o) {
        anchorObject = o;
        anchorPoint = -1;
        for (int c = 0; c < carCount; c++) {
            if (cars[c] == o) {
                objectSlot = carSlots[c];
                float[] f = fractions[c];
                objX = f[0] - camX;
                objY = f[1] - camY;
                objZ = f[2] - camZ;
                objXZ = f[3];
                objXY = f[4];
                objZY = f[5];
                objWXZ = f[6];
                objWZY = f[7];
                return;
            }
        }
        objectSlot = -1;
        objX = -camX;
        objY = -camY;
        objZ = -camZ;
        objXZ = objXY = objZY = objWXZ = objWZY = 0F;
    }

    /** Something drawn in camera-relative units, not part of an object. */
    static void objectNone() {
        objectSlot = -1;
        anchorObject = null;
        anchorPoint = -1;
        objX = -camX;
        objY = -camY;
        objZ = -camZ;
        objXZ = objXY = objZY = objWXZ = objWZY = 0F;
    }

    /** Room for {@code n} corners. */
    private static void fit(int n) {
        if (xs.length < n) {
            xs = new float[n];
            ys = new float[n];
            zs = new float[n];
            outX = new float[n];
            outY = new float[n];
        }
    }

    /**
     * A plane of an object, as {@code Plane.d} places it: {@code ox/oy/oz}
     * (mirrored in x and z when {@code mirror}) at {@code (i, j, k)} from the
     * camera, turned about the wheel axle when the plane is part of a wheel,
     * then by the object's roll, pitch and yaw. Binds the exact corners to
     * the polygon the game draws with {@code sx/sy}.
     */
    static void plane(int[] ox, int[] oy, int[] oz, boolean mirror, int n, int i, int j, int k,
            int yaw, int roll, int pitch, int wheelYaw, int wheelPitch, int wx, int wy, int wz,
            int[] sx, int[] sy) {
        fit(n);
        float fi = i + objX;
        float fj = j + objY;
        float fk = k + objZ;
        for (int v = 0; v < n; v++) {
            xs[v] = (mirror ? -ox[v] : ox[v]) + fi;
            ys[v] = oy[v] + fj;
            zs[v] = (mirror ? -oz[v] : oz[v]) + fk;
        }
        if (wz != 0) {
            rotate(ys, zs, wy + fj, wz + fk, wheelPitch + objWZY, n);
        }
        if (wx != 0) {
            rotate(xs, zs, wx + fi, wz + fk, wheelYaw + objWXZ, n);
        }
        rotate(xs, ys, fi, fj, roll + objXY, n);
        rotate(ys, zs, fj, fk, pitch + objZY, n);
        rotate(xs, zs, fi, fk, yaw + objXZ, n);
        if (retained != null) {
            for (int v = 0; v < n; v++) {
                int point = objectSlot >= 0
                        ? retained.car(objectSlot, mirror ? -ox[v] : ox[v], oy[v], mirror ? -oz[v] : oz[v], wx, wy, wz)
                        : retained.camera(xs[v], ys[v], zs[v], 1, Medium.cz);
                outX[v] = outY[v] = RetainedScene.handle(point);
            }
        } else {
            project(n, Medium.cz, outX, outY);
        }
        recorder.bind(sx, sy, outX, outY, n);
    }

    private static int[] ground = new int[16];

    /** Keeps the heights {@code Plane.s} lays a shadow at, before it turns them by the camera. */
    static void keepGround(int[] y, int n) {
        if (ground.length < n) {
            ground = new int[n];
        }
        System.arraycopy(y, 0, ground, 0, n);
    }

    /**
     * A shadow, as {@code Plane.s} places it: the plane turned like the
     * object, then laid on the ground at the heights given to
     * {@link #keepGround} (worked out by the game from the rounded corners).
     */
    static void shadow(int[] ox, int[] oy, int[] oz, int n, int i, int j, int k, int yaw, int roll, int pitch,
            int[] sx, int[] sy) {
        fit(n);
        float fi = i + objX;
        float fj = j + objY;
        float fk = k + objZ;
        for (int v = 0; v < n; v++) {
            xs[v] = ox[v] + fi;
            ys[v] = oy[v] + fj;
            zs[v] = oz[v] + fk;
        }
        rotate(xs, ys, fi, fj, roll + objXY, n);
        rotate(ys, zs, fj, fk, pitch + objZY, n);
        rotate(xs, zs, fi, fk, yaw + objXZ, n);
        for (int v = 0; v < n; v++) {
            ys[v] = ground[v] - camY;
        }
        if (retained != null) {
            for (int v = 0; v < n; v++) {
                int point = objectSlot >= 0
                        ? retained.shadow(objectSlot, ox[v], oy[v], oz[v], ys[v])
                        : retained.camera(xs[v], ys[v], zs[v], 1, Medium.cz);
                outX[v] = outY[v] = RetainedScene.handle(point);
            }
        } else {
            project(n, Medium.cz, outX, outY);
        }
        recorder.bind(sx, sy, outX, outY, n);
    }

    /**
     * Corners given relative to the camera's whole position, as the scenery
     * code has them just before it turns them by the camera, into exact
     * screen corners {@code ex/ey}. Distant scenery moves with the camera
     * divided by {@code divisor} (whole-number division in the game: 1 for
     * the ground, 20 for clouds, 30 for mountains). {@code nearZ} is where
     * {@code Utility.ys} clamps them (its mode 0 is {@code Medium.cz}, mode
     * 1 is 10). Bind them with {@link #bind}.
     */
    static void camera(int[] x, int[] y, int[] z, int n, int divisor, int nearZ, float[] ex, float[] ey) {
        fit(n);
        float offX = shift(Medium.x, camX, divisor);
        float offY = shift(Medium.y, camY, divisor);
        float offZ = shift(Medium.z, camZ, divisor);
        for (int v = 0; v < n; v++) {
            xs[v] = x[v] + offX;
            ys[v] = y[v] + offY;
            zs[v] = z[v] + offZ;
        }
        if (retained != null) {
            for (int v = 0; v < n; v++) {
                ex[v] = ey[v] = RetainedScene.handle(retained.camera(xs[v], ys[v], zs[v], divisor, nearZ));
            }
        } else {
            project(n, nearZ, ex, ey);
        }
    }

    /** How much further a value the game moved by {@code -whole / divisor} really moves. */
    private static float shift(int whole, float fraction, int divisor) {
        return divisor == 1 ? -fraction : whole / divisor - (whole + fraction) / divisor;
    }

    /**
     * Where a horizontal line at {@code height} below the camera (relative
     * to its whole position, as {@code Medium.ground} is) and {@code depth}
     * ahead appears on screen, as {@code Medium.d} works out the sky and
     * ground bands, clamped to the picture like them.
     */
    static float horizon(int height, int depth, boolean followsCamera) {
        if (retained != null) {
            return RetainedScene.handle(retained.horizon(height - (followsCamera ? camY : 0F), depth, followsCamera));
        }
        int cy = Medium.cy;
        int cz = Medium.cz;
        float dy = height - (followsCamera ? camY : 0F) - cy;
        float dz = depth - cz;
        float y = cy + dy * camCosZY - dz * camSinZY;
        float z = cz + dy * camSinZY + dz * camCosZY;
        float zy = Math.max(z, 10F);
        float screen = (zy - Medium.focus_point) * (cy - y) / zy + y;
        return Math.max(Medium.ih, Math.min(Medium.h, screen));
    }

    /**
     * Records the polygon the game draws at {@code sx/sy} with exact corners
     * {@code ex/ey}; {@code id} names it for tests that follow it from frame
     * to frame.
     */
    static void bind(int[] sx, int[] sy, float[] ex, float[] ey, int n, int id) {
        recorder.tag(id);
        recorder.bind(sx, sy, ex, ey, n);
    }

    /**
     * While a scene is recorded for a whole tick: the corners of a polygon
     * the game worked out on screen only, {@code sx/sy}, as points that move
     * with what they belong to (the dust puff or the object being drawn),
     * into {@code ex/ey}; kept where they are if they belong to nothing.
     */
    static void screen(int[] sx, int[] sy, int n, float[] ex, float[] ey) {
        int anchor = anchor();
        boolean trail = trailCount == n;
        trailCount = 0;
        for (int v = 0; v < n; v++) {
            if (anchor < 0) {
                ex[v] = sx[v];
                ey[v] = sy[v];
            } else if (trail) {
                ex[v] = ey[v] = RetainedScene.handle(retained.screen(anchor, sx[v], sy[v], trailX[v], trailY[v]));
            } else {
                ex[v] = ey[v] = RetainedScene.handle(retained.screen(anchor, sx[v], sy[v]));
            }
        }
    }

    /* where the next polygon worked out on screen was shown a tick ago (smooth effects) */
    private static int[] trailX;
    private static int[] trailY;
    private static int trailCount;

    /**
     * Smooth effects: the next polygon worked out on screen, drawn right
     * after this, was at {@code tx/ty} a tick ago; the kept scene moves its
     * corners from there to where they are now as the frames go by.
     */
    static void trail(int[] tx, int[] ty, int n) {
        trailX = tx;
        trailY = ty;
        trailCount = retained != null ? n : 0;
    }

    /**
     * What the next corners worked out on screen move with: a point
     * {@code (x, y, z)} from the camera's whole position (the centre of a
     * dust puff), until the next {@link #object}.
     */
    static void anchor(int x, int y, int z) {
        if (retained != null) {
            anchorPoint = retained.camera(x - camX, y - camY, z - camZ, 1, Medium.cz);
        }
    }

    /** The point the corners worked out on screen move with, or -1 if none. */
    private static int anchor() {
        if (anchorPoint < 0 && anchorObject != null && retained != null) {
            ContO o = anchorObject;
            anchorPoint = objectSlot >= 0
                    ? retained.car(objectSlot, 0F, 0F, 0F, 0, 0, 0)
                    : retained.camera(o.x - Medium.x + objX, o.y - Medium.y + objY, o.z - Medium.z + objZ, 1, Medium.cz);
        }
        return anchorPoint;
    }

    /* ------------------------------------------------------------ extras */

    /** Points nearer than this to the camera's plane are behind it: a polygon with one is not drawn. */
    private static final float FEATURE_NEAR = 10F;

    /**
     * An extra's polygon fixed in the world at {@code x/y/z}: its whole
     * pixels into {@code sx/sy} for software, and while a frame is recorded
     * for the card its exact corners bound (kept points in a kept scene).
     * False if it is behind the camera or out of the picture: then it is
     * not drawn.
     */
    static boolean featureWorld(float[] x, float[] y, float[] z, int n, int[] sx, int[] sy) {
        fit(n);
        for (int v = 0; v < n; v++) {
            xs[v] = x[v] - Medium.x - camX;
            ys[v] = y[v] - Medium.y - camY;
            zs[v] = z[v] - Medium.z - camZ;
        }
        if (!featurePixels(n, sx, sy)) {
            return false;
        }
        if (on) {
            if (retained != null) {
                for (int v = 0; v < n; v++) {
                    outX[v] = outY[v] = RetainedScene.handle(retained.camera(xs[v], ys[v], zs[v], 1, Medium.cz));
                }
            }
            recorder.bind(sx, sy, outX, outY, n);
        }
        return true;
    }

    /**
     * An extra's polygon moving through the world: at {@code x/y/z} after
     * the last real tick, moving {@code vx/vy/vz} a tick. Drawn where it is
     * at {@code share} of the way between the ticks, {@code base} being
     * where the last real tick is (1, or 0 when the tick ahead is shown);
     * a kept scene keeps it as moving points, placed for every frame.
     */
    static boolean featureMoving(float[] x, float[] y, float[] z, float vx, float vy, float vz, int n, float share,
            float base, int[] sx, int[] sy) {
        java.util.Arrays.fill(sameVX, 0, n, vx);
        java.util.Arrays.fill(sameVY, 0, n, vy);
        java.util.Arrays.fill(sameVZ, 0, n, vz);
        return featureMoving(x, y, z, sameVX, sameVY, sameVZ, n, share, base, sx, sy);
    }

    /* one velocity for every corner, for the call above */
    private static final float[] sameVX = new float[nfm.lit.features.Canvas.MAX_CORNERS];
    private static final float[] sameVY = new float[nfm.lit.features.Canvas.MAX_CORNERS];
    private static final float[] sameVZ = new float[nfm.lit.features.Canvas.MAX_CORNERS];

    /**
     * An extra's polygon whose corners each move their own way: corner
     * {@code v} is at {@code (x, y, z)[v]} after the last real tick and moves
     * {@code (vx, vy, vz)[v]} a tick (zero: fixed in the world). Placed
     * {@code share} of the way between the ticks shown, the last real tick
     * at {@code base}; in a kept scene kept as moving points, which the card
     * places again for every frame.
     */
    static boolean featureMoving(float[] x, float[] y, float[] z, float[] vx, float[] vy, float[] vz, int n,
            float share, float base, int[] sx, int[] sy) {
        fit(n);
        float s = share - base;
        float baseX = -Medium.x - camX;
        float baseY = -Medium.y - camY;
        float baseZ = -Medium.z - camZ;
        for (int v = 0; v < n; v++) {
            xs[v] = x[v] + baseX + vx[v] * s;
            ys[v] = y[v] + baseY + vy[v] * s;
            zs[v] = z[v] + baseZ + vz[v] * s;
        }
        if (!featurePixels(n, sx, sy)) {
            return false;
        }
        if (on) {
            if (retained != null) {
                for (int v = 0; v < n; v++) {
                    int point = retained.moving(x[v] + baseX, y[v] + baseY, z[v] + baseZ, vx[v], vy[v], vz[v], base);
                    outX[v] = outY[v] = RetainedScene.handle(point);
                }
            }
            recorder.bind(sx, sy, outX, outY, n);
        }
        return true;
    }

    /**
     * An extra's polygon on car {@code car}, in the car's own frame at
     * {@code x/y/z}: turned and placed as the car is in this frame, and in a
     * kept scene kept as the car's own points, which follow it.
     */
    static boolean featureOnCar(ContO car, float[] x, float[] y, float[] z, int n, int[] sx, int[] sy) {
        return featureOnWheel(car, x, y, z, n, 0, 0, 0, sx, sy);
    }

    /**
     * An extra's polygon on one of car {@code car}'s wheels, in the car's own
     * frame: as {@link #featureOnCar}, but first spun with the wheels about
     * the hub {@code (wy, wz)} and, for a front wheel ({@code wx} not 0),
     * steered about {@code (wx, wz)}, as the game turns the wheel's own
     * polygons; with all three 0 it is on the body.
     */
    static boolean featureOnWheel(ContO car, float[] x, float[] y, float[] z, int n, int wx, int wy, int wz,
            int[] sx, int[] sy) {
        fit(n);
        object(car);
        int slot = objectSlot;
        float fi = car.x - Medium.x + objX;
        float fj = car.y - Medium.y + objY;
        float fk = car.z - Medium.z + objZ;
        float roll = car.xy + objXY;
        float pitch = car.zy + objZY;
        float yaw = car.xz + objXZ;
        objectNone();
        for (int v = 0; v < n; v++) {
            xs[v] = x[v];
            ys[v] = y[v];
            zs[v] = z[v];
        }
        if (wz != 0) {
            rotate(ys, zs, wy, wz, car.wzy, n); // the wheel's spin about its axle
        }
        if (wx != 0) {
            rotate(xs, zs, wx, wz, car.wxz, n); // steering
        }
        for (int v = 0; v < n; v++) {
            xs[v] += fi;
            ys[v] += fj;
            zs[v] += fk;
        }
        rotate(xs, ys, fi, fj, roll, n);
        rotate(ys, zs, fj, fk, pitch, n);
        rotate(xs, zs, fi, fk, yaw, n);
        if (!featurePixels(n, sx, sy)) {
            return false;
        }
        if (on) {
            if (retained != null) {
                for (int v = 0; v < n; v++) {
                    int point = slot >= 0
                            ? retained.car(slot, x[v], y[v], z[v], wx, wy, wz)
                            : retained.camera(xs[v], ys[v], zs[v], 1, Medium.cz);
                    outX[v] = outY[v] = RetainedScene.handle(point);
                }
            }
            recorder.bind(sx, sy, outX, outY, n);
        }
        return true;
    }

    /**
     * How far world point {@code (x, y, z)} is from the eye, as {@code Plane}
     * measures a polygon to fade it into the stage's fog.
     */
    static float featureDistance(float x, float y, float z) {
        int cx = Medium.cx;
        int cy = Medium.cy;
        int cz = Medium.cz;
        float dx = x - Medium.x - camX - cx;
        float dz = z - Medium.z - camZ - cz;
        float px = cx + dx * camCosXZ - dz * camSinXZ;
        float pz = cz + dx * camSinXZ + dz * camCosXZ;
        float dy = y - Medium.y - camY - cy;
        dz = pz - cz;
        float py = cy + dy * camCosZY - dz * camSinZY;
        pz = cz + dy * camSinZY + dz * camCosZY;
        return (float) Math.sqrt((cy - py) * (cy - py) + (cx - px) * (cx - px) + pz * pz);
    }

    /**
     * Projects the corners in {@link #xs}/{@link #ys}/{@link #zs} (from the
     * camera's whole position) into {@link #outX}/{@link #outY} and their
     * whole pixels into {@code sx/sy}; false if one is behind the camera or
     * all are out of the picture's widened edges.
     */
    private static boolean featurePixels(int n, int[] sx, int[] sy) {
        int cx = Medium.cx;
        int cy = Medium.cy;
        int cz = Medium.cz;
        float focus = Medium.focus_point;
        int left = 0;
        int right = 0;
        int above = 0;
        int below = 0;
        for (int v = 0; v < n; v++) {
            float dx = xs[v] - cx;
            float dz = zs[v] - cz;
            float px = cx + dx * camCosXZ - dz * camSinXZ;
            float pz = cz + dx * camSinXZ + dz * camCosXZ;
            float dy = ys[v] - cy;
            dz = pz - cz;
            float py = cy + dy * camCosZY - dz * camSinZY;
            pz = cz + dy * camSinZY + dz * camCosZY;
            if (pz < FEATURE_NEAR) {
                return false;
            }
            float zx = Math.max(pz, cz);
            float ex = (zx - focus) * (cx - px) / zx + px;
            float ey = (zx - focus) * (cy - py) / zx + py;
            outX[v] = ex;
            outY[v] = ey;
            sx[v] = Math.round(ex);
            sy[v] = Math.round(ey);
            left += ex < -Medium.cullX ? 1 : 0;
            right += ex > Medium.w + Medium.cullX ? 1 : 0;
            above += ey < -Medium.cullY ? 1 : 0;
            below += ey > Medium.h + Medium.cullY ? 1 : 0;
        }
        return left < n && right < n && above < n && below < n;
    }

    /** Turns by the camera and projects like Utility.xs/ys, into {@code ex/ey}. */
    private static void project(int n, int nearZ, float[] ex, float[] ey) {
        int cx = Medium.cx;
        int cy = Medium.cy;
        int cz = Medium.cz;
        float focus = Medium.focus_point;
        for (int v = 0; v < n; v++) {
            float dx = xs[v] - cx;
            float dz = zs[v] - cz;
            float x = cx + dx * camCosXZ - dz * camSinXZ;
            float z = cz + dx * camSinXZ + dz * camCosXZ;
            float dy = ys[v] - cy;
            dz = z - cz;
            float y = cy + dy * camCosZY - dz * camSinZY;
            z = cz + dy * camSinZY + dz * camCosZY;
            float zx = Math.max(z, cz);
            float zy = Math.max(z, nearZ);
            ex[v] = (zx - focus) * (cx - x) / zx + x;
            ey[v] = (zy - focus) * (cy - y) / zy + y;
        }
    }

    private static void rotate(float[] a, float[] b, float ca, float cb, float degrees, int n) {
        if (degrees == 0F) {
            return;
        }
        float r = degrees * DEGREES;
        float sin = (float) Math.sin(r);
        float cos = (float) Math.cos(r);
        for (int v = 0; v < n; v++) {
            float da = a[v] - ca;
            float db = b[v] - cb;
            a[v] = ca + da * cos - db * sin;
            b[v] = cb + da * sin + db * cos;
        }
    }
}
