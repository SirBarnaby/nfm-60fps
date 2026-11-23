package nfm.lit.features;

/**
 * A feature's own random numbers, started from the race's seed and the
 * feature's name, so the same race always looks the same and no feature
 * takes a number from the race's own streams. Not for race features, which
 * draw from a race stream of their own.
 */
public final class Dice {

    private long state;

    /** Dice for {@code feature} in the race started from {@code seed}. */
    public Dice(long seed, String feature) {
        reset(seed, feature);
    }

    public void reset(long seed, String feature) {
        state = mix(seed ^ (feature.hashCode() * 0x9E3779B97F4A7C15L));
        if (state == 0L) {
            state = 0x2545F4914F6CDD1DL;
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number from 0 (included) to 1 (not). */
    public float next() {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        return (state >>> 40) / (float) (1 << 24);
    }

    /** A number from {@code from} to {@code to}. */
    public float between(float from, float to) {
        return from + (to - from) * next();
    }

    /** A whole number from 0 to {@code n - 1}. */
    public int below(int n) {
        return Math.min(n - 1, (int) (next() * n));
    }
}
