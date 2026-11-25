package nfm.lit.features.handling;

/**
 * One car's tyres as the game hands them to the suspension handling inside
 * {@code Madness.drive}'s grip block, and takes them back: the wheels'
 * velocities (moved), the heading (turned), and what the game worked out for
 * the tick. One is reused for every car; it holds nothing between calls, so
 * it is no race state.
 */
public final class Chassis {

    public static final int WHEELS = 4;

    /** Each wheel's velocity, units a tick ({@code scx}, {@code scy}, {@code scz}); y grows downwards. */
    public final float[] vx = new float[WHEELS];
    public final float[] vy = new float[WHEELS];
    public final float[] vz = new float[WHEELS];
    /** Where each wheel sits on the car, in the car's own numbers ({@code keyx}, {@code keyz}; z forwards). */
    public final int[] keyX = new int[WHEELS];
    public final int[] keyZ = new int[WHEELS];
    /** The velocity the game wants this tick: its speed along the heading, tipped by the pitch. */
    public int targetX;
    public int targetY;
    public int targetZ;
    /** The game's grip this tick (its losses to steering, the handbrake and the surface taken), at least 1. */
    public float grip;
    /** The car's bounce ({@code stat.bounce}, 1.05 to 1.3): the cars that leaned most ride softest. */
    public float bounce;
    /** The engine's speed along the heading, units a tick (negative backwards). */
    public float speed;
    /** The heading now, degrees ({@code car.xz}); the suspension may turn it. */
    public int heading;
    /** The heading at the end of the last tick's grip ({@code txz}): the steering is the difference. */
    public int lastHeading;
    /** The car's pitch and roll on the ground, degrees ({@code pzy}, {@code pxy}). */
    public int pitch;
    public int roll;
    /** Capsized, wasted or halted: the game only slows it, nothing turns it. */
    public boolean stopped;
}
