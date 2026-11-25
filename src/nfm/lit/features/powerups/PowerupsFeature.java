package nfm.lit.features.powerups;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * Powerups: boxes along the track give nitrous, dynamite or a magnet, to
 * the computer cars too. The race half ({@link PowerupRace}) plays inside
 * the race tick; this half is the look, the sound and the messages, made
 * after real ticks only from what the race half did (its events) and what
 * it holds, which it copies, so a run-ahead tick is never seen or heard.
 * <p>
 * The look: boxes spinning over the road, the powerup a car holds floating
 * over its roof, the dynamite stick tumbling, a fireball of dithered puffs,
 * a smoke cloud that stays and parts as cars drive through it, clods and
 * sparks, a crater left in the road, the magnet's arc, nitrous's blue flame
 * (Damage and heat's, when that is on) and the game's own white flash for a
 * blast near a player (the Explosion flash switch).
 */
public final class PowerupsFeature implements Feature {

    public static final String ID = "powerups";
    /** The switch of the blast's white flash, under the powerups. */
    static final String FLASH = "flash";
    public static final PowerupsFeature FEATURE = new PowerupsFeature();

    /** The fireball's colours, from its white-hot middle out. */
    private static final int[] FIRE = {0xFFE48A, 0xFFB43C, 0xFF7A1E, 0xE8501A};
    /** A blast this near a player's car flashes their screen and is heard in Classic sound. */
    private static final float NEAR_PLAYER = 6000F;

    /* items in the draw queue */
    static final int BOX = 0;
    static final int STICK = 2_000;
    static final int ICON = 2_100;
    static final int FLAME = 2_200;
    static final int ARC = 2_300;
    static final int CRATER = 2_400;
    static final int PARTICLE = 3_000;

    final PowerupRace race = new PowerupRace();
    private final PowerupDrawing drawing = new PowerupDrawing(this);
    final Particles particles = new Particles(480);
    private final Dice dice = new Dice(0L, ID);
    private final float[] at = new float[3];

    /* what the look shows, copied after each real tick */
    int tick;
    int cars;
    int[] boxWait = new int[0];
    int[] held = new int[0];
    boolean[] burning = new boolean[0];
    boolean[] wasted = new boolean[0];
    int[] pulling = new int[0];
    final int[] stickOwner = new int[PowerupRace.STICKS];
    final float[] stickX = new float[PowerupRace.STICKS];
    final float[] stickY = new float[PowerupRace.STICKS];
    final float[] stickZ = new float[PowerupRace.STICKS];
    final float[] stickVX = new float[PowerupRace.STICKS];
    final float[] stickVY = new float[PowerupRace.STICKS];
    final float[] stickVZ = new float[PowerupRace.STICKS];
    final int[] stickAge = new int[PowerupRace.STICKS];
    /** The race being looked at, for the cars' places and shapes. */
    RaceView view;
    /** Damage and heat draws the nitrous flame, when it is on. */
    boolean realismFlame;

    /* craters, kept for the race */
    static final int CRATERS = 16;
    final float[] craterX = new float[CRATERS];
    final float[] craterY = new float[CRATERS];
    final float[] craterZ = new float[CRATERS];
    final float[] craterSize = new float[CRATERS];
    int craters;

    /** The events seen so far. */
    private int seen;
    /** Each player's hum playing in Classic sound (0 none). */
    private final int[] classicHum = new int[2];
    /** The eye the frame is queued from. */
    float eyeX;
    float eyeY;
    float eyeZ;

    private PowerupsFeature() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean changesRaces() {
        return true;
    }

    /* ----------------------------------------------------------- the race */

    @Override
    public void raceSetUp(RaceTick r, long seed) {
        race.setUp(r, seed);
        PowerupSounds.make(); // made while the stage loads, not in the first frame of the race
    }

    @Override
    public void raceTick(RaceTick r) {
        race.tick(r);
    }

    @Override
    public void sync(RaceInts state) {
        race.sync(state);
    }

    @Override
    public float boost(int car) {
        return race.boost(car);
    }

    /** True while car {@code car}'s nitrous burns, for the flames (Damage and heat's too); false without powerups. */
    public boolean nitrous(int car) {
        return Features.racePlays(this) && car >= 0 && car < race.cars && race.nitrous[car] > 0;
    }

    /** The hum of what car {@code car} holds (1 to 3), for the 3D sound; 0 for nothing or without powerups. */
    public int hum(int car) {
        if (!Features.racePlays(this) || car < 0 || car >= race.cars) {
            return 0;
        }
        return race.held[car];
    }

    /* ----------------------------------------------------------- the look */

    @Override
    public void raceStarted(RaceView r) {
        view = r;
        particles.clear();
        craters = 0;
        seen = race.events;
        dice.reset(r.seed, ID);
        classicHum[0] = classicHum[1] = 0;
        copy(r);
    }

    @Override
    public void afterTick(RaceView r) {
        view = r;
        copy(r);
        int newest = race.events;
        if (newest - seen > PowerupRace.EVENTS) {
            seen = newest - PowerupRace.EVENTS; // more than the ring holds in a tick: the last ones
        }
        while (seen < newest) {
            int e = seen++ % PowerupRace.EVENTS;
            play(r, race.eventKind[e], race.eventCar[e], race.eventWhat[e], race.eventX[e], race.eventY[e],
                    race.eventZ[e]);
        }
        classicHums(r);
        smokeParts(r);
        particles.tick(0.4F, 0.2F);
    }

    @Override
    public void raceEnded(RaceView r) {
        GameHooks hooks = Features.hooks();
        for (int p = 0; p < classicHum.length; p++) {
            if (classicHum[p] != 0) {
                hooks.classic(p, PowerupSounds.HUM + classicHum[p], GameHooks.STOP);
                classicHum[p] = 0;
            }
        }
    }

    /** What the race half holds now, for the frames until the next real tick. */
    private void copy(RaceView r) {
        tick = r.tick;
        cars = Math.min(r.cars, race.cars);
        if (boxWait.length != race.boxes) {
            boxWait = new int[race.boxes];
        }
        System.arraycopy(race.boxWait, 0, boxWait, 0, race.boxes);
        if (held.length != race.cars) {
            held = new int[race.cars];
            burning = new boolean[race.cars];
            wasted = new boolean[race.cars];
            pulling = new int[race.cars];
        }
        for (int c = 0; c < race.cars; c++) {
            held[c] = race.held[c];
            burning[c] = race.nitrous[c] > 0;
            wasted[c] = c < r.cars && r.wasted[c];
            pulling[c] = race.magnet[c] > 0 ? race.magnetTarget[c] : -1;
        }
        System.arraycopy(race.stickOwner, 0, stickOwner, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickX, 0, stickX, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickY, 0, stickY, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickZ, 0, stickZ, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickVX, 0, stickVX, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickVY, 0, stickVY, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickVZ, 0, stickVZ, 0, PowerupRace.STICKS);
        System.arraycopy(race.stickAge, 0, stickAge, 0, PowerupRace.STICKS);
        realismFlame = Features.active("realism");
    }

    /** Shows and plays one event of the race half. */
    private void play(RaceView r, int kind, int car, int what, float x, float y, float z) {
        GameHooks hooks = Features.hooks();
        boolean person = car >= 0 && car < r.humans;
        boolean in3d = hooks.sound3d();
        switch (kind) {
            case PowerupRace.PICKUP -> {
                sound(hooks, in3d, car, person, PowerupSounds.PICKUP, 1F);
                if (person) {
                    hooks.say(car, pickupText(hooks, car, what));
                    hooks.rumble(car, 60F);
                }
            }
            case PowerupRace.USED -> {
                String clip = what == PowerupRace.NITROUS ? PowerupSounds.NITROUS : PowerupSounds.MAGNET;
                sound(hooks, in3d, car, person, clip, 1F);
                if (person) {
                    hooks.rumble(car, what == PowerupRace.NITROUS ? 120F : 80F);
                }
            }
            case PowerupRace.THROWN -> {
                sound(hooks, in3d, car, person, PowerupSounds.THROW, 0.9F);
                for (int k = 0; k < 6; k++) {
                    particles.add(x, y, z, dice.between(-4F, 4F), dice.between(-6F, -2F), dice.between(-4F, 4F), 8F,
                            3F, 2F, 0xFFD070, 1F, 0.1F, -0.6F, true);
                }
            }
            case PowerupRace.BLAST -> blast(r, hooks, in3d, x, y, z, what == 1);
            case PowerupRace.NOBODY_AHEAD -> {
                if (person) {
                    hooks.say(car, "No car ahead to pull toward!");
                }
            }
            default -> {
            }
        }
    }

    private static String pickupText(GameHooks hooks, int car, int what) {
        return switch (what) {
            case PowerupRace.NITROUS -> "Nitrous!  " + hooks.useKey(car) + " to use";
            case PowerupRace.DYNAMITE -> "Dynamite!  Thrown in 2 seconds";
            case PowerupRace.MAGNET -> "Magnet!  " + hooks.useKey(car) + " to use";
            default -> "";
        };
    }

    /** A car's sound: at the car in 3D, a player's own in Classic. */
    private static void sound(GameHooks hooks, boolean in3d, int car, boolean person, String clip, float gain) {
        if (in3d) {
            Sound3D.carSound(car, clip, gain, 1F);
        } else if (person) {
            hooks.classic(car, clip, GameHooks.PLAY);
        }
    }

    /** A stick blew up: the fireball, the smoke, clods and sparks, a crater, the sound, the flash and the rumble. */
    private void blast(RaceView r, GameHooks hooks, boolean in3d, float x, float y, float z, boolean dents) {
        float nearest = Float.MAX_VALUE;
        int nearestPlayer = 0;
        for (int p = 0; p < r.humans && p < r.cars; p++) {
            float dx = r.x[p] - x;
            float dy = r.y[p] - y;
            float dz = r.z[p] - z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < nearest) {
                nearest = d;
                nearestPlayer = p;
            }
            if (d < PowerupRace.BLAST_RADIUS * 1.6F) {
                hooks.rumble(p, 250F * (1F - d / (PowerupRace.BLAST_RADIUS * 1.6F)));
            }
        }
        if (in3d) {
            Sound3D.pointSound(x, y, z, PowerupSounds.BLAST, 1.4F, dice.between(0.94F, 1.06F));
        } else if (nearest < NEAR_PLAYER) {
            hooks.classic(nearestPlayer, PowerupSounds.BLAST, GameHooks.PLAY);
        }
        if (nearest < NEAR_PLAYER && FeatureSettings.on(FLASH)) {
            hooks.flash();
        }
        float ground = dents ? y : y + 60F;
        fireball(x, y - 30F, z);
        smoke(x, y - 20F, z);
        debris(x, y - 10F, z, ground);
        if (dents) {
            int i = craters % CRATERS;
            craterX[i] = x;
            craterY[i] = y;
            craterZ[i] = z;
            craterSize[i] = dice.between(0.85F, 1.15F);
            craters++;
        }
    }

    /** Glowing orange and yellow puffs that swell fast and are gone in about 0.6 s. */
    private void fireball(float x, float y, float z) {
        for (int k = 0; k < 16; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(3F, 11F);
            particles.add(x + dice.between(-30F, 30F), y + dice.between(-30F, 20F), z + dice.between(-30F, 30F),
                    (float) Math.cos(a) * out, dice.between(-9F, -2F), (float) Math.sin(a) * out,
                    dice.between(9F, 13F), dice.between(30F, 55F), dice.between(130F, 190F), FIRE[k % FIRE.length],
                    0.95F, 0.22F, 0.5F, false);
        }
    }

    /** Smoke that rises, spreads and stays about eight seconds. */
    private void smoke(float x, float y, float z) {
        for (int k = 0; k < 40; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(1.5F, 9F);
            int grey = (int) dice.between(52F, 96F);
            int rgb = grey << 16 | (grey - 3) << 8 | (grey - 6);
            particles.add(x + dice.between(-60F, 60F), y + dice.between(-40F, 30F), z + dice.between(-60F, 60F),
                    (float) Math.cos(a) * out, dice.between(-4F, -0.5F), (float) Math.sin(a) * out,
                    dice.between(120F, 170F), dice.between(50F, 90F), dice.between(220F, 330F), rgb, 0.8F, 0.07F,
                    0.025F, false);
        }
    }

    /** Clods of earth thrown up and falling back, and sparks. */
    private void debris(float x, float y, float z, float ground) {
        for (int k = 0; k < 16; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(5F, 15F);
            int clod = particles.add(x, y, z, (float) Math.cos(a) * out, dice.between(-26F, -11F),
                    (float) Math.sin(a) * out, dice.between(30F, 45F), dice.between(6F, 11F), 6F, k % 3 == 0
                            ? 0x3A2E22 : 0x5C4A34, 1F, 0.02F, -1.3F, false);
            particles.floor(clod, ground);
        }
        for (int k = 0; k < 12; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(18F, 36F);
            particles.add(x, y, z, (float) Math.cos(a) * out, dice.between(-24F, -6F), (float) Math.sin(a) * out,
                    dice.between(7F, 12F), 3F, 2F, 0xFFD27A, 1F, 0.05F, -0.9F, true);
        }
    }

    /** A car driving through the smoke shoves it along and aside. */
    private void smokeParts(RaceView r) {
        if (particles.count() == 0) {
            return;
        }
        float[] p = at;
        for (int i = 0; i < particles.capacity(); i++) {
            if (particles.isSpark(i) || !particles.position(i, p)) {
                continue;
            }
            for (int c = 0; c < r.cars; c++) {
                float dx = p[0] - r.x[c];
                float dy = p[1] - r.y[c];
                float dz = p[2] - r.z[c];
                float reach = r.radius[c] + 80F;
                float d2 = dx * dx + dz * dz;
                if (d2 > reach * reach || Math.abs(dy) > 200F) {
                    continue;
                }
                float d = (float) Math.sqrt(d2) + 1F;
                float shove = 3F * (1F - d / reach);
                particles.push(i, r.vx[c] * 0.12F + dx / d * shove, -0.4F, r.vz[c] * 0.12F + dz / d * shove);
            }
        }
    }

    /** A player's hum in Classic sound follows what their car holds. */
    private void classicHums(RaceView r) {
        GameHooks hooks = Features.hooks();
        for (int p = 0; p < classicHum.length; p++) {
            int want = p < r.humans && p < race.cars && !hooks.sound3d() && !wasted[p] ? race.held[p] : 0;
            if (want == classicHum[p]) {
                continue;
            }
            if (classicHum[p] != 0) {
                hooks.classic(p, PowerupSounds.HUM + classicHum[p], GameHooks.STOP);
            }
            if (want != 0) {
                hooks.classic(p, PowerupSounds.HUM + want, GameHooks.LOOP);
            }
            classicHum[p] = want;
        }
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        for (int b = 0; b < boxWait.length && b < race.boxes; b++) {
            if (boxWait[b] == 0) {
                queue.at(race.boxX[b], race.boxY[b], race.boxZ[b], BOX + b);
            }
        }
        for (int s = 0; s < PowerupRace.STICKS; s++) {
            if (stickOwner[s] >= 0) {
                queue.at(stickX[s], stickY[s], stickZ[s], STICK + s);
            }
        }
        for (int c = 0; c < cars; c++) {
            if (wasted[c]) {
                continue;
            }
            if (held[c] != PowerupRace.NONE) {
                queue.after(c, ICON + c);
            }
            if (burning[c] && !realismFlame) {
                queue.after(c, FLAME + c);
            }
            int t = pulling[c];
            if (t >= 0 && t < cars) {
                queue.at((view.x[c] + view.x[t]) / 2F, (view.y[c] + view.y[t]) / 2F, (view.z[c] + view.z[t]) / 2F,
                        ARC + c);
            }
        }
        for (int i = 0; i < Math.min(craters, CRATERS); i++) {
            queue.ground(CRATER + i);
        }
        particles.queue(queue, PARTICLE);
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item >= PARTICLE) {
            particles.draw(canvas, item - PARTICLE);
        } else if (item >= CRATER) {
            drawing.crater(canvas, item - CRATER);
        } else if (item >= ARC) {
            drawing.arc(canvas, item - ARC);
        } else if (item >= FLAME) {
            drawing.flame(canvas, item - FLAME);
        } else if (item >= ICON) {
            drawing.icon(canvas, item - ICON);
        } else if (item >= STICK) {
            drawing.stick(canvas, item - STICK);
        } else {
            drawing.box(canvas, item - BOX);
        }
    }
}
