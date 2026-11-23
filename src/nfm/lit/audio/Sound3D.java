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
    private static final int MAX_CARS = 8;
    /** Occlusion is traced every this many updates (30 ms) and eased in over this long. */
    private static final int TRACE_EVERY = 3;
    private static final float OCCLUSION_SMOOTHING = 0.12F;
    /** How quickly the reverb follows the place, in seconds: moving from a canyon into the open. */
    private static final float REVERB_SMOOTHING = 1F;
    /** The reverb's level where the track closes in all round; open ground stays dry. */
    private static final float REVERB_LEVEL = 0.6F;
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
    private record Command(int kind, String clip, int car, float a, float b, StreamMusic music) {
        Command(int kind, String clip, int car, float a, float b) {
            this(kind, clip, car, a, b, null);
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

    /** Any car skids, from inside the race tick; surface 0 is road. */
    public static void carSkid(int car, int surface, float slide) {
        Sound3D sound = instance;
        if (sound != null && !SoundManager.isDiscarding() && slide > 150F) {
            sound.post(new Command(SKID, null, car, surface, slide));
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
    /** Steam Audio, when its library is there: null otherwise, and nothing is muffled. */
    private SteamAcoustics acoustics;
    private volatile boolean stageWanted = true;
    /* each car's share of sound reaching the listener through the stage, low, mid and high: traced and smoothed */
    private final float[] traced = new float[3 * MAX_CARS];
    private final float[] through = new float[3 * MAX_CARS];
    private final float[] carPlaces = new float[3 * MAX_CARS];
    private final boolean[] carTraced = new boolean[MAX_CARS];
    private int tracedAt;
    private int reverbSlot;
    private int reverbEffect;
    private boolean eaxReverb;
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
            AudioFormat f = in.getFormat();
            AudioInputStream pcm = in;
            if (f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || f.getSampleSizeInBits() != 16
                    || f.isBigEndian()) {
                AudioFormat target = new AudioFormat(f.getSampleRate(), 16, f.getChannels(), true, false);
                pcm = AudioSystem.getAudioInputStream(target, in);
            }
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
        }
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
        int cars = m == null || m.cars == null ? 0 : Math.min(MAX_CARS, m.cars.length);
        if (++tracedAt >= TRACE_EVERY && cars > 0) {
            tracedAt = 0;
            acoustics.listener(camX, camY, camZ, yawSin, yawCos, pitchSin, pitchCos);
            for (int i = 0; i < cars; i++) {
                Car c = m.cars[i];
                carTraced[i] = c.running && i != m.player;
                carPlaces[3 * i] = c.x;
                carPlaces[3 * i + 1] = c.y;
                carPlaces[3 * i + 2] = c.z;
            }
            acoustics.direct(carPlaces, carTraced, cars, traced);
        }
        float k = 1F - (float) Math.exp(-dt / OCCLUSION_SMOOTHING);
        boolean occluding = occlusionOn;
        for (int i = 0; i < 3 * MAX_CARS; i++) {
            float target = occluding && i / 3 < cars && carTraced[i / 3] ? traced[i] : 1F;
            through[i] += (target - through[i]) * k;
        }
        float[] paths = occluding && pathsOn ? acoustics.paths() : null;
        for (int i = 0; i < MAX_CARS; i++) {
            int at = SteamAcoustics.PATH_FLOATS * i;
            boolean found = paths != null && i < cars && carTraced[i] && paths[at] > 0F;
            float mid = found ? paths[at + 2] : 0F;
            pathGain[i] += (mid - pathGain[i]) * k;
            pathTreble[i] += ((found && mid > 0F ? Math.min(1F, paths[at + 3] / mid) : 0F) - pathTreble[i]) * k;
            pathWeight[i] += ((found ? 1F : 0F) - pathWeight[i]) * k;
            if (found) {
                for (int axis = 0; axis < 3; axis++) {
                    pathFrom[3 * i + axis] += (paths[at + 4 + axis] - pathFrom[3 * i + axis]) * k;
                }
            }
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
    private final float[] pathGain = new float[MAX_CARS];
    private final float[] pathTreble = new float[MAX_CARS];
    private final float[] pathWeight = new float[MAX_CARS];
    private final float[] pathFrom = new float[3 * MAX_CARS];

    /** How much of a car the stage hides, 0 in the clear to 1 fully behind something. */
    private float hidden(int car) {
        return car >= 0 && car < MAX_CARS ? 1F - Math.max(0F, Math.min(1F, through[3 * car + 1])) : 0F;
    }

    /** A car's overall level as the stage lets it through. */
    private float throughGain(int car) {
        float blocked = car >= 0 && car < MAX_CARS ? Math.max(BLOCKED_GAIN, pathGain[car]) : BLOCKED_GAIN;
        return 1F - (1F - blocked) * hidden(car);
    }

    /** A car's treble as the stage lets it through, relative to {@link #throughGain}. */
    private float throughTreble(int car) {
        float blocked = car >= 0 && car < MAX_CARS ? Math.max(BLOCKED_TREBLE, pathTreble[car]) : BLOCKED_TREBLE;
        return 1F - (1F - blocked) * hidden(car);
    }

    /**
     * Turns the way to a car, {@code d} (from the listener, in the game's
     * world), toward the way its sound comes round the stage, as far as the
     * car is hidden and a path was found; the distance stays.
     */
    private void bendTowardPath(int car, float[] d) {
        float w = car >= 0 && car < MAX_CARS ? hidden(car) * pathWeight[car] : 0F;
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
            }
        }
        for (EngineVoices e : engines) {
            e.silence();
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

    /** One effect voice: a source, and the car it follows (-1 for none). */
    private static final class Voice {
        final int source;
        /** Muffles the voice as its car is muffled; 0 without EFX. */
        final int filter;
        int car = -1;
        float gain;

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
        Voice voice = null;
        float weakest = Float.MAX_VALUE;
        for (Voice v : effects) {
            if (alGetSourcei(v.source, AL_SOURCE_STATE) != AL_PLAYING) {
                voice = v;
                break;
            }
            float heard = v.gain / Math.max(1F, distanceToCar(v.car) / FULL_VOLUME_DISTANCE);
            if (heard < weakest) {
                weakest = heard;
                voice = v;
            }
        }
        if (voice == null) {
            return;
        }
        alSourceStop(voice.source);
        alSourcei(voice.source, AL_BUFFER, buffer);
        world(voice.source);
        gain *= loud(name) ? 1F : everyday;
        alSourcef(voice.source, AL_GAIN, gain * bus[BUS_EFFECTS]);
        alSourcef(voice.source, AL_PITCH, pitch);
        voice.car = car;
        voice.gain = gain;
        muffle(voice);
        placeAtCar(voice.source, car);
        alSourcePlay(voice.source);
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
        for (Voice v : effects) {
            if (v.car >= 0 && alGetSourcei(v.source, AL_SOURCE_STATE) == AL_PLAYING) {
                placeAtCar(v.source, v.car);
                muffle(v);
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
                b.append(String.format(Locale.ROOT, "through[%s] rt[%.2f %.2f %.2f] closed %.2f %s",
                        throughList(cars), reverbNow[0], reverbNow[1], reverbNow[2], enclosed,
                        acoustics.describe()));
            }
            System.out.println(b);
        }
    }

    private long debugAt;

    private String throughList(int cars) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(cars, MAX_CARS); i++) {
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
        final float[] position = new float[3];
        final float[] velocity = new float[3];

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
                return;
            }
            if (car.engine != engine) {
                setEngine(car.engine);
            }
            mix(car, muted);
            if (!carNow(index, ahead, position, velocity)) {
                return;
            }
            float distance = (float) Math.sqrt(position[0] * position[0] + position[1] * position[1]
                    + position[2] * position[2]);
            if (distance > ENGINE_DETAIL_DISTANCE) {
                loudestOnly();
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
                appliedHf = hf;
                appliedLf = lf;
            }
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
            float t;
            if (s <= b[0]) {
                t = b[0] == 0 ? 0F : s / b[0];
            } else if (s <= b[1]) {
                t = (s - b[0]) / Math.max(1F, b[1] - b[0]);
            } else if (s <= b[2]) {
                t = (s - b[1]) / Math.max(1F, b[2] - b[1]);
            } else {
                t = 1F;
            }
            float p = Math.min(2.999F, 3F * t); // where in the band: 0 to 3, a third per recording
            if (!car.grounded) {
                targetGain[0] = 1F;
                targetPitch[0] = 1F + 0.15F * t;
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

        private float bend(float thirds) {
            return (float) Math.pow(2D, thirds * BEND_SEMITONES / 12D);
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
        }

        void delete() {
            for (int s : sources) {
                alDeleteSources(s);
            }
            if (filter != 0) {
                EXTEfx.alDeleteFilters(filter);
            }
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
