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
 * slide can be held), the drift assist adding only to a countersteer, a
 * held turn going all the way round, the car's path following its nose as
 * soon as under the game's own grip, and the state restored playing on the
 * same. The owner's drive is the requirement's other check.
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
        turnedRound();
        fullLock();
        response();
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

    /** The tyre grips most at its peak and lets go to about 90% in a full slide: a slide can be held, and it still turns the car. */
    private static void peak() {
        double g = 20;
        double atPeak = g * StrictMath.sin(HandlingRace.SHAPE * StrictMath.atan(HandlingRace.STIFFNESS
                * HandlingRace.PEAK));
        double sliding = g * StrictMath.sin(HandlingRace.SHAPE * StrictMath.atan(HandlingRace.STIFFNESS * 20));
        double share = sliding / atPeak;
        check(F, "past its peak the tyre lets go smoothly, to about 90%", Math.abs(atPeak - g) < 1e-6
                && share > 0.88 && share < 0.93, "peak " + n(atPeak) + ", full slide " + n(share * 100) + "%");
    }

    /**
     * The drift assist only adds to the driver's countersteer: a car sliding
     * 75 degrees off its nose is left as it points with no steering and
     * with steering deeper into the turn, and turned more than the steering
     * alone when the driver steers toward where the car goes.
     */
    private static void drift() {
        int alone = slideTurn(0);
        int deeper = slideTurn(-5);
        int counter = slideTurn(5);
        check(F, "the drift assist adds to a countersteer and never turns the nose by itself or against the steering",
                alone == 0 && deeper == 0 && counter >= 3 && counter <= (int) HandlingRace.MAX_TURN,
                "sliding 75 degrees: no steering " + alone + ", steering deeper " + deeper + ", countersteering 5 "
                        + counter + " degrees more in a tick");
    }

    /** The degrees the suspension itself turns a car sliding 75 degrees off its nose in a tick the game steered {@code steer}. */
    private static int slideTurn(int steer) {
        return slideTurn(steer, false);
    }

    /**
     * The same for a car that stands facing the other way from its heading
     * ({@code turnedRound}, as after a stunt landed turned over both ways:
     * the game drives it along its nose, half a turn from its heading).
     */
    private static int slideTurn(int steer, boolean turnedRound) {
        Car car = new Car();
        car.turnedRound = turnedRound;
        car.cruise(150F, 10);
        Chassis c = car.chassis;
        double a = StrictMath.toRadians(turnedRound ? 255 : 75); // 75 degrees off the nose either way
        for (int w = 0; w < HandlingRace.WHEELS; w++) {
            c.vx[w] = (float) (-150 * StrictMath.sin(a));
            c.vz[w] = (float) (150 * StrictMath.cos(a));
        }
        c.heading += steer;
        int before = c.heading;
        car.drive(150F);
        return HandlingRace.wrap(c.heading - before);
    }

    /**
     * A car that landed a stunt turned over both ways stands facing the
     * other way from its heading, and the game drives it along its nose.
     * The assist reads the nose, so it does there what it does anywhere: it
     * adds to a countersteer, and never turns the car by itself or deeper
     * into the slide (which spun the owner's car on 2026-11-27 until its
     * engine ran backwards).
     */
    private static void turnedRound() {
        int alone = slideTurn(0, true);
        int deeper = slideTurn(-5, true);
        int counter = slideTurn(5, true);
        check(F, "a car facing the other way from its heading after a stunt is assisted the same",
                alone == 0 && deeper == 0 && counter >= 3 && counter <= (int) HandlingRace.MAX_TURN,
                "sliding 75 degrees off its nose: no steering " + alone + ", steering deeper " + deeper
                        + ", countersteering 5 " + counter + " degrees more in a tick");
    }

    /** Full lock held for three seconds (7 degrees a tick, the game's most) turns the car as far as the steering does. */
    private static void fullLock() {
        Car car = new Car();
        car.cruise(200F, 20);
        Chassis c = car.chassis;
        int before = c.heading;
        int steered = 0;
        for (int t = 0; t < 60; t++) {
            c.heading += 7;
            steered += 7;
            c.grip = gameGrip(20F, 7, 200F);
            car.drive(200F);
        }
        int turned = c.heading - before;
        check(F, "a held turn goes all the way round: nothing holds the nose back", turned >= steered * 0.92F,
                "steered " + steered + " degrees in 60 ticks, turned " + turned);
    }

    /**
     * Turning in (the steering growing to 7 degrees a tick, as the game's
     * does) and steering back, the car's path follows its nose as soon as
     * under the game's own grip: in the first half second of the turn the
     * angle between where the car points and where it goes is within a
     * degree of the game's, and over the whole of it less on average. (The
     * game's own grip is a square, stronger between the axes than along
     * them, so tick by tick later on now one leads and now the other.)
     */
    private static void response() {
        Car car = new Car();
        car.cruise(200F, 20);
        Chassis c = car.chassis;
        float gx = c.vx[0];
        float gz = c.vz[0];
        float early = -1000F;
        float sum = 0F;
        float sumGame = 0F;
        int ticks = 44;
        for (int t = 0; t < ticks; t++) {
            int steer = t < 20 ? Math.min(7, 1 + t) : Math.max(-7, 7 - (t - 20) * 2);
            c.heading += steer;
            float grip = gameGrip(20F, steer, 200F);
            c.grip = grip;
            car.drive(200F);
            // the game's own tyres, on the same target: each axis by itself, by the grip
            gx += HandlingRace.clamp(c.targetX - gx, -grip, grip);
            gz += HandlingRace.clamp(c.targetZ - gz, -grip, grip);
            float mine = offNose(c.vx[0], c.vz[0], c.heading);
            float game = offNose(gx, gz, c.heading);
            if (t < 10) {
                early = Math.max(early, mine - game);
            }
            sum += mine;
            sumGame += game;
        }
        check(F, "the car's path follows its nose as soon as under the game's own grip",
                early <= 1F && sum <= sumGame, "turning in and steering back at 200: in the first half second "
                        + "at most " + n(Math.max(0F, early)) + " degrees further off the nose than under the game's "
                        + "grip; over 2.3 s " + n(sum / ticks) + " degrees off on average, the game's "
                        + n(sumGame / ticks));
    }

    /** The game's grip for a tick: the car's, less for the steering at speed, at least 1. */
    private static float gameGrip(float grip, int steer, float speed) {
        return Math.max(1F, grip - Math.abs(steer) * speed / 250F);
    }

    /** The degrees between where a car points and where it goes. */
    private static float offNose(float vx, float vz, int heading) {
        float going = (float) StrictMath.toDegrees(StrictMath.atan2(-vx, vz));
        return Math.abs(HandlingRace.wrap(going - heading));
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
        /** The car stands facing the other way from its heading: the game drives it along its nose. */
        boolean turnedRound;

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
            int along = turnedRound ? -1 : 1;
            chassis.targetX = -(int) (along * at * StrictMath.sin(StrictMath.toRadians(chassis.heading)));
            chassis.targetZ = (int) (along * at * StrictMath.cos(StrictMath.toRadians(chassis.heading)));
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
        public void damage(int car, int wheel, float share) {
        }
    }
}
