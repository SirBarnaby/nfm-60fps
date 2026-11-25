package nfm.lit.features.handling;

import nfm.lit.features.CarBody;
import nfm.lit.features.CarShape;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.Features;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * Suspension handling (approach B, "arcade raycast suspension"): each wheel
 * on a spring and damper, tyres with one grip shared by braking and turning
 * that let go smoothly, weight moving forward under braking and outward in
 * corners, a drift assist, stepped 120 times a second. The race part is
 * {@link HandlingRace}; this half holds the switch, hands the race its
 * calls, and draws each car's body riding on its springs over its wheels,
 * from what the race part holds after real ticks (never a run-ahead tick).
 */
public final class HandlingFeature implements Feature, CarBody {

    public static final String ID = "handling";
    public static final HandlingFeature FEATURE = new HandlingFeature();

    /** The most the body is drawn pitched and rolled on its springs, degrees. */
    static final float MAX_PITCH = 7F;
    static final float MAX_ROLL = 8F;
    /** The share of the springs' mean travel that lowers the body. */
    static final float LOWERING = 0.7F;
    /** Below these the body is drawn where the game has it. */
    private static final float STILL_DEGREES = 0.05F;
    private static final float STILL_UNITS = 0.3F;
    /** A pose: pitch (degrees, negative nose down, as the game's zy), roll (positive right side down, as its xy), lowering (units). */
    private static final int POSE = 3;

    final HandlingRace race = new HandlingRace();

    /* each car's body pose after the tick before the last real one, and after the last: POSE floats a car */
    private int cars;
    private float[] was = new float[0];
    private float[] now = new float[0];
    /** The height of each car's axles in its own numbers: the body turns about them. */
    private float[] axles = new float[0];
    private RaceView view;

    /* the pose of the frame being drawn */
    private float[] sinPitch = new float[0];
    private float[] cosPitch = new float[0];
    private float[] sinRoll = new float[0];
    private float[] cosRoll = new float[0];
    private float[] low = new float[0];
    private boolean[] moved = new boolean[0];

    private HandlingFeature() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean changesRaces() {
        return true;
    }

    /** True while the suspension handling plays in this race. */
    public boolean plays() {
        return Features.raceExtras() && Features.racePlays(this);
    }

    /* ----------------------------------------------------------- the race */

    @Override
    public void raceSetUp(RaceTick r, long seed) {
        race.setUp(r);
    }

    @Override
    public void raceTick(RaceTick r) {
        race.tick(r);
    }

    @Override
    public void sync(RaceInts state) {
        race.sync(state);
    }

    /**
     * Car {@code car}'s tyres this tick, from {@code Madness.drive} while it
     * is on the ground: the grip the game shows, or -1 for a car the race
     * part does not know (the game's own grip then runs).
     */
    public float tyres(int car, Chassis chassis) {
        if (car < 0 || car >= race.cars) {
            return -1F;
        }
        return race.tyres(car, chassis);
    }

    /* ----------------------------------------------------------- the look */

    @Override
    public void raceStarted(RaceView r) {
        view = r;
        cars = Math.min(r.cars, race.cars);
        was = new float[cars * POSE];
        now = new float[cars * POSE];
        axles = new float[cars];
        sinPitch = new float[cars];
        cosPitch = new float[cars];
        sinRoll = new float[cars];
        cosRoll = new float[cars];
        low = new float[cars];
        moved = new boolean[cars];
        poses(r);
        System.arraycopy(now, 0, was, 0, now.length);
    }

    @Override
    public void afterTick(RaceView r) {
        view = r;
        System.arraycopy(now, 0, was, 0, now.length);
        poses(r);
    }

    @Override
    public void raceEnded(RaceView r) {
        cars = 0;
        view = null;
    }

    /** Before each scene: the bodies' pose for the frame, between the last two real ticks as the cars are drawn. */
    @Override
    public void queue(DrawQueue queue) {
        float t = queue.share();
        for (int c = 0, at = 0; c < cars; c++, at += POSE) {
            float pitch = was[at] + (now[at] - was[at]) * t;
            float roll = was[at + 1] + (now[at + 1] - was[at + 1]) * t;
            float lowered = was[at + 2] + (now[at + 2] - was[at + 2]) * t;
            moved[c] = Math.abs(pitch) > STILL_DEGREES || Math.abs(roll) > STILL_DEGREES
                    || Math.abs(lowered) > STILL_UNITS;
            double p = Math.toRadians(pitch);
            double q = Math.toRadians(roll);
            sinPitch[c] = (float) Math.sin(p);
            cosPitch[c] = (float) Math.cos(p);
            sinRoll[c] = (float) Math.sin(q);
            cosRoll[c] = (float) Math.cos(q);
            low[c] = lowered;
        }
    }

