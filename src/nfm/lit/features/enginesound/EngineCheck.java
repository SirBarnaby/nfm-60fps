package nfm.lit.features.enginesound;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;
import static nfm.lit.features.ExtrasCheck.waiting;

import java.util.Random;
import nfm.lit.audio.EngineGrains;
import nfm.lit.audio.Sound3D;
import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;

/**
 * The rebuilt engine sound's checks (sound, "The improved sound is rebuilt
 * from the game's own recordings"), on a made-up engine recording and a
 * made-up car, with no sound device: the recording cut into its cycles;
 * played one cycle at a time, its firing rate following the revs while its
 * tone stays and its level holds; the front brighter (engine, intake) than
 * the back (exhaust), each at its end of the car; one sliding wheel skidding
 * alone, from that wheel. What the owner hears waits for the owner.
 */
public final class EngineCheck {

    private static final String F = "engine sound";
    /** The made-up recording: 8000 samples a second, a firing every 64.3 samples, 7.7 firings in the loop, as the game's are. */
    private static final int RECORDED = 8_000;
    private static final float PERIOD = 64.3F;
    private static final int LOOP = Math.round(7.7F * PERIOD);

    private EngineCheck() {
    }

    public static void run() {
        float[] loop = recording();
        EngineGrains.Cycles cycles = EngineGrains.cut(loop, RECORDED);
        cycles(cycles);
        oneCycleAtATime(cycles);
        frontAndBack(cycles);
        oneWheelSkids();
        waiting(F, "front and back, heard", "the owner listens to a car driving past with the 3D sound and Rebuilt"
                + " engine sound on (Extras tab): the engine and intake from its front, the exhaust from its back");
        waiting(F, "the owner listens first", "test\\verify-sound.ps1's baseline is renewed only after the owner has"
                + " listened; with the extra off, as in every hidden run, it is unchanged");
    }

    /** A loop of firings: each a knock that rings at 900 Hz and 250 Hz and dies away, as an engine's cylinder fires. */
    private static float[] recording() {
        float[] x = new float[LOOP];
        for (int i = 0; i < LOOP; i++) {
            float t = (i % PERIOD) / RECORDED;
            double ring = Math.exp(-t * 180D);
            x[i] = (float) (ring * (0.5D * Math.sin(2D * Math.PI * 900D * t) + 0.4D * Math.sin(2D * Math.PI * 250D * t)));
        }
        return x;
    }

    /** The loop is cut at its firings: the cycle found is the firing period, not two of them. */
    private static void cycles(EngineGrains.Cycles c) {
        check(F, "the recording cut into its cycles", Math.abs(c.period - PERIOD) < 1F && c.count == 7,
                "a cycle of " + n(c.period) + " samples (made " + n(PERIOD) + "), " + c.count + " whole cycles");
    }

    /**
     * Played at 1.25 times the revs, the firings come 1.25 times as often,
     * while the sound of each firing (its spectrum's centre) stays where it
     * was: one cycle at a time, not the recording played faster, which would
     * move it up by a quarter. Played as recorded, the level holds.
     */
    private static void oneCycleAtATime(EngineGrains.Cycles c) {
        float[] asRecorded = play(c, 1F, EngineGrains.RATE);
        float[] faster = play(c, 1.25F, EngineGrains.RATE);
        float natural = PERIOD * EngineGrains.RATE / RECORDED;
        float period = EngineGrains.period(tail(faster, 4_000), EngineGrains.RATE);
        float expected = natural / 1.25F;
        check(F, "the firing rate follows the revs", Math.abs(period - expected) / expected < 0.03F,
                "at 1.25 times the revs a firing every " + n(period) + " samples (" + n(expected) + " expected)");
        float c1 = centroid(tail(asRecorded, 2_048));
        float c2 = centroid(tail(faster, 2_048));
        check(F, "each cycle keeps its sound", Math.abs(c2 / c1 - 1F) < 0.12F,
                "the spectrum's centre at " + n(c1) + " Hz as recorded, " + n(c2) + " Hz at 1.25 times the revs (played"
                        + " faster it would be " + n(c1 * 1.25F) + ")");
        float source = rms(recording());
        float made = rms(tail(asRecorded, 8_000));
        check(F, "the level holds", Math.abs(made / source - 1F) < 0.15F,
                "the loop's level " + n(source) + ", played one cycle at a time " + n(made));
    }

