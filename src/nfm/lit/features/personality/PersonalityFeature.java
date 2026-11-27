package nfm.lit.features.personality;

import fallk.logmaster.HLogger;
import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.CarBody;
import nfm.lit.features.CarLook;
import nfm.lit.features.CarLooks;
import nfm.lit.features.CarShape;
import nfm.lit.features.Debris;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.Features;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceView;
import nfm.lit.features.realism.CarSheet;
import nfm.lit.features.realism.CarSheets;
import nfm.lit.features.sounds.Recordings;

/**
 * One small thing each that only that car does (car-personality): Tornado
 * Shark starts dusty, Lead Oxide shows heat at its front, Kool Kat lands
 * heavily, Mighty Eight's radiators breathe and its nitro burns big, MAX
 * Revenge's blower shakes, M A S H E E N's plow carries dirt, DR Monstaa's
 * tyres squash, something slides in Wow Caninaro, EL KING's cab rocks.
 * <p>
 * Seen and heard only: it reads the race's copy and throws its own dice,
 * seeded from the race, so a race replayed shows the same. Its sounds are
 * recordings played at the car through the 3D sound; Classic sound hears
 * none of them. Each car's part (the blower, the cab, the plow) is found on
 * its model when a race starts ({@link Quirk#find}).
 */
public final class PersonalityFeature implements Feature, CarLook, CarBody {

    public static final PersonalityFeature FEATURE = new PersonalityFeature();
    public static final String ID = "personality";

    static final int NONE = 0;
    static final int SHARK = 1;
    static final int OXIDE = 2;
    static final int KAT = 3;
    static final int EIGHT = 4;
    static final int REVENGE = 5;
    static final int MASHEEN = 6;
    static final int MONSTAA = 7;
    static final int CANINARO = 8;
    static final int KING = 9;
    /** The model each personality belongs to, by the numbers above. */
    static final String[] MODELS = {"", "2000tornados", "leadoxide", "koolkat", "audir8", "maxrevenge", "masheen",
        "drmonster", "canyenaro", "king"};

    /* the sounds: recordings, at the car */
    static final String THONK = "quirk-thonk";
    static final String KNOCK = "quirk-knock";
    static final String SLIDE = "quirk-slide";
    static final String AIR = "quirk-air";

    static final float GROUND = 250F;
    static final float DEG = (float) (Math.PI / 180D);

    /* Tornado Shark */
    /** The dust on it at the start, and what the takeoff and each of the first turns shake off. */
    static final float SHARK_TAKEOFF = 0.4F;
    static final float SHARK_TURN = 0.3F;
    static final int SHARK_TURNS = 2;
    static final int SHARK_DUST = 0xc9bfa8;
    /** How much of the dust's colour lies on the car while all of it is still there. */
    static final float SHARK_COVER = 0.34F;
    /* Lead Oxide */
    /** Seconds of hard driving that heat it through, and between two showings of the heat, at least and at most. */
    static final int OXIDE_HEATS = 19 * 6;
    static final int OXIDE_GAP_LEAST = 19 * 4;
    static final int OXIDE_GAP_MOST = 19 * 9;
    static final int OXIDE_SHOWS = 38;
    /* Kool Kat */
    /** How fast the body goes down right after the landing, by how fast it fell: least and most, units a tick. */
    static final float KAT_PUSH_LEAST = 1.6F;
    static final float KAT_PUSH_MOST = 4F;
    static final float KAT_SPRING = 0.3F;
    static final float KAT_DAMPER = 0.42F;
    /* Mighty Eight */
    /** Ticks of throttle before a lift blows the radiators, and ticks they blow. */
    static final int EIGHT_HELD = 19;
    static final int EIGHT_BLOWS = 10;
    /** Its nitro's flame: this long (other cars' is 15), in two colours. */
    static final float EIGHT_FLAME = 36F;
    static final float EIGHT_FLAME_WIDE = 3.4F;
    static final int FLAME = 0x5f9cff;
    static final int FLAME_CORE = 0xdcefff;
    /* MAX Revenge */
    /** How far the blower shakes at full throttle, of its own width. */
    static final float REVENGE_SHAKE = 0.1F;
    /* M A S H E E N */
    static final int MASHEEN_FILLS = 19 * 4;
    /** The nose this far down in the air tips the dirt out, degrees; with at least this much in the plow. */
    static final float MASHEEN_TIPS = 12F;
    static final float MASHEEN_LEAST = 0.3F;
    static final int CHUNKS = 24;
    static final int CHUNKS_KEPT = 96;
    /* DR Monstaa */
    /** How much of its radius a squashed tyre loses under the hub at the landing, and what is left of that a tick later. */
    static final float MONSTAA_SQUASH = 0.34F;
    static final float MONSTAA_BACK = 0.62F;
    static final float MONSTAA_BULGE = 0.28F;
    /* Wow Caninaro */
    /** A corner this hard (units a tick each tick, across the car) sends the thing sliding. */
    static final float CANINARO_HARD = 1.8F;
    /* EL KING */
    /** Slowing down this fast under the brake is a hard stop, units a tick each tick; the cab rocks at most this far, degrees. */
    static final float KING_HARD = 1.2F;
    static final float KING_ROCK = 4.5F;
    static final float KING_SPRING = 0.28F;
    static final float KING_DAMPER = 0.36F;
    /** Ticks after a hard stop in which releasing the brake lets the air out. */
    static final int KING_BREATH = 19 * 3;

