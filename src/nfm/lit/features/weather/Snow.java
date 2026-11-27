package nfm.lit.features.weather;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Features;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceView;
import nfm.lit.features.Weather;

/**
 * What the cars do to the snow and the snow to the cars (weather, "Snow is
 * 3D and collects", "Snow trails", "Snow collects on the cars", "Explosions
 * and fire clear snow"). Looks and sound only: it reads the race's copy.
 * <ul>
 * <li>Trails: every wheel on the ground presses a track into the snow, a
 * strip of flat pieces on the ground that the snowfall fills in again.
 * <li>Heaps: low mounds of snow, real shapes with sides and a top
 * ({@link #mound}). A sliding car throws snow to the outside of its slide,
 * where it piles up; a car sliding on its side or roof pushes a growing
 * heap along and leaves it where it stops. A wheel through a heap flattens
 * it.
 * <li>On the cars: snow packs into the wheel arches while a car drives,
 * and into the open backs of DR Monstaa and M A S H E E N while it snows.
 * A hard landing knocks it out, in lumps that stay on the ground; a ram
 * shakes some off both cars.
 * <li>Bare ground: an explosion clears the snow around it, and the snow
 * melts back around a burning (wasted) car.
 * </ul>
 */
final class Snow {

    /* ------------------------------------------------------------ trails */

    static final int RUTS = 8192;
    /** Ticks of snowfall that fill a trail in again. */
    static final int RUT_FILLS = 19 * 60;
    /** A wheel must have moved this far for a new piece of its trail: two ticks' way, between these, units. */
    static final float RUT_STEP_LEAST = 30F;
    static final float RUT_STEP_MOST = 160F;
    /** Only the cars within this of a player leave trails (their rear wheels'; a player's car all four). */
    static final float RUTS_WITHIN = 9000F;
    /** What a trail lays thinly over the snow: packed, darker. */
    static final int RUT_RGB = 0x76808c;
    static final float RUT_COVER = 0.42F;
    /** Farther than this from the eye trails and bare ground are not drawn. */
    static final float SEEN = 7000F;
    /**
     * Nearer than this a trail is drawn as what it is, a cut into the bed:
     * its packed floor, and a wall along each side, the one turned to the
     * light bright and the other in shade. Farther off it is a flat strip.
     */
    static final float RELIEF = 2600F;
    static final int FLOOR = 0xb4c0d0;
    /** What shows through a trail where the race tells no colour of the ground. */
    static final int UNDER = 0x6e7682;
    static final int WALL = 0x7f8da6;
    /** The light comes from this way across the ground (as the heaps are lit). */
    static final float LIGHT_X = 0.6F;
    static final float LIGHT_Z = -0.8F;
    /** A car's body cuts its own channel once the bed is deeper than the car stands high; of the body's width. */
    static final float CHANNEL_WIDE = 0.94F;
    /** Faster than this a car's rear wheels throw a plume of powder, units a tick, and the speed at which it is fullest. */
    static final float PLUME_FROM = 30F;
    static final float PLUME_FULL = 130F;

    /* ------------------------------------------------------------- heaps */

    static final int HEAPS = 160;
    /** A heap's widest and highest, units. */
    static final float HEAP_WIDEST = 62F;
    static final float HEAP_HIGHEST = 24F;
    /** A wheel sliding sideways faster than this throws snow aside, units a tick. */
    static final float DRIFT_FROM = 4F;
    /** How far outside the car a drift's heap lands, beyond half the car's width. */
    static final float DRIFT_ASIDE = 55F;
    /** A heap this near another grows it instead. */
    static final float HEAP_JOINS = 110F;
    /** A heap younger than this many ticks is not flattened by the wheels that made it. */
    static final int HEAP_SETTLES = 14;
    static final int SIDES = 6;
    static final int LIT = 0xf7f9fc;
    static final int SHADE = 0xc3cedd;

    /* ---------------------------------------------------------- the cars */

    /** Ticks of driving that pack the wheel arches full, and of snowfall that fill an open back. */
    static final int ARCH_FILLS = 19 * 30;
    static final int TRUNK_FILLS = 19 * 45;
    /** How full an open back is when a race starts in snow. */
    static final float TRUNK_START = 0.4F;
    /** A landing after this many ticks in the air is a hard one. */
    static final int HARD_LANDING = 12;
    /** A ram pushing this hard shakes snow off both cars, units a tick. */
    static final float RAM_FROM = 3F;
    /** The models with an open back that collects snow. */
    static final String[] OPEN_BACKS = {"drmonster", "masheen"};
    /** Tilted further than this from upright a car lies on its side or roof: the cosine. */
    static final float LYING = 0.35F;

    /* ------------------------------------------------------- bare ground */

    static final int CLEARS = 32;
    /** How far around a dynamite stick's blast the snow is gone, and around a burning car once it has melted back. */
    static final float BLAST_CLEARS = 300F;
    static final float FIRE_MELTS = 190F;
    /** Ticks a fire takes to melt that far. */
    static final int MELTS_IN = 19 * 12;
    static final int BARE = 0x4c5056;
    static final float BARE_COVER = 0.5F;
    private static final int ROUND = 10;
    private static final float GROUND = 250F;

    /* trails: a ring of four-cornered pieces */
    private final float[] rutX = new float[RUTS * 4];
    private final float[] rutZ = new float[RUTS * 4];
    private final float[] rutY = new float[RUTS];
    private final int[] rutTick = new int[RUTS];
    private final boolean[] rutLive = new boolean[RUTS];
    /** How deep each piece is cut into the bed, units, and whether a body cut it (a channel) or a wheel. */
    private final float[] rutCut = new float[RUTS];
    /** What lies under the bed there (the road's colour, the earth's): a wheel's trail through thin snow shows it. */
    private final int[] rutUnder = new int[RUTS];
    private int underNow = UNDER;
    /** The bed's own colour as it lies on the ground now (the stage's ground under snow). */
    int groundRgb = LIT;
    private final boolean[] rutWide = new boolean[RUTS];
    /** Pieces of channel among {@link #ruts}. */
    int channels;
    private int rutNext;
    int ruts;
    /* per wheel: the trail it is drawing */
    private boolean[] rutOpen = new boolean[0];
    private float[] lastX = new float[0];
    private float[] lastZ = new float[0];
    private float[] edgeLX = new float[0];
    private float[] edgeLZ = new float[0];
    private float[] edgeRX = new float[0];
    private float[] edgeRZ = new float[0];

    /* heaps */
    private final float[] heapX = new float[HEAPS];
    private final float[] heapY = new float[HEAPS];
    private final float[] heapZ = new float[HEAPS];
    private final float[] heapR = new float[HEAPS];
    private final float[] heapH = new float[HEAPS];
    private final int[] heapTick = new int[HEAPS];
    private final boolean[] heapLive = new boolean[HEAPS];
    private int heapNext;
    int heaps;

