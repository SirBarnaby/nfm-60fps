package nfm.lit.gpu;

import java.util.Arrays;

/**
 * Dynamic resolution: picks the scale the race scene is drawn at, as a share
 * of the size it is shown at, so the graphics card finishes each frame within
 * the frame budget. Only the card's own time counts: when the processor is
 * what holds frames back, a smaller scene would be blurrier and no faster.
 * <p>
 * It steps down as soon as the card has used more than 90% of the budget,
 * aiming at 80% (taking drawing time to grow with the pixels, so with the
 * square of the scale), and back up one step at a time when the card has
 * stayed under 75% for a while and the step should leave it under 85%. Much
 * of a frame's cost (the present, resolving the samples) does not shrink with
 * the scene, so the square overstates what a step up costs; a step that has
 * to be taken back within two seconds makes the next try wait twice as long,
 * up to 16 seconds, rather than going up and down. The gap between the
 * thresholds keeps the picture from pumping, and it judges the middle of the
 * last five frames' times, so one slow frame (a texture upload, the window
 * moving) is not taken for a slow card. The scale moves in twentieths. After
 * each change, and when races start being drawn again after a pause (a race
 * starting), it waits for the card's times at the new size before judging.
 */
public final class DynamicResolution {

    private static final float STEPS = 20F;
    private static final float STEP = 1F / STEPS;
    private static final double DOWN_ABOVE = 0.90;
    private static final double DOWN_TO = 0.80;
    private static final double UP_BELOW = 0.75;
    private static final double UP_LIMIT = 0.85;
    /** The card's times lag a frame or two, so after a change the first ones are still of the old size. */
    private static final int SETTLE_FRAMES = 8;
    /** When races start being drawn, the first frames upload and warm up, so it waits half a second. */
    private static final int START_FRAMES = 30;
    /** A longer gap between scenes is a pause (menus, loading), after which the first frames are not typical. */
    private static final long PAUSE_NANOS = 500_000_000L;
    private static final long FIRST_UP_WAIT_NANOS = 1_000_000_000L;
    private static final long LONGEST_UP_WAIT_NANOS = 16_000_000_000L;
    /** A step down this soon after a step up means the step up did not fit. */
    private static final long TAKEN_BACK_NANOS = 2_000_000_000L;
    /** Holding a scale this long makes trying upward cheap again. */
    private static final long SETTLED_NANOS = 20_000_000_000L;
    /* rising times are taken in quickly, falling ones slowly */
    private static final double RISE = 0.25;
    private static final double FALL = 0.05;
    private static final int MEDIAN_OF = 5;

    private float scale = 1F;
    /** The smoothed share of the budget the card took. */
    private double load;
    private int framesSinceChange;
    /** The frames to wait now before judging: SETTLE_FRAMES after a change, START_FRAMES at a start. */
    private int settle = START_FRAMES;
    private long lastUpdate;
    /** When the load last went under UP_BELOW, 0 while it is not. */
    private long calmSince;
    private long lastUp;
    private long lastChange;
    private long upWait = FIRST_UP_WAIT_NANOS;
    private final double[] recent = new double[MEDIAN_OF];
    private final double[] sorted = new double[MEDIAN_OF];

    /** The scale to draw the next scene at, from the lowest allowed to 1. */
    public float scale() {
        return scale;
    }

    /**
     * Takes the card's time for the frame it last finished and returns the
     * scale to draw the next scene at.
     *
     * @param gpuNanos    how long the card took over its last frame, 0 if it cannot say
     * @param budgetNanos the time a frame may take, 0 when there is no frame rate to hold (no limit):
     *                    then the scene is drawn whole
     * @param lowest      the lowest scale allowed, 1 or more for none
     * @param now         {@link System#nanoTime()}
     */
    public float update(long gpuNanos, long budgetNanos, float lowest, long now) {
        if (lastUpdate == 0L || now - lastUpdate > PAUSE_NANOS) {
            framesSinceChange = 0;
            settle = START_FRAMES;
            calmSince = 0L;
        }
        lastUpdate = now;
        if (budgetNanos <= 0L || lowest >= 1F) {
            change(1F, now);
            return scale;
        }
        float floor = Math.max(STEP, lowest);
        if (scale < floor) {
            change(floor, now); // the lowest allowed was raised, or the window made smaller
        }
        if (gpuNanos <= 0L || !judge(gpuNanos / (double) budgetNanos)) {
            return scale;
        }
        if (now - lastChange > SETTLED_NANOS) {
            upWait = FIRST_UP_WAIT_NANOS;
        }
        if (load > DOWN_ABOVE && scale > floor) {
            if (now - lastUp < TAKEN_BACK_NANOS) {
                upWait = Math.min(LONGEST_UP_WAIT_NANOS, upWait * 2);
            }
            float aim = (float) Math.floor(scale * Math.sqrt(DOWN_TO / load) * STEPS + 1e-3) / STEPS;
            change(Math.max(floor, Math.min(scale - STEP, aim)), now);
        } else if (load < UP_BELOW && scale < 1F) {
            if (calmSince == 0L) {
                calmSince = now;
            }
            float next = Math.min(1F, scale + STEP);
            double grown = next / scale;
            if (now - calmSince >= upWait && load * grown * grown < UP_LIMIT) {
                change(next, now);
                lastUp = now;
            }
        } else {
            calmSince = 0L;
        }
        return scale;
    }

    /**
     * Takes in one frame's share of the budget; false while there are not
     * yet enough of them at this size to judge by.
     */
    private boolean judge(double share) {
        framesSinceChange++;
        if (framesSinceChange < settle) {
            return false;
        }
        recent[framesSinceChange % MEDIAN_OF] = share;
        int counted = framesSinceChange - settle + 1;
        if (counted < MEDIAN_OF) {
            return false;
        }
        System.arraycopy(recent, 0, sorted, 0, MEDIAN_OF);
        Arrays.sort(sorted);
        double middle = sorted[MEDIAN_OF / 2];
        if (counted == MEDIAN_OF) {
            load = middle; // start from times at this size
            return false;
        }
        load += (middle - load) * (middle > load ? RISE : FALL);
        return true;
    }

    private void change(float next, long now) {
        if (next != scale) {
            scale = next;
            framesSinceChange = 0;
            settle = SETTLE_FRAMES;
            calmSince = 0L;
            lastChange = now;
        }
    }
}