    /** Items: particles from 0, then each car's own drawing, flying chunks, the chunks lying on the road. */
    static final int PARTICLES = 320;
    static final int CAR_ITEM = PARTICLES;
    static final int MOST_CARS = 64;
    static final int CHUNK_ITEM = CAR_ITEM + MOST_CARS;
    static final int GROUND_ITEM = CHUNK_ITEM + CHUNKS;

    final Particles particles = new Particles(PARTICLES);
    final Debris lying = new Debris(CHUNKS_KEPT);
    private final Dice dice = new Dice(0L, ID);
    private RaceView view;
    private int cars;
    Quirk[] quirks = new Quirk[0];
    private boolean sounds;
    private int tick;
    private float share = 1F;
    private float windX;
    private float windZ;

    /* chunks of dirt in the air */
    private final boolean[] chunkLive = new boolean[CHUNKS];
    private final float[] chunkX = new float[CHUNKS];
    private final float[] chunkY = new float[CHUNKS];
    private final float[] chunkZ = new float[CHUNKS];
    private final float[] chunkVX = new float[CHUNKS];
    private final float[] chunkVY = new float[CHUNKS];
    private final float[] chunkVZ = new float[CHUNKS];
    private final float[] chunkSize = new float[CHUNKS];
    private final float[] chunkTurn = new float[CHUNKS];
    private final int[] chunkRgb = new int[CHUNKS];
    private int chunkNext;

    /** For the checks: since the race started, sounds asked for by name, chunks tipped out and landed. */
    int thonks;
    int knocks;
    int slides;
    int breaths;
    int tipped;
    int landed;
    /** For the checks: in the last frame, flame polygons and polygons of dirt in a plow. */
    int drawnFlames;
    int drawnDirt;

    private final float[] p = new float[3];
    private final float[] q = new float[3];
    private final float[] cx = new float[Canvas.MAX_CORNERS];
    private final float[] cy = new float[Canvas.MAX_CORNERS];
    private final float[] cz = new float[Canvas.MAX_CORNERS];
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    private PersonalityFeature() {
    }

    /** A feature of its own, for the checks. */
    static PersonalityFeature forCheck() {
        return new PersonalityFeature();
    }

    @Override
    public String id() {
        return ID;
    }

    /** The personality of model {@code name}, or {@link #NONE}. */
    static int kindOf(String name) {
        for (int k = 1; k < MODELS.length; k++) {
            if (MODELS[k].equals(name)) {
                return k;
            }
        }
        return NONE;
    }

    @Override
    public void raceStarted(RaceView race) {
        view = race;
        cars = race.cars;
        if (quirks.length < cars) {
            quirks = new Quirk[cars];
        }
        dice.reset(race.seed, ID);
        windX = dice.between(-0.5F, 0.5F);
        windZ = dice.between(-0.5F, 0.5F);
        int found = 0;
        for (int c = 0; c < cars; c++) {
            Quirk k = new Quirk();
            quirks[c] = k;
            k.kind = kindOf(race.modelName[c]);
            k.sheet = CarSheets.of(race.modelName[c]);
            k.find(c < race.shape.length ? race.shape[c] : null, race.wheelDrop[c]);
            k.lastXz = race.xz[c];
            k.gap = OXIDE_GAP_LEAST + dice.below(OXIDE_GAP_MOST - OXIDE_GAP_LEAST);
            found += k.kind != NONE ? 1 : 0;
            if (k.hasPart) {
                int planes = 0;
                for (boolean in : k.part) {
                    planes += in ? 1 : 0;
                }
                HLogger.info("[extras] personality: " + race.modelName[c] + "'s part is " + planes + " polygons, "
                        + (2F * k.half) + " wide, from " + k.partBack + " to " + k.partFront + " along the car and "
                        + k.partTop + " to " + k.partBottom + " down it");
            }
        }
        particles.clear();
        lying.clear();
        java.util.Arrays.fill(chunkLive, false);
        chunkNext = 0;
        thonks = 0;
        knocks = 0;
        slides = 0;
        breaths = 0;
        tipped = 0;
        landed = 0;
        tick = race.tick;
        if (!sounds) {
            sounds = true;
            Recordings.give(THONK, "thud", false);
            Recordings.give(KNOCK, "knock", false);
            Recordings.give(SLIDE, "scrape", false);
            Recordings.give(AIR, "airbrake", false);
        }
        HLogger.info("[extras] personality: " + found + " of " + cars + " cars have one");
    }

    @Override
    public void raceEnded(RaceView race) {
        view = null;
        cars = 0;
        particles.clear();
        lying.clear();
    }

    /* --------------------------------------------------------- each tick */

