package nfm.lit.features;

/**
 * The few lines of synthesis the extras make their sounds from, each made
 * once and the same every run: a sine, an exponential decay, white noise
 * from a fixed seed, and the step to 16-bit samples. Shared so every
 * extra's clips are made the same way.
 */
public final class Synth {

    private Synth() {
    }

    /** A sine of {@code hz} at {@code t} seconds. */
    public static float sine(float hz, float t) {
        return (float) Math.sin(2D * Math.PI * hz * t);
    }

    /** An exponential decay to 1/e in {@code time} seconds, at {@code t} seconds. */
    public static float decay(float t, float time) {
        return (float) Math.exp(-t / time);
    }

    /** {@code out} as 16-bit samples, its peak at {@code level} of full scale. */
    public static short[] pcm(float[] out, float level) {
        float peak = 1e-6F;
        for (float v : out) {
            peak = Math.max(peak, Math.abs(v));
        }
        float scale = level * Short.MAX_VALUE / peak;
        short[] s = new short[out.length];
        for (int i = 0; i < out.length; i++) {
            s[i] = (short) Math.round(out[i] * scale);
        }
        return s;
    }

    /** White noise from a fixed seed, -1 to 1. */
    public static final class Noise {
        private long state;

        public Noise(long seed) {
            state = seed * 0x9E3779B97F4A7C15L + 1L;
        }

        public float next() {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            return ((state >>> 40) / (float) (1 << 24)) * 2F - 1F;
        }
    }
}
