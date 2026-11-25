package nfm.lit.audio;

import java.util.Random;

/**
 * The rebuilt engine sound's synthesis (the {@code enginesound} extra), in
 * plain Java, so its checks need no sound device: the game's own engine
 * recordings played one engine cycle at a time, shaped into the engine and
 * intake heard at a car's front and the exhaust heard at its back, and the
 * game's own skid recordings played for as long as a wheel slides.
 * <p>
 * A recording is a short loop of a few engine cycles. It is cut into them
 * once ({@link Cycles}); a {@link Voice} lays one cycle at each firing, each
 * under a raised-cosine window that overlaps its neighbours
 * (pitch-synchronous overlap-add), so higher revs lay the cycles closer
 * instead of playing them faster: the firing rate rises while each cycle
 * keeps its own sound. {@link Tone} makes the front and the back from the
 * voices' sum, {@link Skid} a wheel's skid.
 * <p>
 * Everything here is made once and reused: nothing is allocated while it
 * plays.
 */
public final class EngineGrains {

    /** Samples a second the rebuilt sound is made at. */
    public static final int RATE = 16_000;

    /* the raised-cosine window, as a table across its width, with a guard entry */
    private static final int WINDOW_STEPS = 1_024;
    private static final float[] WINDOW = new float[WINDOW_STEPS + 2];

    static {
        for (int i = 0; i < WINDOW.length; i++) {
            double u = Math.min(1D, (double) i / WINDOW_STEPS);
            WINDOW[i] = (float) (0.5D - 0.5D * Math.cos(2D * Math.PI * u));
        }
    }

    private EngineGrains() {
    }

    /** The window at {@code u} of the way across it (0 to 1): 0 at the edges, 1 in the middle. */
    static float window(float u) {
        float f = u * WINDOW_STEPS;
        int i = (int) f;
        if (f < 0F || i >= WINDOW_STEPS) {
            return 0F;
        }
        return WINDOW[i] + (WINDOW[i + 1] - WINDOW[i]) * (f - i);
    }

    /* ------------------------------------------------------- the cycles */

    /** The shortest and longest engine cycle looked for, seconds. */
    private static final float SHORTEST = 0.002F;
    private static final float LONGEST = 0.125F;
    /** A shorter lag counts as the cycle when its likeness is this share of the best one's. */
    private static final float NEAR_BEST = 0.95F;
    /** Samples of the loop compared at each lag, at most: enough to see a cycle repeat, quick enough not to hold up the sound. */
    private static final int COMPARED = 2_048;

    /** One recording, a loop, cut into its engine cycles. */
    public static final class Cycles {
        final float[] x;
        /** The loop with a sample before it and three after, each from its other end, so {@link #at} reads it straight. */
        private final float[] padded;
        /** Samples a second of the recording. */
        public final int rate;
        /** One cycle's length, samples of the recording (between samples). */
        public final float period;
        /** Whole cycles in the loop. */
        public final int count;

        Cycles(float[] x, int rate, float period) {
            this.x = x;
            this.rate = rate;
            this.period = period;
            this.count = Math.max(1, (int) (x.length / period));
            int n = x.length;
            padded = new float[n + 4];
            for (int k = 0; n > 0 && k < padded.length; k++) {
                padded[k] = x[Math.floorMod(k - 1, n)];
            }
        }

        /** The recording at {@code pos} samples, between samples, the loop going round. */
        float at(double pos) {
            int n = x.length;
            double wrapped = pos;
            if (wrapped < 0D || wrapped >= n) { // most reads fall inside the loop: the remainder is the costly part
                wrapped %= n;
                if (wrapped < 0D) {
                    wrapped += n;
                }
            }
            int i = (int) wrapped;
            float t = (float) (wrapped - i);
            float a = padded[i];
            float b = padded[i + 1];
            float c = padded[i + 2];
            float d = padded[i + 3];
            // a cubic through the four samples around the point (Catmull-Rom)
            return b + 0.5F * t * (c - a + t * (2F * a - 5F * b + 4F * c - d + t * (3F * (b - c) + d - a)));
        }
    }

    /** Cuts recording {@code x} (samples from -1 to 1, {@code rate} a second, a loop) into its cycles. */
    public static Cycles cut(float[] x, int rate) {
        return new Cycles(x, rate, period(x, rate));
    }

