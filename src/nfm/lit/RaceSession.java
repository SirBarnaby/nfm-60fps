package nfm.lit;

import fallk.logmaster.HLogger;
import nfm.lit.audio.SoundManager;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Developer tooling that records races and plays them back to prove the game
 * is deterministic.
 * <ul>
 * <li>{@code -Dnfm.record=<folder>} saves every race played to a {@code .nfmr}
 * file: the seed, set-up, the player's input each tick and a hash of the race
 * state after each tick ({@link RaceLog}).</li>
 * <li>{@code -Dnfm.autopilot=<races>} (with {@code nfm.record}) plays that many
 * races by itself in a hidden, silent window, for building a test corpus.</li>
 * <li>{@code -Dnfm.verify=<file or folder>} plays recorded races again in a
 * hidden, silent window, from their input alone, and checks every tick. It
 * prints the first tick that differs, the car or part of the race that
 * differed first, and the exact fields, and exits with 0 when every race
 * matched, 1 when one did not and 2 when one could not be run.</li>
 * </ul>
 * A normal game keeps its races too, as replays in the {@code replays} folder
 * next to the game (see {@link Replays}): every race until there are
 * {@link Replays#KEEP_LATEST} of them, and the fastest finish on each stage.
 * <p>
 * Every hook runs on the game thread at a fixed point of the tick, so the
 * session never races the simulation.
 */
final class RaceSession {

    /** What the session needs from the game. */
    interface Host {
        Phase phase();

        void setPhase(Phase phase);

        RaceState.View race();

        xtGraphics ui();

        CheckPoints checkpoints();

        /** The player's controls as the current tick reads them. */
        Control player();

        /** A key press or release, delivered as the keyboard would. */
        void key(int code, boolean down);

        /** A mouse click, as the menus read it. */
        void click();

        int cameraView();

        void setCameraView(int view);

        /** The race as it would be presented now, scene and HUD, without changing anything. */
        BufferedImage frame();

        /** How many of {@link RaceState.View#cars()} are on the track now: the cars, then the stage. */
        int sceneCount();
    }

    private static final String TAG_RECORD = "[record] ";
    private static final String TAG_VERIFY = "[verify] ";
    /* AWT key codes the autopilot presses, as GameSparker.keyDown reads them. */
    private static final int CODE_ENTER = 10;
    private static final int CODE_SPACE = 32;
    private static final int CODE_UP = 1004;
    private static final int CODE_DOWN = 1005;
    private static final int CODE_LEFT = 1006;
    private static final int CODE_RIGHT = 1007;
    private static final int CODE_LOOK_LEFT = 122;
    private static final int CODE_LOOK_RIGHT = 120;
    private static final int CODE_VIEW = 118;
    /** Pause menu entries. */
    private static final int MENU_CONTINUE = 0;
    private static final int MENU_REPLAY = 1;
    private static final int MENU_QUIT = 3;
    /** A verify run that makes no progress for this many ticks is stuck. */
    private static final int STUCK_TICKS = 30_000;
    private static final int REPORT_FIELDS = 25;
    /**
     * Self-test of the verifier: {@code -Dnfm.verify.perturb=<tick>} changes
     * the player's input on that tick, which must be reported as a mismatch.
     */
    private static final int PERTURB_TICK = Integer.getInteger("nfm.verify.perturb", -1);
    /**
     * {@code -Dnfm.verify.canonical=true} compares only the canonical race
     * state, as happens on its own once the code has been reorganised.
     */
    private static final boolean CANONICAL_ONLY = Boolean.getBoolean("nfm.verify.canonical");
    /**
     * {@code -Dnfm.verify.trace=<tick>} writes every named value after that
     * tick to a text file, for comparing two runs of the same race when the
     * stored dumps are too far apart to show where a difference started.
     */
    private static final int TRACE_TICK = Integer.getInteger("nfm.verify.trace", -1);
    /**
     * Rollback self-test: {@code -Dnfm.verify.rollback=<n>} captures the race
     * every {@code n} ticks, plays {@link #ROLLBACK_AHEAD} ticks with the
     * steering and throttle reversed, rewinds and carries on. The race must
     * still match the recording, which proves {@link RaceSnapshot} holds
     * everything a race depends on.
     */
    private static final int ROLLBACK_EVERY = Integer.getInteger("nfm.verify.rollback", 0);
    private static final int ROLLBACK_AHEAD = 8;
    private static final int DRIVING_KEYS = RaceLog.KEY_UP | RaceLog.KEY_DOWN | RaceLog.KEY_LEFT
            | RaceLog.KEY_RIGHT | RaceLog.KEY_HANDBRAKE;

    /** Phases that are part of a race in progress. */
    private static final Set<Phase> RACE_PHASES = EnumSet.of(Phase.INGAME, Phase.PAUSETRIGGER, Phase.PAUSEMENU,
            Phase.INSTANTREPLAY, Phase.NOTENOUGHREPLAYDATA, Phase.INSTRUCTIONS);
    /** Phases a race reaches by being won or lost rather than quit. */
    private static final Set<Phase> FINISH_PHASES = EnumSet.of(Phase.CAUGHTHIGHLIGHT, Phase.GAMEHIGHLIGHT,
            Phase.POSTGAMEFADEOUT, Phase.POSTGAME);

    private final Host host;
    private final File recordFolder;
    private final Autopilot autopilot;
    private final Verifier verifier;
    private final boolean unpaced;
    /** The player's own races, kept as replays rather than for the determinism check. */
    private boolean replays;

    /* Recording. */
    private RaceLog log;
    private boolean racing;
    private boolean customStage;
    private int[] carsBefore = new int[0];
    private int mountainsBefore;
    private Phase lastPhase;
    private int pendingKeys;
    private int pendingLookback;
    private int pendingView;
    private int pendingSpectate;
    /** Between the start and the end of an in-game tick: its sound commands go to the log. */
    private boolean inTick;

    private RaceSession(Host host, File recordFolder, Autopilot autopilot, Verifier verifier, boolean unpaced) {
        this.host = host;
        this.recordFolder = recordFolder;
        this.autopilot = autopilot;
        this.verifier = verifier;
        this.unpaced = unpaced;
    }

    /** True for the runs that must stay off the user's screen and speakers. */
    static boolean runsHidden() {
        return System.getProperty("nfm.verify") != null
                || System.getProperty("nfm.record") != null && System.getProperty("nfm.autopilot") != null;
    }

    /** Process-wide set-up, before the game loads its sounds. */
    static void prepareProcess() {
        if (runsHidden()) {
            SoundManager.silenceAll();
        }
    }

    /** Hidden runs go as fast as the processor allows unless {@code -Dnfm.unpaced=false}. */
    private static boolean unpacedRequested() {
        return !"false".equals(System.getProperty("nfm.unpaced"));
    }

    /**
     * A hidden run has nobody to notice a frozen game, and the window keeps the
     * process alive after the game thread dies. So a crash on the game thread
     * is reported and ends the run with exit code 2.
     */
    private static void exitOnCrash(String tag) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, ex) -> {
            System.out.println(tag + "ERROR: the game crashed: " + ex);
            StackTraceElement[] frames = ex.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 8); i++) {
                System.out.println(tag + "    at " + frames[i]);
            }
            System.exit(2);
        });
    }

    /**
     * The session the command line asks for or, for a normal game, the one
     * that keeps its races as replays (null if the folder cannot be made).
     * Called on the game thread.
     */
    static RaceSession fromCommandLine(Host host) {
        if (runsHidden()) {
            exitOnCrash(System.getProperty("nfm.verify") != null ? TAG_VERIFY : TAG_RECORD);
        }
        String verify = System.getProperty("nfm.verify");
        if (verify != null) {
            List<File> files = raceFiles(new File(verify));
            if (files.isEmpty()) {
                System.out.println(TAG_VERIFY + "no recorded races in " + verify);
                System.exit(2);
            }
            RaceSession session = new RaceSession(host, null, null,
                    new Verifier(host, files, GoldenFrames.fromCommandLine()), unpacedRequested());
            SoundManager.setListener(session::heard);
            return session;
        }
        String record = System.getProperty("nfm.record");
        if (record == null) {
            if (System.getProperty("nfm.autotest") != null) {
                return null; // the smoke test's races are nobody's
            }
            File folder = Replays.folder();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                HLogger.warn("Cannot create " + folder.getAbsolutePath() + ", races are not kept as replays");
                return null;
            }
            RaceSession session = new RaceSession(host, folder, null, null, false);
            session.replays = true;
            SoundManager.setListener(session::heard);
            Runtime.getRuntime().addShutdownHook(new Thread(session::saveOnExit, "nfm-replay-save"));
            return session;
        }
        File folder = new File(record);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            System.out.println(TAG_RECORD + "cannot create " + folder);
            return null;
        }
        Autopilot pilot = null;
        boolean unpaced = false;
        String races = System.getProperty("nfm.autopilot");
        if (races != null) {
            long seed = Long.getLong("nfm.autopilot.seed", System.currentTimeMillis());
            pilot = new Autopilot(host, Integer.parseInt(races), seed, Integer.getInteger("nfm.autopilot.ticks", 2400));
            unpaced = unpacedRequested();
        }
        RaceSession session = new RaceSession(host, folder, pilot, null, unpaced);
        SoundManager.setListener(session::heard);
        Runtime.getRuntime().addShutdownHook(new Thread(session::saveOnExit, "nfm-record-save"));
        System.out.println(TAG_RECORD + "races -> " + folder.getAbsolutePath()
                + (pilot == null ? "" : " (autopilot, " + races + " races, " + (unpaced ? "unpaced" : "real time") + ")"));
        return session;
    }

    private static List<File> raceFiles(File path) {
        List<File> out = new ArrayList<>();
        if (path.isDirectory()) {
            File[] files = path.listFiles((dir, name) -> name.endsWith(".nfmr"));
            if (files != null) {
                out.addAll(Arrays.asList(files));
                Collections.sort(out);
            }
        } else if (path.isFile()) {
            out.add(path);
        }
        return out;
    }

    /** Runs without a visible window: the tick loop does not wait and nothing is presented. */
    boolean unpaced() {
        return unpaced;
    }

    /** The verifier and the autopilot must not overwrite the player's saved progress. */
    boolean keepsCookies() {
        return verifier != null || autopilot != null;
    }

    /* ------------------------------------------------------------------ hooks */

    /** Start of every tick, before the phase runs. */
    void beforeTick() {
        Phase phase = host.phase();
        if (verifier != null) {
            verifier.beforeTick(phase);
            return;
        }
        observe(phase);
        if (autopilot != null) {
            autopilot.beforeTick(phase, racing ? log.ticks() : -1);
        }
        lastPhase = phase;
    }

    /** A stage is about to be set up; returns the seed its race will use. */
    long stageLoading(boolean custom) {
        int[] sc = host.ui().sc;
        carsBefore = Arrays.copyOf(sc, sc.length);
        customStage = custom;
        if (verifier != null) {
            return verifier.stageLoading();
        }
        mountainsBefore = Medium.mgen;
        return RaceRandom.newSeed();
    }

    /** The stage is set up and the opponents are chosen. */
    void stageLoaded() {
        xtGraphics ui = host.ui();
        int players = GameFacts.numberOfPlayers;
        if (verifier != null) {
            verifier.stageLoaded(Arrays.copyOf(ui.sc, players));
            return;
        }
        if (customStage) {
            log = null;
            return;
        }
        log = new RaceLog();
        log.seed = Medium.raceSeed();
        log.stage = host.checkpoints().stage;
        log.nfmmode = ui.nfmmode;
        log.players = players;
        log.unlocked = ui.unlocked;
        log.carsBefore = Arrays.copyOf(carsBefore, players);
        log.carsAfter = Arrays.copyOf(ui.sc, players);
        log.mountains = mountainsBefore;
        log.note = (autopilot != null ? "autopilot " + autopilot.describe() : "played")
                + (unpaced ? ", unpaced" : ", real time") + ", " + timestamp();
    }

    /** A sound command, from any thread; only the in-game tick's are kept. */
    private void heard(String command) {
        if (!inTick) {
            return;
        }
        if (verifier != null) {
            verifier.heard(command);
        } else if (racing) {
            log.addSound(command);
        }
    }

    /** Start of an in-game tick, after the keyboard was read: the input this tick will use. */
    void ingameInput() {
        inTick = true;
        if (verifier != null) {
            verifier.ingameInput();
            return;
        }
        Control u = host.player();
        pendingKeys = RaceLog.keyBits(u);
        pendingLookback = u.lookback;
        pendingView = host.cameraView();
        pendingSpectate = host.ui().spectate;
    }

    /** The countdown begins: the race proper starts from here. */
    void countdownStarting() {
        if (verifier != null) {
            verifier.countdownStarting();
            return;
        }
        if (log == null) {
            return;
        }
        RaceState.View race = host.race();
        log.layout = RaceState.layout(race);
        log.startState = RaceState.startState(race);
        log.startNames = RaceState.startStateNames(race);
        log.raceRandom = host.race().world().random.exportState();
        log.trackShape = RaceState.trackShape(race.track());
        log.trackLeftovers = RaceState.trackLeftovers(race);
        log.setGroups(RaceState.groupCount(race.players()));
        racing = true;
    }

    /** End of an in-game tick. */
    void ingameTickEnded() {
        inTick = false;
        if (verifier != null) {
            verifier.tickEnded();
            return;
        }
        if (!racing) {
            return;
        }
        RaceState.View race = host.race();
        int tick = log.ticks();
        log.addTick(pendingKeys, pendingLookback, pendingView, pendingSpectate, RaceState.hashes(race),
                RaceState.canonicalHash(race));
        ContO car = race.cars()[0];
        log.addPose(car.x, car.y, car.z, car.xz, car.xy, car.zy, car.wxz, car.wzy);
        CheckPoints cp = race.checkpoints();
        if (log.finishTick < 0 && cp.clear[0] >= cp.nlaps * cp.nsp) {
            log.finishTick = tick;
        }
        if (tick % RaceLog.DUMP_INTERVAL == 0) {
            log.addDump(tick, RaceState.dump(race), RaceState.canonical(race));
        }
    }

    /* -------------------------------------------------------------- recording */

    private void observe(Phase phase) {
        if (!racing) {
            return;
        }
        if (phase == Phase.INSTANTREPLAY && lastPhase != Phase.INSTANTREPLAY) {
            log.addEvent(log.ticks(), RaceLog.Event.REPLAY);
        }
        if (!RACE_PHASES.contains(phase)) {
            finishRecording(FINISH_PHASES.contains(phase) ? RaceLog.End.FINISHED : RaceLog.End.QUIT);
        }
    }

    private synchronized void finishRecording(RaceLog.End end) {
        if (!racing) {
            return;
        }
        racing = false;
        RaceLog done = log;
        log = null;
        done.end = end;
        String time = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        String name = replays ? Replays.fileName(time, done.stage, done.finishTick)
                : String.format(Locale.ROOT, "race-%s-stage%02d.nfmr", time, done.stage);
        File file = new File(recordFolder, name);
        try {
            done.write(file);
            if (replays) {
                Replays.tidy(recordFolder);
            } else {
                System.out.println(TAG_RECORD + file.getName() + ": stage " + done.stage + ", " + done.players
                        + " cars, " + done.ticks() + " ticks, " + end.name().toLowerCase(Locale.ROOT));
            }
        } catch (IOException ex) {
            if (replays) {
                HLogger.warn("Could not save the replay " + file + ": " + ex);
            } else {
                System.out.println(TAG_RECORD + "could not save " + file + ": " + ex);
            }
        }
        if (autopilot != null) {
            autopilot.raceRecorded();
        }
    }

    private void saveOnExit() {
        finishRecording(RaceLog.End.CLOSED);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date());
    }

    /** Sets up a race from the main menu without going through the menus' screens. */
    static void setUpRace(Host host, int stage, int nfmmode, int players, int unlocked, int[] cars) {
        xtGraphics ui = host.ui();
        ui.unlocked = unlocked;
        ui.nfmmode = nfmmode;
        ui.nplayers_debug = true;
        ui.nplayers_override = players;
        System.arraycopy(cars, 0, ui.sc, 0, Math.min(cars.length, ui.sc.length));
        host.checkpoints().stage = stage;
        host.player().falseo();
        host.setPhase(Phase.NPLAYERSCHECK);
    }

    /* --------------------------------------------------------------- autopilot */

    /**
     * Plays races by itself for the test corpus. It picks a stage and a car,
     * then drives with seeded, scripted input delivered through the keyboard
     * path, so the recording is exactly what a player could have produced.
     * The opponents are the game's own AI. Some races skip the intro, some
     * watch an instant replay half way, some switch camera views, so the
     * corpus covers the paths that touch the race from outside.
     */
    private static final class Autopilot {
        private final Host host;
        private final int races;
        private final long seed;
        private final int maxTicks;
        private final Random rnd;
        private final List<Integer> stages = new ArrayList<>();

        private int started;
        private int recorded;
        private boolean skipIntro;
        private int replayAt;
        private boolean replayDone;
        private boolean quitting;
        private int stage;

        /* Current driving program. */
        private int segmentLeft;
        private boolean up;
        private boolean down;
        private boolean left;
        private boolean right;
        private boolean handbrake;
        private int lookback;
        private final boolean[] held = new boolean[2048];

        Autopilot(Host host, int races, long seed, int maxTicks) {
            this.host = host;
            this.races = races;
            this.seed = seed;
            this.maxTicks = maxTicks;
            this.rnd = new Random(seed);
            for (int s = 1; s <= GameFacts.numberOfStages; s++) {
                stages.add(s);
            }
            Collections.shuffle(stages, rnd);
        }

        String describe() {
            return "seed " + seed + " race " + started + "/" + races + (skipIntro ? ", intro skipped" : "")
                    + (replayAt > 0 ? ", replay at tick " + replayAt : "");
        }

        void raceRecorded() {
            recorded++;
        }

        void beforeTick(Phase phase, int raceTick) {
            switch (phase) {
                case LOADING -> host.click();
                case MAINMENU -> {
                    if (recorded >= races) {
                        System.out.println(TAG_RECORD + "autopilot done: " + recorded + " races");
                        System.exit(0);
                    }
                    planRace();
                }
                case STAGESELECT, PREGAME -> press(CODE_ENTER);
                case LOCKEDSTAGE, ERRORLOADINGSTAGE -> {
                    System.out.println(TAG_RECORD + "stage " + stage + " could not be started (" + phase + ")");
                    System.exit(2);
                }
                case INGAME -> drive(raceTick);
                case PAUSEMENU -> pauseMenu();
                case NOTENOUGHREPLAYDATA -> press(CODE_ENTER);
                default -> {
                    if (FINISH_PHASES.contains(phase)) {
                        // recorded already; skip the highlight and go straight to the next race
                        releaseAll();
                        host.setPhase(Phase.MAINMENU);
                    }
                }
            }
        }

        private void planRace() {
            stage = stages.get(started % stages.size());
            int car = rnd.nextInt(GameFacts.numberOfCars);
            skipIntro = rnd.nextBoolean();
            replayAt = started % 3 == 1 ? 350 + rnd.nextInt(Math.max(1, maxTicks / 2)) : -1;
            replayDone = false;
            quitting = false;
            segmentLeft = 0;
            started++;
            int[] cars = Arrays.copyOf(host.ui().sc, host.ui().sc.length);
            cars[0] = car;
            setUpRace(host, stage, host.ui().nfmmode, 7, GameFacts.numberOfStages, cars);
        }

        private void drive(int raceTick) {
            if (raceTick < 0) {
                // the intro fly-by and the moment the countdown starts
                if (skipIntro) {
                    press(CODE_ENTER);
                }
                return;
            }
            if (!replayDone && replayAt > 0 && raceTick >= replayAt || raceTick >= maxTicks && !quitting) {
                quitting = raceTick >= maxTicks;
                releaseAll();
                press(CODE_ENTER);
                return;
            }
            if (--segmentLeft <= 0) {
                nextSegment();
            }
            hold(CODE_UP, up);
            hold(CODE_DOWN, down);
            hold(CODE_LEFT, left);
            hold(CODE_RIGHT, right);
            hold(CODE_SPACE, handbrake);
            hold(CODE_LOOK_LEFT, lookback == 1);
            hold(CODE_LOOK_RIGHT, lookback == -1);
            if (rnd.nextInt(400) == 0) {
                press(CODE_VIEW);
            }
        }

        /** A new stretch of driving: mostly full throttle with steering, sometimes stunts. */
        private void nextSegment() {
            segmentLeft = 4 + rnd.nextInt(26);
            int pick = rnd.nextInt(100);
            up = pick < 85;
            down = pick >= 70 && pick < 80 || pick >= 92;
            int steer = rnd.nextInt(3);
            left = steer == 1;
            right = steer == 2;
            handbrake = pick >= 60 && pick < 68;
            lookback = rnd.nextInt(25) == 0 ? (rnd.nextBoolean() ? 1 : -1) : 0;
        }

        private void pauseMenu() {
            xtGraphics ui = host.ui();
            if (quitting) {
                ui.opselect = MENU_QUIT;
            } else if (!replayDone && replayAt > 0) {
                ui.opselect = MENU_REPLAY;
                replayDone = true;
            } else {
                ui.opselect = MENU_CONTINUE;
            }
            press(CODE_ENTER);
        }

        private void hold(int key, boolean down) {
            if (held[key] != down) {
                held[key] = down;
                host.key(key, down);
            }
        }

        private void press(int key) {
            host.key(key, true);
            host.key(key, false);
        }

        private void releaseAll() {
            for (int key = 0; key < held.length; key++) {
                if (held[key]) {
                    held[key] = false;
                    host.key(key, false);
                }
            }
        }
    }

    /* ---------------------------------------------------------------- verifier */

    /** Plays recorded races again from their input and checks every tick. */
    private static final class Verifier {
        private final Host host;
        private final List<File> files;
        private int next;
        private int failed;
        private int errors;
        private int passed;

        private File file;
        private RaceLog log;
        private boolean racing;
        private int tick;
        private int replaysDone;
        private int replaysAtTick = -1;
        private int idleTicks;
        private boolean finishing;
        private int mismatchTick = -1;
        private int[] mismatchHashes;
        /** The full state lines up with the recording; otherwise only the canonical state is compared. */
        private boolean sameLayout;
        private int startDifferences;
        private final List<String> startDifferenceNames = new ArrayList<>();
        /** Track values left by earlier stages that differed from the recording and were put back. */
        private int trackDifferences;
        private String carsProblem;
        /** Golden screenshots to check while the race plays, or null. */
        private final GoldenFrames golden;
        private final List<String> goldenReport = new ArrayList<>();
        /* Rollback self-test: the state to rewind to and the ticks left to play ahead. */
        private final RaceSnapshot rewindTo = new RaceSnapshot();
        /** Everything the race reached at the same tick, to prove the rewind put it all back. */
        private RaceSnapshot rewindReference;
        private int aheadLeft;
        private int rewinds;
        private long captureNanos;
        private long restoreNanos;
        private long fastestCapture;
        private long fastestRestore;
        /** This tick's sound commands, and the first tick whose sounds differed from the recording. */
        private final List<String> heardThisTick = new ArrayList<>();
        private int soundMismatchTick = -1;
        private String soundMismatch;

        Verifier(Host host, List<File> files, GoldenFrames golden) {
            this.host = host;
            this.files = files;
            this.golden = golden;
        }

        /** Sets up the scenery the recording had and returns the race seed. */
        long stageLoading() {
            if (log == null) {
                return RaceRandom.newSeed();
            }
            Medium.mgen = log.mountains;
            return log.seed;
        }

        void beforeTick(Phase phase) {
            if (finishing) {
                finishing = false;
                host.player().falseo();
                host.setPhase(Phase.MAINMENU);
                return;
            }
            if (log != null && ++idleTicks > STUCK_TICKS) {
                error("stuck in " + phase + " at tick " + tick);
                return;
            }
            switch (phase) {
                case LOADING -> host.click();
                case MAINMENU -> startNext();
                case STAGESELECT, PREGAME, NOTENOUGHREPLAYDATA -> host.player().enter = true;
                case LOCKEDSTAGE, ERRORLOADINGSTAGE -> error("the stage could not be loaded (" + phase + ")");
                case PAUSEMENU -> pauseMenu();
                default -> {
                    if (racing && !RACE_PHASES.contains(phase)) {
                        fail("the race ended after " + tick + " of " + log.ticks() + " ticks (" + phase + ")");
                    }
                }
            }
        }

        private void startNext() {
            if (log != null) {
                return;
            }
            if (next >= files.size()) {
                summary();
                return;
            }
            file = files.get(next++);
            try {
                log = RaceLog.read(file);
            } catch (IOException ex) {
                System.out.println(TAG_VERIFY + file.getName() + ": " + ex.getMessage());
                errors++;
                log = null;
                return;
            }
            racing = false;
            tick = 0;
            replaysDone = 0;
            replaysAtTick = -1;
            idleTicks = 0;
            mismatchTick = -1;
            mismatchHashes = null;
            startDifferences = 0;
            startDifferenceNames.clear();
            trackDifferences = 0;
            carsProblem = null;
            goldenReport.clear();
            rewindReference = null;
            aheadLeft = 0;
            rewinds = 0;
            captureNanos = 0L;
            restoreNanos = 0L;
            fastestCapture = Long.MAX_VALUE;
            fastestRestore = Long.MAX_VALUE;
            heardThisTick.clear();
            soundMismatchTick = -1;
            soundMismatch = null;
            System.out.println(TAG_VERIFY + file.getName() + ": stage " + log.stage + ", " + log.players + " cars, "
                    + log.ticks() + " ticks, " + log.end.name().toLowerCase(Locale.ROOT) + " (" + log.note + ")");
            setUpRace(host, log.stage, log.nfmmode, log.players, log.unlocked, log.carsBefore);
        }

        void stageLoaded(int[] cars) {
            if (log != null && !Arrays.equals(cars, log.carsAfter)) {
                carsProblem = "the opponents differ: recorded " + Arrays.toString(log.carsAfter) + ", got "
                        + Arrays.toString(cars);
            }
        }

        void heard(String command) {
            if (racing) {
                heardThisTick.add(command);
            }
        }

        void ingameInput() {
            heardThisTick.clear();
            Control u = host.player();
            if (!racing) {
                // skip the intro fly-by: the race starts from the recorded state anyway
                u.enter = true;
                return;
            }
            if (aheadLeft > 0) {
                int ahead = tick + ROLLBACK_AHEAD - aheadLeft;
                RaceLog.applyKeys(u, log.keys(ahead) ^ DRIVING_KEYS);
                return;
            }
            if (tick >= log.ticks()) {
                return;
            }
            RaceLog.applyKeys(u, log.keys(tick));
            u.lookback = log.lookback(tick);
            host.setCameraView(log.view(tick));
            host.ui().spectate = log.spectate(tick);
            if (tick == PERTURB_TICK) {
                u.left = !u.left;
                System.out.println(TAG_VERIFY + "  (self-test: steering changed on tick " + tick + ")");
            }
        }

        void countdownStarting() {
            if (log == null) {
                return;
            }
            if (carsProblem != null) {
                fail(carsProblem);
                return;
            }
            RaceState.View race = host.race();
            if (!Arrays.equals(RaceState.trackShape(race.track()), log.trackShape)) {
                fail("the stage's collision boxes differ from the recording: the stage loads differently now");
                return;
            }
            /*
             * The race stream follows from the seed: only stage set-up draws
             * from it before the countdown. So it is checked, not restored, and
             * a change to what set-up draws shows up here.
             */
            if (!Arrays.equals(race.world().random.exportState(), log.raceRandom)) {
                fail("the race's random stream is not where it was when the countdown started:"
                        + " setting up the stage draws differently now");
                return;
            }
            trackDifferences = RaceState.restoreTrackLeftovers(race, log.trackLeftovers);
            sameLayout = !CANONICAL_ONLY && RaceState.layout(race) == log.layout;
            if (sameLayout) {
                int[] actual = RaceState.startState(race);
                List<String> names = null;
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != log.startState[i]) {
                        if (names == null) {
                            names = RaceState.startStateNames(race);
                        }
                        if (startDifferenceNames.size() < 16) {
                            startDifferenceNames.add(names.get(i));
                        }
                        startDifferences++;
                    }
                }
                RaceState.restore(race, log.startState);
            } else {
                List<String> missing = RaceState.restoreByName(race, log.startNames, log.startState);
                System.out.println(TAG_VERIFY + (CANONICAL_ONLY ? "  checking the canonical race state only"
                        : "  the race code has been reorganised since this race was recorded:"
                                + " checking the canonical race state only") + (missing.isEmpty() ? ""
                                : " (" + missing.size() + " recorded start values have no counterpart now)"));
            }
            racing = true;
            tick = 0;
        }

        void tickEnded() {
            if (!racing || finishing) {
                return;
            }
            idleTicks = 0;
            RaceState.View race = host.race();
            if (aheadLeft > 0) {
                if (--aheadLeft == 0) {
                    long start = System.nanoTime();
                    rewindTo.restore();
                    long took = System.nanoTime() - start;
                    SoundManager.setDiscarding(false);
                    restoreNanos += took;
                    fastestRestore = Math.min(fastestRestore, took);
                    Map<String, Integer> left = rewindReference.differences();
                    rewindReference = null;
                    if (!left.isEmpty()) {
                        fail("rewinding to tick " + tick + " did not put everything back: " + describeLeftovers(left));
                    }
                }
                return;
            }
            if (tick == TRACE_TICK) {
                trace(race);
            }
            if (mismatchTick < 0) {
                int[] hashes = sameLayout ? RaceState.hashes(race) : null;
                boolean differs = RaceState.canonicalHash(race) != log.canonicalHash(tick);
                for (int g = 0; hashes != null && g < hashes.length && !differs; g++) {
                    differs = hashes[g] != log.hash(tick, g);
                }
                if (differs) {
                    mismatchTick = tick;
                    mismatchHashes = hashes;
                }
            }
            if (soundMismatchTick < 0 && log.hasSounds() && !heardThisTick.equals(log.sounds(tick))) {
                soundMismatchTick = tick;
                soundMismatch = "the sounds differ first at tick " + tick + ": recorded " + log.sounds(tick)
                        + ", got " + heardThisTick;
            }
            if (golden != null && mismatchTick < 0 && GoldenFrames.isGoldenTick(tick, log.ticks())) {
                goldenReport.add(golden.check(file.getName().replace(".nfmr", ""), tick, host.frame()));
            }
            if (mismatchTick >= 0 && log.dumpTickAtOrAfter(mismatchTick) == tick) {
                fail(sameLayout
                        ? describeMismatch(RaceState.dump(race), RaceState.names(race), RaceState.groupOf(race), false)
                        : describeMismatch(RaceState.canonical(race), RaceState.canonicalNames(race), null, true));
                return;
            }
            tick++;
            if (tick >= log.ticks()) {
                if (mismatchTick >= 0) {
                    fail(describeMismatch(null, null, null, false));
                } else if (soundMismatch != null) {
                    fail(soundMismatch);
                } else {
                    pass();
                }
                return;
            }
            if (rollbackDue()) {
                long start = System.nanoTime();
                rewindTo.capture(race, host.sceneCount());
                long took = System.nanoTime() - start;
                captureNanos += took;
                fastestCapture = Math.min(fastestCapture, took);
                rewindReference = RaceSnapshot.captureComplete(race, host.sceneCount());
                aheadLeft = ROLLBACK_AHEAD;
                // what happens in the ticks played ahead is undone, and must not be heard either
                SoundManager.setDiscarding(true);
                rewinds++;
            }
        }

        /**
         * Time to play ahead and rewind: not near the end, and not across a
         * tick where the player paused or pressed Enter, which leave the race.
         */
        private boolean rollbackDue() {
            if (ROLLBACK_EVERY <= 0 || mismatchTick >= 0 || tick % ROLLBACK_EVERY != 0
                    || tick + ROLLBACK_AHEAD + 2 >= log.ticks()) {
                return false;
            }
            for (int t = tick - 1; t <= tick + ROLLBACK_AHEAD; t++) {
                if (log.countEvents(t, RaceLog.Event.REPLAY) > 0 || (log.keys(Math.max(t, 0)) & RaceLog.KEY_ENTER) != 0) {
                    return false;
                }
            }
            return true;
        }

        /** The fields a rewind left changed, most objects first: "Plane.av in 812 objects, ...". */
        private static String describeLeftovers(Map<String, Integer> left) {
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(left.entrySet());
            entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : entries.subList(0, Math.min(entries.size(), REPORT_FIELDS))) {
                sb.append(sb.isEmpty() ? "" : ", ").append(e.getKey()).append(" in ").append(e.getValue())
                        .append(e.getValue() == 1 ? " object" : " objects");
            }
            return sb.append(entries.size() > REPORT_FIELDS ? ", ..." : "").toString();
        }

        private void pauseMenu() {
            if (!racing) {
                return;
            }
            if (replaysAtTick != tick) {
                replaysAtTick = tick;
                replaysDone = 0;
            }
            xtGraphics ui = host.ui();
            if (replaysDone < log.countEvents(tick, RaceLog.Event.REPLAY)) {
                ui.opselect = MENU_REPLAY;
                replaysDone++;
            } else {
                ui.opselect = MENU_CONTINUE;
            }
            host.player().enter = true;
        }

        /**
         * The first differing tick and groups, then the fields that differ at
         * the next stored dump. By then the error has usually spread to other
         * cars, so the fields of the groups that went wrong first are listed
         * first.
         */
        private String describeMismatch(int[] actual, List<String> names, int[] groupOf, boolean canonical) {
            StringBuilder sb = new StringBuilder();
            int players = log.players;
            sb.append("tick ").append(mismatchTick).append(String.format(Locale.ROOT, " (%.1f s into the race)",
                    mismatchTick * GameSparker.SCENE_TICK_NANOS / 1e9));
            if (mismatchHashes != null) {
                sb.append(" differs first in:");
                for (int g = 0; g < mismatchHashes.length; g++) {
                    if (mismatchHashes[g] != log.hash(mismatchTick, g)) {
                        sb.append(' ').append(RaceState.groupName(g, players)).append(',');
                    }
                }
                sb.setLength(sb.length() - 1);
            } else {
                sb.append(" differs");
            }
            int[] recorded = canonical ? log.canonicalDumpAtOrAfter(mismatchTick) : log.dumpAtOrAfter(mismatchTick);
            if (actual != null && recorded != null && recorded.length == actual.length) {
                int dumpTick = log.dumpTickAtOrAfter(mismatchTick);
                List<Integer> differing = new ArrayList<>();
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != recorded[i]) {
                        differing.add(i);
                    }
                }
                if (groupOf != null && mismatchHashes != null) {
                    differing.sort(Comparator.comparingInt(i -> firstWrong(groupOf[i]) ? 0 : 1));
                }
                int total = differing.size();
                StringBuilder fields = new StringBuilder();
                for (int i : differing.subList(0, Math.min(total, REPORT_FIELDS))) {
                    fields.append("\n").append(TAG_VERIFY).append("    ").append(names.get(i))
                            .append(": recorded ").append(describeValue(recorded[i])).append(", got ")
                            .append(describeValue(actual[i]));
                }
                sb.append("\n").append(TAG_VERIFY).append("  ").append(total).append(" values differ at tick ")
                        .append(dumpTick).append(':').append(fields);
            }
            return sb.toString();
        }

        private void trace(RaceState.View race) {
            int[] values = RaceState.dump(race);
            List<String> names = RaceState.names(race);
            List<String> lines = new ArrayList<>(values.length);
            for (int i = 0; i < values.length; i++) {
                lines.add(names.get(i) + " = " + describeValue(values[i]));
            }
            File out = new File(System.getProperty("java.io.tmpdir"),
                    file.getName().replace(".nfmr", "") + "-tick" + tick + "-" + ProcessHandle.current().pid() + ".txt");
            try {
                java.nio.file.Files.write(out.toPath(), lines);
                System.out.println(TAG_VERIFY + "  state after tick " + tick + " written to " + out);
            } catch (IOException ex) {
                System.out.println(TAG_VERIFY + "  could not write " + out + ": " + ex.getMessage());
            }
        }

        /** The group's hash already differed on the first mismatching tick. */
        private boolean firstWrong(int group) {
            return mismatchHashes[group] != log.hash(mismatchTick, group);
        }

        /** Values are stored as raw bits: show them as an int and, if plausible, as a float. */
        private static String describeValue(int bits) {
            float f = Float.intBitsToFloat(bits);
            boolean floatLike = Math.abs(bits) > 0x00800000 && !Float.isNaN(f) && Math.abs(f) < 1e9F;
            return floatLike ? bits + " (" + f + "f)" : Integer.toString(bits);
        }

        private void pass() {
            passed++;
            StringBuilder sb = new StringBuilder(TAG_VERIFY).append("  OK: all ").append(log.ticks())
                    .append(log.hasSounds() ? " ticks and their " + log.soundCount() + " sound commands match"
                            : " ticks match (recorded before sounds were logged)");
            if (startDifferences > 0) {
                sb.append(System.lineSeparator()).append(TAG_VERIFY).append("    ").append(startDifferences)
                        .append(" values at the start of the countdown differed from the recording and were restored")
                        .append(" (the intro camera, or state left by earlier races): ")
                        .append(String.join(", ", startDifferenceNames))
                        .append(startDifferences > startDifferenceNames.size() ? ", ..." : "");
            }
            if (trackDifferences > 0) {
                sb.append(System.lineSeparator()).append(TAG_VERIFY).append("    ").append(trackDifferences)
                        .append(" track values left by the stages loaded before differed from the recording and were")
                        .append(" restored (boundary-wall surfaces, and wall slots the AI still remembers from the")
                        .append(" last race)");
            }
            if (rewinds > 0) {
                sb.append(System.lineSeparator()).append(TAG_VERIFY).append(String.format(Locale.ROOT,
                        "    rewound %d times after playing %d ticks ahead, every value put back"
                                + " (capture %.2f ms on average, %.2f at best; restore %.2f ms, %.2f at best)",
                        rewinds, ROLLBACK_AHEAD, captureNanos / 1e6 / rewinds, fastestCapture / 1e6,
                        restoreNanos / 1e6 / rewinds, fastestRestore / 1e6));
            }
            System.out.println(sb);
            printGoldenReport();
            endRace();
        }

        private void fail(String why) {
            failed++;
            System.out.println(TAG_VERIFY + "  MISMATCH: " + why);
            printGoldenReport();
            endRace();
        }

        private void printGoldenReport() {
            for (String line : goldenReport) {
                System.out.println(TAG_VERIFY + "  frame after " + line);
            }
        }

        private void error(String why) {
            errors++;
            System.out.println(TAG_VERIFY + "  ERROR: " + why);
            endRace();
        }

        private void endRace() {
            racing = false;
            aheadLeft = 0;
            SoundManager.setDiscarding(false);
            log = null;
            finishing = true;
        }

        private void summary() {
            System.out.println(TAG_VERIFY + passed + " matched, " + failed + " differed, " + errors
                    + " could not be run");
            boolean framesChanged = golden != null && golden.anyChanged();
            if (golden != null) {
                System.out.println(TAG_VERIFY + golden.summary());
            }
            System.exit(errors > 0 ? 2 : failed > 0 || framesChanged ? 1 : 0);
        }
    }
}
