package nfm.lit.features.handling;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The suspension handling's checks ({@link nfm.lit.features.ExtrasCheck}):
 * its race part played on made-up cars, through a {@link Chassis} as the
 * game hands it over and a {@link RaceTick} of plain values. The 120 steps a
 * second, the springs loading the front under braking and settling, one
 * grip shared by braking and turning, the grip falling past its peak (a
 * slide can be held), the drift assist's turn, and the state restored
 * playing on the same. The owner's drive is the requirement's other check.
 */
public final class HandlingCheck {

    private static final String F = "handling";

    private HandlingCheck() {
    }

    public static void run() {
        steps();
        braking();
        circle();
        peak();
        drift();
        rewind();
    }

    /** 120 steps a second of game time: 6 or 7 a 53 ms tick, 6360 in 1000 ticks (53 s). */
    private static void steps() {
        int sum = 0;
        boolean sixOrSeven = true;
        for (int t = 0; t < 1000; t++) {
            int s = HandlingRace.steps(t);
            sum += s;
            sixOrSeven &= s == 6 || s == 7;
        }
        float perSecond = sum / (1000 * HandlingRace.TICK_SECONDS);
        check(F, "the suspension and tyres step 120 times a second", sixOrSeven && Math.abs(perSecond - 120F) < 0.01F,
                sum + " steps in 1000 ticks, " + n(perSecond) + " a second");
    }

    /** Braking loads the front springs and lets the rear out; let go, they settle within three seconds. */
    private static void braking() {
        Car car = new Car();
        HandlingRace r = car.race;
        car.cruise(150F, 40);
        float speed = 150F;
        for (int t = 0; t < 8; t++) {
            speed -= 12F;
            car.drive(speed);
        }
        float front = (r.travel[0] + r.travel[1]) / 2F;
        float rear = (r.travel[2] + r.travel[3]) / 2F;
        check(F, "braking moves the weight forward: the front springs in, the rear out", front > 2F && rear < -2F,
                "front " + n(front) + ", rear " + n(rear) + " units");
        car.cruise(speed, 57);
        float most = 0F;
        for (int w = 0; w < HandlingRace.WHEELS; w++) {
            most = Math.max(most, Math.abs(r.travel[w]));
        }
        check(F, "the springs settle when the car drives on steadily", most < 0.5F,
                "within " + n(most) + " units after 3 s");
    }

    /** One grip shared by braking and turning: a slip along a diagonal is pulled in as far as one along an axis. */
    private static void circle() {
        float straight = pullOf(100, 0);
        float diagonal = pullOf(71, 71);
        check(F, "braking and turning share one grip (a circle, not the game's square)",
                Math.abs(diagonal - straight) < 0.02F * straight, "along an axis " + n(straight) + ", diagonally "
                        + n(diagonal) + " units a tick");
    }

    /** The tyre grips most at its peak and lets go to about 71% in a full slide: a slide can be held. */
    private static void peak() {
        double g = 20;
        double atPeak = g * StrictMath.sin(HandlingRace.SHAPE * StrictMath.atan(HandlingRace.STIFFNESS
                * HandlingRace.PEAK));
        double sliding = g * StrictMath.sin(HandlingRace.SHAPE * StrictMath.atan(HandlingRace.STIFFNESS * 20));
        double share = sliding / atPeak;
        check(F, "past its peak the tyre lets go smoothly, to about 71%", Math.abs(atPeak - g) < 1e-6
                && share > 0.69 && share < 0.74, "peak " + n(atPeak) + ", full slide " + n(share * 100) + "%");
    }

    /** A car sliding 60 degrees off its nose is turned toward where it goes. */
    private static void drift() {
        Car car = new Car();
        car.cruise(150F, 10);
        Chassis c = car.chassis;
        double a = StrictMath.toRadians(60);
        for (int w = 0; w < HandlingRace.WHEELS; w++) {
            c.vx[w] = (float) (-150 * StrictMath.sin(a));
            c.vz[w] = (float) (150 * StrictMath.cos(a));
        }
        int before = c.heading;
        car.drive(150F);
        int turned = HandlingRace.wrap(c.heading - before);
        check(F, "the drift assist turns a slide past 38 degrees back toward where the car goes",
                turned >= 8 && turned <= (int) HandlingRace.MAX_TURN, "turned " + turned + " degrees in a tick");
    }

    /** Saved, played on and restored, the state plays on exactly as before (run-ahead and rollback). */
    private static void rewind() {
        Car car = new Car();
        car.cruise(120F, 5);
        RaceInts saved = save(car.race);
        Car copy = car.copy();
        float speed = 120F;
        for (int t = 0; t < 30; t++) {
            speed += t < 15 ? -9F : 6F;
            car.drive(speed);
        }
        RaceInts first = save(car.race);
        saved.startRestoring();
        car.race.sync(saved);
        saved.endRestoring();
        copy.race = car.race;
        speed = 120F;
        for (int t = 0; t < 30; t++) {
            speed += t < 15 ? -9F : 6F;
            copy.drive(speed);
        }
        RaceInts again = save(copy.race);
        check(F, "restored, the springs and tyres play on the same", first.sameAs(again) && first.size() > 0,
                first.size() + " values");
    }

