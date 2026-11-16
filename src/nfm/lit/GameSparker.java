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
    public static final int FPS_MIN = 30;
    public static final int FPS_MAX = 1000;
    private static final String FPS_COOKIE = "fpslimit";
    /** The cookie store reads -1 as "missing", so "match display" is saved as 1. */
    private static final int FPS_COOKIE_MATCH_DISPLAY = 1;
    private static volatile int fpsLimit = FPS_MATCH_DISPLAY;
    private static volatile boolean fpsLimitChanged;
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

    /** Objects rendered by the in-game pass (was a local: aconto1). */
    private ContO[] sceneObjects;
    /** Every loaded model, cars first: a car that is replaced after a crash is copied from here. */
    private ContO[] modelObjects;
    /** Simulation state per car (was a local: amadness). */
    private Madness[] racers;
    private xtGraphics ui;
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
    /**
     * Samples per pixel for races drawn on the graphics card: 1 (off, the
     * game's own hard edges) or 2, 4 or 8, which smooth polygon edges.
     */
    private static volatile int multisample = 1;
    private static volatile boolean multisampleChanged;
    /** Samples per pixel the card last drew a race with: fewer than asked when it does no more. */
    private static volatile int multisampleInUse = 1;
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
    /** The scene read back from the graphics card, at the size it drew it; for tests. */
    private volatile BufferedImage capturedGpuScene;

    public static String gameState = "None";
    public static int gameStateID;
    public static int ContosCount;

    public String wallmodel;

    public int noboffset = 10;      //this makes it so IDs are offset correctly, can be modified by stage via idoffset(x)
    public int nobfix = carModels.length - noboffset;

    public boolean reverseYRot = false;

    /**
     * <a href=
     * "http://www.expandinghead.net/keycode.html">http://www.expandinghead.net/keycode.html</a>
     */
    @Override
    public boolean keyDown(Event event, int i) {
        if (!exwist) {
            synchronized (inputLock) {
                if (i == 1004)
                    liveUp = true;
                if (i == 1005)
                    liveDown = true;
                if (i == 1007)
                    liveRight = true;
                if (i == 1006)
                    liveLeft = true;
                if (i == 32)
                    liveHandb = true;
                if (i == 120 || i == 88)
                    liveLookback = -1;
                if (i == 122 || i == 90)
                    liveLookback = 1;
                // the autopilot and recorded races press keys with no event
                boolean alt = event != null && (event.modifiers & Event.ALT_MASK) != 0;
                boolean fullscreenKey = i == Event.F11 || i == 10 && alt;
                if (fullscreenKey) {
                    RunApp.toggleFullscreen();
                } else if (i == 10 || i == 80 || i == 112 || i == 27)
                    liveEnter = true;
                if (i == 118 || i == 86) {
                    liveView++;
                    if (liveView == 3)
                        liveView = 0;
                }
                if (!latching) {
                    applyLiveKeys();
                }
                // under the lock too: a run-ahead tick puts the controls back as they were
                if (i == 77 || i == 109)
                    u[0].mutem = !u[0].mutem;
                if (i == 78 || i == 110)
                    u[0].mutes = !u[0].mutes;
                if (i == 97 || i == 65)
                    u[0].arrace = !u[0].arrace;
            }
        }
        return false;
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

    @Override
    public boolean lostFocus(Event event, Object obj) {
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
                u[0].falseo();
            }
            setCursor(new Cursor(0));
        }
        return false;
    }

    @Override
    public boolean gotFocus(Event event, Object obj) {
        if (!exwist && lostfcs)
            lostfcs = false;
        return false;
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
    private int readcookie(String string) {
        try {
            ZipFile zipFile = new ZipFile(cookieDirZip);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            String fromEntry = " ";

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().contains(string)) {
                    InputStream stream = zipFile.getInputStream(entry);
                    fromEntry = fromStream(stream);
                }
            }
            zipFile.close();

            HLogger.info("Successfully read cookie " + string + " with value " + Integer.parseInt(fromEntry));
            return Integer.parseInt(fromEntry);
        } catch (IOException ioexception) {
            // HLogger.error(ioexception.toString());
            HLogger.error(string + ".dat probably doesn't exist");
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
        int i = 0;
        int i_97_ = 0;
        if (this.shaka > 10) {
            i = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
            i_97_ = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
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
        x += (int) Math.round(i * scale);
        y += (int) Math.round(i_97_ * scale);
        return new Rectangle(x, y, w, h);
    }

    /** Draws the picture into the window in software, as {@link #paint} does without the GPU. */
    private void paintPicture(Graphics2D graphics2d) {
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
        if (w == GameFacts.screenWidth && h == GameFacts.screenHeight) {
            graphics2d.drawImage(this.offImage, x, y, this);
        } else {
            drawScaled(graphics2d, x, y, w, h);
        }
        // the borders, and the strip a screen shake uncovers
        graphics2d.setComposite(AlphaComposite.Src);
        graphics2d.setColor(Color.BLACK);
        graphics2d.fillRect(0, 0, width, Math.max(0, y));
        graphics2d.fillRect(0, y + h, width, Math.max(0, height - y - h));
        graphics2d.fillRect(0, y, Math.max(0, x), h);
        graphics2d.fillRect(x + w, y, Math.max(0, width - x - w), h);
    }

    /**
     * Draws the frame smoothly resized. The frame is scaled by {@link #frameScaler}
     * and then copied as is, which is several times faster than letting Java
     * filter it while drawing. The lock keeps a paint from the event thread
     * from reusing the scaled image while the game thread is drawing it.
     */
    private void drawScaled(Graphics2D graphics2d, int x, int y, int w, int h) {
        synchronized (frameScaler) {
            BufferedImage scaled = offImage instanceof BufferedImage frame ? frameScaler.scale(frame, w, h) : null;
            if (scaled != null) {
                graphics2d.drawImage(scaled, x, y, null);
                return;
            }
        }
        graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2d.drawImage(this.offImage, x, y, w, h, this);
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
     * @param conto      conto instance
     * @param medium     medium instance
     * @param trackers   trackers instance
     * @param xtgraphics xtgraphics instance
     * @author Kaffeinated, Omar Waly
     */
    private void loadbase(final ContO conto[], Trackers trackers, xtGraphics xtgraphics) {
        xtgraphics.dnload += 6;
        try {
            ZipInputStream zipinputstream;
            final File file = new File(new StringBuilder().append("").append("data/models.radq").toString());
            zipinputstream = new ZipInputStream(new FileInputStream(file));
            ZipEntry zipentry = zipinputstream.getNextEntry();
            for (; zipentry != null; zipentry = zipinputstream.getNextEntry()) {
                int modelId = -1;

                final int carCount = carModels.length;
                final int trackCount = trackModels.length;
                // final int extraCount = extraModels.length;

                for (int car = 0; car < carModels.length; car++)
                    if (zipentry.getName().startsWith(carModels[car]))
                        modelId = car;

                for (int track = 0; track < trackModels.length; track++)
                    if (zipentry.getName().startsWith(trackModels[track]))
                        modelId = track + carCount;

                for (int extra = 0; extra < extraModels.length; extra++)
                    if (zipentry.getName().startsWith(extraModels[extra]))
                        modelId = extra + trackCount + carCount;

                int entireSize = (int) zipentry.getSize();
                final byte[] modelData = new byte[entireSize];

                int unknown1 = 0;
                int unknown2;
                for (; entireSize > 0; entireSize -= unknown2) {
                    unknown2 = zipinputstream.read(modelData, unknown1, entireSize);
                    unknown1 += unknown2;
                }
                conto[modelId] = new ContO(modelData, trackers);
                xtgraphics.dnload++;
            }
            /*
             * be sure to add your added arrays here
             */
            HLogger.info("Contos loaded: " + (carModels.length + trackModels.length + extraModels.length));
            ContosCount = carModels.length + trackModels.length + extraModels.length;
            zipinputstream.close();
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

    /**
     * <a href=
     * "http://www.expandinghead.net/keycode.html">http://www.expandinghead.net/keycode.html</a>
     */
    @Override
    public boolean keyUp(Event event, int i) {
        if (!exwist) {
            synchronized (inputLock) {
                if (i == 1004)
                    liveUp = false;
                if (i == 1005)
                    liveDown = false;
                if (i == 1007)
                    liveRight = false;
                if (i == 1006)
                    liveLeft = false;
                if (i == 32)
                    liveHandb = false;
                if (i == 120 || i == 88 || i == 122 || i == 90)
                    liveLookback = 0;
                if (!latching) {
                    applyHeldKeys();
                }
            }
        }
        return false;
    }

    public void start() {
        if (gamer == null)
            gamer = new Thread(this);
        if (gamer.getState() == Thread.State.NEW)
            gamer.start();
    }

    @Override
    public boolean mouseDown(Event event, int i, int j) {
        if (!exwist && mouses == 0) {
            xm = toPictureX(i);
            ym = toPictureY(j);
            mouses = 1;
        }
        return false;
    }

    /**
     * Loads stage elements
     *
     * @param aconto      conto instance
     * @param aconto1     conto instance specifically for cars
     * @param trackers    trackers instance
     * @param checkpoints checkpoints instance
     * @param xtgraphics  xtgraphics instance
     * @param amadness    madness instance
     * @param record      record instance
     * @author Kaffeinated, Omar Waly
     */

    private void loadstage(ContO aconto[], ContO aconto1[], Trackers trackers, CheckPoints checkpoints,
            xtGraphics xtgraphics, Madness amadness[], Record record, boolean custom) {
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
        xtgraphics.arrowDisabled = false;
        xtgraphics.opstatusDisabled = false;

        resetView();
        int r_wall = 0;
        int l_wall = 100;
        int t_wall = 0;
        int b_wall = 100;

        CheckPoints.customTrack = false;

        loadStage = stageDir + stageSubDir + checkpoints.stage + ".txt";
        if (xtgraphics.nfmmode == 1) {
            stageSubDir = "nfm1/";
        } else if (xtgraphics.nfmmode == 2) {
            stageSubDir = "nfm2/";
        }

        if (custom) {
            loadStage = stageDir + loadStageCus + ".txt";
            HLogger.info(loadStage);
        }

        String string = "";

        try (BufferedReader bufferedreader = new BufferedReader(new FileReader(new File(loadStage)))) {
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
                    xtgraphics.snap(checkpoints.stage);
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
                    xtgraphics.arrowDisabled = true;
                if (line.startsWith("nostatus"))
                    xtgraphics.opstatusDisabled = true;

                if (line.startsWith("idoffset")) {
                    noboffset = Utility.getint("idoffset", line, 0);
                    nobfix = carModels.length - noboffset;
                }

                if (line.startsWith("yrot")) {
                    reverseYRot = true;
                }

                if (line.startsWith("set")) {
                    int k1 = Utility.getint("set", line, 0);
                    k1 += nobfix;
                
                    // compute default Y (ground-height)
                    int yVal = Medium.ground - aconto1[k1].grat;
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
                    aconto[nob] = new ContO(
                        aconto1[k1],
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
                    int yVal = Medium.ground - aconto1[id].grat;
                
                    // if there *is* a 5th comma-separated value, use that instead
                    // (splitting only the part inside the parentheses)
                    String inside = line.substring(line.indexOf('(') + 1, line.lastIndexOf(')'));
                    String[] parts = inside.split("\\s*,\\s*");
                    if (parts.length > 4) {
                        yVal = Utility.getint("ds:set", line, 4);
                    }
                
                    // now create the object, exactly as before but with our yVal
                    aconto[nob] = new ContO(
                        aconto1[id],
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
                    int l1 = Utility.getint("chk", line, 0);
                    l1 += nobfix;
                
                    // compute default Y (ground-height)
                    int yVal = Medium.ground - aconto1[l1].grat;
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
                    aconto[nob] = new ContO(
                        aconto1[l1],
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
                    aconto[nob].checkpoint = checkpoints.nsp + 1;
                    checkpoints.nsp++;
                    nob++;
                    notb = nob;
                }
                if (line.startsWith("fix")) {
                    int i2 = Utility.getint("fix", line, 0);
                    i2 += nobfix;
                    aconto[nob] = new ContO(aconto1[i2], Utility.getint("fix", line, 1), Utility.getint("fix", line, 3),
                            Utility.getint("fix", line, 2), Utility.getint("fix", line, 4));
                    checkpoints.fx[checkpoints.fn] = Utility.getint("fix", line, 1);
                    checkpoints.fz[checkpoints.fn] = Utility.getint("fix", line, 2);
                    checkpoints.fy[checkpoints.fn] = Utility.getint("fix", line, 3);
                    aconto[nob].elec = true;
                    if (Utility.getint("fix", line, 4) != 0) {
                        checkpoints.roted[checkpoints.fn] = true;
                        aconto[nob].roted = true;
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
                    // xtGraphics.sndsize[18] = Utility.getint("soundtrack", string, 2);
                }

                int wall_id = getModel(wallmodel);

                if (line.startsWith("maxr")) {
                    int j2 = Utility.getint("maxr", line, 0);
                    int j3 = Utility.getint("maxr", line, 1);
                    r_wall = j3;
                    int j4 = Utility.getint("maxr", line, 2);
                    for (int j5 = 0; j5 < j2; j5++) {
                        aconto[nob] = new ContO(aconto1[wall_id], j3, Medium.ground - aconto1[wall_id].grat,
                                j5 * 4800 + j4, 0);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.x[trackers.nt] = j3 + 500;
                    trackers.radx[trackers.nt] = 600;
                    trackers.z[trackers.nt] = ((j2 * 4800) / 2 + j4) - 2400;
                    trackers.radz[trackers.nt] = (j2 * 4800) / 2;
                    trackers.xy[trackers.nt] = 90;
                    trackers.zy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }
                if (line.startsWith("maxl")) {
                    int k2 = Utility.getint("maxl", line, 0);
                    int k3 = Utility.getint("maxl", line, 1);
                    l_wall = k3;
                    int k4 = Utility.getint("maxl", line, 2);
                    for (int k5 = 0; k5 < k2; k5++) {
                        aconto[nob] = new ContO(aconto1[wall_id], k3, Medium.ground - aconto1[wall_id].grat,
                                k5 * 4800 + k4, 0);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.x[trackers.nt] = k3 - 500;
                    trackers.radx[trackers.nt] = 600;
                    trackers.z[trackers.nt] = ((k2 * 4800) / 2 + k4) - 2400;
                    trackers.radz[trackers.nt] = (k2 * 4800) / 2;
                    trackers.xy[trackers.nt] = -90;
                    trackers.zy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }

                if (line.startsWith("maxt")) {
                    int l2 = Utility.getint("maxt", line, 0);
                    int l3 = Utility.getint("maxt", line, 1);
                    t_wall = l3;
                    int l4 = Utility.getint("maxt", line, 2);
                    for (int l5 = 0; l5 < l2; l5++) {
                        aconto[nob] = new ContO(aconto1[wall_id], l5 * 4800 + l4, Medium.ground - aconto1[wall_id].grat,
                                l3, 90);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.z[trackers.nt] = l3 + 500;
                    trackers.radz[trackers.nt] = 600;
                    trackers.x[trackers.nt] = ((l2 * 4800) / 2 + l4) - 2400;
                    trackers.radx[trackers.nt] = (l2 * 4800) / 2;
                    trackers.zy[trackers.nt] = 90;
                    trackers.xy[trackers.nt] = 0;
                    trackers.dam[trackers.nt] = 1;
                    trackers.nt++;
                }
                if (line.startsWith("maxb")) {
                    int i3 = Utility.getint("maxb", line, 0);
                    int i4 = Utility.getint("maxb", line, 1);
                    b_wall = i4;
                    int i5 = Utility.getint("maxb", line, 2);
                    for (int i6 = 0; i6 < i3; i6++) {
                        aconto[nob] = new ContO(aconto1[wall_id], i6 * 4800 + i5, Medium.ground - aconto1[wall_id].grat,
                                i4, 90);
                        nob++;
                    }

                    trackers.y[trackers.nt] = -5000;
                    trackers.rady[trackers.nt] = 7100;
                    trackers.z[trackers.nt] = i4 - 500;
                    trackers.radz[trackers.nt] = 600;
                    trackers.x[trackers.nt] = ((i3 * 4800) / 2 + i5) - 2400;
                    trackers.radx[trackers.nt] = (i3 * 4800) / 2;
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

            xtgraphics.fase = Phase.ERRORLOADINGSTAGE;
            HLogger.error("Error loading stage " + checkpoints.stage);
            e.printStackTrace();
        }
        if (checkpoints.stage == 16)
            Medium.lightn = 0;
        else
            Medium.lightn = -1;
        Medium.nochekflk = checkpoints.stage != 1;
        if (xtgraphics.fase == Phase.STAGESELECTTRIGGER) {
            Medium.trx = 0L;
            Medium.trz = 0L;
            if (trackers.nt >= 4) {
                int i1 = 4;
                do {
                    Medium.trx += trackers.x[trackers.nt - i1];
                    Medium.trz += trackers.z[trackers.nt - i1];
                } while (--i1 > 0);
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
            xtgraphics.fase = Phase.STAGESELECT;
            mouses = 0;
        }
        int j1 = 0;
        do
            u[j1].reset(checkpoints, xtgraphics.sc[j1]);
        while (++j1 < GameFacts.numberOfPlayers);
        xtgraphics.resetstat(checkpoints.stage);
        j1 = 0;
        do {
            if (j1 % 3 == 0) {
                aconto[j1] = new ContO(aconto1[xtgraphics.sc[j1]], 0, 250 - aconto1[xtgraphics.sc[j1]].grat,
                        -760 + ((j1 / 3) * 760), 0);
            }
            if (j1 % 3 == 1) {
                aconto[j1] = new ContO(aconto1[xtgraphics.sc[j1]], -350, 250 - aconto1[xtgraphics.sc[j1]].grat,
                        -380 + ((int) (j1 / 3) * 760), 0);
            }
            if (j1 % 3 == 2) {
                aconto[j1] = new ContO(aconto1[xtgraphics.sc[j1]], 350, 250 - aconto1[xtgraphics.sc[j1]].grat,
                        -380 + ((int) (j1 / 3) * 760), 0);
            }
            amadness[j1].reseto(xtgraphics.sc[j1], aconto[j1], checkpoints);
        } while (++j1 < GameFacts.numberOfPlayers);
        record.reset(aconto);
        if (session != null) {
            session.stageLoaded();
        }
        System.gc();
    }

    /**
     * motion
     *
     * @param amadness madness
     * @param shakeAmt shake sensitivity
     *                 1 normal
     *                 20 maximum
     * @param maxAmt   maximum displacement of the screen while shaking
     */
    private void initMoto(Madness amadness[], int shakeAmt, int maxAmt) {
        if (amadness[0].shakedam > 0) {
            shaka = amadness[0].shakedam / (20 / shakeAmt);
            amadness[0].shakedam = 0;
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
        try {
            Graphics2D target = retainedRecording ? recordingRecorder.begin(rd)
                    : gpuSceneFrame ? sceneRecorder.begin(rd) : rd;
            Precise.cameraAngles();
            Medium.d(target);
            Precise.cameraAngles();
            drawWorldObjects(target);
        } finally {
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
        int k3 = 0;
        do {
            if (racers[k3].newcar) {
                int j5 = sceneObjects[k3].xz;
                int j6 = sceneObjects[k3].xy;
                int l8 = sceneObjects[k3].zy;
                sceneObjects[k3] = new ContO(modelObjects[racers[k3].cn], sceneObjects[k3].x, sceneObjects[k3].y, sceneObjects[k3].z,
                        0);
                sceneObjects[k3].xz = j5;
                sceneObjects[k3].xy = j6;
                sceneObjects[k3].zy = l8;
                racers[k3].newcar = false;
            }
        } while (++k3 < GameFacts.numberOfPlayers);

        drawSimulationStep();

        if (ui.starcnt == 0) {
            int l12 = 0;
            do {
                int j14 = 0;
                do {
                    if (j14 != l12) {
                        racers[l12].colide(sceneObjects[l12], racers[j14], sceneObjects[j14]);
                    }
                } while (++j14 < GameFacts.numberOfPlayers);
            } while (++l12 < GameFacts.numberOfPlayers);
            l12 = 0;
            do
                racers[l12].drive(u[l12], sceneObjects[l12], raceTrackers, raceCheckpoints);
            while (++l12 < GameFacts.numberOfPlayers);
            l12 = 0;
            do
                raceRecord.rec(sceneObjects[l12], l12, racers[l12].squash, racers[l12].lastcolido,
                        racers[l12].cntdest);
            while (++l12 < GameFacts.numberOfPlayers);
            raceCheckpoints.checkstat(racers, sceneObjects, raceRecord, GameFacts.numberOfPlayers);

            // This starts the AI code for all the cars.
            l12 = 1;
            do
                u[l12].preform(racers[l12], sceneObjects[l12], raceCheckpoints, raceTrackers, GameFacts.numberOfPlayers);
            while (++l12 < GameFacts.numberOfPlayers);
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
                ui.blendude(offImage);
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
                Medium.adv = GameFacts.screenWidth;
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
        presenter.handOverSpan(spanScratch);
        Presenter.Kept kept = presenter.free();
        if (!recordKept(ahead, kept.recorder, kept.scene)) {
            keptBlocked = true; // until the next tick
            return false;
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
            k.scene.realize(pose, k.recorder.list().data());
            boolean flash = k.flash && k.version != flashedVersion;
            if (flash) {
                flashedVersion = k.version;
            }
            long placed = System.nanoTime();
            boolean shown;
            try {
                shown = showOnGpu(false, k.recorder.list(), k.hud, k.version, flash, true);
            } catch (RuntimeException | LinkageError ex) {
                HLogger.warn("The graphics card stopped drawing a race: " + ex);
                shown = false;
            }
            long end = System.nanoTime();
            if (!shown) {
                failed = true; // the game thread takes over and finds out why
                return;
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

        /** Waits until the next frame is due: the display's refresh, the frame limit or, with none, not at all. */
        private void pace(long now) {
            long interval = frameIntervalNanos(now);
            if (interval <= 0L) {
                Thread.yield();
                return;
            }
            if (fpsLimit == FPS_MATCH_DISPLAY) {
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
                    + " the tick; corrections at new ticks: %s)%s; last scene %s, %d points, %d polygons kept%n",
                    statFrames / seconds, statFrames == 0 ? 0D : statPlaceNanos / 1e6D / statFrames,
                    statFrames == 0 ? 0D : statShowNanos / 1e6D / statFrames, statCarried, corrections, pace,
                    gpu != null ? gpu.sceneStatistics() : "", shown == null ? 0 : shown.scene.points(),
                    shown == null ? 0 : shown.scene.polygons());
            statCorrectionCount = 0;
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
    private void renderHud(Control control, boolean flag) {
        Graphics2D previous = ui.getTarget();
        hudG.setComposite(AlphaComposite.Clear);
        hudG.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        hudG.setComposite(AlphaComposite.SrcOver);
        ui.setTarget(hudG);
        ui.stat(racers, raceCheckpoints, control, sceneObjects, flag);
        ui.setTarget(previous);
    }

    /**
     * Phases whose 3D scene is drawn by the presentation pass, interpolated, with
     * the HUD layer composited on top.
     */
    private static boolean isScenePhase(Phase phase) {
        return phase == Phase.INGAME || phase == Phase.INSTANTREPLAY || phase == Phase.GAMEHIGHLIGHT;
    }

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
    }

    /**
     * Ends a frame whose scene was recorded for the graphics card: keeps the
     * HUD as it is now and the white flash with it, and leaves the back
     * buffer's colour as the software path would have.
     */
    private void finishGpuScene() {
        if (hudImage instanceof BufferedImage hud) {
            if (ownSceneHud == null) {
                ownSceneHud = new BufferedImage(hud.getWidth(), hud.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }
            int[] from = ((DataBufferInt) hud.getRaster().getDataBuffer()).getData();
            int[] to = ((DataBufferInt) ownSceneHud.getRaster().getDataBuffer()).getData();
            System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
            gpuSceneHud = ownSceneHud; // not a kept frame's, which the presenter may be drawing
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
            rd.drawImage(gpuSceneHud, 0, 0, null);
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
        return showOnGpu(capture, gpuSceneFrame ? shownRecorder.list() : null, gpuSceneHud, -1L, gpuSceneFlash,
                false);
    }

    /**
     * Shows a frame through the graphics card: the 3D {@code scene} with the
     * {@code hud} on top (with the same {@code version} of a kept scene, the
     * HUD is not uploaded again and its polygons are cut as before; -1 for
     * a frame drawn anew) and the white {@code flash}, or with no scene the
     * game thread's picture. A frame the {@code presenter} draws on its own
     * never falls back to the picture.
     */
    private boolean showOnGpu(boolean capture, nfm.lit.gpu.SceneList scene, BufferedImage hud, long version,
            boolean flash, boolean presenterFrame) {
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
            boolean smooth = place.width != GameFacts.screenWidth || place.height != GameFacts.screenHeight;
            gpu.setVsync(fpsLimit == FPS_MATCH_DISPLAY);
            Dimension size = new Dimension(width, height);
            boolean shown;
            try {
                BufferedImage picture = (BufferedImage) offImage;
                if (scene != null) {
                    picture = null; // show the scene drawn on the card
                    gpu.setMultisample(multisample);
                    boolean drawn = gpu.drawScene(scene, hud, version, flash, capture, sceneTargetSize(place));
                    multisampleInUse = gpu.multisample();
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
                    shown = true;
                } else {
                    shown = gpu.present(picture, size, place, smooth);
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
     */
    private static Dimension sceneTargetSize(Rectangle place) {
        if (!GPU_SCENE_NATIVE || place.width < GameFacts.screenWidth || place.height < GameFacts.screenHeight) {
            return new Dimension(GameFacts.screenWidth, GameFacts.screenHeight);
        }
        return new Dimension(place.width, place.height);
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

    /** 1, 2, 4 or 8 for "off", "1", "2", "4" or "8" (also with an "x"), or 0 if it is none of those. */
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
            default -> 0;
        };
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
        } else if (System.getProperty("nfm.autotest") == null && !RaceSession.runsHidden()) {
            int saved = readcookie(MSAA_COOKIE);
            samples = saved == 2 || saved == 4 || saved == 8 ? saved : 1;
        }
        multisample = samples;
        multisampleChanged = false;
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
        } else if (System.getProperty("nfm.autotest") != null || RaceSession.runsHidden()) {
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
        if (limit == FPS_UNLIMITED) {
            return 0L;
        }
        if (limit == FPS_MATCH_DISPLAY) {
            if (displayRefreshHz == 0 || now - displayRefreshCheckedAt > 1_000_000_000L) {
                displayRefreshHz = queryRefreshRate();
                displayRefreshCheckedAt = now;
            }
            limit = displayRefreshHz;
            if (limit <= 0) {
                return 0L;
            }
        }
        return 1_000_000_000L / limit;
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
    public static boolean setFpsLimit(int limit) {
        if (limit != FPS_MATCH_DISPLAY && limit != FPS_UNLIMITED && (limit < FPS_MIN || limit > FPS_MAX)) {
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
        int limit = saved == FPS_COOKIE_MATCH_DISPLAY ? FPS_MATCH_DISPLAY : saved;
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
            savecookie(FPS_COOKIE, "" + (limit == FPS_MATCH_DISPLAY ? FPS_COOKIE_MATCH_DISPLAY : limit));
        }
    }

    /**
     * Copies the current back buffer into an independent image.
     */
    private BufferedImage captureFrame() {
        BufferedImage image = new BufferedImage(GameFacts.screenWidth, GameFacts.screenHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2d = image.createGraphics();
        graphics2d.drawImage(offImage, 0, 0, null);
        graphics2d.dispose();
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
        Graphics2D graphics2d = image.createGraphics();
        paintPicture(graphics2d);
        graphics2d.dispose();
        return image;
    }

    private void saveFrame(File directory, String name) {
        capturedFrame = null;
        capturedWindow = null;
        capturedGpu = null;
        capturedGpuScene = null;
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

        while (System.currentTimeMillis() - startMillis < budgetMillis) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                return;
            }

            Phase phase = ui.fase;
            long now = System.currentTimeMillis();

            if (phase != lastPhase) {
                System.out.println("[autotest] phase " + lastPhase + " -> " + phase);
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

            boolean menu = phase == Phase.MAINMENU || phase == Phase.CARSELECT
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

            if (phase == Phase.PAUSEMENU) {
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
        } catch (InterruptedException _ex) {
        }
        System.exit(0);
    }

    @Override
    public void run() {
        rd.setColor(new Color(0, 0, 0));
        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        repaint();
        /*
         * start an example timer
         */
        Utility.startTimer();
        Trackers trackers = new Trackers();
        CheckPoints checkpoints = new CheckPoints();
        xtGraphics xtgraphics = new xtGraphics(rd, this);
        xtgraphics.loaddata();
        Record record = new Record();
        ContO aconto[] = new ContO[carModels.length + trackModels.length + extraModels.length]; // be sure all your
                                                                                                // arrays get in here
        loadbase(aconto, trackers, xtgraphics);
        ContO aconto1[] = new ContO[5000];
        Madness amadness[] = new Madness[51];
        int l = 0;
        do {
            amadness[l] = new Madness(record, xtgraphics, world, l);
            u[l] = new Control(world);
        } while (++l < 51); // dont touch this
        l = 0;
        float f = 35F;
        int i1 = 80;
        /*
         * stop an example timer
         */
        Utility.stopTimer();
        /**
         * this bit in here reads cookies and set values
         */
        l = readcookie("unlocked");
        if (l >= 1 && l <= GameFacts.numberOfStages) {
            /*
             * Note: that is an L
             */
            xtgraphics.unlocked = l;
            if (xtgraphics.unlocked != GameFacts.numberOfStages)
                checkpoints.stage = xtgraphics.unlocked;
            else
                checkpoints.stage = (int) (Math.random() * 17D) + 1;
            xtgraphics.opselect = 0;
        }
        l = readcookie("usercar");
        if (l >= 0 && l <= GameFacts.numberOfCars - 1)
            xtgraphics.sc[0] = l;
        l = readcookie("gameprfact");
        if (l != -1) {
            f = l;
            i1 = 1;
        }

        xtgraphics.stoploading();
        System.gc();
        sceneObjects = aconto1;
        racers = amadness;
        modelObjects = aconto;
        ui = xtgraphics;
        raceCheckpoints = checkpoints;
        raceRecord = record;
        raceTrackers = trackers;

        loadFpsLimit();
        loadRunAhead();
        loadScaleToWindow();
        loadRenderer();
        loadMultisample();
        startAutoTest();
        session = RaceSession.fromCommandLine(new SessionHost());
        final boolean unpaced = session != null && session.unpaced();

        int k1 = 0;
        int i2 = 0;
        int j2 = 0;
        int k2 = 0;
        boolean flag2 = false;
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
        long nextTickNanos = System.nanoTime() + tickNanosFor(xtgraphics.fase);
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
                currentTickNanos = tickNanosFor(xtgraphics.fase);
                tickStartNanos = nextTickNanos;
                nextTickNanos += currentTickNanos;
                ticked = true;
                retainedValid = false; // the next frame records the scene for this tick
                keptStale = true;
                keptBlocked = false;
                ticksThisFrame++;
                profileTicks++;
                if (backBufferStale && !isScenePhase(xtgraphics.fase)) {
                    syncBackBuffer(); // menus and overlays draw on top of the last frame
                }
                saveFpsLimitIfChanged();
                saveRunAheadIfChanged();
                saveScaleToWindowIfChanged();
                saveRendererIfChanged();
                saveMultisampleIfChanged();
                if (session != null) {
                    session.beforeTick();
                }
                if (xtgraphics.fase != Phase.INGAME) {
                    releaseRaceInput();
                }

            if (xtgraphics.fase == Phase.LOADING) {
                if (mouses == 1)
                    i2 = 800;
                if (i2 < 800) {
                    xtgraphics.clicknow();
                    i2++;
                } else {
                    i2 = 0;
                    xtgraphics.fase = Phase.AWAITLOADDISMISSAL;
                    xtgraphics.sm.play("powerup");
                    mouses = 0;
                    lostfcs = false;
                }
            }
            if (xtgraphics.fase == Phase.AWAITLOADDISMISSAL)
                if (i2 < 100 && splashScreenState) {
                    xtgraphics.rad(i2);
                    catchlink(0, xtgraphics);
                    if (mouses == 2)
                        mouses = 0;
                    if (mouses == 1)
                        mouses = 2;
                    i2++;
                } else {
                    i2 = 0;
                    xtgraphics.fase = Phase.MAINMENU;
                    mouses = 0;
                    u[0].falseo();
                }
            if (xtgraphics.fase == Phase.CARSELECTTRIGGER)
                if (i2 < 2) {
                    rd.setColor(new Color(0, 0, 0));
                    rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                    i2++;
                } else {
                    xtgraphics.inishcarselect();
                    i2 = 0;
                    xtgraphics.fase = Phase.CARSELECT;
                    mouses = 0;
                }
            if (xtgraphics.fase == Phase.CREDITS) {
                xtgraphics.credits(u[0]);
                if (xtgraphics.flipo == 102) {
                    syncBackBuffer();
                    rd.drawImage(xtgraphics.credsnap(offImage), 0, 0, null);
                }
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (xtgraphics.flipo <= 100)
                    catchlink(0, xtgraphics);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            // if (xtgraphics.fase == Phase.DRM0) {
            //     xtgraphics.fase = Phase.DRMSCREEN;
            //     String regex = "^([a-zA-Z0-9.-]+):(\\d{1,5})$";
            //     Pattern pattern = Pattern.compile(regex);
            //     Matcher matcher = pattern.matcher(xtgraphics.serverip + ":" + xtgraphics.serverport);
    
            //     if (matcher.matches()) {
            //         String host = matcher.group(1);
            //         int port = Integer.parseInt(matcher.group(2));
    
            //         if (port >= 0 && port <= 65535) {
            //             HLogger.info("Connecting to " + host + " on port " + port + "...");
    
            //             try {
            //                 xtgraphics.socket = new Socket(host, port);
            //                 HLogger.info("Connected to the server");
    
            //                 xtgraphics.serverresponse = new BufferedReader(new InputStreamReader(xtgraphics.socket.getInputStream()));

            //                 PrintWriter out = new PrintWriter(xtgraphics.socket.getOutputStream(), true); // true = auto-flush
            //                 String hwid = HWID.getHWID();
            //                 String hashed = HWID.hashHWID(hwid);

            //                 out.println(hashed);  // <- you send this


            //                 xtgraphics.serverresponse = new BufferedReader(new InputStreamReader(xtgraphics.socket.getInputStream()));
            //                 xtgraphics.serverMessage = xtgraphics.serverresponse.readLine();
            //                 HLogger.info(xtgraphics.serverMessage);

            //                 out.close();
    
            //             } catch (java.net.ConnectException e) {
            //                 xtgraphics.serverMessage = e.getMessage();
            //                 HLogger.info(e.getMessage());
            //             } catch (IOException e) {
            //                 HLogger.info("An error occurred:\n" + e.toString());
            //             } finally {
            //                 try {
            //                     if (xtgraphics.serverresponse != null) xtgraphics.serverresponse.close();
            //                     if (xtgraphics.socket != null) xtgraphics.socket.close();
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
            // if (xtgraphics.fase == Phase.DRMSCREEN) {
            //     repaint();
            //     xtgraphics.drmcheck();
            //     if (xtgraphics.serverMessage.equals("Authenticated")) {
            //         xtgraphics.fase = Phase.LOADING;
            //     }
            // }
            if (xtgraphics.fase == Phase.MAINMENU) {
                xtgraphics.maini(u[0], checkpoints, amadness, aconto, aconto1);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.INSTRUCTIONS) {
                xtgraphics.inst(u[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.CUSTOMSETTINGS) { // settings menu
                xtgraphics.menusettings(u[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.POSTGAMEHANDOVER) {
                xtgraphics.fase = Phase.POSTGAME;
            }
            if (xtgraphics.fase == Phase.SAVEGAME) { // save the fucking game properly

                if (checkpoints.stage == xtgraphics.unlocked && xtgraphics.winner
                        && xtgraphics.unlocked != GameFacts.numberOfStages + 1)
                    savecookie("unlocked", "" + xtgraphics.unlocked);
                savecookie("gameprfact", "" + (int) f);
                savecookie("usercar", "" + xtgraphics.sc[0]);

                xtgraphics.fase = Phase.MAINMENU;
            }
            if (xtgraphics.fase == Phase.POSTGAME) {
                xtgraphics.finish(checkpoints, aconto, u[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (checkpoints.stage == GameFacts.numberOfStages && xtgraphics.winner)
                    catchlink(1, xtgraphics);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.CARSELECT) {
                xtgraphics.carselect(u[0], aconto, amadness[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.PREGAME) {
                xtgraphics.musicomp(checkpoints.stage, u[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.SELECTEDCARSAVE) {
                savecookie("usercar", "" + xtgraphics.sc[0]);

                for (int x = 0; x < GameFacts.numberOfPlayers; x++) {
                    amadness[x].stat = new Stat(xtgraphics.sc[x]);
                }
                xtgraphics.fase = Phase.PREGAMEMUSIC;
            }
            if (xtgraphics.fase == Phase.PREGAMEMUSIC) {
                xtgraphics.loadmusic(checkpoints.stage, i1);
            }
            if (xtgraphics.fase == Phase.LOCKEDSTAGE) {
                xtgraphics.cantgo(u[0]);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.ERRORLOADINGSTAGE) {
                xtgraphics.loadingfailed(checkpoints, u[0], stageError);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.NPLAYERSCHECK) {
                xtgraphics.carspergame(checkpoints);
            }
            if (xtgraphics.fase == Phase.STAGESELECTTRIGGER) {
                xtgraphics.loadingstage(checkpoints.stage);
                loadstage(aconto1, aconto, trackers, checkpoints, xtgraphics, amadness, record, false);
                u[0].falseo();
            }
            if (xtgraphics.fase == Phase.LOADSTAGE) { // for custom stage loading
                repaint();
                loadstage(aconto1, aconto, trackers, checkpoints, xtgraphics, amadness, record, true);
                xtgraphics.fase = Phase.STAGESELECT;
            }
            if (xtgraphics.fase == Phase.LOADSTAGE2) { // for custom stage loading
                repaint();
                loadstage(aconto1, aconto, trackers, checkpoints, xtgraphics, amadness, record, true);
                xtgraphics.loadmusic(checkpoints.stage, i1);
            }
            if (xtgraphics.fase == Phase.STAGESELECT) {
                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                xtgraphics.trackbg(true);
                Medium.d(rd);
                Medium.aroundTrack(checkpoints);
                int i3 = 0;
                int ai[] = new int[200];
                for (int k5 = GameFacts.numberOfPlayers; k5 < notb; k5++)
                    if (aconto1[k5].dist != 0) {
                        ai[i3] = k5;
                        i3++;
                    } else {
                        aconto1[k5].d(rd);
                    }

                int ai5[] = new int[i3];
                for (int j7 = 0; j7 < i3; j7++)
                    ai5[j7] = 0;

                for (int k7 = 0; k7 < i3; k7++) {
                    for (int i11 = k7 + 1; i11 < i3; i11++)
                        if (aconto1[ai[k7]].dist != aconto1[ai[i11]].dist) {
                            if (aconto1[ai[k7]].dist < aconto1[ai[i11]].dist)
                                ai5[k7]++;
                            else
                                ai5[i11]++;
                        } else if (i11 > k7)
                            ai5[k7]++;
                        else
                            ai5[i11]++;

                }

                for (int l7 = 0; l7 < i3; l7++) {
                    for (int j11 = 0; j11 < i3; j11++)
                        if (ai5[j11] == l7)
                            aconto1[ai[j11]].d(rd);

                }

                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
                xtgraphics.stageselect(checkpoints, u[0]);
            }
            if (xtgraphics.fase == Phase.DRAWENVIRONMENT) {
                Medium.d(rd);
                drawWorldObjects(rd); // aconto1 is sceneObjects
                Medium.follow(aconto1[0], 0, 0);
                xtgraphics.hipnoload(checkpoints.stage, false);
                if (i1 != 0) {
                    i1--;
                } else {
                    u[0].enter = false;
                    u[0].handb = false;

                    setCursor(new Cursor(0));
                    xtgraphics.fase = Phase.PREGAME;
                }
            }
            if (autotestHighlightRequested && xtgraphics.fase == Phase.INGAME && xtgraphics.starcnt == 0) {
                /*
                 * Smoke test only: end the race as if it had just been won after a
                 * stunt, so the post-race highlight gets exercised.
                 */
                autotestHighlightRequested = false;
                record.cotchinow(0);
                xtgraphics.looped = 0;
                xtgraphics.fase = Phase.CAUGHTHIGHLIGHT;
            }
            if (autotestRepairRequested && xtgraphics.fase == Phase.INGAME && xtgraphics.starcnt == 0) {
                // Smoke test only: what a repair hoop does to a visible car.
                autotestRepairRequested = false;
                aconto1[0].fix = true;
            }
            if (xtgraphics.fase == Phase.INGAME) {
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
                if (xtgraphics.starcnt >= 38) {
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
            if (xtgraphics.fase == Phase.INSTANTREPLAY) {
                /*
                 * Drawn by the presentation pass, like a race. The caption goes
                 * to the HUD layer.
                 */
                snapshotFrameState();
                if (k1 == 0) {
                    prevFrameValid = false;
                }
                beginOverlayTick();
                if (k1 == 0) {
                    int i4 = 0;
                    do {
                        record.ocar[i4] = new ContO(aconto1[i4], 0, 0, 0, 0);
                        aconto1[i4] = new ContO(record.car[0][i4], 0, 0, 0, 0);
                    } while (++i4 < GameFacts.numberOfPlayers);
                }
                drawSimulationStep();

                if (u[0].enter || u[0].handb || mouses == 1) {
                    k1 = 299;
                    u[0].enter = false;
                    u[0].handb = false;
                    mouses = 0;
                }
                int l9 = 0;
                do {
                    if (record.fix[l9] == k1)
                        if (aconto1[l9].dist == 0)
                            aconto1[l9].fcnt = 8;
                        else
                            aconto1[l9].fix = true;
                    if (aconto1[l9].fcnt == 7 || aconto1[l9].fcnt == 8) {
                        aconto1[l9] = new ContO(aconto[amadness[l9].cn], 0, 0, 0, 0);
                        record.cntdest[l9] = 0;
                    }
                    if (k1 == 299)
                        aconto1[l9] = new ContO(record.ocar[l9], 0, 0, 0, 0);
                    record.play(aconto1[l9], amadness[l9], l9, k1);
                } while (++l9 < GameFacts.numberOfPlayers);
                if (++k1 == 300) {
                    k1 = 0;
                    xtgraphics.fase = Phase.PAUSETRIGGER;
                } else {
                    xtgraphics.replyn();
                }
                Medium.around(aconto1[0], false);
                endOverlayTick();
            }
            if (xtgraphics.fase == Phase.CAUGHTHIGHLIGHT) {
                if (record.hcaught && record.wasted == 0 && record.whenwasted != 229 && checkpoints.stage <= 2
                        && xtgraphics.looped != 0)
                    record.hcaught = false;
                if (record.hcaught) {
                    Medium.vert = Medium.fxRandom() <= 0.45000000000000001D;
                    Medium.adv = (int) (900F * Medium.fxRandom());
                    Medium.vxz = (int) (360F * Medium.fxRandom());
                    k1 = 0;
                    xtgraphics.fase = Phase.GAMEHIGHLIGHT;
                    i2 = 0;
                    j2 = 0;
                } else {
                    k1 = -2;
                    xtgraphics.fase = Phase.POSTGAMEFADEOUT;
                }
            }
            if (xtgraphics.fase == Phase.GAMEHIGHLIGHT) {
                /*
                 * Drawn by the presentation pass, like a race. Captions and the
                 * black and white flashes go to the HUD layer.
                 */
                snapshotFrameState();
                if (k1 == 0) {
                    prevFrameValid = false;
                }
                beginOverlayTick();
                if (k1 == 0) {
                    if (record.wasted == 0) {
                        if (record.whenwasted == 229) {
                            k2 = 67;
                            Medium.vxz += 90;
                        } else {
                            k2 = (int) (Medium.fxRandom() * 4F);
                            if (k2 == 1 || k2 == 3)
                                k2 = 69;
                            if (k2 == 2 || k2 == 4)
                                k2 = 30;
                        }
                    } else if (record.closefinish != 0 && j2 != 0)
                        Medium.vxz += 90;
                    int k4 = 0;
                    do
                        aconto1[k4] = new ContO(record.starcar[k4], 0, 0, 0, 0);
                    while (++k4 < GameFacts.numberOfPlayers);
                }
                drawSimulationStep();

                int l10 = 0;
                do {
                    if (record.hfix[l10] == k1)
                        if (aconto1[l10].dist == 0)
                            aconto1[l10].fcnt = 8;
                        else
                            aconto1[l10].fix = true;
                    if (aconto1[l10].fcnt == 7 || aconto1[l10].fcnt == 8) {
                        aconto1[l10] = new ContO(aconto[amadness[l10].cn], 0, 0, 0, 0);
                        record.cntdest[l10] = 0;
                    }
                    record.playh(aconto1[l10], amadness[l10], l10, k1);
                } while (++l10 < GameFacts.numberOfPlayers);
                if (j2 == 2 && k1 == 299)
                    u[0].enter = true;
                if (u[0].enter || u[0].handb) {
                    xtgraphics.fase = Phase.POSTGAMEFADEOUT;
                    u[0].enter = false;
                    u[0].handb = false;
                    k1 = -7;
                } else {
                    xtgraphics.levelhigh(record.wasted, record.whenwasted, record.closefinish, k1, checkpoints.stage);
                    if (k1 == 0 || k1 == 1 || k1 == 2) {
                        rd.setColor(new Color(0, 0, 0));
                        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                    }
                    if (record.wasted != 0) {
                        if (record.closefinish == 0) {
                            if (i2 == 9 || i2 == 11) {
                                rd.setColor(new Color(255, 255, 255));
                                rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                            }
                            if (i2 == 0)
                                Medium.around(aconto1[0], false);
                            if (i2 > 0 && i2 < 20)
                                Medium.transaround(aconto1[0], aconto1[record.wasted], i2);
                            if (i2 == 20)
                                Medium.around(aconto1[record.wasted], false);
                            if (k1 > record.whenwasted && i2 != 20)
                                i2++;
                            if ((i2 == 0 || i2 == 20) && ++k1 == 300) {
                                k1 = 0;
                                i2 = 0;
                                j2++;
                            }
                        } else if (record.closefinish == 1) {
                            if (i2 == 0)
                                Medium.around(aconto1[0], false);
                            if (i2 > 0 && i2 < 20)
                                Medium.transaround(aconto1[0], aconto1[record.wasted], i2);
                            if (i2 == 20)
                                Medium.around(aconto1[record.wasted], false);
                            if (i2 > 20 && i2 < 40)
                                Medium.transaround(aconto1[record.wasted], aconto1[0], i2 - 20);
                            if (i2 == 40)
                                Medium.around(aconto1[0], false);
                            if (i2 > 40 && i2 < 60)
                                Medium.transaround(aconto1[0], aconto1[record.wasted], i2 - 40);
                            if (i2 == 60)
                                Medium.around(aconto1[record.wasted], false);
                            if (k1 > 160 && i2 < 20)
                                i2++;
                            if (k1 > 230 && i2 < 40)
                                i2++;
                            if (k1 > 280 && i2 < 60)
                                i2++;
                            if ((i2 == 0 || i2 == 20 || i2 == 40 || i2 == 60) && ++k1 == 300) {
                                k1 = 0;
                                i2 = 0;
                                j2++;
                            }
                        } else {
                            if (i2 == 0)
                                Medium.around(aconto1[0], false);
                            if (i2 > 0 && i2 < 20)
                                Medium.transaround(aconto1[0], aconto1[record.wasted], i2);
                            if (i2 == 20)
                                Medium.around(aconto1[record.wasted], false);
                            if (i2 > 20 && i2 < 40)
                                Medium.transaround(aconto1[record.wasted], aconto1[0], i2 - 20);
                            if (i2 == 40)
                                Medium.around(aconto1[0], false);
                            if (i2 > 40 && i2 < 60)
                                Medium.transaround(aconto1[0], aconto1[record.wasted], i2 - 40);
                            if (i2 == 60)
                                Medium.around(aconto1[record.wasted], false);
                            if (i2 > 60 && i2 < 80)
                                Medium.transaround(aconto1[record.wasted], aconto1[0], i2 - 60);
                            if (i2 == 80)
                                Medium.around(aconto1[0], false);
                            if (k1 > 90 && i2 < 20)
                                i2++;
                            if (k1 > 160 && i2 < 40)
                                i2++;
                            if (k1 > 230 && i2 < 60)
                                i2++;
                            if (k1 > 280 && i2 < 80)
                                i2++;
                            if ((i2 == 0 || i2 == 20 || i2 == 40 || i2 == 60 || i2 == 80) && ++k1 == 300) {
                                k1 = 0;
                                i2 = 0;
                                j2++;
                            }
                        }
                    } else {
                        if (k2 == 67 && (i2 == 3 || i2 == 31 || i2 == 66)) {
                            rd.setColor(new Color(255, 255, 255));
                            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                        }
                        if (k2 == 69 && (i2 == 3 || i2 == 5 || i2 == 31 || i2 == 33 || i2 == 66 || i2 == 68)) {
                            rd.setColor(new Color(255, 255, 255));
                            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                        }
                        if (k2 == 30 && i2 >= 1 && i2 < 30)
                            if (i2 % (int) (2.0F + Medium.fxRandom() * 3F) == 0 && !flag2) {
                                rd.setColor(new Color(255, 255, 255));
                                rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                                flag2 = true;
                            } else {
                                flag2 = false;
                            }
                        if (k1 > record.whenwasted && i2 != k2)
                            i2++;
                        Medium.around(aconto1[0], false);
                        if ((i2 == 0 || i2 == k2) && ++k1 == 300) {
                            k1 = 0;
                            i2 = 0;
                            j2++;
                        }
                    }
                }
                endOverlayTick();
            }
            if (xtgraphics.fase == Phase.POSTGAMEFADEOUT) {
                if (k1 <= 0) {
                    rd.drawImage(xtgraphics.mdness, 224, 30, null);
                    rd.drawImage(xtgraphics.dude[0], 70, 10, null);
                }
                if (k1 >= 0) {
                    syncBackBuffer();
                    xtgraphics.fleximage(offImage, k1, checkpoints.stage);
                }
                k1++;
                if (checkpoints.stage == GameFacts.numberOfStages && k1 == 10)
                    xtgraphics.fase = Phase.POSTGAME;
                if (k1 == 12)
                    xtgraphics.fase = Phase.POSTGAME;
            }
            if (xtgraphics.fase == Phase.PAUSETRIGGER) {
                repaint();
                syncBackBuffer();
                xtgraphics.pauseimage(offImage);
                xtgraphics.fase = Phase.PAUSEMENU;
                mouses = 0;
            }
            if (xtgraphics.fase == Phase.PAUSEMENU) {
                xtgraphics.pausedgame(checkpoints.stage, u[0], record);
                if (k1 != 0)
                    k1 = 0;
                xtgraphics.ctachm(xm, ym, mouses, u[0]);
                if (mouses == 2)
                    mouses = 0;
                if (mouses == 1)
                    mouses = 2;
            }
            if (xtgraphics.fase == Phase.NOTENOUGHREPLAYDATA) {
                xtgraphics.cantreply();
                if (++k1 == 150 || u[0].enter || u[0].handb || mouses == 1) {
                    xtgraphics.fase = Phase.PAUSEMENU;
                    mouses = 0;
                    u[0].enter = false;
                    u[0].handb = false;
                }
            }
            if (lostfcs && xtgraphics.fase != Phase.DRAWENVIRONMENT && xtgraphics.fase != Phase.LOADING) {
                if (xtgraphics.fase == Phase.INGAME)
                    u[0].enter = false;
                else
                    xtgraphics.nofocus();
                if (mouses == 1 || mouses == 2)
                    lostfcs = false;
            }
            xtgraphics.playsounds(amadness[0], u[0], checkpoints.stage);
            /*
             * The original loop passed the adaptive sleep target here. Feeding a
             * constant keeps the fog distance at the stage's intended value.
             */
            Medium.adjustFade(currentTickNanos / 1_000_000F);
            if (isScenePhase(xtgraphics.fase)) {
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
                xtgraphics.stopallnow();
                System.gc();
                gamer = null;
                return;
            }

            // if (xtgraphics.devtriggered) {

            // this could be useful for discord rich presence
            switch (xtgraphics.fase) {
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
            boolean scene = isScenePhase(xtgraphics.fase);
            boolean frameDue = renderNanos == 0L || frameNow - nextRenderNanos >= 0L;
            /*
             * Races on the graphics card are drawn by the presenter on its own
             * thread, at the display's rate, from the scene kept for the tick:
             * this thread only keeps it up to date, so a tick never holds a
             * frame back.
             */
            boolean presented = presenterDraws(scene, unpaced) && handOverKept();
            if (presenter != null) {
                presenter.drawRaces(presented);
            }
            if (!presented) {
                keptStale = true; // the presenter starts again from a scene handed over then
            }
            if (!unpaced && !presented && (scene ? frameDue : ticked)) {
                if (renderNanos > 0L) {
                    nextRenderNanos += renderNanos;
                    if (frameNow - nextRenderNanos > renderNanos * 4L || nextRenderNanos - frameNow > renderNanos) {
                        // fell far behind, or the limit was raised: restart the schedule
                        nextRenderNanos = frameNow + renderNanos;
                    }
                }

                gpuSceneFrame = scene && GPU_SCENE && gpuActive && !xtgraphics.debugmode;
                if (scene) {
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
                            rd.drawImage(hudImage, 0, 0, null);
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

                if (xtgraphics.fase != Phase.LOADING && xtgraphics.debugmode) {
                    xtgraphics.gameMetrics.addFrameTimeSample((int) ((frameNow - lastRenderAt) / 1_000_000L));
                    xtgraphics.gameMetrics.render(rd);
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
                        profileAheadChecked, profileAheadMissed, describeFpsLimit(), xtgraphics.fase);
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
                    System.out.printf("[pace] frame gaps: median %.1f ms, 99%% %.1f ms, longest %.1f ms; %d of %d frames came late%n",
                            median / 1e6D, gaps[Math.min(gaps.length - 1, gaps.length * 99 / 100)] / 1e6D,
                            gaps[gaps.length - 1] / 1e6D, hitches, gaps.length);
                }
                profileGapCount = 0;
                profileRecords = 0;
                profileRecordNanos = 0L;
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
            } else if (isScenePhase(xtgraphics.fase)) {
                if (renderNanos == 0L) {
                    Thread.yield();
                    continue;
                } else if (nextRenderNanos - wakeAt < 0L) {
                    wakeAt = nextRenderNanos;
                }
            }
            try {
                frameSleep.sleepUntil(wakeAt);
            } catch (InterruptedException _ex) {
                // shutdown is signalled through exwist, not interrupts
            }
        } while (true);
    }

    public void init() {
        /*
         * load some fonts
         */
        new FontHandler();

        offImage = createImage(GameFacts.screenWidth, GameFacts.screenHeight);
        /*
         * Must be a real ARGB buffer: Component.createImage() returns an opaque
         * display-compatible image, which would hide the scene behind the HUD.
         */
        hudImage = new BufferedImage(GameFacts.screenWidth, GameFacts.screenHeight, BufferedImage.TYPE_INT_ARGB);
        if (offImage != null) {
            sg = offImage.getGraphics();
            rd = ((Graphics2D) sg);
            rd.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            rd.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            rd.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (hudImage != null) {
            hudG = (Graphics2D) hudImage.getGraphics();
            hudG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hudG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hudG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            hudG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
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

    private void openurl(final String string) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(string));
            } catch (final Exception exception) {

            }
        } else {
            try {
                Runtime.getRuntime().exec("" + urlopen() + " " + string + "");
            } catch (final Exception exception) {

            }
        }
    }

    private static String urlopen() {
        String string = "explorer";
        final String string27 = System.getProperty("os.name").toLowerCase();
        if (string27.contains("linux") || string27.contains("unix") || string27.equals("aix")) {
            string = "xdg-open";
        }
        if (string27.contains("mac")) {
            string = "open";
        }
        return string;
    }

    private void catchlink(int i, xtGraphics xtg) {
        if (!lostfcs) {
            if (i == 0)
                if (xm > 0 && xm < GameFacts.screenWidth && ym > 110 && ym < 169
                        || xm > Utility.centeredImageX(xtg.rpro)
                                && xm < Utility.centeredImageX(xtg.rpro) + xtg.rpro.getWidth(null) && ym > 240
                                && ym < 259) {
                    setCursor(new Cursor(12));
                    if (mouses == 2)
                        openurl("http://www.radicalplay.com/");
                } else {
                    setCursor(new Cursor(0));
                }
            if (i == 1)
                if (xm > 0 && xm < GameFacts.screenWidth && ym > 205 && ym < 267) {
                    setCursor(new Cursor(12));
                    if (mouses == 2)
                        openurl("http://www.radicalplay.com/");
                } else {
                    setCursor(new Cursor(0));
                }
        }
    }

    @Override
    public boolean mouseMove(Event event, int i, int j) {
        if (!exwist && !lostfcs) {
            xm = toPictureX(i);
            ym = toPictureY(j);
        }
        return false;
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
        public xtGraphics ui() {
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
                keyDown(null, code);
            } else {
                keyUp(null, code);
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
                rd.drawImage(hudImage, 0, 0, null);
            }
            return captureFrame();
        }

        @Override
        public int sceneCount() {
            return nob;
        }
    }
}