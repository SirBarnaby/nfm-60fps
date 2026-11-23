package nfm.lit.features.powerups;

import java.util.Arrays;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The race half of the powerups: the boxes along the track, what each car
 * holds, nitrous burning, dynamite sticks in flight and their blasts, and
 * magnets pulling. It runs inside the race tick, run-ahead's and the
 * verifier's too, so everything it changes is in the arrays {@link #sync}
 * saves and restores, it draws only from dice of its own, and it reads the
 * race only through {@link RaceTick} (trigonometry through
 * {@link StrictMath}, so every machine plays it the same).
 * <p>
 * What happened in a tick (a pickup, a use, a throw, a blast) goes into a
 * ring of events the look and the sound read after real ticks only.
 */
final class PowerupRace {

    /* what a car holds */
    static final int NONE = 0;
    static final int NITROUS = 1;
    static final int DYNAMITE = 2;
    static final int MAGNET = 3;

    /* events */
    static final int PICKUP = 1;
    static final int USED = 2;
    static final int THROWN = 3;
    static final int BLAST = 4;
    static final int MAGNET_OFF = 5;
    static final int NOBODY_AHEAD = 6;

    /** Ticks a second: the race's 19. */
    static final int SECOND = 19;

    /* boxes */
    /** A row of this many boxes at a checkpoint, this far apart, this high over the road. */
    static final int ROW = 3;
    static final float SPACING = 170F;
    static final float HOVER = 70F;
    /** With more checkpoints than this, every second one has boxes. */
    private static final int MANY_CHECKPOINTS = 16;
    /** A car takes a box within this much plus a share of its radius across, and this much up or down. */
    private static final float REACH = 60F;
    private static final float REACH_PER_RADIUS = 0.4F;
    private static final float REACH_UP = 160F;
    /** A box comes back this long after it was taken. */
    static final int RESPAWN = 4 * SECOND;

    /* nitrous */
    /** Boost added while it burns (the stunt power's scale, 40 to 98), how long, and how long it fades. */
    static final float NITROUS_BOOST = 60F;
    static final int NITROUS_FULL = 2 * SECOND;
    static final int NITROUS_FADE = SECOND;

    /* dynamite */
    /** The stick leaves the car this long after the pickup. */
    static final int FUSE = 2 * SECOND;
    private static final float THROW_FORWARD = 60F;
    private static final float THROW_UP = 16F;
    private static final float GRAVITY = 1.4F;
    /** It blows up on a car this near; the thrower's own only once it has flown this long. */
    private static final float HIT_CAR = 90F;
    private static final int OWN_CAR_AFTER = 12;
    /** It blows up by itself after this long. */
    private static final int FLIGHT = 45;
    /** How far a blast reaches, how hard it throws a wheel away and up, and how much of the most damage it does. */
    static final float BLAST_RADIUS = 520F;
    private static final float BLAST_PUSH = 55F;
    private static final float BLAST_LIFT = 30F;
    private static final float BLAST_TIP = 15F;
    private static final float BLAST_DAMAGE = 0.25F;
    /** A blast this near the ground dents it. */
    static final float CRATER_HEIGHT = 150F;
    static final int STICKS = 8;

    /* magnet */
    static final int MAGNET_TICKS = 3 * SECOND;
    private static final float MAGNET_STEP = 9F;
    private static final float MAGNET_GAP = 260F;
    private static final float MAGNET_BOOST = 25F;

    /* the computer drivers */
    private static final float AI_NITROUS = 0.12F;
    private static final float AI_NITROUS_SPEED = 0.6F;
    private static final float AI_MAGNET = 0.15F;
    private static final float AI_MAGNET_NEAR = 400F;
    private static final float AI_MAGNET_FAR = 3000F;

    /* the events' ring */
    static final int EVENTS = 32;

    /* ---------------------------------------- set when the stage loads */

    int cars;
    int humans;
    int boxes;
    float[] boxX = new float[0];
    float[] boxY = new float[0];
    float[] boxZ = new float[0];
    /** Across which axis each box's row runs: 1 along x, 2 along z (the gate's). */
    int[] boxKind = new int[0];

    /* ------------------------------------------- the state (see sync) */

