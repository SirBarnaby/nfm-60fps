package nfm.lit.features.realism;

import java.util.Arrays;
import nfm.lit.features.RaceView;

/**
 * One car's heat and damage as the effects show them: the temperatures, the
 * coolant, the glass, the lights, the dirt and the scratches. Worked out from the race after each real tick and never read
 * by it. Also where the car is after the tick (its axes in the world), so
 * the effects can place what they draw.
 */
final class CarState {

    /** Crack stars on the windscreen at most, and rays per star. */
    static final int STARS = 3;
    static final int RAYS = 6;
    /** Scratches kept per side, and chips per scratch. */
    static final int SCRATCHES = 8;
    static final int CHIPS = 3;

    final CarParts parts = new CarParts();
    /** Its wipers and the exhaust pipes drawn on it. */
    final Fitted fitted = new Fitted();

    /* where the car is after the tick: its middle and its own axes in the world */
    final float[] at = new float[3];
    final float[] right = new float[3];
    final float[] down = new float[3];
    final float[] ahead = new float[3];

    /* heat, 0 cold to about 1 */
    final float[] brake = new float[RaceView.WHEELS];
    float exhaust;
    float engine;
    boolean braking;
    boolean wasUp;
    /** Ticks of lift-off flame left, and how long the flames reach this tick. */
    int flame;
    final float[] flameReach = new float[2];
    /** A pop's flash at each pipe this tick: its size (1 a pop, more a backfire), 0 for none. */
    final float[] popFlash = new float[2];
    /** Ticks since the car last went fast (for the ticking). */
    int idleTicks;

    /* the radiator: how crushed (0 to 1), which sides leak, the coolant left */
    float crush;
    final boolean[] leaking = new boolean[2];
    float coolant = 1F;
    /** How badly the engine is hurt by rams on its end. */
    float ram;

    /* the windscreen's crack stars: middles and ray ends in the glass's own across and up */
    int stars;
    final float[] starU = new float[STARS];
    final float[] starV = new float[STARS];
    final float[] rayU = new float[STARS * RAYS];
    final float[] rayV = new float[STARS * RAYS];
    final boolean[] windowBroken = new boolean[2];

    /* the lights */
    boolean frontBroken;
    boolean rearBroken;

    /* each wheel bent (degrees) or flat */
    final float[] bent = new float[RaceView.WHEELS];
    final boolean[] flat = new boolean[RaceView.WHEELS];

    /* dirt: how much, and its colour */
    float dirt;
    int dirtRgb;
    /* the wet stain: mud thrown up sliding on soft ground in the rain, how much and its colour */
    float mud;
    int mudRgb;

    /* scratches per side: the polygon, the streak's height and length, the chips */
    final int[] scratches = new int[2];
    final int[] scratchNext = new int[2];
    /** The tick each side was last scratched (a scrape going on lengthens the last scratch). */
    final int[] scratchTick = new int[2];
    final int[] scratchPlane = new int[2 * SCRATCHES];
    final float[] scratchY = new float[2 * SCRATCHES];
    final float[] scratchFrom = new float[2 * SCRATCHES];
    final float[] scratchTo = new float[2 * SCRATCHES];
    final float[] chipY = new float[2 * SCRATCHES * CHIPS];
    final float[] chipZ = new float[2 * SCRATCHES * CHIPS];
    final float[] chipSize = new float[2 * SCRATCHES * CHIPS];

    /*
     * electrical damage: the wipers run on their own and the washer sprays
     * until its water is gone. Ticks of water left; ticks the wipers have
     * run, after the last tick and the one before.
     */
    boolean electric;
    int washer;
    int wiping;
    int wipingWas;

    /* the oil leak: ticks since it began, the tick count at which the engine turns harsh, how far to the next spot */
    boolean leak;
    int leakTicks;
    int harshAt;
    float nextSpot;
    /** The engine runs harsh for want of oil. */
    boolean harsh;
    /** The exhaust is broken. */
    boolean pipeBroken;
    /** The suspension is damaged: it squeaks in turns. Ticks before the next squeak, and the heading a tick ago. */
    boolean suspension;
    int squeakWait;
    int lastXz;

