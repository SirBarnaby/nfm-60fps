package nfm.lit;

/**
 * Smooth effects: dust puffs and flying debris shown on every frame drawn,
 * not only once a game tick.
 * <p>
 * The game moves a puff or a chip of debris once a tick, while it draws it,
 * and a frame between two ticks shows the state of the later one, as the
 * cars at the end of their way between the two. So each effect is drawn
 * where it was shown a tick before ("the trail"), moved {@code alpha} of the
 * way to where it is now: dust also grows and changes shape on the way, and
 * a frame at the tick itself is exactly the original picture.
 * <p>
 * Only where the effects are drawn changes: their state, the random numbers
 * they draw from and the race never see any of it. Frames drawn the whole
 * way take {@link #alpha} directly; a scene kept for a whole tick (see
 * {@link RetainedScene}) gets the trail as a second place for each corner
 * and moves the corners itself, frame by frame.
 */
final class EffectTrails {

    private EffectTrails() {
    }

    /** Smooth effects are on for what is being drawn now. */
    static boolean on;
    /** How far between the two ticks this frame is; NaN while recording a scene kept for the whole tick. */
    private static float alpha = Float.NaN;

    /* corners of the trail, for the effect being drawn now (game thread) */
    static final int[] trailX = new int[8];
    static final int[] trailY = new int[8];
    /* a chip's corners a tick ago, in the world, then turned by the camera */
    static final int[] worldX = new int[3];
    static final int[] worldY = new int[3];
    static final int[] worldZ = new int[3];
    /* the corners drawn this frame, between the trail and now */
    static final int[] drawX = new int[8];
    static final int[] drawY = new int[8];

    /**
     * A race frame starts: {@code frameAlpha} of the way from the last tick
     * to this one, or NaN when it records the scene kept for the whole tick.
     */
    static void begin(boolean smooth, float frameAlpha) {
        on = smooth;
        alpha = frameAlpha;
    }

    /** The race frame is drawn; the tick's own drawing never has trails. */
    static void end() {
        on = false;
        alpha = Float.NaN;
    }

    /** True when the effect is drawn moved here, false when the kept scene moves it. */
    static boolean direct() {
        return !Float.isNaN(alpha);
    }

    /** The corners between the trail and now, for a frame drawn the whole way. */
    static void between(int[] nowX, int[] nowY, int n) {
        float a = Math.max(0F, Math.min(1.25F, alpha));
        for (int k = 0; k < n; k++) {
            drawX[k] = Math.round(trailX[k] + (nowX[k] - trailX[k]) * a);
            drawY[k] = Math.round(trailY[k] + (nowY[k] - trailY[k]) * a);
        }
    }

    /**
     * What one dust puff looked like the last two ticks it was drawn: its
     * stage, its centre and its corners' distances from the centre, before
     * the camera divides them by depth.
     */
    static final class Puff {
        private boolean has;
        private int stage;
        private int x;
        private int y;
        private int z;
        private final double[] corners = new double[16];

        private boolean hadBefore;
        int stageBefore;
        int xBefore;
        int yBefore;
        int zBefore;
        final double[] cornersBefore = new double[16];

        /**
         * Notes the puff as drawn now, at {@code stage}: another drawing in
         * the same tick (a frame more, or the run-ahead again) only replaces
         * what this tick looks like.
         *
         * @return true if the puff as shown the tick before is known
         */
        boolean note(int stage, int x, int y, int z, double[] corners) {
            if (!has || this.stage != stage) {
                hadBefore = has;
                stageBefore = this.stage;
                xBefore = this.x;
                yBefore = this.y;
                zBefore = this.z;
                System.arraycopy(this.corners, 0, cornersBefore, 0, 16);
            }
            has = true;
            this.stage = stage;
            this.x = x;
            this.y = y;
            this.z = z;
            System.arraycopy(corners, 0, this.corners, 0, 16);
            // the stage before, and not too far: a puff that started again does not glide
            return hadBefore && stageBefore == stage - 1 && Math.abs(x - xBefore) + Math.abs(y - yBefore)
                    + Math.abs(z - zBefore) < 600;
        }
    }
}
