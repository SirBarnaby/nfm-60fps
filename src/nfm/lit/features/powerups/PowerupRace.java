package nfm.lit.features.powerups;

import java.util.Arrays;
import nfm.lit.features.BlastPush;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;

/**
 * The race half of the powerups: the boxes along the track, what each car
 * holds, nitrous burning, dynamite sticks in flight and their blasts, and
 * the bombs dropped on the road. It runs inside the race tick, run-ahead's and the
 * verifier's too, so everything it changes is in the arrays {@link #sync}
 * saves and restores, it draws only from dice of its own, and it reads the
 * race only through {@link RaceTick} (trigonometry through
 * {@link StrictMath}, so every machine plays it the same).
 * <p>
 * What happened in a tick (a pickup, a use, a throw, a blast, a bomb dropped
 * or set off) goes into a
 * ring of events the look and the sound read after real ticks only.
 */
final class PowerupRace {

    /* what a car holds */
    static final int NONE = 0;
    static final int NITROUS = 1;
    static final int DYNAMITE = 2;
    static final int BOMB = 3;

    /* events */
    static final int PICKUP = 1;
    /** Nitrous fired: its {@code what} is 1, or 2 when the last one still burned (a double). */
    static final int USED = 2;
    static final int THROWN = 3;
    static final int BLAST = 4;
    static final int DROPPED = 5;
    static final int BANG = 6;

    /** Ticks a second: the race's 19. */
    static final int SECOND = 19;

    /* boxes */
    /** A row of this many boxes at a checkpoint, this far apart, this high over the road. */
    static final int ROW = 3;
    static final float SPACING = 170F;
    static final float HOVER = 70F;
    /** The first build: a row at each checkpoint, every second one with more checkpoints than this. */
    private static final int MANY_CHECKPOINTS = 16;
    /** Now a row at one checkpoint in this many of the first build's, spread evenly round the lap. */
    static final int RARER = 4;
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
    /** The grip left while it burns (the tyres let go sooner), coming back as it fades. */
    static final float NITROUS_GRIP = 0.55F;

    /* dynamite */
    /** The stick leaves the car this long after the pickup. */
    static final int FUSE = 2 * SECOND;
    private static final float THROW_FORWARD = 60F;
    private static final float THROW_UP = 16F;
    private static final float GRAVITY = 1.4F;
    /** The throw turns toward the nearest enemy ahead within this far, by this much at most (degrees). */
    static final float AIM_MOST = 2.5F;
    private static final float AIM_RANGE = 6000F;
    /** It blows up on a car this near; the thrower's own only once it has flown this long. */
    private static final float HIT_CAR = 90F;
    private static final int OWN_CAR_AFTER = 12;
    /** It blows up by itself after this long. */
    private static final int FLIGHT = 45;
    /* its blast: BlastPush's stick */
    static final int STICKS = 8;

    /* the bomb */
    static final int BOMBS = 12;
    /** Dropped in the air: it falls to the flat ground under it. */
    private static final float FALLS = Float.MAX_VALUE;
    /** Dropped this far behind the car's tail. */
    private static final float BOMB_BEHIND = 60F;
    /** A car sets it off within this much plus a share of its radius across, and this much up or down. */
    private static final float BOMB_REACH = 35F;
    private static final float BOMB_REACH_PER_RADIUS = 0.4F;
    private static final float BOMB_UP = 150F;
    /** The car that dropped it sets it off only after this long. */
    static final int BOMB_OWN_AFTER = SECOND;
    /** The bang: the car that set it off takes this much damage and is thrown this much; cars this near, their share. */
    static final float BANG_DAMAGE = 0.30F;
    static final float BANG_RADIUS = 300F;
    private static final float BANG_PUSH = 20F;
    private static final float BANG_LIFT = 22F;
    private static final float BANG_TIP = 12F;

    /* the computer drivers */
    private static final float AI_NITROUS = 0.12F;
    private static final float AI_NITROUS_SPEED = 0.6F;
    /** A computer car drops its bomb with a car this far behind it, in line; or anyway after holding it this long. */
    private static final float AI_BOMB = 0.3F;
    private static final float AI_BOMB_NEAR = 150F;
    private static final float AI_BOMB_FAR = 1400F;
    private static final float AI_BOMB_IN_LINE = 0.9F;
    private static final int AI_BOMB_KEPT = 15 * SECOND;
    private static final float AI_BOMB_ANYWAY = 0.05F;