    @Override
    public boolean body(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ) {
        if (car < 0 || car >= cars || !moved[car] || view == null) {
            return false;
        }
        CarShape shape = car < view.shape.length ? view.shape[car] : null;
        if (shape != null && plane >= 0 && plane < shape.planes && shape.wheel[plane] >= 0) {
            return false; // a wheel's: it stays on the road
        }
        // rolled, then pitched, about the axles, as the game turns a car (xy, then zy); then lowered
        float axle = axles[car];
        float sr = sinRoll[car];
        float cr = cosRoll[car];
        float sp = sinPitch[car];
        float cp = cosPitch[car];
        float down = low[car];
        for (int k = 0; k < n; k++) {
            float px = x[k];
            float py = y[k] - axle;
            float pz = z[k];
            float rx = px * cr - py * sr;
            float ry = px * sr + py * cr;
            outX[k] = Math.round(rx);
            outY[k] = Math.round(ry * cp - pz * sp + axle + down);
            outZ[k] = Math.round(ry * sp + pz * cp);
        }
        return true;
    }

    /** Every car's body pose on its springs into {@link #now}, and its axles' height (the shapes may come after the start). */
    private void poses(RaceView r) {
        for (int c = 0; c < cars; c++) {
            axles[c] = axleHeight(r, c);
            pose(r, c, c * POSE);
        }
    }

    /** Car {@code c}'s body pose on its springs into {@link #now} at {@code out}. */
    private void pose(RaceView r, int c, int out) {
        now[out] = 0F;
        now[out + 1] = 0F;
        now[out + 2] = 0F;
        int at = c * HandlingRace.WHEELS;
        if (at + HandlingRace.WHEELS > race.travel.length || at + HandlingRace.WHEELS > r.keyX.length) {
            return;
        }
        float meanX = 0F;
        float meanZ = 0F;
        float mean = 0F;
        for (int w = 0; w < HandlingRace.WHEELS; w++) {
            meanX += r.keyX[at + w];
            meanZ += r.keyZ[at + w];
            mean += race.travel[at + w];
        }
        meanX /= HandlingRace.WHEELS;
        meanZ /= HandlingRace.WHEELS;
        mean /= HandlingRace.WHEELS;
        float front = 0F;
        float rear = 0F;
        float right = 0F;
        float left = 0F;
        float frontZ = 0F;
        float rearZ = 0F;
        float rightX = 0F;
        float leftX = 0F;
        int fronts = 0;
        int rights = 0;
        for (int w = 0; w < HandlingRace.WHEELS; w++) {
            float t = race.travel[at + w];
            if (r.keyZ[at + w] > meanZ) {
                front += t;
                frontZ += r.keyZ[at + w];
                fronts++;
            } else {
                rear += t;
                rearZ += r.keyZ[at + w];
            }
            if (r.keyX[at + w] > meanX) {
                right += t;
                rightX += r.keyX[at + w];
                rights++;
            } else {
                left += t;
                leftX += r.keyX[at + w];
            }
        }
        int rears = HandlingRace.WHEELS - fronts;
        int lefts = HandlingRace.WHEELS - rights;
        if (fronts > 0 && rears > 0) {
            float base = Math.max(60F, frontZ / fronts - rearZ / rears);
            float pitch = (float) Math.toDegrees(Math.atan((rear / rears - front / fronts) / base));
            now[out] = HandlingRace.clamp(pitch, -MAX_PITCH, MAX_PITCH);
        }
        if (rights > 0 && lefts > 0) {
            float track = Math.max(40F, rightX / rights - leftX / lefts);
            float roll = (float) Math.toDegrees(Math.atan((right / rights - left / lefts) / track));
            now[out + 1] = HandlingRace.clamp(roll, -MAX_ROLL, MAX_ROLL);
        }
        now[out + 2] = mean * LOWERING;
    }

    /** The mean height of car {@code c}'s wheel hubs in its own numbers (0 without its shape). */
    private static float axleHeight(RaceView r, int c) {
        CarShape shape = c < r.shape.length ? r.shape[c] : null;
        if (shape == null) {
            return 0F;
        }
        float sum = 0F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            sum += shape.hubY[w];
        }
        return sum / RaceView.WHEELS;
    }
}
