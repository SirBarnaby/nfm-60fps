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
        Sound3D sound = instance;
        if (sound == null) {
            return false;
        }
        int kind = switch (op) {
            case "play" -> PLAY;
            case "loop" -> LOOP;
            default -> STOP;
        };
        sound.post(new Command(kind, clip, -1, 0F, 0F));
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
    private ShortBuffer rendered;
    private RandomAccessFile renderFile;
    private long renderedSamples;
    private long renderStart;

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
                if (renderFile != null) {
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
            }
        }
    }

    /** Opens the default output device (or the test file) with HRTF as Windows' output suggests. */
    private boolean open() throws IOException {
        if (renderTo != null) {
            device = SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer) null);
        } else {
            device = alcOpenDevice((ByteBuffer) null);
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
                if (type == SOFTSystemEvents.ALC_EVENT_TYPE_DEFAULT_DEVICE_CHANGED_SOFT
                        && deviceType == SOFTSystemEvents.ALC_PLAYBACK_DEVICE_SOFT) {
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
        status = "on, " + (name == null ? "the default device" : name) + (hrtf == ALC_TRUE ? ", HRTF" : "")
                + (monoOut ? ", mono" : "");
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
    private void namedGain(String name, int source) {
        boolean atCar = namedAtPlayer.containsKey(name);
        int b = atCar || name.startsWith("air") ? BUS_EFFECTS : BUS_INTERFACE;
        alSourcef(source, AL_GAIN, (atCar ? 1F : flatGain) * bus[b]);
    }

    /** The gain of a clip heard as it is, undoing the listener's makeup. */
    private float flatGain = 1F;

    /** Windows' default output changed: move to it without stopping anything. */
    private void reopen() {
        reopenWanted = false;
        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        if (SOFTReopenDevice.alcReopenDeviceSOFT(device, (ByteBuffer) null, contextAttributes(deviceCaps))) {
            describeDevice();
            HLogger.info("3D sound moved to the default device: " + status);
        }
    }

    private long connectedCheckedAt;

    /** A device that was unplugged is reopened on the default one. */
    private void checkConnected(long now) {
        if (now - connectedCheckedAt < 1_000_000_000L) {
            return;
        }
        connectedCheckedAt = now;
        if (alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) == ALC_FALSE) {
            reopen();
        }
    }

    private void close() {
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
        Integer known = buffers.get(name);
        if (known != null) {
            return known;
        }
        SoundClip clip = clips.clips().get(name);
        byte[] data = clip == null ? null : clip.data();
        if (data == null) {
            return 0;
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

    /* -------------------------------------------------------- commands */

    private void handleCommands() {
        for (Command c; (c = commands.poll()) != null;) {
            handle(c);
        }
    }

    private void handle(Command c) {
        switch (c.kind) {
            case PLAY, LOOP, STOP -> clip(c.kind, c.clip);
            case CRASH -> crash(c.car, c.a, (int) c.b);
            case SKID -> skid(c.car, (int) c.a);
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
        float g = flatGain * musicLevel * bus[BUS_MUSIC] * (1F - duck);
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
        if (name.startsWith("engs") || name.startsWith("crash") || name.startsWith("lowcrash")
                || name.startsWith("skid") || name.startsWith("dustskid") || name.equals("tires")) {
            return;
        }
        int buffer = buffer(name);
        if (buffer == 0) {
            return;
        }
        boolean atCar = name.equals("wasted") || name.equals("firewasted") || name.equals("carfixed");
        int source = named.computeIfAbsent(name, n -> {
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
        namedGain(name, source);
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
    private void updateReverb(float dt) {
        if (reverbSlot == 0 || acoustics == null) {
            return;
        }
        float[] target = acoustics.reverbTimes();
        float k = 1F - (float) Math.exp(-dt / REVERB_SMOOTHING);
        enclosed += (acoustics.enclosure() - enclosed) * k;
        boolean moved = Math.abs(enclosed - enclosedApplied) > 0.02F;
        if (moved) {
            enclosedApplied = enclosed;
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
        EXTEfx.alAuxiliaryEffectSlotf(reverbSlot, EXTEfx.AL_EFFECTSLOT_GAIN, enclosed * REVERB_LEVEL);
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
        for (int i = 0; i < 3 * MAX_CARS; i++) {
            float target = i / 3 < cars && carTraced[i / 3] ? traced[i] : 1F;
            through[i] += (target - through[i]) * k;
        }
        float[] paths = acoustics.paths();
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
        engineLevel = (1F - duck) * bus[BUS_ENGINES];
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
        renderFile = new RandomAccessFile(renderTo, "rw");
        renderFile.setLength(0L);
        renderFile.write(new byte[44]); // the header is written when the file is closed
        rendered = MemoryUtil.memAllocShort(RENDER_RATE * 2 / 10);
        renderStart = lockstep ? 0L : System.nanoTime();
    }

    /** Mixes the sound up to now into the test file, as fast as a device would play it. */
    private void render(long now) throws IOException {
        long due = (now - renderStart) * RENDER_RATE / 1_000_000_000L;
        byte[] bytes = null;
        while (renderedSamples < due) {
            int n = (int) Math.min(due - renderedSamples, rendered.capacity() / 2);
            rendered.clear();
            SOFTLoopback.alcRenderSamplesSOFT(device, rendered, n);
            if (bytes == null || bytes.length < n * 4) {
                bytes = new byte[n * 4];
            }
            ByteBuffer.wrap(bytes, 0, n * 4).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .put(rendered.limit(n * 2));
            renderFile.write(bytes, 0, n * 4);
            renderedSamples += n;
        }
    }

    private void closeRenderFile() {
        if (renderFile == null) {
            return;
        }
        try {
            long data = renderedSamples * 4L;
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()).putInt((int) (36 + data)).put("WAVE".getBytes()).put("fmt ".getBytes())
                    .putInt(16).putShort((short) 1).putShort((short) 2).putInt(RENDER_RATE)
                    .putInt(RENDER_RATE * 4).putShort((short) 4).putShort((short) 16).put("data".getBytes())
                    .putInt((int) data);
            header.write(h.array());
            renderFile.seek(0L);
            renderFile.write(header.toByteArray());
            renderFile.close();
        } catch (IOException ex) {
            HLogger.warn("3D sound could not finish " + renderTo + ": " + ex);
        }
        renderFile = null;
        if (rendered != null) {
            MemoryUtil.memFree(rendered);
            rendered = null;
        }
    }
}