    private static RaceInts save(HandlingRace r) {
        RaceInts s = new RaceInts();
        s.startSaving();
        r.sync(s);
        s.endSaving();
        return s;
    }

    /** How far one tick pulls a still wheel toward the target {@code (x, z)}. */
    private static float pullOf(int x, int z) {
        Car car = new Car();
        Chassis c = car.chassis;
        c.targetX = x;
        c.targetZ = z;
        car.race.tyres(0, c);
        return (float) Math.sqrt(c.vx[0] * c.vx[0] + c.vz[0] * c.vz[0]);
    }

    /** One made-up car on a flat road, heading along z, as the game hands it to the tyres each tick. */
    private static final class Car implements RaceTick {
        HandlingRace race = new HandlingRace();
        final Chassis chassis = new Chassis();
        float speed;

        Car() {
            int[] kx = {-40, 40, -40, 40};
            int[] kz = {70, 70, -70, -70};
            System.arraycopy(kx, 0, chassis.keyX, 0, 4);
            System.arraycopy(kz, 0, chassis.keyZ, 0, 4);
            chassis.grip = 20F;
            chassis.bounce = 1.2F;
            race.setUp(this);
        }

        Car copy() {
            Car c = new Car();
            System.arraycopy(chassis.vx, 0, c.chassis.vx, 0, 4);
            System.arraycopy(chassis.vy, 0, c.chassis.vy, 0, 4);
            System.arraycopy(chassis.vz, 0, c.chassis.vz, 0, 4);
            c.chassis.heading = chassis.heading;
            c.chassis.lastHeading = chassis.lastHeading;
            c.speed = speed;
            return c;
        }

        /** Drives on at {@code speed} for {@code ticks} ticks. */
        void cruise(float at, int ticks) {
            for (int t = 0; t < ticks; t++) {
                drive(at);
            }
        }

        /** One tick at {@code at}: the tyres, then the race tick. */
        void drive(float at) {
            speed = at;
            chassis.speed = at;
            chassis.targetX = -(int) (at * StrictMath.sin(StrictMath.toRadians(chassis.heading)));
            chassis.targetZ = (int) (at * StrictMath.cos(StrictMath.toRadians(chassis.heading)));
            chassis.targetY = 0;
            race.tyres(0, chassis);
            chassis.lastHeading = chassis.heading;
            race.tick(this);
        }

        @Override
        public int cars() {
            return 1;
        }

        @Override
        public int humans() {
            return 1;
        }

        @Override
        public float x(int car) {
            return 0F;
        }

        @Override
        public float y(int car) {
            return 250F;
        }

        @Override
        public float z(int car) {
            return 0F;
        }

        @Override
        public int yaw(int car) {
            return chassis.heading;
        }

        @Override
        public float speed(int car) {
            return speed;
        }

        @Override
        public float topSpeed(int car) {
            return 150F;
        }

        @Override
        public float vx(int car) {
            return chassis.vx[0];
        }

        @Override
        public float vy(int car) {
            return chassis.vy[0];
        }

        @Override
        public float vz(int car) {
            return chassis.vz[0];
        }

        @Override
        public int radius(int car) {
            return 120;
        }

        @Override
        public float wheelX(int car, int wheel) {
            return chassis.keyX[wheel];
        }

        @Override
        public float wheelZ(int car, int wheel) {
            return chassis.keyZ[wheel];
        }

        @Override
        public boolean grounded(int car) {
            return true;
        }

        @Override
        public boolean wasted(int car) {
            return false;
        }

        @Override
        public boolean throttle(int car) {
            return true;
        }

        @Override
        public boolean steering(int car) {
            return false;
        }

        @Override
        public boolean useKey(int car) {
            return false;
        }

        @Override
        public int place(int car) {
            return 0;
        }

        @Override
        public int points() {
            return 0;
        }

        @Override
        public float pointX(int point) {
            return 0F;
        }

        @Override
        public float pointY(int point) {
            return 250F;
        }

        @Override
        public float pointZ(int point) {
            return 0F;
        }

        @Override
        public int pointKind(int point) {
            return 0;
        }

        @Override
        public float ground(float px, float py, float pz) {
            return 250F;
        }

        @Override
        public void push(int car, int wheel, float vx, float vy, float vz) {
        }

        @Override
        public void move(int car, int dx, int dz) {
        }

        @Override
        public void damage(int car, int wheel, float share) {
        }
    }
}
