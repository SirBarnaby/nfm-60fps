package nfm.lit.features.powerups;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Explosions;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * Powerups: boxes along the track give nitrous, dynamite or a bomb, to the
 * computer cars too. The race half ({@link PowerupRace}) plays inside the
 * race tick; this half is the look, the sound and the messages, made after
 * real ticks only from what the race half did (its events) and what it
 * holds, which it copies, so a run-ahead tick is never seen or heard.
 * <p>
 * The look: boxes spinning over the road, the powerup a car holds floating
 * over its roof, the dynamite stick tumbling, the bomb lying on the road
 * with its fuse sparking, nitrous's flame (blue, red and bigger while two
 * burn at once; Damage and heat's own, when that is on), and the
 * explosions ({@link Explosions}): a fireball of dithered puffs, smoke that
 * lives with the wind and the cars, clods and sparks, a crater left in the
 * road and the game's own white flash for one near a player (the Explosion
 * flash switch).
 */
public final class PowerupsFeature implements Feature {

    public static final String ID = "powerups";
    /** The switch of the blast's white flash, under the powerups. */
    static final String FLASH = "flash";
    public static final PowerupsFeature FEATURE = new PowerupsFeature();

    /** The bomb's bang beside a dynamite stick's blast: its fireball, smoke and loudness. */
    private static final float BANG_SIZE = 0.5F;

    /* items in the draw queue */
    static final int BOX = 0;
    static final int STICK = 2_000;
    static final int ICON = 2_100;
    static final int FLAME = 2_200;
    static final int BOMB = 2_300;
    static final int BLASTS = 3_000;

    final PowerupRace race = new PowerupRace();
    private final PowerupDrawing drawing = new PowerupDrawing(this);
    final Explosions explosions = new Explosions(ID);
    private final Dice dice = new Dice(0L, ID);

    /* what the look shows, copied after each real tick */
    int tick;
    int cars;
    int[] boxWait = new int[0];
    int[] held = new int[0];
    /** Nitrous burning in each car: 0 none, 1 one, 2 two at once. */
    int[] burning = new int[0];
    boolean[] wasted = new boolean[0];
    final int[] stickOwner = new int[PowerupRace.STICKS];
    final float[] stickX = new float[PowerupRace.STICKS];
    final float[] stickY = new float[PowerupRace.STICKS];
    final float[] stickZ = new float[PowerupRace.STICKS];
    final float[] stickVX = new float[PowerupRace.STICKS];
    final float[] stickVY = new float[PowerupRace.STICKS];
    final float[] stickVZ = new float[PowerupRace.STICKS];
    final int[] stickAge = new int[PowerupRace.STICKS];
    final int[] bombOwner = new int[PowerupRace.BOMBS];
    final float[] bombX = new float[PowerupRace.BOMBS];
    final float[] bombY = new float[PowerupRace.BOMBS];
    final float[] bombZ = new float[PowerupRace.BOMBS];
    final float[] bombVY = new float[PowerupRace.BOMBS];
    final int[] bombAge = new int[PowerupRace.BOMBS];
    /** The race being looked at, for the cars' places and shapes. */
    RaceView view;
    /** Damage and heat draws the nitrous flame, when it is on. */
    boolean realismFlame;

    /** The events seen so far. */
    private int seen;
    /** Each player's hum playing in Classic sound (0 none), and their nitrous burning (0 none, else one of the four). */
    private final int[] classicHum = new int[2];
    private final int[] classicNitro = new int[2];
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
        Explosions.sounds();
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

    @Override
    public float grip(int car) {
        return race.grip(car);
    }

    /**
     * The nitrous burning in car {@code car}, for the flames (Damage and
     * heat's too) and the sound: 0 none, 1 one, 2 two at once. 0 without
     * powerups.
     */
    public int nitrousKind(int car) {
        if (!Features.racePlays(this) || car < 0 || car >= race.cars || race.nitrous[car] <= 0) {
            return 0;
        }
        return race.doubled[car] > 0 ? 2 : 1;
    }

