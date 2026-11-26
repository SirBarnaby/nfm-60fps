package nfm.lit.features.enginesound;

import nfm.lit.audio.EngineLayers;
import nfm.lit.audio.Sound3D;
import nfm.lit.features.CarShape;
import nfm.lit.features.Dice;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.RaceView;
import nfm.lit.features.realism.CarSheet;
import nfm.lit.features.realism.CarSheets;
import nfm.lit.features.skidmarks.SkidSlip;
import nfm.lit.features.sounds.Recordings;

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
 * Over the engine lie layers that are real recordings
 * ({@link EngineLayers}): a turbo's whistle and blow-off or a
 * supercharger's whine, the exhaust's pops, the gear whine of reverse and a
 * truck's beeper, the tyres rolling on the road or on loose ground, the
 * radiator fan of a standing car, and the suspension creaking as the car
 * lands. Which a car has comes from its sheet ({@code car-sheets.txt}):
 * this side gives the 3D sound each car's kit when a race starts, and plays
 * the creaks.
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
    /** Side pipes are heard from this share of the way back from the body's middle to its tail. */
    static final float SIDE_BACK = 0.3F;
    /** A wheel of this radius (the model's units) skids at the recordings' own pitch; a bigger one lower. */
    static final float USUAL_WHEEL = 28F;
    /** A car that was off the ground for at least this many ticks creaks as it lands, and not again for {@link #CREAK_GAP}. */
    static final int CREAK_AIR = 4;
    static final int CREAK_GAP = 10;
    static final String[] CREAKS = {"eng-creak1", "eng-creak2", "eng-creak3"};

    /** The one instance, in the extras' list; the game's hooks reach it here. */
    public static final EngineSound FEATURE = new EngineSound();

    private final SkidSlip slip = new SkidSlip();
    private final float[] body = new float[3 * (2 + RaceView.WHEELS)];
    /** Each wheel's skid after the last tick, 0 to 1, and whether it is on loose ground: car * 4 + wheel. */
    private float[] skid = new float[0];
    private boolean[] loose = new boolean[0];
    private int cars;
    private boolean running;
    private final Dice dice = new Dice(0L, ID);
    /** Ticks each car has been off the ground, and ticks until it may creak again. */
    private int[] inAir = new int[0];
    private int[] creakWait = new int[0];
    /** Creaks played since the race started, for the checks. */
    int creaks;

    private EngineSound() {
    }

    /** Gives the 3D sound the layers' recordings, once for the game. */
    static synchronized void make() {
        Recordings.give(EngineLayers.TURBO_CLIP, "turbo", false);
        Recordings.give(EngineLayers.BLOWER_CLIP, "blower", false);
        Recordings.give(EngineLayers.BLOW_OFF_CLIP, "blowoff", false);
        Recordings.give(EngineLayers.BACKFIRE_CLIP, "backfire", false);
        for (int i = 0; i < EngineLayers.POP_CLIPS.length; i++) {
            Recordings.give(EngineLayers.POP_CLIPS[i], "pop" + (i + 1), false);
        }
        for (int layer = EngineLayers.WHINE; layer < EngineLayers.COUNT; layer++) {
            String clip = EngineLayers.clip(layer, EngineLayers.PLAIN);
            Recordings.give(clip, clip.substring("eng-".length()), false);
        }
        for (int i = 0; i < CREAKS.length; i++) {
            Recordings.give(CREAKS[i], "creak" + (i + 1), false);
        }
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
        if (inAir.length < cars) {
            inAir = new int[cars];
            creakWait = new int[cars];
        }
        java.util.Arrays.fill(inAir, 0);
        java.util.Arrays.fill(creakWait, 0);
        creaks = 0;
        dice.reset(race.seed, ID);
        make();
        for (int c = 0; c < cars; c++) {
            CarSheet sheet = CarSheets.of(race.modelName[c]);
            body(race.shape[c], sheet, body);
            Sound3D.carBody(c, body);
            Sound3D.carKit(c, kit(sheet, race.shape[c]));
        }
        running = true;
    }

    /** What the 3D sound's layers need to know of a car: from its sheet, and its wheels' size. */
    static EngineLayers.Kit kit(CarSheet sheet, CarShape s) {
        EngineLayers.Kit k = new EngineLayers.Kit();
        k.charger = !sheet.turbo ? EngineLayers.NO_CHARGER
                : "blower".equals(sheet.trait("charger", "turbo")) ? EngineLayers.BLOWER : EngineLayers.TURBO;
        k.beeps = sheet.is("beeps");
        k.fan = sheet.cooling == CarSheet.RADIATOR_FRONT;
        k.loud = Math.max(0.5F, Math.min(1.6F, sheet.number("loud", 1F)));
        k.revs = Math.max(2F, Math.min(7F, sheet.number("revs", 4F)));
        float radius = 0F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            radius += s.wheelRadius[w] / RaceView.WHEELS;
        }
        // a wheel twice as big skids about a fifth lower
        k.skidPitch = radius <= 0F ? 1F
                : Math.max(0.75F, Math.min(1.25F, (float) Math.pow(USUAL_WHEEL / radius, 0.35D)));
        return k;
    }

    /**
     * Car {@code s}'s points the sound plays from, in its own frame: the
     * front (a little in from the nose, at the middle of the body's height),
     * the back (the tail, low), and each wheel's tread (its hub, down by its
     * radius).
     */
    static void body(CarShape s, float[] out) {
        body(s, CarSheet.DEFAULT, out);
    }

    /**
     * The same, the back where the car's sheet says its exhaust ends: the
     * tail, low; with {@code pipes=side} its left side, low, behind the
     * middle; with stacks the top of the body behind its middle.
     */
    static void body(CarShape s, CarSheet sheet, float[] out) {
        float half = Math.max(1F, (s.maxZ - s.minZ) / 2F);
        float middle = (s.minZ + s.maxZ) / 2F;
        out[0] = 0F;
        out[1] = (s.minY + s.maxY) / 2F;
        out[2] = s.maxZ - FRONT_IN * half;
        out[3] = 0F;
        out[4] = s.minY + EXHAUST_LOW * (s.maxY - s.minY);
        out[5] = s.minZ;
        if (sheet.exhausts == CarSheet.STACKS) {
            out[4] = s.minY;
            out[5] = middle - SIDE_BACK * half;
        } else if ("side".equals(sheet.trait("pipes", "back"))) {
            out[3] = s.minX;
            out[5] = middle - SIDE_BACK * half;
        }
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
            creak(race, c);
        }
    }

    /** The suspension creaks as a car comes down after a while in the air, louder after a longer flight. */
    private void creak(RaceView race, int c) {
        if (creakWait[c] > 0) {
            creakWait[c]--;
        }
        boolean down = race.grounded[c];
        if (down && inAir[c] >= CREAK_AIR && creakWait[c] == 0 && !race.wasted[c]) {
            float hard = Math.min(1F, inAir[c] / 30F);
            Sound3D.carSound(c, CREAKS[dice.below(CREAKS.length)], 0.3F + 0.5F * hard, dice.between(0.85F, 1.15F));
            creakWait[c] = CREAK_GAP;
            creaks++;
        }
        inAir[c] = down ? 0 : inAir[c] + 1;
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
