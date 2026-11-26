package nfm.lit.features.enginesound;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;
import static nfm.lit.features.ExtrasCheck.waiting;

import java.util.Random;
import nfm.lit.audio.EngineGrains;
import nfm.lit.audio.EngineLayers;
import nfm.lit.audio.Sound3D;
import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;
import nfm.lit.features.realism.CarSheet;
import nfm.lit.features.realism.CarSheets;

/**
 * The rebuilt engine sound's checks (sound, "The improved sound is rebuilt
 * from the game's own recordings"), on a made-up engine recording and a
 * made-up car, with no sound device: the recording cut into its cycles;
 * played one cycle at a time, its firing rate following the revs while its
 * tone stays and its level holds; the front brighter (engine, intake) than
 * the back (exhaust), each at its end of the car; one sliding wheel skidding
 * alone, from that wheel. And the layers over the engine ("The engine
 * sound has layers", "Reversing is heard", "In mid-air the revs can max
 * out"): a turbo that spools up
 * and blows off, a supercharger that follows the revs, reverse's whine and
 * a truck's beeper, the tyres on the road or on loose ground, the revs
 * running up in the air, what each car's sheet fits it with, and the
 * suspension's creak on landing. What the owner hears waits for the owner.
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
        chargers();
        reverse();
        rolling();
        inTheAir();
        kits();
        landings();
        waiting(F, "front and back, heard", "the owner listens to a car driving past with the 3D sound and Rebuilt"
                + " engine sound on (Extras tab): the engine and intake from its front, the exhaust from its back");
        waiting(F, "the owner listens first", "test\\verify-sound.ps1's baseline is renewed only after the owner has"
                + " listened; with the extra off, as in every hidden run, it is unchanged");
    }

    /**
     * A loop of firings: each a knock that rings at 250 Hz, 900 Hz and
     * (the valves and the intake, which the game's recordings have up to
     * their 4 kHz) 2600 Hz and dies away, as an engine's cylinder fires.
     */
    private static float[] recording() {
        float[] x = new float[LOOP];
        for (int i = 0; i < LOOP; i++) {
            float t = (i % PERIOD) / RECORDED;
            double ring = Math.exp(-t * 180D);
            x[i] = (float) (ring * (0.5D * Math.sin(2D * Math.PI * 900D * t) + 0.4D * Math.sin(2D * Math.PI * 250D * t)
                    + 0.3D * Math.sin(2D * Math.PI * 2_600D * t)));
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
        float expected = natural / 1.25F;
        float period = firing(tail(faster, 4_000), 0.6F * expected, 1.6F * expected);
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
        tone.shape(engine, front, back, n, 1F);
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

    /* --------------------------------------------- the recorded layers */

    /** A running car on the ground at {@code speed}, the throttle held or not. */
    private static Sound3D.Car driven(float speed, boolean throttle) {
        Sound3D.Car car = new Sound3D.Car();
        car.running = true;
        car.grounded = true;
        car.throttle = throttle;
        car.speed = speed;
        return car;
    }

    /** {@code seconds} of the layers at {@code share} of the revs, in the sound thread's steps; a top speed of 200, reverse's of 60. */
    private static void run(EngineLayers l, Sound3D.Car car, EngineLayers.Kit kit, float share, float seconds) {
        for (int i = 0; i < Math.round(seconds * 100F); i++) {
            l.update(car, kit, share, 60F, 200F, 0.01F, false);
        }
    }

    /**
     * A turbo takes a moment to spin up, whistles higher as it does, and
     * blows off when the throttle lifts; a supercharger is driven by the
     * engine, so it is there at once and follows the revs; a car with
     * neither has neither.
     */
    private static void chargers() {
        EngineLayers.Kit kit = new EngineLayers.Kit();
        kit.charger = EngineLayers.TURBO;
        EngineLayers l = new EngineLayers();
        Sound3D.Car car = driven(150F, true);
        run(l, car, kit, 0.9F, 0.1F);
        float early = l.gain[EngineLayers.CHARGER];
        float earlyPitch = l.pitch[EngineLayers.CHARGER];
        run(l, car, kit, 0.9F, 2F);
        float spun = l.gain[EngineLayers.CHARGER];
        float spunPitch = l.pitch[EngineLayers.CHARGER];
        float none = l.takeBlowOff();
        car.throttle = false;
        run(l, car, kit, 0.9F, 0.01F);
        float blown = l.takeBlowOff();
        run(l, car, kit, 0.9F, 1F);
        float after = l.gain[EngineLayers.CHARGER];
        check(F, "a turbo spools up and blows off", early < 0.5F * spun && spun > 0.3F && spunPitch > earlyPitch
                && none == 0F && blown > 0.5F && after < 0.1F * spun && l.takeBlowOff() == 0F,
                "its whistle " + n(early) + " after 0.1 s of throttle, " + n(spun) + " after 2 s and bent from "
                        + n(earlyPitch) + " to " + n(spunPitch) + "; the throttle lifted: a blow-off of " + n(blown)
                        + ", the whistle down to " + n(after));

        kit = new EngineLayers.Kit();
        kit.charger = EngineLayers.BLOWER;
        l = new EngineLayers();
        car = driven(40F, true);
        run(l, car, kit, 0.15F, 0.3F);
        float low = l.gain[EngineLayers.CHARGER];
        float lowPitch = l.pitch[EngineLayers.CHARGER];
        run(l, car, kit, 0.9F, 0.3F);
        float high = l.gain[EngineLayers.CHARGER];
        float highPitch = l.pitch[EngineLayers.CHARGER];
        run(l, car, kit, 0.9F, 2F);
        float settled = l.gain[EngineLayers.CHARGER];
        car.throttle = false;
        run(l, car, kit, 0.9F, 0.3F);
        float coasting = l.gain[EngineLayers.CHARGER];
        check(F, "a supercharger follows the revs", high > 1.5F * low && high > 0.9F * settled && highPitch > 1.3F * lowPitch
                && coasting < 0.5F * settled && l.takeBlowOff() == 0F,
                "its whine " + n(low) + " low in the revs, " + n(high) + " 0.3 s after they rose (" + n(settled)
                        + " settled), bent from " + n(lowPitch) + " to " + n(highPitch) + "; off the throttle " + n(coasting)
                        + ", and no blow-off");

        l = new EngineLayers();
        car = driven(150F, true);
        run(l, car, EngineLayers.PLAIN, 0.9F, 2F);
        check(F, "no charger, no whistle", l.gain[EngineLayers.CHARGER] == 0F
                && EngineLayers.clip(EngineLayers.CHARGER, EngineLayers.PLAIN) == null
                && EngineLayers.TURBO_CLIP.equals(EngineLayers.clip(EngineLayers.CHARGER, turboKit())),
                "a car with neither: " + n(l.gain[EngineLayers.CHARGER]));
    }

    private static EngineLayers.Kit turboKit() {
        EngineLayers.Kit kit = new EngineLayers.Kit();
        kit.charger = EngineLayers.TURBO;
        return kit;
    }

    /** Reverse whines, higher the faster the car backs; only a truck beeps; neither going forwards. */
    private static void reverse() {
        EngineLayers.Kit truck = new EngineLayers.Kit();
        truck.beeps = true;
        EngineLayers car1 = new EngineLayers();
        EngineLayers truck1 = new EngineLayers();
        Sound3D.Car slow = driven(-15F, true);
        run(car1, slow, EngineLayers.PLAIN, 0.2F, 0.5F);
        float slowPitch = car1.pitch[EngineLayers.WHINE];
        Sound3D.Car backing = driven(-50F, true);
        run(car1, backing, EngineLayers.PLAIN, 0.8F, 0.5F);
        run(truck1, backing, truck, 0.8F, 0.5F);
        float whine = car1.gain[EngineLayers.WHINE];
        float fastPitch = car1.pitch[EngineLayers.WHINE];
        float carBeep = car1.gain[EngineLayers.BEEP];
        float truckBeep = truck1.gain[EngineLayers.BEEP];
        Sound3D.Car forwards = driven(50F, true);
        run(car1, forwards, EngineLayers.PLAIN, 0.8F, 0.5F);
        run(truck1, forwards, truck, 0.8F, 0.5F);
        boolean silent = car1.gain[EngineLayers.WHINE] == 0F && truck1.gain[EngineLayers.BEEP] == 0F
                && truck1.gain[EngineLayers.WHINE] == 0F;
        check(F, "reverse whines, and a truck beeps", whine > 0.3F && fastPitch > 1.2F * slowPitch && carBeep == 0F
                && truckBeep > 0.3F && silent,
                "backing at 50: the whine " + n(whine) + ", bent " + n(slowPitch) + " at 15 and " + n(fastPitch)
                        + " at 50; the beeper " + n(carBeep) + " on a car, " + n(truckBeep) + " on a truck; forwards all"
                        + " silent " + silent);
    }

    /** The tyres are heard rolling: on a hard road or on loose ground, by how many wheels are on each; not in the air. */
    private static void rolling() {
        EngineLayers l = new EngineLayers();
        Sound3D.Car car = driven(30F, false);
        run(l, car, EngineLayers.PLAIN, 0.2F, 0.5F);
        float slow = l.gain[EngineLayers.ROAD];
        car.speed = 180F;
        run(l, car, EngineLayers.PLAIN, 0.9F, 0.5F);
        float road = l.gain[EngineLayers.ROAD];
        float roadGravel = l.gain[EngineLayers.GRAVEL];
        car.loose = 15;
        run(l, car, EngineLayers.PLAIN, 0.9F, 0.5F);
        float gravel = l.gain[EngineLayers.GRAVEL];
        float gravelRoad = l.gain[EngineLayers.ROAD];
        car.loose = 3;
        run(l, car, EngineLayers.PLAIN, 0.9F, 0.5F);
        boolean half = Math.abs(l.gain[EngineLayers.ROAD] - l.gain[EngineLayers.GRAVEL]) < 0.01F
                && l.gain[EngineLayers.ROAD] > 0F;
        car.grounded = false;
        run(l, car, EngineLayers.PLAIN, 0.9F, 0.5F);
        boolean flying = l.gain[EngineLayers.ROAD] == 0F && l.gain[EngineLayers.GRAVEL] == 0F;
        check(F, "the tyres roll on the road or on loose ground", road > 2F * slow && slow > 0F && roadGravel == 0F
                && gravel > 0.2F && gravelRoad == 0F && half && flying,
                "on the road " + n(slow) + " at 30 and " + n(road) + " at 180 (loose " + n(roadGravel) + "); on loose"
                        + " ground " + n(gravel) + " (road " + n(gravelRoad) + "); two wheels on each, half each " + half
                        + "; silent in the air " + flying);
    }

    /** Off the ground with the throttle held nothing holds the engine back: the revs run up to the top; they fall again on landing. */
    private static void inTheAir() {
        EngineLayers l = new EngineLayers();
        Sound3D.Car car = driven(120F, true);
        run(l, car, EngineLayers.PLAIN, 0.4F, 1F);
        float ground = l.air();
        car.grounded = false;
        run(l, car, EngineLayers.PLAIN, 0.4F, 0.2F);
        float rising = l.air();
        run(l, car, EngineLayers.PLAIN, 0.4F, 1F);
        float top = l.air();
        car.grounded = true;
        run(l, car, EngineLayers.PLAIN, 0.4F, 0.5F);
        float landed = l.air();
        car.grounded = false;
        car.throttle = false;
        run(l, car, EngineLayers.PLAIN, 0.4F, 1F);
        float coasting = l.air();
        check(F, "the revs max out in the air", ground == 0F && rising > 0.2F && rising < top && top > 0.9F
                && landed < 0.05F && coasting < 0.05F,
                "on the ground " + n(ground) + "; in the air with the throttle held " + n(rising) + " after 0.2 s, "
                        + n(top) + " after 1.2 s; " + n(landed) + " half a second after landing; in the air off the"
                        + " throttle " + n(coasting));
    }

    private static CarShape shape(float wheel) {
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
            s.wheelRadius[w] = wheel;
        }
        return s;
    }

    /**
     * Each car's sheet says what it is fitted with: a turbo or a
     * supercharger, a truck's beeper, how loud it is and how far its revs
     * climb, where its exhaust ends; and its wheels' size bends its skids.
     */
    private static void kits() {
        CarShape usual = shape(EngineSound.USUAL_WHEEL);
        EngineLayers.Kit plain = EngineSound.kit(CarSheet.DEFAULT, usual);
        EngineLayers.Kit turbo = EngineSound.kit(CarSheets.parseLine("a I6 front front yes disc 1 revs=5"), usual);
        EngineLayers.Kit blower = EngineSound.kit(
                CarSheets.parseLine("b V8 front front yes disc 2 charger=blower pipes=side loud=1.3 revs=3.5"), usual);
        EngineLayers.Kit truck = EngineSound.kit(
                CarSheets.parseLine("c V8 rear air yes disc stacks beeps=yes loud=1.5 revs=2.5"), usual);
        EngineLayers.Kit small = EngineSound.kit(CarSheets.parseLine("d I4 front front no drum 1 loud=0.6"), usual);
        boolean fitted = plain.charger == EngineLayers.NO_CHARGER && !plain.beeps && plain.fan && plain.loud == 1F
                && plain.revs == 4F
                && turbo.charger == EngineLayers.TURBO && turbo.revs == 5F
                && blower.charger == EngineLayers.BLOWER && blower.loud == 1.3F && blower.revs == 3.5F
                && truck.beeps && !truck.fan && truck.loud == 1.5F && small.loud == 0.6F;
        check(F, "a car's sheet fits it out", fitted,
                "a plain car: no charger, a fan, loud " + n(plain.loud) + ", revs " + n(plain.revs) + "; turbo yes: a"
                        + " turbo " + (turbo.charger == EngineLayers.TURBO) + "; charger=blower: a supercharger "
                        + (blower.charger == EngineLayers.BLOWER) + ", loud " + n(blower.loud) + "; beeps=yes: "
                        + truck.beeps + ", air-cooled so no fan " + !truck.fan + "; loud=0.6: " + n(small.loud));

        float big = EngineSound.kit(CarSheet.DEFAULT, shape(2F * EngineSound.USUAL_WHEEL)).skidPitch;
        float little = EngineSound.kit(CarSheet.DEFAULT, shape(0.8F * EngineSound.USUAL_WHEEL)).skidPitch;
        check(F, "big wheels skid lower", plain.skidPitch == 1F && big < 0.85F && big >= 0.75F && little > 1.05F,
                "a wheel of the usual size " + n(plain.skidPitch) + ", twice as big " + n(big) + ", a fifth smaller "
                        + n(little));

        CarShape s = shape(20F);
        float[] tail = new float[3 * (2 + RaceView.WHEELS)];
        float[] side = new float[tail.length];
        float[] stacks = new float[tail.length];
        EngineSound.body(s, CarSheet.DEFAULT, tail);
        EngineSound.body(s, CarSheets.parseLine("b V8 front front yes disc 2 pipes=side"), side);
        EngineSound.body(s, CarSheets.parseLine("c V8 rear air yes disc stacks"), stacks);
        boolean placed = tail[3] == 0F && tail[5] == s.minZ
                && side[3] == s.minX && side[5] > s.minZ && side[5] < 5F && side[4] == tail[4]
                && stacks[3] == 0F && stacks[4] == s.minY && stacks[5] > s.minZ && stacks[5] < 5F;
        check(F, "the exhaust is heard where it ends", placed,
                "pipes at the back: x " + n(tail[3]) + ", z " + n(tail[5]) + "; side pipes: x " + n(side[3]) + ", z "
                        + n(side[5]) + "; stacks: y " + n(stacks[4]) + " (the roof " + n(s.minY) + "), z " + n(stacks[5]));
    }

    /** The suspension creaks as a car comes down from a jump, not over a bump, and not again at once. */
    private static void landings() {
        RaceView v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.modelName[0] = "box";
        v.shape[0] = shape(20F);
        EngineSound sound = EngineSound.FEATURE;
        sound.raceStarted(v);
        v.grounded[0] = true;
        sound.afterTick(v);
        fly(sound, v, 2);
        int bump = sound.creaks;
        fly(sound, v, 12);
        int jump = sound.creaks;
        fly(sound, v, 5);
        int again = sound.creaks;
        for (int t = 0; t < EngineSound.CREAK_GAP; t++) {
            sound.afterTick(v);
        }
        fly(sound, v, 12);
        int later = sound.creaks;
        v.wasted[0] = true;
        fly(sound, v, 12);
        int wrecked = sound.creaks;
        sound.raceEnded(v);
        check(F, "the suspension creaks on landing", bump == 0 && jump == 1 && again == 1 && later == 2 && wrecked == 2,
                "after a bump of 2 ticks " + bump + " creaks; after a jump of 12 ticks " + jump + "; landing again at"
                        + " once " + again + "; after a pause and another jump " + later + "; a wreck landing " + wrecked);
    }

    /** {@code ticks} in the air, then down again. */
    private static void fly(EngineSound sound, RaceView v, int ticks) {
        v.grounded[0] = false;
        for (int t = 0; t < ticks; t++) {
            sound.afterTick(v);
        }
        v.grounded[0] = true;
        sound.afterTick(v);
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

    /**
     * How far apart {@code x}'s firings are, samples: the lag between
     * {@code from} and {@code to} at which it is most like itself. (Not
     * {@link EngineGrains#period}, which finds a recording's whole cycle:
     * played, no two cycles are quite alike, and it would take the loop's
     * length.)
     */
    private static float firing(float[] x, float from, float to) {
        int best = 0;
        double most = -Double.MAX_VALUE;
        for (int lag = Math.round(from); lag <= Math.round(to); lag++) {
            double sum = 0D;
            for (int i = 0; i + lag < x.length; i++) {
                sum += x[i] * x[i + lag];
            }
            sum /= x.length - lag;
            if (sum > most) {
                most = sum;
                best = lag;
            }
        }
        return best;
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