    /** The hum of what car {@code car} holds (1 nitrous, 2 dynamite, 3 a bomb), for the 3D sound; 0 for nothing or without powerups. */
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
        explosions.start(r);
        seen = race.events;
        dice.reset(r.seed, ID);
        classicHum[0] = classicHum[1] = 0;
        classicNitro[0] = classicNitro[1] = 0;
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
                    race.eventZ[e], race.eventGround[e]);
        }
        classicHums(r);
        explosions.tick(r);
    }

    @Override
    public void raceEnded(RaceView r) {
        GameHooks hooks = Features.hooks();
        for (int p = 0; p < classicHum.length; p++) {
            if (classicHum[p] != 0) {
                hooks.classic(p, PowerupSounds.HUM + classicHum[p], GameHooks.STOP);
                classicHum[p] = 0;
            }
            if (classicNitro[p] != 0) {
                hooks.classic(p, PowerupSounds.NITROUS + classicNitro[p], GameHooks.STOP);
                classicNitro[p] = 0;
            }
        }
    }

    /**
     * Which of the four nitrous sounds car {@code c} makes now: 1 on the
     * ground, 2 in the air, 3 two at once on the ground, 4 two at once in
     * the air; 0 while none burns.
     */
    int nitrousSound(RaceView r, int c) {
        if (c >= burning.length || c >= r.cars || burning[c] == 0 || wasted[c]) {
            return 0;
        }
        return (burning[c] - 1) * 2 + (r.grounded[c] ? 1 : 2);
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
            burning = new int[race.cars];
            wasted = new boolean[race.cars];
        }
        for (int c = 0; c < race.cars; c++) {
            held[c] = race.held[c];
            burning[c] = race.nitrous[c] <= 0 ? 0 : race.doubled[c] > 0 ? 2 : 1;
            wasted[c] = c < r.cars && r.wasted[c];
        }
        System.arraycopy(race.bombOwner, 0, bombOwner, 0, PowerupRace.BOMBS);
        System.arraycopy(race.bombX, 0, bombX, 0, PowerupRace.BOMBS);
        System.arraycopy(race.bombY, 0, bombY, 0, PowerupRace.BOMBS);
        System.arraycopy(race.bombZ, 0, bombZ, 0, PowerupRace.BOMBS);
        System.arraycopy(race.bombVY, 0, bombVY, 0, PowerupRace.BOMBS);
        System.arraycopy(race.bombAge, 0, bombAge, 0, PowerupRace.BOMBS);
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
    private void play(RaceView r, int kind, int car, int what, float x, float y, float z, float ground) {
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
                // heard as long as it burns: the 3D sound's nitrous voice, Classic sound's loop (classicLoops)
                if (person) {
                    hooks.rumble(car, what == 2 ? 170F : 120F);
                }
            }
            case PowerupRace.THROWN -> {
                sound(hooks, in3d, car, person, PowerupSounds.THROW, 0.9F);
                for (int k = 0; k < 6; k++) {
                    explosions.particles.add(x, y, z, dice.between(-4F, 4F), dice.between(-6F, -2F),
                            dice.between(-4F, 4F), 8F, 3F, 2F, 0xFFD070, 1F, 0.1F, -0.6F, true);
                }
            }
            case PowerupRace.DROPPED -> sound(hooks, in3d, car, person, PowerupSounds.DROP, 0.9F);
            case PowerupRace.BLAST -> explosions.explode(r, x, y, z, ground, what == 1, 1F, Explosions.BLAST,
                    FeatureSettings.on(FLASH));
            case PowerupRace.BANG -> {
                explosions.explode(r, x, y - 20F, z, ground, false, BANG_SIZE, Explosions.BANG,
                        FeatureSettings.on(FLASH));
                if (person) {
                    hooks.rumble(car, 220F);
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
            case PowerupRace.BOMB -> "Bomb!  " + hooks.useKey(car) + " to drop it behind you";
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

    /** A player's hum in Classic sound follows what their car holds, and their nitrous which of its four sounds it is. */
    private void classicHums(RaceView r) {
        GameHooks hooks = Features.hooks();
        for (int p = 0; p < classicHum.length; p++) {
            boolean theirs = p < r.humans && p < race.cars && !hooks.sound3d() && !wasted[p];
            classicHum[p] = classicLoop(hooks, p, PowerupSounds.HUM, classicHum[p], theirs ? race.held[p] : 0);
            classicNitro[p] = classicLoop(hooks, p, PowerupSounds.NITROUS, classicNitro[p],
                    theirs ? nitrousSound(r, p) : 0);
        }
    }

    /** Player {@code p}'s loop {@code clip + want} instead of {@code clip + playing} (0: none); returns {@code want}. */
    private static int classicLoop(GameHooks hooks, int p, String clip, int playing, int want) {
        if (want != playing) {
            if (playing != 0) {
                hooks.classic(p, clip + playing, GameHooks.STOP);
            }
            if (want != 0) {
                hooks.classic(p, clip + want, GameHooks.LOOP);
            }
        }
        return want;
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
        for (int b = 0; b < PowerupRace.BOMBS; b++) {
            if (bombOwner[b] >= 0) {
                queue.at(bombX[b], bombY[b], bombZ[b], BOMB + b);
            }
        }
        for (int c = 0; c < cars; c++) {
            if (wasted[c]) {
                continue;
            }
            if (held[c] != PowerupRace.NONE) {
                queue.after(c, ICON + c);
            }
            if (burning[c] > 0 && !realismFlame) {
                queue.after(c, FLAME + c);
            }
        }
        explosions.queue(queue, BLASTS);
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item >= BLASTS) {
            explosions.draw(canvas, item - BLASTS);
        } else if (item >= BOMB) {
            drawing.bomb(canvas, item - BOMB);
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
