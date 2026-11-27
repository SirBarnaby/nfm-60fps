package nfm.lit.features.tailwhip;

import fallk.logmaster.HLogger;
import nfm.lit.features.Feature;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * The tailwhip (handling, "Tailwhip"): with the handbrake and the brake
 * held and left or right pressed, a car that is moving rotates a bit more
 * easily, so its tail swings round. A swinging tail that touches another
 * car gives that car some extra spin, and every player is told who
 * tailwhipped whom. It changes races, so it is a switch, and all of it is
 * race state: played again by run-ahead and the verifier, noted in a
 * recording, and off in every recorded race made without it.
 * <p>
 * The race half is the three calls from inside the race tick
 * ({@link #turn}, {@link #touch}, {@link #raceTick}); the other half tells
 * the players, after real ticks only.
 */
public final class TailwhipFeature implements Feature {

    public static final String ID = "tailwhip";
    public static final TailwhipFeature FEATURE = new TailwhipFeature();

    /** Slower than this over the ground a car does not whip: there is nothing to swing. */
    static final float LEAST_MOVING = 6F;
    /** The extra turn, degrees a tick: this much at {@link #LEAST_MOVING}, and at {@link #FULL_MOVING} or faster this much more. */
    static final int LEAST_TURN = 1;
    static final int MORE_TURN = 2;
    static final float FULL_MOVING = 30F;
    /** A tail still swings for this many ticks after the keys let go. */
    static final int SWING = 6;
    /** The spin a tailwhipped car is given, degrees in all; a quarter of what is left each tick. */
    static final int SPIN = 60;
    /** A car that tailwhipped cannot again for this many ticks (two seconds), so one swing is one tailwhip. */
    static final int REST = 38;
    /** A tail that meets a car this near its middle (the share of the lever it could have) spins it the way the tail swings. */
    private static final float DEAD_CENTRE = 0.1F;

    /* the race's: all of it in sync() */
    int cars;
    /** Ticks each car's tail still swings, and which way it turns (1 as left steers it, -1 as right does). */
    int[] swing = new int[0];
    int[] way = new int[0];
    /** 1 if each car's swing began rolling forwards, -1 backwards (its steering turns it the other way then). */
    int[] rolling = new int[0];
    /** Ticks until each car can tailwhip again. */
    int[] rest = new int[0];
    /** The spin each car still has to take, degrees (signed). */
    int[] spin = new int[0];
    /** How many cars each car has tailwhipped in this race, and the last one. */
    int[] whips = new int[0];
    int[] victim = new int[0];

    /** The race, for {@link #touch} (called between its ticks' halves). */
    private RaceTick race;
    /* the look's: what of whips[] the players have been told */
    private int[] told = new int[0];
    private boolean logged;
    /** For the checks: the lines said, newest last. */
    final java.util.List<String> said = new java.util.ArrayList<>();

    private TailwhipFeature() {
    }

    /** A second one for the checks, apart from the game's. */
    static TailwhipFeature forChecks() {
        return new TailwhipFeature();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean changesRaces() {
        return true;
    }

    /* ----------------------------------------------------------- the race */

    @Override
    public void raceSetUp(RaceTick r, long seed) {
        race = r;
        cars = r.cars();
        swing = new int[cars];
        way = new int[cars];
        rolling = new int[cars];
        java.util.Arrays.fill(rolling, 1);
        rest = new int[cars];
        spin = new int[cars];
        whips = new int[cars];
        victim = new int[cars];
        java.util.Arrays.fill(victim, -1);
    }

    /**
     * From the car's steering on the ground: with both brakes held and the
     * wheels turned ({@code steer}, the game's {@code wxz}: positive left),
     * moving {@code moving} over the ground and {@code along} of it along
     * its heading, the degrees more it turns this tick, after the
     * {@code steered} its steering alone just turned it.
     * <p>
     * The tail swings the way the steering turns the car as the swing
     * begins (the other way for a car rolling backwards) and keeps to it
     * while the keys are held. The game steers a car the other way once its
     * engine pulls backwards, which the brake does within a few ticks; while
     * the tail swings that is taken back out, so the car does not turn back
     * in the middle of its slide.
     */
    @Override
    public int turn(int car, int steer, int steered, boolean bothBrakes, float along, float moving) {
        if (car < 0 || car >= cars || !bothBrakes || steer == 0 || moving < LEAST_MOVING) {
            return 0;
        }
        float share = Math.min(1F, (moving - LEAST_MOVING) / (FULL_MOVING - LEAST_MOVING));
        int degrees = LEAST_TURN + (int) (MORE_TURN * share);
        if (swing[car] == 0) {
            rolling[car] = along < 0F ? -1 : 1; // a new swing
        }
        int sign = (steer > 0 ? 1 : -1) * rolling[car];
        int more = sign * degrees;
        if (steered != 0 && (steered > 0) != (sign > 0)) {
            more -= steered;
        }
        swing[car] = SWING;
        way[car] = sign;
        return more;
    }

    /**
     * Two cars touch, corner {@code wheel} of {@code car} and corner
     * {@code otherWheel} of {@code other}: if one of them is a swinging
     * tail, that car tailwhips the other.
     */
    @Override
    public void touch(int car, int wheel, int other, int otherWheel) {
        RaceTick r = race;
        if (r == null || car < 0 || other < 0 || car >= cars || other >= cars || car == other) {
            return;
        }
        if (r.rearWheel(car, wheel)) {
            whip(r, car, wheel, other);
        }
        if (r.rearWheel(other, otherWheel)) {
            whip(r, other, otherWheel, car);
        }
    }

    private void whip(RaceTick r, int by, int tail, int hit) {
        if (swing[by] <= 0 || rest[by] > 0 || r.wasted(by) || r.wasted(hit)) {
            return;
        }
        // the tail sweeps across the car's heading; where it meets the other car says which way that one spins
        double yaw = StrictMath.toRadians(r.yaw(by));
        float sweepX = way[by] * (float) StrictMath.cos(yaw);
        float sweepZ = way[by] * (float) StrictMath.sin(yaw);
        float leverX = r.wheelX(by, tail) - r.x(hit);
        float leverZ = r.wheelZ(by, tail) - r.z(hit);
        float torque = leverX * sweepZ - leverZ * sweepX;
        float lever = (float) StrictMath.sqrt(leverX * leverX + leverZ * leverZ);
        int sense = StrictMath.abs(torque) < DEAD_CENTRE * lever ? way[by] : torque > 0F ? 1 : -1;
        spin[hit] = sense * SPIN;
        rest[by] = REST;
        whips[by]++;
        victim[by] = hit;
    }

    /** True if car {@code car} whipped in the tick just played: its tail is at full swing. */
    boolean whipping(int car) {
        return car >= 0 && car < cars && swing[car] == SWING - 1;
    }

    @Override
    public void raceTick(RaceTick r) {
        race = r;
        for (int c = 0; c < cars; c++) {
            if (swing[c] > 0) {
                swing[c]--;
            }
            if (rest[c] > 0) {
                rest[c]--;
            }
            if (spin[c] != 0) {
                if (r.wasted(c)) {
                    spin[c] = 0;
                    continue;
                }
                int step = spin[c] / 4;
                if (step == 0) {
                    step = spin[c] > 0 ? 1 : -1;
                }
                r.turn(c, step);
                spin[c] -= step;
            }
        }
    }

    @Override
    public void sync(RaceInts state) {
        state.contents(swing);
        state.contents(way);
        state.contents(rolling);
        state.contents(rest);
        state.contents(spin);
        state.contents(whips);
        state.contents(victim);
    }

    /* ----------------------------------------------------------- the look */

    @Override
    public void raceStarted(RaceView r) {
        told = whips.clone(); // switched on mid-race, or a race restored: nothing old is called out
        logged = false;
    }

    /** After a real tick: every tailwhip since the last is called out to every player. */
    @Override
    public void afterTick(RaceView r) {
        if (told.length != whips.length) {
            told = new int[whips.length];
        }
        GameHooks hooks = Features.hooks();
        for (int c = 0; c < whips.length; c++) {
            if (!logged && whipping(c)) {
                logged = true; // once a race: the log shows the move is in play
                HLogger.info("[extras] tailwhip: " + hooks.carName(c) + " swings its tail (tick " + r.tick + ")");
            }
            if (whips[c] > told[c] && victim[c] >= 0) {
                HLogger.info("[extras] tailwhip: " + hooks.carName(c) + " tailwhipped " + hooks.carName(victim[c])
                        + " (tick " + r.tick + ")");
                for (int player = 0; player < r.humans; player++) {
                    String line = line(hooks, player, c, victim[c]);
                    hooks.say(player, line);
                    said.add(line);
                }
            }
            told[c] = whips[c];
        }
    }

    /** What player {@code player} reads when car {@code by} tailwhipped car {@code hit}. */
    static String line(GameHooks hooks, int player, int by, int hit) {
        String who = by == player ? "You" : hooks.carName(by);
        String whom = hit == player ? "you" : hooks.carName(hit);
        return who + " tailwhipped " + whom + "!  TAILWHIP!";
    }
}
