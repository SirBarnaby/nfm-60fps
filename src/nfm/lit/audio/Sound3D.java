package nfm.lit.audio;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

import fallk.logmaster.HLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EnumerateAllExt;
import org.lwjgl.openal.EXTDisconnect;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.SOFTDeferredUpdates;
import org.lwjgl.openal.SOFTDirectChannels;
import org.lwjgl.openal.SOFTHRTF;
import org.lwjgl.openal.SOFTLoopback;
import org.lwjgl.openal.SOFTOutputLimiter;
import org.lwjgl.openal.SOFTOutputMode;
import org.lwjgl.openal.SOFTReopenDevice;
import org.lwjgl.openal.SOFTSourceSpatialize;
import org.lwjgl.openal.SOFTSystemEventProc;
import org.lwjgl.openal.SOFTSystemEvents;
import org.lwjgl.system.MemoryUtil;

/**
 * The new sound: every car is heard where it is, through OpenAL Soft.
 * <p>
 * Each car's engine plays all five of its original recordings at once and
 * mixes them by its speed, so revs rise and fall smoothly instead of
 * stepping: within each of the game's three speed bands the three rising
 * recordings cross-fade and bend a couple of semitones, the idle recording
 * is the engine off the throttle or in the air, and the scream is
 * wheelspin. Crashes and skids come from every car, not only the player's,
 * placed at the car and as loud as the hit. Sounds sit around the listener
 * as the camera sees them, with HRTF on headphones (OpenAL Soft turns it on
 * when Windows says headphones are plugged in), quieter and duller with
 * distance, and bent by Doppler as cars pass. The menus, the countdown and
 * the player's own wind and wreck sounds play as the game asks, centred.
 * <p>
 * The game never waits for it: it posts commands and, once a tick, where
 * the cars and the camera are; a thread of its own turns them into OpenAL
 * calls a hundred times a second, carrying positions on between ticks.
 * OpenAL mixes on its own native thread, so a garbage collection in Java can
 * delay a change but never cause a dropout. It follows Windows' default
 * output device when it changes (headphones plugged in) and picks a device
 * up again after one is unplugged.
 * <p>
 * Nothing here touches the race: it only reads what the game publishes, and
 * its own random choices (a crash's pitch) come from its own generator.
 * <p>
 * A second speaker, when one is chosen, takes the race's announcements off
 * the main mix, as a PS5 controller's speaker does: the countdown,
 * checkpoints, repairs, power-ups, wrecking a car, and the menus' clicks.
 * It is another device with a context of its own, driven by the same sound
 * thread, which makes each device's context current in turn.
 */
public final class Sound3D implements Runnable {

    /** One car as the last tick left it. */
    public static final class Car {
        /** Where it is, in the world's coordinates (y down). */
        public float x;
        public float y;
        public float z;
        /** Its speed, as the game counts it: about the world units it moves in a tick. */
        public float speed;
        /** Which of the five engine sounds the car has. */
        public int engine;
        /** The speeds where the engine's three bands end (the car's Stat.swits). */
        public int[] bands;
        /** The engine is heard: the race is on, the countdown has started, the car is not wrecked. */
        public boolean running;
        public boolean throttle;
        /** Wheelspin, a slide or upside down: the engine screams. */
        public boolean spinning;
        public boolean grounded;
        /** Full power (power 98), where the game plays the top recordings. */
        public boolean fullPower;
        /**
         * The rebuilt engine sound plays it (the enginesound extra): its
         * recordings one engine cycle at a time, the engine and intake from
         * its front, the exhaust from its back, a skid from each wheel that
         * slides. False: the five recordings as before.
         */
        public boolean rebuilt;
        /** How it is turned, degrees, as its model is (ContO's xz, zy and xy), for the rebuilt sound's places on it. */
        public int yaw;
        public int pitch;
        public int roll;
        /** How hard each wheel skids, 0 to 1, for the rebuilt sound (plain fields: nothing more is made a tick). */
        public float skid0;
        public float skid1;
        public float skid2;
        public float skid3;
        /** A bit for each wheel on loose ground, whose skid is the dust skid. */
        public int loose;
        /** The hum of the powerup the car holds (the powerups extra): 1 to 3, 0 for none. */
        public int hum;
        /**
         * Nitrous burning in the car (the powerups extra), one of four
         * sounds: 1 on the ground, 2 in the air, 3 two at once on the
         * ground, 4 two at once in the air; 0 for none.
         */
        public int nitro;
        /**
         * Damage heard under the engine (the damage extra): how loud its
         * broken exhaust blats and how harsh it runs for want of oil, each
         * 0 for none to about 1, and the pitch both are played at, the
         * car's own (about 1), before the revs.
         */
        public float rasp;
        public float harsh;
        public float raspPitch = 1F;

        float skid(int wheel) {
            return switch (wheel) {
                case 0 -> skid0;
                case 1 -> skid1;
                case 2 -> skid2;
                default -> skid3;
            };
        }
    }

    /** Where everything was at the end of a tick. */
    public static final class Moment {
        public long nanos;
        /** How long a tick lasts now. */
        public long tickNanos;
        /** The camera: where it is (world coordinates) and how it turns, in degrees (Medium.xz, Medium.zy). */
        public float cameraX;
        public float cameraY;
        public float cameraZ;
        public int yaw;
        public int pitch;
        public Car[] cars;
        /** Which car is the player's. */
        public int player;
        /** Sound effects are switched off in the game (Control.mutes). */
        public boolean muted;
    }

    /* ------------------------------------------------------------- tuning */

    /** World units in a metre: a car is about 4.5 metres and 270 units long. */
    private static final float UNITS_PER_METRE = 60F;
    /** Within this distance a car is heard at full volume: the chase camera's distance from the player. */
    private static final float FULL_VOLUME_DISTANCE = 900F;
    private static final float MAX_DISTANCE = 30_000F;
    /** Doppler below the physical, as most racing games have it. */
    private static final float DOPPLER = 0.6F;
    /** Far cars lose their treble faster than in still air, so distance is heard. */
    private static final float AIR_ABSORPTION = 3F;
    /*
     * Placing a sound costs loudness: through HRTF a source in front comes out
     * at about a third of its level in each ear (measured on a test render),
     * panned for speakers at 0.71. The listener makes that up, so a car at the
     * chase camera's distance is as loud as Classic plays it, and the clips
     * heard as they are (menus, countdown) are turned down by as much, to stay
     * as loud as Classic too. The output limiter keeps a pile-up from clipping.
     */
    private static final float HRTF_MAKEUP = 3F;
    private static final float PANNED_MAKEUP = 1.41F;
    /** Semitones a recording bends across a third of a band. */
    private static final float BEND_SEMITONES = 4F;
    /** Beyond this, an engine plays only its loudest recording. */
    private static final float ENGINE_DETAIL_DISTANCE = 6_000F;
    /** How quickly an engine's mix follows the car, in seconds. */
    private static final float ENGINE_SMOOTHING = 0.04F;
    private static final float THROTTLE_SMOOTHING = 0.09F;
    private static final int EFFECT_VOICES = 24;
    private static final long UPDATE_NANOS = 10_000_000L;
    /** A car that moved further than this in one tick was put somewhere else, not driven there. */
    private static final float JUMP = 2_000F;
    private static final int RENDER_RATE = 48_000;
    /** Cars Steam Audio traces at once: every car of a race this size, the nearest of a bigger one. */
    private static final int MAX_CARS = 8;
    /** The most cars a race has (a custom game's 32 computer cars and more): each eases its own occlusion. */
    private static final int ALL_CARS = 51;
    /** Occlusion is traced every this many updates (30 ms) and eased in over this long. */
    private static final int TRACE_EVERY = 3;
    private static final float OCCLUSION_SMOOTHING = 0.12F;
    /** How quickly the reverb follows the place, in seconds: moving from a canyon into the open. */
    private static final float REVERB_SMOOTHING = 1F;
    /** The reverb's level where the track closes in all round; open ground stays dry. */
    private static final float REVERB_LEVEL = 0.6F;
    /*
     * A car's own surroundings throw its sound back, wherever the listener
     * is: an echo of each car by how much of the track stands around it
     * (walls, a ramp, a half-pipe within 40 m; a wall along one side is about
     * a quarter). The first tap comes back after a wall some 19 m away would
     * return it, the second a little later from the other side; it repeats
     * faintly and loses treble as a wall takes it. In the open there is none.
     */
    private static final float ECHO_LEVEL = 0.5F;
    private static final float ECHO_DELAY = 0.11F;
    private static final float ECHO_SECOND = 0.07F;
    private static final float ECHO_FEEDBACK = 0.18F;
    private static final float ECHO_DAMPING = 0.6F;
    /** How much of the echo a car sends per share of the track around it, up to all of it. */
    private static final float ECHO_PER_ENCLOSURE = 2F;
    private static final float ECHO_SMOOTHING = 0.35F;
    /*
     * A big crash heard at full volume pulls the engines down by this much
     * (about 5 dB) at once and lets them back over half a second, so the hit
     * lands; a crash far off ducks them less, a tap not at all.
     */
    private static final float DUCK_DEPTH = 0.45F;
    private static final float DUCK_RELEASE = 0.5F;
    /** -Dnfm.sound.debug prints each engine's state once a second. */
    private static final boolean DEBUG = Boolean.getBoolean("nfm.sound.debug");

    /* -------------------------------------------------- the game's side */

    private static volatile Sound3D instance;
    private static volatile boolean wanted;
    private static volatile String status = "off";

    /** What the game asks for, in order. */
    private record Command(int kind, String clip, int car, float a, float b, StreamMusic music, float x, float y,
            float z) {
        Command(int kind, String clip, int car, float a, float b) {
            this(kind, clip, car, a, b, null, 0F, 0F, 0F);
        }

        Command(int kind, String clip, int car, float a, float b, StreamMusic music) {
            this(kind, clip, car, a, b, music, 0F, 0F, 0F);
        }
    }

    private static final int PLAY = 0;
    private static final int LOOP = 1;
    private static final int STOP = 2;
    private static final int CRASH = 3;
    private static final int SKID = 4;
    private static final int STOP_ALL = 5;
    static final int MUSIC_PLAY = 6;
    static final int MUSIC_PAUSE = 7;
    static final int MUSIC_RESUME = 8;
    static final int MUSIC_UNLOAD = 9;
    /** A sound that is not the original's, for the second speaker only (see {@link #event}). */
    private static final int EVENT = 10;
    /** An extra's own clip, played at a car (see {@link #carSound}). */
    private static final int EXTRA = 11;
    /** An extra's own clip, played at a place in the world (see {@link #pointSound}). */
    private static final int EXTRA_AT = 12;
    /** An exhaust pop of a car (see {@link #carPop}). */
    private static final int POP = 13;

    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private volatile Moment latest;
    /**
     * Rendering a race check to a file, the sound runs in step with the
     * race instead of the clock: the game's commands and ticks queue up in
     * order, and each tick is mixed for exactly its own length, so the same
     * race always gives the same samples, however fast it was played.
     */
    private final boolean lockstep;
    private final ConcurrentLinkedQueue<Object> steps = new ConcurrentLinkedQueue<>();
    /** The sound's own time: the clock, or in lockstep the race's ticks added up. */
    private long clock;
    private final SoundManager clips;
    /** With a file, the sound is mixed into it instead of a device: for tests, never heard. */
    private final String renderTo;
    private volatile boolean running = true;
    private volatile boolean reopenWanted;
    private Thread thread;

    private Sound3D(SoundManager clips, String renderTo) {
        this.clips = clips;
        this.renderTo = renderTo;
        this.lockstep = renderTo != null
                && (System.getProperty("nfm.verify") != null || Boolean.getBoolean("nfm.sound.lockstep"));
        if (lockstep) {
            random.setSeed(1L);
            grainRandom.setSeed(2L);
        }
    }

    /** Queues a command for the sound thread, in order with the ticks when in lockstep. */
    private void post(Command c) {
        if (lockstep) {
            steps.add(c);
        } else {
            commands.add(c);
        }
    }

    /**
     * Switches between the new sound (true) and Classic (false). The new
     * sound starts with the next {@link #start}; Classic stops it.
     */
    public static void setWanted(boolean on) {
        wanted = on;
        if (!on) {
            shutdown();
            StreamMusic.handToClassic();
        }
    }

    public static boolean isWanted() {
        return wanted;
    }

    /*
     * The mix's buses, each with a volume of its own from the console
     * ("volume"), saved: everything, the engines, the effects (crashes,
     * skids, the wreck, the repair, the wind), the music, and the interface
     * (countdown, checkpoints, menus). They are the new sound's: Classic
     * plays at its own levels.
     */
    public static final int BUS_MASTER = 0;
    public static final int BUS_ENGINES = 1;
    public static final int BUS_EFFECTS = 2;
    public static final int BUS_MUSIC = 3;
    public static final int BUS_INTERFACE = 4;
    public static final String[] BUS_NAMES = {"master", "engines", "effects", "music", "interface"};
    private static final float[] busLevels = {1F, 1F, 1F, 1F, 1F};
    private static volatile int busVersion;
    private static volatile boolean mono;

    /* the track's effects, each of which can be switched off: muffling behind the track, the way round, the reverb */
    private static volatile boolean occlusionOn = true;
    private static volatile boolean pathsOn = true;
    private static volatile boolean reverbOn = true;
    /**
     * HDR audio's range in dB, 0 for off: the race's everyday sounds (the
     * engines, skids, wind, music and menus) play this much below full
     * scale, and the loud moments (crashes, a wreck, a repair) use the room
     * above them, so a crash stands out from the engines as it would in the
     * room, not only as loud as everything else.
     */
    private static volatile int hdrRange;
    /** The everyday sounds' gain under HDR audio, 1 with it off. */
    private static volatile float everyday = 1F;

    public static boolean isOcclusion() {
        return occlusionOn;
    }

    /** Muffling a car behind the track (and with it, the way round): on by default. */
    public static void setOcclusion(boolean on) {
        occlusionOn = on;
    }

    public static boolean isPaths() {
        return pathsOn;
    }

    /** Sound bending over a crest and round the end of a wall, toward the listener: on by default. */
    public static void setPaths(boolean on) {
        pathsOn = on;
    }

    public static boolean isReverb() {
        return reverbOn;
    }

    /** The track ringing on where the listener is, from half-pipes, walls and canyons: on by default. */
    public static void setReverb(boolean on) {
        reverbOn = on;
    }

    public static int hdrRange() {
        return hdrRange;
    }

    /** Sets HDR audio's range, 0 (off) to 24 dB; the sound takes it within a hundredth of a second. */
    public static synchronized void setHdrRange(int db) {
        hdrRange = Math.max(0, Math.min(24, db));
        everyday = (float) Math.pow(10D, -hdrRange / 20D);
        busVersion++;
    }

    /** Whether a clip is one of the loud moments HDR audio leaves at full scale. */
    private static boolean loud(String name) {
        return name.startsWith("crash") || name.startsWith("lowcrash") || name.equals("wasted")
                || name.equals("firewasted") || name.equals("carfixed");
    }

    /** Sets a bus's volume, 0 to 1; the sound takes it within a hundredth of a second. */
    public static synchronized void setBusLevel(int bus, float level) {
        busLevels[bus] = Math.max(0F, Math.min(1F, level));
        busVersion++;
    }

    public static synchronized float busLevel(int bus) {
        return busLevels[bus];
    }

    /**
     * Everything mixed down to one channel, the same in both ears, for
     * hearing in one ear or a single speaker: no HRTF then. Takes effect at
     * once, without stopping anything.
     */
    public static void setMono(boolean on) {
        mono = on;
        Sound3D sound = instance;
        if (sound != null && sound.renderTo == null) {
            sound.reopenWanted = true;
            LockSupport.unpark(sound.thread);
        }
    }

    public static boolean isMono() {
        return mono;
    }

    /** True while the new sound plays the game's sounds instead of Classic. */
    public static boolean isActive() {
        return instance != null;
    }

    /** What the new sound is doing, in words: the device, HRTF, or why it is off. */
    public static String status() {
        return status;
    }

    /* ------------------------------------------------------- the speakers */

    /** The race's announcements, which a second speaker takes off the main mix. */
    private static final Set<String> ANNOUNCEMENTS = Set.of("three", "two", "one", "go", "checkpoint", "carfixed",
            "powerup");
    /** The menus' click, which a second speaker takes too unless it plays race events only. */
    private static final String CLICK = "tick";
    /** How OpenAL Soft starts each device's name, which says nothing to the player. */
    private static final String DEVICE_PREFIX = "OpenAL Soft on ";

    /** The device the game plays on, by name; null follows Windows' default. */
    private static volatile String speaker;
    /** The second speaker, by name; null for none. "render" mixes it into -Dnfm.sound.render2 (tests). */
    private static volatile String secondSpeaker;
    /** The second speaker takes the menus' clicks as well as the race's events. */
    private static volatile boolean secondClicks = true;
    private static volatile int speakerVersion;
    private static volatile String secondStatus = "off";
    /** A fake list of devices for the hidden device test (-Dnfm.sound.devicetest). */
    private static volatile List<String> fakeDevices;

    /**
     * The output devices Windows has, by the names the player knows them by;
     * empty where OpenAL cannot list them. From any thread.
     */
    public static List<String> devices() {
        List<String> fake = fakeDevices;
        if (fake != null) {
            return fake;
        }
        if (SoundManager.isSilenced()) {
            return List.of(); // a hidden run never looks at the player's devices
        }
        try {
            List<String> raw = ALC.createCapabilities(0L).ALC_ENUMERATE_ALL_EXT
                    ? ALUtil.getStringList(0L, EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER)
                    : ALUtil.getStringList(0L, ALC_DEVICE_SPECIFIER);
            List<String> names = new ArrayList<>();
            if (raw != null) {
                for (String r : raw) {
                    names.add(displayName(r));
                }
            }
            return names;
        } catch (RuntimeException | LinkageError ex) {
            return List.of();
        }
    }

