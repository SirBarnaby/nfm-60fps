package nfm.lit.features;

/**
 * The race's wind, for the looks that drift with it (smoke, later the
 * weather): one gentle wind a race, from the race's seed, so every extra
 * drifts the same way and a race replayed looks the same. It is only a
 * look: nothing in the race feels it.
 */
public final class Wind {

    /** The wind's speed, units a tick: light enough that smoke stays where it was made for a while. */
    private static final float SLOWEST = 0.6F;
    private static final float FASTEST = 1.5F;

    private Wind() {
    }

    /** The wind of the race started from {@code seed}, along x and z, into {@code out[0..1]}. */
    public static void of(long seed, float[] out) {
        Dice dice = new Dice(seed, "wind");
        double way = dice.between(0F, 6.2832F);
        float speed = dice.between(SLOWEST, FASTEST);
        out[0] = (float) Math.cos(way) * speed;
        out[1] = (float) Math.sin(way) * speed;
    }
}
