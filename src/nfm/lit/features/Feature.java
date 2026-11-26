package nfm.lit.features;

/**
 * One extra the game can switch on: skidmarks, the wing trail, damage and
 * heat, the helmet cam, the rebuilt engine sound, powerups, the suspension.
 * <p>
 * Each lives in a folder of its own under {@code nfm.lit.features} and is
 * listed once in {@link Features}. The game calls it through that list: when
 * a race starts, after every real race tick (never a run-ahead tick), when
 * the race ends, and for every frame drawn. A feature that only changes the
 * look or the sound reads the race through {@link RaceView}, a copy, so it
 * cannot change a race; one that changes races ({@link #changesRaces}) keeps
 * its state in the race state instead (see the powerups).
 * <p>
 * Called on the game thread only.
 */
public interface Feature {

    /** The switch's id in {@link FeatureSettings}, also its folder's name. */
    String id();

    /** True if it changes races (its state is part of the race), false if it only changes the look or the sound. */
    default boolean changesRaces() {
        return false;
    }

    /*
     * A race extra (changesRaces) is also called inside the race: set up when
     * the stage loads, ticked in every race tick (run-ahead's and the
     * verifier's too), its state saved, restored and hashed with the race's.
     * Its look and sound still come from the calls below, after real ticks.
     */

    /** A race extra: the stage has loaded for a race started from {@code seed}; forget the last race. */
    default void raceSetUp(RaceTick race, long seed) {
    }

    /** A race extra: one tick of the race proper, after every car has driven. */
    default void raceTick(RaceTick race) {
    }

    /** A race extra: saves, restores or visits everything {@link #raceTick} changes, always in the same order. */
    default void sync(RaceInts state) {
    }

    /** A race extra: what it adds to car {@code car}'s boost this tick (the stunt power's, which sets its pull and top speed). */
    default float boost(int car) {
        return 0F;
    }

    /** A race extra: the share of car {@code car}'s grip it leaves this tick, 1 for all of it (nitrous burning leaves less). */
    default float grip(int car) {
        return 1F;
    }

    /** A race starts (or the feature was switched on during one): forget the last race, read the cars. */
    default void raceStarted(RaceView race) {
    }

    /** A real race tick has ended; {@code race} holds where everything is now and the tick's hits. */
    default void afterTick(RaceView race) {
    }

    /** The race has ended, been left, or the feature was switched off during it. */
    default void raceEnded(RaceView race) {
    }

    /**
     * A frame of the race is about to be drawn: say what this feature draws
     * in it, and where in the game's back-to-front order ({@link DrawQueue}).
     */
    default void queue(DrawQueue queue) {
    }

    /** Draws item {@code item} of those queued, in its place in the order. */
    default void draw(Canvas canvas, int item) {
    }
}
