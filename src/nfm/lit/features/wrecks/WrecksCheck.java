package nfm.lit.features.wrecks;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.BlastPush;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.CheckRace;
import nfm.lit.features.Features;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceView;

/**
 * The exploding wrecks' checks ({@link nfm.lit.features.ExtrasCheck}), on a
 * made-up race ({@link CheckRace}): a wasted car burns two seconds, explodes
 * and dents the ground; with the switch off it never does; a car next to it
 * is pushed and damaged, less than by a dynamite stick, and one far away is
 * untouched; and the state saved and restored plays on the same.
 */
public final class WrecksCheck {

    private static final String F = "wrecks";

    private WrecksCheck() {
    }

    public static void run() {
        explodes();
        switchedOff();
        neighbours();
        rewind();
    }

    /** A car is wasted: two seconds later it explodes, a crater is left, the fireball and smoke are drawn. */
    private static void explodes() {
        WrecksFeature f = WrecksFeature.FEATURE;
        WreckRace r = f.race;
        CheckRace t = new CheckRace(2, 4);
        t.x[0] = 0F;
        t.z[0] = 0F;
        r.setUp(t);
        RaceView v = view(t);
        f.raceStarted(v);
        for (int k = 0; k < 3 * WreckRace.SECOND; k++) {
            tick(f, t, v);
        }
        boolean races = r.blown() == 0 && f.explosions.particles.count() == 0;
        t.wasted[1] = true;
        v.wasted[1] = true;
        int ticks = 0;
        int sparks = 0;
        while (r.burned[1] != WreckRace.BLOWN && ticks < 200) {
            tick(f, t, v);
            ticks++;
            sparks = Math.max(sparks, r.burned[1] != WreckRace.BLOWN ? f.explosions.particles.count() : sparks);
        }
        CheckCanvas canvas = new CheckCanvas();
        canvas.frame(f, 0F, 100F, -1500F, v.tick);
        int fire = canvas.drawn(0xFFB43C);
        int craters = f.explosions.craters();
        float hop = t.pushed[1];
        for (int k = 0; k < 5 * WreckRace.SECOND; k++) {
            tick(f, t, v);
        }
        boolean once = r.blown() == 1 && f.explosions.craters() == 1;
        check(F, "a wasted car burns two seconds, then explodes and dents the ground", races
                && ticks == WreckRace.FUSE && sparks > 0 && fire > 0 && craters == 1 && r.dented[1] && hop > 0F && once
                && f.explosions.particles.count() > 10,
                "exploded " + ticks + " ticks after it was wasted (" + sparks + " sparks while it waited), fireball "
                        + fire + " polygons, craters " + craters + ", the wreck kicked " + n(hop) + ", smoke "
                        + f.explosions.particles.count() + " puffs five seconds on, exploded once: " + once);
    }

    /** The switch off: the extra does not play in the race, so a wasted car is never touched. */
    private static void switchedOff() {
        CheckRace t = new CheckRace(2, 4);
        t.x[1] = t.x[0] + 200F;
        t.z[1] = t.z[0];
        Features.raceStarting(t, 3L);
        boolean plays = Features.racePlays(WrecksFeature.FEATURE);
        t.wasted[0] = true;
        for (int k = 0; k < 10 * WreckRace.SECOND; k++) {
            Features.raceTick(t);
        }
        Features.leftRace();
        check(F, "with the switch off a wasted car never explodes", !plays && t.pushed[0] == 0F && t.pushed[1] == 0F
                && t.damaged[1] == 0F, "plays in the race: " + plays + "; ten seconds wasted: the wreck pushed "
                        + n(t.pushed[0]) + ", the car beside it pushed " + n(t.pushed[1]) + ", damaged "
                        + n(t.damaged[1]));
    }

    /**
     * A car 200 units from an exploding wreck is pushed and damaged, less
     * than a dynamite stick at the same place would; a car 2000 away is
     * untouched.
     */
    private static void neighbours() {
        WreckRace r = new WreckRace();
        CheckRace t = places();
        r.setUp(t);
        t.wasted[0] = true;
        for (int k = 0; k < WreckRace.FUSE; k++) {
            r.tick(t);
        }
        CheckRace stick = places();
        BlastPush.stick(stick, stick.x[0], stick.y[0], stick.z[0]);
        boolean ok = r.burned[0] == WreckRace.BLOWN && t.pushed[1] > 0F && t.damaged[1] > 0F
                && t.pushed[1] < stick.pushed[1] && t.damaged[1] < stick.damaged[1] && t.pushed[2] == 0F
                && t.damaged[2] == 0F;
        check(F, "an exploding wreck pushes and damages a car close to it, less than dynamite", ok,
                "a car 200 away: pushed " + n(t.pushed[1]) + ", damaged " + n(t.damaged[1]) + " (a dynamite stick there: "
                        + n(stick.pushed[1]) + " and " + n(stick.damaged[1]) + "); a car 2000 away: pushed "
                        + n(t.pushed[2]) + ", damaged " + n(t.damaged[2]));
    }

    /** Car 0 at the origin, car 1 200 units beside it, car 2 2000 away. */
    private static CheckRace places() {
        CheckRace t = new CheckRace(3, 4);
        t.x[0] = 0F;
        t.z[0] = 0F;
        t.x[1] = 200F;
        t.z[1] = 0F;
        t.x[2] = 2000F;
        t.z[2] = 0F;
        return t;
    }

    /** Saved, played on and restored, the state plays on exactly as before (run-ahead and rollback). */
    private static void rewind() {
        WreckRace r = new WreckRace();
        CheckRace t = places();
        r.setUp(t);
        t.wasted[0] = true;
        for (int k = 0; k < 20; k++) {
            r.tick(t);
        }
        RaceInts saved = new RaceInts();
        saved.startSaving();
        r.sync(saved);
        saved.endSaving();
        CheckRace copy = t.copy();
        copy.wasted[0] = true;
        for (int k = 0; k < 40; k++) {
            r.tick(t);
        }
        RaceInts first = new RaceInts();
        first.startSaving();
        r.sync(first);
        first.endSaving();
        saved.startRestoring();
        r.sync(saved);
        saved.endRestoring();
        for (int k = 0; k < 40; k++) {
            r.tick(copy);
        }
        RaceInts again = new RaceInts();
        again.startSaving();
        r.sync(again);
        again.endSaving();
        check(F, "restored, the wrecks play on the same", first.sameAs(again) && first.size() > 0
                && r.burned[0] == WreckRace.BLOWN && copy.pushed[1] == t.pushed[1],
                first.size() + " values, the wreck exploded in both, its neighbour pushed " + n(t.pushed[1])
                        + " and " + n(copy.pushed[1]));
    }

    /* ------------------------------------------------------------ helpers */

    private static RaceView view(CheckRace t) {
        RaceView v = new RaceView();
        v.fit(t.cars);
        v.cars = t.cars;
        v.humans = 1;
        v.seed = 5L;
        v.groundColor = 0x4A6A3A;
        look(t, v);
        return v;
    }

    private static void look(CheckRace t, RaceView v) {
        for (int c = 0; c < t.cars; c++) {
            v.x[c] = t.x[c];
            v.y[c] = t.y[c];
            v.z[c] = t.z[c];
            v.radius[c] = t.radius(c);
            v.wasted[c] = t.wasted[c];
        }
    }

    /** One real tick: the race half, then the look half from a fresh copy. */
    private static void tick(WrecksFeature f, CheckRace t, RaceView v) {
        f.race.tick(t);
        v.tick++;
        look(t, v);
        f.afterTick(v);
    }
}
