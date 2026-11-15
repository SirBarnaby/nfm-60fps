package nfm.lit;

import java.security.SecureRandom;

/**
 * The game's random number generator, one instance per independent stream.
 * <p>
 * The algorithm is the original {@code Medium.random()} unchanged: three decimal
 * digits that each step up or down by one per call, with the returned digit
 * rotating between them, and fresh digits and directions every 21 calls. The
 * only difference is where those fresh values come from. The original asked
 * {@link Math#random()}, a global stream seeded from the clock, so no two races
 * could ever be the same. Each stream here draws them from its own seedable
 * SplitMix64 source instead, so a race replays exactly from its seed.
 * <p>
 * The game keeps three streams (see {@link Medium}): the race itself (AI and
 * car physics), the race camera, and cosmetics (dust, sparks, sounds, menus).
 * Keeping them apart means drawing or sound can never change how a race plays.
 */
public final class RaceRandom {

    private static final SecureRandom ENTROPY = new SecureRandom();

    private int cntrn;
    private final boolean[] diup = new boolean[3];
    private final int[] rand = new int[3];
    private int trn;
    private long source;

    /** A stream seeded from system entropy, for anything that is not replayed. */
    public RaceRandom() {
        reseed(ENTROPY.nextLong());
    }

    /**
     * Restarts the stream. The digits start as the original's did at class load:
     * all zero, counting down, with fresh values drawn on the first call.
     */
    public void reseed(long seed) {
        source = seed;
        cntrn = 0;
        trn = 0;
        for (int i = 0; i < 3; i++) {
            rand[i] = 0;
            diup[i] = false;
        }
    }

    /** One value from the original algorithm: 0.0, 0.1, ... 0.9. */
    public float next() {
        if (cntrn == 0) {
            int i = 0;
            do {
                rand[i] = (int) (10D * nextDouble());
                diup[i] = nextDouble() <= nextDouble();
            } while (++i < 3);
            cntrn = 20;
        } else {
            cntrn--;
        }
        int j = 0;
        do {
            if (diup[j]) {
                rand[j]++;
                if (rand[j] == 10) {
                    rand[j] = 0;
                }
            } else {
                rand[j]--;
                if (rand[j] == -1) {
                    rand[j] = 9;
                }
            }
        } while (++j < 3);
        trn++;
        if (trn == 3) {
            trn = 0;
        }
        return rand[trn] / 10F;
    }

    /**
     * A uniform double in [0, 1), the replacement for {@link Math#random()} at
     * the call sites that used it directly.
     */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    private long nextLong() {
        long z = (source += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Length of the state {@link #exportState(int[])} writes. */
    static final int STATE_SIZE = 10;

    /** The whole state as ints, for recorded races: see {@link #importState(int[])}. */
    int[] exportState() {
        return exportState(new int[STATE_SIZE]);
    }

    /** Writes the whole state into {@code s}, without allocating, and returns it. */
    int[] exportState(int[] s) {
        s[0] = (int) source;
        s[1] = (int) (source >>> 32);
        s[2] = cntrn;
        s[3] = trn;
        for (int i = 0; i < 3; i++) {
            s[4 + i] = rand[i];
            s[7 + i] = diup[i] ? 1 : 0;
        }
        return s;
    }

    /** Puts back a state from {@link #exportState()}, rewinding the stream to that point. */
    void importState(int[] s) {
        source = (s[0] & 0xFFFFFFFFL) | ((long) s[1] << 32);
        cntrn = s[2];
        trn = s[3];
        for (int i = 0; i < 3; i++) {
            rand[i] = s[4 + i];
            diup[i] = s[7 + i] != 0;
        }
    }

    /** Folds the whole state into a hash, for the per-tick race checks. */
    long fingerprint() {
        long h = source;
        h = h * 31 + cntrn;
        h = h * 31 + trn;
        for (int i = 0; i < 3; i++) {
            h = h * 31 + rand[i];
            h = h * 31 + (diup[i] ? 1 : 0);
        }
        return h;
    }

    /*
     * Streams derived from a race seed. Each id gives an independent sequence,
     * so a new stream needs an id of its own.
     */
    /** Cosmetics from the moment the stage loads. */
    static final int STREAM_EFFECTS = 1;
    /** The race camera. */
    static final int STREAM_CAMERA = 2;
    /** Cosmetics from the countdown on, so the intro's effects do not shift them. */
    static final int STREAM_RACE_EFFECTS = 3;
    /** The order the opponents are dealt in. */
    static final int STREAM_OPPONENTS = 4;

    /** Derives an independent seed for a second stream from a race seed. */
    static long derive(long seed, long stream) {
        return mix64(seed + stream * 0x9E3779B97F4A7C15L);
    }

    /** MurmurHash3's 64-bit finaliser: every input bit affects every output bit. */
    static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    /** A fresh random seed for a new race. */
    static long newSeed() {
        return ENTROPY.nextLong();
    }
}
