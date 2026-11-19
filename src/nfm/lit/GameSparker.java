package nfm.lit;

import java.util.Arrays;
import fallk.logmaster.HLogger;
import nfm.lit.audio.BASSLoader;
import nfm.lit.audio.SoundManager;
import nfm.lit.gpu.GpuDisplay;
import nfm.lit.gpu.NativeWindow;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * GameSparker brings everything together.
 *
 * @author Kaffeinated, Omar Waly
 */
public class GameSparker extends Panel implements Runnable {
    /**
    *
    */

    /**
     * get os name / type
     */
    public static final String OPERATING_SYSTEM = System.getProperty("os.name").toLowerCase();
    public static final boolean IS_UNIX = OPERATING_SYSTEM.indexOf("nix") == 0 || OPERATING_SYSTEM.indexOf("nux") == 0;
    public static final boolean IS_WINDOWS = OPERATING_SYSTEM.indexOf("win") == 0;
    public static final boolean IS_MAC = OPERATING_SYSTEM.indexOf("mac") == 0;
    /**
     * get os bit
     */
    public static final String IS_64_BIT = System.getProperty("sun.arch.data.model").equals("64") ? "64" : "32";
    /**
     * uh help
     */
    public static final String WORKING_DIRECTORY = ".";
    /**
     * Developer mode. Opens the in-game console on the main menu and enables the
     * frame-time overlay. Can be turned off with -Dnfm.debug=false.
     */
    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("nfm.debug", "true"));

    private static final long serialVersionUID = -34048182014310663L;

    private static final String[] carModels = {
            "2000tornados", "formula7", "canyenaro", "lescrab", "nimi", "maxrevenge", "leadoxide", "koolkat", "drifter",
            "policecops", "mustang", "king", "audir8", "masheen", "radicalone", "drmonster"
    };

    private static final String[] trackModels = {
            "road", "froad", "twister2", "twister1", "turn", "offroad", "bumproad", "offturn", "nroad", "nturn",
            "roblend", "noblend", "rnblend", "roadend", "offroadend", "hpground", "ramp30", "cramp35", "dramp15",
            "dhilo15", "slide10", "takeoff", "sramp22", "offbump", "offramp", "sofframp", "halfpipe", "spikes", "rail",
            "thewall", "checkpoint", "fixpoint", "offcheckpoint", "sideoff", "bsideoff", "uprise", "riseroad", "sroad",
            "soffroad", "tside", "launchpad", "thenet", "speedramp", "offhill", "slider", "uphill", "roll1", "roll2",
            "roll3", "roll4", "roll5", "roll6", "opile1", "opile2", "aircheckpoint", "tree1", "tree2", "tree3", "tree4",
            "tree5", "tree6", "tree7", "tree8", "cac1", "cac2", "cac3", "8sroad", "8soffroad", "singlewallroad", "thewall2"
    };
    private static final String[] extraModels = {};

    /**
     * false to disable splash
     */
    private static final boolean splashScreenState = true;

    private static final String stageDir = "data/stages/";

    public int stageID = 1;
    public static String stageSubDir = "nfm2/";
    public static String stageName = "";

    public String loadStage = stageDir + stageSubDir + stageID + ".txt";
    public static String loadStageCus;

    /**
     * Set directory for temporary creation of cookies (directory is deleted after
     * writing is complete)
     */
    private static final String cookieDirTemp = "data/cookies/";
    /**
     * Set location for the cookie.radq
     */
    private static final String cookieDirZip = "data/cookies.radq";

    private String stageError = "";

    private Graphics2D rd;
    private Graphics sg;
    private Image offImage;
    private Thread gamer;
    private final Control[] u;
    private int mouses;
    private int xm;
    private int ym;
    private boolean lostfcs;
    private volatile boolean exwist;
    private int nob;
    private int notb;
    private int view;

    /* ------------------------------------------------------------------
     * Frame loop / presentation.
     *
     * The original loop fused simulation and rendering into one pass and paced
     * itself with Thread.sleep(), which on Windows overshoots by up to a timer
     * quantum. Every subsystem downstream (physics, AI, animations, HUD timers)
     * counts loop iterations, so the game ran at roughly 19 ticks per second and
     * the frame rate could not be raised without fast-forwarding the world.
     *
     * The loop now separates the two:
     *   - simulation runs in fixed steps at the legacy cadence (SCENE_TICK_NANOS
     *     during races, replays and highlights, MENU_TICK_NANOS elsewhere)
     *   - the 3D scene is redrawn at the display rate, interpolating car and
     *     camera transforms between the last two simulation states
     *   - the HUD is drawn once per simulation tick into its own layer and
     *     composited on top, so its timers keep the original cadence
     * ------------------------------------------------------------------ */
    /*
     * The original pacer aimed for 10 iterations per 530 ms while a race, replay
     * or highlight was on screen and 10 per 400 ms everywhere else. Those are the
     * cadences every frame-counted timer in the game was tuned against.
     */
    static final long SCENE_TICK_NANOS = 53_000_000L;
    private static final long MENU_TICK_NANOS = 40_000_000L;
    /**
     * Ticks the loop may run back to back to catch up after a slow frame before it
     * presents again. Keeps the world at real-time speed when drawing is slow.
     */
    private static final int MAX_TICKS_PER_FRAME = 5;
    private static final long MAX_TICK_LAG_NS = 500_000_000L;
    /** A car or camera that moves further than this in one tick was teleported. */
    private static final int TELEPORT_DISTANCE = 2500;
    private static final boolean PROFILE_LOOP = Boolean.getBoolean("nfm.profile");

    /* Frame rate limit: a positive value caps it, these two are special. */
    public static final int FPS_MATCH_DISPLAY = -1;
    public static final int FPS_UNLIMITED = 0;
    /**
     * For G-Sync and FreeSync monitors: vsync on, frames held just under the
     * refresh rate (refresh - refresh^2/3600, 138 at 144 Hz), so they stay
     * inside the variable-refresh range and never queue behind vsync.
     */
    public static final int FPS_VRR = -2;
    public static final int FPS_MIN = 30;
    public static final int FPS_MAX = 1000;
    private static final String FPS_COOKIE = "fpslimit";
    /** The cookie store reads -1 as "missing", so "match display" is saved as 1. */
    private static final int FPS_COOKIE_MATCH_DISPLAY = 1;
    private static final int FPS_COOKIE_VRR = 2;
    private static volatile int fpsLimit = FPS_MATCH_DISPLAY;
    private static volatile boolean fpsLimitChanged;
    /** On battery, frames a second are held to this, 0 for no cap. */
    private static volatile int batteryFps = 60;
    private static volatile boolean batteryFpsChanged;
    private static final String BATTERY_FPS_COOKIE = "batteryfps";
    /* whether the machine was on battery when last asked, and when that was */
    private static volatile boolean onBattery;
    private static volatile long powerCheckedAt;
    /** The battery cap is what holds frames back now, below the display's own rate. */
    private static volatile boolean batteryHolds;
    private static final String RUN_AHEAD_COOKIE = "runahead";
    /**
     * Run-ahead (on unless turned off): race frames show where the race will
     * be one tick from now with the keys held now, instead of where it was
     * one tick ago, which takes a tick (53 ms) off the input lag.
     */
    private static volatile boolean runAhead = true;
    private static volatile boolean runAheadChanged;

    private Image hudImage;
    private Graphics2D hudG;

    /** Objects rendered by the in-game pass: run()'s scene. */
    private ContO[] sceneObjects;
    /** Every loaded model, cars first: a car that is replaced after a crash is copied from here. */
    private ContO[] modelObjects;
    /** Simulation state per car: run()'s drivers. */
    private Madness[] racers;
    private GameUi ui;
    private CheckPoints raceCheckpoints;
    private Record raceRecord;
    private Trackers raceTrackers;

    private final int[] prevCarX = new int[51];
    private final int[] prevCarY = new int[51];
    private final int[] prevCarZ = new int[51];
    private final int[] prevCarXZ = new int[51];
    private final int[] prevCarXY = new int[51];
    private final int[] prevCarZY = new int[51];

    private final int[] savedCarX = new int[51];
    private final int[] savedCarY = new int[51];
    private final int[] savedCarZ = new int[51];
    private final int[] savedCarXZ = new int[51];
    private final int[] savedCarXY = new int[51];
    private final int[] savedCarZY = new int[51];

    /* Wheel steering angle and wheel roll. */
    private final int[] prevCarWXZ = new int[51];
    private final int[] prevCarWZY = new int[51];
    private final int[] savedCarWXZ = new int[51];
    private final int[] savedCarWZY = new int[51];
    /**
     * The object each car slot held when the snapshot was taken. Replays,
     * highlights and car repairs swap in a fresh ContO, which must not be blended
     * with the transform of the object it replaced.
     */
    private final ContO[] prevCarRef = new ContO[51];

    /** The race as it was before a run-ahead tick, put back right after it. */
    private final RaceSnapshot aheadSnapshot = new RaceSnapshot();
    /** Where the cars and the camera will be after the next tick, by the run-ahead tick. */
    private final Poses aheadPoses = new Poses();
    private final Poses nowPoses = new Poses();
    private final Poses prevPoses = new Poses();
    /** Real race ticks so far: a run-ahead tick is good until the next one, or until a key changes. */
    private int raceTicks;
    private boolean aheadValid;
    private int aheadTick;
    private int aheadKeys;
    private long profileAheadNanos;
    private int profileAheads;
    /** The keys the current real tick latched, to check the run-ahead tick against it. */
    private int latchedKeys;
    /** Real ticks that ran with the keys a run-ahead tick used, and how many of them landed elsewhere. */
    private int profileAheadChecked;
    private int profileAheadMissed;

    /** Real back-buffer graphics while a tick draws its overlays into the HUD layer. */
    private Graphics2D sceneGraphics;
    /** Length of the tick that produced the current state, for interpolation. */
    private long currentTickNanos = SCENE_TICK_NANOS;
    private int displayRefreshHz;
    private long displayRefreshCheckedAt;

    private int prevCamX;
    private int prevCamY;
    private int prevCamZ;
    private int prevCamXZ;
    private int prevCamZY;
    private boolean prevFrameValid;
    /**
     * Target of the per-tick draw step: a graphics context that draws nothing,
     * so drawing costs only the geometry and keeps every side effect. See
     * {@link #drawSimulationStep()}.
     */
    private final Graphics2D stepGraphics = new NullGraphics();
    /** Each scene object's distance from the camera, kept across presented frames. */
    private int[] savedDist = new int[0];

    /** Recording or verifying races, see {@link RaceSession}; null in a normal game. */
    private RaceSession session;
    /** The race's own state that is in no car: its random stream. One for the whole session. */
    private final World world = new World();

    /*
     * Keyboard state as the event thread last saw it. During a race every tick
     * reads the controls once, at its start, so a key that changes in the middle
     * of a tick cannot make the physics and the AI see different input, and a
     * recording stores exactly the input each tick used.
     */
    private final Object inputLock = new Object();
    private boolean liveUp;
    private boolean liveDown;
    private boolean liveLeft;
    private boolean liveRight;
    private boolean liveHandb;
    private int liveLookback;
    private boolean liveEnter;
    private int liveView;
    /** The controls are being latched once per tick (a race is running). */
    private boolean latching;

    private boolean pendingWhiteFlash;
    private float renderAlpha = 1F;
    private long tickStartNanos;

    /** Set by the autotest thread; the next race tick jumps to the post-race highlight. */
    private volatile boolean autotestHighlightRequested;
    /** Set by the autotest thread; the next race tick starts repairing the player's car. */
    private volatile boolean autotestRepairRequested;
    /** Set by the autotest thread; the render pass fulfils it with a complete frame. */
    private volatile boolean captureRequested;
    private volatile BufferedImage capturedFrame;
    private volatile BufferedImage capturedWindow;
    /** The window as the graphics card showed it, read back; only when the GPU renderer is on. */
    private volatile BufferedImage capturedGpu;

    /** Draw order of the scene objects in view, see {@link #drawWorldObjects}; grows with the stage. */
    private long[] drawKeys = new long[200];

    /* variables for screen shake */

    private int shaka = 0;
    /** Where the picture sits in the window, and how much it is scaled: set by {@link #paint}. */
    private volatile int apx = 0;
    private volatile int apy = 0;
    private volatile double presentScale = 1D;
    private final FrameScaler frameScaler = new FrameScaler();
    /** Waits between ticks and frames; used only by the game thread. */
    private final PreciseSleep frameSleep = new PreciseSleep();
    private static final String SCALING_COOKIE = "scaling";
    /**
     * Fill the window (on unless turned off): the 1280x720 picture is scaled
     * to the largest size that fits, keeping its shape, with a smooth filter.
     * Off, it is shown pixel for pixel in the middle of the window.
     */
    private static volatile boolean scaleToWindow = true;
    private static volatile boolean scaleToWindowChanged;
    private static final String RENDERER_COOKIE = "renderer";
    /**
     * Put frames on screen through the graphics card (on unless turned off):
     * scaling to the window is free there and the frame skips GDI. Falls back
     * to software when the card cannot, see {@link #presentOnGpu}.
     */
    private static volatile boolean gpuRenderer = true;
    private static volatile boolean gpuRendererChanged;
    /** The graphics card could not draw this session; cleared by asking for it again. */
    private static volatile boolean gpuUnavailable;
    private static final String MSAA_COOKIE = "msaa";
    /* models.radq is read again at the main menu after "mods reload" */
    private static volatile boolean modelReloadWanted;
    private static volatile boolean modelsLoaded;

    /**
     * Reads the mods folder and its load order again, and the models with
     * them the next time the game is at the main menu; stages and music are
     * read for every race anyway. Images and sounds need a restart.
     */
    public static String reloadMods() {
        GameFiles.rescan();
        modelReloadWanted = modelsLoaded;
        return GameFiles.describe() + " Models are read again at the main menu; stages and music with the next"
                + " race; images and sounds when the game starts again.";
    }

    public static String describeMods() {
        return GameFiles.describe();
    }
    /** Supersampling, as samples per pixel (2, 4 or 8). */
    private static final String SUPERSAMPLE_COOKIE = "supersamples";
    /** Supersampling as the game first saved it: 2 was twice the width and height, now 4x. */
    private static final String OLD_SUPERSAMPLE_COOKIE = "ssaa";
    /**
     * Supersampling for races drawn on the graphics card, in samples per
     * pixel: 2, 4 or 8 draw the scene with that many times the pixels it is
     * shown with (4: twice the width and height) and average them down, which
     * smooths every edge and line and stops thin outlines shimmering; 1 is off.
     */
    private static volatile int supersample = 1;
    private static volatile boolean supersampleChanged;
    /** How many times the shown size the card last drew a race at across, below 2 when its textures are smaller. */
    private static volatile float supersampleInUse = 1F;
    /**
     * Samples per pixel for races drawn on the graphics card: 1 (off, the
     * game's own hard edges) or 2, 4 or 8, which smooth polygon edges.
     */
    private static volatile int multisample = 1;
    private static volatile boolean multisampleChanged;
    /** Samples per pixel the card last drew a race with: fewer than asked when it does no more. */
    private static volatile int multisampleInUse = 1;
    private static final String DYNAMIC_RESOLUTION_COOKIE = "dynres";
    public static final int DYNAMIC_RESOLUTION_DEFAULT = 50;
    public static final int DYNAMIC_RESOLUTION_MIN = 25;
    /**
     * Dynamic resolution for races drawn on the graphics card: the lowest
     * share of the window's size, in percent, the scene may be drawn at to
     * hold the frame rate when the card cannot keep up; 0 for off.
     */
    private static volatile int dynamicResolution = DYNAMIC_RESOLUTION_DEFAULT;
    private static volatile boolean dynamicResolutionChanged;
    /** Picks the scale races are drawn at; used under {@link #presentLock}. */
    private final nfm.lit.gpu.DynamicResolution resolution = new nfm.lit.gpu.DynamicResolution();
    /** The share of the window's size the card last drew a race at. */
    private static volatile float sceneScaleInUse = 1F;
    /** How long the card took over the last frame, for the profile. */
    private static volatile long gpuFrameNanos;
    /** The time a frame may take, from the frame limit or the display; 0 for no limit. */
    private static volatile long frameBudgetNanos;
    /** How long a closing window waits for the game thread to stop drawing into it. */
    private static final long WINDOW_RELEASE_WAIT_NANOS = 500_000_000L;
    /**
     * Held while the GPU draws into the window, so the window cannot be
     * destroyed under it; guards windowShown and windowGeneration.
     */
    private final Object presentLock = new Object();
    private boolean windowShown;
    /** Counts native windows: changes whenever the window is made or destroyed. */
    private int windowGeneration;
    /** The GPU display, used only by the game thread; null while drawing in software. */
    private GpuDisplay gpu;
    private int gpuWindowGeneration;
    /** True while the GPU display owns the window, so the event thread must not paint it. */
    private volatile boolean gpuActive;
    /** Draw races, replays and highlights on the graphics card too, not only put them on screen. */
    private static final boolean GPU_SCENE = !"false".equalsIgnoreCase(System.getProperty("nfm.gpuscene"));
    /** Draw those scenes at the window's resolution rather than the picture's, see {@link #sceneTargetSize}. */
    private static final boolean GPU_SCENE_NATIVE = !"picture".equalsIgnoreCase(System.getProperty("nfm.gpuscene"));
    /** -Dnfm.motiontest=<dir>: write the scene at many points between two ticks, for checking smoothness. */
    private static final String MOTION_TEST = System.getProperty("nfm.motiontest");
    private int motionTestDone;
    /** Records the scene of a frame drawn on the graphics card, see {@link #renderIngameScene}. */
    private final SceneRecorder sceneRecorder = new SceneRecorder();

    /*
     * The scene recorded once per tick and moved to each frame on the
     * graphics card (see RetainedScene). -Dnfm.retained=false draws the
     * whole scene again for every frame instead.
     */
    private static final boolean RETAINED = !"false".equalsIgnoreCase(System.getProperty("nfm.retained"));
    /** Where in the tick the scene is recorded: halfway, so it is never more than half a tick off. */
    private static final float RETAINED_AT = 0.5F;
    /** How far past the picture's sides and its top and bottom the kept scene reaches, in 1280x720 pixels. */
    private static final int RETAINED_MARGIN_X = 480;
    private static final int RETAINED_MARGIN_Y = 270;
    private final SceneRecorder retainedRecorder = new SceneRecorder();
    private final RetainedScene retainedScene = new RetainedScene();
    private final FramePose framePose = new FramePose();
    private final PoseSpan spanScratch = new PoseSpan();
    /* what renderIngameScene records into while a kept scene is recorded */
    private SceneRecorder recordingRecorder;
    private RetainedScene recordingScene;

    /*
     * Races drawn on their own thread (see Presenter): -Dnfm.presenter=false
     * draws every frame on the game thread between the ticks instead.
     */
    private static final boolean PRESENTER = !"false".equalsIgnoreCase(System.getProperty("nfm.presenter"));
    private Presenter presenter;
    /** The kept scene handed to the presenter is out of date: a tick ran since. */
    private boolean keptStale = true;
    private boolean keptAhead;
    /** This tick's scene could not be kept (drawing the card does not do): frames are drawn the whole way. */
    private boolean keptBlocked;
    /** The HUD as the last frame drawn on the game thread showed it. */
    private BufferedImage ownSceneHud;
    /** The kept scene still holds for the frames to come: no tick nor run-ahead since it was recorded. */
    private boolean retainedValid;
    private boolean retainedAhead;
    /** True while the kept scene is being recorded. */
    private boolean retainedRecording;
    /** The recording the last frame drawn on the graphics card came from. */
    private SceneRecorder shownRecorder = sceneRecorder;
    private long profileRecordNanos;
    private int profileRecords;
    /* kept scenes made ready for the graphics card to place, see ProjectedScene */
    private long profileProjectNanos;
    private int profileProjects;
    private long profilePlaceNanos;
    private int profilePlaces;
    /** The frame being drawn goes to the graphics card as a recorded scene. */
    private boolean gpuSceneFrame;
    private boolean gpuSceneFlash;
    /**
     * The back buffer does not hold the last frame, which the graphics card
     * drew: {@link #syncBackBuffer} draws it there before anything reads it.
     */
    private boolean backBufferStale;
    /** The HUD as it was on the last frame the graphics card drew. */
    private BufferedImage gpuSceneHud;
    /** Which HUD {@link #gpuSceneHud} holds, so the card uploads it only when it changed (game thread). */
    private long gpuSceneHudVersion = -1L;
    /* the game thread's own HUD copies, numbered apart from the presenter's kept scenes */
    private long ownHudVersion = 1L << 40;
    private long ownHudTick = -1L;
    /** Ticks run since the start, game thread. */
    private long ticksRun;
    /** The scene read back from the graphics card, at the size it drew it; for tests. */
    private volatile BufferedImage capturedGpuScene;
    /** In HDR, how much each pixel of capturedGpuScene glows. */
    private volatile BufferedImage capturedGlow;

    public static String gameState = "None";
    public static int gameStateID;
    public static int ContosCount;

    public String wallmodel;

    public int noboffset = 10;      //this makes it so IDs are offset correctly, can be modified by stage via idoffset(x)
    public int nobfix = carModels.length - noboffset;

    public boolean reverseYRot = false;

    /**
     * A key went down that the autopilot, a recorded race or a game
     * controller pressed. {@code key} is the key as the game has always
     * numbered it, AWT 1.0's ({@link GameInput#legacyCode}): the character it
     * types, or 1000 and up for the arrows, F keys and the like. These keep
     * the game's own keys, whatever the keyboard's are.
     */
    void pressKey(int key, boolean alt, long age) {
        press(VIRTUAL_KEY | key, key, KeyBindings.legacyAction(key), alt, age);
    }

    /** A key pressed by the autopilot, a recording or a game controller came up; see {@link #pressKey}. */
    void releaseKey(int key, long age) {
        release(VIRTUAL_KEY | key, age);
    }

    /**
     * A key went down on the keyboard. {@code keyCode} is the key's place
     * ({@link java.awt.event.KeyEvent}'s code), which picks the action from
     * {@link KeyBindings}; {@code legacy} is the key as {@link #pressKey}
     * numbers it, or -1 for Shift, Ctrl and Alt; {@code age} is how long ago
     * Windows saw it, in nanoseconds. Out of a race the arrows always move
     * through the menus, whatever they do in one.
     */
    void pressKeyboard(int keyCode, int legacy, boolean alt, long age) {
        int action;
        synchronized (inputLock) {
            int arrow = latching ? KeyBindings.NONE : KeyBindings.menuArrow(keyCode);
            action = arrow != KeyBindings.NONE ? arrow : KeyBindings.action(keyCode);
        }
        press(keyCode, legacy, action, alt, age);
    }

    /** A key came up on the keyboard; see {@link #pressKeyboard}. */
    void releaseKeyboard(int keyCode, long age) {
        release(keyCode, age);
    }

    /* a key pressed by the autopilot, a recording or a controller, told apart from the keyboard's in keysDown */
    private static final int VIRTUAL_KEY = 1 << 30;
    /** The keys down, keyboard and virtual, and the action each does (inputLock). */
    private final java.util.Map<Integer, Integer> keysDown = new java.util.HashMap<>();

    private void press(int id, int legacy, int action, boolean alt, long age) {
        if (legacy >= 0 && SettingsScreen.typeKey(legacy)) {
            return; // a size being typed on the settings screen takes every key
        }
        if (legacy == 1010) {
            PerfOverlay.toggle(); // F3 shows and hides the performance overlay
        }
        if (legacy == 1002 || legacy == 1003 || legacy == 9) {
            SettingsScreen.noteTabKey(legacy == 1002 ? -1 : 1); // Page Up, Page Down, Tab: the settings' tabs
        }
        if (exwist) {
            return;
        }
        synchronized (inputLock) {
            boolean fullscreenKey = legacy == GameInput.F11 || legacy == 10 && alt;
            if (fullscreenKey) {
                RunApp.toggleFullscreen();
            } else if (legacy == 10 || legacy == 27) {
                liveEnter = true;
            }
            if (legacy == 27) {
                SettingsScreen.noteEscape(); // the settings screen goes back on it
            }
            if (action != KeyBindings.NONE) {
                keysDown.put(id, action);
                switch (action) {
                    case KeyBindings.LOOK_BACK -> liveLookback = 1;
                    case KeyBindings.LOOK_BACK_OTHER -> liveLookback = -1;
                    case KeyBindings.CHANGE_VIEW -> liveView = (liveView + 1) % 3;
                    case KeyBindings.PAUSE -> liveEnter = true;
                    default -> heldFromKeys();
                }
            }
            if (!latching) {
                applyLiveKeys();
            }
            // under the lock too: a run-ahead tick puts the controls back as they were
            switch (action) {
                case KeyBindings.MUSIC -> u[0].mutem = !u[0].mutem;
                case KeyBindings.SOUNDS -> u[0].mutes = !u[0].mutes;
                case KeyBindings.ARROW -> u[0].arrace = !u[0].arrace;
                default -> {
                    // driving, looking and pausing are done above
                }
            }
            noteKeys(age);
        }
    }

    private void release(int id, long age) {
        if (exwist) {
            return;
        }
        synchronized (inputLock) {
            Integer action = keysDown.remove(id);
            if (action == null) {
                return;
            }
            if (action == KeyBindings.LOOK_BACK || action == KeyBindings.LOOK_BACK_OTHER) {
                liveLookback = keysDown.containsValue(KeyBindings.LOOK_BACK) ? 1
                        : keysDown.containsValue(KeyBindings.LOOK_BACK_OTHER) ? -1 : 0;
            }
            heldFromKeys();
            if (!latching) {
                applyHeldKeys();
            }
            noteKeys(age);
        }
    }

    /** Under {@link #inputLock}: the driving keys held, from every key down (two keys can share an action). */
    private void heldFromKeys() {
        boolean up = false;
        boolean down = false;
        boolean left = false;
        boolean right = false;
        boolean handbrake = false;
        for (int action : keysDown.values()) {
            up |= action == KeyBindings.ACCELERATE;
            down |= action == KeyBindings.BRAKE;
            left |= action == KeyBindings.STEER_LEFT;
            right |= action == KeyBindings.STEER_RIGHT;
            handbrake |= action == KeyBindings.HANDBRAKE;
        }
        liveUp = up;
        liveDown = down;
        liveLeft = left;
        liveRight = right;
        liveHandb = handbrake;
    }

    /* for the profile's key-to-screen readout: when the driving keys or the camera last changed (inputLock) */
    private int keysNoted = -1;
    private long keysChangedNanos;
    /* the change the last real tick and the last run-ahead tick read, game thread */
    private long tickKeysNanos;
    private long aheadKeysNanos;

    /** Under {@link #inputLock}: notes when the keys held changed, {@code age} nanoseconds ago. */
    private void noteKeys(long age) {
        int keys = heldKeys();
        if (keys != keysNoted) {
            keysNoted = keys;
            keysChangedNanos = System.nanoTime() - age;
        }
    }

    /**
     * Copies the keyboard state into the player's controls. Called under
     * {@link #inputLock}: from the event thread directly while no race is
     * running, and once at the start of every race tick otherwise.
     */
    private void applyLiveKeys() {
        applyHeldKeys();
        Control c = u[0];
        if (liveEnter) {
            c.enter = true;
            liveEnter = false;
        }
        view = liveView;
    }

    /** Only the keys held down: a consumed Enter press must not come back on release. */
    private void applyHeldKeys() {
        Control c = u[0];
        c.up = liveUp;
        c.down = liveDown;
        c.left = liveLeft;
        c.right = liveRight;
        c.handb = liveHandb;
        c.lookback = liveLookback;
    }

    /** Start of a race tick: the controls this tick will read. */
    private void latchRaceInput() {
        synchronized (inputLock) {
            latching = true;
            latchedKeys = heldKeys();
            tickKeysNanos = keysChangedNanos;
            applyLiveKeys();
        }
    }

    /** Not in a race: keys go straight to the controls again, as the menus expect. */
    private void releaseRaceInput() {
        synchronized (inputLock) {
            if (latching) {
                latching = false;
                applyLiveKeys();
            }
        }
    }

    private void resetView() {
        synchronized (inputLock) {
            liveView = 0;
            view = 0;
        }
    }

    /**
     * The folder the game was started from; data files are resolved against it.
     */
    public URL getCodeBase() {
        try {
            return new File(System.getProperty("user.dir")).toURI().toURL();
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public void stop() {
        if (exwist && gamer != null) {
            System.gc();
            // the game loop exits on its own once it sees exwist; just wake it
            gamer.interrupt();
            gamer = null;
        }
        exwist = true;
    }

    /** The window lost the keyboard: every key is let go. */
    void focusLost() {
        if (!exwist && !lostfcs) {
            lostfcs = true;
            mouses = 0;
            synchronized (inputLock) {
                liveUp = false;
                liveDown = false;
                liveLeft = false;
                liveRight = false;
                liveHandb = false;
                liveLookback = 0;
                liveEnter = false;
                keysDown.clear();
                u[0].falseo();
            }
            setCursor(new Cursor(0));
        }
    }

    void focusGained() {
        if (!exwist && lostfcs)
            lostfcs = false;
    }

    private void savecookie(String filename, String num) {
        if (session != null && session.keepsCookies()) {
            return; // recording and verifying must not touch the player's progress
        }
        try {
            /**
             * since I want full control over the filenames, we'll create a normal file in
             * the temporary file directory
             */
            try {
                File cookieTempLocation = new File(cookieDirTemp);

                if (!cookieTempLocation.exists()) {
                    cookieTempLocation.mkdirs();
                }

                File[] cookieFile = {
                        new File(cookieDirTemp + filename + ".dat")
                };

                if (!cookieFile[0].exists()) {
                    cookieFile[0].createNewFile();
                }
                FileWriter fw = new FileWriter(cookieFile[0].getPath());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(num + '\n');
                bw.close();

                File cookieZip = new File(cookieDirZip);
                if (!cookieZip.exists()) {
                    cookieZip.createNewFile();
                }

                addFile(cookieZip, cookieFile, "");

                cookieFile[0].delete();
                cookieTempLocation.delete();

                HLogger.info("Successfully saved game (" + filename + ")");
            } catch (SecurityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String fromStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }
        return out.toString();
    }

    /**
     * attempts to read a cookie
     *
     * @param string name to match
     * @return value
     */
    private static int readcookie(String name) {
        try {
            ZipFile zipFile = new ZipFile(cookieDirZip);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            String fromEntry = " ";

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().contains(name)) {
                    InputStream stream = zipFile.getInputStream(entry);
                    fromEntry = fromStream(stream);
                }
            }
            zipFile.close();

            HLogger.info("Successfully read cookie " + name + " with value " + Integer.parseInt(fromEntry));
            return Integer.parseInt(fromEntry);
        } catch (IOException ioexception) {
            // HLogger.error(ioexception.toString());
            HLogger.error(name + ".dat probably doesn't exist");
            return -1;
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    /**
     * Puts the finished frame in the window: scaled to fit, keeping its shape,
     * or pixel for pixel when scaling is off or the window is exactly the
     * picture's size. What the picture does not cover is black.
     */
    @Override
    public void paint(Graphics g) {
        if (gpuActive) {
            return; // the game thread puts every frame on screen through the graphics card
        }
        paintPicture((Graphics2D) g);
    }

    /**
     * Where the picture goes in a window of the given size, with the screen
     * shake applied, or null when the window has no room. Also remembers the
     * placement for turning mouse positions into picture coordinates.
     */
    private Rectangle placePicture(int width, int height) {
        int shakeX = 0;
        int shakeY = 0;
        if (this.shaka > 10) {
            shakeX = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
            shakeY = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
            this.shaka -= 5;
        }

        double scale = scaleToWindow
                ? Math.min(width / (double) GameFacts.screenWidth, height / (double) GameFacts.screenHeight)
                : 1D;
        if (scale <= 0D) {
            return null;
        }
        int w = (int) Math.round(GameFacts.screenWidth * scale);
        int h = (int) Math.round(GameFacts.screenHeight * scale);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        apx = x;
        apy = y;
        presentScale = scale;
        x += (int) Math.round(shakeX * scale);
        y += (int) Math.round(shakeY * scale);
        return new Rectangle(x, y, w, h);
    }

    /** Draws the picture into the window in software, as {@link #paint} does without the GPU. */
    private void paintPicture(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();
        Rectangle place = placePicture(width, height);
        if (place == null) {
            return;
        }
        int x = place.x;
        int y = place.y;
        int w = place.width;
        int h = place.height;
        if (w == offImage.getWidth(null) && h == offImage.getHeight(null)) {
            g.drawImage(this.offImage, x, y, this);
        } else {
            drawScaled(g, x, y, w, h);
        }
        // the borders, and the strip a screen shake uncovers
        g.setComposite(AlphaComposite.Src);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, Math.max(0, y));
        g.fillRect(0, y + h, width, Math.max(0, height - y - h));
        g.fillRect(0, y, Math.max(0, x), h);
        g.fillRect(x + w, y, Math.max(0, width - x - w), h);
    }

    /**
     * Draws the frame smoothly resized. The frame is scaled by {@link #frameScaler}
     * and then copied as is, which is several times faster than letting Java
     * filter it while drawing. The lock keeps a paint from the event thread
     * from reusing the scaled image while the game thread is drawing it.
     */
    private void drawScaled(Graphics2D g, int x, int y, int w, int h) {
        synchronized (frameScaler) {
            BufferedImage scaled = offImage instanceof BufferedImage frame ? frameScaler.scale(frame, w, h) : null;
            if (scaled != null) {
                g.drawImage(scaled, x, y, null);
                return;
            }
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(this.offImage, x, y, w, h, this);
    }

    /** A window x coordinate in the picture's 1280x720 coordinates. */
    private int toPictureX(int windowX) {
        return (int) Math.floor((windowX - apx) / presentScale);
    }

    /** A window y coordinate in the picture's 1280x720 coordinates. */
    private int toPictureY(int windowY) {
        return (int) Math.floor((windowY - apy) / presentScale);
    }

    /** Turns filling the window on or off, and saves the choice. */
    public static void setScaleToWindow(boolean on) {
        scaleToWindow = on;
        scaleToWindowChanged = true;
    }

    public static boolean isScaleToWindow() {
        return scaleToWindow;
    }

    /** -Dnfm.scale=fit|off, else the saved choice, else fit. */
    private void loadScaleToWindow() {
        String property = System.getProperty("nfm.scale");
        if (property != null) {
            scaleToWindow = !property.trim().equalsIgnoreCase("off");
        } else {
            scaleToWindow = readcookie(SCALING_COOKIE) != 0;
        }
        scaleToWindowChanged = false;
    }

    private void saveScaleToWindowIfChanged() {
        if (scaleToWindowChanged) {
            scaleToWindowChanged = false;
            savecookie(SCALING_COOKIE, scaleToWindow ? "1" : "0");
        }
    }

    /* ---------------------------------------------------------- picture shape */

    /** Draw the picture at the window's own shape (on unless turned off), else at 16:9 with bars. */
    private static volatile boolean windowShape = true;
    private static volatile boolean windowShapeChanged;
    private static final String SHAPE_COOKIE = "pictureshape";
    /* the shape the picture should take, and since when (a window being dragged changes it often) */
    private int shapeWantedWidth = GameFacts.GAME_WIDTH;
    private int shapeWantedHeight = GameFacts.GAME_HEIGHT;
    private long shapeWantedSince;
    private boolean shapeFitted;

    /** Draws the picture at the window's shape, or at 16:9; saves the choice. */
    public static void setWindowShape(boolean on) {
        windowShape = on;
        windowShapeChanged = true;
    }

    /** The picture shape setting as the console and the log write it: window or 16:9. */
    public static String pictureShapeName() {
        return windowShape ? "window" : "16:9";
    }

    public static boolean isWindowShape() {
        return windowShape;
    }

    public static String describePictureShape() {
        return "drawn " + GameFacts.screenWidth + "x" + GameFacts.screenHeight;
    }

    /** -Dnfm.shape=window|16:9, else the saved choice, else the window's. */
    private void loadWindowShape() {
        String property = System.getProperty("nfm.shape");
        if (property != null) {
            windowShape = !property.trim().equals("16:9");
        } else {
            windowShape = readcookie(SHAPE_COOKIE) != 0;
        }
        windowShapeChanged = false;
    }

    /**
     * At the start of a tick: saves a changed choice, and when the picture
     * should take another shape, and has wanted it for a moment, gives it
     * that shape (see GameFacts.pictureShape). The first time, at once.
     */
    private void fitPictureShape(long now) {
        if (windowShapeChanged) {
            windowShapeChanged = false;
            savecookie(SHAPE_COOKIE, windowShape ? "1" : "0");
        }
        Dimension want = windowShape ? GameFacts.pictureShape(getWidth(), getHeight())
                : new Dimension(GameFacts.GAME_WIDTH, GameFacts.GAME_HEIGHT);
        if (want.width != shapeWantedWidth || want.height != shapeWantedHeight) {
            shapeWantedWidth = want.width;
            shapeWantedHeight = want.height;
            shapeWantedSince = now;
        }
        boolean settled = !shapeFitted || now - shapeWantedSince > 250_000_000L;
        if (!settled || want.width == GameFacts.screenWidth && want.height == GameFacts.screenHeight) {
            return;
        }
        shapeFitted = getWidth() > 0 && getHeight() > 0;
        synchronized (presentLock) {
            GameFacts.screenWidth = want.width;
            GameFacts.screenHeight = want.height;
            if (gpu != null) {
                gpu.setPictureSize(want.width, want.height);
            }
        }
        if (offImage != null) {
            // the menus and HUD keep how sharp they are drawn; fitUiResolution goes on from here
            resizeBuffers(Math.round(want.width * uiScale), Math.round(want.height * uiScale));
        }
        if (ui != null) {
            ui.fitScreen();
        }
        keptStale = true;
        HLogger.info("The picture is now " + want.width + "x" + want.height + ", shape " + pictureShapeName());
    }

    public GameSparker() {

        // INFO
        System.out.println(IS_64_BIT + "BIT " + OPERATING_SYSTEM.toUpperCase());

        // DS-patch: Dynamic libs path - BEGIN
        String dllPath = "lib/dlls/";
        if (IS_MAC) {
            dllPath += "mac";
        } else {
            dllPath += (IS_WINDOWS ? "win" : "linux") + IS_64_BIT;
        }
        System.setProperty("org.lwjgl.librarypath", dllPath);
        // DS-patch - END

        BASSLoader.initializeBASS();

        u = new Control[51];
        mouses = 0;
        xm = 0;
        ym = 0;
        lostfcs = false;
        exwist = true;
        nob = 0;
        notb = 0;
        view = 0;
    }

    /**
     * @param input name of model you want id of
     * @return Position on model in array. If you spelled it wrong or if it doesn't
     *         eist, it returns -1, so you have that to look forward to.
     * @author Kaffeinated
     */
    private int getModel(String input) {

        String[][] allModels = new String[][] {
                carModels, trackModels, extraModels
        }; /// need to have all the model arrays here

        int modelId = 0;

        for (int i = 0; i < allModels.length; i++) {
            for (int j = 0; j < allModels[i].length; j++) {
                if (Objects.equals(input, allModels[i][j])) {
                    int addWhat = 0;
                    if (i == 1) {
                        addWhat = carModels.length;
                    }
                    if (i == 2) {
                        addWhat = carModels.length + trackModels.length;
                    }
                    modelId = j + addWhat;
                    // HLogger.info("Found model " + modelId + " matching string \"" + input +
                    // "\"");
                    return modelId;
                }
            }
        }
        HLogger.warn("No results for getModel | check you're speling and grammer");
        return -1;
    }

    /**
     * Loads all models
     *
     * @param models   where each model goes, by its place in the archive's lists
     * @param trackers the surfaces the models' pieces add
     * @param gameUi   the menus, HUD and loading bar
     * @author Kaffeinated, Omar Waly
     */
    private void loadbase(final ContO models[], Trackers trackers, GameUi gameUi) {
        gameUi.addLoaded(6);
        try {
            for (GameFiles.Entry zipentry : GameFiles.archive("models")) {
                int modelId = -1;

                final int carCount = carModels.length;
                final int trackCount = trackModels.length;
                // final int extraCount = extraModels.length;

                for (int car = 0; car < carModels.length; car++)
                    if (zipentry.name().startsWith(carModels[car]))
                        modelId = car;

                for (int track = 0; track < trackModels.length; track++)
                    if (zipentry.name().startsWith(trackModels[track]))
                        modelId = track + carCount;

                for (int extra = 0; extra < extraModels.length; extra++)
                    if (zipentry.name().startsWith(extraModels[extra]))
                        modelId = extra + trackCount + carCount;

                models[modelId] = new ContO(zipentry.data(), trackers);
                gameUi.addLoaded(1);
            }
            /*
             * be sure to add your added arrays here
             */
            HLogger.info("Contos loaded: " + (carModels.length + trackModels.length + extraModels.length));
            ContosCount = carModels.length + trackModels.length + extraModels.length;
        } catch (IOException e) {
            HLogger.error("Error Reading Models: " + e);
            e.printStackTrace();
        }
        System.gc();
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    public void start() {
        if (gamer == null)
            gamer = new Thread(this);
        if (gamer.getState() == Thread.State.NEW)
            gamer.start();
    }

    /** A mouse button went down at (x, y) in the panel. */
    void mousePressedAt(int x, int y) {
        if (!exwist && mouses == 0) {
            xm = toPictureX(x);
            ym = toPictureY(y);
            mouses = 1;
        }
    }

    /**
     * Loads stage elements
     *
     * @param scene       the cars and track pieces placed in the race
     * @param models      every loaded model, cars first
     * @param trackers    the track's surfaces and walls
     * @param checkpoints the stage's checkpoints and fixing hoops
     * @param gameUi      the menus, HUD and loading bar
     * @param drivers     the cars' physics, one per player
     * @param record      the replay buffers
     * @author Kaffeinated, Omar Waly
     */

    private void loadstage(ContO scene[], ContO models[], Trackers trackers, CheckPoints checkpoints,
            GameUi gameUi, Madness drivers[], Record record, boolean custom) {
        /*
         * Every race starts its random streams from one seed, before anything
         * of the stage draws a number, so the race can be played again exactly.
         */
        long seed = session != null ? session.stageLoading(custom) : RaceRandom.newSeed();
        world.seed(seed);
        Medium.seedRace(seed);
        trackers.nt = 0;
        nob = GameFacts.numberOfPlayers;
        notb = 0;
        checkpoints.n = 0;
        checkpoints.nsp = 0;
        checkpoints.fn = 0;
        checkpoints.haltall = false;
        checkpoints.wasted = 0;
        checkpoints.catchfin = 0;
        Medium.lightson = false;
        Medium.detailtype = 2;
        Medium.ground = 250;

        reverseYRot = false;

        noboffset = 10;
        nobfix = carModels.length - noboffset;

        wallmodel = "thewall";

        //reset noarrow and nostatus
        gameUi.arrowDisabled = false;
        gameUi.opstatusDisabled = false;

        resetView();
        int r_wall = 0;
        int l_wall = 100;
        int t_wall = 0;
        int b_wall = 100;

        CheckPoints.customTrack = false;

        loadStage = stageDir + stageSubDir + checkpoints.stage + ".txt";
        if (gameUi.nfmmode == 1) {
            stageSubDir = "nfm1/";
        } else if (gameUi.nfmmode == 2) {
            stageSubDir = "nfm2/";
        }

        if (custom) {
            loadStage = stageDir + loadStageCus + ".txt";
            HLogger.info(loadStage);
        }

        String unused = "";

        try (BufferedReader bufferedreader = new BufferedReader(new FileReader(GameFiles.file(loadStage)))) {
            for (String line; (line = bufferedreader.readLine()) != null;) {
                line = line.trim();

                if (line.startsWith("mountains"))
                    Medium.mgen = Utility.getint("mountains", line, 0);
                if (line.startsWith("snap"))
                    Medium.setSnap(Utility.getint("snap", line, 0), Utility.getint("snap", line, 1),
                            Utility.getint("snap", line, 2));
                if (line.startsWith("sky")) {
                    Medium.setSky(Utility.getint("sky", line, 0), Utility.getint("sky", line, 1),
                            Utility.getint("sky", line, 2));
                    gameUi.snap(checkpoints.stage);
                }
                if (line.startsWith("ground"))
                    Medium.setGround(Utility.getint("ground", line, 0), Utility.getint("ground", line, 1),
                            Utility.getint("ground", line, 2));
                if (line.startsWith("polys"))
                    Medium.setPolys(Utility.getint("polys", line, 0), Utility.getint("polys", line, 1),
                            Utility.getint("polys", line, 2));
                if (line.startsWith("fog"))
                    Medium.setFade(Utility.getint("fog", line, 0), Utility.getint("fog", line, 1),
                            Utility.getint("fog", line, 2));
                if (line.startsWith("density"))
                    Medium.fogd = Utility.getint("density", line, 0);
                if (line.startsWith("texture")) {
                    Medium.setTexture(Utility.getint("texture", line, 0), Utility.getint("texture", line, 1),
                            Utility.getint("texture", line, 2),
                            Utility.getint("texture", line, 3));
                }
                if (line.startsWith("clouds")) {
                    Medium.setClouds(Utility.getint("clouds", line, 0), Utility.getint("clouds", line, 1),
                            Utility.getint("clouds", line, 2),
                            Utility.getint("clouds", line, 3), Utility.getint("clouds", line, 4));
                }
                if (line.startsWith("noclouds"))
                    Medium.noclouds = true;
                if (line.startsWith("fadefrom")) {
                    Medium.fadeFrom(Utility.getint("fadefrom", line, 0));
                    Medium.origfade = Medium.fade[0];
                }
                if (line.startsWith("lightson"))
                    Medium.lightson = true;

                if (line.startsWith("noarrow"))
                    gameUi.arrowDisabled = true;
                if (line.startsWith("nostatus"))
                    gameUi.opstatusDisabled = true;

                if (line.startsWith("idoffset")) {
                    noboffset = Utility.getint("idoffset", line, 0);
                    nobfix = carModels.length - noboffset;
                }

                if (line.startsWith("yrot")) {
                    reverseYRot = true;
                }

                if (line.startsWith("set")) {
                    int piece = Utility.getint("set", line, 0);
                    piece += nobfix;
                
                    // compute default Y (ground-height)
                    int yVal = Medium.ground - models[piece].grat;
                    int rot = Utility.getint("set", line, 3);
                
                    // if there *is* a 5th comma-separated value, use that instead
                    // (splitting only the part inside the parentheses)
                    String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
                    String[] parts = inside.split("\\s*,\\s*");
                    if (parts.length > 4) {
                        yVal = Utility.getint("set", line, 4);
                        if (reverseYRot) {
                            yVal = Utility.getint("set", line, 3);
                            rot = Utility.getint("set", line, 4);
                        }
                    }
                
                    // now create the object, exactly as before but with our yVal
                    scene[nob] = new ContO(
                        models[piece],
                        Utility.getint("set", line, 1),
                        yVal,
                        Utility.getint("set", line, 2),
                        rot
                    );
                
                    if (line.contains(")p")) {
                        checkpoints.x[checkpoints.n] = Utility.getint("set", line, 1);
                        checkpoints.z[checkpoints.n] = Utility.getint("set", line, 2);
                
                        // same trick for the checkpoint Y (default=0)
                        if (parts.length > 4) {
                            checkpoints.y[checkpoints.n] = Utility.getint("set", line, 4);
                            if (reverseYRot) {
                                checkpoints.y[checkpoints.n] = Utility.getint("set", line, 3);
                            }
                        } else {
                            checkpoints.y[checkpoints.n] = 0;
                        }
                
                        checkpoints.typ[checkpoints.n] = 0;
                        if (line.contains(")pt")) checkpoints.typ[checkpoints.n] = -1;
                        if (line.contains(")pr")) checkpoints.typ[checkpoints.n] = -2;
                        if (line.contains(")pl")) checkpoints.typ[checkpoints.n] = -2;
                        if (line.contains(")po")) checkpoints.typ[checkpoints.n] = -3;
                        if (line.contains(")ph")) checkpoints.typ[checkpoints.n] = -4;
                
                        checkpoints.n++;
                        notb = nob + 1;
                    }
                    nob++;
                }
                if (line.startsWith("wall")) {
                    String modelname = Utility.getstring("wall", line, 0);
                    wallmodel = modelname;
                }
                if (line.startsWith("ds:set")) {
                    String modelname = Utility.getstring("ds:set", line, 0);
                    int id = getModel(modelname);
                    int yVal = Medium.ground - models[id].grat;
                
                    // if there *is* a 5th comma-separated value, use that instead
                    // (splitting only the part inside the parentheses)
                    String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
                    String[] parts = inside.split("\\s*,\\s*");
                    if (parts.length > 4) {
                        yVal = Utility.getint("ds:set", line, 4);
                    }
                
                    // now create the object, exactly as before but with our yVal
                    scene[nob] = new ContO(
                        models[id],
                        Utility.getint("ds:set", line, 1),
                        yVal,
                        Utility.getint("ds:set", line, 2),
                        Utility.getint("ds:set", line, 3)
                    );
                
                    if (line.contains(")p")) {
                        checkpoints.x[checkpoints.n] = Utility.getint("ds:set", line, 1);
                        checkpoints.z[checkpoints.n] = Utility.getint("ds:set", line, 2);
                
                        // same trick for the checkpoint Y (default=0)
                        if (parts.length > 4) {
                            checkpoints.y[checkpoints.n] = Utility.getint("ds:set", line, 4);
                        } else {
                            checkpoints.y[checkpoints.n] = 0;
                        }
                
                        checkpoints.typ[checkpoints.n] = 0;
                        if (line.contains(")pt")) checkpoints.typ[checkpoints.n] = -1;
                        if (line.contains(")pr")) checkpoints.typ[checkpoints.n] = -2;
                        if (line.contains(")pl")) checkpoints.typ[checkpoints.n] = -2;
                        if (line.contains(")po")) checkpoints.typ[checkpoints.n] = -3;
                        if (line.contains(")ph")) checkpoints.typ[checkpoints.n] = -4;
                
                        checkpoints.n++;
                        notb = nob + 1;
                    }
                    nob++;
                }
                if (line.startsWith("chk")) {
                    int piece = Utility.getint("chk", line, 0);
                    piece += nobfix;
                
                    // compute default Y (ground-height)
                    int yVal = Medium.ground - models[piece].grat;
                    int rot = Utility.getint("chk", line, 3);
                
                    // grab the args between '(' and ')', split on commas
                    String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
                    String[] parts = inside.split("\\s*,\\s*");
                    // if there's a 5th element, use it as Y instead
                    if (parts.length > 4) {
                        yVal = Utility.getint("chk", line, 4);
                        if (reverseYRot) {
                            yVal = Utility.getint("chk", line, 3);
                            rot = Utility.getint("chk", line, 4);
                        }
                    }
                
                    // create your object exactly as before, but with our yVal
                    scene[nob] = new ContO(
                        models[piece],
                        Utility.getint("chk", line, 1),
                        yVal,
                        Utility.getint("chk", line, 2),
                        rot
                    );
                
                    // now the checkpoint data:
                    checkpoints.x[checkpoints.n] = Utility.getint("chk", line, 1);
                    checkpoints.z[checkpoints.n] = Utility.getint("chk", line, 2);
                    // use the same yVal
                    checkpoints.y[checkpoints.n] = yVal;
                
                    // type based on rotation-arg logic unchanged
                    if (Utility.getint("chk", line, 3) == 0)
                        checkpoints.typ[checkpoints.n] = 1;
                    else
                        checkpoints.typ[checkpoints.n] = 2;
                
                    checkpoints.pcs = checkpoints.n;
                    checkpoints.n++;
                    scene[nob].checkpoint = checkpoints.nsp + 1;
                    checkpoints.nsp++;
                    nob++;
                    notb = nob;
                }
                if (line.startsWith("fix")) {
                    int piece = Utility.getint("fix", line, 0);
                    piece += nobfix;
                    scene[nob] = new ContO(models[piece], Utility.getint("fix", line, 1), Utility.getint("fix", line, 3),
                            Utility.getint("fix", line, 2), Utility.getint("fix", line, 4));
                    checkpoints.fx[checkpoints.fn] = Utility.getint("fix", line, 1);
                    checkpoints.fz[checkpoints.fn] = Utility.getint("fix", line, 2);
                    checkpoints.fy[checkpoints.fn] = Utility.getint("fix", line, 3);
                    scene[nob].elec = true;
                    if (Utility.getint("fix", line, 4) != 0) {
                        checkpoints.roted[checkpoints.fn] = true;
                        scene[nob].roted = true;
                    } else {
                        checkpoints.roted[checkpoints.fn] = false;
                    }
                    checkpoints.special[checkpoints.fn] = line.contains(")s");
                    checkpoints.fn++;
                    nob++;
                    notb = nob;
                }
                if (line.startsWith("nlaps"))
                    checkpoints.nlaps = Utility.getint("nlaps", line, 0);
                if (line.startsWith("name"))
                    checkpoints.name = Utility.getstring("name", line, 0).replace('|', ',');
                if (line.startsWith("soundtrack")) {
                    CheckPoints.customTrack = true;
                    CheckPoints.trackname = Utility.getstring("soundtrack", line, 0);
                    CheckPoints.trackformat = Utility.getstring("soundtrack", line, 1);
                    // GameUi.sndsize[18] = Utility.getint("soundtrack", string, 2);
                }

                int wall_id = getModel(wallmodel);

                if (line.startsWith("maxr")) {
                    int wallCount = Utility.getint("maxr", line, 0);
                    int wallAt = Utility.getint("maxr", line, 1);
                    r_wall = wallAt;
                    int wallStart = Utility.getint("maxr", line, 2);
                    for (int segment = 0; segment < wallCount; segment++) {
                        scene[nob] = new ContO(models[wall_id], wallAt, Medium.ground - models[wall_id].grat,
                                segment * 4800 + wallStart, 0);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.x[trackers.nt] = wallAt + 500;
                    trackers.radx[trackers.nt] = 600;
                    trackers.z[trackers.nt] = ((wallCount * 4800) / 2 + wallStart) - 2400;
                    trackers.radz[trackers.nt] = (wallCount * 4800) / 2;
                    trackers.xy[trackers.nt] = 90;
                    trackers.zy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }
                if (line.startsWith("maxl")) {
                    int wallCount = Utility.getint("maxl", line, 0);
                    int wallAt = Utility.getint("maxl", line, 1);
                    l_wall = wallAt;
                    int wallStart = Utility.getint("maxl", line, 2);
                    for (int segment = 0; segment < wallCount; segment++) {
                        scene[nob] = new ContO(models[wall_id], wallAt, Medium.ground - models[wall_id].grat,
                                segment * 4800 + wallStart, 0);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.x[trackers.nt] = wallAt - 500;
                    trackers.radx[trackers.nt] = 600;
                    trackers.z[trackers.nt] = ((wallCount * 4800) / 2 + wallStart) - 2400;
                    trackers.radz[trackers.nt] = (wallCount * 4800) / 2;
                    trackers.xy[trackers.nt] = -90;
                    trackers.zy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }

                if (line.startsWith("maxt")) {
                    int wallCount = Utility.getint("maxt", line, 0);
                    int wallAt = Utility.getint("maxt", line, 1);
                    t_wall = wallAt;
                    int wallStart = Utility.getint("maxt", line, 2);
                    for (int segment = 0; segment < wallCount; segment++) {
                        scene[nob] = new ContO(models[wall_id], segment * 4800 + wallStart, Medium.ground - models[wall_id].grat,
                                wallAt, 90);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.z[trackers.nt] = wallAt + 500;
                    trackers.radz[trackers.nt] = 600;
                    trackers.x[trackers.nt] = ((wallCount * 4800) / 2 + wallStart) - 2400;
                    trackers.radx[trackers.nt] = (wallCount * 4800) / 2;
                    trackers.zy[trackers.nt] = 90;
                    trackers.xy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }
                if (line.startsWith("maxb")) {
                    int wallCount = Utility.getint("maxb", line, 0);
                    int wallAt = Utility.getint("maxb", line, 1);
                    b_wall = wallAt;
                    int wallStart = Utility.getint("maxb", line, 2);
                    for (int segment = 0; segment < wallCount; segment++) {
                        scene[nob] = new ContO(models[wall_id], segment * 4800 + wallStart, Medium.ground - models[wall_id].grat,
                                wallAt, 90);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.z[trackers.nt] = wallAt - 500;
                    trackers.radz[trackers.nt] = 600;
                    trackers.x[trackers.nt] = ((wallCount * 4800) / 2 + wallStart) - 2400;
                    trackers.radx[trackers.nt] = (wallCount * 4800) / 2;
                    trackers.zy[trackers.nt] = -90;
                    trackers.xy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }
                if (line.startsWith("detail")) {
                    Medium.detailtype = Utility.getint("detail", line, 0);
                }
            }
            Medium.newpolys(l_wall, r_wall - l_wall, b_wall, t_wall - b_wall, trackers, notb);
            Medium.newmountains(l_wall, r_wall, b_wall, t_wall);
            Medium.newclouds(l_wall, r_wall, b_wall, t_wall);
            Medium.newstars();
        } catch (IOException e) {
            String exceptStr = e.toString();
            final int maxChar = 30;

            int maxLength = (exceptStr.length() < maxChar) ? exceptStr.length() : maxChar;
            stageError = e.toString().substring(0, maxLength) + "...";

            gameUi.fase = Phase.ERRORLOADINGSTAGE;
            HLogger.error("Error loading stage " + checkpoints.stage);
            e.printStackTrace();
        }
        if (checkpoints.stage == 16)
            Medium.lightn = 0;
        else
            Medium.lightn = -1;
        Medium.nochekflk = checkpoints.stage != 1;
        if (gameUi.fase == Phase.STAGESELECTTRIGGER) {
            Medium.trx = 0L;
            Medium.trz = 0L;
            if (trackers.nt >= 4) {
                int corner = 4;
                do {
                    Medium.trx += trackers.x[trackers.nt - corner];
                    Medium.trz += trackers.z[trackers.nt - corner];
                } while (--corner > 0);
            }
            Medium.trx = Medium.trx / 4L;
            Medium.trz = Medium.trz / 4L;
            Medium.ptr = 0;
            Medium.ptcnt = -10;
            Medium.hit = 45000;
            Medium.fallen = 0;
            Medium.nrnd = 0;
            Medium.trk = true;
            //Medium.detailtype = 0;
            gameUi.fase = Phase.STAGESELECT;
            mouses = 0;
        }
        int racer = 0;
        do
            u[racer].reset(checkpoints, gameUi.sc[racer]);
        while (++racer < GameFacts.numberOfPlayers);
        gameUi.resetstat(checkpoints.stage);
        racer = 0;
        do {
            if (racer % 3 == 0) {
                scene[racer] = new ContO(models[gameUi.sc[racer]], 0, 250 - models[gameUi.sc[racer]].grat,
                        -760 + ((racer / 3) * 760), 0);
            }
            if (racer % 3 == 1) {
                scene[racer] = new ContO(models[gameUi.sc[racer]], -350, 250 - models[gameUi.sc[racer]].grat,
                        -380 + ((int) (racer / 3) * 760), 0);
            }
            if (racer % 3 == 2) {
                scene[racer] = new ContO(models[gameUi.sc[racer]], 350, 250 - models[gameUi.sc[racer]].grat,
                        -380 + ((int) (racer / 3) * 760), 0);
            }
            drivers[racer].reseto(gameUi.sc[racer], scene[racer], checkpoints);
        } while (++racer < GameFacts.numberOfPlayers);
        record.reset(scene);
        sendSoundStage(scene);
        if (session != null) {
            session.stageLoaded();
        }
        System.gc();
    }

    /**
     * motion
     *
     * @param drivers  the cars' physics; the first is the player's
     * @param shakeAmt shake sensitivity
     *                 1 normal
     *                 20 maximum
     * @param maxAmt   maximum displacement of the screen while shaking
     */
    private void initMoto(Madness drivers[], int shakeAmt, int maxAmt) {
        if (drivers[0].shakedam > 0) {
            shaka = drivers[0].shakedam / (20 / shakeAmt);
            drivers[0].shakedam = 0;
            if (shaka > maxAmt) {
                shaka = maxAmt;
            }
            shaka--;
        }
    }

    /**
     * Copies the transforms that the presentation pass interpolates from.
     * Called at the beginning of every in-game simulation tick, before physics
     * has a chance to move anything.
     */
    private void snapshotFrameState() {
        int np = GameFacts.numberOfPlayers;
        if (np > prevCarX.length) {
            np = prevCarX.length;
        }
        for (int i = 0; i < np; i++) {
            ContO conto = sceneObjects[i];
            prevCarX[i] = conto.x;
            prevCarY[i] = conto.y;
            prevCarZ[i] = conto.z;
            prevCarXZ[i] = conto.xz;
            prevCarXY[i] = conto.xy;
            prevCarZY[i] = conto.zy;
            prevCarWXZ[i] = conto.wxz;
            prevCarWZY[i] = conto.wzy;
            prevCarRef[i] = conto;
        }
        prevCamX = Medium.x;
        prevCamY = Medium.y;
        prevCamZ = Medium.z;
        prevCamXZ = Medium.xz;
        prevCamZY = Medium.zy;
        prevFrameValid = true;
    }

    private static int mix(int a, int b, float t) {
        return a + Math.round((b - a) * t);
    }

    /**
     * For -Dnfm.motiontest: at a few race ticks, records the scene for the
     * graphics card at 49 points between the last two ticks and writes the
     * lists to {@code motion-<tick>.bin} (per point: the list's length, then
     * the list), so a script can check every corner moves smoothly.
     */
    private void motionTest() {
        int[] at = {200, 250, 300, 350, 400, 450};
        if (motionTestDone >= at.length || ui.fase != Phase.INGAME || raceTicks != at[motionTestDone]
                || !prevFrameValid) {
            return;
        }
        boolean wasScene = gpuSceneFrame;
        java.io.File file = new java.io.File(MOTION_TEST, "motion-" + raceTicks + ".bin");
        file.getParentFile().mkdirs();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(new java.io.FileOutputStream(file)))) {
            gpuSceneFrame = true;
            SceneRecorder.tagging = true;
            int steps = 48;
            for (int k = 0; k <= steps; k++) {
                renderIngameScene(k / (float) steps);
                nfm.lit.gpu.SceneList list = sceneRecorder.list();
                out.writeInt(list.size());
                int[] data = list.data();
                for (int i = 0; i < list.size(); i++) {
                    out.writeInt(data[i]);
                }
            }
            HLogger.info("[motiontest] wrote " + file);
            if (RETAINED) {
                writeKeptMotion(steps);
            }
        } catch (java.io.IOException ex) {
            HLogger.warn("[motiontest] " + ex);
        } finally {
            gpuSceneFrame = wasScene;
            SceneRecorder.tagging = false;
            motionTestDone++;
        }
    }

    /**
     * For the motion test: the scene kept for this tick (see
     * {@link RetainedScene}), moved to the same moments as the scenes drawn
     * the whole way, into {@code kept-<tick>.bin}.
     */
    private void writeKeptMotion(int steps) throws java.io.IOException {
        java.io.File file = new java.io.File(MOTION_TEST, "kept-" + raceTicks + ".bin");
        recordRetained(false);
        retainedValid = false;
        if (retainedRecorder.unsupported()) {
            HLogger.warn("[motiontest] the kept scene could not be recorded");
            return;
        }
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(new java.io.FileOutputStream(file)))) {
            for (int k = 0; k <= steps; k++) {
                framePose(k / (float) steps, false, framePose);
                nfm.lit.gpu.SceneList list = retainedRecorder.list();
                retainedScene.realize(framePose, list.data());
                out.writeInt(list.size());
                int[] data = list.data();
                for (int i = 0; i < list.size(); i++) {
                    out.writeInt(data[i]);
                }
            }
        }
        HLogger.info("[motiontest] wrote " + file);
    }

    /** How far the exact value between a and b is past {@link #mix}'s whole one. */
    private static float fraction(int a, int b, float t) {
        float exact = (b - a) * t;
        return exact - Math.round(exact);
    }

    /** How far the exact value is past {@link #mixPeriodic}'s whole one. */
    private static float periodicFraction(int a, int b, float t, int period) {
        int half = period / 2;
        float exact = (Math.floorMod(b - a + half, period) - half) * t;
        return exact - Math.round(exact);
    }

    /**
     * Interpolates an angle along the shortest arc so a wrap from 359 to 1 does
     * not spin the car all the way around between two ticks.
     */
    private static int mixAngle(int a, int b, float t) {
        return mixPeriodic(a, b, t, 360);
    }

    /**
     * Interpolates a value that wraps every {@code period} units, along the
     * shorter way round. Wheel roll, for example, wraps every 45 degrees.
     */
    private static int mixPeriodic(int a, int b, float t, int period) {
        int half = period / 2;
        int delta = Math.floorMod(b - a + half, period) - half;
        return a + Math.round(delta * t);
    }

    private static boolean teleported(int dx, int dy, int dz) {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > TELEPORT_DISTANCE;
    }

    /**
     * Draws all scenery and cars, depth sorted. Anything whose distance was not
     * resolved on the previous pass (newly reset cars, track pieces) is drawn
     * first so it ends up behind everything else.
     */
    /**
     * Draws the scene back to front. Objects out of view (distance 0) are
     * drawn first, in scene order; they update their distance as they draw.
     * The rest go from the farthest to the nearest, and of two at the same
     * distance the later one in the scene first: the order the original's
     * pairwise ranking produced, found by one sort instead of comparing every
     * pair.
     */
    private void drawWorldObjects(Graphics2D g) {
        if (drawKeys.length < nob) {
            drawKeys = new long[nob];
        }
        int count = 0;
        for (int i = 0; i < nob; i++) {
            int dist = sceneObjects[i].dist;
            if (dist != 0) {
                // ascending by distance, then by index; walked backwards below
                drawKeys[count++] = ((long) dist << 32) | i;
            } else {
                sceneObjects[i].d(g);
            }
        }
        Arrays.sort(drawKeys, 0, count);
        for (int k = count - 1; k >= 0; k--) {
            sceneObjects[(int) drawKeys[k]].d(g);
        }
    }

    /**
     * Draws one step of the world, invisibly, at the exact state the last tick
     * left. The original drew the world once at the start of every tick, before
     * the simulation, and the race reads back what drawing did: a car out of
     * view finishes a repair hoop at once, one in view waits for the repair
     * animation, and the HUD's opponent arrows follow what is on screen. Doing
     * that step here, at tick state and never at an interpolated frame, keeps
     * races exactly as they were and independent of the frame rate. Presented
     * frames then draw on top and take their side effects back.
     */
    private void drawSimulationStep() {
        Medium.d(stepGraphics);
        drawWorldObjects(stepGraphics);
    }

    /**
     * Renders the in-game scene at an arbitrary presentation time. The frame is
     * drawn and then rolled back, so it changes nothing but the screen: the
     * steps that drawing advances belong to {@link #drawSimulationStep()}.
     *
     * @param alpha 0 = state at the previous simulation tick, 1 = state at the
     *              most recent one
     */
    private void renderIngameScene(float alpha) {
        final int np = Math.min(GameFacts.numberOfPlayers, savedCarX.length);

        for (int i = 0; i < np; i++) {
            ContO conto = sceneObjects[i];
            savedCarX[i] = conto.x;
            savedCarY[i] = conto.y;
            savedCarZ[i] = conto.z;
            savedCarXZ[i] = conto.xz;
            savedCarXY[i] = conto.xy;
            savedCarZY[i] = conto.zy;
            savedCarWXZ[i] = conto.wxz;
            savedCarWZY[i] = conto.wzy;
        }
        int camX = Medium.x;
        int camY = Medium.y;
        int camZ = Medium.z;
        int camXZ = Medium.xz;
        int camZY = Medium.zy;
        // a frame for the graphics card also gets the exact positions between the whole ones
        Precise.begin(retainedRecording ? recordingRecorder : gpuSceneFrame ? sceneRecorder : null,
                retainedRecording ? recordingScene : null);

        if (prevFrameValid && alpha < 1F) {
            for (int i = 0; i < np; i++) {
                ContO conto = sceneObjects[i];
                if (conto != prevCarRef[i] || teleported(savedCarX[i] - prevCarX[i],
                        savedCarY[i] - prevCarY[i], savedCarZ[i] - prevCarZ[i])) {
                    // new object, respawn or teleport: interpolating would smear the car
                    continue;
                }
                conto.x = mix(prevCarX[i], savedCarX[i], alpha);
                conto.y = mix(prevCarY[i], savedCarY[i], alpha);
                conto.z = mix(prevCarZ[i], savedCarZ[i], alpha);
                conto.xz = mixAngle(prevCarXZ[i], savedCarXZ[i], alpha);
                conto.xy = mixAngle(prevCarXY[i], savedCarXY[i], alpha);
                conto.zy = mixAngle(prevCarZY[i], savedCarZY[i], alpha);
                conto.wxz = mix(prevCarWXZ[i], savedCarWXZ[i], alpha);
                conto.wzy = mixPeriodic(prevCarWZY[i], savedCarWZY[i], alpha, 45);
                if (Precise.on) {
                    Precise.car(i, conto,
                            fraction(prevCarX[i], savedCarX[i], alpha),
                            fraction(prevCarY[i], savedCarY[i], alpha),
                            fraction(prevCarZ[i], savedCarZ[i], alpha),
                            periodicFraction(prevCarXZ[i], savedCarXZ[i], alpha, 360),
                            periodicFraction(prevCarXY[i], savedCarXY[i], alpha, 360),
                            periodicFraction(prevCarZY[i], savedCarZY[i], alpha, 360),
                            fraction(prevCarWXZ[i], savedCarWXZ[i], alpha),
                            periodicFraction(prevCarWZY[i], savedCarWZY[i], alpha, 45));
                }
            }
            if (!teleported(camX - prevCamX, camY - prevCamY, camZ - prevCamZ)) {
                Medium.x = mix(prevCamX, camX, alpha);
                Medium.y = mix(prevCamY, camY, alpha);
                Medium.z = mix(prevCamZ, camZ, alpha);
                Medium.xz = mixAngle(prevCamXZ, camXZ, alpha);
                Medium.zy = mixAngle(prevCamZY, camZY, alpha);
                if (Precise.on) {
                    Precise.camX = fraction(prevCamX, camX, alpha);
                    Precise.camY = fraction(prevCamY, camY, alpha);
                    Precise.camZ = fraction(prevCamZ, camZ, alpha);
                    Precise.camXZ = periodicFraction(prevCamXZ, camXZ, alpha, 360);
                    Precise.camZY = periodicFraction(prevCamZY, camZY, alpha, 360);
                }
            }
        }

        saveSceneDrawState();
        // smooth effects: where dust and debris are drawn between the ticks (the kept scene moves them itself)
        EffectTrails.begin(smoothEffects, retainedRecording ? Float.NaN : prevFrameValid ? alpha : 1F);
        try (Medium.ShownView shown = Medium.shownView()) { // drawn the picture's shape, from the same place
            Graphics2D target = retainedRecording ? recordingRecorder.begin(rd)
                    : gpuSceneFrame ? sceneRecorder.begin(rd) : rd;
            Precise.cameraAngles();
            Medium.d(target);
            Precise.cameraAngles();
            drawWorldObjects(target);
        } finally {
            EffectTrails.end();
            restoreSceneDrawState();
            Precise.end();
        }

        for (int i = 0; i < np; i++) {
            ContO conto = sceneObjects[i];
            conto.x = savedCarX[i];
            conto.y = savedCarY[i];
            conto.z = savedCarZ[i];
            conto.xz = savedCarXZ[i];
            conto.xy = savedCarXY[i];
            conto.zy = savedCarZY[i];
            conto.wxz = savedCarWXZ[i];
            conto.wzy = savedCarWZY[i];
        }
        Medium.x = camX;
        Medium.y = camY;
        Medium.z = camZ;
        Medium.xz = camXZ;
        Medium.zy = camZY;
    }

    /**
     * One tick of a race: replaced cars, the invisible draw step the race
     * reads back, collisions, physics, the replay buffer, standings, the AI,
     * the camera and the HUD. The real tick runs it with the latched keys; the
     * speculative tick (see {@link #speculate}) runs it ahead with the keys held
     * now and puts everything back.
     */
    private void advanceRace() {
        int racer = 0;
        do {
            if (racers[racer].newcar) {
                int turnXz = sceneObjects[racer].xz;
                int turnXy = sceneObjects[racer].xy;
                int turnZy = sceneObjects[racer].zy;
                sceneObjects[racer] = new ContO(modelObjects[racers[racer].cn], sceneObjects[racer].x, sceneObjects[racer].y, sceneObjects[racer].z,
                        0);
                sceneObjects[racer].xz = turnXz;
                sceneObjects[racer].xy = turnXy;
                sceneObjects[racer].zy = turnZy;
                racers[racer].newcar = false;
            }
        } while (++racer < GameFacts.numberOfPlayers);

        drawSimulationStep();

        if (ui.starcnt == 0) {
            int car = 0;
            do {
                int other = 0;
                do {
                    if (other != car) {
                        racers[car].colide(sceneObjects[car], racers[other], sceneObjects[other]);
                    }
                } while (++other < GameFacts.numberOfPlayers);
            } while (++car < GameFacts.numberOfPlayers);
            car = 0;
            do
                racers[car].drive(u[car], sceneObjects[car], raceTrackers, raceCheckpoints);
            while (++car < GameFacts.numberOfPlayers);
            car = 0;
            do
                raceRecord.rec(sceneObjects[car], car, racers[car].squash, racers[car].lastcolido,
                        racers[car].cntdest);
            while (++car < GameFacts.numberOfPlayers);
            raceCheckpoints.checkstat(racers, sceneObjects, raceRecord, GameFacts.numberOfPlayers);

            // This starts the AI code for all the cars.
            car = 1;
            do
                u[car].preform(racers[car], sceneObjects[car], raceCheckpoints, raceTrackers, GameFacts.numberOfPlayers);
            while (++car < GameFacts.numberOfPlayers);
        } else {
            if (ui.starcnt == 130) {
                Medium.adv = 1900;
                Medium.zy = 40;
                Medium.vxz = 70;
                pendingWhiteFlash = true;
            }
            if (ui.starcnt != 0)
                ui.starcnt--;
        }
        if (ui.starcnt < 38) {
            if (view == 0) {
                Medium.follow(sceneObjects[ui.spectate], racers[ui.spectate].cxz,
                        u[ui.spectate].lookback);
                renderHud(u[ui.spectate], true);
                initMoto(racers, 2, 5);
            }
            if (view == 1) {
                Medium.around(sceneObjects[ui.spectate], false);
                renderHud(u[ui.spectate], false);
            }
            if (view == 2) {
                Medium.watch(sceneObjects[ui.spectate], racers[0].mxz);
                renderHud(u[ui.spectate], false);
            }
            if (mouses == 1) {
                u[0].enter = true;
                mouses = 0;
            }
            if (ui.starcnt == 36) {
                /*
                 * Tints the countdown portrait with the picture behind it.
                 * The back buffer holds the last presented frame, scene and
                 * HUD, which is what the original grabbed here too. It must
                 * run in this tick: the portrait is drawn from the next one,
                 * and a slow frame can let both ticks run back to back.
                 */
                syncBackBuffer();
                ui.blendude(pictureAtGameSize());
            }
        } else {
            if (GameFacts.numberOfPlayers < 5)
                Medium.around(sceneObjects[0], true);
            else
                Medium.around(sceneObjects[3], true);
            if (u[0].enter || u[0].handb) {
                ui.starcnt = 38;
                u[0].enter = false;
                u[0].handb = false;
            }
            if (ui.starcnt == 38) {
                if (session != null) {
                    session.countdownStarting();
                }
                Medium.startCountdown();
                mouses = 0;
                Medium.vert = false;
                Medium.adv = GameFacts.GAME_WIDTH;
                Medium.vxz = 180;
                raceCheckpoints.checkstat(racers, sceneObjects, raceRecord, GameFacts.numberOfPlayers);
                Medium.follow(sceneObjects[0], racers[0].cxz, 0);
                renderHud(u[0], true);
                pendingWhiteFlash = true;
            }
        }
    }

    /**
     * Draws this frame from the scene kept for the tick, recording it first
     * if a tick or a new run-ahead came since: the cars and the camera
     * {@code alpha} of the way between the two poses that
     * {@link #renderIngameScene} (or, with {@code ahead},
     * {@link #renderAheadScene}) draws between.
     *
     * @return false if this frame has to be drawn the whole way instead
     */
    private boolean drawRetained(boolean ahead, float alpha) {
        if (!RETAINED || !gpuSceneFrame || captureRequested || MOTION_TEST != null) {
            return false;
        }
        if (!retainedValid || retainedAhead != ahead) {
            recordRetained(ahead);
            if (!retainedValid) {
                return false;
            }
        }
        long start = System.nanoTime();
        framePose(alpha, ahead, framePose);
        retainedScene.realize(framePose, retainedRecorder.list().data());
        shownRecorder = retainedRecorder;
        profilePlaceNanos += System.nanoTime() - start;
        profilePlaces++;
        return true;
    }

    /** Records the scene kept for the frames until the next tick or run-ahead, see {@link RetainedScene}. */
    private void recordRetained(boolean ahead) {
        retainedValid = recordKept(ahead, retainedRecorder, retainedScene);
        retainedAhead = ahead;
    }

    /**
     * Records a scene kept for a whole tick into {@code recorder} and
     * {@code scene}, halfway between the poses {@link #renderIngameScene} (or
     * with {@code ahead}, {@link #renderAheadScene}) draws between.
     *
     * @return false if it used drawing the graphics card does not do
     */
    private boolean recordKept(boolean ahead, SceneRecorder recorder, RetainedScene scene) {
        long start = System.nanoTime();
        Medium.cullX = RETAINED_MARGIN_X;
        Medium.cullY = RETAINED_MARGIN_Y;
        Medium.allBands = true;
        retainedRecording = true;
        recordingRecorder = recorder;
        recordingScene = scene;
        try {
            framePose(RETAINED_AT, ahead, scene.recorded);
            scene.begin();
            if (ahead) {
                renderAheadScene(RETAINED_AT);
            } else {
                renderIngameScene(RETAINED_AT);
            }
        } finally {
            Medium.cullX = 0;
            Medium.cullY = 0;
            Medium.allBands = false;
            retainedRecording = false;
            recordingRecorder = null;
            recordingScene = null;
        }
        profileRecordNanos += System.nanoTime() - start;
        profileRecords++;
        return !recorder.unsupported();
    }

    /**
     * Hands the presenter the scene for the frames to come, when a tick or a
     * change of keys made the last one out of date: the run-ahead tick, the
     * kept scene, the two poses its frames move between, the HUD and the
     * white flash. Game thread only.
     *
     * @return false if this tick's frames have to be drawn the whole way
     */
    private boolean handOverKept() {
        boolean ahead = aheadPossible();
        boolean stale = keptStale || keptAhead != ahead;
        if (ahead) {
            long aheadStart = System.nanoTime();
            if (runAheadTick()) {
                profileAheadNanos += System.nanoTime() - aheadStart;
                profileAheads++;
                stale = true;
            }
        } else {
            aheadValid = false;
        }
        if (!stale) {
            return true;
        }
        poseSpan(ahead, spanScratch);
        spanScratch.handedLate = System.nanoTime() - tickStartNanos;
        spanScratch.keysNanos = ahead ? aheadKeysNanos : tickKeysNanos;
        presenter.handOverSpan(spanScratch);
        Presenter.Kept kept = presenter.free();
        if (!recordKept(ahead, kept.recorder, kept.scene)) {
            keptBlocked = true; // until the next tick
            return false;
        }
        GpuDisplay card = gpu;
        if (card != null && card.projecting()) {
            // made ready for the card here, once, so no frame of the presenter's pays for it
            long projectStart = System.nanoTime();
            kept.scene.realize(kept.scene.recorded, kept.recorder.list().data());
            kept.scene.export(kept.projected.points());
            kept.projected.build(kept.recorder.list());
            profileProjectNanos += System.nanoTime() - projectStart;
            profileProjects++;
        } else {
            kept.projected.clear();
        }
        if (hudImage instanceof BufferedImage hud) {
            if (kept.hud == null || kept.hud.getWidth() != hud.getWidth() || kept.hud.getHeight() != hud.getHeight()) {
                kept.hud = new BufferedImage(hud.getWidth(), hud.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }
            int[] from = ((DataBufferInt) hud.getRaster().getDataBuffer()).getData();
            int[] to = ((DataBufferInt) kept.hud.getRaster().getDataBuffer()).getData();
            System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
        } else {
            kept.hud = null;
        }
        kept.flash = pendingWhiteFlash;
        if (pendingWhiteFlash) {
            rd.setColor(new Color(255, 255, 255)); // as the software frame would leave it
            pendingWhiteFlash = false;
        }
        presenter.handOver(kept);
        // what syncBackBuffer redraws in software: this scene, at the moment it was recorded
        shownRecorder = kept.recorder;
        gpuSceneHud = kept.hud;
        gpuSceneHudVersion = kept.version;
        gpuSceneFlash = kept.flash;
        backBufferStale = true;
        keptStale = false;
        keptAhead = ahead;
        return true;
    }

    /** True if the presenter can draw this race's frames on its own thread now. */
    private boolean presenterDraws(boolean scene, boolean unpaced) {
        return scene && presenter != null && !unpaced && !keptBlocked && GPU_SCENE && RETAINED && gpuActive
                && gpuRenderer && !ui.debugmode && !captureRequested && MOTION_TEST == null
                && !presenter.failed();
    }

    /**
     * Where {@link #renderIngameScene} puts the cars and the camera for
     * {@code alpha}, without rounding: between the last two ticks, or with
     * {@code ahead} between the last tick and the run-ahead one, as
     * {@link #renderAheadScene} arranges it. A car that was replaced or
     * jumped stays where it is, as there.
     */
    private void framePose(float alpha, boolean ahead, FramePose out) {
        poseSpan(ahead, spanScratch);
        spanScratch.at(alpha, out);
    }

    /**
     * The two poses {@link #renderIngameScene} draws between (with
     * {@code ahead}, those of {@link #renderAheadScene}), and when the frames
     * between them are due, for any thread to place a kept scene with.
     */
    private void poseSpan(boolean ahead, PoseSpan out) {
        int np = Math.min(GameFacts.numberOfPlayers, Math.min(prevCarX.length, FramePose.SLOTS));
        out.slots = np;
        out.moving = prevFrameValid;
        out.startNanos = tickStartNanos;
        out.lengthNanos = currentTickNanos;
        for (int i = 0; i < np; i++) {
            ContO c = sceneObjects[i];
            if (ahead) {
                Poses b = aheadPoses;
                if (b.ref[i] != c) {
                    out.setCar(i, PoseSpan.AT_START, c.x, c.y, c.z, c.xz, c.xy, c.zy, c.wxz, c.wzy); // replaced ahead
                } else if (teleported(b.x[i] - c.x, b.y[i] - c.y, b.z[i] - c.z)) {
                    out.setCar(i, PoseSpan.AT_END, b.x[i], b.y[i], b.z[i], b.xz[i], b.xy[i], b.zy[i], b.wxz[i], b.wzy[i]);
                } else {
                    out.setCarStart(i, c.x, c.y, c.z, c.xz, c.xy, c.zy, c.wxz, c.wzy);
                    out.setCar(i, PoseSpan.MOVING, b.x[i], b.y[i], b.z[i], b.xz[i], b.xy[i], b.zy[i], b.wxz[i],
                            b.wzy[i]);
                }
            } else if (c != prevCarRef[i] || teleported(c.x - prevCarX[i], c.y - prevCarY[i], c.z - prevCarZ[i])) {
                out.setCar(i, PoseSpan.AT_END, c.x, c.y, c.z, c.xz, c.xy, c.zy, c.wxz, c.wzy);
            } else {
                out.setCarStart(i, prevCarX[i], prevCarY[i], prevCarZ[i], prevCarXZ[i], prevCarXY[i], prevCarZY[i],
                        prevCarWXZ[i], prevCarWZY[i]);
                out.setCar(i, PoseSpan.MOVING, c.x, c.y, c.z, c.xz, c.xy, c.zy, c.wxz, c.wzy);
            }
        }
        int[] from = out.cameraStart;
        int[] to = out.cameraEnd;
        from[0] = ahead ? Medium.x : prevCamX;
        from[1] = ahead ? Medium.y : prevCamY;
        from[2] = ahead ? Medium.z : prevCamZ;
        from[3] = ahead ? Medium.xz : prevCamXZ;
        from[4] = ahead ? Medium.zy : prevCamZY;
        to[0] = ahead ? aheadPoses.camX : Medium.x;
        to[1] = ahead ? aheadPoses.camY : Medium.y;
        to[2] = ahead ? aheadPoses.camZ : Medium.z;
        to[3] = ahead ? aheadPoses.camXZ : Medium.xz;
        to[4] = ahead ? aheadPoses.camZY : Medium.zy;
        out.cameraMode = teleported(to[0] - from[0], to[1] - from[1], to[2] - from[2]) ? PoseSpan.AT_END
                : PoseSpan.MOVING;
        // placed as frames are drawn: in the picture as it is shown (see Medium.showView)
        int shiftX = -GameFacts.marginX();
        int shiftY = -GameFacts.marginY();
        from[0] += shiftX;
        to[0] += shiftX;
        from[1] += shiftY;
        to[1] += shiftY;
    }

    /** How often the game thread looks at the keys while the presenter draws, for a new run-ahead tick. */
    private static final long KEY_CHECK_NANOS = 2_000_000L;

    /**
     * The thread that owns the graphics card and puts every frame on screen
     * through it.
     * <p>
     * Frames the game thread draws (menus, and races whenever a race frame
     * has to be drawn the whole way) it shows while the game thread waits,
     * as before. Races it draws on its own: the game thread hands it a
     * {@link Kept} scene after each tick and each new run-ahead tick, and it
     * places that scene for the moment each frame is due and shows it, at
     * the display's rate. So ticks, the run-ahead tick and recording the
     * scene, all on the game thread, never hold a frame back, and a frame
     * waiting for the display never holds a tick back. While the next tick
     * is being worked out, frames carry the motion on a little past the end
     * of the last one (see {@link PoseSpan#MAX_CARRY_ON}).
     */
    private final class Presenter implements Runnable {

        /** A scene kept for a tick, with what its frames need; owned by one thread at a time. */
        final class Kept {
            final SceneRecorder recorder = new SceneRecorder();
            final RetainedScene scene = new RetainedScene();
            /** The scene made ready for the card to place, when the card does. */
            final nfm.lit.gpu.ProjectedScene projected = new nfm.lit.gpu.ProjectedScene();
            BufferedImage hud;
            boolean flash;
            long version;
        }

        /* three, so one is drawn, one waits and the game thread fills the third */
        private final Kept[] kept = {new Kept(), new Kept(), new Kept()};
        private final Object lock = new Object();
        /* guarded by lock */
        private Kept drawing;
        private Kept latest;
        private long versions;
        private final PoseSpan handedSpan = new PoseSpan();
        private long spanVersion;
        private boolean job;
        private boolean jobCapture;
        private boolean jobDone;
        private boolean jobShown;
        /** The game thread wants races drawn here. */
        private volatile boolean races;
        /** A race frame could not be shown: the game thread draws until a frame of its own is shown again. */
        private volatile boolean failed;

        private final Thread thread = new Thread(this, "nfm-presenter");
        private final PreciseSleep sleep = new PreciseSleep();
        private final FramePose pose = new FramePose();
        private final FramePose carried = new FramePose();
        /** -Dnfm.projectiontest=N: compares N frames placed on the card with the same frames placed here. */
        private int projectionTests = Integer.getInteger("nfm.projectiontest", 0);
        private int projectionTestFrame;
        /* the span frames are placed with, and the one before it for the profile */
        private final PoseSpan span = new PoseSpan();
        private final PoseSpan previousSpan = new PoseSpan();
        private long placedSpanVersion = -1L;
        private boolean spanChanged;
        private long flashedVersion = -1L;
        private long nextFrameNanos;
        private long lastShownNanos;

        /* the profile, presenter thread only */
        private int statFrames;
        private long statPlaceNanos;
        private long statShowNanos;
        private int statCarried;
        private final double[] statCorrections = new double[1024];
        private final double[] statHandedLate = new double[1024];
        private int statCorrectionCount;
        private final long[] statGaps = new long[4096];
        private int statGapCount;
        /* key change to the first frame shown that has it */
        private final long[] statKeys = new long[256];
        private int statKeyCount;
        private long keysShown = -1L;
        private long statSince = System.nanoTime();

        Presenter() {
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 2);
        }

        void start() {
            thread.start();
        }

        boolean failed() {
            return failed;
        }

        boolean drawingRaces() {
            return races && !failed;
        }

        /** Game thread: a kept scene neither drawn nor waiting, to record the next one into. */
        Kept free() {
            synchronized (lock) {
                for (Kept k : kept) {
                    if (k != drawing && k != latest) {
                        return k;
                    }
                }
            }
            throw new IllegalStateException("no kept scene free");
        }

        /**
         * Game thread: the cars and the camera move as {@code s} from now on.
         * Handed over as soon as a tick has run, before its scene is
         * recorded, so frames never carry the last tick's motion on for
         * longer than the tick took: until the new scene comes, they place
         * the last one, which reaches well past the picture's edges, for the
         * new poses.
         */
        void handOverSpan(PoseSpan s) {
            synchronized (lock) {
                handedSpan.copyFrom(s);
                spanVersion++;
            }
        }

        /** Game thread: {@code k} is the scene for the frames from now on. */
        void handOver(Kept k) {
            synchronized (lock) {
                k.version = ++versions;
                latest = k;
                lock.notifyAll();
            }
        }

        /** Game thread: whether races are drawn here from the kept scenes. */
        void drawRaces(boolean on) {
            if (races == on) {
                return;
            }
            synchronized (lock) {
                races = on;
                if (!on) {
                    latest = null; // a later race starts from a scene handed over then
                }
                lock.notifyAll();
            }
        }

        /**
         * Game thread: shows the frame it drew, as {@link #presentOnGpu},
         * here while the game thread waits.
         *
         * @return false if the frame has to be drawn in software
         */
        boolean show(boolean capture) {
            synchronized (lock) {
                jobCapture = capture;
                jobDone = false;
                job = true;
                lock.notifyAll();
                boolean interrupted = false;
                while (!jobDone) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ex) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (jobShown) {
                    failed = false;
                }
                return jobShown;
            }
        }

        @Override
        public void run() {
            if (!runsHidden()) {
                WindowsScheduling.joinGamesTask(false);
            }
            while (!exwist) {
                boolean doJob;
                Kept frame = null;
                synchronized (lock) {
                    if (!job && !(races && latest != null && !failed)) {
                        try {
                            lock.wait(20L);
                        } catch (InterruptedException ex) {
                            return;
                        }
                    }
                    doJob = job;
                    if (!doJob && races && !failed && latest != null) {
                        drawing = latest;
                        frame = drawing;
                        if (placedSpanVersion != spanVersion) {
                            previousSpan.copyFrom(span);
                            span.copyFrom(handedSpan);
                            spanChanged = placedSpanVersion != -1L;
                            placedSpanVersion = spanVersion;
                        }
                    }
                }
                if (doJob) {
                    boolean shown;
                    try {
                        shown = presentOnGpu(jobCapture);
                    } catch (RuntimeException | LinkageError ex) {
                        HLogger.warn("The graphics card stopped drawing, drawing in software: " + ex);
                        shown = false;
                    }
                    synchronized (lock) {
                        jobShown = shown;
                        job = false;
                        jobDone = true;
                        lock.notifyAll();
                    }
                } else if (frame != null) {
                    drawFrame(frame);
                } else {
                    releaseIfWindowGone();
                }
            }
        }

        /** Lets go of the card when the window it draws in goes or the renderer is switched off, as presentOnGpu does. */
        private void releaseIfWindowGone() {
            synchronized (presentLock) {
                if (gpu != null && (!gpuRenderer || gpuWindowGeneration != windowGeneration)) {
                    boolean stillSameWindow = gpuWindowGeneration == windowGeneration;
                    closeGpu();
                    if (stillSameWindow) {
                        RunApp.recreateWindow(); // a window Direct3D has drawn in cannot go back to GDI
                    }
                }
            }
        }

        /** Places the kept scene for now, shows it and waits for the next frame. */
        private void drawFrame(Kept k) {
            long start = System.nanoTime();
            float alpha = span.alphaAt(start, true);
            if (alpha > 1F) {
                statCarried++;
            }
            span.at(alpha, pose);
            if (PROFILE_LOOP && spanChanged) {
                // how far the motion carried on from the last tick was from where the new one has things
                previousSpan.at(previousSpan.alphaAt(start, true), carried);
                if (statCorrectionCount < statCorrections.length) {
                    statCorrections[statCorrectionCount] = difference(carried, pose);
                    statHandedLate[statCorrectionCount++] = span.handedLate / 1e6D;
                }
            }
            spanChanged = false;
            GpuDisplay card = gpu;
            boolean onCard = card != null && card.projecting() && k.projected.built();
            if (!onCard || projectionTests > 0) {
                k.scene.realize(pose, k.recorder.list().data());
            }
            if (onCard) {
                // the card places every point: only what the frame's moment changes goes to it
                k.scene.frameConstants(pose, k.projected.points());
                if (projectionTests > 0 && ++projectionTestFrame % 30 == 0) {
                    projectionTests--;
                    testProjection(k);
                }
            }
            boolean flash = k.flash && k.version != flashedVersion;
            if (flash) {
                flashedVersion = k.version;
            }
            long placed = System.nanoTime();
            boolean shown;
            try {
                shown = showOnGpu(false, k.recorder.list(), onCard ? k.projected : null, k.hud, k.version, k.version,
                        flash, true);
            } catch (RuntimeException | LinkageError ex) {
                HLogger.warn("The graphics card stopped drawing a race: " + ex);
                shown = false;
            }
            long end = System.nanoTime();
            if (!shown) {
                failed = true; // the game thread takes over and finds out why
                return;
            }
            if (span.keysNanos != keysShown) {
                long took = end - span.keysNanos;
                // a change more than a second old is from before the race (or a pause), not a frame late
                if (keysShown != -1L && took < 1_000_000_000L && statKeyCount < statKeys.length) {
                    statKeys[statKeyCount++] = took;
                }
                keysShown = span.keysNanos;
            }
            statFrames++;
            statPlaceNanos += placed - start;
            statShowNanos += end - placed;
            if (lastShownNanos != 0L && statGapCount < statGaps.length) {
                statGaps[statGapCount++] = end - lastShownNanos;
            }
            lastShownNanos = end;
            if (PROFILE_LOOP && end - statSince >= 2_000_000_000L) {
                report(end);
            }
            pace(end);
        }

        /**
         * Draws this frame twice, placed here and on the card, and logs how
         * many pixels differ: the proof that placing on the card shows what
         * placing here does. The pictures of the first frame that differs
         * most go to the autotest folder.
         */
        private void testProjection(Kept k) {
            nfm.lit.gpu.SceneList list = k.recorder.list();
            if (!showOnGpu(true, list, null, k.hud, -1L, -1L, false, true)) {
                return;
            }
            BufferedImage here = capturedGpuScene;
            if (!showOnGpu(true, list, k.projected, k.hud, k.version, -1L, false, true)) {
                return;
            }
            BufferedImage onCard = capturedGpuScene;
            if (here == null || onCard == null || here.getWidth() != onCard.getWidth()
                    || here.getHeight() != onCard.getHeight()) {
                HLogger.warn("Projection test: no pictures to compare");
                return;
            }
            int w = here.getWidth();
            int h = here.getHeight();
            int[] a = here.getRGB(0, 0, w, h, null, 0, w);
            int[] b = onCard.getRGB(0, 0, w, h, null, 0, w);
            int differ = 0;
            int worst = 0;
            for (int i = 0; i < a.length; i++) {
                int x = a[i];
                int y = b[i];
                if (x != y) {
                    differ++;
                    int d = Math.max(Math.abs((x >> 16 & 0xff) - (y >> 16 & 0xff)), Math.max(
                            Math.abs((x >> 8 & 0xff) - (y >> 8 & 0xff)), Math.abs((x & 0xff) - (y & 0xff))));
                    worst = Math.max(worst, d);
                }
            }
            double share = 100D * differ / a.length;
            HLogger.info(String.format("Projection test: %d of %d pixels differ (%.4f%%), at most by %d",
                    differ, a.length, share, worst));
            if (share > projectionWorst) {
                projectionWorst = share;
                try {
                    java.io.File folder = new java.io.File(System.getProperty("nfm.autotest", "autotest"));
                    folder.mkdirs();
                    javax.imageio.ImageIO.write(here, "png", new java.io.File(folder, "projection-here.png"));
                    javax.imageio.ImageIO.write(onCard, "png", new java.io.File(folder, "projection-card.png"));
                } catch (java.io.IOException ex) {
                    HLogger.warn("Projection test: " + ex);
                }
            }
        }

        private double projectionWorst;

        /** Waits until the next frame is due: the display's refresh, the frame limit or, with none, not at all. */
        private void pace(long now) {
            long interval = frameIntervalNanos(now);
            if (interval <= 0L) {
                Thread.yield();
                return;
            }
            if (fpsLimit == FPS_MATCH_DISPLAY && !batteryHolds) {
                // showing waits for the display; this only keeps a window that never waits from spinning
                nextFrameNanos = now + interval / 4L;
            } else {
                nextFrameNanos += interval;
                if (now - nextFrameNanos > interval * 4L || nextFrameNanos - now > interval) {
                    nextFrameNanos = now + interval; // fell far behind, or the limit was raised
                }
            }
            try {
                sleep.sleepUntil(nextFrameNanos);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        /** The largest distance between the camera or a car in {@code a} and in {@code b}, in world units. */
        private double difference(FramePose a, FramePose b) {
            double d = Math.max(Math.abs(a.camX - b.camX), Math.max(Math.abs(a.camY - b.camY), Math.abs(a.camZ - b.camZ)));
            for (int i = 0; i < Math.min(a.slots, b.slots); i++) {
                d = Math.max(d, Math.max(Math.abs(a.x[i] - b.x[i]), Math.max(Math.abs(a.y[i] - b.y[i]),
                        Math.abs(a.z[i] - b.z[i]))));
            }
            return d;
        }

        private void report(long now) {
            double seconds = (now - statSince) / 1e9D;
            String pace = "";
            if (statGapCount > 0) {
                long[] gaps = Arrays.copyOf(statGaps, statGapCount);
                Arrays.sort(gaps);
                long median = gaps[gaps.length / 2];
                int late = 0;
                for (long gap : gaps) {
                    if (gap > median * 3 / 2 + 2_000_000L) {
                        late++;
                    }
                }
                pace = String.format("; frame gaps: median %.1f ms, 99%% %.1f ms, longest %.1f ms; %d of %d frames came late",
                        median / 1e6D, gaps[Math.min(gaps.length - 1, gaps.length * 99 / 100)] / 1e6D,
                        gaps[gaps.length - 1] / 1e6D, late, gaps.length);
            }
            String keys = "";
            if (statKeyCount > 0) {
                long[] k = Arrays.copyOf(statKeys, statKeyCount);
                Arrays.sort(k);
                keys = String.format("; key to screen: median %.1f ms, longest %.1f ms over %d changes",
                        k[k.length / 2] / 1e6D, k[k.length - 1] / 1e6D, k.length);
            }
            Kept shown = drawing;
            String corrections = "none";
            if (statCorrectionCount > 0) {
                double[] c = Arrays.copyOf(statCorrections, statCorrectionCount);
                Arrays.sort(c);
                double[] h = Arrays.copyOf(statHandedLate, statCorrectionCount);
                Arrays.sort(h);
                corrections = String.format("median %.1f, 90%% %.1f, largest %.1f units; the motion came %.1f ms after"
                        + " the tick was due, 90%% within %.1f, at most %.1f", c[c.length / 2], c[c.length * 9 / 10],
                        c[c.length - 1], h[h.length / 2], h[h.length * 9 / 10], h[h.length - 1]);
            }
            System.out.printf("[draw] own thread: %.1f fps (placing %.2f ms, showing %.2f ms; %d carried on past"
                    + " the tick; corrections at new ticks: %s)%s%s; last scene %s, %d points, %d polygons kept;"
                    + " card %.2f ms a frame, races drawn at %d%%, msaa %dx%n",
                    statFrames / seconds, statFrames == 0 ? 0D : statPlaceNanos / 1e6D / statFrames,
                    statFrames == 0 ? 0D : statShowNanos / 1e6D / statFrames, statCarried, corrections, pace, keys,
                    gpu != null ? gpu.sceneStatistics() : "", shown == null ? 0 : shown.scene.points(),
                    shown == null ? 0 : shown.scene.polygons(), gpuFrameNanos / 1e6D,
                    Math.round(sceneScaleInUse * 100F), multisampleInUse);
            statCorrectionCount = 0;
            statKeyCount = 0;
            statFrames = 0;
            statPlaceNanos = 0L;
            statShowNanos = 0L;
            statCarried = 0;
            statGapCount = 0;
            statSince = now;
        }
    }

    /** Run-ahead applies while the player is racing: not in the intro, countdown, replays or menus. */
    private boolean aheadPossible() {
        return runAhead && prevFrameValid && ui.fase == Phase.INGAME && ui.starcnt == 0;
    }

    /** The driving keys held right now and the chosen camera, as one number; call under {@link #inputLock}. */
    private int heldKeys() {
        return (liveUp ? 1 : 0) | (liveDown ? 2 : 0) | (liveLeft ? 4 : 0) | (liveRight ? 8 : 0)
                | (liveHandb ? 16 : 0) | ((liveLookback + 1) << 5) | (liveView << 7);
    }

    /**
     * Plays the next race tick ahead of time with the keys held now, keeps
     * where it leaves the cars and the camera, and puts the whole race back.
     * The real tick then reads the same keys and, the race being
     * deterministic, lands exactly there; only a key changed in between makes
     * the next frame correct itself. Nothing of it is heard, recorded or
     * drawn: the HUD draws into a copy of its layer that is clipped to nothing. The input lock is held throughout, so a key cannot
     * change the controls while they are being put back.
     *
     * @return false if the last run-ahead still holds: no tick since and the same keys
     */
    private boolean runAheadTick() {
        int np = Math.min(GameFacts.numberOfPlayers, prevCarX.length);
        synchronized (inputLock) {
            int keys = heldKeys();
            if (aheadValid && aheadTick == raceTicks && aheadKeys == keys) {
                return false;
            }
            int mousesBefore = mouses;
            int viewBefore = view;
            int shakaBefore = shaka;
            boolean flashBefore = pendingWhiteFlash;
            Graphics2D hud = hudG;
            FontMetrics metrics = FontHandler.fMetrics;
            // the HUD measures its text, so it needs real font metrics: a copy of its layer clipped to nothing
            Graphics2D blind = (Graphics2D) hud.create();
            blind.setClip(0, 0, 0, 0);
            aheadSnapshot.capture(new RaceState.View(world, racers, u, sceneObjects, raceCheckpoints, raceRecord, ui,
                    raceTrackers, GameFacts.numberOfPlayers), nob);
            SoundManager.setDiscarding(true);
            hudG = blind;
            try {
                applyHeldKeys();
                view = liveView;
                advanceRace();
                aheadPoses.takeLive(np);
            } finally {
                aheadSnapshot.restore();
                SoundManager.setDiscarding(false);
                hudG = hud;
                blind.dispose();
                FontHandler.fMetrics = metrics;
                mouses = mousesBefore;
                view = viewBefore;
                shaka = shakaBefore;
                pendingWhiteFlash = flashBefore;
            }
            aheadTick = raceTicks;
            aheadKeys = keys;
            aheadKeysNanos = keysChangedNanos;
            aheadValid = true;
            return true;
        }
    }

    /**
     * Draws the race between the last real tick ({@code alpha} 0) and the
     * run-ahead tick (1), the same way {@link #renderIngameScene} draws
     * between the two ticks before. A car the run-ahead tick replaced, after
     * a wreck, stays where it is.
     */
    private void renderAheadScene(float alpha) {
        int np = Math.min(GameFacts.numberOfPlayers, prevCarX.length);
        nowPoses.takeLive(np);
        prevPoses.takePrev(np);
        nowPoses.putPrev(np);
        aheadPoses.putLive(np);
        try {
            renderIngameScene(alpha);
        } finally {
            nowPoses.putLive(np);
            prevPoses.putPrev(np);
        }
    }

    /** The cars' transforms and the camera, as the presentation pass interpolates them. */
    private final class Poses {
        private final int[] x = new int[51];
        private final int[] y = new int[51];
        private final int[] z = new int[51];
        private final int[] xz = new int[51];
        private final int[] xy = new int[51];
        private final int[] zy = new int[51];
        private final int[] wxz = new int[51];
        private final int[] wzy = new int[51];
        private final ContO[] ref = new ContO[51];
        private int camX;
        private int camY;
        private int camZ;
        private int camXZ;
        private int camZY;

        void takeLive(int np) {
            for (int i = 0; i < np; i++) {
                ContO c = sceneObjects[i];
                x[i] = c.x;
                y[i] = c.y;
                z[i] = c.z;
                xz[i] = c.xz;
                xy[i] = c.xy;
                zy[i] = c.zy;
                wxz[i] = c.wxz;
                wzy[i] = c.wzy;
                ref[i] = c;
            }
            camX = Medium.x;
            camY = Medium.y;
            camZ = Medium.z;
            camXZ = Medium.xz;
            camZY = Medium.zy;
        }

        /** Only onto the same car objects these were taken from. */
        void putLive(int np) {
            for (int i = 0; i < np; i++) {
                ContO c = sceneObjects[i];
                if (c != ref[i]) {
                    continue;
                }
                c.x = x[i];
                c.y = y[i];
                c.z = z[i];
                c.xz = xz[i];
                c.xy = xy[i];
                c.zy = zy[i];
                c.wxz = wxz[i];
                c.wzy = wzy[i];
            }
            Medium.x = camX;
            Medium.y = camY;
            Medium.z = camZ;
            Medium.xz = camXZ;
            Medium.zy = camZY;
        }

        /** True if the cars and the camera are exactly where these were taken. */
        boolean matchLive(int np) {
            for (int i = 0; i < np; i++) {
                ContO c = sceneObjects[i];
                if (c != ref[i] || c.x != x[i] || c.y != y[i] || c.z != z[i] || c.xz != xz[i] || c.xy != xy[i]
                        || c.zy != zy[i] || c.wxz != wxz[i] || c.wzy != wzy[i]) {
                    return false;
                }
            }
            return Medium.x == camX && Medium.y == camY && Medium.z == camZ && Medium.xz == camXZ
                    && Medium.zy == camZY;
        }

        void takePrev(int np) {
            for (int i = 0; i < np; i++) {
                x[i] = prevCarX[i];
                y[i] = prevCarY[i];
                z[i] = prevCarZ[i];
                xz[i] = prevCarXZ[i];
                xy[i] = prevCarXY[i];
                zy[i] = prevCarZY[i];
                wxz[i] = prevCarWXZ[i];
                wzy[i] = prevCarWZY[i];
                ref[i] = prevCarRef[i];
            }
            camX = prevCamX;
            camY = prevCamY;
            camZ = prevCamZ;
            camXZ = prevCamXZ;
            camZY = prevCamZY;
        }

        void putPrev(int np) {
            for (int i = 0; i < np; i++) {
                prevCarX[i] = x[i];
                prevCarY[i] = y[i];
                prevCarZ[i] = z[i];
                prevCarXZ[i] = xz[i];
                prevCarXY[i] = xy[i];
                prevCarZY[i] = zy[i];
                prevCarWXZ[i] = wxz[i];
                prevCarWZY[i] = wzy[i];
                prevCarRef[i] = ref[i];
            }
            prevCamX = camX;
            prevCamY = camY;
            prevCamZ = camZ;
            prevCamXZ = camXZ;
            prevCamZY = camZY;
        }
    }

    /**
     * Draws the HUD into its own layer. Called from the simulation tick so all of
     * the HUD's internal counters, blink timers and pause handling keep running at
     * the legacy tick rate instead of the display rate.
     */
    private void renderHud(Control control, boolean chaseView) {
        Graphics2D previous = ui.getTarget();
        hudG.setComposite(AlphaComposite.Clear);
        hudG.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        hudG.setComposite(AlphaComposite.SrcOver);
        ui.setTarget(hudG);
        ui.stat(racers, raceCheckpoints, control, sceneObjects, chaseView);
        ui.setTarget(previous);
    }

    /**
     * Phases whose 3D scene is drawn by the presentation pass, interpolated, with
     * the HUD layer composited on top.
     */
    private static boolean isScenePhase(Phase phase) {
        return phase == Phase.INGAME || phase == Phase.INSTANTREPLAY || phase == Phase.GAMEHIGHLIGHT
                || phase == Phase.SETTINGS && raceUnderSettings;
    }

    /** The settings screen is open over a paused race, which the frames draw behind it (game thread). */
    private static boolean raceUnderSettings;

    private static long tickNanosFor(Phase phase) {
        return isScenePhase(phase) || phase == Phase.DRAWENVIRONMENT ? SCENE_TICK_NANOS : MENU_TICK_NANOS;
    }

    /**
     * Sends everything a replay or highlight tick draws (captions, black and
     * white flashes) to the cleared HUD layer instead of the back buffer, so the
     * presentation pass can draw the scene underneath at the display rate.
     */
    private void beginOverlayTick() {
        hudG.setComposite(AlphaComposite.Clear);
        hudG.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        hudG.setComposite(AlphaComposite.SrcOver);
        sceneGraphics = rd;
        rd = hudG;
        ui.setTarget(hudG);
    }

    private void endOverlayTick() {
        rd = sceneGraphics;
        ui.setTarget(rd);
        sceneGraphics = null;
    }

    /**
     * Only the cars and the electrified repair hoops carry state that drawing
     * advances; the rest of the track is drawn without side effects.
     */
    private boolean hasDrawAnimation(int index) {
        return index < GameFacts.numberOfPlayers || sceneObjects[index].elec;
    }

    /**
     * Besides the animations, drawing measures every object's distance from
     * the camera, which the race reads and the next draw step sorts by.
     */
    private void saveSceneDrawState() {
        Medium.saveDrawState();
        if (savedDist.length < nob) {
            savedDist = new int[nob];
        }
        for (int i = 0; i < nob; i++) {
            savedDist[i] = sceneObjects[i].dist;
            if (hasDrawAnimation(i)) {
                sceneObjects[i].saveDrawState();
            }
        }
    }

    private void restoreSceneDrawState() {
        Medium.restoreDrawState();
        for (int i = 0; i < nob; i++) {
            sceneObjects[i].dist = savedDist[i];
            if (hasDrawAnimation(i)) {
                sceneObjects[i].restoreDrawState();
            }
        }
    }

    /**
     * Puts the back buffer on screen right away, from the game thread.
     * <p>
     * repaint() only asks the event thread to paint at some later point and
     * merges requests that arrive faster than it paints, so frames were dropped
     * at random and could be shown while the next one was half drawn.
     */
    private void presentFrame(boolean capture) {
        if (presenter != null ? presenter.show(capture) : presentOnGpu(capture)) {
            return;
        }
        syncBackBuffer(); // a frame meant for the card goes on screen in software after all
        Graphics graphics = getGraphics();
        if (graphics != null) {
            try {
                paintPicture((Graphics2D) graphics);
            } finally {
                graphics.dispose();
            }
        }
        Toolkit.getDefaultToolkit().sync();
        PerfOverlay.frameShown(System.nanoTime(), 0L);
        PerfOverlay.drawingOnCard(false);
    }

    /**
     * Ends a frame whose scene was recorded for the graphics card: keeps the
     * HUD as it is now and the white flash with it, and leaves the back
     * buffer's colour as the software path would have.
     */
    private void finishGpuScene() {
        if (hudImage instanceof BufferedImage hud) {
            boolean resized = ownSceneHud == null || ownSceneHud.getWidth() != hud.getWidth()
                    || ownSceneHud.getHeight() != hud.getHeight();
            if (resized) {
                ownSceneHud = new BufferedImage(hud.getWidth(), hud.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }
            if (resized || ownHudTick != ticksRun) {
                // the HUD is only drawn by ticks: frames between two ticks share one copy and one upload
                int[] from = ((DataBufferInt) hud.getRaster().getDataBuffer()).getData();
                int[] to = ((DataBufferInt) ownSceneHud.getRaster().getDataBuffer()).getData();
                System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
                ownHudTick = ticksRun;
                ownHudVersion++;
            }
            gpuSceneHud = ownSceneHud; // not a kept frame's, which the presenter may be drawing
            gpuSceneHudVersion = ownHudVersion;
        }
        gpuSceneFlash = pendingWhiteFlash;
        if (pendingWhiteFlash) {
            rd.setColor(new Color(255, 255, 255));
            pendingWhiteFlash = false;
        }
        backBufferStale = true;
    }

    /**
     * Draws the last frame into the back buffer when the graphics card drew
     * it, replaying the recorded scene, so whatever reads or draws on the
     * back buffer next finds exactly what the software renderer left there.
     */
    private void syncBackBuffer() {
        if (!backBufferStale) {
            return;
        }
        backBufferStale = false;
        Color color = rd.getColor();
        shownRecorder.replay(rd);
        if (gpuSceneHud != null) {
            rd.drawImage(gpuSceneHud, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
        }
        if (gpuSceneFlash) {
            rd.setColor(new Color(255, 255, 255));
            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        }
        rd.setColor(color);
    }

    /**
     * Shows the frame the game thread drew through the graphics card when
     * that renderer is on and works, starting it on first use and stopping it
     * when the window goes away or the setting changes. On the thread that
     * owns the card: the presenter's while the game thread waits, or the game
     * thread itself without one.
     *
     * @return false if the frame still has to be drawn in software
     */
    private boolean presentOnGpu(boolean capture) {
        return showOnGpu(capture, gpuSceneFrame ? shownRecorder.list() : null, null, gpuSceneHud, -1L,
                gpuSceneHudVersion,
                gpuSceneFlash, false);
    }

    /**
     * Shows a frame through the graphics card: the 3D {@code scene} with the
     * {@code hud} on top (with the same {@code version} of a kept scene, the
     * HUD is not uploaded again and its polygons are cut as before; -1 for
     * a frame drawn anew) and the white {@code flash}, or with no scene the
     * game thread's picture. A frame the {@code presenter} draws on its own
     * never falls back to the picture.
     */
    private boolean showOnGpu(boolean capture, nfm.lit.gpu.SceneList scene, nfm.lit.gpu.ProjectedScene projected,
            BufferedImage hud, long version,
            long hudVersion, boolean flash, boolean presenterFrame) {
        if (gpu == null && presenterFrame) {
            return false;
        }
        if (gpu == null) {
            if (!gpuRenderer || gpuUnavailable || !IS_WINDOWS || !isDisplayable()) {
                return false;
            }
            openGpu();
            if (gpu == null) {
                return false;
            }
        }
        synchronized (presentLock) {
            if (!gpuRenderer || gpuWindowGeneration != windowGeneration) {
                boolean stillSameWindow = gpuWindowGeneration == windowGeneration;
                closeGpu();
                if (stillSameWindow) {
                    RunApp.recreateWindow(); // a window Direct3D has drawn in cannot go back to GDI
                }
                return false;
            }
            int width = getWidth();
            int height = getHeight();
            Rectangle place = placePicture(width, height);
            if (place == null) {
                return true;
            }
            boolean smooth = place.width != offImage.getWidth(null) || place.height != offImage.getHeight(null);
            gpu.setVsync(vsyncInUse());
            gpu.setHdr(hdrMode);
            gpu.setPaperWhite(paperWhite);
            gpu.setPeak(peakNits);
            hdrInUse = gpu.hdrStatus();
            Dimension size = new Dimension(width, height);
            boolean shown;
            try {
                BufferedImage picture = (BufferedImage) offImage;
                if (scene != null) {
                    picture = null; // show the scene drawn on the card
                    gpu.setMultisample(multisample);
                    float scale = dynamicResolution == 0 ? 1F : resolution.scale();
                    Dimension target = sceneTargetSize(place, gpu.maxTextureSize());
                    boolean drawn = gpu.drawScene(scene, projected, hud, version, hudVersion, flash, capture, target,
                            scale);
                    multisampleInUse = gpu.multisample();
                    float across = supersample > 1 ? target.width / (float) Math.max(GameFacts.screenWidth, place.width) : 1F;
                    supersampleInUse = across;
                    supersampleWidth = target.width;
                    supersampleHeight = target.height;
                    sceneScaleInUse = gpu.sceneScale() * across;
                    if (!drawn) {
                        if (presenterFrame) {
                            return false;
                        }
                        syncBackBuffer();
                        picture = (BufferedImage) offImage;
                    }
                }
                if (capture) {
                    capturedGpu = gpu.presentAndCapture(picture, size, place, smooth);
                    capturedGpuScene = picture == null ? gpu.takeSceneCapture() : null;
                    capturedGlow = picture == null ? gpu.takeGlowCapture() : null;
                    shown = true;
                } else {
                    shown = gpu.present(picture, size, place, smooth);
                }
                if (shown) {
                    long gpuNanos = gpu.gpuFrameNanos();
                    gpuFrameNanos = gpuNanos;
                    PerfOverlay.frameShown(System.nanoTime(), gpuNanos);
                    PerfOverlay.drawingOnCard(true);
                    if (PerfOverlay.wantsVideoMemory()) {
                        long[] memory = gpu.videoMemory();
                        PerfOverlay.videoMemory(memory[0], memory[1], memory[2], memory[3]);
                    }
                    if (picture == null) {
                        resolution.update(gpuNanos, frameBudgetNanos, lowestSceneScale(place, supersampleInUse),
                                System.nanoTime());
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                HLogger.warn("The graphics card stopped drawing, drawing in software: " + ex);
                shown = false;
            }
            if (!shown) {
                gpuUnavailable = true;
                closeGpu();
                RunApp.recreateWindow();
            }
            return shown;
        }
    }

    /**
     * The size the card draws the scene at: the size the picture has in the
     * window, so a large window shows it sharp rather than scaled up, or the
     * picture's own size when the window is smaller (lines thinner than a
     * pixel would break up) or {@code -Dnfm.gpuscene=picture} asks for it.
     * Dynamic resolution draws in part of it when the card cannot keep up,
     * but never below the picture's own size, the size the game always drew
     * at. Supersampling doubles it, as far as the card's largest texture
     * ({@code maxSide}) allows, keeping its shape.
     */
    private static Dimension sceneTargetSize(Rectangle place, int maxSide) {
        int w = place.width;
        int h = place.height;
        if (!GPU_SCENE_NATIVE || w < GameFacts.screenWidth || h < GameFacts.screenHeight) {
            w = GameFacts.screenWidth;
            h = GameFacts.screenHeight;
        }
        if (supersample > 1) {
            float factor = Math.min(supersampleAcross(), Math.min(maxSide / (float) w, maxSide / (float) h));
            if (factor > 1F) {
                w = Math.round(w * factor);
                h = Math.round(h * factor);
            }
        }
        return new Dimension(w, h);
    }

    /**
     * The lowest scale dynamic resolution may draw a race at in a window
     * showing it at {@code place}: the setting, but not below the picture's
     * own size; 1 when it is off or the window is no larger than the picture.
     * With supersampling ({@code across} times the shown size) it is a share
     * of the larger target, so the setting still counts from the shown size:
     * when the card cannot keep up, supersampling gives way first.
     */
    private static float lowestSceneScale(Rectangle place, float across) {
        int lowest = dynamicResolution;
        if (lowest == 0 || !GPU_SCENE_NATIVE) {
            return 1F;
        }
        float picture = Math.max(GameFacts.screenWidth / (float) place.width,
                GameFacts.screenHeight / (float) place.height);
        return Math.min(1F, Math.max(lowest / 100F, picture) / across);
    }

    /** Starts the GPU display on the window, or marks it unavailable for this session. */
    private void openGpu() {
        if (!(offImage instanceof BufferedImage)) {
            gpuUnavailable = true;
            return;
        }
        int generation;
        synchronized (presentLock) {
            if (!windowShown) {
                return;
            }
            generation = windowGeneration;
        }
        long window = windowHandle(); // outside the lock: AWT takes its own locks here
        synchronized (presentLock) {
            if (!windowShown || generation != windowGeneration) {
                return; // the window changed meanwhile; try again next frame
            }
            gpu = window == 0L ? null
                    : GpuDisplay.open(window, new Dimension(getWidth(), getHeight()),
                            GameFacts.screenWidth, GameFacts.screenHeight);
            if (gpu == null) {
                gpuUnavailable = true;
                if (window != 0L) {
                    RunApp.recreateWindow(); // in case Direct3D got as far as the window before failing
                }
                return;
            }
            gpuWindowGeneration = generation;
            gpuActive = true;
        }
    }

    /** Stops the GPU display; the caller holds {@link #presentLock}. */
    private void closeGpu() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
        gpuActive = false;
        presentLock.notifyAll();
    }

    /** The window's native handle, or 0 when it cannot be had. */
    private long windowHandle() {
        try {
            return NativeWindow.handle(this);
        } catch (LinkageError ex) {
            HLogger.warn("The graphics card libraries are missing, drawing in software: " + ex);
            return 0L;
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        synchronized (presentLock) {
            windowShown = true;
            windowGeneration++;
        }
    }

    /**
     * The window is about to go (fullscreen switching remakes it). bgfx can
     * only be stopped from the game thread, so give it a moment to let go of
     * the window before it is destroyed.
     */
    @Override
    public void removeNotify() {
        synchronized (presentLock) {
            windowShown = false;
            windowGeneration++;
            long deadline = System.nanoTime() + WINDOW_RELEASE_WAIT_NANOS;
            while (gpuActive && !exwist) {
                long left = deadline - System.nanoTime();
                if (left <= 0L) {
                    break;
                }
                try {
                    presentLock.wait(Math.max(1L, left / 1_000_000L));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        super.removeNotify();
    }

    /** Turns drawing through the graphics card on or off, and saves the choice. */
    public static void setGpuRenderer(boolean on) {
        gpuRenderer = on;
        gpuUnavailable = false; // asking again tries again
        gpuRendererChanged = true;
    }

    public static boolean isGpuRenderer() {
        return gpuRenderer;
    }

    /** Sets how many samples per pixel races are drawn with on the graphics card (1 = off), and saves it. */
    public static void setMultisample(int samples) {
        multisample = samples;
        multisampleChanged = true;
    }

    public static int getMultisample() {
        return multisample;
    }

    /** Supersampling in samples per pixel: 2, 4 or 8, or 1 for off. */
    public static int getSupersample() {
        return supersample;
    }

    /** How many times the shown width and height supersampling draws at: the square root of the samples. */
    private static float supersampleAcross() {
        return (float) Math.sqrt(supersample);
    }

    /** The lowest share of the window races may be drawn at, in percent; 0 for off. */
    public static int getDynamicResolution() {
        return dynamicResolution;
    }

    /** The multisampling setting in words, with what the card made of it when that differs. */
    public static String describeMultisample() {
        int asked = multisample;
        String words = asked <= 1 ? "off" : asked + "x";
        if (!gpuRenderer) {
            return words + " (only with the gpu renderer)";
        }
        int used = multisampleInUse;
        if (asked > 1 && used != asked) {
            return words + " (the graphics card does " + (used <= 1 ? "none" : used + "x at most") + ")";
        }
        return words;
    }

    /** Sets supersampling, in samples per pixel (2, 4 or 8; 1 is off), saved on the next tick. */
    public static void setSupersample(int samples) {
        supersample = samples;
        supersampleChanged = true;
    }

    /** The size races were last drawn at with supersampling, for the settings; 0 before the first. */
    private static volatile int supersampleWidth;
    private static volatile int supersampleHeight;

    /** The supersampling setting in words, with the size it draws at and what the card made of it. */
    public static String describeSupersample() {
        if (supersample <= 1) {
            return "off";
        }
        String words = supersample + "x";
        if (!gpuRenderer) {
            return words + " (only with the gpu renderer)";
        }
        float used = supersampleInUse;
        int w = supersampleWidth;
        int h = supersampleHeight;
        String size = w > 0 && used > 1F ? ", races drawn at " + w + "x" + h : "";
        if (used > 1F && used < supersampleAcross() - 0.01F) {
            return words + size + String.format(" (%.2fx across: the graphics card's largest texture)", used);
        }
        return words + size;
    }

    /** 2, 4 or 8 for those (also with an "x"; "on" is 4), 1 for "off" or "1", else 0. */
    public static int parseSupersample(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.endsWith("x")) {
            t = t.substring(0, t.length() - 1);
        }
        return switch (t) {
            case "on", "4" -> 4;
            case "2" -> 2;
            case "8" -> 8;
            case "off", "1" -> 1;
            default -> 0;
        };
    }

    /** 1, 2, 4, 8 or 16 for "off", "1", "2", "4", "8" or "16" (also with an "x"), or 0 if it is none of those. */
    public static int parseMultisample(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.endsWith("x")) {
            t = t.substring(0, t.length() - 1);
        }
        return switch (t) {
            case "off", "1" -> 1;
            case "2" -> 2;
            case "4" -> 4;
            case "8" -> 8;
            case "16" -> 16;
            default -> 0;
        };
    }

    /**
     * Sets the lowest share of the window's size, in percent, dynamic
     * resolution may draw races at (DYNAMIC_RESOLUTION_MIN to 100, or 0 for
     * off), saved with the rest of the settings on the next tick.
     *
     * @return false if the value is out of range
     */
    public static boolean setDynamicResolution(int lowest) {
        if (lowest != 0 && (lowest < DYNAMIC_RESOLUTION_MIN || lowest > 100)) {
            return false;
        }
        dynamicResolution = lowest == 100 ? 0 : lowest; // never below the whole size is never scaling
        dynamicResolutionChanged = true;
        return true;
    }

    /** The dynamic resolution setting in words, with the scale races are drawn at now when it is on. */
    public static String describeDynamicResolution() {
        int lowest = dynamicResolution;
        if (lowest == 0) {
            return "off";
        }
        String words = "on, down to " + lowest + "% of the window";
        if (!gpuRenderer) {
            return words + " (only with the gpu renderer)";
        }
        return words + String.format(java.util.Locale.ROOT, "; races drawn at %d%% now",
                Math.round(sceneScaleInUse * 100F));
    }

    /** The lowest percent for "on" (the default), "off", or a number; -1 if it is none of those. */
    public static int parseDynamicResolution(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1);
        }
        switch (t) {
            case "off":
                return 0;
            case "on":
                return DYNAMIC_RESOLUTION_DEFAULT;
            default:
                try {
                    int lowest = Integer.parseInt(t);
                    return lowest == 0 || lowest >= DYNAMIC_RESOLUTION_MIN && lowest <= 100 ? lowest : -1;
                } catch (NumberFormatException ex) {
                    return -1;
                }
        }
    }

    /**
     * -Dnfm.dynres, else off in hidden test runs (their pictures are compared
     * pixel for pixel), else the saved choice, else on down to 50%.
     */
    private void loadDynamicResolution() {
        String property = System.getProperty("nfm.dynres");
        int lowest = DYNAMIC_RESOLUTION_DEFAULT;
        if (property != null) {
            lowest = parseDynamicResolution(property);
            if (lowest < 0) {
                HLogger.warn("Ignoring invalid nfm.dynres value: " + property);
                lowest = runsHidden() ? 0 : DYNAMIC_RESOLUTION_DEFAULT;
            }
        } else if (runsHidden()) {
            lowest = 0;
        } else {
            int saved = readcookie(DYNAMIC_RESOLUTION_COOKIE);
            if (saved == 0 || saved >= DYNAMIC_RESOLUTION_MIN && saved <= 100) {
                lowest = saved;
            }
        }
        dynamicResolution = lowest == 100 ? 0 : lowest;
        dynamicResolutionChanged = false;
    }

    private void saveDynamicResolutionIfChanged() {
        if (dynamicResolutionChanged) {
            dynamicResolutionChanged = false;
            savecookie(DYNAMIC_RESOLUTION_COOKIE, String.valueOf(dynamicResolution));
        }
    }

    private static final String GAMEPAD_COOKIE = "gamepad";
    private static final String RUMBLE_COOKIE = "rumble";
    private static volatile boolean gamepadChanged;

    /** Turns the game controllers on or off, and saves the choice. */
    public static void setGamepad(boolean on) {
        Gamepads.setOn(on);
        gamepadChanged = true;
    }

    /** Turns the controllers' shaking on hard hits on or off, and saves the choice. */
    public static void setRumble(boolean on) {
        Gamepads.setRumble(on);
        gamepadChanged = true;
    }

    /** One line on the controllers: on or off, which are connected, and whether they shake. */
    public static String describeGamepads() {
        if (!Gamepads.isOn()) {
            return "Game controllers are off.";
        }
        List<String> pads = Gamepads.connected();
        return (pads.isEmpty() ? "No game controller connected." : "Connected: " + String.join(", ", pads) + ".")
                + " Shaking on hits is " + (Gamepads.isRumble() ? "on" : "off") + ".";
    }

    /**
     * Starts reading game controllers: -Dnfm.gamepad=on|off, else never in
     * hidden test runs (a pad on the desk must not drive them), else as saved.
     */
    private void startGamepads() {
        String property = System.getProperty("nfm.gamepad");
        boolean hidden = runsHidden();
        if (property != null ? property.trim().equalsIgnoreCase("off") || property.trim().equalsIgnoreCase("false")
                : hidden) {
            Gamepads.setOn(false);
            return;
        }
        if (property == null) {
            Gamepads.setOn(readcookie(GAMEPAD_COOKIE) != 0);
            Gamepads.setRumble(readcookie(RUMBLE_COOKIE) != 0);
        }
        gamepadChanged = false;
        Gamepads.start(new Gamepads.Keys() {
            @Override
            public void key(int code, boolean down) {
                if (down) {
                    pressKey(code, false, 0L);
                } else {
                    releaseKey(code, 0L);
                }
            }

            @Override
            public boolean racing() {
                GameUi x = ui;
                return x != null && x.fase == Phase.INGAME;
            }

            @Override
            public boolean focused() {
                return !lostfcs && !exwist;
            }
        });
    }

    private static final String HDR_COOKIE = "hdr";
    /** GpuDisplay.HDR_OFF, HDR_AUTO or HDR_FORCE. */
    private static volatile int hdrMode = nfm.lit.gpu.GpuDisplay.HDR_AUTO;
    private static volatile boolean hdrChanged;
    /** What the graphics card made of hdrMode, for the console. */
    private static volatile String hdrInUse = "";

    /** HDR_OFF, HDR_AUTO or HDR_FORCE for "off", "auto" or "force", or -1 if it is none of those. */
    public static int parseHdr(String text) {
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "off", "false" -> nfm.lit.gpu.GpuDisplay.HDR_OFF;
            case "auto", "on", "true" -> nfm.lit.gpu.GpuDisplay.HDR_AUTO;
            case "force" -> nfm.lit.gpu.GpuDisplay.HDR_FORCE;
            default -> -1;
        };
    }

    /** HDR_OFF or HDR_AUTO, saved. */
    public static void setHdr(int mode) {
        hdrMode = mode;
        hdrChanged = true;
    }

    /** GpuDisplay.HDR_OFF, HDR_AUTO or HDR_FORCE. */
    public static int getHdr() {
        return hdrMode;
    }

    /** Paper white in nits, 0 for Windows' SDR white. */
    public static int getPaperWhite() {
        return paperWhite;
    }

    /** The display's peak in nits, 0 for what the monitor says. */
    public static int getPeak() {
        return peakNits;
    }

    /** The HDR setting in words, with what it does on this monitor. */
    public static String describeHdr() {
        String words = switch (hdrMode) {
            case nfm.lit.gpu.GpuDisplay.HDR_OFF -> "off";
            case nfm.lit.gpu.GpuDisplay.HDR_FORCE -> "forced";
            default -> "auto";
        };
        if (!gpuRenderer) {
            return words + " (only with the gpu renderer)";
        }
        String inUse = hdrInUse;
        return inUse.isEmpty() || hdrMode == nfm.lit.gpu.GpuDisplay.HDR_OFF ? words : words + ": " + inUse;
    }

    /** -Dnfm.hdr, else off in hidden test runs (their pictures are compared pixel for pixel), else the saved choice. */
    private void loadHdr() {
        String property = System.getProperty("nfm.hdr");
        int mode = nfm.lit.gpu.GpuDisplay.HDR_AUTO;
        if (property != null) {
            mode = parseHdr(property);
            if (mode < 0) {
                HLogger.warn("Ignoring invalid nfm.hdr value: " + property);
                mode = nfm.lit.gpu.GpuDisplay.HDR_AUTO;
            }
        } else if (runsHidden()) {
            mode = nfm.lit.gpu.GpuDisplay.HDR_OFF;
        } else if (readcookie(HDR_COOKIE) == 0) {
            mode = nfm.lit.gpu.GpuDisplay.HDR_OFF;
        }
        hdrMode = mode;
        hdrChanged = false;
    }

    private void saveHdrIfChanged() {
        if (hdrChanged) {
            hdrChanged = false;
            savecookie(HDR_COOKIE, hdrMode == nfm.lit.gpu.GpuDisplay.HDR_OFF ? "0" : "1");
            savecookie(PAPER_WHITE_COOKIE, String.valueOf(paperWhite));
            savecookie(PEAK_COOKIE, String.valueOf(peakNits));
        }
    }

    private static final String PAPER_WHITE_COOKIE = "paperwhite";
    private static final String PEAK_COOKIE = "peaknits";
    /** In HDR, the game's white in nits; 0 follows Windows' SDR content brightness. */
    private static volatile int paperWhite;
    /** In HDR, the display's peak in nits; 0 takes what the monitor tells Windows. */
    private static volatile int peakNits;

    /**
     * Nits for "auto" (0) or a number from {@code min} to {@code max}, also
     * with "nits" after it; -1 if it is none of those.
     */
    public static int parseNits(String text, int min, int max) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("auto")) {
            return 0;
        }
        if (t.endsWith("nits")) {
            t = t.substring(0, t.length() - 4).trim();
        }
        try {
            int nits = Integer.parseInt(t);
            return nits >= min && nits <= max ? nits : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Paper white in nits, or 0 for Windows' SDR white; saved. */
    public static void setPaperWhite(int nits) {
        paperWhite = nits;
        hdrChanged = true;
    }

    /** The display's peak in nits, or 0 for what the monitor says; saved. */
    public static void setPeak(int nits) {
        peakNits = nits;
        hdrChanged = true;
    }

    /** A saved or given brightness: 0 (auto) or within range, else 0. */
    private static int nitsOrAuto(int nits, int min, int max) {
        return nits >= min && nits <= max ? nits : 0;
    }

    /** -Dnfm.hdr.white and -Dnfm.hdr.peak, else the saved values (hidden runs: Windows' and the monitor's). */
    private void loadHdrBrightness() {
        int white = 0;
        int peak = 0;
        if (!runsHidden()) {
            white = nitsOrAuto(readcookie(PAPER_WHITE_COOKIE), nfm.lit.gpu.GpuDisplay.PAPER_WHITE_MIN,
                    nfm.lit.gpu.GpuDisplay.PAPER_WHITE_MAX);
            peak = nitsOrAuto(readcookie(PEAK_COOKIE), nfm.lit.gpu.GpuDisplay.PEAK_MIN,
                    nfm.lit.gpu.GpuDisplay.PEAK_MAX);
        }
        String property = System.getProperty("nfm.hdr.white");
        if (property != null) {
            int nits = parseNits(property, nfm.lit.gpu.GpuDisplay.PAPER_WHITE_MIN, nfm.lit.gpu.GpuDisplay.PAPER_WHITE_MAX);
            if (nits < 0) {
                HLogger.warn("Ignoring invalid nfm.hdr.white value: " + property);
            } else {
                white = nits;
            }
        }
        property = System.getProperty("nfm.hdr.peak");
        if (property != null) {
            int nits = parseNits(property, nfm.lit.gpu.GpuDisplay.PEAK_MIN, nfm.lit.gpu.GpuDisplay.PEAK_MAX);
            if (nits < 0) {
                HLogger.warn("Ignoring invalid nfm.hdr.peak value: " + property);
            } else {
                peak = nits;
            }
        }
        paperWhite = white;
        peakNits = peak;
    }

    private static final String VOLUME_COOKIE = "vol";
    private static final String MONO_COOKIE = "soundmono";
    private static final boolean[] volumeChanged = new boolean[nfm.lit.audio.Sound3D.BUS_NAMES.length];
    private static volatile boolean monoChanged;

    /** The bus of this name ("master", "engines", "effects", "music", "interface"), or -1. */
    public static int parseBus(String text) {
        return java.util.Arrays.asList(nfm.lit.audio.Sound3D.BUS_NAMES)
                .indexOf(text.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Sets a bus's volume in percent, saved; the 3D sound takes it at once. */
    public static synchronized void setVolume(int bus, int percent) {
        nfm.lit.audio.Sound3D.setBusLevel(bus, Math.max(0, Math.min(100, percent)) / 100F);
        volumeChanged[bus] = true;
    }

    /** Every bus's volume in words. */
    public static String describeVolumes() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < nfm.lit.audio.Sound3D.BUS_NAMES.length; i++) {
            b.append(i == 0 ? "" : ", ").append(nfm.lit.audio.Sound3D.BUS_NAMES[i]).append(' ')
                    .append(Math.round(nfm.lit.audio.Sound3D.busLevel(i) * 100F));
        }
        return b.toString();
    }

    /** Mono output for the 3D sound, saved; at once. */
    public static void setMono(boolean on) {
        nfm.lit.audio.Sound3D.setMono(on);
        monoChanged = true;
    }

    /* the track's sound effects and HDR audio; the names must not hold another cookie's (they are matched in part) */
    private static final String OCCLUSION_COOKIE = "occlusion";
    private static final String PATHS_COOKIE = "soundpaths";
    private static final String REVERB_COOKIE = "trackreverb";
    private static final String HDR_AUDIO_COOKIE = "audiorange";
    private static volatile boolean audioEffectsChanged;

    public static void setOcclusion(boolean on) {
        nfm.lit.audio.Sound3D.setOcclusion(on);
        audioEffectsChanged = true;
    }

    public static void setSoundPaths(boolean on) {
        nfm.lit.audio.Sound3D.setPaths(on);
        audioEffectsChanged = true;
    }

    public static void setReverb(boolean on) {
        nfm.lit.audio.Sound3D.setReverb(on);
        audioEffectsChanged = true;
    }

    /** HDR audio's range in dB, 0 for off, saved. */
    public static void setHdrAudio(int db) {
        nfm.lit.audio.Sound3D.setHdrRange(db);
        audioEffectsChanged = true;
    }

    public static String describeAudioEffects() {
        int range = nfm.lit.audio.Sound3D.hdrRange();
        return "occlusion " + (nfm.lit.audio.Sound3D.isOcclusion() ? "on" : "off")
                + ", paths " + (nfm.lit.audio.Sound3D.isPaths() ? "on" : "off")
                + ", reverb " + (nfm.lit.audio.Sound3D.isReverb() ? "on" : "off")
                + ", HDR audio " + (range == 0 ? "off" : range + " dB");
    }

    /* each action's keys, one cookie each: "keybind01" on (never "keybind1": cookies are matched in part) */
    private static String keyCookie(int action) {
        return String.format("keybind%02d", action + 1);
    }

    /** The keys as saved; hidden runs keep the game's own. */
    private void loadKeys() {
        int[] saved = new int[KeyBindings.COUNT];
        for (int a = 0; a < saved.length; a++) {
            saved[a] = runsHidden() ? -1 : readcookie(keyCookie(a));
        }
        KeyBindings.load(saved);
    }

    private void saveKeysIfChanged() {
        if (KeyBindings.takeChanged()) {
            for (int a = 0; a < KeyBindings.COUNT; a++) {
                savecookie(keyCookie(a), String.valueOf(KeyBindings.saved(a)));
            }
        }
    }

    /** The saved switches (each saved as 1 on, 2 off), or all on in hidden runs; HDR audio off unless saved. */
    private void loadAudioEffects() {
        boolean hidden = runsHidden();
        nfm.lit.audio.Sound3D.setOcclusion(hidden || readcookie(OCCLUSION_COOKIE) != 2);
        nfm.lit.audio.Sound3D.setPaths(hidden || readcookie(PATHS_COOKIE) != 2);
        nfm.lit.audio.Sound3D.setReverb(hidden || readcookie(REVERB_COOKIE) != 2);
        int range = hidden ? -1 : readcookie(HDR_AUDIO_COOKIE);
        nfm.lit.audio.Sound3D.setHdrRange(range > 0 && range <= 24 ? range : 0);
        audioEffectsChanged = false;
    }

    private void saveAudioEffectsIfChanged() {
        if (audioEffectsChanged) {
            audioEffectsChanged = false;
            savecookie(OCCLUSION_COOKIE, nfm.lit.audio.Sound3D.isOcclusion() ? "1" : "2");
            savecookie(PATHS_COOKIE, nfm.lit.audio.Sound3D.isPaths() ? "1" : "2");
            savecookie(REVERB_COOKIE, nfm.lit.audio.Sound3D.isReverb() ? "1" : "2");
            savecookie(HDR_AUDIO_COOKIE, String.valueOf(nfm.lit.audio.Sound3D.hdrRange()));
        }
    }

    /** The volumes and mono as saved; hidden runs keep everything at full and in stereo. */
    private void loadVolumes() {
        boolean hidden = runsHidden();
        for (int i = 0; i < nfm.lit.audio.Sound3D.BUS_NAMES.length; i++) {
            int saved = hidden ? -1 : readcookie(VOLUME_COOKIE + nfm.lit.audio.Sound3D.BUS_NAMES[i]);
            nfm.lit.audio.Sound3D.setBusLevel(i, saved >= 0 && saved <= 100 ? saved / 100F : 1F);
            volumeChanged[i] = false;
        }
        nfm.lit.audio.Sound3D.setMono(!hidden && readcookie(MONO_COOKIE) == 1);
        monoChanged = false;
    }

    private void saveVolumesIfChanged() {
        for (int i = 0; i < volumeChanged.length; i++) {
            boolean changed;
            synchronized (GameSparker.class) {
                changed = volumeChanged[i];
                volumeChanged[i] = false;
            }
            if (changed) {
                savecookie(VOLUME_COOKIE + nfm.lit.audio.Sound3D.BUS_NAMES[i],
                        String.valueOf(Math.round(nfm.lit.audio.Sound3D.busLevel(i) * 100F)));
            }
        }
        if (monoChanged) {
            monoChanged = false;
            savecookie(MONO_COOKIE, nfm.lit.audio.Sound3D.isMono() ? "1" : "0");
        }
    }

    private static final String MUSIC_SEPARATION_COOKIE = "musicsep";
    private static volatile boolean musicSeparationChanged;

    /** Sets the music's stereo separation in percent (0 to 200), saved; the music playing takes it at once. */
    public static void setMusicSeparation(int percent) {
        nfm.lit.audio.StreamMusic.setSeparation(percent);
        musicSeparationChanged = true;
    }

    /** The music setting in words: its stereo separation, and what plays it. */
    public static String describeMusic() {
        String by = newSound && nfm.lit.audio.StreamMusic.available() ? "played by the 3D sound through libopenmpt"
                : "played by BASS, which keeps its own separation";
        return "stereo separation " + nfm.lit.audio.StreamMusic.separation() + "%, " + by;
    }

    /** -Dnfm.music.separation, else the saved choice, else the default; hidden runs always use the default. */
    private void loadMusicSeparation() {
        String property = System.getProperty("nfm.music.separation");
        int percent = nfm.lit.audio.StreamMusic.DEFAULT_SEPARATION;
        if (property != null) {
            try {
                percent = Integer.parseInt(property.trim());
            } catch (NumberFormatException ex) {
                HLogger.warn("Ignoring invalid nfm.music.separation value: " + property);
            }
        } else if (!runsHidden()) {
            int saved = readcookie(MUSIC_SEPARATION_COOKIE);
            if (saved >= 0) {
                percent = saved;
            }
        }
        nfm.lit.audio.StreamMusic.setSeparation(percent);
        musicSeparationChanged = false;
    }

    private void saveMusicSeparationIfChanged() {
        if (musicSeparationChanged) {
            musicSeparationChanged = false;
            savecookie(MUSIC_SEPARATION_COOKIE, String.valueOf(nfm.lit.audio.StreamMusic.separation()));
        }
    }

    private static final String SOUND_COOKIE = "sound3d";
    /** The new (3D) sound, or Classic: the game's own sound as it always played. */
    private static volatile boolean newSound = true;
    private static volatile boolean newSoundChanged;
    /** The new sound was started (or found it could not be) for the current setting. */
    private boolean newSoundTried;

    /** True for "3d", "new" or "on", false for "classic" or "off", null for anything else. */
    public static Boolean parseSound(String text) {
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "3d", "new", "on" -> Boolean.TRUE;
            case "classic", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** True for the new (3D) sound, false for Classic. */
    public static boolean isNewSound() {
        return newSound;
    }

    /** Switches between the new sound and Classic, saved; takes effect on the next tick. */
    public static void setNewSound(boolean on) {
        newSound = on;
        newSoundChanged = true;
    }

    /** The sound setting in words, with the device and HRTF when the new sound plays. */
    public static String describeSound() {
        if (!newSound) {
            return "classic";
        }
        return nfm.lit.audio.Sound3D.isActive() ? "3d (" + nfm.lit.audio.Sound3D.status() + ")"
                : "3d (" + nfm.lit.audio.Sound3D.status() + "; Classic plays meanwhile)";
    }

    /** -Dnfm.sound, else the saved choice, else the new sound. */
    private void loadSound() {
        String property = System.getProperty("nfm.sound");
        boolean on = true;
        if (property != null) {
            Boolean parsed = parseSound(property);
            if (parsed == null) {
                HLogger.warn("Ignoring invalid nfm.sound value: " + property);
            } else {
                on = parsed;
            }
        } else if (readcookie(SOUND_COOKIE) == 0) {
            on = false;
        }
        newSound = on;
        newSoundChanged = false;
    }

    /**
     * Saves a changed sound setting and starts or stops the new sound to
     * match, once the game's clips are loaded; on the game thread, once a
     * tick. Classic's clips are stopped when the new sound takes over, so a
     * looping engine does not carry on underneath it.
     */
    private void applySound() {
        if (newSoundChanged) {
            newSoundChanged = false;
            savecookie(SOUND_COOKIE, newSound ? "1" : "0");
            newSoundTried = false;
            if (!newSound) {
                nfm.lit.audio.Sound3D.setWanted(false);
            }
        }
        if (newSound && !newSoundTried && ui != null && !ui.sm.clips().isEmpty()) {
            newSoundTried = true;
            nfm.lit.audio.Sound3D.setWanted(true);
            nfm.lit.audio.Sound3D.start(ui.sm);
            if (nfm.lit.audio.Sound3D.isActive()) {
                ui.sm.stopClassic();
            }
        }
    }

    /**
     * Tells the new sound where every car and the camera are at the end of
     * this tick, and what each engine is doing, judged the way the game
     * judges the player's engine (see GameUi.playsounds).
     */
    /** How far the ground the 3D sound traces against reaches past the stage's pieces. */
    private static final int SOUND_GROUND_MARGIN = 20_000;

    /**
     * Hands the stage's pieces to the 3D sound as triangles in the world,
     * with the ground under them, so cars behind a hill or a wall are
     * muffled and the track rings as its shape makes it. Each piece is
     * placed and turned as the renderer turns it; green faces are taken for
     * foliage, glass for glass, the rest for concrete and rock. Reads the
     * pieces only, so the race is untouched.
     */
    private void sendSoundStage(ContO[] pieces) {
        int vertices = 4;
        int triangles = 2;
        for (int i = GameFacts.numberOfPlayers; i < nob; i++) {
            ContO c = pieces[i];
            for (int j = 0; c != null && j < c.npl; j++) {
                int n = Math.min(c.p[j].n, c.p[j].ox.length);
                if (n >= 3) {
                    vertices += n;
                    triangles += n - 2;
                }
            }
        }
        float[] v = new float[3 * vertices];
        int[] t = new int[3 * triangles];
        byte[] kinds = new byte[triangles];
        int vi = 0;
        int ti = 0;
        float minX = -SOUND_GROUND_MARGIN;
        float maxX = SOUND_GROUND_MARGIN;
        float minZ = -SOUND_GROUND_MARGIN;
        float maxZ = SOUND_GROUND_MARGIN;
        for (int i = GameFacts.numberOfPlayers; i < nob; i++) {
            ContO c = pieces[i];
            if (c == null) {
                continue;
            }
            double xy = Math.toRadians(c.xy);
            double zy = Math.toRadians(c.zy);
            double xz = Math.toRadians(c.xz);
            float sxy = (float) Math.sin(xy);
            float cxy = (float) Math.cos(xy);
            float szy = (float) Math.sin(zy);
            float czy = (float) Math.cos(zy);
            float sxz = (float) Math.sin(xz);
            float cxz = (float) Math.cos(xz);
            for (int j = 0; j < c.npl; j++) {
                Plane plane = c.p[j];
                int n = Math.min(plane.n, plane.ox.length);
                if (n < 3) {
                    continue;
                }
                int first = vi;
                for (int k = 0; k < n; k++) {
                    // as Plane.d turns it: by xy, then zy, then xz, about the piece's middle
                    float x = plane.ox[k];
                    float y = plane.oy[k];
                    float z = plane.oz[k];
                    float x1 = x * cxy - y * sxy;
                    float y1 = x * sxy + y * cxy;
                    float y2 = y1 * czy - z * szy;
                    float z2 = y1 * szy + z * czy;
                    float x3 = x1 * cxz - z2 * sxz;
                    float z3 = x1 * sxz + z2 * cxz;
                    v[3 * vi] = c.x + x3;
                    v[3 * vi + 1] = c.y + y2;
                    v[3 * vi + 2] = c.z + z3;
                    minX = Math.min(minX, v[3 * vi] - SOUND_GROUND_MARGIN);
                    maxX = Math.max(maxX, v[3 * vi] + SOUND_GROUND_MARGIN);
                    minZ = Math.min(minZ, v[3 * vi + 2] - SOUND_GROUND_MARGIN);
                    maxZ = Math.max(maxZ, v[3 * vi + 2] + SOUND_GROUND_MARGIN);
                    vi++;
                }
                int[] rgb = plane.c;
                byte kind = plane.glass ? nfm.lit.audio.Sound3D.SURFACE_GLASS
                        : rgb[1] > rgb[0] * 1.15F && rgb[1] > rgb[2] * 1.15F ? nfm.lit.audio.Sound3D.SURFACE_FOLIAGE
                        : nfm.lit.audio.Sound3D.SURFACE_HARD;
                for (int k = 1; k < n - 1; k++) {
                    t[3 * ti] = first;
                    t[3 * ti + 1] = first + k;
                    t[3 * ti + 2] = first + k + 1;
                    kinds[ti++] = kind;
                }
            }
        }
        float[][] corners = {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}};
        int ground = vi;
        for (float[] corner : corners) {
            v[3 * vi] = corner[0];
            v[3 * vi + 1] = Medium.ground;
            v[3 * vi + 2] = corner[1];
            vi++;
        }
        int[][] halves = {{0, 1, 2}, {0, 2, 3}};
        for (int[] half : halves) {
            for (int k = 0; k < 3; k++) {
                t[3 * ti + k] = ground + half[k];
            }
            kinds[ti++] = nfm.lit.audio.Sound3D.SURFACE_GROUND;
        }
        nfm.lit.audio.Sound3D.setStage(v, t, kinds);
    }

    private void publishSound() {
        if (!nfm.lit.audio.Sound3D.isActive() || racers == null || sceneObjects == null) {
            return;
        }
        nfm.lit.audio.Sound3D.Moment m = new nfm.lit.audio.Sound3D.Moment();
        m.nanos = System.nanoTime();
        m.tickNanos = currentTickNanos;
        m.cameraX = Medium.x + Medium.cx;
        m.cameraY = Medium.y + Medium.cy;
        m.cameraZ = Medium.z + Medium.cz;
        m.yaw = Medium.xz;
        m.pitch = Medium.zy;
        m.muted = Control.mutes;
        m.player = 0;
        boolean live = ui.fase == Phase.INGAME && ui.starcnt < 35;
        boolean replay = ui.fase == Phase.INSTANTREPLAY || ui.fase == Phase.GAMEHIGHLIGHT
                || ui.fase == Phase.CAUGHTHIGHLIGHT;
        int n = Math.min(GameFacts.numberOfPlayers, Math.min(racers.length, sceneObjects.length));
        m.cars = new nfm.lit.audio.Sound3D.Car[n];
        if (!replay) {
            replayHeard = false;
        }
        for (int i = 0; i < n; i++) {
            Madness r = racers[i];
            ContO c = sceneObjects[i];
            Control k = u[i];
            nfm.lit.audio.Sound3D.Car car = new nfm.lit.audio.Sound3D.Car();
            if (replay && r != null && c != null) {
                replayCar(i, r, c, car);
            } else if (r != null && c != null && k != null) {
                car.x = c.x;
                car.y = c.y;
                car.z = c.z;
                car.speed = r.speed;
                car.engine = r.stat.engine;
                car.bands = r.stat.swits;
                car.running = live && !r.dest;
                car.throttle = k.up && r.speed > 0F || k.down && r.speed < 10F;
                car.spinning = r.capsized || r.skid == 1 && k.handb || slipping(r.scz) || slipping(r.scx)
                        || k.up && r.speed < 10F;
                car.grounded = r.mtouch;
                car.fullPower = r.power == 98F;
            }
            m.cars[i] = car;
        }
        replayHeard = replay;
        nfm.lit.audio.Sound3D.publish(m);
    }

    /* replays keep where each car was, not how it was driven: the 3D sound works its engine out from that */
    private final float[][] replayPlace = new float[GameFacts.numberOfPlayers][3];
    private final float[] replaySpeed = new float[GameFacts.numberOfPlayers];
    private boolean replayHeard;

    /**
     * A car in a replay or highlight, heard from the replay camera: its
     * speed is how far it moved this tick (a car moves by its speed each
     * tick), on the throttle while it is not slowing. While the picture holds
     * still on an event, the engines hold their note.
     */
    private void replayCar(int i, Madness r, ContO c, nfm.lit.audio.Sound3D.Car car) {
        float[] was = replayPlace[i];
        float dx = c.x - was[0];
        float dy = c.y - was[1];
        float dz = c.z - was[2];
        float moved = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float before = replaySpeed[i];
        if (!replayHeard || moved > 2_000F) {
            replaySpeed[i] = 0F; // the replay started or cut to another moment
        } else if (ui.fase != Phase.CAUGHTHIGHLIGHT) {
            replaySpeed[i] += (moved - replaySpeed[i]) * 0.5F;
        }
        was[0] = c.x;
        was[1] = c.y;
        was[2] = c.z;
        car.x = c.x;
        car.y = c.y;
        car.z = c.z;
        car.speed = replaySpeed[i];
        car.engine = r.stat.engine;
        car.bands = r.stat.swits;
        car.running = true;
        car.throttle = replaySpeed[i] >= before - 1F && replaySpeed[i] > 5F;
        car.grounded = true;
    }

    /** One wheel slides against the others, as GameUi.playsounds tells wheelspin. */
    private static boolean slipping(float[] wheels) {
        return Math.abs(wheels[0] - (wheels[1] + wheels[0] + wheels[2] + wheels[3]) / 4F) > 1.0F;
    }

    /** A smoke test, a race check or another run with no window to show. */
    private static boolean runsHidden() {
        return HIDDEN_RUN;
    }

    private static final boolean HIDDEN_RUN = System.getProperty("nfm.autotest") != null || RaceSession.runsHidden();

    private void saveGamepadIfChanged() {
        if (gamepadChanged) {
            gamepadChanged = false;
            savecookie(GAMEPAD_COOKIE, Gamepads.isOn() ? "1" : "0");
            savecookie(RUMBLE_COOKIE, Gamepads.isRumble() ? "1" : "0");
        }
    }

    /** -Dnfm.msaa, else off in hidden test runs (their pictures are compared pixel for pixel), else the saved choice. */
    private void loadMultisample() {
        String property = System.getProperty("nfm.msaa");
        int samples = 1;
        if (property != null) {
            samples = parseMultisample(property);
            if (samples == 0) {
                HLogger.warn("Ignoring invalid nfm.msaa value: " + property);
                samples = 1;
            }
        } else if (!runsHidden()) {
            int saved = readcookie(MSAA_COOKIE);
            samples = saved == 2 || saved == 4 || saved == 8 || saved == 16 ? saved : 1;
        }
        multisample = samples;
        multisampleChanged = false;
    }

    /** -Dnfm.ssaa, else off in hidden test runs (their pictures are compared pixel for pixel), else the saved choice. */
    private void loadSupersample() {
        String property = System.getProperty("nfm.ssaa");
        int factor = 1;
        if (property != null) {
            factor = parseSupersample(property);
            if (factor == 0) {
                HLogger.warn("Ignoring invalid nfm.ssaa value: " + property);
                factor = 1;
            }
        } else if (!runsHidden()) {
            int saved = readcookie(SUPERSAMPLE_COOKIE);
            if (saved == 2 || saved == 4 || saved == 8) {
                factor = saved;
            } else if (saved < 0 && readcookie(OLD_SUPERSAMPLE_COOKIE) == 2) {
                factor = 4; // "on" before the levels: twice the width and height
            }
        }
        supersample = factor;
        supersampleChanged = false;
    }

    private void saveSupersampleIfChanged() {
        if (supersampleChanged) {
            supersampleChanged = false;
            savecookie(SUPERSAMPLE_COOKIE, String.valueOf(supersample));
        }
    }

    private void saveMultisampleIfChanged() {
        if (multisampleChanged) {
            multisampleChanged = false;
            savecookie(MSAA_COOKIE, String.valueOf(multisample));
        }
    }

    public static String describeRenderer() {
        if (!gpuRenderer) {
            return "software";
        }
        return gpuUnavailable ? "gpu (not available here, drawing in software)" : "gpu";
    }

    /**
     * -Dnfm.renderer=gpu|software, else software in hidden test runs, else the
     * saved choice, else the graphics card.
     */
    private void loadRenderer() {
        String property = System.getProperty("nfm.renderer");
        if (property != null) {
            gpuRenderer = !property.trim().equalsIgnoreCase("software");
        } else if (runsHidden()) {
            gpuRenderer = false;
        } else {
            gpuRenderer = readcookie(RENDERER_COOKIE) != 0;
        }
        gpuRendererChanged = false;
    }

    private void saveRendererIfChanged() {
        if (gpuRendererChanged) {
            gpuRendererChanged = false;
            savecookie(RENDERER_COOKIE, gpuRenderer ? "1" : "0");
        }
    }

    /**
     * Minimum time between two presented frames for the current limit, or 0 for
     * no limit. "Match display" follows the refresh rate of the monitor the
     * window is on and notices when it is moved to another one.
     */
    private long frameIntervalNanos(long now) {
        int limit = fpsLimit;
        double rate = limit;
        if (limit == FPS_MATCH_DISPLAY || limit == FPS_VRR) {
            if (displayRefreshHz == 0 || now - displayRefreshCheckedAt > 1_000_000_000L) {
                displayRefreshHz = queryRefreshRate();
                displayRefreshCheckedAt = now;
            }
            int hz = displayRefreshHz;
            rate = limit == FPS_VRR && hz > 0 ? vrrRate(hz) : hz;
        }
        int cap = batteryCap(now);
        boolean capped = cap > 0 && (rate <= 0 || rate > cap);
        batteryHolds = capped;
        if (capped) {
            rate = cap;
        }
        long interval = rate <= 0 ? 0L : (long) (1_000_000_000L / rate);
        frameBudgetNanos = interval;
        return interval;
    }

    /**
     * The battery frame cap while the machine runs on battery, 0 otherwise or
     * when it is off. Windows is asked every few seconds, so pulling the plug
     * takes effect within them. Hidden test runs measure the game as it is, so
     * they are never capped.
     */
    private static int batteryCap(long now) {
        int cap = batteryFps;
        if (cap <= 0 || runsHidden()) {
            return 0;
        }
        if (powerCheckedAt == 0L || now - powerCheckedAt > 3_000_000_000L) {
            powerCheckedAt = now;
            boolean battery = WindowsScheduling.onBattery();
            if (battery != onBattery) {
                HLogger.info(battery ? "On battery: frames held to " + cap + " a second" : "Plugged in: frame limit as set");
            }
            onBattery = battery;
        }
        return onBattery ? cap : 0;
    }

    /**
     * Sets the frame cap on battery (FPS_MIN to FPS_MAX, or 0 for none), saved
     * with the rest of the settings on the next tick.
     *
     * @return false if the value is out of range
     */
    public static boolean setBatteryFps(int cap) {
        if (cap != 0 && (cap < FPS_MIN || cap > FPS_MAX)) {
            return false;
        }
        batteryFps = cap;
        batteryFpsChanged = true;
        powerCheckedAt = 0L; // look again now
        return true;
    }

    /** The battery cap in words, and whether it holds now. */
    public static String describeBatteryFps() {
        int cap = batteryFps;
        if (cap <= 0) {
            return "off";
        }
        return cap + " fps" + (onBattery && !runsHidden() ? " (on battery now)" : " (plugged in now)");
    }

    /** -Dnfm.batteryfps, else the saved choice, else 60. */
    /* VSync: as the frame rate asks (Match display and Variable refresh wait for it), always, or never */
    public static final int VSYNC_AUTO = 0;
    public static final int VSYNC_ON = 1;
    public static final int VSYNC_OFF = 2;
    private static final String VSYNC_COOKIE = "vsyncmode";
    private static volatile int vsyncMode = VSYNC_AUTO;
    private static volatile boolean vsyncModeChanged;

    public static int getVsync() {
        return vsyncMode;
    }

    /** Sets VSync (VSYNC_AUTO, VSYNC_ON or VSYNC_OFF) and saves it. */
    public static void setVsync(int mode) {
        vsyncMode = mode == VSYNC_ON || mode == VSYNC_OFF ? mode : VSYNC_AUTO;
        vsyncModeChanged = true;
    }

    /**
     * Whether frames wait for the display's refresh: on Auto, with Match
     * display and Variable refresh, whose frames are paced to it; never with
     * a cap or unlimited, where a frame goes out the moment it is ready (and
     * the picture may tear) for the least lag.
     */
    static boolean vsyncInUse() {
        int mode = vsyncMode;
        return mode == VSYNC_ON || mode == VSYNC_AUTO && (fpsLimit == FPS_MATCH_DISPLAY || fpsLimit == FPS_VRR);
    }

    public static String describeVsync() {
        if (!isGpuRenderer()) {
            return "the software renderer cannot wait for the display";
        }
        boolean on = vsyncInUse();
        return switch (vsyncMode) {
            case VSYNC_ON -> "on: frames wait for the display, no tearing";
            case VSYNC_OFF -> "off: frames go out at once, the picture may tear";
            default -> (on ? "on" : "off") + ", as the frame rate (" + describeFpsLimit() + ") asks";
        };
    }

    /** "auto", "on" or "off", else null. */
    static Integer parseVsync(String text) {
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "auto" -> VSYNC_AUTO;
            case "on", "true" -> VSYNC_ON;
            case "off", "false" -> VSYNC_OFF;
            default -> null;
        };
    }

    /** -Dnfm.vsync=auto|on|off, else Auto in hidden test runs, else the saved choice, else Auto. */
    private void loadVsync() {
        Integer property = System.getProperty("nfm.vsync") == null ? null : parseVsync(System.getProperty("nfm.vsync"));
        if (property != null) {
            vsyncMode = property;
        } else if (!runsHidden()) {
            int saved = readcookie(VSYNC_COOKIE);
            vsyncMode = saved == VSYNC_ON || saved == VSYNC_OFF ? saved : VSYNC_AUTO;
        }
        vsyncModeChanged = false;
    }

    private void saveVsyncIfChanged() {
        if (vsyncModeChanged) {
            vsyncModeChanged = false;
            savecookie(VSYNC_COOKIE, String.valueOf(vsyncMode));
        }
    }

    private void loadBatteryFps() {
        String property = System.getProperty("nfm.batteryfps");
        int cap = 60;
        if (property != null) {
            Integer parsed = parseFpsLimit(property);
            cap = parsed == null ? -1 : parsed == FPS_UNLIMITED ? 0 : parsed;
            if (cap != 0 && (cap < FPS_MIN || cap > FPS_MAX)) {
                HLogger.warn("Ignoring invalid nfm.batteryfps value: " + property);
                cap = 60;
            }
        } else {
            int saved = readcookie(BATTERY_FPS_COOKIE);
            if (saved == 0 || saved >= FPS_MIN && saved <= FPS_MAX) {
                cap = saved;
            }
        }
        batteryFps = cap;
        batteryFpsChanged = false;
    }

    private void saveBatteryFpsIfChanged() {
        if (batteryFpsChanged) {
            batteryFpsChanged = false;
            savecookie(BATTERY_FPS_COOKIE, String.valueOf(batteryFps));
        }
    }

    private int queryRefreshRate() {
        try {
            GraphicsConfiguration configuration = getGraphicsConfiguration();
            GraphicsDevice device = configuration != null ? configuration.getDevice()
                    : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            int hz = device.getDisplayMode().getRefreshRate();
            return hz == DisplayMode.REFRESH_RATE_UNKNOWN ? -1 : hz;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /**
     * Sets the frame rate limit: {@link #FPS_MATCH_DISPLAY}, {@link #FPS_UNLIMITED}
     * or a cap between {@link #FPS_MIN} and {@link #FPS_MAX}. The game saves it
     * with the rest of its settings on the next tick.
     *
     * @return false if the value is out of range
     */
    /** The frame rate that keeps a variable-refresh monitor inside its range at this refresh rate. */
    static double vrrRate(int hz) {
        return hz - hz * (double) hz / 3600.0;
    }

    public static boolean setFpsLimit(int limit) {
        if (limit != FPS_MATCH_DISPLAY && limit != FPS_VRR && limit != FPS_UNLIMITED
                && (limit < FPS_MIN || limit > FPS_MAX)) {
            return false;
        }
        fpsLimit = limit;
        fpsLimitChanged = true;
        return true;
    }

    public static String describeFpsLimit() {
        int limit = fpsLimit;
        if (limit == FPS_MATCH_DISPLAY) {
            return "match display";
        }
        if (limit == FPS_VRR) {
            return "variable refresh (just under the display's refresh rate)";
        }
        return limit == FPS_UNLIMITED ? "unlimited" : limit + " fps";
    }

    /**
     * The limit comes from -Dnfm.fps (madness64.ini) when present, otherwise
     * from the saved setting, otherwise it matches the display.
     */
    private void loadFpsLimit() {
        String property = System.getProperty("nfm.fps");
        if (property != null) {
            Integer parsed = parseFpsLimit(property);
            if (parsed != null && setFpsLimit(parsed)) {
                fpsLimitChanged = false;
                return;
            }
            HLogger.warn("Ignoring invalid nfm.fps value: " + property);
        }
        int saved = readcookie(FPS_COOKIE);
        int limit = saved == FPS_COOKIE_MATCH_DISPLAY ? FPS_MATCH_DISPLAY : saved == FPS_COOKIE_VRR ? FPS_VRR : saved;
        if (saved < 0 || !setFpsLimit(limit)) {
            fpsLimit = FPS_MATCH_DISPLAY;
        }
        fpsLimitChanged = false;
    }

    /**
     * Parses "auto"/"display", "unlimited"/"off"/"0" or a number.
     *
     * @return the limit, or null if the text is not one of those
     */
    public static Integer parseFpsLimit(String text) {
        String value = text.trim().toLowerCase();
        switch (value) {
            case "auto":
            case "display":
                return FPS_MATCH_DISPLAY;
            case "vrr":
            case "gsync":
            case "freesync":
                return FPS_VRR;
            case "unlimited":
            case "uncapped":
            case "off":
                return FPS_UNLIMITED;
            default:
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    return null;
                }
        }
    }

    /** Turns run-ahead on or off, and saves the choice. */
    public static void setRunAhead(boolean on) {
        runAhead = on;
        runAheadChanged = true;
    }

    public static boolean isRunAhead() {
        return runAhead;
    }

    private static final String SMOOTH_EFFECTS_COOKIE = "smoothfx";
    /**
     * Smooth effects (on unless turned off): dust and flying debris move on
     * every frame drawn, as the cars do, instead of once a game tick. Only
     * where they are drawn changes; the race itself never sees it.
     */
    private static volatile boolean smoothEffects = true;
    private static volatile boolean smoothEffectsChanged;

    /** Turns smooth effects on or off, and saves the choice. */
    public static void setSmoothEffects(boolean on) {
        smoothEffects = on;
        smoothEffectsChanged = true;
    }

    public static boolean isSmoothEffects() {
        return smoothEffects;
    }

    /** -Dnfm.smootheffects=true|false, else off in hidden test runs (as the original drew), else the saved choice, else on. */
    private void loadSmoothEffects() {
        String property = System.getProperty("nfm.smootheffects");
        if (property != null) {
            smoothEffects = !property.trim().equalsIgnoreCase("false") && !property.trim().equalsIgnoreCase("off");
        } else if (runsHidden()) {
            smoothEffects = false;
        } else {
            smoothEffects = readcookie(SMOOTH_EFFECTS_COOKIE) != 0;
        }
        smoothEffectsChanged = false;
    }

    private void saveSmoothEffectsIfChanged() {
        if (smoothEffectsChanged) {
            smoothEffectsChanged = false;
            savecookie(SMOOTH_EFFECTS_COOKIE, smoothEffects ? "1" : "0");
        }
    }

    private static final String PERF_OVERLAY_COOKIE = "perfhud";

    /**
     * -Dnfm.overlay=off|on|all (on: the default readouts, top left; all:
     * every readout), else the saved choice, else off.
     */
    private void loadPerfOverlay() {
        String property = System.getProperty("nfm.overlay");
        if (property != null) {
            String p = property.trim().toLowerCase(java.util.Locale.ROOT);
            PerfOverlay.setCorner(p.equals("off") || p.equals("false") ? PerfOverlay.OFF : PerfOverlay.TOP_LEFT);
            PerfOverlay.setReadouts(p.equals("all") ? PerfOverlay.ALL : PerfOverlay.DEFAULT_READOUTS);
            PerfOverlay.takeChanged(); // a test's choice is not saved
        } else if (!runsHidden()) {
            PerfOverlay.restore(readcookie(PERF_OVERLAY_COOKIE));
        }
    }

    private static final String FULLSCREEN_COOKIE = "fullscreen";

    /** Fullscreen as last chosen (not in hidden runs, whose window stays off screen). */
    private void loadFullscreen() {
        if (!runsHidden() && readcookie(FULLSCREEN_COOKIE) == 1) {
            RunApp.setFullscreen(true);
        }
        RunApp.takeFullscreenChanged();
    }

    private void saveFullscreenIfChanged() {
        if (RunApp.takeFullscreenChanged()) {
            savecookie(FULLSCREEN_COOKIE, RunApp.isFullscreen() ? "1" : "0");
        }
    }

    /* the window's inside as the player left it, in the monitor's pixels */
    private static final String WINDOW_WIDTH_COOKIE = "winwidth";
    private static final String WINDOW_HEIGHT_COOKIE = "winheight";

    /** The window's size as last left, in the monitor's pixels; null if none was saved (or in hidden runs). */
    static Dimension savedWindowSize() {
        if (runsHidden()) {
            return null;
        }
        int w = readcookie(WINDOW_WIDTH_COOKIE);
        int h = readcookie(WINDOW_HEIGHT_COOKIE);
        return w >= GameFacts.screenWidth / 2 && h >= GameFacts.screenHeight / 2 ? new Dimension(w, h) : null;
    }

    private void saveWindowSizeIfChanged() {
        if (RunApp.takeWindowSizeChanged() && !runsHidden() && !RunApp.isFullscreen()) {
            Dimension size = RunApp.windowPixels();
            savecookie(WINDOW_WIDTH_COOKIE, String.valueOf(size.width));
            savecookie(WINDOW_HEIGHT_COOKIE, String.valueOf(size.height));
        }
    }

    /** The frame rate limit: FPS_MATCH_DISPLAY, FPS_VRR, FPS_UNLIMITED or a cap. */
    public static int getFpsLimit() {
        return fpsLimit;
    }

    /** The frame cap on battery, 0 for none. */
    public static int getBatteryFps() {
        return batteryFps;
    }

    /** -Dnfm.runahead=true|false, else the saved choice, else on. */
    private void loadRunAhead() {
        String property = System.getProperty("nfm.runahead");
        if (property != null) {
            runAhead = !property.trim().equalsIgnoreCase("false");
        } else {
            runAhead = readcookie(RUN_AHEAD_COOKIE) != 0;
        }
        runAheadChanged = false;
    }

    private void saveRunAheadIfChanged() {
        if (runAheadChanged) {
            runAheadChanged = false;
            savecookie(RUN_AHEAD_COOKIE, runAhead ? "1" : "0");
        }
    }

    private void saveFpsLimitIfChanged() {
        if (fpsLimitChanged) {
            fpsLimitChanged = false;
            int limit = fpsLimit;
            savecookie(FPS_COOKIE, "" + (limit == FPS_MATCH_DISPLAY ? FPS_COOKIE_MATCH_DISPLAY
                    : limit == FPS_VRR ? FPS_COOKIE_VRR : limit));
        }
    }

    /**
     * Copies the current back buffer into an independent image.
     */
    private BufferedImage captureFrame() {
        BufferedImage image = new BufferedImage(GameFacts.screenWidth, GameFacts.screenHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        if (uiScale != 1F) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        g.drawImage(offImage, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
        g.dispose();
        return image;
    }

    /**
     * Asks the render pass for a complete frame and writes it to disk.
     * <p>
     * Copying the back buffer straight from the test thread can catch the game
     * mid-draw, which produces torn screenshots. The request is fulfilled at the
     * end of a presentation pass instead.
     */
    /** The window's inside as {@link #paint} fills it: the picture scaled, and the borders. */
    private BufferedImage captureWindow() {
        BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        paintPicture(g);
        g.dispose();
        return image;
    }

    private void saveFrame(File directory, String name) {
        capturedFrame = null;
        capturedWindow = null;
        capturedGpu = null;
        capturedGpuScene = null;
        capturedGlow = null;
        captureRequested = true;
        long deadline = System.currentTimeMillis() + 2_000L;
        while (captureRequested && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ex) {
                return;
            }
        }
        BufferedImage image = capturedFrame;
        if (image == null) {
            System.out.println("[autotest] no frame for " + name);
            return;
        }
        try {
            ImageIO.write(image, "png", new File(directory, name));
            BufferedImage window = capturedWindow;
            if (window != null) {
                // the picture as the window shows it, when the window is not 1280x720
                ImageIO.write(window, "png", new File(directory, name.replace(".png", "-window.png")));
            }
            BufferedImage gpuScene = capturedGpuScene;
            if (gpuScene != null) {
                // the scene as the graphics card drew it (1280x720 unless the window is larger), to compare with the picture
                ImageIO.write(gpuScene, "png", new File(directory, name.replace(".png", "-gpuscene.png")));
            }
            BufferedImage glow = capturedGlow;
            if (glow != null) {
                // in HDR, what of the scene glows (white glows most)
                ImageIO.write(glow, "png", new File(directory, name.replace(".png", "-glow.png")));
            }
            BufferedImage gpuWindow = capturedGpu;
            if (gpuWindow != null) {
                // the window read back from the graphics card, to compare with the software picture
                ImageIO.write(gpuWindow, "png", new File(directory, name.replace(".png", "-gpu.png")));
            }
        } catch (IOException ex) {
            System.out.println("[autotest] screenshot failed: " + ex);
        }
    }

    /**
     * Scripted smoke test used while developing the frame loop.
     * <p>
     * Enabled with {@code -Dnfm.autotest=<screenshot directory>}. The harness
     * drives the game through its own {@link Control} objects and writes the
     * back buffer to PNG files, so it needs no OS-level keyboard, mouse or
     * screen access and never takes focus away from the desktop.
     */
    private void startAutoTest() {
        final String path = System.getProperty("nfm.autotest");
        if (path == null) {
            return;
        }
        final File directory = new File(path);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            System.out.println("[autotest] cannot create " + directory);
            return;
        }
        Thread thread = new Thread(() -> runAutoTest(directory), "nfm-autotest");
        thread.setDaemon(true);
        thread.start();
    }

    private void runAutoTest(File directory) {
        System.out.println("[autotest] screenshots -> " + directory.getAbsolutePath());
        final long budgetMillis = 150_000L;
        final long startMillis = System.currentTimeMillis();

        long lastMenuPress = 0L;
        long inGameMillis = 0L;
        long lastShot = 0L;
        long phaseSettledAt = startMillis;
        int shots = 0;
        int replayShots = 0;
        Phase lastPhase = null;
        Phase pendingPhaseShot = null;
        boolean pauseRequested = false;
        boolean replayAttempted = false;
        boolean replaySeen = false;
        boolean resumed = false;
        ContO repairCar = null;
        long repairStartedAt = 0L;
        boolean repairChecked = false;
        boolean highlightRequested = false;
        boolean highlightSeen = false;
        int highlightShots = 0;
        /* the settings screen, from the main menu (0 to 3) and the pause menu (4 to 6) */
        int settingsStep = 0;
        int settingsDowns = 0;

        while (System.currentTimeMillis() - startMillis < budgetMillis) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                return;
            }

            Phase phase = ui.fase;
            long now = System.currentTimeMillis();

            if (phase != lastPhase) {
                System.out.println("[autotest] phase " + lastPhase + " -> " + phase + " at " + java.time.LocalTime.now());
                lastPhase = phase;
                phaseSettledAt = now;
                pendingPhaseShot = phase;
            }

            /* Let the phase draw itself before grabbing its screenshot. */
            if (pendingPhaseShot != null && phase == pendingPhaseShot && now - phaseSettledAt >= 300L) {
                saveFrame(directory, String.format("%02d-%s.png", shots++, phase.name().toLowerCase()));
                pendingPhaseShot = null;
                lastShot = now;
            }

            /*
             * Look at the settings screen from the main menu, then from the
             * pause menu: open it, walk down to the sound settings, go back
             * with Escape. Nothing is changed, so nothing is saved.
             */
            if (phase == Phase.MAINMENU && settingsStep == 0 && now - phaseSettledAt >= 600L) {
                ui.opselect = 3;
                u[0].enter = true;
                settingsStep = 1;
                System.out.println("[autotest] opening the settings from the main menu");
            }
            if (phase == Phase.PAUSEMENU && settingsStep == 3 && now - phaseSettledAt >= 600L) {
                ui.opselect = 3;
                u[0].enter = true;
                settingsStep = 4;
                System.out.println("[autotest] opening the settings from the pause menu");
            }
            if (phase == Phase.SETTINGS && (settingsStep == 1 || settingsStep == 4) && now - phaseSettledAt >= 700L) {
                // a few settings chosen in turn, each shown for a moment (the main menu's, then the pause menu's)
                String[] shown = settingsStep == 1 ? new String[] {"Supersampling", "Resolution", "Accelerate", "Video memory"}
                        : new String[] {"Sound system", "HDR audio"};
                int at = settingsDowns / 4;
                if (at >= shown.length) {
                    settingsStep++;
                    settingsDowns = 0;
                } else {
                    if (settingsDowns % 4 == 0) {
                        ui.selectSetting(shown[at]);
                    } else if (settingsDowns % 4 == 3) {
                        saveFrame(directory, String.format("%02d-settings-%s.png", shots++,
                                shown[at].toLowerCase(java.util.Locale.ROOT).replace(' ', '-')));
                    }
                    settingsDowns++;
                }
            } else if (phase == Phase.SETTINGS && (settingsStep == 2 || settingsStep == 5)) {
                SettingsScreen.noteEscape();
                u[0].enter = true;
                settingsStep++;
                System.out.println("[autotest] back from the settings");
            }
            if (phase == Phase.MAINMENU && settingsStep == 3 && ui.opselect == 3) {
                ui.opselect = 0; // Play Game
            }

            boolean menu = phase == Phase.MAINMENU && settingsStep == 3 || phase == Phase.CARSELECT
                    || phase == Phase.STAGESELECT || phase == Phase.PREGAME;
            if (menu && now - lastMenuPress > 700L) {
                u[0].enter = true;
                lastMenuPress = now;
            }

            if (phase == Phase.INGAME) {
                inGameMillis += 100L;

                if (!pauseRequested) {
                    u[0].up = true;
                    // Small steering nudges exercise rotation interpolation without
                    // driving the car off the track or into the sea.
                    long cycle = inGameMillis % 4000L;
                    u[0].right = cycle > 2200L && cycle < 2500L;
                    u[0].left = cycle > 3400L && cycle < 3700L;

                    if (now - lastShot >= 1000L) {
                        saveFrame(directory, String.format("%02d-ingame-%04dms.png", shots++, inGameMillis));
                        lastShot = now;
                    }
                    /*
                     * The countdown eats the first ~7 s of INGAME, and instant
                     * replay needs 300 recorded ticks, so wait a little longer
                     * than the intro before pausing.
                     */
                    if (inGameMillis >= 26_000L) {
                        u[0].up = false;
                        u[0].right = false;
                        u[0].left = false;
                        u[0].enter = true;
                        pauseRequested = true;
                        System.out.println("[autotest] requesting pause");
                    }
                } else if (resumed) {
                    if (now - lastShot >= 1000L) {
                        saveFrame(directory, String.format("%02d-resumed.png", shots++));
                        lastShot = now;
                    }
                    /*
                     * Drive through a virtual repair hoop: the hoop only sets fix,
                     * and the car is replaced once a tick sees the repair
                     * animation reach its last step.
                     */
                    if (inGameMillis >= 28_000L && repairCar == null) {
                        repairCar = sceneObjects[0];
                        repairStartedAt = now;
                        autotestRepairRequested = true;
                        System.out.println("[autotest] entering a repair hoop");
                    }
                    if (repairCar != null && !repairChecked) {
                        if (sceneObjects[0] != repairCar) {
                            System.out.println("[autotest] repair hoop OK: car fixed after " + (now - repairStartedAt) + " ms");
                            repairChecked = true;
                        } else if (now - repairStartedAt > 3_000L) {
                            System.out.println("[autotest] repair hoop FAILED: still repairing after 3 s (fcnt "
                                    + repairCar.fcnt + ", fix " + repairCar.fix + ")");
                            repairChecked = true;
                        }
                    }
                    if (inGameMillis >= 32_000L && !highlightRequested) {
                        autotestHighlightRequested = true;
                        highlightRequested = true;
                        System.out.println("[autotest] finishing the race into the post-race highlight");
                    }
                }
            }

            if (phase == Phase.GAMEHIGHLIGHT) {
                if (!highlightSeen) {
                    /*
                     * Start with the camera low, below the car's height, which is
                     * where the highlight camera used to tip over and stare at the
                     * ground. It climbs back above the car within a few seconds.
                     */
                    Medium.vert = false;
                    Medium.adv = 100;
                    highlightSeen = true;
                }
                if (now - lastShot >= 700L && highlightShots < 20) {
                    System.out.println("[autotest] highlight camera: pitch " + Medium.zy + ", height " + Medium.y
                            + ", adv " + Medium.adv);
                    saveFrame(directory, String.format("%02d-highlight.png", shots++));
                    lastShot = now;
                    highlightShots++;
                }
            }
            if (highlightSeen && phase != Phase.GAMEHIGHLIGHT || highlightShots >= 20) {
                System.out.println("[autotest] done (highlight ended in " + phase + ")");
                break;
            }

            if (phase == Phase.PAUSEMENU && settingsStep == 6) {
                if (!replayAttempted) {
                    // Select INSTANT REPLAY (second entry) without a real keyboard.
                    ui.opselect = 1;
                    u[0].enter = true;
                    replayAttempted = true;
                    System.out.println("[autotest] requesting instant replay");
                } else if (!resumed) {
                    // Either the replay finished or there was not enough data.
                    ui.opselect = 0;
                    u[0].enter = true;
                    resumed = true;
                    System.out.println("[autotest] resuming race (replay seen: " + replaySeen + ")");
                }
            }

            if (phase == Phase.INSTANTREPLAY) {
                replaySeen = true;
                if (now - lastShot >= 1500L && replayShots < 4) {
                    saveFrame(directory, String.format("%02d-replay.png", shots++));
                    lastShot = now;
                    replayShots++;
                }
            }
        }

        System.out.println("[autotest] finishing");
        try {
            Thread.sleep(500L);
        } catch (InterruptedException ignored) {
        }
        System.exit(0);
    }

    @Override
    public void run() {
        if (!runsHidden()) {
            // a test run hidden in the background should stay out of the way of what the machine is doing
            WindowsScheduling.noPowerThrottling();
            WindowsScheduling.joinGamesTask(true);
        }
        rd.setColor(new Color(0, 0, 0));
        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        repaint();
        /*
         * start an example timer
         */
        Utility.startTimer();
        loadFullscreen();
        loadRenderer();
        if (gpuRenderer && IS_WINDOWS) {
            // the graphics card's libraries and shaders get ready while the game loads
            Thread prepare = new Thread(nfm.lit.gpu.GpuDisplay::prepare, "nfm-gpu-prepare");
            prepare.setDaemon(true);
            prepare.start();
        }
        Trackers trackers = new Trackers();
        CheckPoints checkpoints = new CheckPoints();
        GameUi gameUi = new GameUi(rd, this);
        ContO models[] = new ContO[carModels.length + trackModels.length + extraModels.length]; // be sure all your
                                                                                                // arrays get in here
        /*
         * The models load on a thread of their own while the images and sounds
         * load here. They stay in the archive's order on that one thread: sand
         * planes draw from the shape stream as they are made, so the order
         * decides their shapes.
         */
        Throwable[] modelsFailed = new Throwable[1];
        Thread modelLoader = new Thread(() -> {
            try {
                loadbase(models, trackers, gameUi);
            } catch (Throwable ex) {
                modelsFailed[0] = ex;
            }
        }, "nfm-load-models");
        modelLoader.start();
        gameUi.loaddata();
        Record record = new Record();
        try {
            modelLoader.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (modelsFailed[0] != null) {
            throw new IllegalStateException("Loading the models failed", modelsFailed[0]);
        }
        modelsLoaded = true;
        ContO scene[] = new ContO[5000];
        Madness drivers[] = new Madness[51];
        int value = 0;
        do {
            drivers[value] = new Madness(record, gameUi, world, value);
            u[value] = new Control(world);
        } while (++value < 51); // dont touch this
        value = 0;
        float perfFactor = 35F;
        int previewFrames = 80;
        /*
         * stop an example timer
         */
        Utility.stopTimer();
        /**
         * this bit in here reads cookies and set values
         */
        value = readcookie("unlocked");
        if (value >= 1 && value <= GameFacts.numberOfStages) {
            /*
             * Note: that is an L
             */
            gameUi.unlocked = value;
            if (gameUi.unlocked != GameFacts.numberOfStages)
                checkpoints.stage = gameUi.unlocked;
            else
                checkpoints.stage = (int) (Math.random() * 17D) + 1;
            gameUi.opselect = 0;
        }
        value = readcookie("usercar");
        if (value >= 0 && value <= GameFacts.numberOfCars - 1)
            gameUi.sc[0] = value;
        value = readcookie("gameprfact");
        if (value != -1) {
            perfFactor = value;
            previewFrames = 1;
        }

        gameUi.stoploading();
        System.gc();
        sceneObjects = scene;
        racers = drivers;
        modelObjects = models;
        ui = gameUi;
        raceCheckpoints = checkpoints;
        raceRecord = record;
        raceTrackers = trackers;

        loadFpsLimit();
        loadBatteryFps();
        loadVsync();
        loadAudioEffects();
        loadKeys();
        loadRunAhead();
        loadScaleToWindow();
        loadWindowShape();
        loadRenderer();
        loadMultisample();
        loadSupersample();
        loadDynamicResolution();
        loadHdr();
        loadHdrBrightness();
        loadSound();
        loadMusicSeparation();
        loadVolumes();
        loadSmoothEffects();
        loadPerfOverlay();
        startAutoTest();
        session = RaceSession.fromCommandLine(new SessionHost());
        startGamepads();
        final boolean unpaced = session != null && session.unpaced();

        int replayFrame = 0;
        int frames = 0;
        int replayPass = 0;
        int flashLength = 0;
        boolean flashed = false;
        exwist = false;

        /*
         * Fixed simulation cadence, at the rates the original adaptive pacer
         * converged on (see SCENE_TICK_NANOS), so physics, AI and every
         * frame-counted timer keep behaving exactly as they did before.
         */
        final long profileWindowNanos = 2_000_000_000L;

        if (PRESENTER && IS_WINDOWS && presenter == null) {
            presenter = new Presenter();
            presenter.start();
            /*
             * Ahead of the presenter: a tick is a few milliseconds every 53 and
             * the frames wait for its motion, while the presenter, with no frame
             * limit, would take all the time it gets.
             */
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 3);
        }
        long nextTickNanos = System.nanoTime() + tickNanosFor(gameUi.fase);
        long nextRenderNanos = System.nanoTime();
        long lastProfileFlush = nextRenderNanos;
        long lastRenderAt = nextRenderNanos;
        int profileTicks = 0;
        int profileFrames = 0;
        long profileTickNanos = 0L;
        long profileRenderNanos = 0L;
        long profilePresentNanos = 0L;
        // gaps between frames reaching the screen, for the pacing figures in the profile
        long[] profileGaps = new long[4096];
        int profileGapCount = 0;
        long lastShownAt = 0L;

        do {
            boolean ticked = false;
            int ticksThisFrame = 0;

            /*
             * Run every tick that is due, so a slow frame delays the picture but
             * never slows the world down. The cap stops a long stall from
             * running a burst of ticks with nothing on screen.
             */
            while (ticksThisFrame < MAX_TICKS_PER_FRAME && (unpaced || System.nanoTime() - nextTickNanos >= 0L)) {
                long now = System.nanoTime();
                if (now - nextTickNanos > MAX_TICK_LAG_NS) {
                    /*
                     * Debugger stop, window drag or a long GC pause. Drop the
                     * backlog instead of fast-forwarding the whole world.
                     */
                    nextTickNanos = now;
                }
                currentTickNanos = tickNanosFor(gameUi.fase);
                tickStartNanos = nextTickNanos;
                nextTickNanos += currentTickNanos;
                ticked = true;
                retainedValid = false; // the next frame records the scene for this tick
                keptStale = true;
                keptBlocked = false;
                ticksThisFrame++;
                profileTicks++;
                if (backBufferStale && !isScenePhase(gameUi.fase)) {
                    syncBackBuffer(); // menus and overlays draw on top of the last frame
                }
                saveFpsLimitIfChanged();
                saveBatteryFpsIfChanged();
                saveVsyncIfChanged();
                saveKeysIfChanged();
                saveAudioEffectsIfChanged();
                fitPictureShape(now);
                fitUiResolution(now);
                ticksRun++;
                saveRunAheadIfChanged();
                saveScaleToWindowIfChanged();
                saveRendererIfChanged();
                saveMultisampleIfChanged();
                saveSupersampleIfChanged();
                saveDynamicResolutionIfChanged();
                saveHdrIfChanged();
                applySound();
                saveMusicSeparationIfChanged();
                saveVolumesIfChanged();
                saveGamepadIfChanged();
                saveSmoothEffectsIfChanged();
                if (PerfOverlay.takeChanged()) {
                    savecookie(PERF_OVERLAY_COOKIE, String.valueOf(PerfOverlay.saved()));
                }
                saveFullscreenIfChanged();
                saveWindowSizeIfChanged();
                if (modelReloadWanted && gameUi.fase == Phase.MAINMENU) {
                    modelReloadWanted = false;
                    loadbase(models, trackers, gameUi); // the next race and car select use them
                    HLogger.info("Models loaded again" + (GameFiles.names().isEmpty() ? "" : " with mods: "
                            + GameFiles.names()));
                }
                if (session != null) {
                    session.beforeTick();
                }
                if (gameUi.fase != Phase.INGAME) {
                    releaseRaceInput();
                }

            if (gameUi.fase == Phase.LOADING) {
                if (mouses == 1)
                    frames = 800;
                if (frames < 800) {
                    gameUi.clicknow();
                    frames++;
                } else {
                    frames = 0;
                    gameUi.fase = Phase.AWAITLOADDISMISSAL;
                    gameUi.sm.play("powerup");
                    mouses = 0;
                    lostfcs = false;
                }
            }
            if (gameUi.fase == Phase.AWAITLOADDISMISSAL)
                if (frames < 100 && splashScreenState) {
                    gameUi.rad(frames);
                    catchlink(0, gameUi);
                    if (mouses == 2)
                        mouses = 0;
                    if (mouses == 1)
                        mouses = 2;
                    frames++;
                } else {
                    frames = 0;
                    gameUi.fase = Phase.MAINMENU;
                    mouses = 0;
                    u[0].falseo();
                }
            if (gameUi.fase == Phase.CARSELECTTRIGGER)
                if (frames < 2) {
                    rd.setColor(new Color(0, 0, 0));
                    rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                    frames++;
                } else {
                    gameUi.inishcarselect();
                    frames = 0;
                    gameUi.fase = Phase.CARSELECT;
                    mouses = 0;
                }
            if (gameUi.fase == Phase.CREDITS) {
                gameUi.credits(u[0]);
                if (gameUi.flipo == 102) {
                    syncBackBuffer();
                    rd.drawImage(gameUi.credsnap(pictureAtGameSize()), 0, 0, null);
                }
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (gameUi.flipo <= 100)
                    catchlink(0, gameUi);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            // if (gameUi.fase == Phase.DRM0) {
            //     gameUi.fase = Phase.DRMSCREEN;
            //     String regex = "^([a-zA-Z0-9.-]+):(\\d{1,5})$";
            //     Pattern pattern = Pattern.compile(regex);
            //     Matcher matcher = pattern.matcher(gameUi.serverip + ":" + gameUi.serverport);
    
            //     if (matcher.matches()) {
            //         String host = matcher.group(1);
            //         int port = Integer.parseInt(matcher.group(2));
    
            //         if (port >= 0 && port <= 65535) {
            //             HLogger.info("Connecting to " + host + " on port " + port + "...");
    
            //             try {
            //                 gameUi.socket = new Socket(host, port);
            //                 HLogger.info("Connected to the server");
    
            //                 gameUi.serverresponse = new BufferedReader(new InputStreamReader(gameUi.socket.getInputStream()));

            //                 PrintWriter out = new PrintWriter(gameUi.socket.getOutputStream(), true); // true = auto-flush
            //                 String hwid = HWID.getHWID();
            //                 String hashed = HWID.hashHWID(hwid);

            //                 out.println(hashed);  // <- you send this


            //                 gameUi.serverresponse = new BufferedReader(new InputStreamReader(gameUi.socket.getInputStream()));
            //                 gameUi.serverMessage = gameUi.serverresponse.readLine();
            //                 HLogger.info(gameUi.serverMessage);

            //                 out.close();
    
            //             } catch (java.net.ConnectException e) {
            //                 gameUi.serverMessage = e.getMessage();
            //                 HLogger.info(e.getMessage());
            //             } catch (IOException e) {
            //                 HLogger.info("An error occurred:\n" + e.toString());
            //             } finally {
            //                 try {
            //                     if (gameUi.serverresponse != null) gameUi.serverresponse.close();
            //                     if (gameUi.socket != null) gameUi.socket.close();
            //                 } catch (IOException e) {
            //                     HLogger.info("An error occurred while closing connection:\n" + e.toString());
            //                 }
            //             }
    
            //         } else {
            //             HLogger.info("Port must be between 0 and 65535.");
            //         }
            //     } else {
            //         HLogger.info("Invalid host:port format.");
            //     }
            // }
            // if (gameUi.fase == Phase.DRMSCREEN) {
            //     repaint();
            //     gameUi.drmcheck();
            //     if (gameUi.serverMessage.equals("Authenticated")) {
            //         gameUi.fase = Phase.LOADING;
            //     }
            // }
            if (gameUi.fase == Phase.MAINMENU) {
                gameUi.maini(u[0], checkpoints, drivers, models, scene);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.INSTRUCTIONS) {
                gameUi.inst(u[0]);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            raceUnderSettings = gameUi.settingsOverRace();
            if (gameUi.fase == Phase.SETTINGS) {
                if (raceUnderSettings) {
                    // the paused race stands still behind the screen, drawn with the settings as they are
                    snapshotFrameState();
                    beginOverlayTick();
                }
                gameUi.settings(u[0], raceUnderSettings);
                if (raceUnderSettings) {
                    endOverlayTick();
                }
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.POSTGAMEHANDOVER) {
                gameUi.fase = Phase.POSTGAME;
            }
            if (gameUi.fase == Phase.SAVEGAME) { // save the fucking game properly

                if (checkpoints.stage == gameUi.unlocked && gameUi.winner
                        && gameUi.unlocked != GameFacts.numberOfStages + 1)
                    savecookie("unlocked", "" + gameUi.unlocked);
                savecookie("gameprfact", "" + (int) perfFactor);
                savecookie("usercar", "" + gameUi.sc[0]);

                gameUi.fase = Phase.MAINMENU;
            }
            if (gameUi.fase == Phase.POSTGAME) {
                gameUi.finish(checkpoints, models, u[0]);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (checkpoints.stage == GameFacts.numberOfStages && gameUi.winner)
                    catchlink(1, gameUi);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.CARSELECT) {
                gameUi.carselect(u[0], models, drivers[0]);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.PREGAME) {
                gameUi.musicomp(checkpoints.stage, u[0]);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.SELECTEDCARSAVE) {
                savecookie("usercar", "" + gameUi.sc[0]);

                for (int x = 0; x < GameFacts.numberOfPlayers; x++) {
                    drivers[x].stat = new Stat(gameUi.sc[x]);
                }
                gameUi.fase = Phase.PREGAMEMUSIC;
            }
            if (gameUi.fase == Phase.PREGAMEMUSIC) {
                gameUi.loadmusic(checkpoints.stage, previewFrames);
            }
            if (gameUi.fase == Phase.LOCKEDSTAGE) {
                gameUi.cantgo(u[0]);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.ERRORLOADINGSTAGE) {
                gameUi.loadingfailed(checkpoints, u[0], stageError);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.NPLAYERSCHECK) {
                gameUi.carspergame(checkpoints);
            }
            if (gameUi.fase == Phase.STAGESELECTTRIGGER) {
                gameUi.loadingstage(checkpoints.stage);
                loadstage(scene, models, trackers, checkpoints, gameUi, drivers, record, false);
                u[0].falseo();
            }
            if (gameUi.fase == Phase.LOADSTAGE) { // for custom stage loading
                repaint();
                loadstage(scene, models, trackers, checkpoints, gameUi, drivers, record, true);
                gameUi.fase = Phase.STAGESELECT;
            }
            if (gameUi.fase == Phase.LOADSTAGE2) { // for custom stage loading
                repaint();
                loadstage(scene, models, trackers, checkpoints, gameUi, drivers, record, true);
                gameUi.loadmusic(checkpoints.stage, previewFrames);
            }
            if (gameUi.fase == Phase.STAGESELECT) {
                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                gameUi.trackbg(true);
                try (Medium.ShownView shown = Medium.shownView()) {
                    Medium.d(rd);
                }
                Medium.aroundTrack(checkpoints);
                int farCount = 0;
                int far[] = new int[200];
                try (Medium.ShownView shown = Medium.shownView()) {
                for (int piece = GameFacts.numberOfPlayers; piece < notb; piece++)
                    if (scene[piece].dist != 0) {
                        far[farCount] = piece;
                        farCount++;
                    } else {
                        scene[piece].d(rd);
                    }

                int order[] = new int[farCount];
                for (int n = 0; n < farCount; n++)
                    order[n] = 0;

                for (int first = 0; first < farCount; first++) {
                    for (int second = first + 1; second < farCount; second++)
                        if (scene[far[first]].dist != scene[far[second]].dist) {
                            if (scene[far[first]].dist < scene[far[second]].dist)
                                order[first]++;
                            else
                                order[second]++;
                        } else if (second > first)
                            order[first]++;
                        else
                            order[second]++;

                }

                for (int rank = 0; rank < farCount; rank++) {
                    for (int slot = 0; slot < farCount; slot++)
                        if (order[slot] == rank)
                            scene[far[slot]].d(rd);

                }
                }

                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
                gameUi.stageselect(checkpoints, u[0]);
            }
            if (gameUi.fase == Phase.DRAWENVIRONMENT) {
                try (Medium.ShownView shown = Medium.shownView()) {
                    Medium.d(rd);
                    drawWorldObjects(rd); // scene is sceneObjects
                }
                Medium.follow(scene[0], 0, 0);
                gameUi.hipnoload(checkpoints.stage, false);
                if (previewFrames != 0) {
                    previewFrames--;
                } else {
                    u[0].enter = false;
                    u[0].handb = false;

                    setCursor(new Cursor(0));
                    gameUi.fase = Phase.PREGAME;
                }
            }
            if (autotestHighlightRequested && gameUi.fase == Phase.INGAME && gameUi.starcnt == 0) {
                /*
                 * Smoke test only: end the race as if it had just been won after a
                 * stunt, so the post-race highlight gets exercised.
                 */
                autotestHighlightRequested = false;
                record.cotchinow(0);
                gameUi.looped = 0;
                gameUi.fase = Phase.CAUGHTHIGHLIGHT;
            }
            if (autotestRepairRequested && gameUi.fase == Phase.INGAME && gameUi.starcnt == 0) {
                // Smoke test only: what a repair hoop does to a visible car.
                autotestRepairRequested = false;
                scene[0].fix = true;
            }
            if (gameUi.fase == Phase.INGAME) {
                latchRaceInput();
                if (session != null) {
                    session.ingameInput();
                }
                /*
                 * Snapshot the transforms the presentation pass interpolates from,
                 * then advance the simulation. Everything on screen is drawn by
                 * the presentation pass, at the display rate.
                 */
                snapshotFrameState();

                /*
                 * The intro fly-by draws no HUD. Clear the layer so a previous
                 * race's HUD cannot bleed through during the camera sweep.
                 */
                if (gameUi.starcnt >= 38) {
                    hudG.setComposite(AlphaComposite.Clear);
                    hudG.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                    hudG.setComposite(AlphaComposite.SrcOver);
                }

                boolean aheadComparable = aheadValid && aheadTick == raceTicks && aheadKeys == latchedKeys;
                advanceRace();
                if (aheadComparable) {
                    // same state, same keys: the race must land where the run-ahead tick did
                    profileAheadChecked++;
                    if (!aheadPoses.matchLive(Math.min(GameFacts.numberOfPlayers, prevCarX.length))) {
                        profileAheadMissed++;
                    }
                }
                raceTicks++;

                if (session != null) {
                    session.ingameTickEnded();
                }
            }
            if (gameUi.fase == Phase.INSTANTREPLAY) {
                /*
                 * Drawn by the presentation pass, like a race. The caption goes
                 * to the HUD layer.
                 */
                snapshotFrameState();
                if (replayFrame == 0) {
                    prevFrameValid = false;
                }
                beginOverlayTick();
                if (replayFrame == 0) {
                    int racer = 0;
                    do {
                        record.ocar[racer] = new ContO(scene[racer], 0, 0, 0, 0);
                        scene[racer] = new ContO(record.car[0][racer], 0, 0, 0, 0);
                    } while (++racer < GameFacts.numberOfPlayers);
                }
                drawSimulationStep();

                if (u[0].enter || u[0].handb || mouses == 1) {
                    replayFrame = 299;
                    u[0].enter = false;
                    u[0].handb = false;
                    mouses = 0;
                }
                int racer = 0;
                do {
                    if (record.fix[racer] == replayFrame)
                        if (scene[racer].dist == 0)
                            scene[racer].fcnt = 8;
                        else
                            scene[racer].fix = true;
                    if (scene[racer].fcnt == 7 || scene[racer].fcnt == 8) {
                        scene[racer] = new ContO(models[drivers[racer].cn], 0, 0, 0, 0);
                        record.cntdest[racer] = 0;
                    }
                    if (replayFrame == 299)
                        scene[racer] = new ContO(record.ocar[racer], 0, 0, 0, 0);
                    record.play(scene[racer], drivers[racer], racer, replayFrame);
                } while (++racer < GameFacts.numberOfPlayers);
                if (++replayFrame == 300) {
                    replayFrame = 0;
                    gameUi.fase = Phase.PAUSETRIGGER;
                } else {
                    gameUi.replyn();
                }
                Medium.around(scene[0], false);
                endOverlayTick();
            }
            if (gameUi.fase == Phase.CAUGHTHIGHLIGHT) {
                if (record.hcaught && record.wasted == 0 && record.whenwasted != 229 && checkpoints.stage <= 2
                        && gameUi.looped != 0)
                    record.hcaught = false;
                if (record.hcaught) {
                    Medium.vert = Medium.fxRandom() <= 0.45000000000000001D;
                    Medium.adv = (int) (900F * Medium.fxRandom());
                    Medium.vxz = (int) (360F * Medium.fxRandom());
                    replayFrame = 0;
                    gameUi.fase = Phase.GAMEHIGHLIGHT;
                    frames = 0;
                    replayPass = 0;
                } else {
                    replayFrame = -2;
                    gameUi.fase = Phase.POSTGAMEFADEOUT;
                }
            }
            if (gameUi.fase == Phase.GAMEHIGHLIGHT) {
                /*
                 * Drawn by the presentation pass, like a race. Captions and the
                 * black and white flashes go to the HUD layer.
                 */
                snapshotFrameState();
                if (replayFrame == 0) {
                    prevFrameValid = false;
                }
                beginOverlayTick();
                if (replayFrame == 0) {
                    if (record.wasted == 0) {
                        if (record.whenwasted == 229) {
                            flashLength = 67;
                            Medium.vxz += 90;
                        } else {
                            flashLength = (int) (Medium.fxRandom() * 4F);
                            if (flashLength == 1 || flashLength == 3)
                                flashLength = 69;
                            if (flashLength == 2 || flashLength == 4)
                                flashLength = 30;
                        }
                    } else if (record.closefinish != 0 && replayPass != 0)
                        Medium.vxz += 90;
                    int racer = 0;
                    do
                        scene[racer] = new ContO(record.starcar[racer], 0, 0, 0, 0);
                    while (++racer < GameFacts.numberOfPlayers);
                }
                drawSimulationStep();

                int racer = 0;
                do {
                    if (record.hfix[racer] == replayFrame)
                        if (scene[racer].dist == 0)
                            scene[racer].fcnt = 8;
                        else
                            scene[racer].fix = true;
                    if (scene[racer].fcnt == 7 || scene[racer].fcnt == 8) {
                        scene[racer] = new ContO(models[drivers[racer].cn], 0, 0, 0, 0);
                        record.cntdest[racer] = 0;
                    }
                    record.playh(scene[racer], drivers[racer], racer, replayFrame);
                } while (++racer < GameFacts.numberOfPlayers);
                if (replayPass == 2 && replayFrame == 299)
                    u[0].enter = true;
                if (u[0].enter || u[0].handb) {
                    gameUi.fase = Phase.POSTGAMEFADEOUT;
                    u[0].enter = false;
                    u[0].handb = false;
                    replayFrame = -7;
                } else {
                    gameUi.levelhigh(record.wasted, record.whenwasted, record.closefinish, replayFrame, checkpoints.stage);
                    if (replayFrame == 0 || replayFrame == 1 || replayFrame == 2) {
                        rd.setColor(new Color(0, 0, 0));
                        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                    }
                    if (record.wasted != 0) {
                        if (record.closefinish == 0) {
                            if (frames == 9 || frames == 11) {
                                rd.setColor(new Color(255, 255, 255));
                                rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                            }
                            if (frames == 0)
                                Medium.around(scene[0], false);
                            if (frames > 0 && frames < 20)
                                Medium.transaround(scene[0], scene[record.wasted], frames);
                            if (frames == 20)
                                Medium.around(scene[record.wasted], false);
                            if (replayFrame > record.whenwasted && frames != 20)
                                frames++;
                            if ((frames == 0 || frames == 20) && ++replayFrame == 300) {
                                replayFrame = 0;
                                frames = 0;
                                replayPass++;
                            }
                        } else if (record.closefinish == 1) {
                            if (frames == 0)
                                Medium.around(scene[0], false);
                            if (frames > 0 && frames < 20)
                                Medium.transaround(scene[0], scene[record.wasted], frames);
                            if (frames == 20)
                                Medium.around(scene[record.wasted], false);
                            if (frames > 20 && frames < 40)
                                Medium.transaround(scene[record.wasted], scene[0], frames - 20);
                            if (frames == 40)
                                Medium.around(scene[0], false);
                            if (frames > 40 && frames < 60)
                                Medium.transaround(scene[0], scene[record.wasted], frames - 40);
                            if (frames == 60)
                                Medium.around(scene[record.wasted], false);
                            if (replayFrame > 160 && frames < 20)
                                frames++;
                            if (replayFrame > 230 && frames < 40)
                                frames++;
                            if (replayFrame > 280 && frames < 60)
                                frames++;
                            if ((frames == 0 || frames == 20 || frames == 40 || frames == 60) && ++replayFrame == 300) {
                                replayFrame = 0;
                                frames = 0;
                                replayPass++;
                            }
                        } else {
                            if (frames == 0)
                                Medium.around(scene[0], false);
                            if (frames > 0 && frames < 20)
                                Medium.transaround(scene[0], scene[record.wasted], frames);
                            if (frames == 20)
                                Medium.around(scene[record.wasted], false);
                            if (frames > 20 && frames < 40)
                                Medium.transaround(scene[record.wasted], scene[0], frames - 20);
                            if (frames == 40)
                                Medium.around(scene[0], false);
                            if (frames > 40 && frames < 60)
                                Medium.transaround(scene[0], scene[record.wasted], frames - 40);
                            if (frames == 60)
                                Medium.around(scene[record.wasted], false);
                            if (frames > 60 && frames < 80)
                                Medium.transaround(scene[record.wasted], scene[0], frames - 60);
                            if (frames == 80)
                                Medium.around(scene[0], false);
                            if (replayFrame > 90 && frames < 20)
                                frames++;
                            if (replayFrame > 160 && frames < 40)
                                frames++;
                            if (replayFrame > 230 && frames < 60)
                                frames++;
                            if (replayFrame > 280 && frames < 80)
                                frames++;
                            if ((frames == 0 || frames == 20 || frames == 40 || frames == 60 || frames == 80) && ++replayFrame == 300) {
                                replayFrame = 0;
                                frames = 0;
                                replayPass++;
                            }
                        }
                    } else {
                        if (flashLength == 67 && (frames == 3 || frames == 31 || frames == 66)) {
                            rd.setColor(new Color(255, 255, 255));
                            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                        }
                        if (flashLength == 69 && (frames == 3 || frames == 5 || frames == 31 || frames == 33 || frames == 66 || frames == 68)) {
                            rd.setColor(new Color(255, 255, 255));
                            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                        }
                        if (flashLength == 30 && frames >= 1 && frames < 30)
                            if (frames % (int) (2.0F + Medium.fxRandom() * 3F) == 0 && !flashed) {
                                rd.setColor(new Color(255, 255, 255));
                                rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                                flashed = true;
                            } else {
                                flashed = false;
                            }
                        if (replayFrame > record.whenwasted && frames != flashLength)
                            frames++;
                        Medium.around(scene[0], false);
                        if ((frames == 0 || frames == flashLength) && ++replayFrame == 300) {
                            replayFrame = 0;
                            frames = 0;
                            replayPass++;
                        }
                    }
                }
                endOverlayTick();
            }
            if (gameUi.fase == Phase.POSTGAMEFADEOUT) {
                if (replayFrame <= 0) {
                    rd.drawImage(gameUi.mdness, 224, 30, null);
                    rd.drawImage(gameUi.dude[0], 70, 10, null);
                }
                if (replayFrame >= 0) {
                    syncBackBuffer();
                    gameUi.fleximage(pictureAtGameSize(), replayFrame, checkpoints.stage);
                }
                replayFrame++;
                if (checkpoints.stage == GameFacts.numberOfStages && replayFrame == 10)
                    gameUi.fase = Phase.POSTGAME;
                if (replayFrame == 12)
                    gameUi.fase = Phase.POSTGAME;
            }
            if (gameUi.fase == Phase.PAUSETRIGGER) {
                repaint();
                syncBackBuffer();
                gameUi.pauseimage(pictureAtGameSize());
                gameUi.fase = Phase.PAUSEMENU;
                mouses = 0;
            }
            if (gameUi.fase == Phase.PAUSEMENU) {
                gameUi.pausedgame(checkpoints.stage, u[0], record);
                if (replayFrame != 0)
                    replayFrame = 0;
                gameUi.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (gameUi.fase == Phase.NOTENOUGHREPLAYDATA) {
                gameUi.cantreply();
                if (++replayFrame == 150 || u[0].enter || u[0].handb || mouses == 1) {
                    gameUi.fase = Phase.PAUSEMENU;
                    mouses = 0;
                    u[0].enter = false;
                    u[0].handb = false;
                }
            }
            if (lostfcs && gameUi.fase != Phase.DRAWENVIRONMENT && gameUi.fase != Phase.LOADING) {
                if (gameUi.fase == Phase.INGAME)
                    u[0].enter = false;
                else
                    gameUi.nofocus();
                if (mouses == 1 || mouses == 2)
                    lostfcs = false;
            }
            gameUi.playsounds(drivers[0], u[0], checkpoints.stage);
            publishSound();
            /*
             * The original loop passed the adaptive sleep target here. Feeding a
             * constant keeps the fog distance at the stage's intended value.
             */
            Medium.adjustFade(currentTickNanos / 1_000_000F);
            if (isScenePhase(gameUi.fase)) {
                /*
                 * The legacy loop clamped the camera in Medium.d() once per tick;
                 * the presentation pass draws from a copy, so do it here. Without
                 * this the highlight camera's pitch runs away and looks straight
                 * down at the ground for seconds at a time.
                 */
                Medium.clampView();
            }
            if (exwist) {
                rd.dispose();
                gameUi.stopallnow();
                System.gc();
                gamer = null;
                return;
            }

            // if (gameUi.devtriggered) {

            // this could be useful for discord rich presence
            switch (gameUi.fase) {
                case INGAME:
                    gameState = "In game\nStage " + checkpoints.stage + ": " + checkpoints.name +
                    "\nPlayers: " + GameFacts.numberOfPlayers;
                    gameStateID = 0;
                    break;
                case STAGESELECT:
                    gameState = "Selecting a Stage";
                    gameStateID = 1;
                    break;
                case ERRORLOADINGSTAGE:
                    gameState = "Selecting a Stage";
                    gameStateID = 3;
                    break;
                case CARSELECT:
                    gameState = "Selecting a Car";
                    gameStateID = 7;
                    break;
                case MAINMENU:
                    gameState = "In main menu";
                    gameStateID = 10;
                    break;
                case INSTRUCTIONS:
                    gameState = "Reading game instructions";
                    gameStateID = 11;
                    break;
                default:
                    gameState = "Unknown State";
                    gameStateID = -1738;
                    break;
            }

                if (gameUi.fase != Phase.LOADING) {
                    // the performance overlay: on the HUD layer over a race, on the picture elsewhere
                    PerfOverlay.draw(isScenePhase(gameUi.fase) ? hudG : rd, isScenePhase(gameUi.fase));
                }
                profileTickNanos += System.nanoTime() - now;
            } // end of simulation tick

            /*
             * Presentation pass.
             *
             * Menus and the other 2D phases draw themselves while the tick runs,
             * so they are only put on screen when a tick produced a new picture.
             * Races, replays and highlights are redrawn here at the display rate
             * from transforms interpolated between the last two simulation
             * states, with the HUD layer composited on top.
             */
            long frameNow = System.nanoTime();
            long renderNanos = frameIntervalNanos(frameNow);
            boolean scenePhase = isScenePhase(gameUi.fase);
            boolean frameDue = renderNanos == 0L || frameNow - nextRenderNanos >= 0L;
            /*
             * Races on the graphics card are drawn by the presenter on its own
             * thread, at the display's rate, from the scene kept for the tick:
             * this thread only keeps it up to date, so a tick never holds a
             * frame back.
             */
            boolean presented = presenterDraws(scenePhase, unpaced) && handOverKept();
            if (presenter != null) {
                presenter.drawRaces(presented);
            }
            if (!presented) {
                keptStale = true; // the presenter starts again from a scene handed over then
            }
            if (!unpaced && !presented && (scenePhase ? frameDue : ticked)) {
                if (renderNanos > 0L) {
                    nextRenderNanos += renderNanos;
                    if (frameNow - nextRenderNanos > renderNanos * 4L || nextRenderNanos - frameNow > renderNanos) {
                        // fell far behind, or the limit was raised: restart the schedule
                        nextRenderNanos = frameNow + renderNanos;
                    }
                }

                gpuSceneFrame = scenePhase && GPU_SCENE && gpuActive && !gameUi.debugmode;
                if (scenePhase) {
                    double elapsed = (double) (frameNow - tickStartNanos) / (double) currentTickNanos;
                    if (!prevFrameValid || elapsed >= 1D) {
                        renderAlpha = 1F;
                    } else if (elapsed <= 0D) {
                        renderAlpha = 0F;
                    } else {
                        renderAlpha = (float) elapsed;
                    }

                    if (MOTION_TEST != null) {
                        motionTest();
                    }
                    shownRecorder = sceneRecorder;
                    if (aheadPossible()) {
                        long aheadStart = System.nanoTime();
                        if (runAheadTick()) {
                            profileAheadNanos += System.nanoTime() - aheadStart;
                            profileAheads++;
                            retainedValid = false;
                        }
                        if (!drawRetained(true, renderAlpha)) {
                            renderAheadScene(renderAlpha);
                        }
                    } else {
                        aheadValid = false;
                        if (!drawRetained(false, renderAlpha)) {
                            renderIngameScene(renderAlpha);
                        }
                    }

                    if (gpuSceneFrame && shownRecorder.unsupported()) {
                        shownRecorder.replay(rd); // drawing the card does not do: this frame in software
                        gpuSceneFrame = false;
                    }
                    if (gpuSceneFrame) {
                        finishGpuScene();
                    } else {
                        if (hudImage != null) {
                            rd.drawImage(hudImage, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
                        }
                        if (pendingWhiteFlash) {
                            rd.setColor(new Color(255, 255, 255));
                            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                            pendingWhiteFlash = false;
                        }
                        backBufferStale = false;
                    }
                }

                boolean capture = captureRequested;
                if (capture) {
                    syncBackBuffer(); // the software picture of this frame, to compare with the card
                    capturedFrame = captureFrame();
                    capturedWindow = getWidth() != GameFacts.screenWidth || getHeight() != GameFacts.screenHeight
                            ? captureWindow() : null;
                }

                if (gameUi.fase != Phase.LOADING && gameUi.debugmode) {
                    gameUi.gameMetrics.addFrameTimeSample((int) ((frameNow - lastRenderAt) / 1_000_000L));
                    gameUi.gameMetrics.render(rd);
                }
                lastRenderAt = frameNow;
                profileFrames++;

                long presentStart = System.nanoTime();
                presentFrame(capture);
                gpuSceneFrame = false;
                if (capture) {
                    captureRequested = false; // after presenting: the GPU read-back comes from there
                }
                long presentEnd = System.nanoTime();
                if (lastShownAt != 0L && profileGapCount < profileGaps.length) {
                    profileGaps[profileGapCount++] = presentEnd - lastShownAt;
                }
                lastShownAt = presentEnd;
                profilePresentNanos += presentEnd - presentStart;
                profileRenderNanos += presentEnd - frameNow;
            }

            if (PROFILE_LOOP && frameNow - lastProfileFlush >= profileWindowNanos) {
                double seconds = (frameNow - lastProfileFlush) / 1e9D;
                System.out.printf("[loop] sim %.1f Hz (%.1f ms/tick) | render %.1f fps (%.1f ms/frame, %.1f to show"
                        + " on %s%s) | run-ahead %s (%d, %.1f ms each; %d checked, %d missed) | limit %s | %s%n",
                        profileTicks / seconds, profileTicks == 0 ? 0D : profileTickNanos / 1e6D / profileTicks,
                        profileFrames / seconds, profileFrames == 0 ? 0D : profileRenderNanos / 1e6D / profileFrames,
                        profileFrames == 0 ? 0D : profilePresentNanos / 1e6D / profileFrames,
                        gpuActive ? "gpu" : "software",
                        gpu != null && GPU_SCENE && (presenter == null || !presenter.drawingRaces())
                                ? "; last scene " + gpu.sceneStatistics() : "",
                        runAhead ? "on" : "off", profileAheads,
                        profileAheads == 0 ? 0D : profileAheadNanos / 1e6D / profileAheads,
                        profileAheadChecked, profileAheadMissed, describeFpsLimit(), gameUi.fase);
                if (profileGapCount > 0) {
                    long[] gaps = Arrays.copyOf(profileGaps, profileGapCount);
                    Arrays.sort(gaps);
                    long median = gaps[gaps.length / 2];
                    int hitches = 0;
                    for (long gap : gaps) {
                        if (gap > median * 3 / 2 + 2_000_000L) {
                            hitches++;
                        }
                    }
                    if (profileRecords > 0) {
                        System.out.printf("[kept] scene recorded %d times (%.1f ms each); placed on this thread for %d"
                                + " frames (%.2f ms each)%n",
                                profileRecords, profileRecordNanos / 1e6D / profileRecords, profilePlaces,
                                profilePlaces == 0 ? 0D : profilePlaceNanos / 1e6D / profilePlaces);
                    }
                    if (profileProjects > 0) {
                        System.out.printf("[kept] made ready for the card %d times (%.2f ms each)%n", profileProjects,
                                profileProjectNanos / 1e6D / profileProjects);
                    }
                    System.out.printf("[pace] frame gaps: median %.1f ms, 99%% %.1f ms, longest %.1f ms; %d of %d frames came late%n",
                            median / 1e6D, gaps[Math.min(gaps.length - 1, gaps.length * 99 / 100)] / 1e6D,
                            gaps[gaps.length - 1] / 1e6D, hitches, gaps.length);
                }
                profileGapCount = 0;
                profileRecords = 0;
                profileRecordNanos = 0L;
                profileProjects = 0;
                profileProjectNanos = 0L;
                profilePlaces = 0;
                profilePlaceNanos = 0L;
                profileAheads = 0;
                profileAheadChecked = 0;
                profileAheadMissed = 0;
                profileAheadNanos = 0L;
                profileTicks = 0;
                profileFrames = 0;
                profileTickNanos = 0L;
                profileRenderNanos = 0L;
                profilePresentNanos = 0L;
                lastProfileFlush = frameNow;
            }

            /*
             * Sleep until the next tick or frame is due, on the high-resolution
             * timer where Windows has one (see PreciseSleep). With no frame
             * limit a race redraws continuously and only yields.
             */
            if (unpaced) {
                continue; // a verify or corpus run: next tick right away
            }
            long wakeAt = nextTickNanos;
            if (presenter != null && presenter.drawingRaces()) {
                wakeAt = Math.min(wakeAt - System.nanoTime(), KEY_CHECK_NANOS) + System.nanoTime();
            } else if (isScenePhase(gameUi.fase)) {
                if (renderNanos == 0L) {
                    Thread.yield();
                    continue;
                } else if (nextRenderNanos - wakeAt < 0L) {
                    wakeAt = nextRenderNanos;
                }
            }
            try {
                frameSleep.sleepUntil(wakeAt);
            } catch (InterruptedException ignored) {
                // shutdown is signalled through exwist, not interrupts
            }
        } while (true);
    }

    public void init() {
        GameInput.attach(this);
        GameFiles.setUp(runsHidden());
        /*
         * load some fonts
         */
        new FontHandler();

        loadUiScale();
        makeBuffers(GameFacts.screenWidth, GameFacts.screenHeight);
    }

    /**
     * Makes the picture the menus are drawn in and the HUD layer over races,
     * {@code width} x {@code height} pixels: the game's 1280x720, or larger
     * for the UI resolution. Their graphics are scaled to it, so everything
     * is still drawn in the game's 1280x720 coordinates; lettering and lines
     * come out sharp at the larger size, and the art is drawn scaled up.
     * What the old ones held is carried over, scaled.
     */
    private void makeBuffers(int width, int height) {
        Image oldPicture = offImage;
        Image oldHud = hudImage;
        offImage = createImage(width, height);
        /*
         * Must be a real ARGB buffer: Component.createImage() returns an opaque
         * display-compatible image, which would hide the scene behind the HUD.
         */
        hudImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        uiScale = width / (float) GameFacts.screenWidth;
        if (offImage != null) {
            sg = offImage.getGraphics();
            rd = ((Graphics2D) sg);
            rd.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            rd.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            rd.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (width != GameFacts.screenWidth || height != GameFacts.screenHeight) {
                rd.scale(width / (double) GameFacts.screenWidth, height / (double) GameFacts.screenHeight);
                rd.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            }
            if (oldPicture != null) {
                rd.drawImage(oldPicture, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
            }
        }
        if (hudImage != null) {
            hudG = (Graphics2D) hudImage.getGraphics();
            hudG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hudG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hudG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            hudG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (width != GameFacts.screenWidth || height != GameFacts.screenHeight) {
                hudG.scale(width / (double) GameFacts.screenWidth, height / (double) GameFacts.screenHeight);
                hudG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            }
            if (oldHud != null) {
                hudG.setComposite(AlphaComposite.Src);
                hudG.drawImage(oldHud, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
                hudG.setComposite(AlphaComposite.SrcOver);
            }
        }
    }

    /* ------------------------------------------------------------ UI resolution */

    /** The UI resolution: Auto (the size the picture is shown at), else this many times 1280x720. */
    public static final int UI_SCALE_AUTO = 0;
    public static final int UI_SCALE_MOST = 4;
    private static final String UI_SCALE_COOKIE = "uiscale";
    private static volatile int uiScaleSetting = UI_SCALE_AUTO;
    private static volatile boolean uiScaleSettingChanged;
    /** How many times the game's 1280x720 the picture is drawn at now (game thread). */
    private float uiScale = 1F;
    private static volatile String uiResolutionNow = "1280x720";
    /* the size the picture should be, and since when it has wanted it (a window being dragged changes it often) */
    private int uiWantedWidth = GameFacts.screenWidth;
    private int uiWantedHeight = GameFacts.screenHeight;
    private long uiWantedSince;

    public static int getUiScale() {
        return uiScaleSetting;
    }

    /** Sets the UI resolution (UI_SCALE_AUTO, or 1 to 4 times 1280x720) and saves it. */
    public static void setUiScale(int scale) {
        uiScaleSetting = Math.max(UI_SCALE_AUTO, Math.min(UI_SCALE_MOST, scale));
        uiScaleSettingChanged = true;
    }

    public static String describeUiScale() {
        return "menus and HUD drawn at " + uiResolutionNow
                + (uiScaleSetting == UI_SCALE_AUTO ? ", the size they are shown at" : "");
    }

    /** "auto", or 1 to 4 (also with an "x"), else null. */
    static Integer parseUiScale(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("auto")) {
            return UI_SCALE_AUTO;
        }
        t = t.endsWith("x") ? t.substring(0, t.length() - 1) : t;
        return switch (t) {
            case "1", "2", "3", "4" -> Integer.parseInt(t);
            default -> null;
        };
    }

    /** -Dnfm.uiscale=auto|1-4, else 1 in hidden test runs (frames the tests compare), else the saved choice. */
    private void loadUiScale() {
        String property = System.getProperty("nfm.uiscale");
        Integer parsed = property == null ? null : parseUiScale(property);
        if (parsed != null) {
            uiScaleSetting = parsed;
        } else if (runsHidden()) {
            uiScaleSetting = 1;
        } else {
            int saved = readcookie(UI_SCALE_COOKIE);
            uiScaleSetting = saved >= 1 && saved <= UI_SCALE_MOST + 1 ? saved - 1 : UI_SCALE_AUTO;
        }
        uiScaleSettingChanged = false;
    }

    /**
     * At the start of a tick: saves a changed UI resolution, and when the
     * picture should be another size, and has wanted it for a moment, makes
     * it that size. Auto draws it at the size it is shown at in the window,
     * so every pixel of the window is the game's own; with the software
     * renderer, Auto stays at 1280x720, which it draws fastest.
     */
    private void fitUiResolution(long now) {
        if (uiScaleSettingChanged) {
            uiScaleSettingChanged = false;
            savecookie(UI_SCALE_COOKIE, String.valueOf(uiScaleSetting + 1)); // the store reads 0 badly
        }
        int setting = uiScaleSetting;
        int width;
        int height;
        if (setting != UI_SCALE_AUTO) {
            width = GameFacts.screenWidth * setting;
            height = GameFacts.screenHeight * setting;
        } else {
            Rectangle place = gpuRenderer && !gpuUnavailable ? placePicture(getWidth(), getHeight()) : null;
            width = place == null ? GameFacts.screenWidth : place.width;
            height = place == null ? GameFacts.screenHeight : place.height;
            // never below the game's own size, nor above four times it
            float scale = Math.max(1F, Math.min(UI_SCALE_MOST, width / (float) GameFacts.screenWidth));
            width = Math.round(GameFacts.screenWidth * scale);
            height = Math.round(GameFacts.screenHeight * scale);
        }
        if (width != uiWantedWidth || height != uiWantedHeight) {
            uiWantedWidth = width;
            uiWantedHeight = height;
            uiWantedSince = now;
        }
        boolean settled = now - uiWantedSince > 250_000_000L || setting != UI_SCALE_AUTO;
        if (settled && offImage != null
                && (offImage.getWidth(null) != width || offImage.getHeight(null) != height)) {
            resizeBuffers(width, height);
            HLogger.info("Menus and HUD now drawn at " + uiResolutionNow);
        }
    }

    /** The menus and HUD drawn at {@code width} x {@code height} from now on. */
    private void resizeBuffers(int width, int height) {
        makeBuffers(width, height);
        uiWantedWidth = width;
        uiWantedHeight = height;
        uiResolutionNow = width + "x" + height;
        if (ui != null) {
            ui.setTarget(rd);
        }
    }

    /**
     * The picture at the game's own 1280x720, for the effects that read its
     * pixels (the pause menu's background, the portraits): the picture
     * itself, or a copy of it made that size.
     */
    private Image pictureAtGameSize() {
        if (offImage.getWidth(null) == GameFacts.screenWidth && offImage.getHeight(null) == GameFacts.screenHeight) {
            return offImage;
        }
        return captureFrame();
    }

    private void addFile(File source, File[] files, String path) {
        try {
            File tmpZip = File.createTempFile(source.getName(), null);
            tmpZip.delete();
            if (!source.renameTo(tmpZip)) {
                throw new RuntimeException("Could not make temp file (" + source.getName() + ")");
            }
            byte[] buffer = new byte[4096];
            ZipInputStream zin = new ZipInputStream(new FileInputStream(tmpZip));
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(source));
            for (File file : files) {
                InputStream in = new FileInputStream(file);
                out.putNextEntry(new ZipEntry(path + file.getName()));
                for (int read = in.read(buffer); read > -1; read = in.read(buffer)) {
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
                in.close();
            }
            for (ZipEntry ze = zin.getNextEntry(); ze != null; ze = zin.getNextEntry()) {
                if (!zipEntryMatch(ze.getName(), files, path)) {
                    out.putNextEntry(ze);
                    for (int read = zin.read(buffer); read > -1; read = zin.read(buffer)) {
                        out.write(buffer, 0, read);
                    }
                    out.closeEntry();
                }
            }
            out.close();
            tmpZip.delete();
            zin.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean zipEntryMatch(String zeName, File[] files, String path) {
        for (File file : files) {
            if ((path + file.getName()).equals(zeName)) {
                return true;
            }
        }
        return false;
    }

    private void openurl(final String url) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (final Exception ignored) {

            }
        } else {
            try {
                Runtime.getRuntime().exec("" + urlopen() + " " + url + "");
            } catch (final Exception ignored) {

            }
        }
    }

    private static String urlopen() {
        String opener = "explorer";
        final String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux") || osName.contains("unix") || osName.equals("aix")) {
            opener = "xdg-open";
        }
        if (osName.contains("mac")) {
            opener = "open";
        }
        return opener;
    }

    private void catchlink(int screen, GameUi gameUi) {
        if (!lostfcs) {
            if (screen == 0)
                if (xm > 0 && xm < GameFacts.screenWidth && ym > 110 && ym < 169
                        || xm > Utility.centeredImageX(gameUi.rpro)
                                && xm < Utility.centeredImageX(gameUi.rpro) + gameUi.rpro.getWidth(null) && ym > 240
                                && ym < 259) {
                    setCursor(new Cursor(12));
                    if (mouses == 2)
                        openurl("http://www.radicalplay.com/");
                } else {
                    setCursor(new Cursor(0));
                }
            if (screen == 1)
                if (xm > 0 && xm < GameFacts.screenWidth && ym > 205 && ym < 267) {
                    setCursor(new Cursor(12));
                    if (mouses == 2)
                        openurl("http://www.radicalplay.com/");
                } else {
                    setCursor(new Cursor(0));
                }
        }
    }

    /** The pointer moved to (x, y) in the panel with no button held. */
    void mouseMovedTo(int x, int y) {
        if (!exwist && !lostfcs) {
            xm = toPictureX(x);
            ym = toPictureY(y);
        }
    }

    /** What {@link RaceSession} may see and do. Called on the game thread only. */
    private final class SessionHost implements RaceSession.Host {
        @Override
        public Phase phase() {
            return ui.fase;
        }

        @Override
        public void setPhase(Phase phase) {
            ui.fase = phase;
        }

        @Override
        public RaceState.View race() {
            return new RaceState.View(world, racers, u, sceneObjects, raceCheckpoints, raceRecord, ui,
                    raceTrackers, GameFacts.numberOfPlayers);
        }

        @Override
        public GameUi ui() {
            return ui;
        }

        @Override
        public CheckPoints checkpoints() {
            return raceCheckpoints;
        }

        @Override
        public Control player() {
            return u[0];
        }

        @Override
        public void key(int code, boolean down) {
            if (down) {
                pressKey(code, false, 0L);
            } else {
                releaseKey(code, 0L);
            }
        }

        @Override
        public void click() {
            mouses = 1;
        }

        @Override
        public int cameraView() {
            return view;
        }

        @Override
        public void setCameraView(int cameraView) {
            view = cameraView;
        }

        @Override
        public BufferedImage frame() {
            renderIngameScene(1F);
            if (hudImage != null) {
                rd.drawImage(hudImage, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight, null);
            }
            return captureFrame();
        }

        @Override
        public int sceneCount() {
            return nob;
        }
    }
}