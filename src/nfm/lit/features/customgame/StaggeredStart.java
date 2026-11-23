package nfm.lit.features.customgame;

import nfm.lit.Control;
import nfm.lit.World;

/**
 * A big custom game's grid and start. With more than {@link #STAGGER_ABOVE}
 * computer cars they stand in groups of {@link #GROUP} across and go one
 * group after another, front first, {@link #LAUNCH_GAP_TICKS} apart; the
 * player, always last on the grid, goes with the last group.
 * <p>
 * The start is race state: when the grid is laid out the race keeps its
 * groups ({@link World#startGroups}, 0 when everyone starts at once, as in
 * every other race) and counts the ticks since Go! ({@link World#ticksSinceGo}).
 * Both are in {@link World}, which run-ahead and rollback capture and put
 * back. The race tick, the fly-by, the HUD and the frame log read them,
 * never the menu's choices.
 */
public final class StaggeredStart {

    /** With more computer cars than this they start in groups; with this many or fewer, all at once. */
    public static final int STAGGER_ABOVE = 9;
    /** The computer cars in a group that starts at once. */
    public static final int GROUP = 6;
    /** Race ticks from one group's start to the next: 19 ticks of 53 ms, a second (at most 2 s apart). */
    public static final int LAUNCH_GAP_TICKS = 19;
    /** The most race ticks the owner allows between two groups' starts: 2 seconds. */
    public static final int MOST_TICKS_APART = 38;

    private StaggeredStart() {
    }

    /* =============================================================== grid */

    /**
     * The groups a race of {@code cars} (the player's included) starts in:
     * a custom game's with more than {@link #STAGGER_ABOVE} computer cars,
     * else 0 (everyone at once). For {@link World#startGroups}, as the grid is
     * laid out.
     */
    public static int groupsFor(int cars) {
        return CustomGame.on && cars - 1 > STAGGER_ABOVE ? groups(cars) : 0;
    }

    /** The groups the computer cars of a staggered race start in. */
    public static int groups(int cars) {
        return (cars - 1 + GROUP - 1) / GROUP;
    }

    /** Lengthwise, every grid stays between these, so no car starts on the ramps or bends either side of a stage's start. */
    private static final int GRID_BACK = -1860;
    private static final int GRID_FRONT = 1980;
    /** Across: a group's six columns, 300 apart. */
    private static final int COLUMN_GAP = 300;
    /** From a group's front cars to the next group's, as on the classic grid, and the least it may be squeezed to. */
    private static final int ROW_PITCH = 760;
    private static final int MIN_ROW_PITCH = 600;
    /** From the last group's rearmost car to the player, in the middle behind it. */
    private static final int PLAYER_GAP = 440;
    /** The order a group's columns fill: from the middle out, so a group short of six stays in the middle. */
    private static final int[] COLUMN_ORDER = {2, 3, 1, 4, 0, 5};

    /**
     * Where car {@code slot} of a staggered race of {@code cars} starts on the
     * track: {x, z}. The player (slot 0) is behind everyone, in the middle;
     * the computer cars stand in groups of six across, the highest slot at
     * the front. A group's columns alternate, every other one half a row
     * back, so neighbours stand as far apart as on the classic grid. The rows
     * are squeezed together just enough for the grid to fit between
     * {@link #GRID_BACK} and {@link #GRID_FRONT}.
     */
    public static int[] gridPlace(int slot, int cars) {
        int rivals = cars - 1;
        int groups = groups(cars);
        int inLast = rivals - (groups - 1) * GROUP;
        boolean lastHalfRow = inLast >= 2; // a second column is half a row back
        float rowsDeep = groups - 1 + (lastHalfRow ? 0.5F : 0F);
        int pitch = (int) Math.max(MIN_ROW_PITCH,
                Math.min(ROW_PITCH, (GRID_FRONT - GRID_BACK - PLAYER_GAP) / Math.max(1F, rowsDeep)));
        int span = Math.round(rowsDeep * pitch) + PLAYER_GAP;
        int front = (GRID_FRONT + GRID_BACK) / 2 + span / 2;
        if (slot == 0) {
            return new int[] {0, front - span};
        }
        int rank = rivals - slot; // 0 at the front
        int group = rank / GROUP;
        int column = COLUMN_ORDER[rank % GROUP];
        int x = Math.round((column - (GROUP - 1) / 2F) * COLUMN_GAP); // -750 to 750
        int z = front - group * pitch - (column % 2 == 1 ? pitch / 2 : 0);
        return new int[] {x, z};
    }

    /** The car the fly-by circles before the countdown on a staggered grid: one in its middle. */
    public static int flybyCar(int cars) {
        return cars / 2;
    }

