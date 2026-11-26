package nfm.lit.features;

/**
 * What a feature may know of the race, copied from it after each real race
 * tick. It is a copy: writing it changes nothing in the race.
 * <p>
 * Units are the game's: positions in world units (the flat ground is at
 * {@code y = 250}, and y grows downwards), angles in degrees as the cars'
 * models turn them (roll {@code xy}, then pitch {@code zy}, then yaw
 * {@code xz}), velocities in units per tick (19 ticks a second). Per-car
 * arrays are indexed by car (0 is Player 1's), per-wheel ones by
 * {@code car * 4 + wheel} (wheels 0 and 1 at the front, 0 and 2 on the left
 * as the model has them).
 */
public final class RaceView {

    public static final int WHEELS = 4;
    /** At most this many hits are kept per tick. */
    public static final int MAX_HITS = 32;

    /* ---------------------------------------------------------- the race */

    /** Cars in the race. */
    public int cars;
    /** People driving (2 in split screen); their cars are 0 and 1. */
    public int humans;
    /** Real ticks since the race started. */
    public int tick;
    /** The race's seed: features that throw dice start from it, so a race replayed looks the same. */
    public long seed;
    /** The stage's number and name. */
    public int stage;
    public String stageName = "";
    /** The stage's ground colour, 0xRRGGBB (what soft ground and mud look like there). */
    public int groundColor;
    /**
     * True if a feature may keep files between races (in {@code cache\});
     * false in test runs, recording and verifying, which leave no trace.
     */
    public boolean keepsFiles;
    /** The folder the game keeps its caches in. */
    public java.nio.file.Path cacheDir = java.nio.file.Path.of("cache");

    /* ------------------------------------------------------------ per car */