    /** For the hidden device test: the devices the game is to believe it has. */
    public static void fakeDevices(List<String> names) {
        fakeDevices = names == null ? null : List.copyOf(names);
        speakerVersion++;
    }

    private static String displayName(String specifier) {
        return specifier != null && specifier.startsWith(DEVICE_PREFIX)
                ? specifier.substring(DEVICE_PREFIX.length()) : specifier;
    }

    /**
     * The device among {@code names} that a saved or typed name means: the
     * same name, else the first that contains it (ignoring case), else null.
     */
    public static String match(String wanted, List<String> names) {
        if (wanted == null || wanted.isEmpty()) {
            return null;
        }
        for (String n : names) {
            if (n.equals(wanted)) {
                return n;
            }
        }
        String w = wanted.toLowerCase(Locale.ROOT);
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).contains(w)) {
                return n;
            }
        }
        return null;
    }

    /**
     * Where the game plays (null for Windows' default), the second speaker
     * (null for none) and whether it takes the clicks too; the sound moves
     * within a hundredth of a second, without stopping anything.
     */
    public static void setSpeakers(String main, String second, boolean clicks) {
        speaker = main == null || main.isEmpty() ? null : main;
        secondSpeaker = second == null || second.isEmpty() ? null : second;
        secondClicks = clicks;
        speakerVersion++;
        Sound3D sound = instance;
        if (sound != null && sound.thread != null) {
            LockSupport.unpark(sound.thread);
        }
    }

    public static String speaker() {
        return speaker;
    }

    public static String secondSpeaker() {
        return secondSpeaker;
    }

    public static boolean secondClicks() {
        return secondClicks;
    }

    /** What the second speaker is doing, in words: off, the device, or why events play on the main one. */
    public static String secondStatus() {
        return secondStatus;
    }

    /** True while a second speaker is open and taking the announcements. */
    public static boolean secondPlaying() {
        Sound3D sound = instance;
        return sound != null && sound.second != null;
    }

    /**
     * A sound that is not the original game's, heard only on the second
     * speaker: wrecking a car, a click where the game never clicked. Never
     * logged and never heard without a second speaker, so the main mix, and
     * every recorded race's sound, stay as they were.
     *
     * @param click a menu click, which the second speaker may be set to leave out
     */
    public static void event(String clip, boolean click) {
        event(clip, click, 0);
    }

    /** {@link #event(String, boolean)} for a split-screen player, 0 or 1: on that player's speaker when each has one. */
    public static void event(String clip, boolean click, int player) {
        Sound3D sound = instance;
        if (sound != null && secondSpeaker != null && splitSound != SPLIT_ONE && !SoundManager.isDiscarding()
                && (!click || secondClicks)) {
            sound.post(new Command(EVENT, clip, player > 0 ? player : -1, 0F, 0F));
        }
    }

    /** Not in split screen: the second speaker does as Audio's settings say. */
    public static final int SPLIT_OFF = -1;
    /** Split screen on one speaker: the second one rests, its announcements on the main one. */
    public static final int SPLIT_ONE = 0;
    /** Split screen with the race events on the second speaker, as outside it. */
    public static final int SPLIT_EVENTS = 1;
    /**
     * Split screen with a speaker each: Player 2's car and announcements play
     * on the second speaker, flat, as a controller's speaker would, and Player
     * 1's stay on the main one.
     */
    public static final int SPLIT_EACH = 2;

    private static volatile int splitSound = SPLIT_OFF;

    /** How split screen shares the speakers: one of the SPLIT_ values. */
    public static void setSplitSound(int how) {
        splitSound = how;
    }

    /** True while Player 2 has the second speaker to themselves. */
    public static boolean splitEach() {
        return splitSound == SPLIT_EACH && secondPlaying();
    }

    /**
     * Starts the new sound on the clips the game loaded, if it is wanted and
     * not already running. In a hidden test run it starts only to mix into
     * the file -Dnfm.sound.render names, so nothing is heard.
     */
    public static synchronized void start(SoundManager clips) {
        if (!wanted || instance != null) {
            return;
        }
        String file = System.getProperty("nfm.sound.render");
        if (SoundManager.isSilenced() && file == null) {
            return;
        }
        Sound3D sound = new Sound3D(clips, file);
        if (sound.lockstep) {
            sound.stageWanted = false;
            SteamAcoustics.Stage s = stage;
            if (s != null) {
                sound.steps.add(s);
            }
        }
        if (file != null) {
            // a test file needs its header written: close it when the game exits
            Runtime.getRuntime().addShutdownHook(new Thread(Sound3D::shutdown, "nfm-sound-close"));
        }
        sound.thread = new Thread(sound, "nfm-sound");
        sound.thread.setDaemon(true);
        sound.thread.setPriority(Thread.MAX_PRIORITY);
        instance = sound;
        sound.thread.start();
        StreamMusic.handTo3d();
    }

    /** Stops the new sound and lets go of the device; Classic plays from then on. */
    public static synchronized void shutdown() {
        Sound3D sound = instance;
        instance = null;
        if (sound != null) {
            sound.running = false;
            LockSupport.unpark(sound.thread);
            try {
                sound.thread.join(sound.lockstep ? 60_000L : 2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        status = wanted ? "starting" : "off";
    }

    /** A clip command from the game ("play", "loop" or "stop"); false if the new sound is not running. */
    static boolean command(String op, String clip) {
        return command(op, clip, 0);
    }

    /** {@link #command(String, String)} for a split-screen player, 0 or 1. */
    static boolean command(String op, String clip, int player) {
        Sound3D sound = instance;
        if (sound == null) {
            return false;
        }
        int kind = switch (op) {
            case "play" -> PLAY;
            case "loop" -> LOOP;
            default -> STOP;
        };
        sound.post(new Command(kind, clip, player > 0 ? player : -1, 0F, 0F));
        return true;
    }

    /**
     * Music for the new sound to play, pause, resume or let go of (see
     * {@link StreamMusic}); false if the new sound is not running.
     */
    static boolean music(StreamMusic m, int op) {
        Sound3D sound = instance;
        if (sound == null) {
            return false;
        }
        sound.post(new Command(op, null, -1, 0F, 0F, m));
        return true;
    }

    /**
     * True when a hidden test run renders the new sound to a file: its music
     * is rendered with it (never heard), so the sound check covers it too.
     */
    static boolean rendersMusic() {
        return wanted && System.getProperty("nfm.sound.render") != null;
    }

    /** Silences everything at once, as leaving a race does. */
    public static void stopAll() {
        Sound3D sound = instance;
        if (sound != null) {
            sound.post(new Command(STOP_ALL, null, -1, 0F, 0F));
        }
    }

    /**
     * Any car hit something, from inside the race tick (see Madness): the
     * new sound hears every car, Classic only the player's.
     *
     * @param kind 0 a hit to the body, -1 or 1 a roll onto the roof or the wheels
     */
    public static void carCrash(int car, float force, int kind) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding()) {
            sound.post(new Command(CRASH, null, car, force, kind));
        }
    }

    /*
     * The extras' own sounds (nfm.lit.features): clips they make from a few
     * lines of synthesis, registered by name and played at a car. Only the
     * 3D sound plays them, so Classic sound stays exactly the original's.
     */
    private static final Map<String, short[]> extraClips = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Integer> extraRates = new java.util.concurrent.ConcurrentHashMap<>();

    /** An extra's clip {@code name}: 16-bit mono samples at {@code rate} a second, kept for the rest of the game. */
    public static void extraClip(String name, short[] pcm, int rate) {
        extraClips.put(name, pcm.clone());
        extraRates.put(name, rate);
    }

    /** True if an extra's clip {@code name} has been made. */
    public static boolean hasExtraClip(String name) {
        return extraClips.containsKey(name);
    }

    /**
     * Plays the extra's clip {@code name} at car {@code car}, at {@code gain}
     * (1 as loud as the game's own effects) and {@code pitch}; only with the
     * 3D sound, and never for a run-ahead tick.
     */
    public static void carSound(int car, String name, float gain, float pitch) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding() && extraClips.containsKey(name)) {
            sound.post(new Command(EXTRA, name, car, gain, pitch));
        }
    }

    /**
     * Plays the extra's clip {@code name} at world point {@code (x, y, z)}
     * (a blast), held there while it lasts, at {@code gain} and
     * {@code pitch}; only with the 3D sound, and never for a run-ahead tick.
     */
    public static void pointSound(float x, float y, float z, String name, float gain, float pitch) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding() && extraClips.containsKey(name)) {
            sound.post(new Command(EXTRA_AT, name, -1, gain, pitch, null, x, y, z));
        }
    }

    /*
     * Beds: an extra's sounds that are all around the listener rather than
     * at a place (rain, wind): a few looping sources heard as they are. The
     * game thread says what each plays and how loud; the sound thread
     * follows over a third of a second, so nothing starts or ends with a
     * click, and a bed whose clip changes fades out first.
     */
    public static final int BEDS = 6;
    private static final float BED_SMOOTHING = 0.33F;
    private static final String[] bedClip = new String[BEDS];
    private static final float[] bedGain = new float[BEDS];
    private static final float[] bedPitch = new float[BEDS];

    /** Bed {@code slot} plays clip {@code clip} (an extra's, looping) at {@code gain} and {@code pitch}; 0 or null ends it. */
    public static void bed(int slot, String clip, float gain, float pitch) {
        if (slot < 0 || slot >= BEDS) {
            return;
        }
        synchronized (bedClip) {
            bedClip[slot] = clip != null && gain > 0F && extraClips.containsKey(clip) ? clip : null;
            bedGain[slot] = bedClip[slot] == null ? 0F : gain;
            bedPitch[slot] = pitch;
        }
    }

    /** One bed's source on the sound thread. */
    private final class BedVoice {
        final int source = alGenSources();
        String playing;
        float now;

        BedVoice() {
            flat(source);
            alSourcei(source, AL_LOOPING, AL_TRUE);
            alSourcef(source, AL_GAIN, 0F);
        }

        void update(String clip, float gain, float pitch, float dt) {
            boolean same = clip != null && clip.equals(playing);
            float want = same ? gain : 0F;
            now += (want - now) * (1F - (float) Math.exp(-dt / BED_SMOOTHING));
            boolean on = alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
            if (!same && now < 0.003F) {
                // silent: free to take the new clip
                if (on) {
                    alSourceStop(source);
                    on = false;
                }
                now = 0F;
                playing = null;
                if (clip != null) {
                    int b = buffer(clip);
                    if (b != 0) {
                        alSourcei(source, AL_BUFFER, b);
                        playing = clip;
                    }
                }
                return;
            }
            if (now < 0.003F && want == 0F) {
                if (on) {
                    alSourcePause(source);
                }
                return;
            }
            alSourcef(source, AL_GAIN, now * bus[BUS_EFFECTS] * everyday);
            alSourcef(source, AL_PITCH, same ? pitch : 1F);
            if (!on) {
                alSourcePlay(source);
            }
        }

        void silence() {
            now = 0F;
            alSourceStop(source);
        }
    }

    private final BedVoice[] beds = new BedVoice[BEDS];

    /** The beds follow what the game thread last said; {@code silent} while nothing may sound (no race, paused, muted). */
    private void updateBeds(float dt, boolean silent) {
        for (int i = 0; i < BEDS; i++) {
            String clip;
            float gain;
            float pitch;
            synchronized (bedClip) {
                clip = bedClip[i];
                gain = bedGain[i];
                pitch = bedPitch[i];
            }
            if (beds[i] == null) {
                if (clip == null || silent) {
                    continue;
                }
                beds[i] = new BedVoice();
            }
            beds[i].update(silent ? null : clip, silent ? 0F : gain, pitch, dt);
        }
    }

    /** Any car skids, from inside the race tick; surface 0 is road. */
    public static void carSkid(int car, int surface, float slide) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding() && slide > 150F) {
            sound.post(new Command(SKID, null, car, surface, slide));
        }
    }

    /* the rebuilt sound's places on each car, in its own frame: the front, the back, then each wheel's tread */
    private static final int REBUILT_WHEELS = 4;
    private static final int BODY_VALUES = 3 * (2 + REBUILT_WHEELS);
    private static final float[][] bodies = new float[ALL_CARS][BODY_VALUES];

    /**
     * Car {@code car}'s points the rebuilt engine sound plays from, in its
     * own frame (as its model has it: y down, z forward): {@code local}
     * holds x, y and z of the front (the engine and intake), the back (the
     * exhaust), then each of the four wheels' treads. From the game thread
     * when a race starts; kept for the replays after it.
     */
    public static void carBody(int car, float[] local) {
        if (car >= 0 && car < ALL_CARS) {
            synchronized (bodies) {
                Arrays.fill(bodies[car], 0F);
                System.arraycopy(local, 0, bodies[car], 0, Math.min(local.length, BODY_VALUES));
            }
        }
    }

    private static final EngineLayers.Kit[] kits = new EngineLayers.Kit[ALL_CARS];

    /**
     * What car {@code car} is fitted with, for the rebuilt engine sound's
     * layers (its turbo or supercharger, a truck's beeper, how loud its
     * engine is); null for nothing. From the game thread when a race starts.
     */
    public static void carKit(int car, EngineLayers.Kit kit) {
        if (car >= 0 && car < ALL_CARS) {
            synchronized (bodies) {
                kits[car] = kit;
            }
        }
    }

    private static EngineLayers.Kit kitOf(int car) {
        synchronized (bodies) {
            EngineLayers.Kit k = car >= 0 && car < ALL_CARS ? kits[car] : null;
            return k == null ? EngineLayers.PLAIN : k;
        }
    }

    /**
     * Car {@code car}'s exhaust pops, at {@code gain}; {@code big} for a
     * loud backfire. With the rebuilt engine sound it is heard from the
     * exhaust, mixed into it; only with the 3D sound, and never for a
     * run-ahead tick.
     */
    public static void carPop(int car, float gain, boolean big) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding()) {
            sound.post(new Command(POP, null, car, gain, big ? 1F : 0F));
        }
    }

    private static volatile SteamAcoustics.Stage stage;

    /** What a stage triangle is made of, for how it muffles and reflects sound. */
    public static final byte SURFACE_HARD = 0;
    public static final byte SURFACE_GROUND = 1;
    public static final byte SURFACE_FOLIAGE = 2;
    public static final byte SURFACE_GLASS = 3;

    /**
     * The stage just loaded, as triangles in the game's world, for the sound
     * to be muffled and to ring by: from the game thread when a stage loads.
     *
     * @param kinds one per triangle, a SURFACE kind
     */
    public static void setStage(float[] vertices, int[] triangles, byte[] kinds) {
        SteamAcoustics.Stage s = new SteamAcoustics.Stage(vertices, triangles, kinds);
        stage = s;
        Sound3D sound = instance;
        if (sound != null && sound.lockstep) {
            sound.steps.add(s); // at its place among the ticks
        } else if (sound != null) {
            sound.stageWanted = true;
        }
    }

    /** Where everything is at the end of this tick; from the game thread, once a tick. */
    public static void publish(Moment moment) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding()) {
            if (sound.lockstep) {
                sound.steps.add(moment);
            } else {
                sound.latest = moment;
            }
        }
    }

    /* ------------------------------------------------- the sound thread */

    private long device;
    private long context;
    private boolean efx;
    private boolean deferred;
    private SOFTSystemEventProc deviceEvents;
    private final Map<String, Integer> buffers = new HashMap<>();
    /** One source per named clip, for the sounds the game plays by name. */
    private final Map<String, Integer> named = new HashMap<>();
    private final List<EngineVoices> engines = new ArrayList<>();
    private final Voice[] effects = new Voice[EFFECT_VOICES];
    private final CarEffects[] carEffects = new CarEffects[64];
    private final Random random = new Random();
    /** The rebuilt sound's own random choices (which cycle next, the pops, the skid grains), apart from the crashes'. */
    private final Random grainRandom = new Random();
    /** Steam Audio, when its library is there: null otherwise, and nothing is muffled. */
    private SteamAcoustics acoustics;
    private volatile boolean stageWanted = true;
    /* each car's share of sound reaching the listener through the stage, low, mid and high: traced and smoothed */
    private final float[] traced = new float[3 * MAX_CARS];
    private final float[] through = new float[3 * ALL_CARS];
    private final float[] carPlaces = new float[3 * MAX_CARS];
    private final boolean[] carTraced = new boolean[MAX_CARS];
    private final boolean[] carAround = new boolean[MAX_CARS];
    /* which car each traced slot is, each car's slot (-1 for none), and how far each car is from the listener */
    private final int[] tracedCar = new int[MAX_CARS];
    private final int[] tracedSlot = new int[ALL_CARS];
    private final float[] carDistance = new float[ALL_CARS];
    private int tracedAt;
    private int reverbSlot;
    private int reverbEffect;
    private boolean eaxReverb;
    /* the cars' echo: its slot and effect, and per car a filter that is how much of it the car sends (0: none) */
    private int echoSlot;
    private int echoEffect;
    private final int[] echoFilter = new int[ALL_CARS];
    private final float[] echo = new float[ALL_CARS];
    private final float[] reverbNow = new float[3];
    private final float[] reverbApplied = {-1F, -1F, -1F};
    private float enclosed;
    /** How far the engines are pulled down under a big crash, 0 to DUCK_DEPTH. */
    private float duck;
    private float engineLevel = 1F;
    private float enclosedApplied = -1F;
    private Moment previous;
    private Moment current;
    /** The test file the main mix renders into, or null when it plays on a device. */
    private WavOut wav;

    @Override
    public void run() {
        try {
            if (!open()) {
                handMusicOver();
                return;
            }
            for (int i = 0; i < effects.length; i++) {
                effects[i] = new Voice(alGenSources(), efx ? EXTEfx.alGenFilters() : 0);
            }
            Arrays.fill(through, 1F);
            Arrays.fill(traced, 1F);
            identityTraced();
            acoustics = SteamAcoustics.open(MAX_CARS, lockstep);
            if (lockstep) {
                runInStep();
                return;
            }
            long next = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                clock = now;
                if (reopenWanted) {
                    reopen();
                }
                applySpeakers();
                if (deferred) {
                    SOFTDeferredUpdates.alDeferUpdatesSOFT();
                }
                handleCommands();
                applyBuses();
                update(now);
                feedMusic();
                if (deferred) {
                    SOFTDeferredUpdates.alProcessUpdatesSOFT();
                }
                if (wav != null) {
                    render(now);
                } else {
                    checkConnected(now);
                }
                next += UPDATE_NANOS;
                if (next - now < 0L) {
                    next = now + UPDATE_NANOS;
                }
                LockSupport.parkNanos(next - now);
            }
        } catch (Throwable ex) {
            HLogger.warn("The 3D sound stopped: " + ex);
            status = "stopped: " + ex.getMessage();
            instance = null;
            handMusicOver();
        } finally {
            close();
        }
    }

    /* the new sound could not carry on: BASS plays the music meanwhile (never in a test render) */
    private void handMusicOver() {
        if (renderTo == null) {
            StreamMusic.handToClassic();
        }
    }

    /**
     * The lockstep loop: commands as they come, and for each tick the
     * updates and the samples of exactly its length, until the game has
     * stopped and everything queued is mixed.
     */
    private void runInStep() throws IOException {
        while (true) {
            Object step = steps.poll();
            if (step == null) {
                if (!running) {
                    return;
                }
                LockSupport.parkNanos(1_000_000L);
                continue;
            }
            if (step instanceof Command c) {
                handle(c);
                continue;
            }
            if (step instanceof SteamAcoustics.Stage st) {
                if (acoustics != null) {
                    acoustics.stage(st);
                }
                continue;
            }
            Moment m = (Moment) step;
            m.nanos = clock;
            latest = m;
            long end = clock + m.tickNanos;
            while (clock < end) {
                if (deferred) {
                    SOFTDeferredUpdates.alDeferUpdatesSOFT();
                }
                applyBuses();
                update(clock);
                feedMusic();
                if (deferred) {
                    SOFTDeferredUpdates.alProcessUpdatesSOFT();
                }
                clock = Math.min(end, clock + UPDATE_NANOS);
                render(clock);
                if (second != null) {
                    second.render(clock);
                }
            }
        }
    }

    /** Opens the default output device (or the test file) with HRTF as Windows' output suggests. */
    private boolean open() throws IOException {
        if (renderTo != null) {
            device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
        } else {
            String named = specifier(speaker);
            device = named == null ? 0L : alcOpenDevice(named);
            mainOn = named == null ? null : speaker;
            if (device == 0L) {
                device = alcOpenDevice((ByteBuffer) null);
                mainOn = null;
            }
        }
        if (device == 0L) {
            status = "no sound device";
            HLogger.warn("The 3D sound found no sound device; Classic sound plays");
            instance = null;
            return false;
        }
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        context = alcCreateContext(device, contextAttributes(deviceCaps));
        if (context == 0L || !alcMakeContextCurrent(context)) {
            status = "OpenAL would not start";
            HLogger.warn("The 3D sound could not start OpenAL; Classic sound plays");
            instance = null;
            return false;
        }
        ALCapabilities caps = AL.createCapabilities(deviceCaps);
        mainCaps = caps;
        efx = deviceCaps.ALC_EXT_EFX;
        deferred = caps.AL_SOFT_deferred_updates;
        musicFloat = caps.AL_EXT_FLOAT32;
        musicDirect = caps.AL_SOFT_direct_channels;
        alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
        alDopplerFactor(DOPPLER);
        AL11.alSpeedOfSound(343.3F * UNITS_PER_METRE);
        if (efx) {
            alListenerf(EXTEfx.AL_METERS_PER_UNIT, 1F / UNITS_PER_METRE);
            openReverb();
        }
        if (renderTo != null) {
            openRenderFile();
        } else if (deviceCaps.ALC_SOFT_system_events && deviceCaps.ALC_SOFT_reopen_device) {
            deviceEvents = SOFTSystemEventProc.create((type, deviceType, dev, length, message, user) -> {
                // only while the game follows Windows' default: a chosen device stays chosen
                if (type == SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT
                        && deviceType == SOFTSystemEvents.ALC_PLAYBACK_DEVICE_SOFT && mainOn == null) {
                    reopenWanted = true;
                    LockSupport.unpark(thread);
                }
            });
            SOFTSystemEvents.alcEventCallbackSOFT(deviceEvents, 0L);
            SOFTSystemEvents.alcEventControlSOFT(
                    new int[] {SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT}, true);
        }
        describeDevice();
        HLogger.info("3D sound: " + status);
        speakersApplied = speakerVersion;
        openSecond();
        return true;
    }

    private int[] contextAttributes(ALCCapabilities deviceCaps) {
        List<Integer> a = new ArrayList<>();
        monoOut = mono && renderTo == null && deviceCaps.ALC_SOFT_output_mode;
        if (deviceCaps.ALC_SOFT_HRTF) {
            a.add(SOFTHRTF.ALC_HRTF_SOFT);
            // on headphones by itself; a test file is always rendered as for headphones; never in mono
            a.add(renderTo != null ? ALC_TRUE : monoOut ? ALC_FALSE : SOFTHRTF.ALC_DONT_CARE_SOFT);
        }
        if (monoOut) {
            a.add(SOFTOutputMode.ALC_OUTPUT_MODE_SOFT);
            a.add(SOFTOutputMode.ALC_MONO_SOFT);
        }
        if (deviceCaps.ALC_SOFT_output_limiter) {
            a.add(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT);
            a.add(ALC_TRUE);
        }
        if (renderTo != null) {
            a.add(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT);
            a.add(SOFTLoopback.ALC_STEREO_SOFT);
            a.add(SOFTLoopback.ALC_FORMAT_TYPE_SOFT);
            a.add(SOFTLoopback.ALC_SHORT_SOFT);
            a.add(ALC_FREQUENCY);
            a.add(RENDER_RATE);
        }
        a.add(0);
        return a.stream().mapToInt(Integer::intValue).toArray();
    }

    private void describeDevice() {
        String name = renderTo != null ? "a test file" : alcGetString(device, ALC_DEVICE_SPECIFIER);
        int hrtf = alcGetInteger(device, SOFTHRTF.ALC_HRTF_SOFT);
        status = "on, " + (name == null ? "the default device" : displayName(name)) + (hrtf == ALC_TRUE ? ", HRTF" : "")
                + (monoOut ? ", mono" : "")
                + (renderTo == null && speaker != null && mainOn == null ? ", " + speaker + " not connected" : "");
        makeup = hrtf == ALC_TRUE ? HRTF_MAKEUP : PANNED_MAKEUP;
        alListenerf(AL_GAIN, makeup * bus[BUS_MASTER]);
        flatGain = 1F / makeup;
        applyNamedGains();
    }

    /** The listener's gain that makes up for HRTF or panning, before the master volume. */
    private float makeup = 1F;
    private boolean monoOut;
    /* the buses' volumes as the sound thread has them */
    private final float[] bus = {1F, 1F, 1F, 1F, 1F};
    private int busApplied;

    /** Takes the buses' volumes when they changed: at once for what plays by name or as an effect. */
    private void applyBuses() {
        int version = busVersion;
        if (version == busApplied) {
            return;
        }
        busApplied = version;
        synchronized (Sound3D.class) {
            System.arraycopy(busLevels, 0, bus, 0, bus.length);
        }
        alListenerf(AL_GAIN, makeup * bus[BUS_MASTER]);
        applyNamedGains();
        if (second != null) {
            second.applyGains();
        }
        for (Voice v : effects) {
            if (v != null) {
                alSourcef(v.source, AL_GAIN, v.gain * bus[BUS_EFFECTS]);
            }
        }
        // the engines and the music take theirs on their next update
    }

    private void applyNamedGains() {
        named.forEach(this::namedGain);
    }

    /* a clip played by name: at the player's car as an effect, the wind as an effect, the rest as interface */
    private void namedGain(String key, int source) {
        boolean atCar = namedAtPlayer.containsKey(key);
        String name = key.startsWith(PLAYER_TWO) ? key.substring(PLAYER_TWO.length()) : key;
        int b = atCar || name.startsWith("air") ? BUS_EFFECTS : BUS_INTERFACE;
        alSourcef(source, AL_GAIN, (atCar ? 1F : flatGain) * bus[b] * (loud(name) ? 1F : everyday));
    }

    /** The gain of a clip heard as it is, undoing the listener's makeup. */
    private float flatGain = 1F;

    /**
     * Moves to the chosen speaker, or to Windows' default when none is
     * chosen or it is not connected, without stopping anything.
     */
    private void reopen() {
        reopenWanted = false;
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        String named = specifier(speaker);
        boolean moved = named != null
                && SOFTReopenDevice.alcReopenDeviceSOFT(device, named, contextAttributes(deviceCaps));
        if (moved) {
            mainOn = speaker;
        } else {
            moved = SOFTReopenDevice.alcReopenDeviceSOFT(device, (ByteBuffer) null, contextAttributes(deviceCaps));
            if (moved) {
                mainOn = null;
            }
        }
        if (moved) {
            describeDevice();
            HLogger.info("3D sound moved: " + status);
        }
    }

    /** The full OpenAL name of the device a player's name means, or null when it is not there. */
    private static String specifier(String wanted) {
        if (wanted == null || fakeDevices != null) {
            return null; // the device test only pretends
        }
        try {
            List<String> raw = ALC.createCapabilities(0L).ALC_ENUMERATE_ALL_EXT
                    ? ALUtil.getStringList(0L, EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER)
                    : ALUtil.getStringList(0L, ALC_DEVICE_SPECIFIER);
            if (raw == null) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (String r : raw) {
                names.add(displayName(r));
            }
            String m = match(wanted, names);
            return m == null ? null : raw.get(names.indexOf(m));
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private long connectedCheckedAt;

    /**
     * Once a second: a device that was unplugged is reopened on the default
     * one, a chosen speaker that is plugged back in is moved to again, and
     * the second speaker is let go of when unplugged and opened when back.
     */
    private void checkConnected(long now) {
        if (now - connectedCheckedAt < 1_000_000_000L) {
            return;
        }
        connectedCheckedAt = now;
        if (alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) == ALC_FALSE
                || speaker != null && mainOn == null && specifier(speaker) != null) {
            reopen();
        }
        if (second != null && !second.connected()) {
            HLogger.info("Second speaker " + second.name + " was unplugged; events play on the main speaker");
            closeSecond();
        }
        if (second == null && secondSpeaker != null) {
            openSecond();
        }
    }

    /** Takes a changed choice of speakers, on the sound thread. */
    private void applySpeakers() {
        int version = speakerVersion;
        if (version == speakersApplied) {
            return;
        }
        speakersApplied = version;
        if (renderTo == null && !java.util.Objects.equals(speaker, mainOn)) {
            reopen();
        }
        String wanted = secondSpeaker;
        if (second != null && (wanted == null || !second.wanted.equals(wanted))) {
            closeSecond();
        }
        if (second == null) {
            openSecond();
        }
        if (second != null) {
            second.applyGains();
        }
    }

    /* ----------------------------------------------------- second speaker */

    private ALCapabilities mainCaps;
    /** The chosen speaker the main device is on now; null when on Windows' default. */
    private volatile String mainOn;
    private int speakersApplied;
    /** The second speaker while it is open; null otherwise, when everything plays on the main one. */
    private volatile SecondSpeaker second;
    private long secondTriedAt;

    /** Opens the chosen second speaker, if there is one and it is not the main speaker. */
    private void openSecond() {
        String wanted = secondSpeaker;
        if (wanted == null) {
            secondStatus = "off";
            return;
        }
        boolean test = wanted.equals("render");
        if (test && System.getProperty("nfm.sound.render2") == null) {
            secondStatus = "off";
            return;
        }
        if (!test && renderTo != null) {
            secondStatus = "off"; // a test render never opens a real device
            return;
        }
        if (fakeDevices != null) {
            String found = match(wanted, fakeDevices);
            String main = speaker == null ? fakeDevices.isEmpty() ? null : fakeDevices.get(0) : match(speaker, fakeDevices);
            secondStatus = found == null ? "not connected" : found.equals(main) ? "same" : "on, " + found;
            HLogger.info("[speakers] second " + wanted + ": " + secondStatus);
            return;
        }
        String named = test ? null : specifier(wanted);
        if (!test && named == null) {
            secondStatus = "not connected";
            return;
        }
        if (!test) {
            String mainName = alcGetString(device, EnumerateAllExt.ALC_ALL_DEVICES_SPECIFIER);
            if (named.equals(mainName)) {
                secondStatus = "same";
                return;
            }
        }
        SecondSpeaker s = new SecondSpeaker(wanted, displayName(named));
        try {
            if (s.open(named, test)) {
                second = s;
                secondStatus = "on, " + (test ? "a test file" : s.name);
                HLogger.info("Second speaker: " + secondStatus);
            } else {
                secondStatus = "not connected";
            }
        } finally {
            useMain();
        }
    }

    private void closeSecond() {
        if (second != null) {
            second.close();
            useMain();
            second = null;
        }
        secondStatus = secondSpeaker == null ? "off" : "not connected";
    }

    /** Makes the main device's context the one OpenAL calls go to again. */
    private void useMain() {
        if (context != 0L) {
            alcMakeContextCurrent(context);
            if (mainCaps != null) {
                AL.setCurrentProcess(mainCaps);
            }
        }
    }

    /**
     * Whether a clip the game plays by name goes to the second speaker
     * instead of the main mix: the announcements, and the clicks unless it
     * plays race events only.
     */
    private boolean toSecond(String name) {
        return second != null && splitSound != SPLIT_ONE
                && (ANNOUNCEMENTS.contains(name) || secondClicks && name.equals(CLICK));
    }

    /**
     * The second speaker: a device and context of its own, flat stereo with
     * no HRTF (a controller's or a desk speaker), one source per clip.
     */
    private final class SecondSpeaker {
        /** The name it was chosen by, and the device's name. */
        final String wanted;
        final String name;
        private long dev;
        private long ctx;
        private ALCapabilities caps;
        private final Map<String, Integer> clipBuffers = new HashMap<>();
        private final Map<String, Integer> sources = new HashMap<>();
        private WavOut out;

        SecondSpeaker(String wanted, String name) {
            this.wanted = wanted;
            this.name = name;
        }

        boolean open(String specifier, boolean test) {
            dev = test ? SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null) : alcOpenDevice(specifier);
            if (dev == 0L) {
                return false;
            }
            ALCCapabilities dc = ALC.createCapabilities(dev);
            List<Integer> a = new ArrayList<>();
            if (dc.ALC_SOFT_HRTF) {
                a.add(SOFTHRTF.ALC_HRTF_SOFT);
                a.add(ALC_FALSE);
            }
            if (dc.ALC_SOFT_output_mode && !test) {
                a.add(SOFTOutputMode.ALC_OUTPUT_MODE_SOFT);
                a.add(SOFTOutputMode.ALC_STEREO_BASIC_SOFT);
            }
            if (dc.ALC_SOFT_output_limiter) {
                a.add(SOFTOutputLimiter.ALC_OUTPUT_LIMITER_SOFT);
                a.add(ALC_TRUE);
            }
            if (test) {
                a.add(SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT);
                a.add(SOFTLoopback.ALC_STEREO_SOFT);
                a.add(SOFTLoopback.ALC_FORMAT_TYPE_SOFT);
                a.add(SOFTLoopback.ALC_SHORT_SOFT);
                a.add(ALC_FREQUENCY);
                a.add(RENDER_RATE);
            }
            a.add(0);
            ctx = alcCreateContext(dev, a.stream().mapToInt(Integer::intValue).toArray());
            if (ctx == 0L || !alcMakeContextCurrent(ctx)) {
                close();
                return false;
            }
            caps = AL.createCapabilities(dc);
            AL.setCurrentProcess(caps);
            if (test) {
                try {
                    out = new WavOut(System.getProperty("nfm.sound.render2"), lockstep ? 0L : System.nanoTime());
                } catch (IOException ex) {
                    HLogger.warn("Second speaker cannot write its test file: " + ex);
                    close();
                    return false;
                }
            }
            return true;
        }

        private void use() {
            alcMakeContextCurrent(ctx);
            AL.setCurrentProcess(caps);
        }

        boolean connected() {
            return out != null || alcGetInteger(dev, EXTDisconnect.ALC_CONNECTED) != ALC_FALSE;
        }

        /** Plays, loops or stops a clip here, centred, with the voice kept as {@code key}. */
        void clip(int kind, String clip, String key) {
            use();
            try {
                int buffer = buffer(clip, clipBuffers);
                if (buffer == 0) {
                    return;
                }
                int source = sources.computeIfAbsent(key, n -> {
                    int s = alGenSources();
                    alSourcei(s, AL_BUFFER, buffer);
                    alSourcei(s, AL_SOURCE_RELATIVE, AL_TRUE);
                    alSource3f(s, AL_POSITION, 0F, 0F, 0F);
                    alSourcef(s, AL_ROLLOFF_FACTOR, 0F);
                    return s;
                });
                alSourcef(source, AL_GAIN, gain(clip));
                switch (kind) {
                    case PLAY -> {
                        alSourcei(source, AL_LOOPING, AL_FALSE);
                        alSourceRewind(source);
                        alSourcePlay(source);
                    }
                    case LOOP -> {
                        alSourcei(source, AL_LOOPING, AL_TRUE);
                        if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
                            alSourcePlay(source);
                        }
                    }
                    default -> alSourceStop(source);
                }
            } finally {
                useMain();
            }
        }

        /* the interface bus and the master volume, the loud moments above HDR audio's everyday level */
        private float gain(String key) {
            String clip = key.startsWith(PLAYER_TWO) ? key.substring(PLAYER_TWO.length()) : key;
            int b = ownSound(clip) || clip.startsWith("air") ? BUS_EFFECTS : BUS_INTERFACE;
            return bus[BUS_MASTER] * bus[b] * (loud(clip) ? 1F : everyday);
        }

        void applyGains() {
            use();
            try {
                sources.forEach((clip, s) -> alSourcef(s, AL_GAIN, gain(clip)));
            } finally {
                useMain();
            }
        }

        void stopAll() {
            use();
            try {
                for (int s : sources.values()) {
                    alSourceStop(s);
                }
            } finally {
                useMain();
            }
        }

        void render(long now) throws IOException {
            if (out != null) {
                out.render(dev, now);
            }
        }

        void close() {
            if (ctx != 0L) {
                use();
                for (int s : sources.values()) {
                    alDeleteSources(s);
                }
                for (int b : clipBuffers.values()) {
                    if (b != 0) {
                        alDeleteBuffers(b);
                    }
                }
                alcMakeContextCurrent(0L);
                alcDestroyContext(ctx);
                ctx = 0L;
            }
            if (dev != 0L) {
                alcCloseDevice(dev);
                dev = 0L;
            }
            if (out != null) {
                out.close();
                out = null;
            }
        }
    }

    private void close() {
        closeSecond();
        stopEverything();
        stopMusic();
        if (acoustics != null) {
            acoustics.close();
            acoustics = null;
        }
        if (context != 0L) {
            if (reverbSlot != 0) {
                EXTEfx.alDeleteAuxiliaryEffectSlots(reverbSlot);
                EXTEfx.alDeleteEffects(reverbEffect);
            }
            if (echoSlot != 0) {
                EXTEfx.alDeleteAuxiliaryEffectSlots(echoSlot);
                EXTEfx.alDeleteEffects(echoEffect);
                for (int f : echoFilter) {
                    EXTEfx.alDeleteFilters(f);
                }
                echoSlot = 0;
            }
            for (int source : named.values()) {
                alDeleteSources(source);
            }
            if (musicSource != 0) {
                alDeleteSources(musicSource);
                alDeleteBuffers(musicBuffers);
                musicSource = 0;
                musicArena.close();
            }
            for (EngineVoices e : engines) {
                e.delete();
            }
            for (Voice v : effects) {
                if (v != null) {
                    alDeleteSources(v.source);
                    if (v.filter != 0) {
                        EXTEfx.alDeleteFilters(v.filter);
                    }
                }
            }
            for (int buffer : buffers.values()) {
                if (buffer != 0) {
                    alDeleteBuffers(buffer);
                }
            }
            alcMakeContextCurrent(0L);
            alcDestroyContext(context);
            context = 0L;
        }
        if (device != 0L) {
            alcCloseDevice(device);
            device = 0L;
        }
        if (deviceEvents != null) {
            deviceEvents.free();
            deviceEvents = null;
        }
        closeRenderFile();
    }

    /* ----------------------------------------------------------- clips */

    /**
     * The OpenAL buffer of a clip, decoded at its own rate, unchanged, the
     * first time it is asked for: the game may still be loading its clips
     * when the new sound starts. 0 when the clip is not (yet) there.
     */
    private int buffer(String name) {
        return buffer(name, buffers);
    }

    /** The same, into the given device's buffers, in the context current now. */
    private int buffer(String name, Map<String, Integer> buffers) {
        Integer known = buffers.get(name);
        if (known != null) {
            return known;
        }
        SoundClip clip = clips.clips().get(name);
        byte[] data = clip == null ? null : clip.data();
        if (data == null) {
            return extraBuffer(name, buffers);
        }
        int buffer = 0;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            AudioInputStream pcm = pcm16(in);
            byte[] samples = pcm.readAllBytes();
            ByteBuffer direct = MemoryUtil.memAlloc(samples.length);
            try {
                direct.put(samples).flip();
                buffer = alGenBuffers();
                alBufferData(buffer, pcm.getFormat().getChannels() == 2 ? AL_FORMAT_STEREO16 : AL_FORMAT_MONO16,
                        direct, (int) pcm.getFormat().getSampleRate());
            } finally {
                MemoryUtil.memFree(direct);
            }
        } catch (Exception ex) {
            HLogger.warn("3D sound cannot read clip " + name + ": " + ex);
        }
        buffers.put(name, buffer); // a clip that cannot be read is not tried again
        return buffer;
    }

    /** An extra's clip {@code name} as a buffer of the device current now; 0 if there is none. */
    private int extraBuffer(String name, Map<String, Integer> buffers) {
        short[] pcm = extraClips.get(name);
        Integer rate = extraRates.get(name);
        if (pcm == null || rate == null) {
            return 0;
        }
        ShortBuffer direct = MemoryUtil.memAllocShort(pcm.length);
        int buffer;
        try {
            direct.put(pcm).flip();
            buffer = alGenBuffers();
            alBufferData(buffer, AL_FORMAT_MONO16, direct, rate);
        } finally {
            MemoryUtil.memFree(direct);
        }
        buffers.put(name, buffer);
        return buffer;
    }

    /* -------------------------------------------------------- commands */

    private void handleCommands() {
        for (Command c; (c = commands.poll()) != null;) {
            handle(c);
        }
    }

    private void handle(Command c) {
        switch (c.kind) {
            case PLAY, LOOP, STOP -> {
                if (c.car == 1) {
                    secondPlayerClip(c.kind, c.clip);
                } else {
                    clip(c.kind, c.clip);
                }
            }
            case EVENT -> {
                if (second != null) {
                    second.clip(PLAY, c.clip, c.car == 1 ? PLAYER_TWO + c.clip : c.clip);
                }
            }
            case CRASH -> crash(c.car, c.a, (int) c.b);
            case SKID -> skid(c.car, (int) c.a);
            case EXTRA -> {
                Moment m = current;
                if (m != null && !m.muted && effectsOf(c.car) != null) {
                    playAt(c.car, c.clip, c.a, c.b);
                }
            }
            case EXTRA_AT -> {
                Moment m = current;
                if (m != null && !m.muted) {
                    playAtPoint(c.x, c.y, c.z, c.clip, c.a, c.b);
                }
            }
            case POP -> {
                Moment m = current;
                if (m != null && !m.muted) {
                    pop(c.car, c.a, c.b != 0F);
                }
            }
            case STOP_ALL -> stopEverything();
            case MUSIC_PLAY -> startMusic(c.music);
            case MUSIC_PAUSE, MUSIC_RESUME -> pauseMusic(c.music, c.kind == MUSIC_PAUSE);
            case MUSIC_UNLOAD -> {
                if (c.music == musicNow) {
                    stopMusic();
                }
            }
            default -> {
            }
        }
    }

    /* ----------------------------------------------------------- music */

    /*
     * The music streams through one source of its own, straight to the left
     * and right speakers (no HRTF, no reverb, no ducking), in blocks decoded
     * as the source uses them up: six of 1024 frames, a little over a tenth
     * of a second ahead, enough to ride out a hitch of the sound thread.
     */
    private static final int MUSIC_RATE = 48_000;
    private static final int MUSIC_FRAMES = 1_024;
    private final int[] musicBuffers = new int[6];
    private StreamMusic musicNow;
    private MusicCodecs.Decoder decoder;
    private int musicSource;
    private boolean musicPaused;
    private float musicLevel = 1F;
    private boolean musicFloat;
    private boolean musicDirect;
    private Arena musicArena;
    private MemorySegment musicBlock;
    private MemorySegment musicShorts;

    /** Starts this music from its beginning, in place of any other: BASS too played one at a time. */
    private void startMusic(StreamMusic m) {
        stopMusic();
        try {
            decoder = m.open(MUSIC_RATE);
        } catch (IOException | RuntimeException ex) {
            HLogger.warn("Music: " + ex.getMessage());
            return;
        }
        if (musicSource == 0) {
            musicSource = alGenSources();
            flat(musicSource);
            if (musicDirect) {
                alSourcei(musicSource, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL_TRUE);
            }
            alGenBuffers(musicBuffers);
            musicArena = Arena.ofConfined();
            musicBlock = musicArena.allocate(MUSIC_FRAMES * 8L, 16);
            musicShorts = musicArena.allocate(MUSIC_FRAMES * 4L, 16);
        }
        musicNow = m;
        musicPaused = false;
        musicLevel = decoder.level();
        musicGainApplied = -1F;
        setMusicGain();
        try {
            for (int b : musicBuffers) {
                fillMusic(b);
            }
        } catch (RuntimeException ex) {
            HLogger.warn("Music: cannot play " + m.name() + ": " + ex);
            stopMusic();
            return;
        }
        alSourceQueueBuffers(musicSource, musicBuffers);
        alSourcePlay(musicSource);
    }

    private void pauseMusic(StreamMusic m, boolean pause) {
        if (m != musicNow || decoder == null) {
            return;
        }
        musicPaused = pause;
        if (pause) {
            alSourcePause(musicSource);
        } else {
            alSourcePlay(musicSource);
        }
    }

    private void stopMusic() {
        if (musicSource != 0) {
            alSourceStop(musicSource);
            alSourcei(musicSource, AL_BUFFER, 0);
        }
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
        musicNow = null;
    }

    private float musicGainApplied = -1F;

    /* the music's level, its bus, and the dip under a big crash, as the engines dip */
    private void setMusicGain() {
        float g = flatGain * musicLevel * bus[BUS_MUSIC] * (1F - duck) * everyday;
        if (Math.abs(g - musicGainApplied) > 1e-4F) {
            musicGainApplied = g;
            alSourcef(musicSource, AL_GAIN, g);
        }
    }

    /** Decodes the next block into this buffer, as float or, where OpenAL has no float, 16-bit. */
    private void fillMusic(int buffer) {
        decoder.read(musicBlock, MUSIC_FRAMES);
        if (musicFloat) {
            alBufferData(buffer, EXTFloat32.AL_FORMAT_STEREO_FLOAT32, musicBlock.asByteBuffer(), decoder.rate());
            return;
        }
        for (int i = 0; i < MUSIC_FRAMES * 2; i++) {
            float v = Math.max(-1F, Math.min(1F, musicBlock.getAtIndex(ValueLayout.JAVA_FLOAT, i)));
            musicShorts.setAtIndex(ValueLayout.JAVA_SHORT, i, (short) (v * 32767F));
        }
        alBufferData(buffer, AL_FORMAT_STEREO16, musicShorts.asByteBuffer(), decoder.rate());
    }

    /** Refills what the music source has played; restarts it if it ran dry. */
    private void feedMusic() {
        if (decoder == null || musicPaused) {
            return;
        }
        setMusicGain();
        try {
            int processed = alGetSourcei(musicSource, AL_BUFFERS_PROCESSED);
            for (int i = 0; i < processed; i++) {
                int b = alSourceUnqueueBuffers(musicSource);
                fillMusic(b);
                alSourceQueueBuffers(musicSource, b);
            }
        } catch (RuntimeException ex) {
            HLogger.warn("Music: " + musicNow.name() + " stopped: " + ex);
            stopMusic();
            return;
        }
        if (alGetSourcei(musicSource, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(musicSource);
        }
    }

    /**
     * A sound the game plays by name. The engine and the crashes and skids
     * are the new sound's own; the rest play as the game asks, one voice per
     * clip as before: the wreck and the repair at the player's car, the rest
     * (wind, countdown, menus) centred.
     */
    private void clip(int kind, String name) {
        if (ownSound(name)) {
            return;
        }
        if (toSecond(name)) {
            second.clip(kind, name, name);
            return;
        }
        namedClip(kind, name, name, name.equals("wasted") || name.equals("firewasted") || name.equals("carfixed"));
    }

    /** The engine, crashes and skids: the new sound makes them at each car, whatever the game plays by name. */
    private static boolean ownSound(String name) {
        return name.startsWith("engs") || name.startsWith("crash") || name.startsWith("lowcrash")
                || name.startsWith("skid") || name.startsWith("dustskid") || name.equals("tires");
    }

    /** Split screen's Player 2's clips have voices of their own, kept by this prefix and the clip's name. */
    private static final String PLAYER_TWO = "2/";

    /**
     * A clip split screen's Player 2 plays by name: with a speaker of their
     * own, all of it there, the car's engine, crashes and skids too (the
     * world's 3D sound is Player 1's); otherwise as Player 1's clips are,
     * with voices of its own so the two players never cut each other off.
     */
    private void secondPlayerClip(int kind, String name) {
        if (splitSound == SPLIT_EACH && second != null) {
            second.clip(kind, name, PLAYER_TWO + name);
            return;
        }
        if (ownSound(name)) {
            return;
        }
        if (toSecond(name)) {
            second.clip(kind, name, PLAYER_TWO + name);
            return;
        }
        namedClip(kind, name, PLAYER_TWO + name, false);
    }

    /** Plays, loops or stops a clip by name on the main speaker, with the voice kept as {@code key}. */
    private void namedClip(int kind, String name, String key, boolean atCar) {
        int buffer = buffer(name);
        if (buffer == 0) {
            return;
        }
        int source = named.computeIfAbsent(key, n -> {
            int s = alGenSources();
            alSourcei(s, AL_BUFFER, buffer);
            if (!atCar) {
                flat(s);
            } else {
                world(s);
            }
            return s;
        });
        if (atCar) {
            namedAtPlayer.put(name, source);
        }
        namedGain(key, source);
        switch (kind) {
            case PLAY -> {
                alSourcei(source, AL_LOOPING, AL_FALSE);
                alSourceRewind(source);
                alSourcePlay(source);
            }
            case LOOP -> {
                alSourcei(source, AL_LOOPING, AL_TRUE);
                if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
                    alSourcePlay(source);
                }
            }
            default -> alSourceStop(source);
        }
    }

    private final Map<String, Integer> namedAtPlayer = new HashMap<>();

    /** A source heard as it is, centred, without HRTF: menus, countdown, wind. */
    private void flat(int s) {
        alSourcei(s, AL_SOURCE_RELATIVE, AL_TRUE);
        alSource3f(s, AL_POSITION, 0F, 0F, 0F);
        alSourcef(s, AL_ROLLOFF_FACTOR, 0F);
        alSourcef(s, AL_GAIN, flatGain);
        if (AL.getCapabilities().AL_SOFT_source_spatialize) {
            alSourcei(s, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL_FALSE);
        }
    }

    /** A source in the world: placed, quieter and duller with distance. */
    private void world(int s) {
        alSourcei(s, AL_SOURCE_RELATIVE, AL_FALSE);
        alSourcef(s, AL_REFERENCE_DISTANCE, FULL_VOLUME_DISTANCE);
        alSourcef(s, AL_MAX_DISTANCE, MAX_DISTANCE);
        alSourcef(s, AL_ROLLOFF_FACTOR, 1F);
        if (efx) {
            alSourcef(s, EXTEfx.AL_AIR_ABSORPTION_FACTOR, AIR_ABSORPTION);
            // the track's reverb, fainter with distance as the direct sound is
            alSourcef(s, EXTEfx.AL_ROOM_ROLLOFF_FACTOR, 1F);
            AL11.alSource3i(s, EXTEfx.AL_AUXILIARY_SEND_FILTER, reverbSlot, 0, EXTEfx.AL_FILTER_NULL);
            echoSend(s, -1);
        }
    }

    /** Source {@code s} sends to the echo as car {@code car} does (its surroundings); -1: not at all. */
    private void echoSend(int s, int car) {
        if (echoSlot == 0) {
            return;
        }
        if (car >= 0 && car < ALL_CARS) {
            AL11.alSource3i(s, EXTEfx.AL_AUXILIARY_SEND_FILTER, echoSlot, 1, echoFilter[car]);
        } else {
            AL11.alSource3i(s, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 1, EXTEfx.AL_FILTER_NULL);
        }
    }

    /** How much of the echo car {@code car} sends now, 0 to 1. */
    private float echoOf(int car) {
        return car >= 0 && car < ALL_CARS ? Math.min(1F, ECHO_PER_ENCLOSURE * echo[car]) : 0F;
    }

    /**
     * One reverb for the whole track, set from the reverb times Steam Audio
     * traces where the listener is; silent until it has some, and always
     * without Steam Audio.
     */
    private void openReverb() {
        reverbEffect = EXTEfx.alGenEffects();
        alGetError();
        EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        eaxReverb = alGetError() == AL_NO_ERROR;
        if (!eaxReverb) {
            EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
        }
        reverbSlot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSlotf(reverbSlot, EXTEfx.AL_EFFECTSLOT_GAIN, 0F);
        EXTEfx.alAuxiliaryEffectSloti(reverbSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);
        openEcho();
    }

    /** The cars' echo, on a send of its own beside the reverb's; without a second send there is none. */
    private void openEcho() {
        if (alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS) < 2) {
            return;
        }
        alGetError();
        echoEffect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(echoEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);
        if (alGetError() != AL_NO_ERROR) {
            EXTEfx.alDeleteEffects(echoEffect);
            return;
        }
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_DELAY, ECHO_DELAY);
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_LRDELAY, ECHO_SECOND);
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_FEEDBACK, ECHO_FEEDBACK);
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_DAMPING, ECHO_DAMPING);
        echoSlot = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSlotf(echoSlot, EXTEfx.AL_EFFECTSLOT_GAIN, reverbOn ? ECHO_LEVEL : 0F);
        EXTEfx.alAuxiliaryEffectSloti(echoSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, echoEffect);
        for (int i = 0; i < ALL_CARS; i++) {
            echoFilter[i] = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(echoFilter[i], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(echoFilter[i], EXTEfx.AL_LOWPASS_GAIN, 0F);
        }
    }

    /** Eases the reverb toward the place's reverb times and sets it when it has moved enough to hear. */
    /** Whether the reverb was last set switched on, so switching it applies at once. */
    private boolean reverbOnApplied = true;

    private void updateReverb(float dt) {
        if (reverbSlot == 0 || acoustics == null) {
            return;
        }
        float[] target = acoustics.reverbTimes();
        float k = 1F - (float) Math.exp(-dt / REVERB_SMOOTHING);
        enclosed += (acoustics.enclosure() - enclosed) * k;
        boolean moved = Math.abs(enclosed - enclosedApplied) > 0.02F || reverbOn != reverbOnApplied;
        if (echoSlot != 0 && reverbOn != reverbOnApplied) {
            EXTEfx.alAuxiliaryEffectSlotf(echoSlot, EXTEfx.AL_EFFECTSLOT_GAIN, reverbOn ? ECHO_LEVEL : 0F);
        }
        if (moved) {
            enclosedApplied = enclosed;
            reverbOnApplied = reverbOn;
        }
        for (int b = 0; b < 3; b++) {
            reverbNow[b] += (target[b] - reverbNow[b]) * k;
            moved |= Math.abs(reverbNow[b] - reverbApplied[b]) > 0.03F * Math.max(0.1F, reverbApplied[b]);
        }
        if (!moved) {
            return;
        }
        System.arraycopy(reverbNow, 0, reverbApplied, 0, 3);
        float mid = Math.max(0.1F, Math.min(20F, reverbNow[1]));
        float hf = Math.max(0.1F, Math.min(2F, reverbNow[2] / mid));
        if (eaxReverb) {
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_TIME, mid);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, hf);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_LFRATIO,
                    Math.max(0.1F, Math.min(2F, reverbNow[0] / mid)));
        } else {
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DECAY_TIME, mid);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DECAY_HFRATIO, hf);
        }
        // open ground stays dry; walls and a half-pipe around bring the reverb in
        EXTEfx.alAuxiliaryEffectSlotf(reverbSlot, EXTEfx.AL_EFFECTSLOT_GAIN, reverbOn ? enclosed * REVERB_LEVEL : 0F);
        EXTEfx.alAuxiliaryEffectSloti(reverbSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);
    }

    /**
     * Asks Steam Audio how much of each car gets through the stage to the
     * listener, and eases each car toward it. Your own car is never muffled:
     * the chase camera is not where you would hear it from.
     */
    private void updateOcclusion(float dt) {
        if (acoustics == null) {
            return;
        }
        SteamAcoustics.Stage s = stage;
        if (stageWanted && s != null) {
            stageWanted = false;
            acoustics.stage(s);
        }
        Moment m = current;
        int all = m == null || m.cars == null ? 0 : Math.min(ALL_CARS, m.cars.length);
        int cars = Math.min(MAX_CARS, all); // traced
        if (++tracedAt >= TRACE_EVERY && cars > 0) {
            tracedAt = 0;
            acoustics.listener(camX, camY, camZ, yawSin, yawCos, pitchSin, pitchCos);
            chooseTraced(m, all);
            for (int i = 0; i < cars; i++) {
                int car = tracedCar[i];
                Car c = m.cars[car];
                carTraced[i] = c.running && car != m.player;
                carAround[i] = c.running; // your own car's surroundings echo as the others' do
                carPlaces[3 * i] = c.x;
                carPlaces[3 * i + 1] = c.y;
                carPlaces[3 * i + 2] = c.z;
            }
            acoustics.direct(carPlaces, carTraced, carAround, cars, traced);
        }
        float ke = 1F - (float) Math.exp(-dt / ECHO_SMOOTHING);
        for (int car = 0; car < ALL_CARS; car++) {
            int i = tracedSlot[car]; // only a traced car's surroundings are known (the nearest of a big field)
            float around = reverbOn && i >= 0 && i < cars && carAround[i] ? acoustics.enclosure(i) : 0F;
            echo[car] += (around - echo[car]) * ke;
            if (echo[car] < 0.005F && around == 0F) {
                echo[car] = 0F;
            }
        }
        float k = 1F - (float) Math.exp(-dt / OCCLUSION_SMOOTHING);
        boolean occluding = occlusionOn;
        float[] paths = occluding && pathsOn ? acoustics.paths() : null;
        for (int car = 0; car < Math.max(MAX_CARS, all); car++) {
            int i = tracedSlot[car];
            if (i < 0 && occluding) {
                continue; // not among the nearest: it keeps what it had until it is near again
            }
            boolean heard = occluding && i >= 0 && i < cars && carTraced[i];
            for (int band = 0; band < 3; band++) {
                float target = heard ? traced[3 * i + band] : 1F;
                through[3 * car + band] += (target - through[3 * car + band]) * k;
            }
            int at = SteamAcoustics.PATH_FLOATS * i;
            boolean found = paths != null && heard && paths[at] > 0F;
            float mid = found ? paths[at + 2] : 0F;
            pathGain[car] += (mid - pathGain[car]) * k;
            pathTreble[car] += ((found && mid > 0F ? Math.min(1F, paths[at + 3] / mid) : 0F) - pathTreble[car]) * k;
            pathWeight[car] += ((found ? 1F : 0F) - pathWeight[car]) * k;
            if (found) {
                for (int axis = 0; axis < 3; axis++) {
                    pathFrom[3 * car + axis] += (paths[at + 4 + axis] - pathFrom[3 * car + axis]) * k;
                }
            }
        }
    }

    /**
     * Which cars Steam Audio traces, one per slot ({@link #tracedCar}, and
     * each car's slot in {@link #tracedSlot}, -1 for none): every car of a
     * race of up to {@link #MAX_CARS}, each in its own slot, or the
     * {@link #MAX_CARS} nearest the listener of a bigger one.
     */
    private void chooseTraced(Moment m, int cars) {
        if (cars <= MAX_CARS) {
            identityTraced();
            return;
        }
        for (int car = 0; car < cars; car++) {
            Car c = m.cars[car];
            float dx = c.x - camX;
            float dy = c.y - camY;
            float dz = c.z - camZ;
            carDistance[car] = dx * dx + dy * dy + dz * dz;
        }
        int count = 0;
        for (int car = 0; car < cars; car++) {
            // insertion into the nearest found so far, nearest first
            int at = count < MAX_CARS ? count++ : MAX_CARS;
            while (at > 0 && carDistance[tracedCar[at - 1]] > carDistance[car]) {
                if (at < MAX_CARS) {
                    tracedCar[at] = tracedCar[at - 1];
                }
                at--;
            }
            if (at < MAX_CARS) {
                tracedCar[at] = car;
            }
        }
        Arrays.fill(tracedSlot, -1);
        for (int i = 0; i < MAX_CARS; i++) {
            tracedSlot[tracedCar[i]] = i;
        }
    }

    /** Car i in slot i, as in every race of up to {@link #MAX_CARS} cars. */
    private void identityTraced() {
        Arrays.fill(tracedSlot, -1);
        for (int i = 0; i < MAX_CARS; i++) {
            tracedCar[i] = i;
            tracedSlot[i] = i;
        }
    }

    /*
     * Rays straight through a hill say nearly nothing gets through, but sound
     * bends over a crest and around the ends of a wall. A car fully behind
     * something is muffled, not cut: 9 dB quieter and losing most of its
     * treble; foliage and glass, which let sound through, muffle less. Where
     * Steam Audio finds the way round (its paths between the stage's baked
     * probes), the car is as loud and as bright as that path lets it be, if
     * that is more, and is heard from where the path comes from: over the
     * crest, round the end of the wall.
     */
    private static final float BLOCKED_GAIN = 0.35F;
    private static final float BLOCKED_TREBLE = 0.15F;
    /* per car, eased like through: how much of it the path round brings (mid band), its treble relative to that,
       how sure the path is (0 none to 1 found), and the direction it arrives from in the game's world */
    private final float[] pathGain = new float[ALL_CARS];
    private final float[] pathTreble = new float[ALL_CARS];
    private final float[] pathWeight = new float[ALL_CARS];
    private final float[] pathFrom = new float[3 * ALL_CARS];

    /** How much of a car the stage hides, 0 in the clear to 1 fully behind something. */
    private float hidden(int car) {
        return car >= 0 && car < ALL_CARS ? 1F - Math.max(0F, Math.min(1F, through[3 * car + 1])) : 0F;
    }

    /** A car's overall level as the stage lets it through. */
    private float throughGain(int car) {
        float blocked = car >= 0 && car < ALL_CARS ? Math.max(BLOCKED_GAIN, pathGain[car]) : BLOCKED_GAIN;
        return 1F - (1F - blocked) * hidden(car);
    }

    /** A car's treble as the stage lets it through, relative to {@link #throughGain}. */
    private float throughTreble(int car) {
        float blocked = car >= 0 && car < ALL_CARS ? Math.max(BLOCKED_TREBLE, pathTreble[car]) : BLOCKED_TREBLE;
        return 1F - (1F - blocked) * hidden(car);
    }

    /**
     * Turns the way to a car, {@code d} (from the listener, in the game's
     * world), toward the way its sound comes round the stage, as far as the
     * car is hidden and a path was found; the distance stays.
     */
    private void bendTowardPath(int car, float[] d) {
        float w = car >= 0 && car < ALL_CARS ? hidden(car) * pathWeight[car] : 0F;
        float length = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (w < 0.01F || length < 1e-3F) {
            return;
        }
        float bx = d[0] / length + (pathFrom[3 * car] - d[0] / length) * w;
        float by = d[1] / length + (pathFrom[3 * car + 1] - d[1] / length) * w;
        float bz = d[2] / length + (pathFrom[3 * car + 2] - d[2] / length) * w;
        float b = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (b > 1e-3F) {
            d[0] = bx / b * length;
            d[1] = by / b * length;
            d[2] = bz / b * length;
        }
    }

    private final float[] toCar = new float[3];

    private void stopEverything() {
        if (context == 0L) {
            return;
        }
        if (second != null) {
            second.stopAll();
        }
        for (int source : named.values()) {
            alSourceStop(source);
        }
        for (Voice v : effects) {
            if (v != null) {
                alSourceStop(v.source);
                v.car = -1;
                v.fixed = false;
            }
        }
        for (EngineVoices e : engines) {
            e.silence();
        }
        synchronized (bedClip) {
            java.util.Arrays.fill(bedClip, null);
            java.util.Arrays.fill(bedGain, 0F);
        }
        for (BedVoice b : beds) {
            if (b != null) {
                b.silence();
            }
        }
    }

    /* ------------------------------------------- crashes and skids */

    /**
     * Each car's crash and skid state, as the game keeps it for the player:
     * a hit within two ticks of another is not heard again, a skid only after
     * 35 quiet ticks, and the three recordings of each take turns.
     */
    private static final class CarEffects {
        int crashWait;
        int skidWait;
        int crashTurn;
        int skidTurn;
        int dustTurn;
    }

    private CarEffects effectsOf(int car) {
        if (car < 0 || car >= carEffects.length) {
            return null;
        }
        if (carEffects[car] == null) {
            carEffects[car] = new CarEffects();
        }
        return carEffects[car];
    }

    /** The exhaust pops' recordings at the rebuilt sound's rate, made the first time one pops (the sound thread's). */
    private float[][] popRecordings;
    private float[] backfireRecording;

    /** Extra clip {@code name} as samples from -1 to 1 at the rebuilt sound's rate; null if there is none. */
    private static float[] atGrainRate(String name) {
        short[] pcm = extraClips.get(name);
        Integer rate = extraRates.get(name);
        if (pcm == null || rate == null || pcm.length < 2) {
            return null;
        }
        int n = (int) ((long) pcm.length * EngineGrains.RATE / rate);
        float[] out = new float[n];
        double step = (double) rate / EngineGrains.RATE;
        for (int i = 0; i < n; i++) {
            double at = i * step;
            int a = Math.min(pcm.length - 2, (int) at);
            float f = (float) (at - a);
            out[i] = (pcm[a] * (1F - f) + pcm[a + 1] * f) / 32_768F;
        }
        return out;
    }

    private float[][] popRecordings() {
        if (popRecordings == null) {
            List<float[]> found = new ArrayList<>();
            for (String name : EngineLayers.POP_CLIPS) {
                float[] x = atGrainRate(name);
                if (x != null) {
                    found.add(x);
                }
            }
            backfireRecording = atGrainRate(EngineLayers.BACKFIRE_CLIP);
            if (found.isEmpty()) {
                return new float[0][]; // not given yet: look again at the next pop
            }
            popRecordings = found.toArray(new float[0][]);
        }
        return popRecordings;
    }

    /** A car's exhaust pops: from its exhaust with the rebuilt sound, else at the car. */
    private void pop(int car, float gain, boolean big) {
        float[][] pops = popRecordings();
        float[] clip = big && backfireRecording != null ? backfireRecording
                : pops.length == 0 ? null : pops[random.nextInt(pops.length)];
        if (clip == null || car < 0 || car >= engines.size()) {
            return;
        }
        RebuiltVoices rebuilt = engines.get(car).rebuilt;
        if (rebuilt != null && rebuilt.level > 0F) {
            rebuilt.tone.pop(clip, gain);
        } else {
            playAt(car, big ? EngineLayers.BACKFIRE_CLIP : EngineLayers.POP_CLIPS[random.nextInt(pops.length)], gain,
                    variation());
        }
    }

    private void crash(int car, float force, int kind) {
        CarEffects e = effectsOf(car);
        Moment m = current;
        if (e == null || e.crashWait != 0 || m == null || m.muted) {
            return;
        }
        float f = Math.abs(force);
        if (kind == 1) {
            playAt(car, "tires", 1F, 1F);
            e.crashWait = 3;
            return;
        }
        int turn = kind == 0 ? e.crashTurn : 2;
        if (f > 25F && f < 170F) {
            // a tap is quiet, a near-crash nearly full
            playAt(car, "lowcrash" + turn, 0.45F + 0.55F * (f - 25F) / 145F, variation());
            e.crashWait = 2;
        } else if (f >= 170F) {
            playAt(car, "crash" + turn, 1F, variation());
            e.crashWait = 2;
            duck = Math.max(duck, DUCK_DEPTH / Math.max(1F, distanceToCar(car) / FULL_VOLUME_DISTANCE));
        }
        if (kind == 0 && f > 25F) {
            e.crashTurn = (e.crashTurn + 1) % 3;
        }
    }

    private void skid(int car, int surface) {
        CarEffects e = effectsOf(car);
        Moment m = current;
        if (e == null || e.crashWait != 0 || e.skidWait != 0 || m == null || m.muted) {
            return;
        }
        if (m.cars != null && car < m.cars.length && m.cars[car] != null && m.cars[car].rebuilt) {
            return; // the rebuilt sound plays each wheel's skid itself
        }
        if (surface == 0) {
            playAt(car, "skid" + e.skidTurn, 1F, variation());
            e.skidTurn = (e.skidTurn + 1) % 3;
        } else {
            playAt(car, "dustskid" + e.dustTurn, 1F, variation());
            e.dustTurn = (e.dustTurn + 1) % 3;
        }
        e.skidWait = 35;
    }

    /** A small change of pitch, so a sound heard twice is not the same. */
    private float variation() {
        return 0.96F + random.nextFloat() * 0.08F;
    }

    /** One effect voice: a source, and the car it follows (-1 for none) or the place it is held at. */
    private static final class Voice {
        final int source;
        /** Muffles the voice as its car is muffled; 0 without EFX. */
        final int filter;
        int car = -1;
        float gain;
        /** Held at a place in the world (px, py, pz) rather than following a car. */
        boolean fixed;
        float px;
        float py;
        float pz;

        Voice(int source, int filter) {
            this.source = source;
            this.filter = filter;
            if (filter != 0) {
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
        }
    }

    /** Sets a voice's filter from its car's occlusion. */
    private void muffle(Voice v) {
        if (v.filter != 0) {
            EXTEfx.alFilterf(v.filter, EXTEfx.AL_LOWPASS_GAIN, throughGain(v.car));
            EXTEfx.alFilterf(v.filter, EXTEfx.AL_LOWPASS_GAINHF, throughTreble(v.car));
            alSourcei(v.source, EXTEfx.AL_DIRECT_FILTER, v.filter);
        }
    }

    /** Plays a clip at a car, taking a free voice or the one heard least. */
    private void playAt(int car, String name, float gain, float pitch) {
        int buffer = buffer(name);
        if (buffer == 0) {
            return;
        }
        Voice voice = freeVoice();
        if (voice == null) {
            return;
        }
        voice.fixed = false;
        alSourceStop(voice.source);
        alSourcei(voice.source, AL_BUFFER, buffer);
        world(voice.source);
        gain *= loud(name) ? 1F : everyday;
        alSourcef(voice.source, AL_GAIN, gain * bus[BUS_EFFECTS]);
        alSourcef(voice.source, AL_PITCH, pitch);
        voice.car = car;
        voice.gain = gain;
        muffle(voice);
        echoSend(voice.source, car); // a crash between walls comes back from them
        placeAtCar(voice.source, car);
        alSourcePlay(voice.source);
    }

    /** A free effect voice, or the one heard least. */
    private Voice freeVoice() {
        Voice voice = null;
        float weakest = Float.MAX_VALUE;
        for (Voice v : effects) {
            if (alGetSourcei(v.source, AL_SOURCE_STATE) != AL_PLAYING) {
                return v;
            }
            float distance = v.fixed ? distanceTo(v.px, v.py, v.pz) : distanceToCar(v.car);
            float heard = v.gain / Math.max(1F, distance / FULL_VOLUME_DISTANCE);
            if (heard < weakest) {
                weakest = heard;
                voice = v;
            }
        }
        return voice;
    }

    /** Plays a clip held at a place in the world (an extra's blast), taking a free voice or the one heard least. */
    private void playAtPoint(float x, float y, float z, String name, float gain, float pitch) {
        int buffer = buffer(name);
        if (buffer == 0) {
            return;
        }
        Voice voice = freeVoice();
        if (voice == null) {
            return;
        }
        alSourceStop(voice.source);
        alSourcei(voice.source, AL_BUFFER, buffer);
        world(voice.source);
        gain *= everyday;
        alSourcef(voice.source, AL_GAIN, gain * bus[BUS_EFFECTS]);
        alSourcef(voice.source, AL_PITCH, pitch);
        if (voice.filter != 0) {
            alSourcei(voice.source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL); // not muffled as a car was
        }
        voice.car = -1;
        voice.gain = gain;
        voice.fixed = true;
        voice.px = x;
        voice.py = y;
        voice.pz = z;
        placeAtPoint(voice);
        alSourcePlay(voice.source);
    }

    /** Places a voice held at a place in the world, from where the camera is now. */
    private void placeAtPoint(Voice v) {
        turn(v.px - camX, v.py - camY, v.pz - camZ, where);
        alSource3f(v.source, AL_POSITION, where[0], where[1], where[2]);
        alSource3f(v.source, AL_VELOCITY, 0F, 0F, 0F);
    }

    private float distanceTo(float x, float y, float z) {
        float dx = x - camX;
        float dy = y - camY;
        float dz = z - camZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /* ---------------------------------------------------------- update */

    /** Positions, velocities and engines, from the last tick carried on to now. */
    private void update(long now) {
        Moment m = latest;
        if (m == null) {
            return;
        }
        if (m != current) {
            previous = current;
            current = m;
            tick();
        }
        float ahead = Math.min(m.tickNanos, Math.max(0L, now - m.nanos)) / 1e9F;
        camera(ahead);
        int cars = m.cars == null ? 0 : m.cars.length;
        while (engines.size() < cars) {
            engines.add(new EngineVoices());
        }
        float dt = UPDATE_NANOS / 1e9F;
        updateOcclusion(dt);
        updateReverb(dt);
        engineLevel = (1F - duck) * bus[BUS_ENGINES] * everyday;
        duck *= (float) Math.exp(-dt / DUCK_RELEASE);
        for (int i = 0; i < engines.size(); i++) {
            engines.get(i).update(i < cars ? m.cars[i] : null, i, ahead, dt, m.muted);
        }
        updateBeds(dt, m.muted);
        for (Voice v : effects) {
            if (v.car >= 0 && alGetSourcei(v.source, AL_SOURCE_STATE) == AL_PLAYING) {
                placeAtCar(v.source, v.car);
                muffle(v);
            } else if (v.fixed && alGetSourcei(v.source, AL_SOURCE_STATE) == AL_PLAYING) {
                placeAtPoint(v); // the camera moves past it
            }
        }
        for (int source : namedAtPlayer.values()) {
            placeAtCar(source, m.player);
        }
        if (DEBUG && now - debugAt > 1_000_000_000L) {
            debugAt = now;
            StringBuilder b = new StringBuilder("[sound] ");
            for (int i = 0; i < Math.min(cars, engines.size()); i++) {
                EngineVoices e = engines.get(i);
                Car c = m.cars[i];
                b.append(String.format(Locale.ROOT, "car%d %s%s%s v%.0f at(%.0f,%.0f,%.0f) g[%.2f %.2f %.2f %.2f %.2f] | ",
                        i, c.running ? "R" : "-", c.throttle ? "T" : "-", c.grounded ? "G" : "-", c.speed,
                        e.position[0], e.position[1], e.position[2], e.gain[0], e.gain[1], e.gain[2], e.gain[3],
                        e.gain[4]));
            }
            if (acoustics != null) {
                StringBuilder around = new StringBuilder();
                for (int i = 0; i < Math.min(cars, MAX_CARS); i++) {
                    around.append(String.format(Locale.ROOT, "%s%.2f", i == 0 ? "" : " ", echo[i]));
                }
                b.append(String.format(Locale.ROOT, "through[%s] rt[%.2f %.2f %.2f] closed %.2f around[%s] %s",
                        throughList(cars), reverbNow[0], reverbNow[1], reverbNow[2], enclosed, around,
                        acoustics.describe()));
            }
            System.out.println(b);
        }
    }

    private long debugAt;

    private String throughList(int cars) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(cars, ALL_CARS); i++) {
            b.append(String.format(Locale.ROOT, "%s%.2f", i == 0 ? "" : " ", through[3 * i + 1]));
        }
        return b.toString();
    }

    /** A new tick: count down each car's crash and skid waits, as the game does once a tick. */
    private void tick() {
        for (CarEffects e : carEffects) {
            if (e != null) {
                if (e.crashWait > 0) {
                    e.crashWait--;
                }
                if (e.skidWait > 0) {
                    e.skidWait--;
                }
            }
        }
    }

    /* the camera now, carried on from the last tick, and how it turns */
    private float camX;
    private float camY;
    private float camZ;
    private float yawSin;
    private float yawCos;
    private float pitchSin;
    private float pitchCos;
    private final float[] scratch = new float[3];

    private void camera(float ahead) {
        Moment m = current;
        float vx = 0F;
        float vy = 0F;
        float vz = 0F;
        Moment p = previous;
        if (p != null) {
            float seconds = m.tickNanos / 1e9F;
            float dx = m.cameraX - p.cameraX;
            float dy = m.cameraY - p.cameraY;
            float dz = m.cameraZ - p.cameraZ;
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < JUMP && seconds > 0F) {
                vx = dx / seconds;
                vy = dy / seconds;
                vz = dz / seconds;
            }
        }
        camX = m.cameraX + vx * ahead;
        camY = m.cameraY + vy * ahead;
        camZ = m.cameraZ + vz * ahead;
        double yaw = Math.toRadians(m.yaw);
        double pitch = Math.toRadians(m.pitch);
        yawSin = (float) Math.sin(yaw);
        yawCos = (float) Math.cos(yaw);
        pitchSin = (float) Math.sin(pitch);
        pitchCos = (float) Math.cos(pitch);
        // the listener stays at the origin looking down -z; the world turns around it
        alListener3f(AL_POSITION, 0F, 0F, 0F);
        turn(vx, vy, vz, scratch);
        alListener3f(AL_VELOCITY, scratch[0], scratch[1], scratch[2]);
    }

    /**
     * Turns a world vector into the listener's view exactly as the renderer
     * turns points (Utility.rot by Medium.xz, then Medium.zy), then into
     * OpenAL's axes (y up, -z ahead), so a sound comes from where its car is
     * drawn.
     */
    private void turn(float x, float y, float z, float[] out) {
        float x1 = x * yawCos - z * yawSin;
        float z1 = x * yawSin + z * yawCos;
        float y2 = y * pitchCos - z1 * pitchSin;
        float z2 = y * pitchSin + z1 * pitchCos;
        out[0] = x1;
        out[1] = -y2;
        out[2] = -z2;
    }

    /** Where a car is now, carried on from its last tick by its speed then, and that velocity. */
    private boolean carNow(int car, float ahead, float[] position, float[] velocity) {
        Moment m = current;
        if (m == null || m.cars == null || car < 0 || car >= m.cars.length) {
            return false;
        }
        Car c = m.cars[car];
        float vx = 0F;
        float vy = 0F;
        float vz = 0F;
        Moment p = previous;
        if (p != null && p.cars != null && car < p.cars.length) {
            Car b = p.cars[car];
            float seconds = m.tickNanos / 1e9F;
            float dx = c.x - b.x;
            float dy = c.y - b.y;
            float dz = c.z - b.z;
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < JUMP && seconds > 0F) {
                vx = dx / seconds;
                vy = dy / seconds;
                vz = dz / seconds;
            }
        }
        toCar[0] = c.x + vx * ahead - camX;
        toCar[1] = c.y + vy * ahead - camY;
        toCar[2] = c.z + vz * ahead - camZ;
        bendTowardPath(car, toCar);
        turn(toCar[0], toCar[1], toCar[2], position);
        turn(vx, vy, vz, velocity);
        return true;
    }

    private final float[] where = new float[3];
    private final float[] moving = new float[3];

    private void placeAtCar(int source, int car) {
        Moment m = current;
        float ahead = m == null ? 0F : Math.min(m.tickNanos, Math.max(0L, clock - m.nanos)) / 1e9F;
        if (carNow(car, ahead, where, moving)) {
            alSource3f(source, AL_POSITION, where[0], where[1], where[2]);
            alSource3f(source, AL_VELOCITY, moving[0], moving[1], moving[2]);
        }
    }

    private float distanceToCar(int car) {
        Moment m = current;
        if (m == null || m.cars == null || car < 0 || car >= m.cars.length) {
            return MAX_DISTANCE;
        }
        Car c = m.cars[car];
        float dx = c.x - camX;
        float dy = c.y - camY;
        float dz = c.z - camZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /* ---------------------------------------------------------- engines */

    /** How loud a held powerup hums, against the game's effects, and how quickly it fades in and out, in seconds. */
    private static final float HUM_LEVEL = 0.45F;
    private static final float HUM_SMOOTHING = 0.15F;

    /**
     * The hum of the powerup a car holds (the powerups extra): one looping
     * source of its own that follows the car, fading in when the car picks a
     * powerup up and out when it uses it.
     */
    private final class HumVoice {
        final int source;
        /** The hum whose clip is on the source: 1 to 3, 0 for none yet. */
        int playing;
        float gain;

        HumVoice() {
            source = alGenSources();
            world(source);
            alSourcei(source, AL_LOOPING, AL_TRUE);
        }

        /** Hums {@code kind} (0 for none) from {@code position}, moving at {@code velocity}; null keeps the place. */
        void update(int kind, float[] position, float[] velocity, float dt) {
            if (kind != 0 && kind != playing) {
                int buffer = buffer("pwr-hum" + kind);
                if (buffer == 0) {
                    kind = 0;
                } else {
                    alSourceStop(source);
                    alSourcei(source, AL_BUFFER, buffer);
                    playing = kind;
                    gain = 0F;
                }
            }
            float target = kind != 0 ? 1F : 0F;
            gain += (target - gain) * (1F - (float) Math.exp(-dt / HUM_SMOOTHING));
            boolean on = alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
            if (target == 0F && gain < 0.002F) {
                if (on) {
                    alSourcePause(source);
                }
                return;
            }
            alSourcef(source, AL_GAIN, gain * HUM_LEVEL * bus[BUS_EFFECTS] * everyday);
            if (position != null) {
                alSource3f(source, AL_POSITION, position[0], position[1], position[2]);
                alSource3f(source, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
            }
            if (!on) {
                alSourcePlay(source);
            }
        }

        void silence() {
            gain = 0F;
            alSourceStop(source);
        }

        void delete() {
            alDeleteSources(source);
        }
    }

    /** The loops laid under a damaged car's engine: a broken exhaust's blat, and an engine running harsh. */
    public static final String RASP_CLIP = "xtra-rasp";
    public static final String HARSH_CLIP = "xtra-harsh";
    /** How loud they are against the engines, and how quickly they follow the car, in seconds. */
    private static final float ROUGH_LEVEL = 0.9F;
    private static final float ROUGH_SMOOTHING = 0.18F;

    /**
     * One of those loops on a car (the damage extra): a looping source that
     * follows the car, louder on the throttle, its pitch rising with the
     * revs from the car's own.
     */
    private final class RoughVoice {
        final int source;
        final String clip;
        boolean loaded;
        float gain;

        RoughVoice(String clip) {
            this.clip = clip;
            source = alGenSources();
            world(source);
            alSourcei(source, AL_LOOPING, AL_TRUE);
        }

        /** Plays at {@code level} (0 for none) and {@code pitch} from {@code position}; null keeps the place. */
        void update(float level, float pitch, float throttle, float[] position, float[] velocity, float dt) {
            if (level > 0F && !loaded) {
                int buffer = buffer(clip);
                if (buffer == 0) {
                    level = 0F;
                } else {
                    alSourcei(source, AL_BUFFER, buffer);
                    loaded = true;
                }
            }
            float target = level * (0.45F + 0.55F * throttle);
            gain += (target - gain) * (1F - (float) Math.exp(-dt / ROUGH_SMOOTHING));
            boolean on = alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
            if (!loaded || target == 0F && gain < 0.002F) {
                if (on) {
                    alSourcePause(source);
                }
                return;
            }
            alSourcef(source, AL_GAIN, gain * ROUGH_LEVEL * engineLevel);
            alSourcef(source, AL_PITCH, Math.max(0.5F, Math.min(2F, pitch)));
            if (position != null) {
                alSource3f(source, AL_POSITION, position[0], position[1], position[2]);
                alSource3f(source, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
            }
            if (!on) {
                alSourcePlay(source);
            }
        }

        void silence() {
            gain = 0F;
            alSourceStop(source);
        }

        void delete() {
            alDeleteSources(source);
        }
    }

    /** The clips of nitrous burning: gas rushing out, and an afterburner. */
    public static final String NITRO_HISS = "pwr-nitro-hiss";
    public static final String NITRO_BURNER = "pwr-nitro-burner";
    /** How loud nitrous burns, against the game's effects, and how quickly its two parts follow the car, in seconds. */
    private static final float NITRO_LEVEL = 0.8F;
    private static final float NITRO_SMOOTHING = 0.12F;
    /**
     * The four sounds of nitrous burning, each a mix of the two recordings:
     * the hiss's gain and pitch, the afterburner's gain and pitch. On the
     * ground it is the gas alone; in the air some of it gives way to the
     * afterburner; two at once are lower and louder, and in the air mostly
     * afterburner.
     */
    private static final float[][] NITRO_MIX = {
        {0F, 1F, 0F, 1F},
        {1F, 1F, 0F, 1F},
        {0.7F, 1F, 0.6F, 1F},
        {1.3F, 0.8F, 0F, 1F},
        {0.9F, 0.8F, 1F, 1F},
    };

    /** The mix of nitrous sound {@code kind} (see {@link Car#nitro}): the hiss's gain and pitch, the afterburner's gain and pitch. */
    public static float[] nitroMix(int kind) {
        return NITRO_MIX[kind < 0 || kind >= NITRO_MIX.length ? 0 : kind].clone();
    }

    /**
     * Nitrous burning in a car (the powerups extra): two looping sources
     * that follow the car, the gas hiss and the afterburner, mixed by which
     * of the four sounds it is, so a jump or a second nitrous crosses over
     * without a break.
     */
    private final class NitroVoice {
        final int hiss;
        final int burner;
        boolean loaded;
        final float[] now = {0F, 1F, 0F, 1F};

        NitroVoice() {
            hiss = alGenSources();
            burner = alGenSources();
            world(hiss);
            world(burner);
            alSourcei(hiss, AL_LOOPING, AL_TRUE);
            alSourcei(burner, AL_LOOPING, AL_TRUE);
        }

        /** Burns as {@code kind} (0 for none) from {@code position}, moving at {@code velocity}; null keeps the place. */
        void update(int kind, float[] position, float[] velocity, float dt) {
            if (!loaded && kind != 0) {
                int a = buffer(NITRO_HISS);
                int b = buffer(NITRO_BURNER);
                if (a == 0 || b == 0) {
                    return;
                }
                alSourcei(hiss, AL_BUFFER, a);
                alSourcei(burner, AL_BUFFER, b);
                loaded = true;
            }
            if (!loaded) {
                return;
            }
            float[] want = NITRO_MIX[kind < 0 || kind >= NITRO_MIX.length ? 0 : kind];
            float k = 1F - (float) Math.exp(-dt / NITRO_SMOOTHING);
            now[0] += (want[0] - now[0]) * k;
            now[2] += (want[2] - now[2]) * k;
            if (kind != 0) {
                // the pitch only while it burns: fading out it keeps the pitch it had
                now[1] += (want[1] - now[1]) * k;
                now[3] += (want[3] - now[3]) * k;
            }
            part(hiss, now[0], now[1], position, velocity);
            part(burner, now[2], now[3], position, velocity);
        }

        private void part(int source, float gain, float pitch, float[] position, float[] velocity) {
            boolean on = alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
            if (gain < 0.002F) {
                if (on) {
                    alSourcePause(source);
                }
                return;
            }
            alSourcef(source, AL_GAIN, gain * NITRO_LEVEL * bus[BUS_EFFECTS] * everyday);
            alSourcef(source, AL_PITCH, pitch);
            if (position != null) {
                alSource3f(source, AL_POSITION, position[0], position[1], position[2]);
                alSource3f(source, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
            }
            if (!on) {
                alSourcePlay(source);
            }
        }

        void silence() {
            now[0] = 0F;
            now[2] = 0F;
            alSourceStop(hiss);
            alSourceStop(burner);
        }

        void delete() {
            alDeleteSources(hiss);
            alDeleteSources(burner);
        }
    }

    /**
     * One car's engine: its five recordings, all looping, mixed by gain and
     * bent by pitch, and a low-pass filter that dulls them off the throttle.
     */
    private final class EngineVoices {
        final int[] sources = new int[5];
        final float[] gain = new float[5];
        final float[] pitch = {1F, 1F, 1F, 1F, 1F};
        final float[] targetGain = new float[5];
        final float[] targetPitch = {1F, 1F, 1F, 1F, 1F};
        int engine = -1;
        int filter;
        float throttle;
        float appliedHf = -1F;
        float appliedLf = -1F;
        /** How much of the echo its sources were last set to send; -1: not set yet. */
        float appliedEcho = -1F;
        final float[] position = new float[3];
        final float[] velocity = new float[3];
        /** The rebuilt sound's voices for this car, made the first time the extra plays it. */
        RebuiltVoices rebuilt;
        /** True once the five recordings are paused for the rebuilt sound: only the classic mix plays them again. */
        boolean classicPaused;
        /** The hum of the powerup the car holds, made the first time it holds one. */
        HumVoice hum;
        /** Nitrous burning in the car, made the first time it burns. */
        NitroVoice nitro;
        /** A broken exhaust's blat and a harsh engine's crackle, each made the first time the car has it. */
        RoughVoice rasp;
        RoughVoice harsh;

        EngineVoices() {
            for (int i = 0; i < 5; i++) {
                sources[i] = alGenSources();
                world(sources[i]);
                alSourcei(sources[i], AL_LOOPING, AL_TRUE);
            }
            if (efx) {
                filter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            }
        }

        void update(Car car, int index, float ahead, float dt, boolean muted) {
            if (car == null || car.bands == null) {
                fadeOut(dt);
                if (rebuilt != null) {
                    rebuilt.quiet(dt);
                }
                if (hum != null) {
                    hum.update(0, null, null, dt);
                }
                if (nitro != null) {
                    nitro.update(0, null, null, dt);
                }
                if (rasp != null) {
                    rasp.update(0F, 1F, 0F, null, null, dt);
                }
                if (harsh != null) {
                    harsh.update(0F, 1F, 0F, null, null, dt);
                }
                return;
            }
            if (car.engine != engine) {
                setEngine(car.engine);
            }
            semitones = car.rebuilt ? kitOf(index).revs : BEND_SEMITONES;
            mix(car, muted);
            if (!carNow(index, ahead, position, velocity)) {
                return;
            }
            float distance = (float) Math.sqrt(position[0] * position[0] + position[1] * position[1]
                    + position[2] * position[2]);
            if (distance > ENGINE_DETAIL_DISTANCE) {
                loudestOnly();
            }
            if (car.hum != 0 || hum != null) {
                if (hum == null) {
                    hum = new HumVoice();
                }
                hum.update(muted || !car.running ? 0 : car.hum, position, velocity, dt);
            }
            if (car.nitro != 0 || nitro != null) {
                if (nitro == null) {
                    nitro = new NitroVoice();
                }
                nitro.update(muted || !car.running ? 0 : car.nitro, position, velocity, dt);
            }
            if (car.rasp > 0F || rasp != null) {
                if (rasp == null) {
                    rasp = new RoughVoice(RASP_CLIP);
                }
                rasp.update(muted || !car.running ? 0F : car.rasp, car.raspPitch * (0.75F + 0.6F * bandShare(car)),
                        throttle, position, velocity, dt);
            }
            if (car.harsh > 0F || harsh != null) {
                if (harsh == null) {
                    harsh = new RoughVoice(HARSH_CLIP);
                }
                harsh.update(muted || !car.running ? 0F : car.harsh,
                        car.raspPitch * (0.7F + 0.5F * bandShare(car)), throttle, position, velocity, dt);
            }
            float k = 1F - (float) Math.exp(-dt / ENGINE_SMOOTHING);
            float kt = 1F - (float) Math.exp(-dt / THROTTLE_SMOOTHING);
            throttle += ((car.throttle ? 1F : 0F) - throttle) * kt;
            // duller off the throttle, and muffled by whatever of the stage is in the way
            float hf = (0.3F + 0.7F * throttle) * throughTreble(index);
            float lf = throughGain(index);
            if (efx && (Math.abs(hf - appliedHf) > 0.01F || Math.abs(lf - appliedLf) > 0.01F)) {
                EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, lf);
                EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, hf);
                for (int s : sources) {
                    alSourcei(s, EXTEfx.AL_DIRECT_FILTER, filter);
                }
                if (rebuilt != null) {
                    rebuilt.filter(filter);
                }
                appliedHf = hf;
                appliedLf = lf;
            }
            if (car.rebuilt || rebuilt != null) {
                if (rebuilt == null) {
                    rebuilt = new RebuiltVoices(filter);
                    appliedEcho = -1F; // its sources send to the echo as the car's others do
                }
            }
            float sent = echoOf(index);
            if (echoSlot != 0 && (Math.abs(sent - appliedEcho) > 0.03F || sent == 0F && appliedEcho != 0F)) {
                // what stands around the car throws its engine back (a filter is copied when it is attached)
                EXTEfx.alFilterf(echoFilter[index], EXTEfx.AL_LOWPASS_GAIN, sent);
                for (int s : sources) {
                    echoSend(s, index);
                }
                if (rebuilt != null) {
                    echoSend(rebuilt.front.source, index);
                    echoSend(rebuilt.back.source, index);
                }
                appliedEcho = sent;
            }
            if (car.rebuilt || rebuilt != null) {
                if (car.rebuilt) {
                    // the same mix, played one cycle at a time: the five recordings fall silent
                    for (int i = 0; i < 5; i++) {
                        gain[i] += (targetGain[i] - gain[i]) * k;
                        pitch[i] += (targetPitch[i] - pitch[i]) * k;
                        if (!classicPaused && alGetSourcei(sources[i], AL_SOURCE_STATE) == AL_PLAYING) {
                            alSourcePause(sources[i]);
                        }
                    }
                    classicPaused = true;
                    rebuilt.play(car, index, dt, gain, pitch, throttle, position, velocity, muted);
                    return;
                }
                rebuilt.quiet(dt);
            }
            classicPaused = false;
            for (int i = 0; i < 5; i++) {
                gain[i] += (targetGain[i] - gain[i]) * k;
                pitch[i] += (targetPitch[i] - pitch[i]) * k;
                int s = sources[i];
                boolean playing = alGetSourcei(s, AL_SOURCE_STATE) == AL_PLAYING;
                if (gain[i] < 0.002F && targetGain[i] == 0F) {
                    if (playing) {
                        alSourcePause(s);
                    }
                    continue;
                }
                alSourcef(s, AL_GAIN, gain[i] * engineLevel);
                alSourcef(s, AL_PITCH, Math.max(0.5F, Math.min(2F, pitch[i])));
                alSource3f(s, AL_POSITION, position[0], position[1], position[2]);
                alSource3f(s, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
                if (!playing) {
                    alSourcePlay(s);
                }
            }
        }

        /**
         * Sets the target mix from the car's state, keeping what each of the
         * game's recordings stands for: 0 idle (off the throttle or in the
         * air), 1 to 3 rising through a speed band, 4 wheelspin and full power.
         */
        private void mix(Car car, boolean muted) {
            Arrays.fill(targetGain, 0F);
            Arrays.fill(targetPitch, 1F);
            if (!car.running || muted) {
                return;
            }
            float s = Math.abs(car.speed);
            int[] b = car.bands;
            float t = bandShare(car);
            float p = Math.min(2.999F, 3F * t); // where in the band: 0 to 3, a third per recording
            if (!car.grounded) {
                // with the rebuilt sound the revs run up to the limiter while the throttle is held in the air
                float air = car.rebuilt && rebuilt != null ? rebuilt.layers.air() : 0F;
                targetGain[0] = (float) Math.cos(air * Math.PI / 2);
                targetPitch[0] = 1F + 0.15F * t;
                targetGain[4] = (float) Math.sin(air * Math.PI / 2);
                targetPitch[4] = bend(1.5F * air - 0.5F);
                return;
            }
            if (car.spinning && car.throttle) {
                targetGain[4] = 1F;
                targetPitch[4] = bend(p - 1.5F) * 0.97F;
                return;
            }
            if (car.fullPower && car.throttle) {
                if (s > b[1]) {
                    targetGain[4] = 1F;
                } else {
                    targetGain[3] = 1F;
                }
                return;
            }
            // the three rising recordings, cross-faded at equal power, each bent around its middle
            float f = Math.max(0F, Math.min(2F, p - 0.5F));
            int low = Math.min(1, (int) f);
            float w = f - low;
            float idle = s < b[0] * 0.08F ? 1F - s / (b[0] * 0.08F) : 0F;
            float band = car.throttle ? 1F : 0.5F;
            targetGain[1 + low] = band * (float) Math.cos(w * Math.PI / 2) * (1F - idle);
            targetGain[2 + low] = band * (float) Math.sin(w * Math.PI / 2) * (1F - idle);
            for (int i = 1; i <= 3; i++) {
                targetPitch[i] = bend(p - (i - 0.5F));
            }
            targetGain[0] = Math.max(idle, car.throttle ? 0F : 0.6F);
        }

        /** Semitones a recording bends across a third of a band: the car's own with the rebuilt sound. */
        float semitones = BEND_SEMITONES;

        private float bend(float thirds) {
            return (float) Math.pow(2D, thirds * semitones / 12D);
        }

        private void loudestOnly() {
            int loudest = 0;
            for (int i = 1; i < 5; i++) {
                if (targetGain[i] > targetGain[loudest]) {
                    loudest = i;
                }
            }
            float total = 0F;
            for (int i = 0; i < 5; i++) {
                total += targetGain[i];
                if (i != loudest) {
                    targetGain[i] = 0F;
                }
            }
            targetGain[loudest] = Math.min(1F, total);
        }

        private void setEngine(int type) {
            boolean complete = true;
            for (int i = 0; i < 5; i++) {
                int buffer = buffer("engs" + type + i);
                complete &= buffer != 0;
                alSourceStop(sources[i]);
                alSourcei(sources[i], AL_BUFFER, buffer);
                gain[i] = 0F;
            }
            engine = complete ? type : -1; // tried again next update while the game is still loading
        }

        private void fadeOut(float dt) {
            Arrays.fill(targetGain, 0F);
            float k = 1F - (float) Math.exp(-dt / ENGINE_SMOOTHING);
            for (int i = 0; i < 5; i++) {
                gain[i] -= gain[i] * k;
                if (gain[i] < 0.002F) {
                    alSourcePause(sources[i]);
                } else {
                    alSourcef(sources[i], AL_GAIN, gain[i] * engineLevel);
                }
            }
        }

        void silence() {
            Arrays.fill(gain, 0F);
            for (int s : sources) {
                alSourceStop(s);
            }
            if (rebuilt != null) {
                rebuilt.silence();
            }
            if (hum != null) {
                hum.silence();
            }
            if (nitro != null) {
                nitro.silence();
            }
            if (rasp != null) {
                rasp.silence();
            }
            if (harsh != null) {
                harsh.silence();
            }
        }

        void delete() {
            for (int s : sources) {
                alDeleteSources(s);
            }
            if (rebuilt != null) {
                rebuilt.delete();
            }
            if (hum != null) {
                hum.delete();
            }
            if (nitro != null) {
                nitro.delete();
            }
            if (rasp != null) {
                rasp.delete();
            }
            if (harsh != null) {
                harsh.delete();
            }
            if (filter != 0) {
                EXTEfx.alDeleteFilters(filter);
            }
        }
    }

    /* -------------------------------------------------- rebuilt engines */

    /**
     * A block of the rebuilt sound, 20 ms; blocks each stream has, and how
     * many are kept queued ahead: 80 ms, which rides out the sound thread
     * cutting an engine's recordings when it is first heard.
     */
    private static final int REBUILT_BLOCK = EngineGrains.RATE / 50;
    private static final int STREAM_BUFFERS = 6;
    private static final int STREAM_AHEAD = 4;
    /** The front's and the back's levels, the skids', and how fast a car fades in or out when the extra starts or stops playing it, seconds. */
    private static final float FRONT_LEVEL = 0.6F;
    private static final float BACK_LEVEL = 0.7F;
    private static final float SKID_LEVEL = 0.55F;
    private static final float REBUILT_FADE = 0.15F;
    /** How fast a wheel's skid comes in and dies away, seconds. */
    private static final float SKID_ATTACK = 0.02F;
    private static final float SKID_RELEASE = 0.12F;
    /** The game's skid recordings, a different one at each wheel. */
    private static final String[] SKID_CLIPS = {"skid0", "skid1", "skid2"};
    private static final String[] DUST_CLIPS = {"dustskid0", "dustskid1", "dustskid2"};
    /** A voice's gain below this (-120 dB) is silence: the voice stops instead of playing on unheard. */
    private static final float SILENT = 1e-6F;

    /* the recordings the rebuilt sound plays, read and cut once (the sound thread's) */
    private final Map<String, EngineGrains.Cycles> cycleCache = new HashMap<>();
    private final Map<String, Recording> skidCache = new HashMap<>();

    /** A clip as samples from -1 to 1 (its channels averaged), {@code rate} a second. */
    private record Recording(float[] x, int rate) {
    }

    /** What {@link #recording} gives for a clip that cannot be read or is empty: it is not tried again. */
    private static final Recording UNREADABLE = new Recording(new float[0], 0);

    /** Engine recording {@code name} cut into its cycles, or null while it is not there or cannot be read. */
    private EngineGrains.Cycles cycles(String name) {
        EngineGrains.Cycles c = cycleCache.get(name);
        if (c == null && !cycleCache.containsKey(name)) {
            Recording r = recording(name);
            if (r != null) {
                c = r == UNREADABLE ? null : EngineGrains.cut(r.x(), r.rate());
                cycleCache.put(name, c);
            }
        }
        return c;
    }

    /** Skid recording {@code name}, or null while it is not there or cannot be read. */
    private Recording skidRecording(String name) {
        Recording r = skidCache.get(name);
        if (r == null) {
            r = recording(name);
            if (r != null) {
                skidCache.put(name, r);
            }
        }
        return r == UNREADABLE ? null : r;
    }

    /** Clip stream {@code in} as 16-bit little-endian samples, as OpenAL and the rebuilt sound read them. */
    private static AudioInputStream pcm16(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16 && !f.isBigEndian()) {
            return in;
        }
        return AudioSystem.getAudioInputStream(new AudioFormat(f.getSampleRate(), 16, f.getChannels(), true, false),
                in);
    }

    /**
     * Clip {@code name} as a recording: null while it is not there (the game
     * still loading), {@link #UNREADABLE} if it cannot be read or is empty.
     */
    private Recording recording(String name) {
        SoundClip clip = clips.clips().get(name);
        byte[] data = clip == null ? null : clip.data();
        if (data == null) {
            return null;
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            AudioInputStream pcm = pcm16(in);
            byte[] b = pcm.readAllBytes();
            int channels = Math.max(1, pcm.getFormat().getChannels());
            int n = b.length / (2 * channels);
            float[] x = new float[n];
            for (int i = 0; i < n; i++) {
                float sum = 0F;
                for (int c = 0; c < channels; c++) {
                    int at = 2 * (i * channels + c);
                    sum += (short) ((b[at] & 0xff) | (b[at + 1] << 8));
                }
                x[i] = sum / (channels * 32768F);
            }
            return n > 0 ? new Recording(x, (int) pcm.getFormat().getSampleRate()) : UNREADABLE;
        } catch (Exception ex) {
            HLogger.warn("3D sound cannot read clip " + name + " for the rebuilt engine sound: " + ex);
            return UNREADABLE;
        }
    }

    /** Where the car is in its speed band, 0 to 1, as {@link EngineVoices#mix} reckons it. */
    private static float bandShare(Car car) {
        float s = Math.abs(car.speed);
        int[] b = car.bands;
        if (s <= b[0]) {
            return b[0] == 0 ? 0F : s / b[0];
        }
        if (s <= b[1]) {
            return (s - b[0]) / Math.max(1F, b[1] - b[0]);
        }
        if (s <= b[2]) {
            return (s - b[1]) / Math.max(1F, b[2] - b[1]);
        }
        return 1F;
    }

    /** A source fed blocks of samples as it plays them. */
    private static final class Stream {
        final int source;
        private final int[] buffers = new int[STREAM_BUFFERS];
        private final int[] spare = new int[STREAM_BUFFERS];
        private int spares;

        Stream() {
            source = alGenSources();
            alGenBuffers(buffers);
            System.arraycopy(buffers, 0, spare, 0, STREAM_BUFFERS);
            spares = STREAM_BUFFERS;
        }

        /** Blocks it has played or never had, ready to be filled. */
        int free() {
            for (int done = alGetSourcei(source, AL_BUFFERS_PROCESSED); done > 0 && spares < STREAM_BUFFERS; done--) {
                spare[spares++] = alSourceUnqueueBuffers(source);
            }
            return spares;
        }

        /** Blocks still to play (as of the last {@link #free}). */
        int queued() {
            return STREAM_BUFFERS - spares;
        }

        void queue(ShortBuffer samples) {
            int b = spare[--spares];
            alBufferData(b, AL_FORMAT_MONO16, samples, EngineGrains.RATE);
            alSourceQueueBuffers(source, b);
        }

        /** Plays on if it ran dry, or starts, when it has blocks. */
        void keepPlaying() {
            if (queued() > 0 && alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
                alSourcePlay(source);
            }
        }

        void stop() {
            alSourceStop(source);
            free();
        }

        void delete() {
            alSourceStop(source);
            alSourcei(source, AL_BUFFER, 0);
            alDeleteSources(source);
            alDeleteBuffers(buffers);
        }
    }

    /**
     * A car the rebuilt engine sound plays (the enginesound extra): its
     * engine's five recordings, each one cycle at a time
     * ({@link EngineGrains.Voice}), mixed as {@link EngineVoices} mixes them,
     * made into the front and the back ({@link EngineGrains.Tone}) and
     * streamed from two sources, the front at the car's nose and the back at
     * its tail; and a skid streamed from each wheel while it slides
     * ({@link EngineGrains.Skid}). Placed by the car's pose, carried on
     * between ticks with the car, muffled and bent by Doppler as the
     * engines are. Everything is made when the extra first plays the car.
     */
    private final class RebuiltVoices {
        private final Stream front = new Stream();
        private final Stream back = new Stream();
        private final Stream[] wheels = new Stream[REBUILT_WHEELS];
        private final EngineGrains.Voice[] voices = new EngineGrains.Voice[5];
        private final EngineGrains.Tone tone = new EngineGrains.Tone();
        private final EngineGrains.Skid[] skids = new EngineGrains.Skid[REBUILT_WHEELS];
        private final float[] skidLevel = new float[REBUILT_WHEELS];
        private final float[] body = new float[BODY_VALUES];
        private final float[] mixed = new float[REBUILT_BLOCK];
        private final float[] frontBlock = new float[REBUILT_BLOCK];
        private final float[] backBlock = new float[REBUILT_BLOCK];
        private final float[] wheelBlock = new float[REBUILT_BLOCK];
        private final ShortBuffer pcm = MemoryUtil.memAllocShort(REBUILT_BLOCK);
        private final float[] lastGain = new float[5];
        private final float[] lastPitch = {1F, 1F, 1F, 1F, 1F};
        private final float[] placed = new float[3];
        /** The layers over the engine, the sources that loop them (made when first heard) and the car's kit. */
        final EngineLayers layers = new EngineLayers();
        private final int[] layerSource = new int[EngineLayers.COUNT];
        private final String[] layerClip = new String[EngineLayers.COUNT];
        private EngineLayers.Kit kit = EngineLayers.PLAIN;
        private float reach = -1F;
        private final int wheelFilter;
        private float wheelLf = -1F;
        private float wheelHf = -1F;
        private int engine = -1;
        /** How much of the car it plays: 1 while the extra plays it, fading to 0 when it stops. */
        private float level;
        private float lastThrottle;
        /* the car's turn, for its points */
        private float sy;
        private float cy;
        private float sp;
        private float cp;
        private float sr;
        private float cr;

        RebuiltVoices(int filter) {
            world(front.source);
            world(back.source);
            for (int w = 0; w < REBUILT_WHEELS; w++) {
                wheels[w] = new Stream();
                world(wheels[w].source);
                skids[w] = new EngineGrains.Skid();
            }
            for (int i = 0; i < 5; i++) {
                voices[i] = new EngineGrains.Voice();
            }
            if (efx) {
                filter(filter);
                wheelFilter = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(wheelFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            } else {
                wheelFilter = 0;
            }
        }

        /** The engine's filter (dulled off the throttle, muffled by the stage) on the front and the back. */
        void filter(int filter) {
            if (efx && filter != 0) {
                alSourcei(front.source, EXTEfx.AL_DIRECT_FILTER, filter);
                alSourcei(back.source, EXTEfx.AL_DIRECT_FILTER, filter);
            }
        }

        /**
         * Plays the car for the next update: the five recordings' smoothed
         * {@code gain} and {@code pitch} (the 3D sound's mix), the throttle
         * smoothed, and where the car's middle is heard ({@code position},
         * {@code velocity}, turned to the listener).
         */
        void play(Car car, int index, float dt, float[] gain, float[] pitch, float throttle, float[] position,
                float[] velocity, boolean muted) {
            if (car.engine != engine) {
                setEngine(car.engine);
            }
            synchronized (bodies) {
                if (index < ALL_CARS) {
                    System.arraycopy(bodies[index], 0, body, 0, BODY_VALUES);
                } else {
                    Arrays.fill(body, 0F);
                }
            }
            turnOf(car);
            kit = kitOf(index);
            if (kit.loud != reach) {
                // a strong engine carries further, a quiet one fades sooner
                reach = kit.loud;
                alSourcef(front.source, AL_REFERENCE_DISTANCE, FULL_VOLUME_DISTANCE * reach);
                alSourcef(back.source, AL_REFERENCE_DISTANCE, FULL_VOLUME_DISTANCE * reach);
            }
            place(front.source, 0, position, velocity);
            place(back.source, 3, position, velocity);
            level = Math.min(1F, level + dt / REBUILT_FADE);
            System.arraycopy(gain, 0, lastGain, 0, 5);
            System.arraycopy(pitch, 0, lastPitch, 0, 5);
            lastThrottle = throttle;
            engineBlocks(gain, pitch, throttle);
            wheelBlocks(car, index, dt, muted, position, velocity);
            int[] b = car.bands;
            layers.update(car, kit, bandShare(car), b[0], b[2], dt, muted);
            float blown = layers.takeBlowOff();
            if (blown > 0F) {
                playAt(index, EngineLayers.BLOW_OFF_CLIP, 0.6F * blown * kit.loud, variation());
            }
            layerVoices(position, velocity);
        }

        /** Where on the car each layer is heard: an offset into {@link #body}, or -1 for its middle. */
        private static final int[] LAYER_AT = {0, 3, 3, -1, -1, 0};

        /** Each layer's source: made and started when the layer is first heard, paused while it is silent. */
        private void layerVoices(float[] position, float[] velocity) {
            for (int i = 0; i < EngineLayers.COUNT; i++) {
                float g = layers.gain[i] * level;
                int source = layerSource[i];
                if (g <= 0F) {
                    if (source != 0 && layerClip[i] != null && alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING) {
                        alSourcePause(source);
                    }
                    continue;
                }
                String clip = EngineLayers.clip(i, kit);
                if (clip == null) {
                    continue;
                }
                if (source == 0) {
                    source = layerSource[i] = alGenSources();
                    world(source);
                    alSourcei(source, AL_LOOPING, AL_TRUE);
                    if (wheelFilter != 0) {
                        alSourcei(source, EXTEfx.AL_DIRECT_FILTER, wheelFilter); // muffled by the stage as the skids are
                    }
                }
                if (!clip.equals(layerClip[i])) {
                    int buffer = buffer(clip);
                    if (buffer == 0) {
                        continue;
                    }
                    alSourceStop(source);
                    alSourcei(source, AL_BUFFER, buffer);
                    layerClip[i] = clip;
                }
                boolean engines = i == EngineLayers.CHARGER || i == EngineLayers.WHINE || i == EngineLayers.FAN;
                alSourcef(source, AL_GAIN, g * (engines ? engineLevel * kit.loud : everyday * bus[BUS_EFFECTS]));
                alSourcef(source, AL_PITCH, Math.max(0.5F, Math.min(2F, layers.pitch[i])));
                if (LAYER_AT[i] < 0) {
                    alSource3f(source, AL_POSITION, position[0], position[1], position[2]);
                    alSource3f(source, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
                } else {
                    place(source, LAYER_AT[i], position, velocity);
                }
                if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
                    alSourcePlay(source);
                }
            }
        }

        private void layersOff() {
            layers.clear();
            for (int source : layerSource) {
                if (source != 0) {
                    alSourceStop(source);
                }
            }
        }

        /** The extra no longer plays the car (or the car is gone): fades it out, then lets the streams run dry. */
        void quiet(float dt) {
            if (level <= 0F && !anyWheel()) {
                return;
            }
            level = Math.max(0F, level - dt / REBUILT_FADE);
            layersOff();
            engineBlocks(lastGain, lastPitch, lastThrottle);
            float down = 1F - (float) Math.exp(-dt / SKID_RELEASE);
            for (int w = 0; w < REBUILT_WHEELS; w++) {
                skidLevel[w] -= skidLevel[w] * down;
                if (skidLevel[w] < 0.002F) {
                    skidLevel[w] = 0F;
                }
                topUp(w);
            }
        }

        private boolean anyWheel() {
            for (int w = 0; w < REBUILT_WHEELS; w++) {
                if (skidLevel[w] > 0F || skids[w].sounding()) {
                    return true;
                }
            }
            return false;
        }

        private void setEngine(int type) {
            boolean complete = true;
            for (int i = 0; i < 5; i++) {
                EngineGrains.Cycles c = cycles("engs" + type + i);
                complete &= c != null;
                voices[i].use(c);
            }
            engine = complete ? type : -1; // tried again next update while the game is still loading
        }

        /** Tops the front's and the back's queues up with blocks of the engine. */
        private void engineBlocks(float[] gain, float[] pitch, float throttle) {
            boolean sound = tone.popping();
            for (int i = 0; i < 5; i++) {
                sound |= level > 0F && gain[i] > 0.0005F || voices[i].sounding();
            }
            if (!sound) {
                return; // what is queued plays out, then the sources stop
            }
            int ahead = lockstep ? STREAM_BUFFERS : STREAM_AHEAD;
            int need = Math.min(front.free(), back.free()) - (STREAM_BUFFERS - ahead);
            for (int b = 0; b < need; b++) {
                Arrays.fill(mixed, 0F);
                for (int i = 0; i < 5; i++) {
                    float g = gain[i] * level; // eased toward 0, it never quite gets there
                    voices[i].add(mixed, 0, REBUILT_BLOCK, pitch[i], g < SILENT ? 0F : g, grainRandom);
                }
                tone.shape(mixed, frontBlock, backBlock, REBUILT_BLOCK, throttle);
                queue(front, frontBlock, FRONT_LEVEL);
                queue(back, backBlock, BACK_LEVEL);
            }
            alSourcef(front.source, AL_GAIN, engineLevel * kit.loud);
            alSourcef(back.source, AL_GAIN, engineLevel * kit.loud);
            front.keepPlaying();
            back.keepPlaying();
        }

        /** Each wheel's skid: up and down with how hard it slides, from the wheel, on road or loose ground. */
        private void wheelBlocks(Car car, int index, float dt, boolean muted, float[] position, float[] velocity) {
            float up = 1F - (float) Math.exp(-dt / SKID_ATTACK);
            float down = 1F - (float) Math.exp(-dt / SKID_RELEASE);
            if (wheelFilter != 0) {
                float lf = throughGain(index);
                float hf = throughTreble(index);
                if (Math.abs(lf - wheelLf) > 0.01F || Math.abs(hf - wheelHf) > 0.01F) {
                    EXTEfx.alFilterf(wheelFilter, EXTEfx.AL_LOWPASS_GAIN, lf);
                    EXTEfx.alFilterf(wheelFilter, EXTEfx.AL_LOWPASS_GAINHF, hf);
                    for (Stream s : wheels) {
                        alSourcei(s.source, EXTEfx.AL_DIRECT_FILTER, wheelFilter);
                    }
                    for (int source : layerSource) {
                        if (source != 0) {
                            alSourcei(source, EXTEfx.AL_DIRECT_FILTER, wheelFilter);
                        }
                    }
                    wheelLf = lf;
                    wheelHf = hf;
                }
            }
            for (int w = 0; w < REBUILT_WHEELS; w++) {
                float target = muted || !car.running ? 0F : Math.max(0F, Math.min(1F, car.skid(w)));
                skidLevel[w] += (target - skidLevel[w]) * (target > skidLevel[w] ? up : down);
                if (target == 0F && skidLevel[w] < 0.002F) {
                    skidLevel[w] = 0F;
                }
                if (skidLevel[w] == 0F && !skids[w].sounding()) {
                    continue;
                }
                String clip = ((car.loose >> w) & 1) != 0 ? DUST_CLIPS[w % 3] : SKID_CLIPS[w % 3];
                Recording skid = skidRecording(clip);
                if (skid != null) {
                    skids[w].use(skid.x(), skid.rate());
                }
                place(wheels[w].source, 6 + 3 * w, position, velocity);
                topUp(w);
            }
        }

        /** Tops wheel {@code w}'s queue up with its skid, at its level now. */
        private void topUp(int w) {
            EngineGrains.Skid skid = skids[w];
            if (skidLevel[w] == 0F && !skid.sounding()) {
                return;
            }
            Stream s = wheels[w];
            int ahead = lockstep ? STREAM_BUFFERS : STREAM_AHEAD;
            int need = s.free() - (STREAM_BUFFERS - ahead);
            for (int b = 0; b < need; b++) {
                Arrays.fill(wheelBlock, 0F);
                skid.add(wheelBlock, 0, REBUILT_BLOCK, (0.95F + 0.12F * skidLevel[w]) * kit.skidPitch, skidLevel[w],
                        grainRandom);
                queue(s, wheelBlock, SKID_LEVEL);
            }
            alSourcef(s.source, AL_GAIN, everyday * bus[BUS_EFFECTS]);
            s.keepPlaying();
        }

        private void queue(Stream s, float[] block, float gain) {
            pcm.clear();
            for (int i = 0; i < REBUILT_BLOCK; i++) {
                float v = block[i] * gain * 32_767F;
                pcm.put((short) Math.max(-32_768F, Math.min(32_767F, v)));
            }
            pcm.flip();
            s.queue(pcm);
        }

        private void turnOf(Car car) {
            double y = Math.toRadians(car.yaw);
            double p = Math.toRadians(car.pitch);
            double r = Math.toRadians(car.roll);
            sy = (float) Math.sin(y);
            cy = (float) Math.cos(y);
            sp = (float) Math.sin(p);
            cp = (float) Math.cos(p);
            sr = (float) Math.sin(r);
            cr = (float) Math.cos(r);
        }

        /**
         * Puts {@code source} at the car's point {@code body[at..at+2]}: turned
         * with the car as the game turns its points (roll, pitch, then yaw;
         * RaceView#toWorld), then to the listener, beside its middle.
         */
        private void place(int source, int at, float[] position, float[] velocity) {
            float lx = body[at];
            float ly = body[at + 1];
            float lz = body[at + 2];
            float x1 = lx * cr - ly * sr;
            float y1 = lx * sr + ly * cr;
            float y2 = y1 * cp - lz * sp;
            float z2 = y1 * sp + lz * cp;
            turn(x1 * cy - z2 * sy, y2, x1 * sy + z2 * cy, placed);
            alSource3f(source, AL_POSITION, position[0] + placed[0], position[1] + placed[1], position[2] + placed[2]);
            alSource3f(source, AL_VELOCITY, velocity[0], velocity[1], velocity[2]);
        }

        void silence() {
            front.stop();
            back.stop();
            for (int w = 0; w < REBUILT_WHEELS; w++) {
                wheels[w].stop();
                skidLevel[w] = 0F;
                skids[w].use(null, 1);
            }
            level = 0F;
            Arrays.fill(lastGain, 0F);
            tone.clear();
            layersOff();
            engine = -1; // the voices start again from their first cycle
        }

        void delete() {
            front.delete();
            back.delete();
            for (Stream s : wheels) {
                s.delete();
            }
            for (int source : layerSource) {
                if (source != 0) {
                    alDeleteSources(source);
                }
            }
            if (wheelFilter != 0) {
                EXTEfx.alDeleteFilters(wheelFilter);
            }
            MemoryUtil.memFree(pcm);
        }
    }

    /* ---------------------------------------------------- test rendering */

    private void openRenderFile() throws IOException {
        wav = new WavOut(renderTo, lockstep ? 0L : System.nanoTime());
    }

    /** Mixes the sound up to now into the test file, as fast as a device would play it. */
    private void render(long now) throws IOException {
        wav.render(device, now);
    }

    private void closeRenderFile() {
        if (wav != null) {
            wav.close();
            wav = null;
        }
    }

    /** A loopback device's mix written to a 16-bit stereo WAV file, for tests: never heard. */
    private static final class WavOut {
        private final String path;
        private final RandomAccessFile file;
        private final ShortBuffer rendered = MemoryUtil.memAllocShort(RENDER_RATE * 2 / 10);
        private final long start;
        private long samples;
        private byte[] bytes;

        WavOut(String path, long start) throws IOException {
            this.path = path;
            this.start = start;
            file = new RandomAccessFile(path, "rw");
            file.setLength(0L);
            file.write(new byte[44]); // the header is written when the file is closed
        }

        /** Mixes the device's sound up to now into the file. */
        void render(long dev, long now) throws IOException {
            long due = (now - start) * RENDER_RATE / 1_000_000_000L;
            while (samples < due) {
                int n = (int) Math.min(due - samples, rendered.capacity() / 2);
                rendered.clear();
                SOFTLoopback.alcRenderSamplesSOFT(dev, rendered, n);
                if (bytes == null || bytes.length < n * 4) {
                    bytes = new byte[n * 4];
                }
                ByteBuffer.wrap(bytes, 0, n * 4).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        .put(rendered.limit(n * 2));
                file.write(bytes, 0, n * 4);
                samples += n;
            }
        }

        void close() {
            try {
                long data = samples * 4L;
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
                h.put("RIFF".getBytes()).putInt((int) (36 + data)).put("WAVE".getBytes()).put("fmt ".getBytes())
                        .putInt(16).putShort((short) 1).putShort((short) 2).putInt(RENDER_RATE)
                        .putInt(RENDER_RATE * 4).putShort((short) 4).putShort((short) 16).put("data".getBytes())
                        .putInt((int) data);
                header.write(h.array());
                file.seek(0L);
                file.write(header.toByteArray());
                file.close();
            } catch (IOException ex) {
                HLogger.warn("3D sound could not finish " + path + ": " + ex);
            }
            MemoryUtil.memFree(rendered);
        }
    }
}
