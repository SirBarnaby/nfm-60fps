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
        recorder = target;
        on = target != null;
        objectNone();
    }

    /** Ends the frame; until the next {@link #begin} nothing is exact. */
    static void end() {
        on = false;
        recorder = null;
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
    private static final int MAX_CARS = 16;
    private static final ContO[] cars = new ContO[MAX_CARS];
    private static final float[][] fractions = new float[MAX_CARS][8];
    private static int carCount;

    /** Forgets the cars' fractions. */
    static void clearCars() {
        java.util.Arrays.fill(cars, 0, carCount, null);
        carCount = 0;
    }

    /**
     * Car {@code o} is really {@code x..wzy} past its whole position, angles
     * and wheel angles in this frame.
     */
    static void car(ContO o, float x, float y, float z, float xz, float xy, float zy, float wxz, float wzy) {
        if (carCount == MAX_CARS) {
            return;
        }
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
        for (int c = 0; c < carCount; c++) {
            if (cars[c] == o) {
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
        objectNone();
    }

    /** Something drawn in camera-relative units, not part of an object. */
    static void objectNone() {
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
        project(n, Medium.cz, outX, outY);
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
        project(n, Medium.cz, outX, outY);
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
        project(n, nearZ, ex, ey);
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