    private long dice;
    /** Ticks until each box is back; 0 while it is there. */
    int[] boxWait = new int[0];
    int[] held = new int[0];
    /** Ticks since the car picked up what it holds. */
    int[] heldTicks = new int[0];
    /** The Use powerup key was down last tick. */
    private boolean[] keyBefore = new boolean[0];
    /** Ticks of nitrous left, the last {@link #NITROUS_FADE} fading. */
    int[] nitrous = new int[0];
    /** Ticks of magnet left, and the car it pulls toward. */
    int[] magnet = new int[0];
    int[] magnetTarget = new int[0];
    /** The magnet's moves below a whole unit, carried to the next tick. */
    private float[] carryX = new float[0];
    private float[] carryZ = new float[0];
    /** Dynamite in flight: the car that threw it (-1: none), where it is, how it moves, how long it has flown. */
    final int[] stickOwner = new int[STICKS];
    final float[] stickX = new float[STICKS];
    final float[] stickY = new float[STICKS];
    final float[] stickZ = new float[STICKS];
    final float[] stickVX = new float[STICKS];
    final float[] stickVY = new float[STICKS];
    final float[] stickVZ = new float[STICKS];
    final int[] stickAge = new int[STICKS];
    /** The ring of events, and how many there have been. */
    final int[] eventKind = new int[EVENTS];
    final int[] eventCar = new int[EVENTS];
    /** The powerup of a pickup or use; for a blast, 1 if it dented the ground. */
    final int[] eventWhat = new int[EVENTS];
    final float[] eventX = new float[EVENTS];
    final float[] eventY = new float[EVENTS];
    final float[] eventZ = new float[EVENTS];
    int events;

    /** The stage has loaded: the boxes go at its checkpoints, and nobody holds anything. */
    void setUp(RaceTick race, long seed) {
        cars = race.cars();
        humans = race.humans();
        dice = seed;
        placeBoxes(race);
        boxWait = new int[boxes];
        held = new int[cars];
        heldTicks = new int[cars];
        keyBefore = new boolean[cars];
        nitrous = new int[cars];
        magnet = new int[cars];
        magnetTarget = new int[cars];
        carryX = new float[cars];
        carryZ = new float[cars];
        Arrays.fill(magnetTarget, -1);
        Arrays.fill(stickOwner, -1);
        Arrays.fill(stickAge, 0);
        Arrays.fill(eventKind, 0);
        events = 0;
    }

    /** A row of boxes across the road at each checkpoint (every second one on a stage with many). */
    private void placeBoxes(RaceTick race) {
        int gates = 0;
        for (int p = 0; p < race.points(); p++) {
            if (race.pointKind(p) > 0) {
                gates++;
            }
        }
        int every = gates > MANY_CHECKPOINTS ? 2 : 1;
        int rows = (gates + every - 1) / every;
        boxes = rows * ROW;
        boxX = new float[boxes];
        boxY = new float[boxes];
        boxZ = new float[boxes];
        boxKind = new int[boxes];
        int gate = 0;
        int b = 0;
        for (int p = 0; p < race.points(); p++) {
            int kind = race.pointKind(p);
            if (kind <= 0) {
                continue;
            }
            if (gate++ % every != 0) {
                continue;
            }
            for (int k = 0; k < ROW; k++) {
                float offset = (k - (ROW - 1) / 2F) * SPACING;
                float x = race.pointX(p) + (kind == 1 ? offset : 0F);
                float z = race.pointZ(p) + (kind == 1 ? 0F : offset);
                // the road under the gate: the nearest flat top at or below a little over the checkpoint
                float road = race.ground(x, race.pointY(p) - 200F, z);
                boxX[b] = x;
                boxY[b] = road - HOVER;
                boxZ[b] = z;
                boxKind[b] = kind;
                b++;
            }
        }
    }