    /* ============================================================== start */

    /** The group car {@code car} starts with (0 first): the player goes with the last. */
    public static int launchGroup(int car, int cars) {
        int rivals = cars - 1;
        return car == 0 ? groups(cars) - 1 : (rivals - car) / GROUP;
    }

    /** The tick after Go! a group starts on: a second after the group before it. */
    public static int launchTick(int group) {
        return group * LAUNCH_GAP_TICKS;
    }

    /** True if car {@code car} still waits for its group's start on tick {@code tick} after "Go!". */
    static boolean held(int car, int cars, int tick) {
        return tick < launchTick(launchGroup(car, cars));
    }

    /** True if the player still waits on the grid of {@code world}'s staggered start. */
    public static boolean playerWaiting(World world, int cars) {
        return world.staggered() && held(0, cars, world.ticksSinceGo);
    }

    /**
     * Before the cars drive, each tick from Go! on: the cars whose group has
     * not started stand with no throttle, brake or steering and the
     * handbrake on. Nothing in a race whose cars start at once.
     */
    public static void beforeDrive(World world, Control[] drivers, int cars) {
        if (!world.staggered()) {
            return;
        }
        for (int car = 0; car < cars; car++) {
            if (held(car, cars, world.ticksSinceGo)) {
                Control c = drivers[car];
                c.up = false;
                c.down = false;
                c.left = false;
                c.right = false;
                c.handb = true;
            }
        }
    }

    /**
     * True if computer car {@code car}'s driving is worked out after this
     * tick's drive, as the game does for every computer car: its controls
     * are for the next tick, so they are worked out from the tick before its
     * group starts, and it sets off on its group's start, as the player does.
     */
    public static boolean drivesNext(World world, int car, int cars) {
        return !world.staggered() || !held(car, cars, world.ticksSinceGo + 1);
    }

    /** After the cars drive: the staggered start's clock goes on. */
    public static void afterDrive(World world) {
        if (world.staggered()) {
            world.ticksSinceGo++;
        }
    }

    /* =========================================================== the HUD */

    /** Where the staggered start's captions go: under the countdown's portrait, which is 160 high. */
    public static final int CAPTION_Y = 180;
    /** How long the player's own Go! stays up: about half a second. */
    private static final int GO_TICKS = 10;

    /** What the HUD shows of a staggered start in a frame: see {@link #countdown}. */
    public static final class Countdown {
        /** A line under the portrait, or null. */
        public String caption;
        /** The countdown's number, 3 to 1, 0 for Go!, or -1 for no countdown. */
        public int number;
        /** The portrait: 0 still, 1 speaking, 2 at Go!. */
        public int talking;
        /** What the countdown's voice says this frame ("three", "two", "one", "go"), or null. */
        public String voice;
    }

    /**
     * A staggered start's own countdown, for the HUD in a frame of the race:
     * under the classic countdown ({@code starcnt} above 0, which starts the
     * front group) a line on how the start goes; then, while the player
     * waits, which group is off and the seconds left, and for the last three
     * the classic countdown's numbers, voice and Go! once more, for the
     * player's own start.
     */
    public static void countdown(World world, int cars, int starcnt, Countdown out) {
        out.caption = null;
        out.number = -1;
        out.talking = 0;
        out.voice = null;
        int groups = world.startGroups;
        if (starcnt != 0) {
            if (starcnt <= 35) {
                out.caption = "Staggered start: " + groups + " groups, a second apart. You go last.";
            }
            return;
        }
        int gap = LAUNCH_GAP_TICKS;
        int tick = world.ticksSinceGo - 1; // the tick just run: 0 the first after Go!, -1 on Go!'s own frame
        int launch = launchGroup(0, cars); // the player's wait in seconds, counted from Go!
        int wait = launchTick(launch) - tick;
        // the seconds left, 0 for Go!; never more than the whole wait, which Go!'s own frame would round up past
        int number = wait <= 0 ? 0 : Math.min((wait + gap - 1) / gap, launch);
        if (number > 3) {
            int gone = Math.min(groups, tick / gap + 1);
            out.caption = "Group " + gone + " of " + groups + " is off. You go in " + number;
            return;
        }
        if (wait < -GO_TICKS) {
            return;
        }
        if (wait == number * gap) {
            out.voice = switch (number) {
                case 3 -> "three";
                case 2 -> "two";
                case 1 -> "one";
                default -> "go";
            };
        }
        out.number = number;
        out.talking = number == 0 ? 2 : wait > number * gap - 5 ? 1 : 0; // the portrait speaks each number
    }
}
