package nfm.lit.features;

import java.util.Arrays;

/**
 * What one extra shows of a car that another draws with: a bent wheel's
 * wobble (the damage extra bends it, the skidmarks make its marks wavy) and
 * where the debris that stays is kept (clods thrown by the skidmarks stay
 * when the damage extra is on). Cleared when a race starts; drawing-only
 * state, never part of a race.
 */
public final class CarLooks {

    /** How far each wheel (car * 4 + wheel) is bent, degrees; 0 runs true. */
    private static float[] bent = new float[0];
    /** Each wheel's radius, for how often its wobble comes round. */
    private static float[] radius = new float[0];
    /** Where pieces that stay are dropped, or null while no extra keeps them. */
    private static Debris debris;
    /**
     * While {@link CarLook#bend} is asked about a wheel's polygon: how far
     * the game is about to spin that wheel about its axle and steer it,
     * degrees. A feature that wants the polygon drawn as it lies after the
     * spin and the steering (a tyre flattened against the ground) turns the
     * corners itself and sets {@link #baked}: the game then draws them as
     * given, without turning them again.
     */
    public static int spin;
    public static int turn;
    public static boolean baked;

    private CarLooks() {
    }

    /** A race of {@code cars} cars starts: every wheel runs true. */
    public static void clear(int cars) {
        int n = cars * RaceView.WHEELS;
        if (bent.length < n) {
            bent = new float[n];
            radius = new float[n];
        }
        Arrays.fill(bent, 0F);
        Arrays.fill(radius, 0F);
    }

    /** Wheel {@code w} of car {@code c} is bent {@code degrees}, radius {@code r}. */
    public static void bend(int c, int w, float degrees, float r) {
        int at = c * RaceView.WHEELS + w;
        if (at >= 0 && at < bent.length) {
            bent[at] = degrees;
            radius[at] = r;
        }
    }

    /** How far wheel {@code w} of car {@code c} is bent, degrees. */
    public static float bent(int c, int w) {
        int at = c * RaceView.WHEELS + w;
        return at >= 0 && at < bent.length ? bent[at] : 0F;
    }

    /** Wheel {@code w} of car {@code c}'s radius (0 if not known). */
    public static float radius(int c, int w) {
        int at = c * RaceView.WHEELS + w;
        return at >= 0 && at < radius.length ? radius[at] : 0F;
    }

    /** Pieces that land are kept here from now on, or not ({@code null}). */
    public static void keepDebris(Debris d) {
        debris = d;
    }

    /** Where landed pieces are kept, or null. */
    public static Debris debris() {
        return debris;
    }
}
