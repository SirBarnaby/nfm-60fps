package nfm.lit.features.wrecks;

import java.util.Arrays;
import nfm.lit.features.BlastPush;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The race half of the exploding wrecks: how long each wasted car has
 * burned, and the explosion two seconds on, which throws and damages the
 * cars close by as a small dynamite does ({@link BlastPush#wreck}) and
 * kicks the wreck itself off the ground. It runs inside the race tick,
 * run-ahead's and the verifier's too, so everything it changes is in the
 * arrays {@link #sync} saves and restores, and it reads and moves the race
 * only through {@link RaceTick}. It throws no dice.
 */
final class WreckRace {

    /** Ticks a second: the race's 19. */
    static final int SECOND = 19;
    /** A wasted car burns this long, then explodes. */
    static final int FUSE = 2 * SECOND;
    /** {@link #burned} of a wreck that has exploded. */
    static final int BLOWN = -1;
    /** The wreck's own wheels are kicked up this much, two of them this much more, so it hops and tips. */
    private static final float HOP = 24F;
    private static final float HOP_TIP = 10F;

    int cars;

    /* ------------------------------------------- the state (see sync) */

    /** Ticks each car has been wasted (0: it still races); {@link #BLOWN} once it has exploded. */
    int[] burned = new int[0];
    /** Where each wreck exploded, the ground under it there, and whether it dented that. */
    float[] blastX = new float[0];
    float[] blastY = new float[0];
    float[] blastZ = new float[0];
    float[] blastGround = new float[0];
    boolean[] dented = new boolean[0];

    /** The stage has loaded: nobody is wasted yet. */
    void setUp(RaceTick race) {
        cars = race.cars();
        burned = new int[cars];
        blastX = new float[cars];
        blastY = new float[cars];
        blastZ = new float[cars];
        blastGround = new float[cars];
        dented = new boolean[cars];
    }

    /** Saves, restores or visits everything a tick changes, always in this order. */
    void sync(RaceInts s) {
        s.contents(burned);
        s.contents(blastX);
        s.contents(blastY);
        s.contents(blastZ);
        s.contents(blastGround);
        s.contents(dented);
    }

    /** One tick, after every car has driven. */
    void tick(RaceTick race) {
        if (race.cars() != cars) {
            return; // never in a race: the cars are set when the stage loads
        }
        for (int c = 0; c < cars; c++) {
            if (!race.wasted(c)) {
                burned[c] = 0;
                continue;
            }
            if (burned[c] != BLOWN && ++burned[c] >= FUSE) {
                explode(race, c);
            }
        }
    }

    /** Wreck {@code c} blows up where it is. */
    private void explode(RaceTick race, int c) {
        float x = race.x(c);
        float y = race.y(c);
        float z = race.z(c);
        float ground = race.grounded(c) ? y + race.wheelDrop(c) : race.ground(x, y, z);
        burned[c] = BLOWN;
        blastX[c] = x;
        blastY[c] = y;
        blastZ[c] = z;
        blastGround[c] = ground;
        dented[c] = ground - y < BlastPush.CRATER_HEIGHT;
        BlastPush.wreck(race, x, y, z); // the wreck itself is wasted, so the blast passes it by
        for (int w = 0; w < 4; w++) {
            race.push(c, w, 0F, -(HOP + (w == 0 || w == 3 ? HOP_TIP : 0F)), 0F);
        }
    }

    /** Wrecks that have exploded so far. */
    int blown() {
        int n = 0;
        for (int b : burned) {
            n += b == BLOWN ? 1 : 0;
        }
        return n;
    }

    @Override
    public String toString() {
        return "wrecks " + Arrays.toString(burned);
    }
}
