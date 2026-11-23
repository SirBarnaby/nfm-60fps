package nfm.lit.features.powerups;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The powerups' checks ({@link nfm.lit.features.ExtrasCheck}): the race
 * half played on a made-up straight with checkpoints, through a
 * {@link RaceTick} of plain arrays. Each requirement's situation: a pickup,
 * nitrous's burst, dynamite thrown two seconds after the pickup and its
 * blast denting the ground, the magnet's three seconds; and the state
 * saved and restored plays on the same.
 */
public final class PowerupsCheck {

    private static final String F = "powerups";

    private PowerupsCheck() {
    }

    public static void run() {
        boxes();
        pickup();
        nitrous();
        dynamite();
        magnet();
        leaderNeverMagnet();
        rewind();
    }

    /** A row of three boxes at each of the four checkpoints. */
    private static void boxes() {
        Track t = new Track(2);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 1L);
        boolean over = true;
        for (int b = 0; b < r.boxes; b++) {
            over &= r.boxY[b] == 250F - PowerupRace.HOVER;
        }
        check(F, "a row of three boxes over the road at each checkpoint", r.boxes == 12 && over,
                r.boxes + " boxes, 4 checkpoints");
    }

    /** Driving through a box: the car holds a powerup, the box is gone four seconds, a pickup event. */
    private static void pickup() {
        Track t = new Track(2);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 2L);
        t.x[0] = r.boxX[1];
        t.z[0] = r.boxZ[1];
        r.tick(t);
        boolean held = r.held[0] != PowerupRace.NONE;
        int kind = r.eventKind[0];
        check(F, "driving through a box gives a powerup and a pickup sound's event",
                held && kind == PowerupRace.PICKUP && r.boxWait[1] == PowerupRace.RESPAWN,
                "holds " + r.held[0] + ", event " + kind + ", box back in " + r.boxWait[1] + " ticks");
    }

    /** Nitrous on a press: more boost for two seconds, fading to none a second later. */
    private static void nitrous() {
        Track t = new Track(2);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 3L);
        r.held[0] = PowerupRace.NITROUS;
        t.key[0] = true;
        r.tick(t);
        float start = r.boost(0);
        t.key[0] = false;
        int ticks = 0;
        while (r.boost(0) > 0F && ticks < 200) {
            r.tick(t);
            ticks++;
        }
        check(F, "nitrous gives a burst of speed for about three seconds", start == PowerupRace.NITROUS_BOOST
                && ticks == PowerupRace.NITROUS_FULL + PowerupRace.NITROUS_FADE,
                "boost " + n(start) + ", for " + ticks + " more ticks");
    }

    /** Dynamite: thrown forwards 2 s after the pickup; its blast throws a car near it and dents the ground. */
    private static void dynamite() {
        Track t = new Track(2);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 4L);
        r.held[0] = PowerupRace.DYNAMITE;
        r.heldTicks[0] = 0;
        int thrownAt = -1;
        for (int k = 0; k < 60 && thrownAt < 0; k++) {
            r.tick(t);
            for (int s = 0; s < PowerupRace.STICKS; s++) {
                if (r.stickOwner[s] == 0) {
                    thrownAt = k + 1;
                }
            }
        }
        float forward = r.stickVZ[0];
        // a car parked where the stick comes down
        t.x[1] = t.x[0];
        t.z[1] = t.z[0] + 1500F;
        int blastAt = -1;
        for (int k = 0; k < 80 && blastAt < 0; k++) {
            r.tick(t);
            for (int e = 0; e < Math.min(r.events, PowerupRace.EVENTS); e++) {
                if (r.eventKind[e] == PowerupRace.BLAST) {
                    blastAt = e;
                }
            }
        }
        check(F, "dynamite leaves the car forwards two seconds after the pickup",
                thrownAt == PowerupRace.FUSE && forward > 0F, "thrown after " + thrownAt + " ticks, "
                        + n(forward) + " a tick forwards");
        boolean dents = blastAt >= 0 && r.eventWhat[blastAt] == 1;
        check(F, "the blast dents the ground and throws a car near it", dents && t.pushed[1] > 0F
                && t.damaged[1] > 0F, "dent " + dents + ", car 1 pushed " + n(t.pushed[1]) + ", damaged "
                        + n(t.damaged[1]));
    }

    /** The magnet: toward the car ahead for three seconds, then the pull stops. */
    private static void magnet() {
        Track t = new Track(2);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 5L);
        t.place[0] = 1;
        t.place[1] = 0;
        t.z[1] = t.z[0] + 2000F;
        r.held[0] = PowerupRace.MAGNET;
        t.key[0] = true;
        r.tick(t);
        t.key[0] = false;
        float gapBefore = t.z[1] - t.z[0];
        int ticks = 0;
        while (r.magnet[0] > 0 && ticks < 200) {
            r.tick(t);
            ticks++;
        }
        float gapAfter = t.z[1] - t.z[0];
        float z = t.z[0];
        r.tick(t);
        check(F, "the magnet pulls toward the car ahead for 3 seconds, then stops",
                ticks == PowerupRace.MAGNET_TICKS && gapAfter < gapBefore && t.z[0] == z,
                "pulled " + ticks + " ticks, the gap " + n(gapBefore) + " to " + n(gapAfter));
    }

    /** The leader is never given a magnet: there is nobody ahead to pull toward. */
    private static void leaderNeverMagnet() {
        int magnets = 0;
        for (long seed = 1; seed <= 400; seed++) {
            Track t = new Track(2);
            PowerupRace r = new PowerupRace();
            r.setUp(t, seed);
            t.x[0] = r.boxX[0];
            t.z[0] = r.boxZ[0];
            r.tick(t);
            magnets += r.held[0] == PowerupRace.MAGNET ? 1 : 0;
        }
        check(F, "the leader is never given a magnet", magnets == 0, magnets + " magnets in 400 pickups");
    }

    /** Saved, played on and restored, the state plays on exactly as before (run-ahead and rollback). */
    private static void rewind() {
        Track t = new Track(3);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 6L);
        r.held[0] = PowerupRace.DYNAMITE;
        r.held[1] = PowerupRace.NITROUS;
        RaceInts saved = new RaceInts();
        saved.startSaving();
        r.sync(saved);
        saved.endSaving();
        Track copy = t.copy();
        for (int k = 0; k < 70; k++) {
            r.tick(t);
        }
        RaceInts first = new RaceInts();
        first.startSaving();
        r.sync(first);
        first.endSaving();
        saved.startRestoring();
        r.sync(saved);
        saved.endRestoring();
        for (int k = 0; k < 70; k++) {
            r.tick(copy);
        }
        RaceInts again = new RaceInts();
        again.startSaving();
        r.sync(again);
        again.endSaving();
        check(F, "restored, the race extras play on the same", first.sameAs(again) && first.size() > 0,
                first.size() + " values");
    }

    /** A straight along z with a checkpoint every 1500 units, the cars on it, as plain arrays. */
    private static final class Track implements RaceTick {
        final int cars;
        final float[] x;
        final float[] y;
        final float[] z;
        final int[] place;
        final boolean[] key;
        final float[] pushed;
        final float[] damaged;

        Track(int cars) {
            this.cars = cars;
            x = new float[cars];
            y = new float[cars];
            z = new float[cars];
            place = new int[cars];
            key = new boolean[cars];
            pushed = new float[cars];
            damaged = new float[cars];
            for (int c = 0; c < cars; c++) {
                x[c] = -900F - 400F * c; // beside the boxes
                y[c] = 220F;
                z[c] = -3000F;
                place[c] = c;
            }
        }

        Track copy() {
            Track t = new Track(cars);
            System.arraycopy(x, 0, t.x, 0, cars);
            System.arraycopy(y, 0, t.y, 0, cars);
            System.arraycopy(z, 0, t.z, 0, cars);
            System.arraycopy(place, 0, t.place, 0, cars);
            return t;
        }

        @Override
        public int cars() {
            return cars;
        }

        @Override
        public int humans() {
            return 1;
        }

        @Override
        public float x(int car) {
            return x[car];
        }

        @Override
        public float y(int car) {
            return y[car];
        }

        @Override
        public float z(int car) {
            return z[car];
        }

        @Override
        public int yaw(int car) {
            return 0;
        }

        @Override
        public float speed(int car) {
            return 0F;
        }

        @Override
        public float topSpeed(int car) {
            return 100F;
        }

        @Override
        public float vx(int car) {
            return 0F;
        }

        @Override
        public float vy(int car) {
            return 0F;
        }

        @Override
        public float vz(int car) {
            return 0F;
        }

        @Override
        public int radius(int car) {
            return 120;
        }

        @Override
        public float wheelX(int car, int wheel) {
            return x[car] + ((wheel & 1) == 0 ? -40F : 40F);
        }

        @Override
        public float wheelZ(int car, int wheel) {
            return z[car] + (wheel < 2 ? 70F : -70F);
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
            return false;
        }

        @Override
        public boolean steering(int car) {
            return false;
        }

        @Override
        public boolean useKey(int car) {
            return car == 0 && key[0];
        }

        @Override
        public int place(int car) {
            return place[car];
        }

        @Override
        public int points() {
            return 8;
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
            return point * 750F;
        }

        @Override
        public int pointKind(int point) {
            return point % 2 == 1 ? 1 : 0;
        }

        @Override
        public float ground(float px, float py, float pz) {
            return 250F;
        }

        @Override
        public void push(int car, int wheel, float vx, float vy, float vz) {
            pushed[car] += (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        }

        @Override
        public void move(int car, int dx, int dz) {
            x[car] += dx;
            z[car] += dz;
        }

        @Override
        public void damage(int car, int wheel, float share) {
            damaged[car] += share;
        }
    }
}
