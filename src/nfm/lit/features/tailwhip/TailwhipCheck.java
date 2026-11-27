package nfm.lit.features.tailwhip;

import static nfm.lit.features.ExtrasCheck.check;

import nfm.lit.features.CheckRace;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceView;

/**
 * The tailwhip's checks ({@link nfm.lit.features.ExtrasCheck}), on a
 * made-up race ({@link CheckRace}), one for each scenario of the
 * requirement and named after it: the turn (more with both brakes and the
 * steering, none with the switch off) and the hit (the car a swinging tail
 * touches is spun, and every player is told); and that its state, saved
 * and restored, plays on the same.
 */
public final class TailwhipCheck {

    private static final String F = "tailwhip";

    private TailwhipCheck() {
    }

    public static void run() {
        theTurn();
        theHit();
        rewind();
    }

    /** Two cars: car 0 at the origin heading along z, car 1 where the check puts it. */
    private static CheckRace two(float x1, float z1) {
        CheckRace t = new CheckRace(2, 4);
        t.x[0] = 0F;
        t.z[0] = 0F;
        t.x[1] = x1;
        t.z[1] = z1;
        return t;
    }

    private static RaceView view(CheckRace t, int humans) {
        RaceView v = new RaceView();
        v.fit(t.cars);
        v.cars = t.cars;
        v.humans = humans;
        return v;
    }

    /**
     * The turn: with the handbrake and the brake held and left pressed a
     * moving car turns more than its steering alone gives, right the other
     * way, the faster it moves the more; without both brakes, without
     * steering or standing, nothing; and with the switch off the game asks
     * and gets nothing.
     */
    private static void theTurn() {
        TailwhipFeature f = TailwhipFeature.forChecks();
        CheckRace t = two(-2000F, 0F);
        f.raceSetUp(t, 1L);
        int oneBrake = f.turn(0, 20, 0, false, 40F, 40F);
        int noSteer = f.turn(0, 0, 0, true, 40F, 40F);
        int standing = f.turn(0, 20, 0, true, 2F, 2F);
        boolean idle = f.swing[0] == 0;
        int left = f.turn(0, 20, 0, true, 40F, 40F);
        int right = f.turn(0, -20, 0, true, 40F, 40F);
        int slow = f.turn(0, 20, 0, true, 8F, 8F);
        // a swing that begins rolling backwards turns the other way, as the steering does then
        TailwhipFeature b = TailwhipFeature.forChecks();
        b.raceSetUp(t, 1L);
        int backwards = b.turn(0, 20, -4, true, -40F, 40F);
        // a second of it: the steering alone turns the car 4 degrees a tick, then (the engine pulling backwards) 4 back
        int second = 0;
        int alone = 0;
        for (int k = 0; k < 19; k++) {
            int steered = k < 6 ? 4 : -4;
            alone += steered;
            second += steered + f.turn(0, 20, steered, true, k < 12 ? 40F : -10F, 40F);
            f.raceTick(t);
        }
        // through the game's own call: off by default, on when the switch is
        int was = FeatureSettings.get(TailwhipFeature.ID);
        FeatureSettings.set(TailwhipFeature.ID, 0);
        Features.raceStarting(t, 3L);
        boolean playsOff = Features.racePlays(TailwhipFeature.FEATURE);
        int off = Features.raceTurn(0, 20, 0, true, 40F, 40F);
        Features.leftRace();
        FeatureSettings.set(TailwhipFeature.ID, 1);
        Features.raceStarting(t, 3L);
        boolean playsOn = Features.racePlays(TailwhipFeature.FEATURE);
        int on = Features.raceTurn(0, 20, 0, true, 40F, 40F);
        Features.leftRace();
        FeatureSettings.set(TailwhipFeature.ID, was);
        boolean ok = oneBrake == 0 && noSteer == 0 && standing == 0 && idle && left > 0 && right == -left
                && slow > 0 && slow < left && backwards == -left && alone < 0 && second == 6 * 4 + 19 * left
                && !playsOff && off == 0 && playsOn && on == left;
        check(F, "the turn", ok, "handbrake, brake and left at 40 units a tick: " + left + " degrees a tick more, right "
                + right + ", at 8 units a tick " + slow + ", begun rolling backwards " + backwards + "; a second of"
                + " it where the steering alone turns the car " + alone + " degrees (the engine pulls it back): "
                + second + "; one brake only " + oneBrake + ", no steering " + noSteer + ", standing " + standing
                + "; asked by the game with the switch off " + off + " (plays: " + playsOff + "), on " + on);
    }

