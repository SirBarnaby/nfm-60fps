package nfm.lit.features.wingtrail;

import nfm.lit.features.RaceView;

/**
 * The cars' wing settings: for each car with a wing that trails vapour, its
 * wing tips, the widest points at the back of its model. Only the Radical
 * One has one (effects, "The Radical One's wing trail").
 * <p>
 * Kept in the model file's own numbers, so they read against
 * {@code radicalone.rad}; the game scales a model as it loads it, and the
 * wheels with it, so the tips are scaled by the same factor, found from the
 * car's front wheel.
 */
final class WingTips {

    /** The Radical One's place in the car list ({@code radicalone}). */
    static final int RADICAL_ONE = 14;
    /** Its wing's right outer rear corner in {@code radicalone.rad}: {@code p(70,-55,-135)}, mirrored on the left. */
    private static final float[] RADICAL_ONE_TIP = {70F, -55F, -135F};
    /** Its front right wheel's x in the same file ({@code w(42,...)}), to find the game's scale. */
    private static final float RADICAL_ONE_WHEEL_X = 42F;

    private WingTips() {
    }

    /** Car model {@code model}'s right wing tip in its model file's numbers, or null if it has no trailing wing. */
    static float[] of(int model) {
        return model == RADICAL_ONE ? RADICAL_ONE_TIP : null;
    }

    /** How much the game scaled car {@code c}'s model as it loaded it. */
    static float scale(RaceView race, int c) {
        float wheel = Math.abs(race.keyX[c * RaceView.WHEELS + 1]);
        return wheel > 0F ? wheel / RADICAL_ONE_WHEEL_X : 1F;
    }
}
