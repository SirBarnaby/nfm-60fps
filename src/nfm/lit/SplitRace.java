package nfm.lit;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Split screen: two players on one PC, cars 0 and 1, each with a view, a
 * HUD and keys or a controller of their own. This holds the choices (how
 * the window is shared, the computer cars, where each player is heard) and,
 * while a split-screen race is set up or run, that race's own state:
 * Player 2's camera and HUD, who wrecked which car, and how it ends.
 * <p>
 * The classic game never makes one: {@link #active} stays null, and every
 * split-screen branch in the game is behind it.
 */
final class SplitRace {

    /** How the window is shared: whichever suits its shape, a line down the middle, or across. */
    static final int VIEW_AUTO = 0;
    static final int VIEW_SIDE = 1;
    static final int VIEW_STACKED = 2;

    /** Where the players are heard: both on the speaker, race events on the second speaker, or a speaker each. */
    static final int SOUND_ONE = nfm.lit.audio.Sound3D.SPLIT_ONE;
    static final int SOUND_EVENTS = nfm.lit.audio.Sound3D.SPLIT_EVENTS;
    static final int SOUND_EACH = nfm.lit.audio.Sound3D.SPLIT_EACH;

    /** The most computer cars a split-screen race has. */
    static final int MAX_RIVALS = 5;

    /** A player's controller: Auto, none, or the n-th connected (0 for the first). */
    static final int PAD_AUTO = -1;
    static final int PAD_NONE = -2;

    static volatile int view = VIEW_AUTO;
    static volatile int rivals = MAX_RIVALS;
    static volatile int sound = SOUND_ONE;
    /** Each player's controller: PAD_AUTO, PAD_NONE, or the n-th connected. */
    static final int[] pads = {PAD_AUTO, PAD_AUTO};
    /** Each player's car as last chosen. */
    static final int[] cars = {0, 1};
    static volatile boolean changed;

    /**
     * Split screen is on: picked from the main menu, through the car and
     * stage select, the race and its results, until back at the main menu.
     * Game thread.
     */
    static boolean on;

    /** The race being set up or run in split screen, or null. Game thread. */
    static SplitRace active;

    /**
     * The split-screen player whose HUD is being drawn or whose sounds are
     * being decided: 1 while Player 2's state is swapped in, otherwise 0.
     */
    static int drawing;

    /** Player 2's camera, swapped into Medium while it is placed or drawn from. */
    final Medium.Camera cam2 = new Medium.Camera();
    /** Player 2's HUD state, swapped into GameUi while Player 2's HUD runs. */
    final PlayerHud hud2;
    /** The computer cars in this race. */
    final int rivalsNow;
    /** For each car, the player whose car last hit it (0 or 1), or -1. */
    final int[] hitBy = new int[51];
    /** Each player's view of the cars: their distance from the camera, 0 if off screen. */
    final int[] onscreen1 = new int[51];
    final int[] onscreen2 = new int[51];
    /** Which players' cars are wasted. */
    final boolean[] wasted = new boolean[2];
    /** The car that won, or -1 while the race is on or if nobody did. */
    int winner = -1;
    /** The race ended at the finish line, not by wrecks. */
    boolean finished;
    /** The race is over: each pane shows how it ended, then the results. */
    boolean over;
    /** Ticks since the race ended. */
    int overTicks;

    SplitRace(GameUi ui, int rivalsNow) {
        this.rivalsNow = rivalsNow;
        this.hud2 = new PlayerHud(ui, 1);
        java.util.Arrays.fill(hitBy, -1);
    }

    /** Car {@code target} was hit by player {@code by}'s car: if it is wrecked, it was that player. */
    static void hit(int target, int by) {
        SplitRace s = active;
        if (s != null) {
            s.hitBy[target] = by;
        }
    }

    /**
     * Looks at the race after a tick: true the tick it ends. The first car
     * over the finish line wins. Otherwise it is down to wrecks: a player
     * whose car is the only one left wins, and with both players wasted
     * nobody does; a player wasted while the other races on watches their
     * wreck until it ends.
     */
    boolean judge(Madness[] drivers, CheckPoints checkpoints, int cars) {
        if (over) {
            overTicks++;
            return false;
        }
        int asked = endAsked;
        if (asked != NO_END) {
            return end(asked, false);
        }
        wasted[0] = drivers[0].dest;
        wasted[1] = drivers[1].dest;
        for (int car = 0; car < cars; car++) {
            if (checkpoints.clear[car] == checkpoints.nlaps * checkpoints.nsp && checkpoints.pos[car] == 0) {
                return end(car, true);
            }
        }
        if (wasted[0] && wasted[1]) {
            return end(-1, false);
        }
        int left = -1;
        for (int car = 0; car < cars; car++) {
            if (!drivers[car].dest) {
                if (left != -1) {
                    return false; // two or more still racing
                }
                left = car;
            }
        }
        return left >= 0 && left < 2 && end(left, false);
    }

    /** No end asked for (see {@link #endNow}). */
    private static final int NO_END = -2;
    private volatile int endAsked = NO_END;

    /** Ends the race at the next tick, won by {@code car} or nobody (-1): for the hidden test run. Any thread. */
    void endNow(int car) {
        endAsked = car;
    }

    private boolean end(int car, boolean atFinish) {
        winner = car;
        finished = atFinish;
        over = true;
        return true;
    }

    /** The name a car goes by in captions: "Player 1", "Player 2", or the computer car's own. */
    static String nameOf(int car, String carName) {
        SplitRace s = active;
        return s != null && car < 2 ? "Player " + (car + 1) : carName;
    }

    /**
     * Whose the controller at {@code index} of {@code count} connected is:
     * the player who chose it, else the players on Auto take the ones no one
     * chose, in order (Player 1 the first); -2 for no one.
     */
    static int padOwner(int index, int count) {
        int first = pads[0];
        int second = pads[1];
        if (first == index) {
            return 0;
        }
        if (second == index) {
            return 1;
        }
        int free = 0; // the controllers before this one that no one chose
        for (int i = 0; i < index && i < count; i++) {
            if (i != first && i != second) {
                free++;
            }
        }
        int auto = 0;
        for (int p = 0; p < 2; p++) {
            if (pads[p] == PAD_AUTO) {
                if (auto == free) {
                    return p;
                }
                auto++;
            }
        }
        return -2;
    }

    /* ------------------------------------------------------------ the panes */

    /**
     * Where the panes are, for a window drawn at {@code width} x
     * {@code height}: side by side or one above the other, as chosen or as
     * suits the window's shape.
     */
    static boolean sideBySide(int width, int height, int choice) {
        if (choice != VIEW_AUTO) {
            return choice == VIEW_SIDE;
        }
        double wide = Math.log(16D / 9D);
        double side = Math.abs(Math.log(width / 2D / Math.max(1, height)) - wide);
        double stacked = Math.abs(Math.log(width / (height / 2D)) - wide);
        return side < stacked - 1E-9; // a tie (a 16:9 window) goes to top and bottom
    }

    /**
     * A pane's picture in the game's units, for a pane {@code width} x
     * {@code height} of the window: at least 4:3 it keeps the game's height
     * (a wider pane sees more to the sides); narrower, it keeps 960 wide and
     * grows taller, so the cars stay a size to drive by. Even sizes.
     */
    static Dimension paneShape(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new Dimension(GameFacts.GAME_WIDTH, GameFacts.GAME_HEIGHT);
        }
        double shape = width / (double) height;
        if (shape >= 4D / 3D) {
            return new Dimension(Math.max(960, (int) Math.round(GameFacts.GAME_HEIGHT * shape / 2D) * 2),
                    GameFacts.GAME_HEIGHT);
        }
        return new Dimension(960, Math.max(GameFacts.GAME_HEIGHT, (int) Math.round(960D / shape / 2D) * 2));
    }

    /** The smallest pane a HUD is drawn full size in: half the game's view either way, which its corners fit. */
    private static final double HUD_MIN_WIDTH = GameFacts.GAME_WIDTH / 2D;
    private static final double HUD_MIN_HEIGHT = GameFacts.GAME_HEIGHT / 2D;

    /**
     * The screen a pane's HUD is drawn on, for a pane {@code width} x
     * {@code height} of the menus' picture: the pane itself, at the HUD's
     * full size, or for a pane smaller than half the game's view a larger
     * screen of its shape, shown smaller, so the HUD keeps its layout.
     */
    static Dimension hudShape(double width, double height) {
        double scale = Math.min(1D, Math.min(width / HUD_MIN_WIDTH, height / HUD_MIN_HEIGHT));
        if (scale <= 0D) {
            return new Dimension(GameFacts.GAME_WIDTH, GameFacts.GAME_HEIGHT);
        }
        return new Dimension((int) Math.round(width / scale), (int) Math.round(height / scale));
    }

    /** The race's picture for split screen in a window {@code width} x {@code height}: both panes together. */
    static Dimension pictureShape(int width, int height, int choice) {
        boolean side = sideBySide(width, height, choice);
        Dimension pane = side ? paneShape(width / 2, height) : paneShape(width, height / 2);
        return side ? new Dimension(pane.width * 2, pane.height) : new Dimension(pane.width, pane.height * 2);
    }

    /** Player {@code player}'s pane in a picture {@code width} x {@code height} split as {@code side}. */
    static Rectangle pane(int player, int width, int height, boolean side) {
        if (side) {
            int half = width / 2;
            return player == 0 ? new Rectangle(0, 0, half, height) : new Rectangle(half, 0, width - half, height);
        }
        int half = height / 2;
        return player == 0 ? new Rectangle(0, 0, width, half) : new Rectangle(0, half, width, height - half);
    }

    /**
     * The race's picture now is split: both panes, side by side or not, as
     * {@link #pictureShape} made it. Written with the picture's size.
     */
    static volatile boolean pictureSplit;
    static volatile boolean pictureSide;

    /** The split-screen race the race's picture is split for now; null when it is not split. */
    static SplitRace inPicture() {
        return pictureSplit ? active : null;
    }

    /** How the window is shared now, in words, for the settings: "side by side, each view 960x1080". */
    static String describe(int width, int height) {
        boolean side = sideBySide(width, height, view);
        Rectangle p = pane(0, width, height, side);
        return (side ? "side by side" : "top and bottom") + ", each view " + p.width + "x" + p.height;
    }
}
