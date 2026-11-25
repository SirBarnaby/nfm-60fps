package nfm.lit.features.skidmarks;

import nfm.lit.features.RaceView;

/**
 * How each wheel's tread moves over the ground, from what the race says
 * after a tick: whether the wheel touches, how fast it slides sideways and
 * lengthwise, and what kind of mark that leaves.
 * <p>
 * The game's cars grip until a turn, the brakes or a hit ask more than their
 * grip gives; then the car's velocity lags its heading (it slides). That
 * slide, the car's turn rate and the wheels' own velocities are shared out
 * over the four wheels the way a real car's would be:
 * <ul>
 * <li>a slide with the body nearly straight (understeer) is taken by the
 * steered front wheels, the rear rolling;
 * <li>a slide with the body well across its path (a power slide, a
 * handbrake turn) is taken by the rear, which also swings round with the
 * car's turn, the fronts sliding only as far as they are not steered into
 * the slide (countersteering straightens them);
 * <li>the outside wheels, which carry the load, slide harder;
 * <li>the handbrake locks the rear wheels; braking or spinning beyond the
 * grip slides each wheel lengthwise by its own amount;
 * <li>a wheel off the ground slides nothing.
 * </ul>
 * Plain arithmetic on the copy of the race; it keeps only the cars' last
 * headings. The rebuilt engine sound plays each wheel's skid from the same
 * slide.
 */
public final class SkidSlip {

    static final int NONE = 0;
    /** Locked or dragged: sliding along the mark. */
    static final int LOCKED = 1;
    /** Rolling and sliding sideways. */
    static final int SIDEWAYS = 2;
    /** Both. */
    static final int MIXED = 3;
    /** The tread turning faster than the car moves. */
    static final int SPIN = 4;

    private static final float DEG = (float) (Math.PI / 180D);
    /** Sideways slide (units a tick) a little above which a mark starts, and below which it ends. */
    static final float ON = 4F;
    static final float OFF = 2.5F;
    /** The slip below which a wheel rolls cleanly ({@link #ratio}). */
    static final float CLEAN = 0.1F;

    /* per wheel, after update: car * 4 + wheel */
    float[] lateral = new float[0];
    float[] longitudinal = new float[0];
    /** The slide's size, units a tick. */
    public float[] slide = new float[0];
    /** The slip: lengthwise as a share of the car's speed, sideways about the slip angle's sine twice over. */
    float[] ratio = new float[0];
    int[] kind = new int[0];

    /* per car */
    private int[] lastYaw = new int[0];
    private boolean[] known = new boolean[0];
    /** The car's turn rate, degrees a tick. */
    float[] turn = new float[0];
    /** The body's angle to its path, degrees. */
    float[] bodySlip = new float[0];
    /** Ticks since the car came down after at least {@link #FLIGHT} ticks in the air (0 on the tick it lands). */
    int[] sinceLanding = new int[0];
    private int[] lastAir = new int[0];

    /** Ticks in the air that make coming down a landing. */
    static final int FLIGHT = 3;
    /** A landing's first ticks, in which a wheel down marks more readily and the car's turn swings its tyres. */
    static final int LANDING = 2;

    public void reset() {
        java.util.Arrays.fill(known, false);
        java.util.Arrays.fill(sinceLanding, 100);
        java.util.Arrays.fill(lastAir, 0);
    }

    /** True in a landing's first ticks. */
    boolean landing(int c) {
        return sinceLanding[c] <= LANDING;
    }

    private float landing(RaceView race, int c) {
        return landing(c) ? 1F : 0F;
    }

    /** Works out every wheel's slide after the tick {@code race} holds. */
    public void update(RaceView race) {
        int cars = race.cars;
        fit(cars);
        for (int c = 0; c < cars; c++) {
            car(race, c);
        }
    }

    private void fit(int cars) {
        if (lastYaw.length >= cars) {
            return;
        }
        int w = cars * RaceView.WHEELS;
        lateral = new float[w];
        longitudinal = new float[w];
        slide = new float[w];
        ratio = new float[w];
        kind = new int[w];
        lastYaw = new int[cars];
        known = new boolean[cars];
        turn = new float[cars];
        bodySlip = new float[cars];
        sinceLanding = new int[cars];
        lastAir = new int[cars];
        java.util.Arrays.fill(sinceLanding, 100);
    }

