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
     * tick (0.25 is 13 ms); past that it stays at the end.
     */
    static final float MAX_CARRY_ON = 0.25F;

    int slots;
    final int[] mode = new int[FramePose.SLOTS];
    /* x, y, z, xz, xy, zy, wxz, wzy of each car at the start and at the end */
    final int[][] start = new int[8][FramePose.SLOTS];
    final int[][] end = new int[8][FramePose.SLOTS];
    int cameraMode;
    /* x, y, z, xz, zy */
    final int[] cameraStart = new int[5];
    final int[] cameraEnd = new int[5];

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
        if (between && cameraMode == MOVING) {
            out.camera(alpha, cameraStart[0], cameraStart[1], cameraStart[2], cameraStart[3], cameraStart[4],
                    cameraEnd[0], cameraEnd[1], cameraEnd[2], cameraEnd[3], cameraEnd[4]);
        } else {
            out.camera(cameraEnd[0], cameraEnd[1], cameraEnd[2], cameraEnd[3], cameraEnd[4]);
        }
    }
}
