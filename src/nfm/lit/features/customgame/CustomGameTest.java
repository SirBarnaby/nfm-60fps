package nfm.lit.features.customgame;

import java.util.Arrays;
import nfm.lit.Phase;
import nfm.lit.World;
import nfm.lit.features.FeatureSettings;

/**
 * The hidden custom test: {@code -Dnfm.autotest=<dir>} with
 * {@code -Dnfm.autotest.custom=<computer cars>[,default|novice|pro|custom]},
 * or {@code saved} for the choices as the player left them. The custom
 * game's switch is on for the run, never saved. Main menu, the custom game's
 * page, the car and stage select, a race with screenshots (every second
 * through a staggered start) that ends 30 seconds after the player's start
 * in the post-race highlight (sooner if it is won or lost), the results, and
 * the custom game's page again, from where Escape goes back to the main menu.
 * <p>
 * It fails, with exit code 1 and {@code custom test FAILED: <why>}, if the
 * race's field is not what the lineup brings or the player does not stand
 * last ({@link CustomGameCheck}), a staggered start is not as the owner
 * asked ({@link CustomGameCheck.Launches}, watched on the game thread), it
 * skips the highlight, the results or the page after them, or it never gets
 * through; a pass ends with {@code custom test done}.
 * <p>
 * It runs on its own thread, every 100 ms, and reaches the game only
 * through {@link Host}, the game's own test helpers.
 */
public final class CustomGameTest {

    /** What the test needs of the game. */
    public interface Host {
        Phase phase();

        /** How long the main menu is held before the test goes on. */
        long menuHoldMillis();

        /** How long between screenshots in a race: {@code usual}, or the live test's own spacing. */
        long shotEvery(long usual);

        /** Saves the picture on screen as {@code name} in the test's folder. */
        void shot(String name);

        /** Clicks past a loading screen that waits for a click. */
        void clickPastLoading(Phase phase, long held);

        void pressEnter();

        /** Holds the player's arrows as the keyboard does: the next race tick latches them. */
        void holdKeys(boolean up, boolean left, boolean right);

        /** Puts the main menu's choice on Custom Game. */
        void chooseCustomGame();

        /** Puts the custom game's page on the row {@code label}. */
        void selectRow(String label);

        /** The next Enter on the custom game's page is Escape: back to the main menu. */
        void leavePage();

        int starcnt();

        boolean raceOver();

        boolean won();

        /** Ends the race into the post-race highlight. */
        void finishRace();

        World world();

        /** The race's cars, by slot, the player's first. */
        int[] field();

        /** Where each car stands lengthwise, by slot. */
        float[] gridZ();

        int stage();

        int unlocked();

        /** Lets {@code launches} watch the race ticks on the game thread; null stops it. */
        void watch(CustomGameCheck.Launches launches);

        /** The end of a hidden run, as every hidden test ends. */
        void finish();
    }

    private final String ask;
    private final Host host;

    public CustomGameTest(String ask, Host host) {
        this.ask = ask;
        this.host = host;
    }