    /* the events' ring */
    static final int EVENTS = 32;

    /* ---------------------------------------- set when the stage loads */

    int cars;
    int humans;
    int boxes;
    float[] boxX = new float[0];
    float[] boxY = new float[0];
    float[] boxZ = new float[0];

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
    /** Ticks the nitrous before the last still burns under it: while above 0 the two overlap (a double). */
    int[] doubled = new int[0];
    /** Where each car was after the last tick, so a bomb between two ticks is not driven over unseen. */
    private float[] lastX = new float[0];
    private float[] lastZ = new float[0];
    /** Dynamite in flight: the car that threw it (-1: none), where it is, how it moves, how long it has flown. */
    final int[] stickOwner = new int[STICKS];
    final float[] stickX = new float[STICKS];
    final float[] stickY = new float[STICKS];
    final float[] stickZ = new float[STICKS];
    final float[] stickVX = new float[STICKS];
    final float[] stickVY = new float[STICKS];
    final float[] stickVZ = new float[STICKS];
    final int[] stickAge = new int[STICKS];
    /** Bombs dropped: the car that dropped it (-1: none), where it is, how fast it falls, how long it has been out. */
    final int[] bombOwner = new int[BOMBS];
    final float[] bombX = new float[BOMBS];
    final float[] bombY = new float[BOMBS];
    final float[] bombZ = new float[BOMBS];
    final float[] bombVY = new float[BOMBS];
    /** The road it was dropped on (under the wheels of a car on the ground); {@link #FALLS} when dropped in the air. */
    final float[] bombRoad = new float[BOMBS];
    final int[] bombAge = new int[BOMBS];
    /** The ring of events, and how many there have been. */
    final int[] eventKind = new int[EVENTS];
    final int[] eventCar = new int[EVENTS];
    /** The powerup of a pickup; 1 or 2 for nitrous fired alone or doubled; for a blast, 1 if it dented the ground. */
    final int[] eventWhat = new int[EVENTS];
    final float[] eventX = new float[EVENTS];
    final float[] eventY = new float[EVENTS];
    final float[] eventZ = new float[EVENTS];
    /** The ground under a blast or a bang. */
    final float[] eventGround = new float[EVENTS];
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
        doubled = new int[cars];
        lastX = new float[cars];
        lastZ = new float[cars];
        for (int c = 0; c < cars; c++) {
            lastX[c] = race.x(c);
            lastZ[c] = race.z(c);
        }
        Arrays.fill(stickOwner, -1);
        Arrays.fill(stickAge, 0);
        Arrays.fill(bombOwner, -1);
        Arrays.fill(bombAge, 0);
        Arrays.fill(eventKind, 0);
        events = 0;
        /*
         * The empty slots too: they are saved with the rest, and what the
         * race before left in them made a race recorded after another
         * differ at its first tick when played again by itself (the owner's
         * races of 2026-11-27).
         */
        for (float[] slots : new float[][] {stickX, stickY, stickZ, stickVX, stickVY, stickVZ, bombX, bombY, bombZ,
                bombVY, bombRoad, eventX, eventY, eventZ, eventGround}) {
            Arrays.fill(slots, 0F);
        }
        Arrays.fill(eventCar, 0);
        Arrays.fill(eventWhat, 0);
    }

    /** Rows of boxes the first build had on a lap with {@code gates} checkpoints: one at each, every second with many. */
    static int firstBuildRows(int gates) {
        int every = gates > MANY_CHECKPOINTS ? 2 : 1;
        return (gates + every - 1) / every;
    }

    /** Rows of boxes on a lap with {@code gates} checkpoints: a quarter of the first build's, one at least. */
    static int rows(int gates) {
        return gates <= 0 ? 0 : Math.max(1, Math.round(firstBuildRows(gates) / (float) RARER));
    }

    /** A row of boxes across the road at a few of the checkpoints, spread evenly round the lap. */
    private void placeBoxes(RaceTick race) {
        int gates = 0;
        for (int p = 0; p < race.points(); p++) {
            if (race.pointKind(p) > 0) {
                gates++;
            }
        }
        int rows = rows(gates);
        boxes = rows * ROW;
        boxX = new float[boxes];
        boxY = new float[boxes];
        boxZ = new float[boxes];
        int gate = 0;
        int row = 0;
        int b = 0;
        for (int p = 0; p < race.points() && row < rows; p++) {
            int kind = race.pointKind(p);
            if (kind <= 0) {
                continue;
            }
            if (gate++ != (int) ((row + 0.5F) * gates / rows)) {
                continue;
            }
            row++;
            for (int k = 0; k < ROW; k++) {
                float offset = (k - (ROW - 1) / 2F) * SPACING;
                float x = race.pointX(p) + (kind == 1 ? offset : 0F);
                float z = race.pointZ(p) + (kind == 1 ? 0F : offset);
                // the road under the gate: the nearest flat top at or below a little over the checkpoint
                float road = race.ground(x, race.pointY(p) - 200F, z);
                boxX[b] = x;
                boxY[b] = road - HOVER;
                boxZ[b] = z;
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
        s.contents(doubled);
        s.contents(lastX);
        s.contents(lastZ);
        s.contents(stickOwner);
        s.contents(stickX);
        s.contents(stickY);
        s.contents(stickZ);
        s.contents(stickVX);
        s.contents(stickVY);
        s.contents(stickVZ);
        s.contents(stickAge);
        s.contents(bombOwner);
        s.contents(bombX);
        s.contents(bombY);
        s.contents(bombZ);
        s.contents(bombVY);
        s.contents(bombRoad);
        s.contents(bombAge);
        s.contents(eventKind);
        s.contents(eventCar);
        s.contents(eventWhat);
        s.contents(eventX);
        s.contents(eventY);
        s.contents(eventZ);
        s.contents(eventGround);
        events = s.value(events);
    }

    /** What the powerups add to car {@code car}'s boost: nitrous burning (two at once no more than one). */
    float boost(int car) {
        if (car < 0 || car >= cars) {
            return 0F;
        }
        float add = 0F;
        int n = nitrous[car];
        if (n > 0) {
            add += n > NITROUS_FADE ? NITROUS_BOOST : NITROUS_BOOST * n / NITROUS_FADE;
        }
        return add;
    }

    /** What is left of car {@code car}'s grip, 0 to 1: less while its nitrous burns, back to all of it as that fades. */
    float grip(int car) {
        if (car < 0 || car >= cars || nitrous[car] <= 0) {
            return 1F;
        }
        int n = nitrous[car];
        float burning = n > NITROUS_FADE ? 1F : n / (float) NITROUS_FADE;
        return 1F - (1F - NITROUS_GRIP) * burning;
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
                doubled[c] = 0;
                continue;
            }
            if (nitrous[c] > 0) {
                nitrous[c]--;
            }
            if (doubled[c] > 0) {
                doubled[c]--;
            }
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
        for (int b = 0; b < BOMBS; b++) {
            if (bombOwner[b] >= 0) {
                lie(race, b);
            }
        }
        for (int c = 0; c < cars; c++) {
            lastX[c] = race.x(c);
            lastZ[c] = race.z(c);
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
            event(PICKUP, c, held[c], boxX[b], boxY[b], boxZ[b], 0F);
            return;
        }
    }

    /**
     * What a box gives car {@code c}, by its place: the leader, with the
     * field behind it, more often a bomb and seldom dynamite; the last car,
     * with the field ahead, seldom a bomb.
     */
    private int roll(RaceTick race, int c) {
        int place = race.place(c);
        int r = below(100);
        if (place <= 0) {
            return r < 45 ? NITROUS : r < 60 ? DYNAMITE : BOMB;
        }
        if (place >= cars - 1) {
            return r < 45 ? NITROUS : r < 90 ? DYNAMITE : BOMB;
        }
        return r < 40 ? NITROUS : r < 75 ? DYNAMITE : BOMB;
    }

    /** Car {@code c} uses what it holds: a person on a press of the key, a computer car when it suits, dynamite by itself. */
    private void use(RaceTick race, int c, boolean pressed) {
        boolean person = c < humans;
        switch (held[c]) {
            case NITROUS -> {
                if (person ? pressed : wantsNitrous(race, c)) {
                    // fired while the last one still burns: the two overlap for as long as that one had left
                    boolean twice = nitrous[c] > 0;
                    doubled[c] = Math.max(doubled[c], nitrous[c]);
                    nitrous[c] = NITROUS_FULL + NITROUS_FADE;
                    held[c] = NONE;
                    heldTicks[c] = 0;
                    event(USED, c, twice ? 2 : 1, race.x(c), race.y(c), race.z(c), 0F);
                }
            }
            case BOMB -> {
                if (person ? pressed : wantsToDrop(race, c)) {
                    drop(race, c);
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

    /** A computer car fires nitrous on the ground, flat out, going straight and already quick. */
    private boolean wantsNitrous(RaceTick race, int c) {
        return race.grounded(c) && race.throttle(c) && !race.steering(c)
                && race.speed(c) > AI_NITROUS_SPEED * race.topSpeed(c) && chance(AI_NITROUS);
    }

    /** A computer car drops its bomb in front of a car close behind it, or anyway once it has carried it long. */
    private boolean wantsToDrop(RaceTick race, int c) {
        if (!race.grounded(c)) {
            return false;
        }
        double yaw = StrictMath.toRadians(race.yaw(c));
        float fx = (float) -StrictMath.sin(yaw);
        float fz = (float) StrictMath.cos(yaw);
        for (int o = 0; o < cars; o++) {
            if (o == c || race.wasted(o)) {
                continue;
            }
            float dx = race.x(o) - race.x(c);
            float dz = race.z(o) - race.z(c);
            float d = distance(dx, dz);
            if (d > AI_BOMB_NEAR && d < AI_BOMB_FAR && -(dx * fx + dz * fz) > AI_BOMB_IN_LINE * d) {
                return chance(AI_BOMB);
            }
        }
        return heldTicks[c] > AI_BOMB_KEPT && chance(AI_BOMB_ANYWAY);
    }

    /* ---------------------------------------------------------- the bomb */

    /** Car {@code c} drops its bomb behind its tail; it falls to the road and lies there. */
    private void drop(RaceTick race, int c) {
        held[c] = NONE;
        heldTicks[c] = 0;
        int b = 0;
        while (b < BOMBS && bombOwner[b] >= 0) {
            b++;
        }
        if (b == BOMBS) {
            b = 0; // every place taken: the one out the longest makes way
            for (int k = 1; k < BOMBS; k++) {
                if (bombAge[k] > bombAge[b]) {
                    b = k;
                }
            }
        }
        double yaw = StrictMath.toRadians(race.yaw(c));
        float fx = (float) -StrictMath.sin(yaw);
        float fz = (float) StrictMath.cos(yaw);
        float behind = race.radius(c) + BOMB_BEHIND;
        bombOwner[b] = c;
        bombAge[b] = 0;
        bombX[b] = race.x(c) - fx * behind;
        bombZ[b] = race.z(c) - fz * behind;
        if (race.grounded(c)) {
            // on the road the car is on, a slope or a ramp too
            bombRoad[b] = race.y(c) + race.wheelDrop(c);
            bombY[b] = bombRoad[b];
            bombVY[b] = 0F;
        } else {
            bombRoad[b] = FALLS;
            bombY[b] = race.y(c);
            bombVY[b] = Math.max(0F, race.vy(c));
        }
        event(DROPPED, c, BOMB, bombX[b], bombY[b], bombZ[b], 0F);
    }

    /** Bomb {@code b} a tick on: it falls until it lies on the road, and the first car that drives onto it sets it off. */
    private void lie(RaceTick race, int b) {
        bombAge[b]++;
        float ground = bombRoad[b] != FALLS ? bombRoad[b] : race.ground(bombX[b], bombY[b] - 10F, bombZ[b]);
        if (bombY[b] < ground) {
            bombVY[b] += GRAVITY;
            bombY[b] = Math.min(ground, bombY[b] + bombVY[b]);
        } else {
            bombY[b] = ground;
            bombVY[b] = 0F;
        }
        for (int c = 0; c < cars; c++) {
            if (race.wasted(c) || c == bombOwner[b] && bombAge[b] < BOMB_OWN_AFTER
                    || Math.abs(race.y(c) - bombY[b]) > BOMB_UP) {
                continue;
            }
            float reach = BOMB_REACH + BOMB_REACH_PER_RADIUS * race.radius(c);
            if (passed(lastX[c], lastZ[c], race.x(c), race.z(c), bombX[b], bombZ[b]) < reach * reach) {
                bang(race, b, c, ground);
                return;
            }
        }
    }

    /** How near (squared) the way from {@code (ax, az)} to {@code (bx, bz)} comes to {@code (px, pz)}. */
    private static float passed(float ax, float az, float bx, float bz, float px, float pz) {
        float wx = bx - ax;
        float wz = bz - az;
        float len2 = wx * wx + wz * wz;
        float t = len2 > 0F ? ((px - ax) * wx + (pz - az) * wz) / len2 : 0F;
        t = Math.max(0F, Math.min(1F, t));
        float dx = ax + wx * t - px;
        float dz = az + wz * t - pz;
        return dx * dx + dz * dz;
    }

    /** Bomb {@code b} goes off under car {@code c}: that car takes all of it, cars close by their share. */
    private void bang(RaceTick race, int b, int c, float ground) {
        bombOwner[b] = -1;
        BlastPush.throwCars(race, bombX[b], bombY[b], bombZ[b], BANG_RADIUS, BANG_PUSH, BANG_LIFT, BANG_TIP,
                BANG_DAMAGE, c, -1);
        event(BANG, c, BOMB, bombX[b], bombY[b], bombZ[b], ground);
    }

    /* ------------------------------------------------------- the dynamite */

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
        double yaw = StrictMath.toRadians(race.yaw(c));
        float fx = (float) -StrictMath.sin(yaw);
        float fz = (float) StrictMath.cos(yaw);
        float nose = race.radius(c) * 0.8F;
        stickOwner[s] = c;
        stickAge[s] = 0;
        stickX[s] = race.x(c) + fx * nose;
        stickY[s] = race.y(c) - 30F;
        stickZ[s] = race.z(c) + fz * nose;
        float vx = race.vx(c) + fx * THROW_FORWARD;
        float vz = race.vz(c) + fz * THROW_FORWARD;
        int enemy = nearestAhead(race, c, fx, fz);
        if (enemy >= 0) {
            // the way it leaves, turned toward the enemy by a couple of degrees at most (x = sin, z = cos of a heading)
            double leaves = StrictMath.atan2(vx, vz);
            double wanted = StrictMath.atan2(race.x(enemy) - stickX[s], race.z(enemy) - stickZ[s]);
            double turn = wanted - leaves;
            if (turn > StrictMath.PI) {
                turn -= 2D * StrictMath.PI;
            } else if (turn < -StrictMath.PI) {
                turn += 2D * StrictMath.PI;
            }
            double most = StrictMath.toRadians(AIM_MOST);
            turn = Math.max(-most, Math.min(most, turn));
            float speed = distance(vx, vz);
            vx = (float) (StrictMath.sin(leaves + turn) * speed);
            vz = (float) (StrictMath.cos(leaves + turn) * speed);
        }
        stickVX[s] = vx;
        stickVY[s] = race.vy(c) - THROW_UP;
        stickVZ[s] = vz;
        event(THROWN, c, DYNAMITE, stickX[s], stickY[s], stickZ[s], 0F);
    }

    /** The nearest car ahead of car {@code c}'s nose that still races, within the aim's range; -1 if none. */
    private int nearestAhead(RaceTick race, int c, float fx, float fz) {
        int nearest = -1;
        float least = AIM_RANGE;
        for (int o = 0; o < cars; o++) {
            if (o == c || race.wasted(o)) {
                continue;
            }
            float dx = race.x(o) - race.x(c);
            float dz = race.z(o) - race.z(c);
            float d = distance(dx, dz);
            if (d < least && dx * fx + dz * fz > 0F) {
                least = d;
                nearest = o;
            }
        }
        return nearest;
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
        BlastPush.stick(race, x, y, z);
        boolean dents = ground - y < BlastPush.CRATER_HEIGHT;
        event(BLAST, owner, dents ? 1 : 0, x, dents ? ground : y, z, ground);
    }

    private void event(int kind, int car, int what, float x, float y, float z, float ground) {
        int at = events % EVENTS;
        eventKind[at] = kind;
        eventCar[at] = car;
        eventWhat[at] = what;
        eventX[at] = x;
        eventY[at] = y;
        eventZ[at] = z;
        eventGround[at] = ground;
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
