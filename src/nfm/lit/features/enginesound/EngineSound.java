package nfm.lit.features.enginesound;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.CarShape;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.RaceView;
import nfm.lit.features.skidmarks.SkidSlip;

/**
 * The rebuilt engine sound (switch {@code enginesound}): with the 3D sound,
 * each car's engine is its own recordings played one engine cycle at a time,
 * the engine and intake heard from its front and the exhaust from its back,
 * and each wheel that slides is heard skidding from that wheel. The sound
 * itself is made on the 3D sound's thread ({@code Sound3D},
 * {@code EngineGrains}); this side tells it, when a race starts, where on
 * each car its front, back and wheels are, and after each tick how hard each
 * wheel skids and on what.
 * <p>
 * Sound only: it reads the race, never writes it, and the 3D sound never
 * reads the race (it hears what the game publishes after each tick).
 */
public final class EngineSound implements Feature {

    public static final String ID = "enginesound";
    /** A wheel's skid is silent below this slide (units a tick) and full at {@link #FULL}. */
    static final float QUIET = 4F;
    static final float FULL = 24F;
    /** The engine and intake are heard this far back from the nose (a share of the body's half length), at the bonnet's height. */
    static final float FRONT_IN = 0.2F;
    /** The exhaust at the tail, this share of the way down from the body's top. */
    static final float EXHAUST_LOW = 0.8F;

    /** The one instance, in the extras' list; the game's hooks reach it here. */
    public static final EngineSound FEATURE = new EngineSound();

    private final SkidSlip slip = new SkidSlip();
    private final float[] body = new float[3 * (2 + RaceView.WHEELS)];
    /** Each wheel's skid after the last tick, 0 to 1, and whether it is on loose ground: car * 4 + wheel. */
    private float[] skid = new float[0];
    private boolean[] loose = new boolean[0];
    private int cars;
    private boolean running;

    private EngineSound() {
    }

    @Override
    public String id() {
        return ID;
    }

    /** True when the 3D sound should play the cars rebuilt: the switch is on (hidden runs keep it off). */
    public boolean plays() {
        return FeatureSettings.on(ID);
    }

    @Override
    public void raceStarted(RaceView race) {
        cars = race.cars;
        int wheels = cars * RaceView.WHEELS;
        if (skid.length < wheels) {
            skid = new float[wheels];
            loose = new boolean[wheels];
        }
        java.util.Arrays.fill(skid, 0F);
        java.util.Arrays.fill(loose, false);
        slip.reset();
        for (int c = 0; c < cars; c++) {
            body(race.shape[c], body);
            Sound3D.carBody(c, body);
        }
        running = true;
    }

    /**
     * Car {@code s}'s points the sound plays from, in its own frame: the
     * front (a little in from the nose, at the middle of the body's height),
     * the back (the tail, low), and each wheel's tread (its hub, down by its
     * radius).
     */
    static void body(CarShape s, float[] out) {
        float half = Math.max(1F, (s.maxZ - s.minZ) / 2F);
        out[0] = 0F;
        out[1] = (s.minY + s.maxY) / 2F;
        out[2] = s.maxZ - FRONT_IN * half;
        out[3] = 0F;
        out[4] = s.minY + EXHAUST_LOW * (s.maxY - s.minY);
        out[5] = s.minZ;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            out[6 + 3 * w] = s.hubX[w];
            out[7 + 3 * w] = s.hubY[w] + s.wheelRadius[w];
            out[8 + 3 * w] = s.hubZ[w];
        }
    }

    @Override
    public void afterTick(RaceView race) {
        int n = Math.min(cars, race.cars);
        slip.update(race);
        for (int c = 0; c < n; c++) {
            for (int w = 0; w < RaceView.WHEELS; w++) {
                int at = c * RaceView.WHEELS + w;
                skid[at] = strength(slip.slide[at]);
                loose[at] = race.surface[at] != 0;
            }
        }
    }

    /** How loud a wheel sliding {@code slide} units a tick skids: nothing below {@link #QUIET}, all of it from {@link #FULL}. */
    static float strength(float slide) {
        return SkidSlip.smooth(QUIET, FULL, slide);
    }

    @Override
    public void raceEnded(RaceView race) {
        running = false;
        java.util.Arrays.fill(skid, 0F);
    }

    /** Car {@code c}'s wheels' skids after the last tick into the 3D sound's car; none when no race runs. */
    public void skids(int c, Sound3D.Car car) {
        if (!running || c < 0 || c >= cars) {
            car.skid0 = car.skid1 = car.skid2 = car.skid3 = 0F;
            car.loose = 0;
            return;
        }
        int at = c * RaceView.WHEELS;
        car.skid0 = skid[at];
        car.skid1 = skid[at + 1];
        car.skid2 = skid[at + 2];
        car.skid3 = skid[at + 3];
        int bits = 0;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            if (loose[at + w]) {
                bits |= 1 << w;
            }
        }
        car.loose = bits;
    }
}
