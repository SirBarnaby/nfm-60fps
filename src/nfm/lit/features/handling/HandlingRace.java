package nfm.lit.features.handling;

import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The suspension handling's race part: a spring and damper at each wheel,
 * tyres with one grip shared by braking and turning, the axles' loads
 * setting the car's balance, and a drift assist that adds to the driver's
 * countersteer, stepped 120 times a second of game time inside the race's
 * 53 ms tick.
 * <p>
 * It acts at the game's tyres ({@link #tyres}, from {@code Madness.drive}'s
 * grip block, while the car is on the ground) and in the race tick
 * ({@link #tick}, the springs of a car in the air); everything else the car
 * does (steering, gears, air control, stunts, collisions, damage) is the
 * game's. Everything it changes is in the arrays {@link #sync} saves and
 * restores, so run-ahead, rollback and the verifier carry it. Its
 * trigonometry is {@code StrictMath}; it draws no dice.
 */
final class HandlingRace {

    static final int WHEELS = Chassis.WHEELS;
    /** The race's tick, seconds. */
    static final float TICK_SECONDS = 0.053F;
    /** Steps in 25 ticks: 159 in 1.325 s is 120 a second. */
    static final int STEPS_IN_25 = 159;

    /* springs */
    /** How far a spring moves either way from where the car stands, units. */
    static final float TRAVEL = 16F;
    /** How long a spring hangs in the air, units. */
    static final float DROOP = 5F;
    /** Units of travel per unit a tick per tick of the car's speeding up or slowing down, times its bounce. */
    static final float FORWARD = 0.5F;
    /** Units of travel per unit a tick per tick of the corner's sideways pull, times its bounce. */
    static final float SIDEWAYS = 0.55F;
    /** A landing's kick to every spring: units a second per unit a tick of the fall. */
    static final float LANDING = 3.2F;
    static final float MAX_FALL = 60F;
    /** The share of the ground's turn under the car felt by its springs as a kick. */
    static final float GROUND = 0.35F;
    static final int MAX_GROUND_TURN = 20;

    /* tyres */
    /** Units of travel that double a wheel's load. */
    static final float LOAD_UNITS = 18F;
    /** A tyre's grip grows as its load to this power: each unit of load grips less than the one before. */
    static final double LOAD_POWER = 0.8;
    /**
     * The circle's grip at its peak over the game's per-axis grip: the
     * circle through the corners of the game's square. A sliding tyre keeps
     * {@link #SLIDING} of it, 1.27, which is the square's mean pull toward
     * where the car should go; so the car's path follows its nose as soon
     * as the game's does, and a long slide keeps the game's speed (the
     * owner, 2026-11-27: "must be somewhat instant").
     */
    static final float CIRCLE = 1.41F;
    /** The slip, as a share of the grip, at which a tyre grips most. */
    static final float PEAK = 0.7F;
    /** The share of its peak grip a tyre keeps in a full slide. */
    static final double SLIDING = 0.9;
    /** The magic formula's shape: past its peak the grip falls to sin(C pi/2), {@link #SLIDING}. */
    static final double SHAPE = 2 * (StrictMath.PI - StrictMath.asin(SLIDING)) / StrictMath.PI;
    /** Its stiffness: the peak (atan reaching pi/(2C)) falls at a slip of {@link #PEAK}. */
    static final double STIFFNESS = StrictMath.tan(StrictMath.PI / (2 * SHAPE)) / PEAK;

    /* the car's turn */
    /** The steering's turn grows by this times the front axle's grip over the rear's. */
    static final float BALANCE = 0.5F;
    /** Countersteered, a slide past this many degrees between the nose and where the car goes is turned back. */
    static final float DRIFT_ANGLE = 38F;
    /** The share of a slide's excess turned back each tick of countersteer. */
    static final float DRIFT_PULL = 0.6F;
    /** Steering into a slide turns the car this much more. */
    static final float COUNTER = 0.25F;
    static final float MAX_TURN = 12F;
    /** Below this speed (forwards) nothing turns the car but the game. */
    static final float MIN_SPEED = 20F;
    /** Below this speed, units a tick, a car is not sliding. */
    static final float MIN_SLIDE_SPEED = 30F;

    /** Cars in the race (not state: fixed when the stage loads). */
    int cars;

    /* the state: everything below is saved and restored by sync */
    /** The race ticks played, which set each tick's steps. */
    int ticks;
    /** Each wheel's spring: how far it is pushed in (units, negative hanging) and how fast (units a second). */
    float[] travel = new float[0];
    float[] rate = new float[0];
    /** Each car's speed at the end of its last tick. */
    float[] lastSpeed = new float[0];
    /** Each car's pitch and roll at its last tick. */
    int[] lastPitch = new int[0];
    int[] lastRoll = new int[0];
    /** Ticks in a row the car's tyres did not touch. */
    int[] air = new int[0];
    /** How fast the car fell in its last tick in the air, units a tick. */
    float[] fall = new float[0];
    /** The part of a degree of turn left for the next tick (the heading is whole degrees). */
    float[] turnLeft = new float[0];
    /** The car's bounce, from its tyres' last tick (for its springs in the air). */
    float[] bounce = new float[0];
    /** True once the car's tyres ran this tick. */
    boolean[] gripped = new boolean[0];

    /* scratch, never state */
    private final float[] rest = new float[WHEELS];
    private final boolean[] front = new boolean[WHEELS];
    private final float[] lever = new float[WHEELS];
    private final float[] across = new float[WHEELS];
    private final float[] side = new float[WHEELS];

    /** The steps tick {@code tick} takes: 6 or 7, 159 in every 25 ticks. */
    static int steps(int tick) {
        long t = tick & 0xFFFFFFFFL;
        return (int) (((t + 1) * STEPS_IN_25) / 25 - (t * STEPS_IN_25) / 25);
    }

    /** Sets the race up when its stage loads. */
    void setUp(RaceTick race) {
        cars = race.cars();
        ticks = 0;
        travel = new float[cars * WHEELS];
        rate = new float[cars * WHEELS];
        lastSpeed = new float[cars];
        lastPitch = new int[cars];
        lastRoll = new int[cars];
        air = new int[cars];
        fall = new float[cars];
        turnLeft = new float[cars];
        bounce = new float[cars];
        gripped = new boolean[cars];
        for (int c = 0; c < cars; c++) {
            lastSpeed[c] = race.speed(c);
            lastPitch[c] = race.pitch(c);
            lastRoll[c] = race.roll(c);
            bounce[c] = 1.15F;
        }
    }

    /** Saves, restores or visits the state, in one order. */
    void sync(RaceInts s) {
        ticks = s.value(ticks);
        s.contents(travel);
        s.contents(rate);
        s.contents(lastSpeed);
        s.contents(lastPitch);
        s.contents(lastRoll);
        s.contents(air);
        s.contents(fall);
        s.contents(turnLeft);
        s.contents(bounce);
        s.contents(gripped);
    }

    /**
     * Car {@code c}'s tyres this tick, on the ground: the springs and tyres
     * stepped together, then the car's balance and the drift assist turn
     * its heading. Returns the grip the game shows (its dust, skid sounds
     * and skidmarks): lower while the tyres slide past their peak.
     */
    float tyres(int c, Chassis ch) {
        int at = c * WHEELS;
        bounce[c] = ch.bounce;
        layout(ch);
        kick(c, at, ch);
        air[c] = 0;
        lastPitch[c] = ch.pitch;
        lastRoll[c] = ch.roll;
        gripped[c] = true;

        // where the springs come to rest this tick: weight forward under braking, outward in corners
        int steer = wrap(ch.heading - ch.lastHeading);
        float along = ch.speed - lastSpeed[c];
        float aside = ch.speed * (float) StrictMath.toRadians(steer);
        float b = ch.bounce;
        for (int w = 0; w < WHEELS; w++) {
            float r = FORWARD * b * -along * (front[w] ? 1F : -1F) + SIDEWAYS * b * aside * side[w];
            rest[w] = clamp(r, -TRAVEL, TRAVEL);
        }

        int n = steps(ticks);
        float dt = TICK_SECONDS / n;
        float h = 1F / n;
        float omega = omega(b);
        float damping = 2F * zeta(b) * omega;
        float grip = ch.grip * CIRCLE;
        float worst = 0F;
        float frontGrip = 1F;
        float rearGrip = 1F;
        for (int step = 0; step < n; step++) {
            spring(at, omega, damping, dt);
            frontGrip = axle(at, true);
            rearGrip = axle(at, false);
            float g = grip * (frontGrip + rearGrip) * 0.5F;
            boolean last = step == n - 1;
            for (int w = 0; w < WHEELS; w++) {
                float dx = ch.targetX - ch.vx[w];
                float dz = ch.targetZ - ch.vz[w];
                float slip = (float) StrictMath.sqrt(dx * dx + dz * dz);
                if (slip > 1e-4F) {
                    float pull = (float) (g * StrictMath.sin(SHAPE * StrictMath.atan(STIFFNESS * slip / g)));
                    float move = Math.min(pull * h, slip);
                    ch.vx[w] += dx * move / slip;
                    ch.vz[w] += dz * move / slip;
                    if (last) {
                        worst = Math.max(worst, slip / (PEAK * g));
                    }
                }
            }
        }
        lift(ch);

        float turn = 0F;
        if (!ch.stopped && ch.speed > MIN_SPEED) {
            turn = steer * BALANCE * (frontGrip - rearGrip) + assist(ch, steer);
            turn = clamp(turn, -MAX_TURN, MAX_TURN);
        }
        turnLeft[c] += turn;
        int whole = (int) turnLeft[c];
        turnLeft[c] -= whole;
        ch.heading += whole;
        return worst > 1F ? Math.max(1F, ch.grip / worst) : ch.grip;
    }

    /** One race tick, after every car drove: a car whose tyres did not touch hangs on its springs. */
    void tick(RaceTick race) {
        int n = steps(ticks);
        float dt = TICK_SECONDS / n;
        int count = Math.min(cars, race.cars());
        for (int c = 0; c < count; c++) {
            if (!gripped[c]) {
                if (air[c] < 1_000_000) {
                    air[c]++;
                }
                fall[c] = race.vy(c);
                lastPitch[c] = race.pitch(c);
                lastRoll[c] = race.roll(c);
                java.util.Arrays.fill(rest, -DROOP);
                float omega = omega(bounce[c]);
                float damping = 2F * zeta(bounce[c]) * omega;
                for (int step = 0; step < n; step++) {
                    spring(c * WHEELS, omega, damping, dt);
                }
            }
            gripped[c] = false;
            lastSpeed[c] = race.speed(c);
        }
        ticks++;
    }

    /** Which wheels are at the front, and each wheel's lever along and across the car, from the chassis. */
    private void layout(Chassis ch) {
        float meanX = 0F;
        float meanZ = 0F;
        for (int w = 0; w < WHEELS; w++) {
            meanX += ch.keyX[w];
            meanZ += ch.keyZ[w];
        }
        meanX /= WHEELS;
        meanZ /= WHEELS;
        for (int w = 0; w < WHEELS; w++) {
            front[w] = ch.keyZ[w] > meanZ;
            lever[w] = ch.keyZ[w] - meanZ;
            across[w] = ch.keyX[w] - meanX;
            side[w] = across[w] > 0F ? 1F : -1F;
        }
    }

    /**
     * The kicks to the springs as the tick starts: a landing pushes every
     * spring in; on the ground, the ground turning under the car (a ramp, a
     * bump) pushes the springs at its rising side in and lets the others
     * out, so the body lags behind the ground and settles.
     */
    private void kick(int c, int at, Chassis ch) { // after layout(ch)
        if (air[c] >= 2) {
            float push = clamp(fall[c], 0F, MAX_FALL) * LANDING;
            for (int w = 0; w < WHEELS; w++) {
                rate[at + w] += push;
            }
            return;
        }
        int pitch = clampTurn(wrap(ch.pitch - lastPitch[c]));
        int roll = clampTurn(wrap(ch.roll - lastRoll[c]));
        if (pitch == 0 && roll == 0) {
            return;
        }
        float byPitch = GROUND * (float) StrictMath.toRadians(pitch) / TICK_SECONDS;
        float byRoll = GROUND * (float) StrictMath.toRadians(roll) / TICK_SECONDS;
        for (int w = 0; w < WHEELS; w++) {
            // nose up: the front springs pushed in; right side down: the right springs let out
            rate[at + w] += byPitch * lever[w] - byRoll * across[w];
        }
    }

    /** One step of car {@code at / WHEELS}'s four springs toward {@link #rest}, stopped at the ends of their travel. */
    private void spring(int at, float omega, float damping, float dt) {
        float stiffness = omega * omega;
        for (int w = 0; w < WHEELS; w++) {
            int i = at + w;
            float push = stiffness * (rest[w] - travel[i]) - damping * rate[i];
            rate[i] += push * dt;
            travel[i] += rate[i] * dt;
            if (travel[i] > TRAVEL) {
                travel[i] = TRAVEL;
                rate[i] = Math.min(rate[i], 0F);
            } else if (travel[i] < -TRAVEL) {
                travel[i] = -TRAVEL;
                rate[i] = Math.max(rate[i], 0F);
            }
        }
    }

    /** The grip of the front (or rear) axle's tyres for their load: 1 standing still. */
    private float axle(int at, boolean frontAxle) {
        float sum = 0F;
        int count = 0;
        for (int w = 0; w < WHEELS; w++) {
            if (front[w] == frontAxle) {
                float load = clamp(1F + travel[at + w] / LOAD_UNITS, 0.15F, 2.2F);
                sum += (float) StrictMath.pow(load, LOAD_POWER);
                count++;
            }
        }
        return count == 0 ? 1F : sum / count;
    }

    /** The game's own upward pull of each wheel toward the velocity it wants, with its own grip. */
    private static void lift(Chassis ch) {
        for (int w = 0; w < WHEELS; w++) {
            if (Math.abs(ch.vy[w] - ch.targetY) > ch.grip) {
                if (ch.vy[w] < ch.targetY) {
                    ch.vy[w] += ch.grip;
                } else {
                    ch.vy[w] -= ch.grip;
                }
            } else {
                ch.vy[w] = ch.targetY;
            }
        }
    }

    /**
     * The drift assist, which only ever adds to the driver's own
     * countersteer: while the steering turns the nose toward where the car
     * goes, it turns it that much more, and a slide past
     * {@link #DRIFT_ANGLE} is turned back besides. Steering deeper into the
     * turn, or not at all, it does nothing: the nose is the driver's, as
     * far round as the game's steering takes it. Degrees for this tick.
     * <p>
     * The nose is where the game drives the car (its target velocity), not
     * its heading: a car that landed a stunt turned over both ways stands
     * on its wheels facing the other way, and drives that way, with its
     * heading left half a turn behind.
     */
    private static float assist(Chassis ch, int steer) {
        if (steer == 0 || ch.targetX == 0 && ch.targetZ == 0) {
            return 0F;
        }
        float mx = 0F;
        float mz = 0F;
        for (int w = 0; w < WHEELS; w++) {
            mx += ch.vx[w];
            mz += ch.vz[w];
        }
        mx /= WHEELS;
        mz /= WHEELS;
        if (mx * mx + mz * mz < MIN_SLIDE_SPEED * MIN_SLIDE_SPEED) {
            return 0F;
        }
        // a car heading h drives along (-sin h, cos h): the same angle for where it goes and where it is driven
        float going = (float) StrictMath.toDegrees(StrictMath.atan2(-mx, mz));
        float nose = (float) StrictMath.toDegrees(StrictMath.atan2(-ch.targetX, ch.targetZ));
        float slide = wrap(going - nose);
        float size = Math.abs(slide);
        if (size >= 120F || size <= 8F || steer * slide <= 0F) {
            return 0F; // going backwards, no slide to speak of, or not countersteering
        }
        float turn = steer * COUNTER;
        if (size > DRIFT_ANGLE) {
            turn += Math.signum(slide) * (size - DRIFT_ANGLE) * DRIFT_PULL;
        }
        return turn;
    }

    /** A spring's natural speed, radians a second: 1.4 to 1.85 Hz for the game's bounces. */
    static float omega(float bounce) {
        float hertz = clamp(1.9F - 1.6F * (bounce - 1F), 1.3F, 2F);
        return (float) (2 * StrictMath.PI * hertz);
    }

    /** A damper's share of the critical, 0.3 to 0.4: the bouncier cars settle slower. */
    static float zeta(float bounce) {
        return clamp(0.42F - 0.4F * (bounce - 1F), 0.25F, 0.45F);
    }

    static int wrap(int degrees) {
        int d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d >= 180F) {
            d -= 360F;
        } else if (d < -180F) {
            d += 360F;
        }
        return d;
    }

    private static int clampTurn(int degrees) {
        return Math.max(-MAX_GROUND_TURN, Math.min(MAX_GROUND_TURN, degrees));
    }

    static float clamp(float v, float low, float high) {
        return v < low ? low : v > high ? high : v;
    }
}
