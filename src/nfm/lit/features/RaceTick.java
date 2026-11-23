package nfm.lit.features;

/**
 * What a race extra (a feature that {@link Feature#changesRaces changes
 * races}) reads and moves inside the race tick: the cars as they are after
 * driving this tick, the keys the tick latched, the track, and a few pushes
 * into the race. It is the race itself, not a copy: everything a race extra
 * does through it is part of the race, played again by run-ahead and the
 * verifier, so it may only depend on the race (never on the clock, the
 * screen or {@code Math.random}) and keep its own state in
 * {@link Feature#sync}.
 * <p>
 * Units are {@link RaceView}'s: world units, y growing downwards (the flat
 * ground at 250), angles in degrees, velocities per tick.
 */
public interface RaceTick {

    /** Cars in the race. */
    int cars();

    /** People driving (2 in split screen); their cars are 0 and 1. */
    int humans();

    float x(int car);

    float y(int car);

    float z(int car);

    /** The way the car points, degrees (the model's {@code xz}): it drives along (-sin, cos) of it. */
    int yaw(int car);

    /** How fast the engine drives the car along its heading (negative backwards). */
    float speed(int car);

    /** The car's top speed, to compare {@link #speed} with. */
    float topSpeed(int car);

    /** How the car moves, the mean of its wheels, per tick. */
    float vx(int car);

    float vy(int car);

    float vz(int car);

    /** The car's radius. */
    int radius(int car);

    /** Where wheel {@code wheel} of the car is, across the ground (its place on the car turned by the yaw). */
    float wheelX(int car, int wheel);

    float wheelZ(int car, int wheel);

    /** Some wheel touches the ground. */
    boolean grounded(int car);

    boolean wasted(int car);

    /** The throttle is held (the key or the computer's choice) this tick. */
    boolean throttle(int car);

    /** Steering either way this tick. */
    boolean steering(int car);

    /** The player's Use powerup key is down this tick (always false for a computer car). */
    boolean useKey(int car);

    /** How many cars are ahead of the car in the race (0 for the leader). */
    int place(int car);

    /** Points along the track (the computer drivers' path and the checkpoints), in order. */
    int points();

    float pointX(int point);

    float pointY(int point);

    float pointZ(int point);

    /** 0 or below for a point of the path, 1 for a checkpoint gate across x, 2 for one across z. */
    int pointKind(int point);

    /** The top of the ground under point {@code (x, y, z)}: the nearest flat surface at or below it (250 without one). */
    float ground(float x, float y, float z);

    /** Adds {@code (vx, vy, vz)} to wheel {@code wheel}'s velocity, as a crash does. */
    void push(int car, int wheel, float vx, float vy, float vz);

    /** Moves the car {@code (dx, dz)} whole units across the ground (the game keeps a car's place in whole units). */
    void move(int car, int dx, int dz);

    /**
     * Damages the car by {@code share} of what wastes it, struck at wheel
     * {@code wheel} from below, and shakes its driver's screen.
     */
    void damage(int car, int wheel, float share);
}