    /** For the checks: ticks the washer sprayed, spots of oil left and squeaks made since the race started. */
    int sprayed;
    int spots;
    int squeaks;

    /* landing and rattling */
    float lastVy;
    int lastAir;
    int rattleWait;
    /** Ticks before a spiked car's next glint. */
    int spikeWait;

    /** Forgets the last race: a cold, clean, whole car. */
    void reset(int groundRgb) {
        Arrays.fill(brake, 0F);
        exhaust = 0F;
        engine = 0F;
        braking = false;
        wasUp = false;
        flame = 0;
        Arrays.fill(flameReach, 0F);
        Arrays.fill(popFlash, 0F);
        idleTicks = 0;
        repair();
        dirt = 0F;
        dirtRgb = groundRgb;
        mud = 0F;
        mudRgb = groundRgb;
        lastVy = 0F;
        lastAir = 0;
        rattleWait = 0;
        spikeWait = 0;
        sprayed = 0;
        spots = 0;
        squeaks = 0;
        lastXz = 0;
    }

    /**
     * A repair hoop: every kind of damage is fixed (the radiator and its coolant, the engine, the glass, the
     * lights, the wheels, the scratches, the electrics, the oil leak, the exhaust and the suspension). Heat and
     * dirt are not damage: they stay.
     */
    void repair() {
        crush = 0F;
        Arrays.fill(leaking, false);
        coolant = 1F;
        ram = 0F;
        stars = 0;
        Arrays.fill(windowBroken, false);
        frontBroken = false;
        rearBroken = false;
        Arrays.fill(bent, 0F);
        Arrays.fill(flat, false);
        Arrays.fill(scratches, 0);
        Arrays.fill(scratchNext, 0);
        Arrays.fill(scratchTick, 0);
        electric = false;
        washer = 0;
        wiping = 0;
        wipingWas = 0;
        leak = false;
        leakTicks = 0;
        harsh = false;
        pipeBroken = false;
        suspension = false;
        squeakWait = 0;
    }

    /** Car {@code c}'s place and axes after the tick, from {@code race}. */
    void pose(RaceView race, int c, float[] scratch) {
        race.toWorld(c, 0F, 0F, 0F, at);
        race.toWorld(c, 1F, 0F, 0F, scratch);
        right[0] = scratch[0] - at[0];
        right[1] = scratch[1] - at[1];
        right[2] = scratch[2] - at[2];
        race.toWorld(c, 0F, 1F, 0F, scratch);
        down[0] = scratch[0] - at[0];
        down[1] = scratch[1] - at[1];
        down[2] = scratch[2] - at[2];
        race.toWorld(c, 0F, 0F, 1F, scratch);
        ahead[0] = scratch[0] - at[0];
        ahead[1] = scratch[1] - at[1];
        ahead[2] = scratch[2] - at[2];
    }

    /** The car's own point {@code (x, y, z)} in the world after the tick, into {@code out}. */
    void world(float x, float y, float z, float[] out) {
        out[0] = at[0] + right[0] * x + down[0] * y + ahead[0] * z;
        out[1] = at[1] + right[1] * x + down[1] * y + ahead[1] * z;
        out[2] = at[2] + right[2] * x + down[2] * y + ahead[2] * z;
    }

    /** True if the car's face at its own point {@code p} with its own normal {@code n} is turned to the eye. */
    boolean faces(float px, float py, float pz, float nx, float ny, float nz, float eyeX, float eyeY, float eyeZ) {
        float wx = right[0] * nx + down[0] * ny + ahead[0] * nz;
        float wy = right[1] * nx + down[1] * ny + ahead[1] * nz;
        float wz = right[2] * nx + down[2] * ny + ahead[2] * nz;
        float ox = at[0] + right[0] * px + down[0] * py + ahead[0] * pz;
        float oy = at[1] + right[1] * px + down[1] * py + ahead[1] * pz;
        float oz = at[2] + right[2] * px + down[2] * py + ahead[2] * pz;
        return wx * (eyeX - ox) + wy * (eyeY - oy) + wz * (eyeZ - oz) > 0F;
    }
}