    /** The car's model number (its place in the car list). */
    public int[] model = new int[0];
    /** The car's model's file name (such as {@code radicalone}), or "" for one not in the list. */
    public String[] modelName = new String[0];
    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] z = new float[0];
    /** Yaw, roll and pitch, as the car is drawn. */
    public int[] xz = new int[0];
    public int[] xy = new int[0];
    public int[] zy = new int[0];
    /** The steering angle of the front wheels. */
    public int[] steer = new int[0];
    /** How fast the engine drives the car along its heading (the game's {@code speed}, negative backwards). */
    public float[] speed = new float[0];
    /** The car's top speed, to compare {@link #speed} with. */
    public float[] topSpeed = new float[0];
    /** How the car really moves, the mean of its wheels, per tick. */
    public float[] vx = new float[0];
    public float[] vy = new float[0];
    public float[] vz = new float[0];
    /** Damage, 0 (none) to 1 (wasted). */
    public float[] damage = new float[0];
    public boolean[] wasted = new boolean[0];
    public boolean[] capsized = new boolean[0];
    /** Some wheel touches the ground. */
    public boolean[] grounded = new boolean[0];
    /** Ticks since any wheel touched the ground (0 while one does). */
    public int[] airTicks = new int[0];
    /** The keys (or the computer's choices) this tick. */
    public boolean[] up = new boolean[0];
    public boolean[] down = new boolean[0];
    public boolean[] left = new boolean[0];
    public boolean[] right = new boolean[0];
    public boolean[] handbrake = new boolean[0];
    /** The stunt power bar, 0 to 98. */
    public float[] power = new float[0];
    /** The car's radius, in world units. */
    public int[] radius = new int[0];
    /** How far below the car's centre its wheels touch the ground. */
    public int[] wheelDrop = new int[0];
    /** Nitrous burns in the car's engine (the powerups' nitrous). */
    public boolean[] nitrous = new boolean[0];
    /** The car went through a repair hoop this tick: all its damage is fixed. */
    public boolean[] repaired = new boolean[0];
    /** Each car's model as loaded (its polygons), filled when the race starts. */
    public CarShape[] shape = new CarShape[0];

    /* ---------------------------------------------------------- per wheel */

    /** The wheel's place on the car (as the model has it). */
    public int[] keyX = new int[0];
    public int[] keyZ = new int[0];
    /** Where the wheel meets the ground (its lowest point), in the world. */
    public float[] wheelX = new float[0];
    public float[] wheelY = new float[0];
    public float[] wheelZ = new float[0];
    /** The wheel touches the ground. */
    public boolean[] contact = new boolean[0];
    /** The wheel touches a sloped piece (a ramp), which the game draws among its objects rather than as road. */
    public boolean[] onRamp = new boolean[0];
    /** The ground under it: 0 road, 1 and 2 loose, 3 and 4 bumpy (the stage's surfaces). */
    public int[] surface = new int[0];
    /** The colour of the ground under it, 0xRRGGBB: the piece's, or the stage's ground off every piece. */
    public int[] groundRgb = new int[0];
    /** The wheel's velocity (the game's {@code scx}, {@code scz}), per tick. */
    public float[] wheelVX = new float[0];
    public float[] wheelVZ = new float[0];
    /** How fast the wheel slides across the car's heading, per tick. */
    public float[] slipSide = new float[0];
    /**
     * How much faster the wheel turns than the car moves along its heading,
     * per tick: above 0 it spins, below 0 it is locked or dragged.
     */
    public float[] slipLong = new float[0];

    /**
     * The strongest push on the wheel's corner from a touch this tick (a
     * wall, another car), along the world's x and z, as the game measures
     * it before deciding on a hit: 0 if nothing touched it. Light touches
     * that never make a hit show here: a scrape along a wall.
     */
    public float[] touchX = new float[0];
    public float[] touchZ = new float[0];

    /* ---------------------------------------------------------- the hits */

    /** Hits in the last tick, each a car, a wheel (the corner hit), an axis (0 x, 1 y, 2 z) and a force. */
    public int hits;
    public final int[] hitCar = new int[MAX_HITS];
    public final int[] hitWheel = new int[MAX_HITS];
    public final int[] hitAxis = new int[MAX_HITS];
    public final float[] hitForce = new float[MAX_HITS];

    /* ---------------------------------------------------------- the rams */

    /** At most this many rams are kept per tick. */
    public static final int MAX_RAMS = 32;
    /**
     * Rams in the last tick: car {@code ramCar}'s corner {@code ramWheel} pushed car {@code ramOther}'s
     * corner {@code ramOtherWheel} by {@code ramPush} units a tick, along the world's x or z.
     */
    public int rams;
    public final int[] ramCar = new int[MAX_RAMS];
    public final int[] ramWheel = new int[MAX_RAMS];
    public final int[] ramOther = new int[MAX_RAMS];
    public final int[] ramOtherWheel = new int[MAX_RAMS];
    public final float[] ramPush = new float[MAX_RAMS];

    /** Room for {@code n} cars. */
    public void fit(int n) {
        if (model.length >= n) {
            return;
        }
        model = new int[n];
        modelName = new String[n];
        java.util.Arrays.fill(modelName, "");
        x = new float[n];
        y = new float[n];
        z = new float[n];
        xz = new int[n];
        xy = new int[n];
        zy = new int[n];
        steer = new int[n];
        speed = new float[n];
        topSpeed = new float[n];
        vx = new float[n];
        vy = new float[n];
        vz = new float[n];
        damage = new float[n];
        wasted = new boolean[n];
        capsized = new boolean[n];
        grounded = new boolean[n];
        airTicks = new int[n];
        up = new boolean[n];
        down = new boolean[n];
        left = new boolean[n];
        right = new boolean[n];
        handbrake = new boolean[n];
        power = new float[n];
        radius = new int[n];
        wheelDrop = new int[n];
        nitrous = new boolean[n];
        repaired = new boolean[n];
        CarShape[] shapes = new CarShape[n];
        for (int c = 0; c < n; c++) {
            shapes[c] = c < shape.length ? shape[c] : new CarShape();
        }
        shape = shapes;
        int w = n * WHEELS;
        keyX = new int[w];
        keyZ = new int[w];
        wheelX = new float[w];
        wheelY = new float[w];
        wheelZ = new float[w];
        contact = new boolean[w];
        onRamp = new boolean[w];
        surface = new int[w];
        groundRgb = new int[w];
        wheelVX = new float[w];
        wheelVZ = new float[w];
        slipSide = new float[w];
        slipLong = new float[w];
        touchX = new float[w];
        touchZ = new float[w];
    }

    /**
     * Point {@code (lx, ly, lz)} of car {@code car}'s own frame (as its
     * model has it) in the world, as the car is after the tick, into
     * {@code out[0..2]}.
     */
    public void toWorld(int car, float lx, float ly, float lz, float[] out) {
        double roll = Math.toRadians(xy[car]);
        double pitch = Math.toRadians(zy[car]);
        double yaw = Math.toRadians(xz[car]);
        float sr = (float) Math.sin(roll);
        float cr = (float) Math.cos(roll);
        float sp = (float) Math.sin(pitch);
        float cp = (float) Math.cos(pitch);
        float sy = (float) Math.sin(yaw);
        float cy = (float) Math.cos(yaw);
        float x1 = lx * cr - ly * sr;
        float y1 = lx * sr + ly * cr;
        float y2 = y1 * cp - lz * sp;
        float z2 = y1 * sp + lz * cp;
        out[0] = x[car] + x1 * cy - z2 * sy;
        out[1] = y[car] + y2;
        out[2] = z[car] + x1 * sy + z2 * cy;
    }

    /** The car's speed over the ground, per tick. */
    public float groundSpeed(int car) {
        return (float) Math.sqrt(vx[car] * vx[car] + vz[car] * vz[car]);
    }
}