    private void car(RaceView race, int c) {
        float yawRate = 0F;
        if (known[c]) {
            yawRate = wrap(race.xz[c] - lastYaw[c]);
            if (Math.abs(yawRate) > 60F) {
                yawRate = 0F; // put back on the track or turned round by the game: not a turn
            }
        }
        lastYaw[c] = race.xz[c];
        known[c] = true;
        turn[c] = yawRate;
        int air = lastAir[c];
        lastAir[c] = race.airTicks[c];
        if (race.grounded[c] && air >= FLIGHT) {
            sinceLanding[c] = 0;
        } else if (sinceLanding[c] < 100) {
            sinceLanding[c]++;
        }

        int at0 = c * RaceView.WHEELS;
        float side = 0F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            side += race.slipSide[at0 + w];
        }
        side /= RaceView.WHEELS;
        float yaw = race.xz[c] * DEG;
        float along = race.vx[c] * -(float) Math.sin(yaw) + race.vz[c] * (float) Math.cos(yaw);
        float speed = race.groundSpeed(c);
        // the body's angle to its path, positive when the path is to the car's right
        float signed = (float) Math.atan2(side, Math.max(Math.abs(along), 1F)) / DEG;
        float beta = Math.abs(signed);
        bodySlip[c] = beta;

        // how much the slide is the rear's (a power slide) rather than the steered front's (understeer)
        float drift = race.handbrake[c] ? 1F : smooth(6F, 18F, beta);
        // the fronts' own angle to the path: steering left (positive) turns them further from a path to the
        // right, countersteering (towards the path) straightens them
        float frontAngle = (float) Math.abs(Math.sin((signed + race.steer[c]) * DEG));
        float frontDrift = Math.min(1.2F, frontAngle / Math.max(0.05F, (float) Math.sin(beta * DEG)));
        // under the handbrake the car pivots on its front wheels, which roll on
        float frontShare = (1F - drift) + drift * (race.handbrake[c] ? 0.05F : 0.5F * frontDrift);
        float rearShare = (1F - drift) * 0.15F + drift;
        float swing = Math.max(drift, landing(race, c)) * Math.abs(yawRate * DEG);

        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = at0 + w;
            if (!race.contact[at]) {
                lateral[at] = 0F;
                longitudinal[at] = 0F;
                slide[at] = 0F;
                ratio[at] = 0F;
                kind[at] = NONE;
                continue;
            }
            boolean front = race.keyZ[at] > 0;
            float own = Math.abs(race.slipSide[at]);
            float lat = own * (front ? frontShare : rearShare);
            if (!front || landing(c)) {
                // the car's turn swings the rear round in a slide, and every wheel as it comes down turning
                lat += swing * Math.abs(race.keyZ[at]);
            }
            // the outside wheels carry the load
            float load = Math.min(1F, Math.abs(side) / 10F) * 0.35F;
            lat *= 1F + (Math.signum(race.keyX[at]) == Math.signum(side) ? load : -load);
            float lng = race.slipLong[at];
            if (race.handbrake[c] && !front) {
                lng = -Math.abs(along); // locked: the tread stands while the car moves, either way
            }
            if (lng > 0F && front) {
                lng = 0F; // the rear wheels drive
            }
            lateral[at] = lat;
            longitudinal[at] = lng;
            float s = (float) Math.sqrt(lat * lat + lng * lng);
            slide[at] = s;
            // the slip: lengthwise as a share of the speed, sideways as twice the slip angle's sine, so
            // about 3 degrees of slip angle count as much as a tenth of the speed lengthwise
            float sp = Math.max(8F, speed);
            float lr = lng / sp;
            float sr = 2F * lat / sp;
            ratio[at] = (float) Math.sqrt(lr * lr + sr * sr);
            float absLng = Math.abs(lng);
            if (s < OFF) {
                kind[at] = NONE;
            } else if (lng < 0F && absLng > 2F * lat) {
                kind[at] = LOCKED;
            } else if (lng > 0F && absLng > 2F * lat) {
                kind[at] = SPIN;
            } else if (lat > 2F * absLng) {
                kind[at] = SIDEWAYS;
            } else {
                kind[at] = MIXED;
            }
        }
    }

    static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d > 180F) {
            d -= 360F;
        } else if (d < -180F) {
            d += 360F;
        }
        return d;
    }

    /** 0 at or below {@code from}, 1 at or above {@code to}, smoothly between (smoothstep). */
    public static float smooth(float from, float to, float v) {
        float t = Math.max(0F, Math.min(1F, (v - from) / (to - from)));
        return t * t * (3F - 2F * t);
    }
}