    public void run() {
        String[] parts = ask.split(",");
        if (!parts[0].trim().equals("saved")) {
            CustomGame.forTest(Integer.parseInt(parts[0].trim()), parts.length > 1 ? parts[1] : null);
        }
        FeatureSettings.set(CustomGameFeature.ID, 1); // never saved: a hidden run
        System.out.println("[autotest] custom game (" + ask + ")");
        long start = System.currentTimeMillis();
        long menuHold = host.menuHoldMillis();
        Phase lastPhase = null;
        Phase pendingShot = null;
        long settledAt = start;
        long lastPress = 0L;
        long lastShot = 0L;
        long inGameMillis = 0L;
        int shots = 0;
        int step = 0;
        long racingMillis = 0L;
        CustomGameCheck.Launches launches = null;
        boolean highlight = false;
        boolean results = false;
        String failed = null;
        while (System.currentTimeMillis() - start < 240_000L + menuHold) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                return;
            }
            Phase phase = host.phase();
            long now = System.currentTimeMillis();
            World world = host.world();
            if (phase != lastPhase) {
                System.out.println("[autotest] phase " + lastPhase + " -> " + phase + " at " + java.time.LocalTime.now());
                if (phase == Phase.INGAME && step < 10) {
                    int[] sc = host.field();
                    int cars = sc.length;
                    String grid = CustomGameCheck.grid(host.gridZ(), cars, world.startGroups);
                    String field = CustomGameCheck.field(sc, cars, host.stage(), host.unlocked());
                    System.out.println("[autotest] custom race: " + cars + " cars, "
                            + CustomGame.lineupText(CustomGame.fieldLineup()) + ", "
                            + (world.staggered() ? world.startGroups + " groups" : "all at once") + ", cars "
                            + Arrays.toString(sc) + ", player starts " + (grid == null ? "last" : "NOT last"));
                    failed = failed != null ? failed : field != null ? field : grid;
                    if (world.staggered()) {
                        launches = new CustomGameCheck.Launches(cars, world.startGroups);
                        host.watch(launches);
                    }
                    inGameMillis = 0L;
                    step = 10;
                }
                if (phase == Phase.CAUGHTHIGHLIGHT || phase == Phase.GAMEHIGHLIGHT) {
                    highlight = true;
                }
                if (phase == Phase.POSTGAME) {
                    results = true;
                }
                lastPhase = phase;
                settledAt = now;
                pendingShot = phase;
            }
            if (pendingShot != null && now - settledAt >= 300L) {
                host.shot(String.format("%02d-%s.png", shots++, phase.name().toLowerCase()));
                pendingShot = null;
            }
            long held = now - settledAt;
            host.clickPastLoading(phase, held);
            switch (phase) {
                case MAINMENU -> {
                    if (step >= 30) {
                        host.watch(null);
                        if (failed == null && launches != null) {
                            failed = launches.problem();
                        }
                        if (failed == null && !highlight) {
                            failed = "the race never went into the post-race highlight";
                        }
                        if (failed == null && !results) {
                            failed = "the race never showed its results";
                        }
                        host.finish();
                        if (failed != null) {
                            System.out.println("[autotest] custom test FAILED: " + failed);
                            System.exit(1);
                        }
                        System.out.println("[autotest] custom test done");
                        System.exit(0);
                    }
                    if (step == 0 && held >= 600L + menuHold) {
                        host.chooseCustomGame();
                        host.pressEnter();
                        step = 1;
                        System.out.println("[autotest] custom game from the main menu");
                    }
                }
                case CUSTOMGAME -> {
                    if (step == 1 && held >= 700L) {
                        host.selectRow("Their cars");
                        step = 2;
                    } else if (step == 2 && held >= 1500L) {
                        host.shot(String.format("%02d-custom-game.png", shots++));
                        host.selectRow("Race!");
                        host.pressEnter();
                        step = 3;
                    } else if (step == 20 && held >= 1500L) {
                        host.shot(String.format("%02d-custom-game-again.png", shots++));
                        host.leavePage();
                        host.pressEnter();
                        step = 30;
                    }
                }
                case CARSELECT, STAGESELECT, PREGAME -> {
                    if (now - lastPress > 700L && held >= 500L) {
                        host.pressEnter();
                        lastPress = now;
                    }
                }
                case INGAME -> {
                    inGameMillis += 100L;
                    int cars = host.field().length;
                    long cycle = inGameMillis % 4000L;
                    boolean over = host.raceOver();
                    host.holdKeys(!over, !over && cycle > 3400L && cycle < 3700L, !over && cycle > 2200L && cycle < 2500L);
                    boolean starting = host.starcnt() == 0 && StaggeredStart.playerWaiting(world, cars);
                    if (now - lastShot >= host.shotEvery(starting ? 1000L : 5000L)) {
                        host.shot(String.format("%02d-race-%06dms.png", shots++, inGameMillis));
                        lastShot = now;
                    }
                    if (host.starcnt() == 0 && !starting && !over) {
                        racingMillis += 100L;
                    }
                    if (racingMillis >= 30_000L && step == 10) {
                        host.finishRace();
                        step = 11;
                        if (launches != null) {
                            System.out.println("[autotest] groups set off at ticks " + launches.describe());
                        }
                        System.out.println("[autotest] finishing the race into the post-race highlight");
                    }
                    if (over && held >= 1000L && now - lastPress > 1500L) {
                        System.out.println("[autotest] race over, " + (host.won() ? "won" : "lost"));
                        host.pressEnter();
                        lastPress = now;
                    }
                }
                case CAUGHTHIGHLIGHT, GAMEHIGHLIGHT -> {
                    host.holdKeys(false, false, false);
                    if (now - lastShot >= host.shotEvery(2000L)) {
                        host.shot(String.format("%02d-highlight.png", shots++));
                        lastShot = now;
                    }
                }
                case POSTGAME -> {
                    host.holdKeys(false, false, false);
                    if (held >= 1500L && now - lastPress > 1500L) {
                        host.pressEnter();
                        lastPress = now;
                        step = 20;
                    }
                }
                default -> {
                }
            }
        }
        // unlike the other tests, a custom test that never got through counts as failed: a dead game thread leaves
        // the game standing on one screen, and that should not pass for a finished run
        host.watch(null);
        System.out.println("[autotest] custom test FAILED: ran out of time in " + host.phase());
        host.finish();
        System.exit(1);
    }
}