    /* per car */
    private int cars;
    private float[] drift = new float[0];
    /** How much snow a car on its side or roof has pushed up ahead of it, 0 to 1. */
    float[] pushed = new float[0];
    float[] arch = new float[0];
    /** The snow in the car's open back, 0 to 1; -1 for a car with none. */
    float[] trunk = new float[0];
    private float[] trunkX = new float[0];
    /** The polygon of the car that is the floor of its open back. */
    private int[] trunkPlane = new int[0];
    private float[] trunkY = new float[0];
    private float[] trunkZ = new float[0];
    private float[] trunkWide = new float[0];
    private float[] trunkLong = new float[0];
    /** How high the walls around the open back stand over its floor (0 for a flat deck). */
    private float[] trunkWall = new float[0];
    private int[] lastAir = new int[0];
    /** Ticks since the car was wasted (it burns), or -1. */
    private int[] burning = new int[0];
    /* each car's place and axes after the last tick, and its velocity */
    private float[] pose = new float[0];
    private float[] carV = new float[0];
    private float[] halfWide = new float[0];
    private float[] tyre = new float[0];

    /* bare ground around blasts */
    private final float[] clearX = new float[CLEARS];
    private final float[] clearY = new float[CLEARS];
    private final float[] clearZ = new float[CLEARS];
    private final float[] clearR = new float[CLEARS];
    private final int[] clearTick = new int[CLEARS];
    private int clears;
    private int seenBlasts;

    final Particles puffs = new Particles(800);
    /** The bed itself: the cars' shapes pressed into it, its drifts, the snow about the wheels, what blows over it. */
    final SnowBed bed = new SnowBed(this);
    private float windX;
    private float windZ;
    private Gusts gusts;
    /** How high each car's floor stands over the ground. */
    private float[] clearance = new float[0];
    /** Since the race started: puffs of powder thrown behind wheels at speed. */
    int plumes;

    /** The wind the next race's snow blows in, and its gusts. */
    void wind(float x, float z, Gusts blowing) {
        windX = x;
        windZ = z;
        gusts = blowing;
    }

    private final Dice dice = new Dice(0L, "weather snow");
    private boolean on;
    private int tick;
    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private final float[] p = new float[3];
    private final float[] q = new float[3];
    private final float[] cx = new float[ROUND];
    private final float[] cy = new float[ROUND];
    private final float[] cz = new float[ROUND];

    /** For the checks: since the race started, puffs thrown, lumps knocked out of open backs, sounds asked for. */
    int thrown;
    int lumps;
    int heard;
    /** Drawn in the last frame: trail pieces, heaps, bare patches, polygons on cars. */
    int drawnRuts;
    int drawnHeaps;
    int drawnBare;
    /** Sides and tops drawn since the last frame was queued: of the heaps lying, and of those cars push along. */
    int drawnSides;
    int drawnPushed;

    /** A race starts in snow (or snow joins one). */
    void start(RaceView r) {
        on = true;
        tick = r.tick;
        dice.reset(r.seed, "weather snow");
        puffs.clear();
        java.util.Arrays.fill(rutLive, false);
        java.util.Arrays.fill(heapLive, false);
        rutNext = 0;
        ruts = 0;
        heapNext = 0;
        heaps = 0;
        clears = 0;
        seenBlasts = Weather.blasts();
        thrown = 0;
        lumps = 0;
        heard = 0;
        plumes = 0;
        channels = 0;
        cars = r.cars;
        shapes = r.shape;
        humans = Math.max(1, Math.min(r.humans, r.cars));
        if (drift.length < cars) {
            drift = new float[cars];
            pushed = new float[cars];
            arch = new float[cars];
            trunk = new float[cars];
            trunkX = new float[cars];
            trunkPlane = new int[cars];
            trunkY = new float[cars];
            trunkZ = new float[cars];
            trunkWide = new float[cars];
            trunkLong = new float[cars];
            trunkWall = new float[cars];
            lastAir = new int[cars];
            burning = new int[cars];
            pose = new float[cars * 12];
            carV = new float[cars * 3];
            halfWide = new float[cars];
            tyre = new float[cars];
            clearance = new float[cars];
            // a trail for each wheel, and after those one for each body
            int wheels = cars * (RaceView.WHEELS + 1);
            rutOpen = new boolean[wheels];
            lastX = new float[wheels];
            lastZ = new float[wheels];
            edgeLX = new float[wheels];
            edgeLZ = new float[wheels];
            edgeRX = new float[wheels];
            edgeRZ = new float[wheels];
        }
        java.util.Arrays.fill(rutOpen, false);
        for (int c = 0; c < cars; c++) {
            drift[c] = 0F;
            pushed[c] = 0F;
            arch[c] = 0F;
            lastAir[c] = 0;
            burning[c] = -1;
            CarShape s = r.shape[c];
            halfWide[c] = s != null && s.maxX > s.minX ? (s.maxX - s.minX) / 2F : 40F;
            tyre[c] = s != null && s.wheelHalfWidth[2] > 0F ? Math.max(8F, s.wheelHalfWidth[2] * 2F) : 14F;
            trunk[c] = openBack(r.modelName[c]) && findBack(c, s) ? TRUNK_START : -1F;
            clearance[c] = s != null && s.wheelRadius[2] > 0F ? Math.max(4F, s.hubY[2] + s.wheelRadius[2] - s.maxY)
                    : 16F;
        }
        if (gusts == null) {
            gusts = new Gusts();
            gusts.start(r.seed);
        }
        bed.start(r, windX, windZ, gusts);
        place(r);
        if (Boolean.getBoolean("nfm.weather.show")) {
            show(r);
        }
    }

    /**
     * For a hidden test run's screenshots ({@code -Dnfm.weather.show=true}):
     * heaps of every size beside the first car, every arch packed and every
     * open back full, so one frame at the start shows them all.
     */
    private void show(RaceView r) {
        for (int k = 0; k < 6; k++) {
            float size = (k + 1) / 6F;
            heap(r.x[0] + (k % 2 == 0 ? -1F : 1F) * (170F + 45F * k), GROUND, r.z[0] + 120F + 150F * k,
                    20F + 42F * size, 7F + 17F * size);
        }
        for (int c = 0; c < cars; c++) {
            arch[c] = 1F;
            if (trunk[c] >= 0F) {
                trunk[c] = 1F;
            }
        }
    }

    /** No race in snow. */
    void stop() {
        on = false;
        cars = 0;
        ruts = 0;
        heaps = 0;
        clears = 0;
        puffs.clear();
        bed.stop();
    }