    @Override
    public void afterTick(RaceView race) {
        if (race.cars != cars || view != race) {
            raceStarted(race);
        }
        tick = race.tick;
        for (int c = 0; c < cars; c++) {
            Quirk k = quirks[c];
            if (k.kind == NONE) {
                continue;
            }
            float speed = race.groundSpeed(c);
            float turned = wrap(race.xz[c] - k.lastXz);
            boolean landing = race.grounded[c] && k.lastAir >= 3;
            boolean alive = !race.wasted[c];
            switch (k.kind) {
                case SHARK -> shark(race, c, k, speed, turned);
                case OXIDE -> oxide(race, c, k, alive);
                case KAT -> kat(race, c, k, landing);
                case EIGHT -> eight(race, c, k, speed, alive);
                case REVENGE -> revenge(race, c, k, alive);
                case MASHEEN -> masheen(race, c, k, speed);
                case MONSTAA -> monstaa(race, c, k, landing);
                case CANINARO -> caninaro(race, c, k, speed, turned);
                case KING -> king(race, c, k, speed, alive);
                default -> {
                }
            }
            k.lastXz = race.xz[c];
            k.lastSpeed = speed;
            k.lastVy = race.vy[c];
            k.lastAir = race.airTicks[c];
            if (SHOW) {
                show(race, c, k);
            }
        }
        chunks();
        particles.tick(windX, windZ);
    }

    /** A hidden test run's screenshots show every personality at its fullest, all the time ({@code -Dnfm.personality.show=true}). */
    private static final boolean SHOW = Boolean.getBoolean("nfm.personality.show");

    private void show(RaceView race, int c, Quirk k) {
        k.dust = 1F;
        k.dip = k.dipWas = k.room;
        k.shake = k.shakeWas = 1F;
        k.dirt = 1F;
        k.squash[0] = 1F;
        k.rock = k.rockWas = KING_ROCK;
        k.burns = k.kind == EIGHT;
        k.flame[0] = k.flame[1] = EIGHT_FLAME;
        if (k.kind == OXIDE) {
            k.heat = 1F;
            k.gap = 0;
        }
    }

    /** Tornado Shark: the dust it stood under comes off at the takeoff and in its first couple of turns. */
    private void shark(RaceView race, int c, Quirk k, float speed, float turned) {
        if (!k.started) {
            k.started = true;
            k.dust = 1F;
        }
        if (!k.away && speed > 4F) {
            k.away = true;
            k.shedTicks = 28;
            k.shedEach = SHARK_TAKEOFF / 28F;
        }
        boolean turning = k.away && Math.abs(turned) >= 2F && speed > 12F;
        if (turning && k.straight >= 10 && k.turns < SHARK_TURNS && k.shedTicks == 0) {
            k.turns++;
            k.shedTicks = 16;
            k.shedEach = SHARK_TURN / 16F;
        }
        k.straight = turning ? 0 : k.straight + 1;
        if (k.shedTicks > 0) {
            k.shedTicks--;
            k.dust = Math.max(0F, k.dust - k.shedEach);
            CarShape s = k.shape;
            for (int n = 0; n < 2 && s != null; n++) {
                // off the roof, the bonnet and the tail, left behind in the car's own wind
                race.toWorld(c, dice.between(s.minX, s.maxX), dice.between(s.minY, (s.minY + s.maxY) / 2F),
                        dice.between(s.minZ, s.maxZ), p);
                particles.add(p[0], p[1], p[2], race.vx[c] * 0.35F + dice.between(-1.5F, 1.5F),
                        -dice.between(0.3F, 1.4F), race.vz[c] * 0.35F + dice.between(-1.5F, 1.5F),
                        dice.between(16F, 24F), 9F, 30F, SHARK_DUST, 0.3F, 0.08F, 0.03F, false);
                k.puffs++;
            }
            if (k.shedTicks == 0 && k.turns >= SHARK_TURNS) {
                k.dust = 0F; // the last of it is gone with the second turn
            }
        }
    }

    /** Lead Oxide: driven hard it heats through, and now and then the heat shows over its nose. */
    private void oxide(RaceView race, int c, Quirk k, boolean alive) {
        boolean hard = alive && race.up[c] && Math.abs(race.speed[c]) > 0.6F * race.topSpeed[c];
        k.heat = Math.max(0F, Math.min(1F, k.heat + (hard ? 1F / OXIDE_HEATS : -1F / (OXIDE_HEATS * 2F))));
        if (k.heat > 0.6F && k.showTicks == 0 && --k.gap <= 0) {
            k.showTicks = OXIDE_SHOWS;
            k.gap = OXIDE_GAP_LEAST + dice.below(OXIDE_GAP_MOST - OXIDE_GAP_LEAST);
            k.shown++;
        }
        if (k.showTicks > 0) {
            k.showTicks--;
            CarShape s = k.shape;
            if (s != null) {
                // a pale haze off the front of the bonnet, rising and left behind
                race.toWorld(c, dice.between(s.minX, s.maxX) * 0.55F, s.minY + (s.maxY - s.minY) * 0.45F,
                        s.maxZ * dice.between(0.6F, 0.95F), p);
                particles.add(p[0], p[1], p[2], race.vx[c] * 0.6F + dice.between(-0.8F, 0.8F),
                        -dice.between(0.8F, 1.6F), race.vz[c] * 0.6F + dice.between(-0.8F, 0.8F),
                        dice.between(14F, 20F), 8F, 28F, 0xe9edf0, 0.15F, 0.06F, 0.1F, false);
                k.puffs++;
            }
        }
    }