    /** Saves, restores or visits everything a tick changes, always in this order. */
    void sync(RaceInts s) {
        dice = s.value(dice);
        s.contents(boxWait);
        s.contents(held);
        s.contents(heldTicks);
        s.contents(keyBefore);
        s.contents(nitrous);
        s.contents(magnet);
        s.contents(magnetTarget);
        s.contents(carryX);
        s.contents(carryZ);
        s.contents(stickOwner);
        s.contents(stickX);
        s.contents(stickY);
        s.contents(stickZ);
        s.contents(stickVX);
        s.contents(stickVY);
        s.contents(stickVZ);
        s.contents(stickAge);
        s.contents(eventKind);
        s.contents(eventCar);
        s.contents(eventWhat);
        s.contents(eventX);
        s.contents(eventY);
        s.contents(eventZ);
        events = s.value(events);
    }

    /** What the powerups add to car {@code car}'s boost: nitrous burning, a magnet pulling. */
    float boost(int car) {
        if (car < 0 || car >= cars) {
            return 0F;
        }
        float add = 0F;
        int n = nitrous[car];
        if (n > 0) {
            add += n > NITROUS_FADE ? NITROUS_BOOST : NITROUS_BOOST * n / NITROUS_FADE;
        }
        if (magnet[car] > 0) {
            add += MAGNET_BOOST;
        }
        return add;
    }

    /** One tick, after every car has driven. */
    void tick(RaceTick race) {
        if (race.cars() != cars) {
            return; // never in a race: the cars are set when the stage loads
        }
        for (int b = 0; b < boxes; b++) {
            if (boxWait[b] > 0) {
                boxWait[b]--;
            }
        }
        for (int c = 0; c < cars; c++) {
            boolean key = race.useKey(c);
            boolean pressed = key && !keyBefore[c];
            keyBefore[c] = key;
            if (race.wasted(c)) {
                nitrous[c] = 0;
                stopMagnet(c, false);
                continue;
            }
            if (nitrous[c] > 0) {
                nitrous[c]--;
            }
            pull(race, c);
            if (held[c] == NONE) {
                pickUp(race, c);
                continue;
            }
            heldTicks[c]++;
            use(race, c, pressed);
        }
        for (int s = 0; s < STICKS; s++) {
            if (stickOwner[s] >= 0) {
                fly(race, s);
            }
        }
    }

    /** Car {@code c} takes the first box there within its reach. */
    private void pickUp(RaceTick race, int c) {
        float x = race.x(c);
        float y = race.y(c);
        float z = race.z(c);
        float reach = REACH + REACH_PER_RADIUS * race.radius(c);
        float reach2 = reach * reach;
        for (int b = 0; b < boxes; b++) {
            if (boxWait[b] > 0 || Math.abs(y - boxY[b]) > REACH_UP) {
                continue;
            }
            float dx = x - boxX[b];
            float dz = z - boxZ[b];
            if (dx * dx + dz * dz > reach2) {
                continue;
            }
            boxWait[b] = RESPAWN;
            held[c] = roll(race, c);
            heldTicks[c] = 0;
            event(PICKUP, c, held[c], boxX[b], boxY[b], boxZ[b]);
            return;
        }
    }

    /** What a box gives car {@code c}, by its place: the leader never a magnet, the last car more often one. */
    private int roll(RaceTick race, int c) {
        int place = race.place(c);
        int r = below(100);
        if (place <= 0) {
            return r < 57 ? NITROUS : DYNAMITE;
        }
        if (place >= cars - 1) {
            return r < 40 ? NITROUS : r < 60 ? DYNAMITE : MAGNET;
        }
        return r < 40 ? NITROUS : r < 70 ? DYNAMITE : MAGNET;
    }

    /** Car {@code c} uses what it holds: a person on a press of the key, a computer car when it suits, dynamite by itself. */
    private void use(RaceTick race, int c, boolean pressed) {
        boolean person = c < humans;
        switch (held[c]) {
            case NITROUS -> {
                if (person ? pressed : wantsNitrous(race, c)) {
                    nitrous[c] = NITROUS_FULL + NITROUS_FADE;
                    used(c, NITROUS, race);
                }
            }
            case MAGNET -> {
                int ahead = carAhead(race, c);
                if (person && pressed && ahead < 0) {
                    event(NOBODY_AHEAD, c, MAGNET, race.x(c), race.y(c), race.z(c));
                } else if (ahead >= 0 && (person ? pressed : wantsMagnet(race, c, ahead))) {
                    magnet[c] = MAGNET_TICKS;
                    magnetTarget[c] = ahead;
                    carryX[c] = 0F;
                    carryZ[c] = 0F;
                    used(c, MAGNET, race);
                }
            }
            case DYNAMITE -> {
                if (heldTicks[c] >= FUSE) {
                    throwStick(race, c);
                }
            }
            default -> held[c] = NONE;
        }
    }