    private static boolean openBack(String model) {
        for (String name : OPEN_BACKS) {
            if (name.equals(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The open back of car {@code c}: the biggest level panel that faces
     * up behind all of the car's glass and below its roof. False if the
     * model has none.
     */
    private boolean findBack(int c, CarShape s) {
        if (s == null) {
            return false;
        }
        float glassBack = Float.MAX_VALUE;
        for (int i = 0; i < s.planes; i++) {
            for (int k = s.first[i]; s.glass[i] && k < s.first[i] + s.count[i]; k++) {
                glassBack = Math.min(glassBack, s.z[k]);
            }
        }
        if (glassBack == Float.MAX_VALUE) {
            return false;
        }
        float high = s.maxY - s.minY;
        float best = 0F;
        for (int i = 0; i < s.planes; i++) {
            if (s.glass[i] || s.wheel[i] >= 0 || s.count[i] < 3) {
                continue;
            }
            float x0 = Float.MAX_VALUE;
            float x1 = -Float.MAX_VALUE;
            float y0 = Float.MAX_VALUE;
            float y1 = -Float.MAX_VALUE;
            float z0 = Float.MAX_VALUE;
            float z1 = -Float.MAX_VALUE;
            for (int k = s.first[i]; k < s.first[i] + s.count[i]; k++) {
                x0 = Math.min(x0, s.x[k]);
                x1 = Math.max(x1, s.x[k]);
                y0 = Math.min(y0, s.y[k]);
                y1 = Math.max(y1, s.y[k]);
                z0 = Math.min(z0, s.z[k]);
                z1 = Math.max(z1, s.z[k]);
            }
            // level, behind the glass, below the roof and above the floor pan, across the car's middle
            boolean level = y1 - y0 <= 0.04F * high + 1F;
            float area = (x1 - x0) * (z1 - z0);
            if (level && z1 <= glassBack + 2F && y0 < s.minY + 0.85F * high && y0 > s.minY + 0.15F * high && x0 < 0F
                    && x1 > 0F && area > best) {
                best = area;
                trunkPlane[c] = i;
                trunkX[c] = (x0 + x1) / 2F;
                trunkY[c] = y0;
                trunkZ[c] = (z0 + z1) / 2F;
                trunkWide[c] = (x1 - x0) / 2F;
                trunkLong[c] = (z1 - z0) / 2F;
            }
        }
        if (best <= 0F) {
            return false;
        }
        // the walls around it (a pickup's sides and tailgate): the highest of the body over its edges, not the cab
        float top = trunkY[c];
        for (int i = 0; i < s.planes; i++) {
            for (int k = s.first[i]; !s.glass[i] && s.wheel[i] < 0 && k < s.first[i] + s.count[i]; k++) {
                if (Math.abs(s.x[k] - trunkX[c]) <= trunkWide[c] * 1.7F
                        && Math.abs(s.z[k] - trunkZ[c]) <= trunkLong[c] * 1.3F && s.y[k] < top
                        && s.y[k] > trunkY[c] - 0.35F * high) {
                    top = s.y[k];
                }
            }
        }
        trunkWall[c] = trunkY[c] - top;
        return true;
    }

    /** Each car's place, axes and velocity after the tick. */
    private void place(RaceView r) {
        for (int c = 0; c < cars; c++) {
            int at = c * 12;
            r.toWorld(c, 0F, 0F, 0F, p);
            pose[at] = p[0];
            pose[at + 1] = p[1];
            pose[at + 2] = p[2];
            for (int axis = 0; axis < 3; axis++) {
                r.toWorld(c, axis == 0 ? 1F : 0F, axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F, q);
                pose[at + 3 + axis * 3] = q[0] - p[0];
                pose[at + 4 + axis * 3] = q[1] - p[1];
                pose[at + 5 + axis * 3] = q[2] - p[2];
            }
            carV[c * 3] = r.vx[c];
            carV[c * 3 + 1] = r.vy[c];
            carV[c * 3 + 2] = r.vz[c];
        }
    }

    /** How deep the snow lies, 0 to 1: more of it is thrown and piled the deeper it is. */
    private float depth = 1F;

    /* -------------------------------------------------------------- tick */

    /** One tick of a race in snow lying {@code deep} (0 to 1). */
    void tick(RaceView r, float deep) {
        if (!on || r.cars != cars) {
            start(r);
        }
        tick = r.tick;
        depth = deep;
        blasts();
        for (int c = 0; c < cars; c++) {
            car(r, c);
        }
        rams(r);
        bed.tick(r, SnowBed.deep(deep));
        place(r);
        // the powder drifts off with the wind, farther in a gust
        float blow = 2F + 8F * gusts.at(tick);
        puffs.tick(windX * blow, windZ * blow);
    }

    private void car(RaceView r, int c) {
        float speed = r.groundSpeed(c);
        boolean landed = r.grounded[c] && lastAir[c] > 0;
        boolean hard = landed && lastAir[c] >= HARD_LANDING;
        float sliding = 0F;
        int touching = 0;
        boolean trails = c < humans || nearPlayer(r, c);
        float bedDeep = SnowBed.deep(depth);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (!r.contact[at] || r.wasted[c]) {
                rutOpen[at] = false;
                continue;
            }
            touching++;
            float x = r.wheelX[at];
            float y = r.wheelY[at];
            float z = r.wheelZ[at];
            underNow = r.groundRgb[at] != 0 ? r.groundRgb[at] : UNDER;
            if (r.onRamp[at] || !trails || w < 2 && c >= humans) {
                rutOpen[at] = false;
            } else {
                rut(at, x, y, z, tyre[c], Math.max(RUT_STEP_LEAST, Math.min(RUT_STEP_MOST, speed * 2F)), bedDeep,
                        false);
            }
            // powder behind the rear wheels at speed: a plume that hangs and blows off with the wind
            float plume = Math.max(0F, Math.min(1F, (speed - PLUME_FROM) / (PLUME_FULL - PLUME_FROM)));
            // (thin and low: the chase camera looks through it)
            if (w >= 2 && plume > 0F && trails && dice.next() < 0.15F + 0.45F * plume) {
                powder(x, y, z, r.vx[c] * 0.4F + dice.between(-2F, 2F), -(2F + 4F * plume),
                        r.vz[c] * 0.4F + dice.between(-2F, 2F), 10F, 26F + 26F * plume, 0.1F + 0.1F * plume);
                plumes++;
            }
            if (landed) {
                // a landing throws the snow up around each wheel
                for (int k = 0; k < (hard ? 3 : 1); k++) {
                    puff(x, y, z, r.vx[c] * 0.3F + dice.between(-4F, 4F), -dice.between(2F, 6F),
                            r.vz[c] * 0.3F + dice.between(-4F, 4F), 14F, 34F);
                }
            }
            float slip = Math.abs(r.slipSide[at]);
            if (slip >= DRIFT_FROM && speed > 4F) {
                sliding += slip;
                if (dice.next() < 0.5F) {
                    puff(x, y, z, r.vx[c] * 0.4F + dice.between(-2F, 2F), -dice.between(2F, 5F),
                            r.vz[c] * 0.4F + dice.between(-2F, 2F), 8F, 22F);
                }
            }
        }
        if (touching > 0 && speed > 4F) {
            arch[c] = Math.min(1F, arch[c] + 1F / ARCH_FILLS);
        }
        // the body's own channel, where the bed is deeper than the car stands high
        int body = cars * RaceView.WHEELS + c;
        float cut = bedDeep - clearance[c];
        if (touching >= 2 && trails && cut > 1F && !onRamp(r, c)) {
            rut(body, r.x[c], groundUnder(r, c), r.z[c], halfWide[c] * 2F * CHANNEL_WIDE,
                    Math.max(RUT_STEP_LEAST * 2F, Math.min(RUT_STEP_MOST, speed * 2F)), cut, true);
            if (speed > 10F && dice.next() < 0.1F + 0.012F * cut) {
                // ploughing: now and then a lump breaks off the wave at its nose
                double yaw = Math.toRadians(r.xz[c]);
                float side = dice.between(-1F, 1F) * halfWide[c];
                float nx = r.x[c] + r.vx[c] * 2.5F + (float) Math.cos(yaw) * side;
                float nz = r.z[c] + r.vz[c] * 2.5F + (float) Math.sin(yaw) * side;
                Weather.lump(nx, groundUnder(r, c) - 6F, nz, r.vx[c] * 0.8F + dice.between(-4F, 4F),
                        -dice.between(3F, 8F), r.vz[c] * 0.8F + dice.between(-4F, 4F), dice.between(7F, 12F));
                puff(nx, groundUnder(r, c), nz, r.vx[c] * 0.6F + dice.between(-3F, 3F), -dice.between(2F, 5F),
                        r.vz[c] * 0.6F + dice.between(-3F, 3F), 10F, 26F);
            }
        } else {
            rutOpen[body] = false;
        }
        if (trunk[c] >= 0F) {
            trunk[c] = Math.min(1F, trunk[c] + 1F / TRUNK_FILLS);
        }
        if (sliding > 0F) {
            drifting(r, c, sliding);
        }
        lying(r, c, speed);
        if (hard) {
            arch[c] *= 0.3F;
            if (trunk[c] > 0.1F) {
                knockedOut(r, c);
            }
        }
        if (touching > 0 && speed > 6F) {
            flatten(r, c);
        }
        burning[c] = r.wasted[c] ? burning[c] + 1 : -1;
        if (burning[c] >= 0) {
            melt(r.x[c], r.z[c], melted(burning[c]));
        }
        lastAir[c] = r.airTicks[c];
    }

    private static boolean onRamp(RaceView r, int c) {
        for (int w = 0; w < RaceView.WHEELS; w++) {
            if (r.onRamp[c * RaceView.WHEELS + w]) {
                return true;
            }
        }
        return false;
    }

    /** How high car {@code c}'s floor stands over the ground. */
    float clearance(int c) {
        return c >= 0 && c < clearance.length ? clearance[c] : 16F;
    }

    /** True if car {@code c} is near enough to a player for its marks to matter. */
    boolean marks(RaceView r, int c) {
        return c < humans || nearPlayer(r, c);
    }

    private boolean nearPlayer(RaceView r, int c) {
        for (int h = 0; h < humans; h++) {
            float dx = r.x[c] - r.x[h];
            float dz = r.z[c] - r.z[h];
            if (dx * dx + dz * dz < RUTS_WITHIN * RUTS_WITHIN) {
                return true;
            }
        }
        return false;
    }

    /** How far around a car that has burned {@code ticks} ticks the snow has melted. */
    static float melted(int ticks) {
        return FIRE_MELTS * Math.max(0F, Math.min(1F, ticks / (float) MELTS_IN));
    }

    /** Trail {@code at} (a wheel's, or a body's channel if {@code body}) goes on to {@code (x, y, z)}, cut {@code cut} deep. */
    private void rut(int at, float x, float y, float z, float wide, float step, float cut, boolean body) {
        if (!rutOpen[at]) {
            rutOpen[at] = true;
            lastX[at] = x;
            lastZ[at] = z;
            edgeLX[at] = Float.NaN;
            return;
        }
        float dx = x - lastX[at];
        float dz = z - lastZ[at];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < step) {
            return;
        }
        if (len > 600F) {
            // set down somewhere else (a reset): a new trail
            lastX[at] = x;
            lastZ[at] = z;
            edgeLX[at] = Float.NaN;
            return;
        }
        float half = wide / 2F;
        float ox = -dz / len * half;
        float oz = dx / len * half;
        int i = rutNext;
        rutNext = (rutNext + 1) % RUTS;
        if (!rutLive[i]) {
            ruts++;
        } else if (rutWide[i]) {
            channels--;
        }
        rutLive[i] = true;
        rutCut[i] = cut;
        rutWide[i] = body;
        rutUnder[i] = underNow;
        if (body) {
            channels++;
        }
        boolean joined = !Float.isNaN(edgeLX[at]);
        rutX[i * 4] = joined ? edgeLX[at] : lastX[at] + ox;
        rutZ[i * 4] = joined ? edgeLZ[at] : lastZ[at] + oz;
        rutX[i * 4 + 1] = joined ? edgeRX[at] : lastX[at] - ox;
        rutZ[i * 4 + 1] = joined ? edgeRZ[at] : lastZ[at] - oz;
        rutX[i * 4 + 2] = x - ox;
        rutZ[i * 4 + 2] = z - oz;
        rutX[i * 4 + 3] = x + ox;
        rutZ[i * 4 + 3] = z + oz;
        rutY[i] = y;
        rutTick[i] = tick;
        edgeLX[at] = x + ox;
        edgeLZ[at] = z + oz;
        edgeRX[at] = x - ox;
        edgeRZ[at] = z - oz;
        lastX[at] = x;
        lastZ[at] = z;
    }

    /** Car {@code c}'s wheels slide sideways, {@code sliding} in all: the snow they plough goes to the outside of the slide. */
    private void drifting(RaceView r, int c, float sliding) {
        drift[c] += sliding * 0.012F * (0.5F + 0.5F * depth);
        if (drift[c] < 1F) {
            return;
        }
        drift[c] -= 1F;
        // sideways to the car's heading, the way it slides
        double yaw = Math.toRadians(r.xz[c]);
        float hx = (float) -Math.sin(yaw);
        float hz = (float) Math.cos(yaw);
        float along = r.vx[c] * hx + r.vz[c] * hz;
        float sx = r.vx[c] - along * hx;
        float sz = r.vz[c] - along * hz;
        float side = (float) Math.sqrt(sx * sx + sz * sz);
        if (side < 0.5F) {
            // sliding on the spot: to the car's right or left, as the wheels slip
            sx = hz;
            sz = -hx;
            side = 1F;
        }
        float out = halfWide[c] + DRIFT_ASIDE + dice.between(0F, 25F);
        heap(r.x[c] + sx / side * out + hx * dice.between(-30F, 30F), groundUnder(r, c),
                r.z[c] + sz / side * out + hz * dice.between(-30F, 30F), 26F, 9F);
    }

    /** A car on its side or roof, sliding: it pushes snow ahead of it and leaves it where it stops. */
    private void lying(RaceView r, int c, float speed) {
        double roll = Math.toRadians(r.xy[c]);
        double pitch = Math.toRadians(r.zy[c]);
        boolean down = (r.capsized[c] || Math.cos(roll) * Math.cos(pitch) < LYING) && r.y[c] > GROUND - 100F
                && Math.abs(r.vy[c]) < 9F;
        if (down && speed > 3F) {
            pushed[c] = Math.min(1F, pushed[c] + speed * 0.0016F * (0.5F + 0.5F * depth));
            if (dice.next() < 0.6F) {
                float lead = halfWide[c] * 0.8F / Math.max(1F, speed);
                puff(r.x[c] + r.vx[c] * lead, GROUND, r.z[c] + r.vz[c] * lead, r.vx[c] * 0.5F + dice.between(-3F, 3F),
                        -dice.between(2F, 5F), r.vz[c] * 0.5F + dice.between(-3F, 3F), 10F, 30F);
            }
        } else if (pushed[c] > 0F) {
            if (pushed[c] > 0.12F) {
                // where it stopped (or got back on its wheels): the snow it pushed stays
                float lead = halfWide[c] * 0.9F;
                float vx = carV[c * 3];
                float vz = carV[c * 3 + 2];
                float v = (float) Math.sqrt(vx * vx + vz * vz);
                float ax = v > 0.5F ? vx / v : 0F;
                float az = v > 0.5F ? vz / v : 1F;
                heap(r.x[c] + ax * lead, GROUND, r.z[c] + az * lead, 26F + 36F * pushed[c], 9F + 15F * pushed[c]);
                sound(r.x[c], r.y[c], r.z[c], 0.3F + 0.4F * pushed[c]);
            }
            pushed[c] = 0F;
        }
    }

    /** A hard landing: the snow in car {@code c}'s open back is thrown out, in lumps that stay where they land. */
    private void knockedOut(RaceView r, int c) {
        float load = trunk[c];
        trunk[c] = 0F;
        r.toWorld(c, trunkX[c], trunkY[c], trunkZ[c], p);
        int n = 2 + (int) (load * 2.5F);
        for (int k = 0; k < n; k++) {
            float ox = dice.between(-90F, 90F);
            float oz = dice.between(-90F, 90F);
            heap(p[0] + ox - r.vx[c] * 2F, groundUnder(r, c), p[2] + oz - r.vz[c] * 2F, 14F + 12F * load,
                    5F + 6F * load);
            lumps++;
        }
        for (int k = 0; k < 10; k++) {
            puff(p[0], p[1], p[2], r.vx[c] * 0.5F + dice.between(-6F, 6F), -dice.between(3F, 9F),
                    r.vz[c] * 0.5F + dice.between(-6F, 6F), 12F, 36F);
        }
        sound(p[0], p[1], p[2], 0.5F + 0.4F * load);
    }

    /** Each ram shakes snow off both cars. */
    private void rams(RaceView r) {
        for (int k = 0; k < r.rams; k++) {
            if (Math.abs(r.ramPush[k]) < RAM_FROM) {
                continue;
            }
            shaken(r, r.ramCar[k]);
            shaken(r, r.ramOther[k]);
        }
    }

    private void shaken(RaceView r, int c) {
        if (c < 0 || c >= cars) {
            return;
        }
        float had = arch[c] + Math.max(0F, trunk[c]);
        arch[c] *= 0.4F;
        if (trunk[c] > 0F) {
            trunk[c] *= 0.6F;
        }
        int n = 3 + (int) (had * 4F);
        for (int k = 0; k < n; k++) {
            puff(r.x[c] + dice.between(-40F, 40F), r.y[c] - dice.between(0F, 30F), r.z[c] + dice.between(-40F, 40F),
                    r.vx[c] * 0.6F + dice.between(-5F, 5F), -dice.between(1F, 6F),
                    r.vz[c] * 0.6F + dice.between(-5F, 5F), 9F, 26F);
        }
    }

    /** The wheels of car {@code c} run through the heaps: each is flattened and spreads. */
    private void flatten(RaceView r, int c) {
        for (int i = 0; i < HEAPS; i++) {
            if (!heapLive[i] || tick - heapTick[i] < HEAP_SETTLES) {
                continue;
            }
            float dx = heapX[i] - r.x[c];
            float dz = heapZ[i] - r.z[c];
            float reach = heapR[i] + halfWide[c] + 80F;
            if (dx * dx + dz * dz > reach * reach) {
                continue;
            }
            for (int w = 0; w < RaceView.WHEELS; w++) {
                int at = c * RaceView.WHEELS + w;
                float wx = heapX[i] - r.wheelX[at];
                float wz = heapZ[i] - r.wheelZ[at];
                if (r.contact[at] && wx * wx + wz * wz < heapR[i] * heapR[i] * 0.7F) {
                    for (int k = 0; k < 3; k++) {
                        puff(heapX[i], heapY[i] - heapH[i] * 0.5F, heapZ[i], r.vx[c] * 0.4F + dice.between(-5F, 5F),
                                -dice.between(2F, 6F), r.vz[c] * 0.4F + dice.between(-5F, 5F), 10F, 30F);
                    }
                    for (int k = 0; k < 2 && heapH[i] > 6F; k++) {
                        Weather.lump(heapX[i] + dice.between(-12F, 12F), heapY[i] - heapH[i] * 0.6F,
                                heapZ[i] + dice.between(-12F, 12F), r.vx[c] * 0.5F + dice.between(-5F, 5F),
                                -dice.between(4F, 9F), r.vz[c] * 0.5F + dice.between(-5F, 5F),
                                dice.between(7F, 13F));
                    }
                    heapH[i] *= 0.5F;
                    heapR[i] = Math.min(HEAP_WIDEST, heapR[i] * 1.1F);
                    heapTick[i] = tick;
                    if (heapH[i] < 2.5F) {
                        heapLive[i] = false;
                        heaps--;
                    }
                    break;
                }
            }
        }
    }

    /** Snow lands at {@code (x, z)} on ground at height {@code y}: a heap {@code r} wide and {@code h} high, or more of one already there. */
    void heap(float x, float y, float z, float r, float h) {
        if (cleared(x, z)) {
            return;
        }
        for (int i = 0; i < HEAPS; i++) {
            float dx = heapX[i] - x;
            float dz = heapZ[i] - z;
            if (heapLive[i] && dx * dx + dz * dz < HEAP_JOINS * HEAP_JOINS && Math.abs(heapY[i] - y) < 20F) {
                heapR[i] = Math.min(HEAP_WIDEST, heapR[i] + r * 0.16F);
                heapH[i] = Math.min(HEAP_HIGHEST, heapH[i] + h * 0.22F);
                heapTick[i] = tick;
                return;
            }
        }
        int i = heapNext;
        heapNext = (heapNext + 1) % HEAPS;
        if (!heapLive[i]) {
            heaps++;
        }
        heapLive[i] = true;
        heapX[i] = x;
        heapY[i] = y;
        heapZ[i] = z;
        heapR[i] = Math.min(HEAP_WIDEST, r);
        heapH[i] = Math.min(HEAP_HIGHEST, h);
        heapTick[i] = tick;
    }

    static float groundUnder(RaceView r, int c) {
        float sum = 0F;
        int n = 0;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (r.contact[at] && !r.onRamp[at]) {
                sum += r.wheelY[at];
                n++;
            }
        }
        return n > 0 ? sum / n : GROUND;
    }

    /** A cloud of powder: it hangs, thins and blows off with the wind. */
    void powder(float x, float y, float z, float vx, float vy, float vz, float from, float to, float cover) {
        int i = puffs.add(x, y - 5F, z, vx, vy, vz, dice.between(8F, 13F), from, to, LIT, cover, 0.08F, 0F, false);
        puffs.water(i);
        thrown++;
    }

    /** The snow's own dice, for the bed. */
    Dice dice() {
        return dice;
    }

    void puff(float x, float y, float z, float vx, float vy, float vz, float from, float to) {
        int i = puffs.add(x, y - 3F, z, vx, vy, vz, dice.between(9F, 16F), from, to, LIT, 0.34F, 0.1F, -0.25F,
                false);
        puffs.water(i); // not smoke: the weather does not beat it down
        thrown++;
    }

    void sound(float x, float y, float z, float gain) {
        if (Features.hooks().sound3d()) {
            Sound3D.pointSound(x, y, z, WeatherSounds.HIT, gain, dice.between(0.9F, 1.1F));
        }
        heard++;
    }

    /* ------------------------------------------------------- bare ground */

    /** The explosions since the last tick: each clears the snow around it and throws it outwards. */
    private void blasts() {
        int now = Weather.blasts();
        if (now < seenBlasts) {
            seenBlasts = now;
        }
        for (int n = Math.max(seenBlasts, now - Weather.BLASTS); n < now; n++) {
            int slot = n % Weather.BLASTS;
            float x = Weather.blastX(slot);
            float y = Weather.blastGround(slot);
            float z = Weather.blastZ(slot);
            float size = Math.max(0.5F, Weather.blastSize(slot));
            int i = clears % CLEARS;
            clears++;
            clearX[i] = x;
            clearY[i] = y;
            clearZ[i] = z;
            clearR[i] = BLAST_CLEARS * size;
            clearTick[i] = tick;
            melt(x, z, clearR[i]);
            for (int k = 0; k < 14; k++) {
                double a = 2D * Math.PI * k / 14D;
                float out = dice.between(6F, 14F);
                puff(x + (float) Math.cos(a) * clearR[i] * 0.5F, y, z + (float) Math.sin(a) * clearR[i] * 0.5F,
                        (float) Math.cos(a) * out, -dice.between(4F, 10F), (float) Math.sin(a) * out, 18F, 50F);
            }
        }
        seenBlasts = now;
    }

    /** The heaps and trails within {@code r} of {@code (x, z)} are gone. */
    private void melt(float x, float z, float r) {
        if (r <= 0F) {
            return;
        }
        for (int i = 0; i < HEAPS; i++) {
            float dx = heapX[i] - x;
            float dz = heapZ[i] - z;
            if (heapLive[i] && dx * dx + dz * dz < r * r) {
                heapLive[i] = false;
                heaps--;
            }
        }
        for (int i = 0; i < RUTS; i++) {
            float dx = rutX[i * 4] - x;
            float dz = rutZ[i * 4] - z;
            if (rutLive[i] && dx * dx + dz * dz < r * r) {
                rutLive[i] = false;
                ruts--;
                if (rutWide[i]) {
                    channels--;
                }
            }
        }
        bed.melt(x, z, r);
    }

    /** How much of the bare ground a blast left {@code ticks} ago still shows, 1 to 0: the snowfall covers it again. */
    static float bare(int ticks) {
        float t = ticks / (float) DrySpots.SNOWED_IN;
        return t <= 0.6F ? 1F : Math.max(0F, 1F - (t - 0.6F) / 0.4F);
    }

    /** True if {@code (x, z)} is on ground a blast bared a moment ago or a fire keeps bare. */
    boolean cleared(float x, float z) {
        for (int n = Math.max(0, clears - CLEARS); n < clears; n++) {
            int i = n % CLEARS;
            float dx = clearX[i] - x;
            float dz = clearZ[i] - z;
            if (tick - clearTick[i] < DrySpots.SNOWED_IN / 2 && dx * dx + dz * dz < clearR[i] * clearR[i]) {
                return true;
            }
        }
        for (int c = 0; c < cars; c++) {
            if (burning[c] >= 0) {
                float r = melted(burning[c]);
                float dx = pose[c * 12] - x;
                float dz = pose[c * 12 + 2] - z;
                if (dx * dx + dz * dz < r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    /* ------------------------------------------------------------ drawing */

    /**
     * Queues what the snow shows: the trails and the bare ground as one
     * ground item ({@code groundItem}), each heap by its distance (from
     * {@code heapBase}), the snow on each car after the car (from
     * {@code carBase}), the puffs (from {@code puffBase}).
     */
    void queue(DrawQueue queue, int groundItem, int heapBase, int carBase, int puffBase, int bedItem, int driftBase,
            int wheelBase) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        drawnHeaps = 0;
        drawnSides = 0;
        drawnPushed = 0;
        if (!on) {
            return;
        }
        boolean burns = false;
        for (int c = 0; c < cars; c++) {
            burns |= burning[c] > 0;
            int at = c * 12;
            if (!near(pose[at], pose[at + 2], 5000F)) {
                continue;
            }
            if (arch[c] > 0.08F) {
                queue.after(c, carBase + c * CAR_PARTS); // on the outside of the side that faces the eye
            }
            if (trunk[c] > 0.05F) {
                // a part of the car: among its polygons, after the floor it lies on, so the cab and the walls cover it
                float ly = trunkY[c] - trunkWall[c] * 0.5F;
                queue.within(c, trunkPlane[c], pose[at] + pose[at + 3] * trunkX[c] + pose[at + 6] * ly
                        + pose[at + 9] * trunkZ[c], pose[at + 1] + pose[at + 4] * trunkX[c] + pose[at + 7] * ly
                        + pose[at + 10] * trunkZ[c], pose[at + 2] + pose[at + 5] * trunkX[c] + pose[at + 8] * ly
                        + pose[at + 11] * trunkZ[c], carBase + c * CAR_PARTS + 1);
            }
            if (pushed[c] > 0.05F) {
                queue.within(c, -1, pose[at], GROUND - 10F, pose[at + 2], carBase + c * CAR_PARTS + 2);
            }
        }
        if (ruts > 0 || clears > 0 || burns) {
            queue.ground(groundItem);
        }
        bed.queue(queue, bedItem, driftBase, wheelBase);
        for (int i = 0; i < HEAPS; i++) {
            if (heapLive[i] && near(heapX[i], heapZ[i], SEEN)) {
                // pulled a little towards the eye, so the road under it is drawn first
                // (beside or under a car it is drawn as a part of the car, so it does not show through it)
                queue.near(eyeX + (heapX[i] - eyeX) * 0.97F, eyeY + (heapY[i] - eyeY) * 0.97F,
                        eyeZ + (heapZ[i] - eyeZ) * 0.97F, heapBase + i);
            }
        }
        puffs.queue(queue, puffBase);
    }

    private boolean near(float x, float z, float far) {
        float dx = x - eyeX;
        float dz = z - eyeZ;
        return dx * dx + dz * dz < far * far;
    }

    /** Draws the trails and the bare ground near enough to see. */
    void drawGround(Canvas canvas) {
        drawnRuts = 0;
        drawnBare = 0;
        canvas.dither(1F);
        drawnRelief = 0;
        // the channels first: the wheels' trails run along their floors
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < RUTS; i++) {
                if (!rutLive[i] || rutWide[i] != (pass == 0)) {
                    continue;
                }
                int age = tick - rutTick[i];
                if (age >= RUT_FILLS) {
                    rutLive[i] = false;
                    ruts--;
                    if (rutWide[i]) {
                        channels--;
                    }
                    continue;
                }
                if (!near(rutX[i * 4], rutZ[i * 4], SEEN)) {
                    continue;
                }
                float left = 1F - age / (float) RUT_FILLS;
                if (near(rutX[i * 4], rutZ[i * 4], RELIEF)) {
                    relief(canvas, i, left);
                } else {
                    for (int k = 0; k < 4; k++) {
                        cx[k] = rutX[i * 4 + k];
                        cy[k] = rutY[i] - 0.3F;
                        cz[k] = rutZ[i * 4 + k];
                    }
                    canvas.color(canvas.fog(RUT_RGB, cx[0], cy[0], cz[0]));
                    canvas.dither((rutWide[i] ? 0.5F : 1F) * RUT_COVER * left);
                    canvas.world(cx, cy, cz, 4);
                }
                drawnRuts++;
            }
        }
        for (int n = Math.max(0, clears - CLEARS); n < clears; n++) {
            int i = n % CLEARS;
            float shows = bare(tick - clearTick[i]);
            if (shows > 0.03F && near(clearX[i], clearZ[i], SEEN)) {
                patch(canvas, clearX[i], clearY[i], clearZ[i], clearR[i], BARE_COVER * shows, i);
            }
        }
        for (int c = 0; c < cars; c++) {
            float r = burning[c] >= 0 ? melted(burning[c]) : 0F;
            if (r > 12F && near(pose[c * 12], pose[c * 12 + 2], SEEN)) {
                patch(canvas, pose[c * 12], GROUND, pose[c * 12 + 2], r, BARE_COVER, 100 + c);
            }
        }
        canvas.dither(1F);
    }

    /** Trail pieces of the last frame drawn as cuts with walls. */
    int drawnRelief;

    /**
     * Trail piece {@code i} near the eye, {@code left} of it not yet filled
     * in by the snowfall (1 to 0): its floor, and a wall inside each edge as
     * wide as the cut is deep, lit or in shade by the way it faces.
     */
    private void relief(Canvas canvas, int i, float left) {
        int at = i * 4;
        float y = rutY[i] - 0.3F;
        // corners 0 and 3 are one edge, 1 and 2 the other; across the trail from the first to the second
        float ax = rutX[at + 1] - rutX[at];
        float az = rutZ[at + 1] - rutZ[at];
        float bx = rutX[at + 2] - rutX[at + 3];
        float bz = rutZ[at + 2] - rutZ[at + 3];
        float al = (float) Math.sqrt(ax * ax + az * az);
        float bl = (float) Math.sqrt(bx * bx + bz * bz);
        if (al < 1F || bl < 1F) {
            return;
        }
        float cut = rutCut[i] * (0.35F + 0.65F * left);
        float wall = Math.min(cut * 0.55F, Math.min(al, bl) * 0.3F);
        float lit = 0.5F + 0.5F * (ax / al * LIGHT_X + az / al * LIGHT_Z);
        float deep = Math.min(1F, cut / 34F);
        // the floor: a body skims the top off, packed snow; a wheel presses through to what lies under thin snow
        int floor = rutWide[i] ? Air.mix(rutUnder[i], FLOOR, 0.45F + 0.4F * deep)
                : Air.mix(Air.mix(rutUnder[i], 0x000000, 0.15F), FLOOR, 0.15F + 0.6F * deep);
        canvas.color(canvas.fog(floor, rutX[at], y, rutZ[at]));
        canvas.dither(Math.min(1F, (rutWide[i] ? 0.8F : 0.92F) * (0.25F + 0.75F * left)));
        for (int k = 0; k < 4; k++) {
            cx[k] = rutX[at + k];
            cy[k] = y;
            cz[k] = rutZ[at + k];
        }
        canvas.world(cx, cy, cz, 4);
        canvas.dither(Math.min(1F, 0.3F + 0.7F * left));
        // the wall along the first edge faces across to the second, and the second's back
        for (int side = 0; side < 2; side++) {
            float way = side == 0 ? 1F : -1F;
            float facing = side == 0 ? lit : 1F - lit;
            int p = side == 0 ? at : at + 1;
            int q = side == 0 ? at + 3 : at + 2;
            canvas.color(canvas.fog(Air.mix(WALL, LIT, facing * facing), rutX[at], y, rutZ[at]));
            cx[0] = rutX[p];
            cz[0] = rutZ[p];
            cx[1] = rutX[p] + way * ax / al * wall;
            cz[1] = rutZ[p] + way * az / al * wall;
            cx[2] = rutX[q] + way * bx / bl * wall;
            cz[2] = rutZ[q] + way * bz / bl * wall;
            cx[3] = rutX[q];
            cz[3] = rutZ[q];
            cy[0] = cy[1] = cy[2] = cy[3] = y - 0.1F;
            canvas.world(cx, cy, cz, 4);
            if (rutWide[i]) {
                // a channel's lip: the snow it pushed aside lies along its edge, lit from the other way
                float lip = Math.min(cut * 0.8F, 16F);
                canvas.color(canvas.fog(Air.mix(WALL, 0xffffff, 0.72F + 0.28F * (1F - facing)), rutX[at], y, rutZ[at]));
                cx[1] = rutX[p] - way * ax / al * lip;
                cz[1] = rutZ[p] - way * az / al * lip;
                cx[2] = rutX[q] - way * bx / bl * lip;
                cz[2] = rutZ[q] - way * bz / bl * lip;
                canvas.world(cx, cy, cz, 4);
            }
        }
        drawnRelief++;
    }

    /** A patch of bare ground {@code r} around {@code (x, z)}: an uneven round, laid darkly and thinly over the snow. */
    private void patch(Canvas canvas, float x, float y, float z, float r, float cover, int seed) {
        for (int k = 0; k < ROUND; k++) {
            double a = 2D * Math.PI * k / ROUND;
            float reach = r * (0.84F + 0.3F * Fall.hash(seed, k, 9));
            cx[k] = x + (float) Math.cos(a) * reach;
            cy[k] = y - 0.5F;
            cz[k] = z + (float) Math.sin(a) * reach;
        }
        canvas.color(canvas.fog(BARE, x, y, z));
        canvas.dither(cover);
        canvas.world(cx, cy, cz, ROUND);
        drawnBare++;
    }

    /** Draws heap {@code i}. */
    void drawHeap(Canvas canvas, int i) {
        if (i < 0 || i >= HEAPS || !heapLive[i]) {
            return;
        }
        // seen from (ex, ey, ez) in the heap's own numbers: x and z as the world's, up positive
        mound(canvas, -1, heapX[i], heapY[i], heapZ[i], heapR[i], heapR[i], heapH[i], eyeX - heapX[i],
                heapY[i] - eyeY, eyeZ - heapZ[i], i, 0F, 0F, 0F);
        drawnHeaps++;
    }

    /** Items a car: the snow in its wheel arches, in its open back, and what it pushes lying on its side. */
    static final int CAR_PARTS = 4;

    /** Draws item {@code item} of the snow on a car ({@link #CAR_PARTS} a car). */
    void drawCar(Canvas canvas, int item) {
        int c = item / CAR_PARTS;
        int part = item % CAR_PARTS;
        if (c < 0 || c >= cars) {
            return;
        }
        int at = c * 12;
        // the eye in the car's own numbers
        float dx = eyeX - pose[at];
        float dy = eyeY - pose[at + 1];
        float dz = eyeZ - pose[at + 2];
        float ex = dx * pose[at + 3] + dy * pose[at + 4] + dz * pose[at + 5];
        float ey = dx * pose[at + 6] + dy * pose[at + 7] + dz * pose[at + 8];
        float ez = dx * pose[at + 9] + dy * pose[at + 10] + dz * pose[at + 11];
        if (part == 2 && pushed[c] > 0.05F) {
            // the heap it pushes: in the world, ahead of it the way it slides, moving with it
            float vx = carV[c * 3];
            float vz = carV[c * 3 + 2];
            float v = (float) Math.sqrt(vx * vx + vz * vz);
            if (v > 0.5F) {
                float lead = halfWide[c] * 0.9F;
                float hx = pose[at] + vx / v * lead;
                float hz = pose[at + 2] + vz / v * lead;
                float r = 24F + 34F * pushed[c];
                mound(canvas, -1, hx, GROUND, hz, r, r, 8F + 14F * pushed[c], eyeX - hx, GROUND - eyeY, eyeZ - hz,
                        200 + c, vx, 0F, vz);
            }
        }
        if (part == 1 && trunk[c] > 0.05F) {
            // it fills from the floor up to the top of the walls around it, and heaps up over them
            float fill = trunk[c];
            float foot = trunkY[c] - trunkWall[c] * Math.min(1F, fill * 1.5F);
            float high = 0.55F * Math.min(trunkWide[c], trunkLong[c]) * (0.3F + 0.7F * fill);
            mound(canvas, c, trunkX[c], foot, trunkZ[c], trunkWide[c] * (0.6F + 0.3F * fill),
                    trunkLong[c] * (0.6F + 0.3F * fill), high, ex - trunkX[c], foot - ey, ez - trunkZ[c],
                    300 + c, 0F, 0F, 0F);
        }
        if (part == 0 && arch[c] > 0.08F && shapes != null && c < shapes.length && shapes[c] != null) {
            arches(canvas, c, shapes[c], ex);
        }
    }

    /** The cars' models, for where their wheels are. */
    private CarShape[] shapes;
    private int humans = 1;

    /**
     * Snow packed over each wheel on the side of car {@code c} that faces
     * the eye: a thin crescent above the tyre, in the plane of the wheel's
     * outer face.
     */
    private void arches(Canvas canvas, int c, CarShape s, float ex) {
        float full = arch[c];
        int at = c * 12;
        canvas.color(canvas.fog(LIT, pose[at], pose[at + 1], pose[at + 2]));
        canvas.dither(1F);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            float r = s.wheelRadius[w];
            if (r <= 0F) {
                continue;
            }
            float out = s.hubX[w] < 0F ? -1F : 1F;
            if (out * ex < Math.abs(s.hubX[w])) {
                continue; // the far side's are behind the car
            }
            float x = s.hubX[w] + out * (s.wheelHalfWidth[w] + 0.6F);
            float inner = r * 1.03F;
            float outer = r * (1.08F + 0.2F * full);
            // from 50 degrees ahead of the top to 50 behind it, thickest at the top
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(-50D + k * 100D / 3D);
                float thick = k == 0 || k == 3 ? 0.35F : 1F;
                cx[k] = x;
                cy[k] = s.hubY[w] - (float) Math.cos(a) * (inner + (outer - inner) * thick);
                cz[k] = s.hubZ[w] + (float) Math.sin(a) * (inner + (outer - inner) * thick);
                cx[7 - k] = x;
                cy[7 - k] = s.hubY[w] - (float) Math.cos(a) * inner;
                cz[7 - k] = s.hubZ[w] + (float) Math.sin(a) * inner;
            }
            canvas.onCar(c, cx, cy, cz, 8);
        }
    }