    /** Kool Kat: landing on all four wheels pushes its heavy body down on them, with a thonk. */
    private void kat(RaceView race, int c, Quirk k, boolean landing) {
        if (landing) {
            k.window = 3;
            k.fell = Math.max(0F, k.lastVy);
        }
        if (k.window > 0) {
            k.window--;
            int w0 = c * RaceView.WHEELS;
            if (race.contact[w0] && race.contact[w0 + 1] && race.contact[w0 + 2] && race.contact[w0 + 3]) {
                k.window = 0;
                k.dipSpeed += Math.max(KAT_PUSH_LEAST, Math.min(KAT_PUSH_MOST, k.fell * 0.22F));
                k.events++;
                thonks++;
                sound(c, THONK, 0.55F, 0.72F);
            }
        }
        k.dipWas = k.dip;
        k.dipSpeed += -KAT_SPRING * k.dip - KAT_DAMPER * k.dipSpeed;
        k.dip += k.dipSpeed;
        if (k.dip > k.room) {
            k.dip = k.room;
            k.dipSpeed = Math.min(0F, k.dipSpeed);
        }
        if (Math.abs(k.dip) < 0.02F && Math.abs(k.dipSpeed) < 0.02F) {
            k.dip = 0F;
            k.dipSpeed = 0F;
        }
    }

    /** Mighty Eight: lifting off the gas, its radiators blow; its nitro's flame is the biggest there is. */
    private void eight(RaceView race, int c, Quirk k, float speed, boolean alive) {
        if (alive && race.up[c]) {
            k.held++;
        } else {
            if (alive && k.held >= EIGHT_HELD && speed > 15F) {
                k.blowTicks = EIGHT_BLOWS;
                k.events++;
            }
            k.held = 0;
        }
        if (k.blowTicks > 0) {
            k.blowTicks--;
            CarShape s = k.shape;
            for (int side = -1; side <= 1 && s != null; side += 2) {
                boolean pods = k.sheet.cooling == CarSheet.SIDE_PODS;
                float lx = pods ? side * s.maxX : side * s.maxX * dice.between(0.2F, 0.7F);
                float lz = pods ? s.minZ + (s.maxZ - s.minZ) * 0.36F : s.maxZ;
                race.toWorld(c, lx, s.minY + (s.maxY - s.minY) * 0.6F, lz, p);
                // out of the radiator: sideways from a side pod, ahead from a nose, then left behind
                race.toWorld(c, lx + (pods ? side * 5F : 0F), s.minY + (s.maxY - s.minY) * 0.55F,
                        lz + (pods ? 0F : 5F), q);
                particles.add(p[0], p[1], p[2], race.vx[c] * 0.7F + (q[0] - p[0]) + dice.between(-0.6F, 0.6F),
                        (q[1] - p[1]) - dice.between(0.2F, 0.8F),
                        race.vz[c] * 0.7F + (q[2] - p[2]) + dice.between(-0.6F, 0.6F), dice.between(9F, 14F), 4F, 15F,
                        0xe2e8ee, 0.28F, 0.1F, 0.05F, false);
                k.puffs++;
            }
        }
        k.burns = alive && race.nitrous[c];
        for (int pipe = 0; pipe < 2; pipe++) {
            k.flame[pipe] = k.burns ? EIGHT_FLAME * dice.between(0.82F, 1.12F) : 0F;
        }
    }

    /** MAX Revenge: under strong throttle its blower shakes on the engine. */
    private void revenge(RaceView race, int c, Quirk k, boolean alive) {
        float want = alive && race.up[c] ? 1F : 0F;
        k.shakeWas = k.shake;
        k.shake += (want - k.shake) * 0.35F;
        if (k.shake < 0.01F) {
            k.shake = 0F;
        }
    }