    /**
     * The length of one cycle of loop {@code x}, in samples: the lag at which
     * the loop is most like itself (its circular autocorrelation), between
     * 2 ms and an eighth of a second and at most half the loop; of lags
     * nearly as good, the shortest, so two cycles are not taken for one.
     * Refined between samples by the parabola through the peak. The whole
     * loop when nothing repeats. Compares at most {@link #COMPARED} samples
     * at each lag, so a recording is cut in a few milliseconds.
     */
    public static float period(float[] x, int rate) {
        int n = x.length;
        int lo = Math.max(2, (int) (rate * SHORTEST));
        int hi = Math.min(n / 2, (int) (rate * LONGEST));
        if (hi <= lo + 2) {
            return n;
        }
        double mean = 0D;
        for (float v : x) {
            mean += v;
        }
        mean /= n;
        float[] centred = new float[n];
        for (int i = 0; i < n; i++) {
            centred[i] = (float) (x[i] - mean);
        }
        int m = Math.min(n, COMPARED);
        double energy = 0D;
        for (int i = 0; i < m; i++) {
            energy += centred[i] * centred[i];
        }
        if (energy <= 1e-9D) {
            return n;
        }
        float[] r = new float[hi + 2];
        float best = -Float.MAX_VALUE;
        for (int lag = lo - 1; lag <= hi + 1; lag++) {
            double s = 0D;
            int split = Math.min(m, n - lag);
            for (int i = 0; i < split; i++) {
                s += centred[i] * centred[i + lag];
            }
            for (int i = split; i < m; i++) {
                s += centred[i] * centred[i + lag - n];
            }
            r[lag] = (float) (s / energy);
            if (lag >= lo && lag <= hi) {
                best = Math.max(best, r[lag]);
            }
        }
        if (best <= 0F) {
            return n;
        }
        for (int lag = lo; lag <= hi; lag++) {
            if (r[lag] >= NEAR_BEST * best && r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1]) {
                float a = r[lag - 1];
                float b = r[lag];
                float c = r[lag + 1];
                float bend = a - 2F * b + c;
                float shift = bend < 0F ? 0.5F * (a - c) / bend : 0F;
                return lag + Math.max(-0.5F, Math.min(0.5F, shift));
            }
        }
        return n;
    }

    /* -------------------------------------------------------- a voice */

    /** A recording played one cycle at a time. */
    public static final class Voice {
        /** Grains sounding at once, at most: about twice the pitch, and the pitch is at most 2. */
        private static final int GRAINS = 12;
        /** The share of grains that take another cycle of the loop than the next one, for a running engine's unevenness. */
        private static final float WANDER = 0.12F;

        private final double[] start = new double[GRAINS];
        private final double[] from = new double[GRAINS];
        private final float[] width = new float[GRAINS];
        private final float[] level = new float[GRAINS];
        private int grains;
        private Cycles cycles;
        /** Output samples made so far, and where the next grain's middle falls. */
        private long time;
        private double next;
        private int cycle;
        private float lastGain;

        /** Plays {@code c} from now on (null for nothing), from its first cycle. */
        public void use(Cycles c) {
            cycles = c;
            grains = 0;
            next = time;
            cycle = 0;
        }

        /** True if it made sound in the last block or still has grains sounding. */
        public boolean sounding() {
            return lastGain > 0F || grains > 0;
        }

        /**
         * Adds {@code n} samples of the recording to {@code out[off..]}:
         * fired {@code pitch} times as often as it was recorded (1 as
         * recorded), {@code gain} loud by the end of the block (from where
         * the last block ended).
         */
        public void add(float[] out, int off, int n, float pitch, float gain, Random random) {
            long end = time + n;
            if (cycles == null || gain <= 0F && lastGain <= 0F) {
                grains = 0;
                next = end;
                time = end;
                lastGain = 0F;
                return;
            }
            pitch = Math.max(0.25F, Math.min(4F, pitch));
            double read = (double) cycles.rate / RATE; // samples of the recording per sample made
            float step = (float) read; // the same for every grain: use() starts afresh with new recordings
            float natural = (float) (cycles.period / read); // one cycle as recorded, samples made
            float spacing = natural / pitch; // between firings
            float half = Math.max(natural, spacing); // the window: as wide as a cycle, or the gap
            float keep = spacing / half; // the windows overlap half/spacing times over: keeps the level
            while (next - half < end) {
                if (grains < GRAINS) {
                    int g = grains++;
                    double centre = (double) cycle * cycles.period;
                    start[g] = next - half;
                    width[g] = 2F * half;
                    from[g] = centre - half * read;
                    level[g] = keep;
                }
                next += spacing;
                cycle = random.nextFloat() < WANDER ? random.nextInt(cycles.count) : (cycle + 1) % cycles.count;
            }
            float g0 = lastGain;
            float dg = (gain - g0) / n;
            for (int g = 0; g < grains; g++) {
                double s = start[g];
                int a = (int) Math.max(0L, (long) Math.ceil(s) - time);
                int b = (int) Math.min(n, (long) Math.ceil(s + width[g]) - time);
                float inv = 1F / width[g];
                for (int i = a; i < b; i++) {
                    double t = time + i - s;
                    float w = window((float) (t * inv));
                    out[off + i] += w * level[g] * (g0 + dg * i) * cycles.at(from[g] + t * step);
                }
            }
            // grains that have ended go
            for (int g = grains - 1; g >= 0; g--) {
                if (start[g] + width[g] <= end) {
                    int last = --grains;
                    start[g] = start[last];
                    width[g] = width[last];
                    from[g] = from[last];
                    level[g] = level[last];
                }
            }
            time = end;
            lastGain = gain;
        }
    }

    /* ---------------------------------------------------------- filters */

    /** A two-pole filter, from the audio EQ cookbook. */
    static final class Biquad {
        private float b0;
        private float b1;
        private float b2;
        private float a1;
        private float a2;
        private float x1;
        private float x2;
        private float y1;
        private float y2;

        Biquad lowPass(float hz, float q) {
            double w = 2D * Math.PI * hz / RATE;
            double alpha = Math.sin(w) / (2D * q);
            double c = Math.cos(w);
            return set((1D - c) / 2D, 1D - c, (1D - c) / 2D, 1D + alpha, -2D * c, 1D - alpha);
        }

        Biquad highPass(float hz, float q) {
            double w = 2D * Math.PI * hz / RATE;
            double alpha = Math.sin(w) / (2D * q);
            double c = Math.cos(w);
            return set((1D + c) / 2D, -(1D + c), (1D + c) / 2D, 1D + alpha, -2D * c, 1D - alpha);
        }

        /** A band around {@code hz}, its peak at 1. */
        Biquad bandPass(float hz, float q) {
            double w = 2D * Math.PI * hz / RATE;
            double alpha = Math.sin(w) / (2D * q);
            double c = Math.cos(w);
            return set(alpha, 0D, -alpha, 1D + alpha, -2D * c, 1D - alpha);
        }

        private Biquad set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
            b0 = (float) (nb0 / na0);
            b1 = (float) (nb1 / na0);
            b2 = (float) (nb2 / na0);
            a1 = (float) (na1 / na0);
            a2 = (float) (na2 / na0);
            return this;
        }

        float run(float x) {
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = Math.abs(y) < 1e-20F ? 0F : y; // no denormals in the silence
            return y1;
        }

        void clear() {
            x1 = x2 = y1 = y2 = 0F;
        }
    }

    /** A soft clip: about x for small x, never past 1. */
    static float soft(float x) {
        if (x > 3F) {
            return 1F;
        }
        if (x < -3F) {
            return -1F;
        }
        float x2 = x * x;
        return x * (27F + x2) / (27F + 9F * x2);
    }

    /* ------------------------------------------------------ front, back */

    /** The engine's sound made into the front (engine and intake) and the back (exhaust). */
    public static final class Tone {
        /** The pops after a lift: how long they go on, seconds, how many a second, and how long one rings. */
        static final float POP_TIME = 0.8F;
        static final float POPS_A_SECOND = 5F;
        static final float POP_RING = 0.006F;

        private final Biquad thin = new Biquad().highPass(120F, 0.7F);
        private final Biquad intake = new Biquad().bandPass(700F, 1.4F);
        private final Biquad body = new Biquad().lowPass(1_000F, 0.7F);
        private final Biquad low = new Biquad().lowPass(180F, 0.7F);
        private final Biquad popTone = new Biquad().lowPass(2_500F, 0.9F);
        private float popsLeft;
        private float pop;
        private final float popFade = (float) Math.exp(-1D / (POP_RING * RATE));

        /** A lift of the throttle at high revs: the exhaust pops for a moment. */
        public void lifted() {
            popsLeft = POP_TIME;
        }

        /** Stops the pops: the engine stopped or the car left the ground. */
        public void calm() {
            popsLeft = 0F;
        }

        /** True while it is popping. */
        public boolean popping() {
            return popsLeft > 0F || pop > 0.001F;
        }

        /**
         * Shapes {@code n} samples of the engine ({@code in}) into the front
         * and the back, {@code load} the throttle (0 to 1, smoothed).
         */
        public void shape(float[] in, float[] front, float[] back, int n, float load, Random random) {
            float growl = 1F + 2F * load;
            float intakeLevel = 0.3F + 0.9F * load;
            float lowLevel = 0.4F + 0.4F * load;
            float popChance = POPS_A_SECOND / RATE;
            for (int i = 0; i < n; i++) {
                float x = in[i];
                front[i] = 0.8F * thin.run(x) + intakeLevel * intake.run(x);
                float b = body.run(x) + lowLevel * low.run(x);
                b = soft(growl * b) / growl; // as loud, its peaks rounded: harmonics, a growl under load
                if (popsLeft > 0F && random.nextFloat() < popChance) {
                    pop = 0.35F + 0.35F * random.nextFloat();
                }
                if (pop > 0.001F) {
                    b += popTone.run(pop * (2F * random.nextFloat() - 1F));
                    pop *= popFade;
                } else {
                    pop = 0F;
                }
                back[i] = b;
            }
            popsLeft = Math.max(0F, popsLeft - (float) n / RATE);
        }

        /** Forgets the filters' state, for a fresh start. */
        public void clear() {
            thin.clear();
            intake.clear();
            body.clear();
            low.clear();
            popTone.clear();
            popsLeft = 0F;
            pop = 0F;
        }
    }

    /* ----------------------------------------------------------- skids */

    /** A wheel's skid: a skid recording in overlapping grains from its steady middle, endless, without a seam. */
    public static final class Skid {
        /** A grain's length and the gap between grains, samples made: 80 ms, overlapping by half. */
        static final int GRAIN = RATE * 80 / 1_000;
        static final int HOP = GRAIN / 2;
        /** The steady middle of a recording, as shares of its length: its start and its fade left out. */
        static final float FROM = 0.15F;
        static final float TO = 0.85F;
        private static final int GRAINS = 3;
        /**
         * The square root of the window at each sample of a grain: grains
         * from random places add as noise does, and a sine window keeps
         * their power even.
         */
        private static final float[] ROOT_WINDOW = new float[GRAIN];

        static {
            float inv = 1F / GRAIN;
            for (int t = 0; t < GRAIN; t++) {
                ROOT_WINDOW[t] = (float) Math.sqrt(window(t * inv));
            }
        }

        private final double[] from = new double[GRAINS];
        private final long[] start = new long[GRAINS];
        private final float[] step = new float[GRAINS];
        private int grains;
        private float[] x;
        private int rate;
        private long time;
        private long next;
        private float lastGain;

        /** Plays recording {@code samples} ({@code r} a second) from the next grain on. */
        public void use(float[] samples, int r) {
            if (samples != x) {
                x = samples;
                rate = r;
                grains = 0;
                next = time;
            }
        }

        /** True if it made sound in the last block or still has grains sounding. */
        public boolean sounding() {
            return lastGain > 0F || grains > 0;
        }

        /** Adds {@code n} samples at {@code pitch} (1 as recorded), {@code gain} loud by the block's end. */
        public void add(float[] out, int off, int n, float pitch, float gain, Random random) {
            long end = time + n;
            if (x == null || gain <= 0F && lastGain <= 0F) {
                grains = 0;
                next = end;
                time = end;
                lastGain = 0F;
                return;
            }
            float read = (float) rate / RATE * pitch;
            int length = x.length;
            float span = GRAIN * read;
            float lo = FROM * length;
            float hi = Math.max(lo, TO * length - span);
            while (next < end) {
                if (grains < GRAINS) {
                    int g = grains++;
                    start[g] = next;
                    from[g] = lo + random.nextFloat() * (hi - lo);
                    step[g] = read;
                }
                next += HOP;
            }
            float g0 = lastGain;
            float dg = (gain - g0) / n;
            for (int g = 0; g < grains; g++) {
                int a = (int) Math.max(0L, start[g] - time);
                int b = (int) Math.min(n, start[g] + GRAIN - time);
                for (int i = a; i < b; i++) {
                    long t = time + i - start[g];
                    double pos = from[g] + t * step[g];
                    int p = (int) pos;
                    if (p + 1 >= length) {
                        continue;
                    }
                    float f = (float) (pos - p);
                    float s = x[p] + (x[p + 1] - x[p]) * f;
                    out[off + i] += ROOT_WINDOW[(int) t] * (g0 + dg * i) * s; // 0 <= t < GRAIN, from a and b
                }
            }
            for (int g = grains - 1; g >= 0; g--) {
                if (start[g] + GRAIN <= end) {
                    int last = --grains;
                    start[g] = start[last];
                    from[g] = from[last];
                    step[g] = step[last];
                }
            }
            time = end;
            lastGain = gain;
        }
    }
}