    /**
     * A mound of snow: a six-sided base {@code rx} by {@code rz}, a ring
     * at 55% of that at 70% of its height {@code h}, and a peak; only the
     * sides turned to the eye are drawn, so nothing of it covers another
     * part (it bulges nowhere inwards). Its middle is at {@code (x, y, z)}:
     * in the world, or with {@code car} not -1 on that car, in its numbers
     * (up is towards lesser y in both). The eye is at {@code (ex, up, ez)}
     * from its middle, {@code up} above its foot. In the world it moves
     * {@code (vx, vy, vz)} a tick.
     */
    void mound(Canvas canvas, int car, float x, float y, float z, float rx, float rz, float h, float ex,
            float up, float ez, int seed, float vx, float vy, float vz) {
        float fogX = car < 0 ? x : pose[car * 12];
        float fogY = car < 0 ? y : pose[car * 12 + 1];
        float fogZ = car < 0 ? z : pose[car * 12 + 2];
        float turn = Fall.hash(seed, 3, 7) * 1.05F;
        for (int k = 0; k < SIDES; k++) {
            double a0 = turn + 2D * Math.PI * k / SIDES;
            double a1 = turn + 2D * Math.PI * (k + 1) / SIDES;
            double am = (a0 + a1) / 2D;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos(am);
            float nz = (float) Math.sin(am);
            // lit from one side of the world, the way the sector faces
            float lit = 0.5F + 0.5F * (nx * 0.6F - nz * 0.8F);
            // the lower side: a steep band; outward normal (nx * 0.7 h, 0.45 r, nz * 0.7 h) or so
            float r = (rx + rz) / 2F;
            float mx = nx * rx * 0.78F;
            float mz = nz * rz * 0.78F;
            if ((ex - mx) * nx * 0.7F * h + (up - 0.35F * h) * 0.45F * r + (ez - mz) * nz * 0.7F * h > 0F) {
                corner(0, x + c0 * rx, y, z + s0 * rz);
                corner(1, x + c1 * rx, y, z + s1 * rz);
                corner(2, x + c1 * rx * 0.55F, y - h * 0.7F, z + s1 * rz * 0.55F);
                corner(3, x + c0 * rx * 0.55F, y - h * 0.7F, z + s0 * rz * 0.55F);
                canvas.color(canvas.fog(Air.mix(SHADE, LIT, 0.15F + 0.6F * lit), fogX, fogY, fogZ));
                emit(canvas, car, 4, vx, vy, vz);
            }
            // the top: a shallow triangle to the peak; outward normal (nx * 0.3 h, 0.55 r, nz * 0.3 h) or so
            mx = nx * rx * 0.3F;
            mz = nz * rz * 0.3F;
            if ((ex - mx) * nx * 0.3F * h + (up - 0.85F * h) * 0.55F * r + (ez - mz) * nz * 0.3F * h > 0F) {
                corner(0, x + c0 * rx * 0.55F, y - h * 0.7F, z + s0 * rz * 0.55F);
                corner(1, x + c1 * rx * 0.55F, y - h * 0.7F, z + s1 * rz * 0.55F);
                corner(2, x, y - h, z);
                canvas.color(canvas.fog(Air.mix(SHADE, LIT, 0.55F + 0.45F * lit), fogX, fogY, fogZ));
                emit(canvas, car, 3, vx, vy, vz);
            }
        }
    }