    /**
     * The hit: a swinging tail that touches another car gives that car 60
     * degrees of spin over the next ticks, the way the tail pushed it, and
     * each player reads who tailwhipped whom; a tail that is not swinging,
     * and a swinging car's nose, do nothing; one swing is one tailwhip.
     */
    private static void theHit() {
        // car 1 stands behind car 0's left rear corner, a little ahead of it and a little behind it
        int[] spun = new int[2];
        for (int where = 0; where < 2; where++) {
            TailwhipFeature f = TailwhipFeature.forChecks();
            CheckRace t = two(-140F, where == 0 ? 60F : -200F);
            f.raceSetUp(t, 1L);
            f.turn(0, 20, 0, true, 40F, 40F);
            f.touch(0, 2, 1, 1);
            for (int k = 0; k < 30; k++) {
                f.raceTick(t);
            }
            spun[where] = t.turned[1];
        }
        TailwhipFeature f = TailwhipFeature.forChecks();
        CheckRace t = two(-140F, -200F);
        f.raceSetUp(t, 1L);
        RaceView v = view(t, 2);
        f.raceStarted(v);
        // touching without a swing, and the swinging car's nose touching: nothing
        f.touch(0, 2, 1, 1);
        f.raceTick(t);
        f.afterTick(v);
        boolean still = f.whips[0] == 0 && t.turned[1] == 0;
        f.turn(0, 20, 0, true, 40F, 40F);
        f.touch(0, 0, 1, 1);
        f.raceTick(t);
        f.afterTick(v);
        boolean nose = f.whips[0] == 0 && t.turned[1] == 0;
        // the other car's corner meeting the swinging tail (the game reports a touch from either side)
        f.turn(0, 20, 0, true, 40F, 40F);
        f.touch(1, 1, 0, 2);
        f.touch(0, 3, 1, 1); // and the other rear corner in the same tick: still one tailwhip
        f.raceTick(t);
        f.afterTick(v);
        int first = t.turned[1];
        int ticks = 1;
        while (f.spin[1] != 0 && ticks < 100) {
            f.raceTick(t);
            f.afterTick(v);
            ticks++;
        }
        int total = t.turned[1];
        boolean one = f.whips[0] == 1 && f.victim[0] == 1 && t.turned[0] == 0;
        String lines = String.join(" | ", f.said);
        boolean told = f.said.size() == 2 && f.said.get(0).equals("You tailwhipped Car 2!  TAILWHIP!")
                && f.said.get(1).equals("Car 1 tailwhipped you!  TAILWHIP!");
        // still touching while the first swing rests: no second; after the rest, a new swing is a new tailwhip
        f.turn(0, 20, 0, true, 40F, 40F);
        f.touch(0, 2, 1, 1);
        f.raceTick(t);
        boolean rested = f.whips[0] == 1;
        for (int k = 0; k < TailwhipFeature.REST; k++) {
            f.raceTick(t);
        }
        f.turn(0, 20, 0, true, 40F, 40F);
        f.touch(0, 2, 1, 1);
        f.raceTick(t);
        boolean again = f.whips[0] == 2;
        // a wasted car is not spun
        TailwhipFeature g = TailwhipFeature.forChecks();
        CheckRace w = two(-140F, -200F);
        g.raceSetUp(w, 1L);
        w.wasted[1] = true;
        g.turn(0, 20, 0, true, 40F, 40F);
        g.touch(0, 2, 1, 1);
        g.raceTick(w);
        boolean wasted = g.whips[0] == 0 && w.turned[1] == 0;
        boolean ok = still && nose && one && Math.abs(total) == TailwhipFeature.SPIN && Math.abs(first) >= 10
                && ticks >= 8 && ticks <= 30 && told && rested && again && wasted
                && Math.abs(spun[0]) == TailwhipFeature.SPIN && spun[0] == -spun[1];
        check(F, "the hit", ok, "a tail not swinging: nothing " + still + "; the swinging car's nose: nothing " + nose
                + "; its swinging tail: the other car spun " + first + " degrees in the first tick, " + total + " in "
                + ticks + " ticks, the whipping car itself " + t.turned[0] + "; hit ahead of its middle " + spun[0]
                + ", behind it " + spun[1] + "; two players read: " + lines + "; still touching in the next tick: no"
                + " second " + rested + ", two seconds on a new swing: a second " + again + "; a wasted car is left "
                + wasted);
    }

    /** Saved in the middle of a spin and restored, the race plays on the same. */
    private static void rewind() {
        TailwhipFeature f = TailwhipFeature.forChecks();
        CheckRace t = two(-140F, -200F);
        f.raceSetUp(t, 1L);
        f.turn(0, 20, 0, true, 40F, 40F);
        f.touch(0, 2, 1, 1);
        for (int k = 0; k < 3; k++) {
            f.raceTick(t);
        }
        RaceInts saved = new RaceInts();
        saved.startSaving();
        f.sync(saved);
        saved.endSaving();
        CheckRace copy = t.copy();
        for (int k = 0; k < 40; k++) {
            f.raceTick(t);
        }
        RaceInts first = new RaceInts();
        first.startSaving();
        f.sync(first);
        first.endSaving();
        saved.startRestoring();
        f.sync(saved);
        saved.endRestoring();
        for (int k = 0; k < 40; k++) {
            f.raceTick(copy);
        }
        RaceInts again = new RaceInts();
        again.startSaving();
        f.sync(again);
        again.endSaving();
        check(F, "restored, the tailwhip plays on the same", first.sameAs(again) && first.size() == 14
                && copy.yaw[1] == t.yaw[1] && t.yaw[1] != 0, first.size() + " values; the spun car points "
                        + t.yaw[1] + " and " + copy.yaw[1] + " degrees");
    }
}