    private void used(int c, int what, RaceTick race) {
        held[c] = NONE;
        heldTicks[c] = 0;
        event(USED, c, what, race.x(c), race.y(c), race.z(c));
    }

    /** A computer car fires nitrous on the ground, flat out, going straight and already quick. */
    private boolean wantsNitrous(RaceTick race, int c) {
        return race.grounded(c) && race.throttle(c) && !race.steering(c)
                && race.speed(c) > AI_NITROUS_SPEED * race.topSpeed(c) && chance(AI_NITROUS);
    }

    /** A computer car uses the magnet with the car ahead neither too near nor too far. */
    private boolean wantsMagnet(RaceTick race, int c, int ahead) {
        float d = distance(race.x(ahead) - race.x(c), race.z(ahead) - race.z(c));
        return d > AI_MAGNET_NEAR && d < AI_MAGNET_FAR && chance(AI_MAGNET);
    }

    /** The car one place ahead of car {@code c}, if it is still racing; -1 if none. */
    private int carAhead(RaceTick race, int c) {
        int place = race.place(c);
        if (place <= 0) {
            return -1;
        }
        for (int o = 0; o < cars; o++) {
            if (o != c && race.place(o) == place - 1) {
                return race.wasted(o) ? -1 : o;
            }
        }
        return -1;
    }

    /** A magnet pulling car {@code c} toward its target: closing in at most a few units a tick, never ramming. */
    private void pull(RaceTick race, int c) {
        if (magnet[c] <= 0) {
            return;
        }
        int t = magnetTarget[c];
        if (t < 0 || t >= cars || race.wasted(t)) {
            stopMagnet(c, true);
            return;
        }
        float dx = race.x(t) - race.x(c);
        float dz = race.z(t) - race.z(c);
        float d = distance(dx, dz);
        if (d > MAGNET_GAP) {
            float step = Math.min(MAGNET_STEP, (d - MAGNET_GAP) / 10F);
            float mx = dx / d * step + carryX[c];
            float mz = dz / d * step + carryZ[c];
            int ix = (int) mx;
            int iz = (int) mz;
            carryX[c] = mx - ix;
            carryZ[c] = mz - iz;
            if (ix != 0 || iz != 0) {
                race.move(c, ix, iz);
            }
        }
        if (--magnet[c] == 0) {
            stopMagnet(c, true);
        }
    }

    private void stopMagnet(int c, boolean tell) {
        if (magnet[c] > 0 || magnetTarget[c] >= 0) {
            if (tell) {
                event(MAGNET_OFF, c, MAGNET, 0F, 0F, 0F);
            }
            magnet[c] = 0;
            magnetTarget[c] = -1;
        }
    }

    /** Car {@code c} throws its stick from its nose: its own speed and more, forwards and a little up. */
    private void throwStick(RaceTick race, int c) {
        held[c] = NONE;
        heldTicks[c] = 0;
        int s = 0;
        while (s < STICKS && stickOwner[s] >= 0) {
            s++;
        }
        if (s == STICKS) {
            s = oldestStick();
        }
        double yaw = Math.toRadians(race.yaw(c));
        float fx = (float) -StrictMath.sin(yaw);
        float fz = (float) StrictMath.cos(yaw);
        float nose = race.radius(c) * 0.8F;
        stickOwner[s] = c;
        stickAge[s] = 0;
        stickX[s] = race.x(c) + fx * nose;
        stickY[s] = race.y(c) - 30F;
        stickZ[s] = race.z(c) + fz * nose;
        stickVX[s] = race.vx(c) + fx * THROW_FORWARD;
        stickVY[s] = race.vy(c) - THROW_UP;
        stickVZ[s] = race.vz(c) + fz * THROW_FORWARD;
        event(THROWN, c, DYNAMITE, stickX[s], stickY[s], stickZ[s]);
    }