    private void corner(int k, float x, float y, float z) {
        cx[k] = x;
        cy[k] = y;
        cz[k] = z;
    }

    private void emit(Canvas canvas, int car, int n, float vx, float vy, float vz) {
        if (car >= 0) {
            canvas.onCar(car, cx, cy, cz, n);
        } else if (vx != 0F || vy != 0F || vz != 0F) {
            canvas.moving(cx, cy, cz, vx, vy, vz, n);
            drawnPushed++;
        } else {
            canvas.world(cx, cy, cz, n);
            drawnSides++;
        }
    }

    void drawPuff(Canvas canvas, int item) {
        puffs.draw(canvas, item);
    }

    /* -------------------------------------------------------- for checks */

    /** Heap {@code n} of those lying (in no order): its place, width and height into {@code out[0..4]}; false past the last. */
    boolean heapAt(int n, float[] out) {
        for (int i = 0; i < HEAPS; i++) {
            if (heapLive[i] && n-- == 0) {
                out[0] = heapX[i];
                out[1] = heapY[i];
                out[2] = heapZ[i];
                out[3] = heapR[i];
                out[4] = heapH[i];
                return true;
            }
        }
        return false;
    }

    /** The bare ground blasts have left, counted since the race started. */
    int clears() {
        return clears;
    }
}