    /** M A S H E E N: its plow picks up dirt off the road, and tips it out when the nose goes down in the air. */
    private void masheen(RaceView race, int c, Quirk k, float speed) {
        int w0 = c * RaceView.WHEELS;
        for (int w = 0; w < 2; w++) {
            if (race.contact[w0 + w] && race.surface[w0 + w] != 0 && speed > 4F) {
                k.dirt = Math.min(1F, k.dirt + 0.5F / MASHEEN_FILLS);
                k.dirtRgb = shade(race.groundRgb[w0 + w], 0.82F);
            }
        }
        float nose = -(float) Math.toDegrees(Math.asin(Math.sin(race.zy[c] * DEG)));
        if (race.airTicks[c] >= 4 && nose >= MASHEEN_TIPS && k.dirt >= MASHEEN_LEAST && k.hasPart) {
            int n = k.dirt >= 0.6F ? 3 : 2;
            for (int i = 0; i < n; i++) {
                // out over the plow's lip, with the car's own speed and a little more
                race.toWorld(c, k.half * dice.between(-0.7F, 0.7F), k.partBottom - 4F, k.partFront, p);
                race.toWorld(c, 0F, 0F, 3F, q);
                int j = chunkNext;
                chunkNext = (chunkNext + 1) % CHUNKS;
                chunkLive[j] = true;
                chunkX[j] = p[0];
                chunkY[j] = p[1];
                chunkZ[j] = p[2];
                chunkVX[j] = race.vx[c] + (q[0] - race.x[c]) * dice.between(0.5F, 1.2F) + dice.between(-1.5F, 1.5F);
                chunkVY[j] = race.vy[c] + dice.between(0F, 1.5F);
                chunkVZ[j] = race.vz[c] + (q[2] - race.z[c]) * dice.between(0.5F, 1.2F) + dice.between(-1.5F, 1.5F);
                chunkSize[j] = k.half * dice.between(0.2F, 0.32F);
                chunkTurn[j] = dice.between(0F, 6.28F);
                chunkRgb[j] = shade(k.dirtRgb, dice.between(0.85F, 1.1F));
                tipped++;
            }
            k.dirt = 0F;
            k.events++;
        }
    }

    /** The chunks of dirt in the air fall, and stay where they land. */
    private void chunks() {
        for (int j = 0; j < CHUNKS; j++) {
            if (!chunkLive[j]) {
                continue;
            }
            chunkVY[j] += 2.1F;
            chunkX[j] += chunkVX[j];
            chunkY[j] += chunkVY[j];
            chunkZ[j] += chunkVZ[j];
            chunkTurn[j] += 0.6F;
            if (chunkY[j] >= GROUND) {
                chunkLive[j] = false;
                lying.drop(chunkX[j], GROUND, chunkZ[j], chunkSize[j] * 1.5F, chunkRgb[j], 5, chunkTurn[j]);
                landed++;
                for (int n = 0; n < 2; n++) {
                    particles.add(chunkX[j], GROUND - 3F, chunkZ[j], chunkVX[j] * 0.2F + dice.between(-1.5F, 1.5F),
                            -dice.between(0.5F, 1.5F), chunkVZ[j] * 0.2F + dice.between(-1.5F, 1.5F),
                            dice.between(12F, 18F), 8F, 26F, shade(chunkRgb[j], 1.25F), 0.3F, 0.1F, 0.02F, false);
                }
            }
        }
    }