    /** The front (engine and intake) is brighter than the back (exhaust), and each is at its end of the car. */
    private static void frontAndBack(EngineGrains.Cycles c) {
        float[] engine = play(c, 1F, EngineGrains.RATE);
        int n = engine.length;
        float[] front = new float[n];
        float[] back = new float[n];
        EngineGrains.Tone tone = new EngineGrains.Tone();
        tone.shape(engine, front, back, n, 1F, new Random(3L));
        float cf = centroid(tail(front, 2_048));
        float cb = centroid(tail(back, 2_048));
        check(F, "the front brighter than the back", cf > 1.5F * cb,
                "the spectrum's centre at the front " + n(cf) + " Hz, at the back " + n(cb) + " Hz");
        CarShape s = new CarShape();
        s.minX = -40F;
        s.maxX = 40F;
        s.minY = -60F;
        s.maxY = 0F;
        s.minZ = -120F;
        s.maxZ = 130F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            s.hubX[w] = w % 2 == 0 ? -45F : 45F;
            s.hubY[w] = -5F;
            s.hubZ[w] = w < 2 ? 85F : -80F;
            s.wheelRadius[w] = 20F;
        }
        float[] body = new float[3 * (2 + RaceView.WHEELS)];
        EngineSound.body(s, body);
        boolean ends = body[2] > 90F && body[5] == s.minZ && body[4] > body[1];
        boolean treads = body[6 + 3 * 2 + 1] == 15F && body[6 + 3 * 2 + 2] == -80F;
        check(F, "the front at the nose, the back at the tail", ends && treads,
                "front at z " + n(body[2]) + ", back at z " + n(body[5]) + " and " + n(body[4] - body[1])
                        + " lower, the third wheel's tread at y " + n(body[6 + 3 * 2 + 1]));
    }

    /** One wheel locked while the other three roll: only it skids, and the sound hears it from that wheel. */
    private static void oneWheelSkids() {
        RaceView v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.modelName[0] = "box";
        v.speed[0] = 80F;
        v.vz[0] = 80F;
        v.grounded[0] = true;
        int[] keyX = {-45, 45, -45, 45};
        int[] keyZ = {85, 85, -80, -80};
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.keyX[w] = keyX[w];
            v.keyZ[w] = keyZ[w];
            v.contact[w] = true;
            v.wheelVZ[w] = 80F;
        }
        EngineSound sound = EngineSound.FEATURE;
        sound.raceStarted(v);
        v.slipLong[2] = -80F; // the left rear locked: its tread stands while the car moves on; the others roll
        sound.afterTick(v);
        sound.afterTick(v);
        Sound3D.Car car = new Sound3D.Car();
        sound.skids(0, car);
        boolean alone = car.skid2 > 0.5F && car.skid0 == 0F && car.skid1 == 0F && car.skid3 == 0F;
        check(F, "one wheel skids, alone", alone,
                "skids " + n(car.skid0) + ", " + n(car.skid1) + ", " + n(car.skid2) + ", " + n(car.skid3)
                        + " (the third wheel locked at 80 a tick)");
        sound.raceEnded(v);
        sound.skids(0, car);
        check(F, "no skid after the race", car.skid2 == 0F, "the third wheel's skid " + n(car.skid2));
    }

    /** One second of {@code c} played one cycle at a time at {@code pitch}, in blocks as the 3D sound plays it. */
    private static float[] play(EngineGrains.Cycles c, float pitch, int samples) {
        float[] out = new float[samples];
        EngineGrains.Voice voice = new EngineGrains.Voice();
        voice.use(c);
        Random random = new Random(1L);
        int block = EngineGrains.RATE / 50;
        for (int at = 0; at + block <= samples; at += block) {
            voice.add(out, at, block, pitch, 1F, random);
        }
        return out;
    }

    private static float[] tail(float[] x, int n) {
        float[] t = new float[n];
        System.arraycopy(x, x.length - n, t, 0, n);
        return t;
    }

    private static float rms(float[] x) {
        double s = 0D;
        for (float v : x) {
            s += v * v;
        }
        return (float) Math.sqrt(s / x.length);
    }

    /** The centre of {@code x}'s spectrum (made at the rebuilt sound's rate), Hz: where its sound sits. */
    private static float centroid(float[] x) {
        int n = x.length;
        double weighted = 0D;
        double total = 0D;
        for (int k = 1; k < n / 2; k++) {
            double re = 0D;
            double im = 0D;
            for (int i = 0; i < n; i++) {
                double w = 0.5D - 0.5D * Math.cos(2D * Math.PI * i / n);
                double a = 2D * Math.PI * k * i / n;
                re += w * x[i] * Math.cos(a);
                im -= w * x[i] * Math.sin(a);
            }
            double mag = Math.sqrt(re * re + im * im);
            weighted += mag * k;
            total += mag;
        }
        return total == 0D ? 0F : (float) (weighted / total * EngineGrains.RATE / n);
    }
}