    private int oldestStick() {
        int oldest = 0;
        for (int s = 1; s < STICKS; s++) {
            if (stickAge[s] > stickAge[oldest]) {
                oldest = s;
            }
        }
        return oldest;
    }

    /** A stick flies a tick: it falls, and blows up on the ground, on a car or when its time is up. */
    private void fly(RaceTick race, int s) {
        stickAge[s]++;
        stickX[s] += stickVX[s];
        stickY[s] += stickVY[s];
        stickZ[s] += stickVZ[s];
        stickVY[s] += GRAVITY;
        float ground = race.ground(stickX[s], stickY[s] - stickVY[s] - 10F, stickZ[s]);
        if (stickY[s] >= ground) {
            blast(race, s, stickX[s], ground, stickZ[s], ground);
            return;
        }
        for (int c = 0; c < cars; c++) {
            if (race.wasted(c) || c == stickOwner[s] && stickAge[s] < OWN_CAR_AFTER) {
                continue;
            }
            float dx = race.x(c) - stickX[s];
            float dy = race.y(c) - stickY[s];
            float dz = race.z(c) - stickZ[s];
            if (dx * dx + dy * dy + dz * dz < HIT_CAR * HIT_CAR) {
                blast(race, s, stickX[s], stickY[s], stickZ[s], ground);
                return;
            }
        }
        if (stickAge[s] >= FLIGHT) {
            blast(race, s, stickX[s], stickY[s], stickZ[s], ground);
        }
    }

    /**
     * Stick {@code s} blows up at {@code (x, y, z)} over ground at
     * {@code ground}: every car within reach is thrown away and up (its
     * nearer wheels more, so it tips), damaged and shaken, by how near it is.
     */
    private void blast(RaceTick race, int s, float x, float y, float z, float ground) {
        int owner = stickOwner[s];
        stickOwner[s] = -1;
        for (int c = 0; c < cars; c++) {
            if (race.wasted(c)) {
                continue;
            }
            float dx = race.x(c) - x;
            float dy = race.y(c) - y;
            float dz = race.z(c) - z;
            float d = (float) StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= BLAST_RADIUS) {
                continue;
            }
            float share = 1F - d / BLAST_RADIUS;
            float nearest = Float.MAX_VALUE;
            float farthest = 0F;
            int nearWheel = 0;
            for (int w = 0; w < 4; w++) {
                float wd = distance(race.wheelX(c, w) - x, race.wheelZ(c, w) - z);
                if (wd < nearest) {
                    nearest = wd;
                    nearWheel = w;
                }
                farthest = Math.max(farthest, wd);
            }
            for (int w = 0; w < 4; w++) {
                float wx = race.wheelX(c, w) - x;
                float wz = race.wheelZ(c, w) - z;
                float wd = distance(wx, wz);
                float ax = wd > 1F ? wx / wd : 0F;
                float az = wd > 1F ? wz / wd : 0F;
                float near = farthest > nearest ? (farthest - wd) / (farthest - nearest) : 1F;
                float lift = (BLAST_LIFT + BLAST_TIP * near) * share;
                race.push(c, w, ax * BLAST_PUSH * share, -lift, az * BLAST_PUSH * share);
            }
            race.damage(c, nearWheel, BLAST_DAMAGE * share);
        }
        boolean dents = ground - y < CRATER_HEIGHT;
        event(BLAST, owner, dents ? 1 : 0, x, dents ? ground : y, z);
    }

    private void event(int kind, int car, int what, float x, float y, float z) {
        int at = events % EVENTS;
        eventKind[at] = kind;
        eventCar[at] = car;
        eventWhat[at] = what;
        eventX[at] = x;
        eventY[at] = y;
        eventZ[at] = z;
        events++;
    }

    private static float distance(float dx, float dz) {
        return (float) StrictMath.sqrt(dx * dx + dz * dz);
    }

    /* ----------------------------------------------- the dice (SplitMix64) */

    private long next() {
        dice += 0x9E3779B97F4A7C15L;
        long z = dice;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A whole number from 0 to {@code n - 1}. */
    private int below(int n) {
        return (int) ((next() >>> 33) % n);
    }

    /** True with chance {@code p}. */
    private boolean chance(float p) {
        return (next() >>> 40) < (long) (p * (1L << 24));
    }
}
