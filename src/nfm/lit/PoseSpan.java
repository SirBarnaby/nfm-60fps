package nfm.lit;

/**
 * The two poses a stretch of frames moves between, and when: the cars and
 * the camera at the start and at the end of a race tick (or of the
 * run-ahead tick), the moment the stretch starts and how long it lasts.
 * Taken on the game thread; whichever thread draws then places the scene
 * for any moment with {@link #at}, without touching the race.
 */
final class PoseSpan {

    /** A car or the camera stays at the start pose, stays at the end pose, or moves between the two. */
    static final int AT_START = 0;
    static final int AT_END = 1;
    static final int MOVING = 2;

    /**
     * How far past the end a frame may carry the motion on while the next
     * tick is still being worked out on the game thread, as a share of the
     * tick (0.5 is 26 ms); past that it stays at the end. The next tick and
     * its run-ahead tick usually reach the presenter 10-25 ms after the tick
     * was due, so at 0.25 (13 ms) frames stood still for a few ms at most
     * ticks and then jumped. Hidden A/B runs of the same race: frames off
     * even by over 4 ms went from 4.9-8.7% of the race to 2.9-3.3%, and the
     * jump where the new tick takes over got smaller too (median 15 to 6
     * world units), as carrying on lands nearer than standing still.
     */
    static final float MAX_CARRY_ON = 0.5F;

    int slots;
    final int[] mode = new int[FramePose.SLOTS];
    /* x, y, z, xz, xy, zy, wxz, wzy of each car at the start and at the end */
    final int[][] start = new int[8][FramePose.SLOTS];
    final int[][] end = new int[8][FramePose.SLOTS];
    int cameraMode;
    /* x, y, z, xz, zy */
    final int[] cameraStart = new int[5];
    final int[] cameraEnd = new int[5];
    /* how far past cameraStart and cameraEnd the camera really is (a helmet cam's exact place); zero for the game's own */
    final float[] cameraStartFraction = new float[5];
    final float[] cameraEndFraction = new float[5];
    boolean cameraFractions;

    /** When the stretch starts and how long it lasts, as System.nanoTime. */
    long startNanos;
    long lengthNanos = 1L;
    /** False until there are two poses to move between: every frame then shows the end. */
    boolean moving;
    /**
     * True if the end is the run-ahead tick's: the stretch then shows the
     * race one tick on from the ticks it is due with (see {@link #motionAt}).
     */
    boolean ahead;
    /** For the profile: how long after the tick was due the span was handed over. */
    long handedLate;
    /** For the profile: when the keys this span's tick read last changed, as System.nanoTime. */
    long keysNanos;

    void setCar(int i, int mode, int x, int y, int z, int xz, int xy, int zy, int wxz, int wzy) {
        this.mode[i] = mode;
        int[][] s = mode == AT_START ? start : end;
        s[0][i] = x;
        s[1][i] = y;
        s[2][i] = z;
        s[3][i] = xz;
        s[4][i] = xy;
        s[5][i] = zy;
        s[6][i] = wxz;
        s[7][i] = wzy;
    }

    void setCarStart(int i, int x, int y, int z, int xz, int xy, int zy, int wxz, int wzy) {
        start[0][i] = x;
        start[1][i] = y;
        start[2][i] = z;
        start[3][i] = xz;
        start[4][i] = xy;
        start[5][i] = zy;
        start[6][i] = wxz;
        start[7][i] = wzy;
    }

    void copyFrom(PoseSpan o) {
        slots = o.slots;
        System.arraycopy(o.mode, 0, mode, 0, slots);
        for (int k = 0; k < 8; k++) {
            System.arraycopy(o.start[k], 0, start[k], 0, slots);
            System.arraycopy(o.end[k], 0, end[k], 0, slots);
        }
        cameraMode = o.cameraMode;
        System.arraycopy(o.cameraStart, 0, cameraStart, 0, 5);
        System.arraycopy(o.cameraEnd, 0, cameraEnd, 0, 5);
        System.arraycopy(o.cameraStartFraction, 0, cameraStartFraction, 0, 5);
        System.arraycopy(o.cameraEndFraction, 0, cameraEndFraction, 0, 5);
        cameraFractions = o.cameraFractions;
        startNanos = o.startNanos;
        lengthNanos = o.lengthNanos;
        moving = o.moving;
        ahead = o.ahead;
        handedLate = o.handedLate;
        keysNanos = o.keysNanos;
    }

    /**
     * How far through the stretch moment {@code now} is: 0 at the start, 1
     * at the end, a little more while the next tick is late (see
     * {@link #MAX_CARRY_ON}) when {@code carryOn}.
     */
    float alphaAt(long now, boolean carryOn) {
        if (!moving) {
            return 1F;
        }
        double elapsed = (double) (now - startNanos) / (double) lengthNanos;
        double max = carryOn ? 1D + MAX_CARRY_ON : 1D;
        return (float) Math.max(0D, Math.min(max, elapsed));
    }

    /**
     * The moment of the race a frame placed at {@code alpha} shows, on the
     * clock the ticks are due by, for the frame log's motion figures
     * ({@link FrameLog#shown(long, long, long, long, String, boolean)}): the
     * start's tick and as far on as {@code alpha} is, one tick later with the
     * run-ahead tick at the end; {@link FrameLog#NO_MOTION} while nothing moves.
     */
    long motionAt(float alpha) {
        if (!moving) {
            return FrameLog.NO_MOTION;
        }
        return startNanos + Math.round(alpha * (double) lengthNanos) + (ahead ? lengthNanos : 0L);
    }

    /** The cars and the camera at {@code alpha} (0 = start, 1 = end, more carries the motion on). */
    void at(float alpha, FramePose out) {
        boolean between = moving && alpha != 1F;
        out.alpha = between ? alpha : 1F;
        out.slots = slots;
        for (int i = 0; i < slots; i++) {
            int m = between ? mode[i] : mode[i] == AT_START ? AT_START : AT_END;
            if (m == MOVING) {
                out.car(i, alpha, start[0][i], start[1][i], start[2][i], start[3][i], start[4][i], start[5][i],
                        start[6][i], start[7][i], end[0][i], end[1][i], end[2][i], end[3][i], end[4][i], end[5][i],
                        end[6][i], end[7][i]);
            } else {
                int[][] s = m == AT_START ? start : end;
                out.car(i, s[0][i], s[1][i], s[2][i], s[3][i], s[4][i], s[5][i], s[6][i], s[7][i]);
            }
        }
        boolean cameraMoving = between && cameraMode == MOVING;
        if (cameraMoving) {
            out.camera(alpha, cameraStart[0], cameraStart[1], cameraStart[2], cameraStart[3], cameraStart[4],
                    cameraEnd[0], cameraEnd[1], cameraEnd[2], cameraEnd[3], cameraEnd[4]);
        } else {
            out.camera(cameraEnd[0], cameraEnd[1], cameraEnd[2], cameraEnd[3], cameraEnd[4]);
        }
        if (cameraFractions) {
            float[] a = cameraStartFraction;
            float[] b = cameraEndFraction;
            float t = cameraMoving ? alpha : 1F;
            out.shiftCamera(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t,
                    a[3] + (b[3] - a[3]) * t, a[4] + (b[4] - a[4]) * t);
        }
    }

    /** The camera's start and end are the game's own, at whole values. */
    void wholeCamera() {
        cameraFractions = false;
        java.util.Arrays.fill(cameraStartFraction, 0F);
        java.util.Arrays.fill(cameraEndFraction, 0F);
    }
}
