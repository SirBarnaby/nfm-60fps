package nfm.lit;
import fallk.logmaster.HLogger;
import nfm.lit.audio.BASSLoader;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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

    private Image hudImage;
    private Graphics2D hudG;

    /** Objects rendered by the in-game pass (was a local: aconto1). */
    private ContO[] sceneObjects;
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
    private BufferedImage capturedFrame;

    private final int[] drawOrder = new int[200];
    private final int[] drawRank = new int[200];
    private final int[] drawIndices = new int[200];

    /* variables for screen shake */

    private int shaka = 0;
    private int apx = 0;
    private int apy = 0;

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
                if (i == 10 || i == 80 || i == 112 || i == 27)
                    liveEnter = true;
                if (i == 118 || i == 86) {
                    liveView++;
                    if (liveView == 3)
                        liveView = 0;
                }
                if (!latching) {
                    applyLiveKeys();
                }
            }
            if (i == 77 || i == 109)
                u[0].mutem = !u[0].mutem;
            if (i == 78 || i == 110)
                u[0].mutes = !u[0].mutes;
            if (i == 97 || i == 65)
                u[0].arrace = !u[0].arrace;
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

    private void cropit(final Graphics2D graphics2d, final int i, final int i_98_) {
        if (i != 0 || i_98_ != 0) {
            graphics2d.setComposite(AlphaComposite.getInstance(3, 1.0F));
            graphics2d.setColor(new Color(0, 0, 0));
        }
        if (i != 0) {
            if (i < 0) {
                graphics2d.fillRect(apx + i, apy - (int) (25.0F), Math.abs(i), (int) (720.0F));
            } else {
                graphics2d.fillRect(apx + (int) (1280.0F), apy - (int) (25.0F), i, (int) (720.0F));
            }
        }
        if (i_98_ != 0) {
            if (i_98_ < 0) {
                graphics2d.fillRect(apx - (int) (25.0F), apy + i_98_, (int) (1280.0F + 50.F), Math.abs(i_98_));
            } else {
                graphics2d.fillRect(apx - (int) (25.0F), apy + (int) (720.0F + 50.0F), (int) (1280.0F + 50.0F), i_98_);
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D graphics2d = (Graphics2D) g;
        int i = 0;
        int i_97_ = 0;
        if (this.shaka > 10) {
            i = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
            i_97_ = (int) ((double) (this.shaka * 2) * Math.random() - (double) this.shaka);
            this.shaka -= 5;
        }

        this.apx = (int) ((float) (this.getWidth() / 2) - GameFacts.screenWidth/2);
        this.apy = (int) ((float) (this.getHeight() / 2) - GameFacts.screenHeight/2);
        graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2d.drawImage(this.offImage, this.apx + i, this.apy + i_97_, this);
        this.cropit(graphics2d, i, i_97_);
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
            xm = i;
            ym = j;
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
        Medium.seedRace(session != null ? session.stageLoading(custom) : RaceRandom.newSeed());
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
    private void drawWorldObjects(Graphics2D g) {
        int count = 0;
        for (int i = 0; i < nob; i++) {
            if (sceneObjects[i].dist != 0) {
                drawOrder[count++] = i;
            } else {
                sceneObjects[i].d(g);
            }
        }

        for (int i = 0; i < count; i++) {
            drawRank[i] = 0;
        }
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (sceneObjects[drawOrder[i]].dist != sceneObjects[drawOrder[j]].dist) {
                    if (sceneObjects[drawOrder[i]].dist < sceneObjects[drawOrder[j]].dist) {
                        drawRank[i]++;
                    } else {
                        drawRank[j]++;
                    }
                } else if (j > i) {
                    drawRank[i]++;
                } else {
                    drawRank[j]++;
                }
            }
            drawIndices[drawRank[i]] = i;
        }

        for (int i = 0; i < count; i++) {
            sceneObjects[drawOrder[drawIndices[i]]].d(g);
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
            }
            if (!teleported(camX - prevCamX, camY - prevCamY, camZ - prevCamZ)) {
                Medium.x = mix(prevCamX, camX, alpha);
                Medium.y = mix(prevCamY, camY, alpha);
                Medium.z = mix(prevCamZ, camZ, alpha);
                Medium.xz = mixAngle(prevCamXZ, camXZ, alpha);
                Medium.zy = mixAngle(prevCamZY, camZY, alpha);
            }
        }

        saveSceneDrawState();
        try {
            Medium.d(rd);
            drawWorldObjects(rd);
        } finally {
            restoreSceneDrawState();
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
    private void presentFrame() {
        Graphics graphics = getGraphics();
        if (graphics != null) {
            try {
                paint(graphics);
            } finally {
                graphics.dispose();
            }
        }
        Toolkit.getDefaultToolkit().sync();
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
    private void saveFrame(File directory, String name) {
        capturedFrame = null;
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
            amadness[l] = new Madness(record, xtgraphics, l);
            u[l] = new Control();
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
        ui = xtgraphics;
        raceCheckpoints = checkpoints;
        raceRecord = record;
        raceTrackers = trackers;

        loadFpsLimit();
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

        long nextTickNanos = System.nanoTime() + tickNanosFor(xtgraphics.fase);
        long nextRenderNanos = System.nanoTime();
        long lastProfileFlush = nextRenderNanos;
        long lastRenderAt = nextRenderNanos;
        int profileTicks = 0;
        int profileFrames = 0;
        long profileTickNanos = 0L;
        long profileRenderNanos = 0L;

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
                ticksThisFrame++;
                profileTicks++;
                saveFpsLimitIfChanged();
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
                int j3 = 0;
                int ai1[] = new int[200];
                for (int i6 = 0; i6 < nob; i6++)
                    if (aconto1[i6].dist != 0) {
                        ai1[j3] = i6;
                        j3++;
                    } else {
                        aconto1[i6].d(rd);
                    }

                int ai6[] = new int[j3];
                for (int i8 = 0; i8 < j3; i8++)
                    ai6[i8] = 0;

                for (int j8 = 0; j8 < j3; j8++) {
                    for (int k11 = j8 + 1; k11 < j3; k11++)
                        if (aconto1[ai1[j8]].dist != aconto1[ai1[k11]].dist) {
                            if (aconto1[ai1[j8]].dist < aconto1[ai1[k11]].dist)
                                ai6[j8]++;
                            else
                                ai6[k11]++;
                        } else if (k11 > j8)
                            ai6[j8]++;
                        else
                            ai6[k11]++;

                }

                for (int k8 = 0; k8 < j3; k8++) {
                    for (int l11 = 0; l11 < j3; l11++)
                        if (ai6[l11] == k8)
                            aconto1[ai1[l11]].d(rd);

                }

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

                int k3 = 0;
                do {
                    if (amadness[k3].newcar) {
                        int j5 = aconto1[k3].xz;
                        int j6 = aconto1[k3].xy;
                        int l8 = aconto1[k3].zy;
                        aconto1[k3] = new ContO(aconto[amadness[k3].cn], aconto1[k3].x, aconto1[k3].y, aconto1[k3].z,
                                0);
                        aconto1[k3].xz = j5;
                        aconto1[k3].xy = j6;
                        aconto1[k3].zy = l8;
                        amadness[k3].newcar = false;
                    }
                } while (++k3 < GameFacts.numberOfPlayers);

                drawSimulationStep();

                if (xtgraphics.starcnt == 0) {
                    int l12 = 0;
                    do {
                        int j14 = 0;
                        do {
                            if (j14 != l12) {
                                amadness[l12].colide(aconto1[l12], amadness[j14], aconto1[j14]);
                            }
                        } while (++j14 < GameFacts.numberOfPlayers);
                    } while (++l12 < GameFacts.numberOfPlayers);
                    l12 = 0;
                    do
                        amadness[l12].drive(u[l12], aconto1[l12], trackers, checkpoints);
                    while (++l12 < GameFacts.numberOfPlayers);
                    l12 = 0;
                    do
                        record.rec(aconto1[l12], l12, amadness[l12].squash, amadness[l12].lastcolido,
                                amadness[l12].cntdest);
                    while (++l12 < GameFacts.numberOfPlayers);
                    checkpoints.checkstat(amadness, aconto1, record, GameFacts.numberOfPlayers);

                    // This starts the AI code for all the cars.
                    l12 = 1;
                    do
                        u[l12].preform(amadness[l12], aconto1[l12], checkpoints, trackers, GameFacts.numberOfPlayers);
                    while (++l12 < GameFacts.numberOfPlayers);
                } else {
                    if (xtgraphics.starcnt == 130) {
                        Medium.adv = 1900;
                        Medium.zy = 40;
                        Medium.vxz = 70;
                        pendingWhiteFlash = true;
                    }
                    if (xtgraphics.starcnt != 0)
                        xtgraphics.starcnt--;
                }
                if (xtgraphics.starcnt < 38) {
                    if (view == 0) {
                        Medium.follow(aconto1[xtgraphics.spectate], amadness[xtgraphics.spectate].cxz,
                                u[xtgraphics.spectate].lookback);
                        renderHud(u[xtgraphics.spectate], true);
                        initMoto(amadness, 2, 5);
                    }
                    if (view == 1) {
                        Medium.around(aconto1[xtgraphics.spectate], false);
                        renderHud(u[xtgraphics.spectate], false);
                    }
                    if (view == 2) {
                        Medium.watch(aconto1[xtgraphics.spectate], amadness[0].mxz);
                        renderHud(u[xtgraphics.spectate], false);
                    }
                    if (mouses == 1) {
                        u[0].enter = true;
                        mouses = 0;
                    }
                    if (xtgraphics.starcnt == 36) {
                        /*
                         * Tints the countdown portrait with the picture behind it.
                         * The back buffer holds the last presented frame, scene and
                         * HUD, which is what the original grabbed here too. It must
                         * run in this tick: the portrait is drawn from the next one,
                         * and a slow frame can let both ticks run back to back.
                         */
                        xtgraphics.blendude(offImage);
                    }
                } else {
                    if (GameFacts.numberOfPlayers < 5)
                        Medium.around(aconto1[0], true);
                    else
                        Medium.around(aconto1[3], true);
                    if (u[0].enter || u[0].handb) {
                        xtgraphics.starcnt = 38;
                        u[0].enter = false;
                        u[0].handb = false;
                    }
                    if (xtgraphics.starcnt == 38) {
                        if (session != null) {
                            session.countdownStarting();
                        }
                        Medium.startCountdown();
                        mouses = 0;
                        Medium.vert = false;
                        Medium.adv = GameFacts.screenWidth;
                        Medium.vxz = 180;
                        checkpoints.checkstat(amadness, aconto1, record, GameFacts.numberOfPlayers);
                        Medium.follow(aconto1[0], amadness[0].cxz, 0);
                        renderHud(u[0], true);
                        pendingWhiteFlash = true;
                    }
                }
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
                if (k1 >= 0)
                    xtgraphics.fleximage(offImage, k1, checkpoints.stage);
                k1++;
                if (checkpoints.stage == GameFacts.numberOfStages && k1 == 10)
                    xtgraphics.fase = Phase.POSTGAME;
                if (k1 == 12)
                    xtgraphics.fase = Phase.POSTGAME;
            }
            if (xtgraphics.fase == Phase.PAUSETRIGGER) {
                repaint();
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
            if (!unpaced && (scene ? frameDue : ticked)) {
                if (renderNanos > 0L) {
                    nextRenderNanos += renderNanos;
                    if (frameNow - nextRenderNanos > renderNanos * 4L || nextRenderNanos - frameNow > renderNanos) {
                        // fell far behind, or the limit was raised: restart the schedule
                        nextRenderNanos = frameNow + renderNanos;
                    }
                }

                if (scene) {
                    double elapsed = (double) (frameNow - tickStartNanos) / (double) currentTickNanos;
                    if (!prevFrameValid || elapsed >= 1D) {
                        renderAlpha = 1F;
                    } else if (elapsed <= 0D) {
                        renderAlpha = 0F;
                    } else {
                        renderAlpha = (float) elapsed;
                    }

                    renderIngameScene(renderAlpha);

                    if (hudImage != null) {
                        rd.drawImage(hudImage, 0, 0, null);
                    }
                    if (pendingWhiteFlash) {
                        rd.setColor(new Color(255, 255, 255));
                        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
                        pendingWhiteFlash = false;
                    }
                }

                if (captureRequested) {
                    capturedFrame = captureFrame();
                    captureRequested = false;
                }

                if (xtgraphics.fase != Phase.LOADING && xtgraphics.debugmode) {
                    xtgraphics.gameMetrics.addFrameTimeSample((int) ((frameNow - lastRenderAt) / 1_000_000L));
                    xtgraphics.gameMetrics.render(rd);
                }
                lastRenderAt = frameNow;
                profileFrames++;

                presentFrame();
                profileRenderNanos += System.nanoTime() - frameNow;
            }

            if (PROFILE_LOOP && frameNow - lastProfileFlush >= profileWindowNanos) {
                double seconds = (frameNow - lastProfileFlush) / 1e9D;
                System.out.printf("[loop] sim %.1f Hz (%.1f ms/tick) | render %.1f fps (%.1f ms/frame) | limit %s | %s%n",
                        profileTicks / seconds, profileTicks == 0 ? 0D : profileTickNanos / 1e6D / profileTicks,
                        profileFrames / seconds, profileFrames == 0 ? 0D : profileRenderNanos / 1e6D / profileFrames,
                        describeFpsLimit(), xtgraphics.fase);
                profileTicks = 0;
                profileFrames = 0;
                profileTickNanos = 0L;
                profileRenderNanos = 0L;
                lastProfileFlush = frameNow;
            }

            /*
             * Sleep until the next tick or frame is due. Short sleeps keep the
             * cadence accurate: Thread.sleep() can overshoot by a millisecond or
             * more, so the last stretch is spent yielding. With no frame limit a
             * race redraws continuously and only yields.
             */
            if (unpaced) {
                continue; // a verify or corpus run: next tick right away
            }
            long wakeAt = nextTickNanos;
            if (isScenePhase(xtgraphics.fase)) {
                if (renderNanos == 0L) {
                    wakeAt = System.nanoTime();
                } else if (nextRenderNanos - wakeAt < 0L) {
                    wakeAt = nextRenderNanos;
                }
            }
            long sleepNanos = wakeAt - System.nanoTime();
            if (sleepNanos > 2_000_000L) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000L - 1L);
                } catch (InterruptedException _ex) {
                    // shutdown is signalled through exwist, not interrupts
                }
            } else {
                Thread.yield();
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
            xm = i;
            ym = j;
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
            return new RaceState.View(racers, u, sceneObjects, raceCheckpoints, raceRecord, ui, raceTrackers,
                    GameFacts.numberOfPlayers);
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
    }
}