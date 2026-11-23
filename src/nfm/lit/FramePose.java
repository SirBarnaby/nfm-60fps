package nfm.lit;

/**
 * Where the camera and the cars are at one moment between two race ticks,
 * without rounding: what {@link RetainedScene} moves a scene recorded once
 * per tick to. Positions are in world units, angles in degrees; a car
 * placed at a whole position (one that just respawned, say) simply has no
 * fraction.
 */
final class FramePose {

    /** As many cars as the presentation pass keeps poses for. */
    static final int SLOTS = 51;

    /**
     * How far from the tick before to this one the moment is, 0 to 1 (a
     * little more when the next tick is late): how far effects have come
     * from where they were shown a tick ago.
     */
    float alpha = 1F;

    double camX;
    double camY;
    double camZ;
    float camXZ;
    float camZY;

    /** Cars 0 to {@code slots - 1} below are set. */
    int slots;
    final double[] x = new double[SLOTS];
    final double[] y = new double[SLOTS];
    final double[] z = new double[SLOTS];
    final float[] xz = new float[SLOTS];
    final float[] xy = new float[SLOTS];
    final float[] zy = new float[SLOTS];
    final float[] wxz = new float[SLOTS];
    final float[] wzy = new float[SLOTS];

    /** Car {@code i} at whole values. */
    void car(int i, int px, int py, int pz, int pxz, int pxy, int pzy, int pwxz, int pwzy) {
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        xz[i] = pxz;
        xy[i] = pxy;
        zy[i] = pzy;
        wxz[i] = pwxz;
        wzy[i] = pwzy;
    }

    /** Car {@code i} a fraction {@code t} of the way from {@code a} to {@code b}, angles the shorter way round. */
    void car(int i, float t, int ax, int ay, int az, int axz, int axy, int azy, int awxz, int awzy,
            int bx, int by, int bz, int bxz, int bxy, int bzy, int bwxz, int bwzy) {
        x[i] = ax + (double) (bx - ax) * t;
        y[i] = ay + (double) (by - ay) * t;
        z[i] = az + (double) (bz - az) * t;
        xz[i] = axz + turn(axz, bxz, 360) * t;
        xy[i] = axy + turn(axy, bxy, 360) * t;
        zy[i] = azy + turn(azy, bzy, 360) * t;
        wxz[i] = awxz + (bwxz - awxz) * t;
        wzy[i] = awzy + turn(awzy, bwzy, 45) * t;
    }

    /** The camera at whole values. */
    void camera(int px, int py, int pz, int pxz, int pzy) {
        camX = px;
        camY = py;
        camZ = pz;
        camXZ = pxz;
        camZY = pzy;
        clampCamera();
    }

    /** The camera a fraction {@code t} of the way from {@code a} to {@code b}. */
    void camera(float t, int ax, int ay, int az, int axz, int azy, int bx, int by, int bz, int bxz, int bzy) {
        camX = ax + (double) (bx - ax) * t;
        camY = ay + (double) (by - ay) * t;
        camZ = az + (double) (bz - az) * t;
        camXZ = axz + turn(axz, bxz, 360) * t;
        camZY = azy + turn(azy, bzy, 360) * t;
        clampCamera();
    }

    /** The camera moved on by fractions of its whole values (a helmet cam's exact place, see PoseSpan). */
    void shiftCamera(float dx, float dy, float dz, float dxz, float dzy) {
        camX += dx;
        camY += dy;
        camZ += dz;
        camXZ += dxz;
        camZY += dzy;
        clampCamera();
    }

    /** As {@code Medium.clampView} keeps it: never below the ground nor past straight up or down. */
    private void clampCamera() {
        if (camY > 0D) {
            camY = 0D;
        }
        camZY = Math.max(-90F, Math.min(90F, camZY));
    }

    void copyFrom(FramePose other) {
        alpha = other.alpha;
        camX = other.camX;
        camY = other.camY;
        camZ = other.camZ;
        camXZ = other.camXZ;
        camZY = other.camZY;
        slots = other.slots;
        System.arraycopy(other.x, 0, x, 0, slots);
        System.arraycopy(other.y, 0, y, 0, slots);
        System.arraycopy(other.z, 0, z, 0, slots);
        System.arraycopy(other.xz, 0, xz, 0, slots);
        System.arraycopy(other.xy, 0, xy, 0, slots);
        System.arraycopy(other.zy, 0, zy, 0, slots);
        System.arraycopy(other.wxz, 0, wxz, 0, slots);
        System.arraycopy(other.wzy, 0, wzy, 0, slots);
    }

    /** The shorter way from angle {@code a} to {@code b}, in a circle of {@code period}. */
    private static int turn(int a, int b, int period) {
        int half = period / 2;
        return Math.floorMod(b - a + half, period) - half;
    }
}