    /** DR Monstaa: landing on one wheel flattens that tyre against the ground for a moment. */
    private void monstaa(RaceView race, int c, Quirk k, boolean landing) {
        int w0 = c * RaceView.WHEELS;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            k.squash[w] = k.squash[w] < 0.06F ? 0F : k.squash[w] * MONSTAA_BACK;
        }
        if (landing) {
            int touching = 0;
            int which = -1;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                if (race.contact[w0 + w]) {
                    touching++;
                    which = w;
                }
            }
            if (touching == 1) {
                k.squash[which] = 1F;
                k.events++;
            }
        }
        // which way is down, in the car's own numbers: the tyre flattens against the ground, not against the car
        double roll = race.xy[c] * DEG;
        double pitch = race.zy[c] * DEG;
        k.downX = (float) (Math.sin(roll) * Math.cos(pitch));
        k.downY = (float) (Math.cos(roll) * Math.cos(pitch));
        k.downZ = (float) -Math.sin(pitch);
    }

    /**
     * Wow Caninaro: something loose in the back slides to the outside of a
     * hard corner and knocks against the side; already there, it stays
     * silent, and the other way it is heard travelling all the way across.
     */
    private void caninaro(RaceView race, int c, Quirk k, float speed, float turned) {
        float across = turned * DEG * speed; // how hard the corner pushes, and which way
        if (race.grounded[c] && Math.abs(across) >= CANINARO_HARD) {
            float push = 0.06F * Math.min(2F, Math.abs(across) / CANINARO_HARD);
            k.slideSpeed += across > 0F ? push : -push;
        }
        k.slideSpeed *= 0.86F;
        boolean wasAtSide = Math.abs(k.slid) >= 1F;
        float before = k.slid;
        k.slid += k.slideSpeed;
        if (!k.sliding && Math.abs(k.slideSpeed) > 0.05F && Math.abs(k.slid) < 1F
                && (wasAtSide || Math.abs(before) < 0.2F)) {
            k.sliding = true;
            slides++;
            sound(c, SLIDE, 0.16F, 1.35F);
        }
        if (Math.abs(k.slid) >= 1F) {
            k.slid = k.slid > 0F ? 1F : -1F;
            if (!wasAtSide) {
                k.events++;
                knocks++;
                sound(c, KNOCK, Math.min(0.6F, 0.22F + Math.abs(k.slideSpeed)), 0.85F);
            }
            k.slideSpeed = 0F;
            k.sliding = false;
        } else if (Math.abs(k.slideSpeed) < 0.01F) {
            k.sliding = false;
        }
    }

    /** EL KING: a hard stop rocks its cab forward; letting the brake go lets the air out as the cab settles. */
    private void king(RaceView race, int c, Quirk k, float speed, boolean alive) {
        boolean brake = alive && (race.down[c] || race.handbrake[c]);
        float slowing = k.lastSpeed - speed;
        if (brake && race.grounded[c] && slowing >= KING_HARD) {
            k.rockSpeed += Math.min(2.2F, slowing * 0.55F);
            k.breath = KING_BREATH;
            k.events++;
        }
        if (k.wasBrake && !brake && k.breath > 0) {
            k.breath = 0;
            breaths++;
            sound(c, AIR, 0.5F, 1F);
            k.rockSpeed -= 0.5F; // the cab drops back as the air goes
        }
        if (k.breath > 0) {
            k.breath--;
        }
        k.wasBrake = brake;
        k.rockWas = k.rock;
        k.rockSpeed += -KING_SPRING * k.rock - KING_DAMPER * k.rockSpeed;
        k.rock += k.rockSpeed;
        if (k.rock > KING_ROCK) {
            k.rock = KING_ROCK;
            k.rockSpeed = Math.min(0F, k.rockSpeed);
        } else if (k.rock < -1F) {
            k.rock = -1F;
            k.rockSpeed = Math.max(0F, k.rockSpeed);
        }
        if (Math.abs(k.rock) < 0.02F && Math.abs(k.rockSpeed) < 0.02F) {
            k.rock = 0F;
            k.rockSpeed = 0F;
        }
    }

    private void sound(int car, String clip, float gain, float pitch) {
        if (Features.hooks().sound3d()) {
            Sound3D.carSound(car, clip, gain, pitch);
        }
    }

    /* ------------------------------------------- the cars' own polygons */

    /** Tornado Shark's dust lies on its body and thins as it comes off. */
    @Override
    public int color(int car, int plane, int rgb) {
        if (car < 0 || car >= cars) {
            return rgb;
        }
        Quirk k = quirks[car];
        if (k.kind != SHARK || k.dust <= 0F || k.shape == null || plane < 0 || plane >= k.shape.planes
                || k.shape.glass[plane] || k.shape.wheel[plane] >= 0 || k.shape.light[plane] != 0) {
            return rgb;
        }
        return mix(rgb, SHARK_DUST, k.dust * SHARK_COVER);
    }

    @Override
    public int lamp() {
        return -1;
    }

    /** DR Monstaa's squashed tyre: its polygons as they lie after the spin and the steering, flat against the ground. */
    @Override
    public boolean bend(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ) {
        if (car < 0 || car >= cars) {
            return false;
        }
        Quirk k = quirks[car];
        if (k.kind != MONSTAA || k.shape == null || plane < 0 || plane >= k.shape.planes) {
            return false;
        }
        int w = k.shape.wheel[plane];
        if (w < 0 || k.squash[w] <= 0F) {
            return false;
        }
        CarShape s = k.shape;
        float squash = k.squash[w];
        float hx = s.hubX[w];
        float hy = s.hubY[w];
        float hz = s.hubZ[w];
        float r = Math.max(1F, s.wheelRadius[w]);
        double spin = CarLooks.spin * DEG;
        float ss = (float) Math.sin(spin);
        float sc = (float) Math.cos(spin);
        double turn = s.steerX[w] != 0 ? CarLooks.turn * DEG : 0D;
        float ts = (float) Math.sin(turn);
        float tc = (float) Math.cos(turn);
        float sink = MONSTAA_SQUASH * r * squash;
        for (int v = 0; v < n; v++) {
            // as the game turns a wheel's polygon: spun about the axle, then steered
            float dy = y[v] - hy;
            float dz = z[v] - hz;
            float py = hy + dy * sc - dz * ss;
            float pz = hz + dy * ss + dz * sc;
            float px = x[v];
            if (s.steerX[w] != 0) {
                float ax = px - s.steerX[w];
                float az = pz - hz;
                px = s.steerX[w] + ax * tc - az * ts;
                pz = hz + ax * ts + az * tc;
            }
            // the car's weight comes down on the tyre: the wheel sinks, and what would be under the ground lies flat on it
            float rx = px - hx;
            float ry = py - hy;
            float rz = pz - hz;
            float below = rx * k.downX + ry * k.downY + rz * k.downZ;
            float flat = Math.min(below + sink, r) - below;
            // and the tyre bulges out to the sides near the ground
            float bulge = MONSTAA_BULGE * squash * Math.max(0F, Math.min(1F, (below - 0.2F * r) / (0.8F * r)));
            float sideX = rx - k.downX * below;
            float sideY = ry - k.downY * below;
            float sideZ = rz - k.downZ * below;
            outX[v] = Math.round(px + k.downX * flat + sideX * bulge);
            outY[v] = Math.round(py + k.downY * flat + sideY * bulge);
            outZ[v] = Math.round(pz + k.downZ * flat + sideZ * bulge);
        }
        CarLooks.baked = true;
        return true;
    }

    /** Kool Kat's body on its springs, MAX Revenge's blower shaking, EL KING's cab rocking. */
    @Override
    public boolean body(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ) {
        if (car < 0 || car >= cars) {
            return false;
        }
        Quirk k = quirks[car];
        if (k.kind != KAT && k.kind != REVENGE && k.kind != KING || k.shape == null || plane < 0
                || plane >= k.shape.planes) {
            return false;
        }
        switch (k.kind) {
            case KAT -> {
                float dip = k.dipWas + (k.dip - k.dipWas) * share;
                if (dip == 0F || k.shape.wheel[plane] >= 0) {
                    return false;
                }
                int down = Math.round(dip);
                for (int v = 0; v < n; v++) {
                    outX[v] = x[v];
                    outY[v] = y[v] + down;
                    outZ[v] = z[v];
                }
                return true;
            }
            case REVENGE -> {
                float shake = k.shakeWas + (k.shake - k.shakeWas) * share;
                if (shake <= 0F || !k.part[plane]) {
                    return false;
                }
                // two shakes of their own pace, so it never looks like a swing
                float t = tick + share;
                float reach = Math.max(1.2F, REVENGE_SHAKE * k.half * 2F) * shake;
                int dx = Math.round(reach * (float) Math.sin(t * 5.1F + car));
                int dy = Math.round(reach * 0.7F * (float) Math.sin(t * 7.3F + car * 2F));
                for (int v = 0; v < n; v++) {
                    outX[v] = x[v] + dx;
                    outY[v] = y[v] + dy;
                    outZ[v] = z[v];
                }
                return true;
            }
            default -> {
                float rock = k.rockWas + (k.rock - k.rockWas) * share;
                if (rock == 0F || !k.part[plane]) {
                    return false;
                }
                // about the cab's foot at the nose: its back lifts, its top goes forward
                float sin = (float) Math.sin(rock * DEG);
                float cos = (float) Math.cos(rock * DEG);
                for (int v = 0; v < n; v++) {
                    float dy = y[v] - k.pivotY;
                    float dz = z[v] - k.pivotZ;
                    outX[v] = x[v];
                    outY[v] = Math.round(k.pivotY + dy * cos + dz * sin);
                    outZ[v] = Math.round(k.pivotZ + dz * cos - dy * sin);
                }
                return true;
            }
        }
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        share = queue.share();
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        drawnFlames = 0;
        drawnDirt = 0;
        RaceView race = view;
        if (race == null) {
            return;
        }
        for (int c = 0; c < cars && c < MOST_CARS; c++) {
            Quirk k = quirks[c];
            if (k.kind == EIGHT && k.burns || k.kind == MASHEEN && k.dirt > 0.02F && k.hasPart) {
                queue.after(c, CAR_ITEM + c);
            }
        }
        for (int j = 0; j < CHUNKS; j++) {
            if (chunkLive[j]) {
                queue.near(chunkX[j], chunkY[j], chunkZ[j], CHUNK_ITEM + j);
            }
        }
        if (lying.count() > 0) {
            queue.ground(GROUND_ITEM);
        }
        particles.queue(queue, 0);
    }

    @Override
    public void draw(Canvas canvas, int item) {
        RaceView race = view;
        if (race == null) {
            return;
        }
        if (item < PARTICLES) {
            particles.draw(canvas, item);
        } else if (item < CHUNK_ITEM) {
            int c = item - CAR_ITEM;
            if (c < cars) {
                Quirk k = quirks[c];
                if (k.kind == EIGHT) {
                    flames(canvas, race, c, k);
                } else if (k.kind == MASHEEN) {
                    dirt(canvas, race, c, k);
                }
            }
        } else if (item < GROUND_ITEM) {
            chunk(canvas, item - CHUNK_ITEM);
        } else if (item == GROUND_ITEM) {
            for (int n = 0; n < lying.count(); n++) {
                lying.draw(canvas, lying.slot(n));
            }
        }
    }

    /** Mighty Eight's nitro: two long blue flames from its tail, each two teardrops square to each other, a pale core in each. */
    private void flames(Canvas canvas, RaceView race, int c, Quirk k) {
        CarShape s = k.shape;
        if (s == null) {
            return;
        }
        canvas.glow(1F);
        float ty = s.minY + (s.maxY - s.minY) * 0.78F;
        for (int pipe = 0; pipe < 2; pipe++) {
            float reach = k.flame[pipe];
            if (reach <= 0F) {
                continue;
            }
            float tx = (pipe == 0 ? -1F : 1F) * s.maxX * 0.45F;
            for (int layer = 0; layer < 2; layer++) {
                float length = reach * (layer == 1 ? 0.6F : 1F);
                float half = EIGHT_FLAME_WIDE * (layer == 1 ? 0.55F : 1F);
                canvas.color(canvas.fog(layer == 1 ? FLAME_CORE : FLAME, race.x[c], race.y[c], race.z[c]));
                for (int plane = 0; plane < 2; plane++) {
                    for (int v = 0; v < 5; v++) {
                        float along = DROP_ALONG[v] * length;
                        float across = DROP_ACROSS[v] * half;
                        cx[v] = tx + (plane == 0 ? across : 0F);
                        cy[v] = ty + (plane == 1 ? across : 0F);
                        cz[v] = s.minZ - along;
                    }
                    canvas.onCar(c, cx, cy, cz, 5);
                    drawnFlames++;
                }
            }
        }
        canvas.glow(0F);
    }

    private static final float[] DROP_ALONG = {0F, 0.45F, 1F, 0.45F, 0F};
    private static final float[] DROP_ACROSS = {-1F, -1.3F, 0F, 1.3F, 1F};

    /**
     * The wedge of dirt in M A S H E E N's plow: three strips from the
     * plow's back wall down to its lip, the middle one heaped highest.
     * Drawn only from where the plow's mouth is seen (from ahead or above).
     */
    private void dirt(Canvas canvas, RaceView race, int c, Quirk k) {
        float deep = k.partFront - k.partBack;
        float tall = k.partBottom - k.partTop;
        float midY = k.partBottom - tall * 0.25F;
        float midZ = k.partBack + deep * 0.55F;
        race.toWorld(c, 0F, midY, midZ, p);
        race.toWorld(c, 0F, midY - 0.8F, midZ + 0.6F, q);
        if ((eyeX - p[0]) * (q[0] - p[0]) + (eyeY - p[1]) * (q[1] - p[1]) + (eyeZ - p[2]) * (q[2] - p[2]) <= 0F) {
            return;
        }
        for (int strip = 0; strip < 3; strip++) {
            float x0 = -k.half * 0.92F + strip * k.half * 0.92F * 2F / 3F;
            float x1 = x0 + k.half * 0.92F * 2F / 3F;
            float high = tall * (strip == 1 ? 0.5F : 0.36F) * k.dirt;
            cx[0] = x0;
            cy[0] = k.partBottom - 1F - high;
            cz[0] = k.partBack + deep * 0.22F;
            cx[1] = x1;
            cy[1] = k.partBottom - 1F - high * (strip == 1 ? 1F : 0.9F);
            cz[1] = k.partBack + deep * 0.22F;
            cx[2] = x1;
            cy[2] = k.partBottom - 1F;
            cz[2] = k.partBack + deep * (0.45F + 0.5F * k.dirt);
            cx[3] = x0;
            cy[3] = k.partBottom - 1F;
            cz[3] = k.partBack + deep * (0.45F + 0.5F * k.dirt);
            canvas.color(canvas.fog(shade(k.dirtRgb, strip == 1 ? 1.06F : strip == 0 ? 0.9F : 0.84F), p[0], p[1],
                    p[2]));
            canvas.onCar(c, cx, cy, cz, 4);
            drawnDirt++;
        }
    }

    /** A chunk of dirt in the air: a chunky five-cornered piece, tumbling as it falls. */
    private void chunk(Canvas canvas, int j) {
        if (j < 0 || j >= CHUNKS || !chunkLive[j]) {
            return;
        }
        float r = chunkSize[j] / 2F;
        float tip = (float) Math.sin(chunkTurn[j]);
        for (int v = 0; v < 5; v++) {
            float a = chunkTurn[j] * 0.5F + v * (float) (2D * Math.PI / 5D);
            float reach = (v & 1) == 0 ? r : r * 0.75F;
            cx[v] = chunkX[j] + (float) Math.cos(a) * reach;
            cy[v] = chunkY[j] + (float) Math.sin(a) * reach * tip;
            cz[v] = chunkZ[j] + (float) Math.sin(a) * reach * (1F - Math.abs(tip) * 0.5F);
        }
        canvas.color(canvas.fog(chunkRgb[j], chunkX[j], chunkY[j], chunkZ[j]));
        canvas.moving(cx, cy, cz, chunkVX[j], chunkVY[j], chunkVZ[j], 5);
    }

    /* ------------------------------------------------------------- helpers */

    /** An angle in degrees brought into -180 to 180. */
    static float wrap(float degrees) {
        float a = degrees % 360F;
        return a > 180F ? a - 360F : a < -180F ? a + 360F : a;
    }

    static int mix(int a, int b, float share) {
        float t = Math.max(0F, Math.min(1F, share));
        int r = Math.round((a >> 16 & 0xff) + ((b >> 16 & 0xff) - (a >> 16 & 0xff)) * t);
        int g = Math.round((a >> 8 & 0xff) + ((b >> 8 & 0xff) - (a >> 8 & 0xff)) * t);
        int bl = Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * t);
        return r << 16 | g << 8 | bl;
    }

    static int shade(int color, float f) {
        int r = Math.max(0, Math.min(255, (int) ((color >> 16 & 0xff) * f)));
        int g = Math.max(0, Math.min(255, (int) ((color >> 8 & 0xff) * f)));
        int b = Math.max(0, Math.min(255, (int) ((color & 0xff) * f)));
        return r << 16 | g << 8 | b;
    }
}
