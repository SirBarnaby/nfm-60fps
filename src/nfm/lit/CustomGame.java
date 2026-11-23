package nfm.lit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Custom game: a race with as many computer cars as the player likes, up to
 * {@link #MAX_RIVALS}, driving the cars of a lineup the player chooses:
 * Default (the cars the classic game brings to the stage), Novice (the cars
 * up to Drifter X), Pro (DR Monstaa first, then down the car list, the cars
 * after Drifter X only) or Custom (how many of each car, picked one by one).
 * <p>
 * This holds the choices, the cars each lineup brings, where every car starts
 * and, with more than {@link #STAGGER_ABOVE} computer cars, the staggered
 * start: groups of {@link #GROUP} go one after the other, front first,
 * {@link #LAUNCH_GAP_TICKS} apart, and the player, always last on the grid,
 * goes with the last group.
 * <p>
 * The classic game never turns it on: {@link #on} is false outside the custom
 * game's own screens and races, and every custom-game branch before a race is
 * behind it; a race's own staggered start is in {@link World#startGroups},
 * which is 0 in every other race. So the classic races, split screen and
 * every recorded race are as before.
 */
final class CustomGame {

    /** The most computer cars a custom game has. */
    static final int MAX_RIVALS = 32;
    /** The classic game's computer cars: the count a custom game starts with. */
    static final int CLASSIC_RIVALS = 6;

    /** The lineups: whose cars the computer cars drive. */
    static final int LINEUP_DEFAULT = 0;
    static final int LINEUP_NOVICE = 1;
    static final int LINEUP_PRO = 2;
    static final int LINEUP_CUSTOM = 3;
    static final int LINEUPS = 4;

    /** Drifter X: the last of the Novice cars. The Pro cars are the ones after it, up to DR Monstaa. */
    static final int LAST_NOVICE = 8;

    /** With more computer cars than this they start in groups; with this many or fewer, all at once. */
    static final int STAGGER_ABOVE = 9;
    /** The computer cars in a group that starts at once. */
    static final int GROUP = 6;
    /** Race ticks from one group's start to the next: 19 ticks of 53 ms, a second (at most 2 s apart). */
    static final int LAUNCH_GAP_TICKS = 19;

    static volatile int rivals = CLASSIC_RIVALS;
    static volatile int lineup = LINEUP_DEFAULT;
    /** How many of each car the Custom lineup brings, by car. */
    static final int[] picks = new int[GameFacts.numberOfCars];
    static volatile boolean changed;

    /**
     * The custom game is on: picked from the main menu, through its screen,
     * the car and stage select, the race and its results, until back at the
     * main menu. Game thread.
     */
    static boolean on;

    private CustomGame() {
    }

    /* ============================================================ choices */

    /** The computer cars the next race has: the count chosen, or every car picked for the Custom lineup. */
    static int rivalsNow() {
        return lineup == LINEUP_CUSTOM ? picked() : rivals;
    }

    /** How many cars the Custom lineup has picked in all. */
    static int picked() {
        int sum = 0;
        for (int p : picks) {
            sum += p;
        }
        return sum;
    }

    /** Sets the computer cars, none to {@link #MAX_RIVALS}. */
    static void setRivals(int count) {
        rivals = count;
        changed = true;
    }

    static void setLineup(int chosen) {
        lineup = chosen;
        changed = true;
    }

    /**
     * Sets how many of {@code car} the Custom lineup brings, and makes the
     * lineup Custom. False if that would take the lineup past
     * {@link #MAX_RIVALS} cars (or below none), and nothing changes.
     */
    static boolean setPick(int car, int count) {
        if (count < 0 || picked() - picks[car] + count > MAX_RIVALS) {
            return false;
        }
        picks[car] = count;
        lineup = LINEUP_CUSTOM;
        changed = true;
        return true;
    }

    /** Takes every car out of the Custom lineup. */
    static void clearPicks() {
        java.util.Arrays.fill(picks, 0);
        changed = true;
    }

    /**
     * Sets the choices from saved or typed values; anything out of range
     * leaves that choice as it is. {@code savedPicks} is a count per car,
     * comma separated, or null.
     */
    static void load(int savedRivals, int savedLineup, String savedPicks) {
        if (savedRivals >= 0 && savedRivals <= MAX_RIVALS) {
            rivals = savedRivals;
        }
        if (savedLineup >= 0 && savedLineup < LINEUPS) {
            lineup = savedLineup;
        }
        int[] counts = parsePicks(savedPicks);
        if (counts != null) {
            System.arraycopy(counts, 0, picks, 0, picks.length);
        }
        changed = false;
    }

    /** The Custom lineup's counts as saved: one per car, comma separated. */
    static String picksText() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < picks.length; i++) {
            b.append(i == 0 ? "" : ",").append(picks[i]);
        }
        return b.toString();
    }

    /** True if {@code text} is picks as {@link #picksText} writes them, adding up to at most MAX_RIVALS. */
    static boolean readsAsPicks(String text) {
        return parsePicks(text) != null;
    }

    /** A count per car, comma separated, or null if it is not one that adds up to at most MAX_RIVALS. */
    private static int[] parsePicks(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != GameFacts.numberOfCars) {
            return null;
        }
        int[] counts = new int[parts.length];
        int sum = 0;
        try {
            for (int i = 0; i < parts.length; i++) {
                counts[i] = Integer.parseInt(parts[i].trim());
                if (counts[i] < 0) {
                    return null;
                }
                sum += counts[i];
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return sum <= MAX_RIVALS ? counts : null;
    }

    /** A lineup by name, as the console and -Dnfm.custom.lineup take it, or -1. */
    static int lineupNamed(String name) {
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "default", "0" -> LINEUP_DEFAULT;
            case "novice", "1" -> LINEUP_NOVICE;
            case "pro", "2" -> LINEUP_PRO;
            case "custom", "3" -> LINEUP_CUSTOM;
            default -> -1;
        };
    }

    static String lineupText(int lineup) {
        return switch (lineup) {
            case LINEUP_NOVICE -> "Novice";
            case LINEUP_PRO -> "Pro";
            case LINEUP_CUSTOM -> "Custom";
            default -> "Default";
        };
    }

    /** What a lineup brings, in a line for the screens. */
    static String lineupHelp(int lineup, String[] names) {
        return switch (lineup) {
            case LINEUP_NOVICE -> names[0] + " up to " + names[LAST_NOVICE] + ", in a shuffled order";
            case LINEUP_PRO -> "Pro cars only: " + names[GameFacts.numberOfCars - 1]
                    + " first, then down the line to " + names[LAST_NOVICE + 1];
            case LINEUP_CUSTOM -> "You pick them: how many of each car";
            default -> "The cars this stage brings in the classic game, more of them in a bigger race";
        };
    }

    static String rivalsText(int rivals) {
        return rivals == 0 ? "None: a practice race" : String.valueOf(rivals);
    }

    /**
     * The choices this race is played with, as a recorded race keeps them
     * ({@code rivals;lineup;picks}), or empty if it is not a custom game's.
     */
    static String recorded() {
        return on ? rivals + ";" + lineup + ";" + picksText() : "";
    }

    /**
     * Sets up the next race as recorded (see {@link #recorded}): a custom
     * game's with its choices, or, for an empty one, any other race.
     */
    static void replay(String recorded) {
        on = !recorded.isEmpty();
        if (on) {
            String[] parts = recorded.split(";");
            load(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
        }
    }

    /* =============================================================== cars */

    /**
     * True if the classic game's own choice of cars ({@code GameUi.sortcars})
     * picks this race's computer cars: the Default lineup with no more
     * computer cars than the classic game's {@link #CLASSIC_RIVALS}, so
     * Default with six is exactly the classic game.
     */
    static boolean classicPicks() {
        return lineup == LINEUP_DEFAULT && rivals <= CLASSIC_RIVALS;
    }

    /**
     * Fills {@code sc[1]} to {@code sc[cars - 1]} with the computer cars'
     * cars, for any lineup but the classic game's own (see
     * {@link #classicPicks}). The car in the highest slot starts at the
     * front of the grid, so the strongest go there: the Default lineup's
     * stage boss, DR Monstaa in Pro, the best car picked in Custom.
     *
     * @param stage    the stage (-1 for one loaded from a file)
     * @param unlocked the stages the player has unlocked
     * @param deck     shuffles: the race's own stream for the opponents, so a
     *                 race played again from its seed has the same cars
     */
    static void pickCars(int[] sc, int cars, int stage, int unlocked, Random deck) {
        int last = cars - 1;
        switch (lineup) {
            case LINEUP_NOVICE -> deal(sc, 1, last, range(0, LAST_NOVICE, -1), deck);
            case LINEUP_PRO -> {
                // DR Monstaa at the front, then down the line; past Sword of Justice, from DR Monstaa again
                int pros = GameFacts.numberOfCars - 1 - LAST_NOVICE;
                for (int i = 0; i < last; i++) {
                    sc[last - i] = GameFacts.numberOfCars - 1 - i % pros;
                }
            }
            case LINEUP_CUSTOM -> {
                // the best car picked at the front, the rest behind it in the car list's order, downward
                int slot = last;
                for (int car = GameFacts.numberOfCars - 1; car >= 0; car--) {
                    for (int n = 0; n < picks[car] && slot >= 1; n++) {
                        sc[slot--] = car;
                    }
                }
            }
            default -> pickDefault(sc, last, stage, unlocked, deck);
        }
    }

    /**
     * The Default lineup in a race bigger than the classic one: the cars the
     * classic game draws for this stage with six computer cars (the same
     * span of the car list, and the stage's boss at the front), each shuffled
     * span dealt in turn until every computer car has one.
     */
    private static void pickDefault(int[] sc, int last, int stage, int unlocked, Random deck) {
        int classicCars = CLASSIC_RIVALS + 1;
        int dealTo = last;
        if (stage != 0) {
            int boss = GameUi.stageBoss(stage, classicCars);
            if (stage != -1 && sc[0] != boss) {
                sc[last] = boss;
                dealTo--;
            }
            int from = GameUi.carSpanStage(stage, unlocked) / 2;
            deal(sc, 1, dealTo, range(from, Math.min(from + classicCars - 1, GameFacts.numberOfCars - 1), sc[0]),
                    deck);
        } else {
            deal(sc, 1, dealTo, range(0, CLASSIC_RIVALS, sc[0]), deck);
        }
    }

    /** Cars {@code from} to {@code to}, but not {@code except}. */
    private static List<Integer> range(int from, int to, int except) {
        List<Integer> out = new ArrayList<>();
        for (int car = from; car <= to; car++) {
            if (car != except) {
                out.add(car);
            }
        }
        return out;
    }

    /**
     * Gives {@code sc[from]} to {@code sc[to]} a car each from {@code cars},
     * shuffled with {@code deck}: every car once before any twice, shuffled
     * again for each round.
     */
    private static void deal(int[] sc, int from, int to, List<Integer> cars, Random deck) {
        if (cars.isEmpty()) {
            cars = range(0, GameFacts.numberOfCars - 1, -1);
        }
        List<Integer> round = new ArrayList<>(cars);
        int at = round.size();
        for (int slot = from; slot <= to; slot++) {
            if (at == round.size()) {
                Collections.shuffle(round, deck);
                at = 0;
            }
            sc[slot] = round.get(at++);
        }
    }

    /* =============================================================== grid */

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
     * True if a race of {@code cars} (the player's included) gets the
     * staggered start and its grid: decided as the grid is laid out, and kept
     * in {@link World#startGroups} for the race.
     */
    static boolean staggered(int cars) {
        return on && cars - 1 > STAGGER_ABOVE;
    }

    /** The groups the computer cars of a staggered race start in. */
    static int groups(int cars) {
        return (cars - 1 + GROUP - 1) / GROUP;
    }

    /**
     * Where car {@code slot} of a staggered race of {@code cars} starts on the
     * track: {x, z}. The player (slot 0) is behind everyone, in the middle;
     * the computer cars stand in groups of six across, the highest slot at
     * the front. A group's columns alternate, every other one half a row
     * back, so neighbours stand as far apart as on the classic grid. The rows
     * are squeezed together just enough for the grid to fit between
     * {@link #GRID_BACK} and {@link #GRID_FRONT}.
     */
    static int[] gridPlace(int slot, int cars) {
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
    static int flybyCar(int cars) {
        return cars / 2;
    }

    /* ============================================================== start */

    /** The group car {@code car} starts with (0 first): the player goes with the last. */
    static int launchGroup(int car, int cars) {
        int rivals = cars - 1;
        return car == 0 ? groups(cars) - 1 : (rivals - car) / GROUP;
    }

    /** True if car {@code car} still waits for its group's start, {@code ticksSinceGo} after "Go!". */
    static boolean held(int car, int cars, int ticksSinceGo) {
        return ticksSinceGo < launchGroup(car, cars) * LAUNCH_GAP_TICKS;
    }

    /** Ticks until the player's group starts, 0 once it has. */
    static int playerWait(int cars, int ticksSinceGo) {
        return Math.max(0, launchGroup(0, cars) * LAUNCH_GAP_TICKS - ticksSinceGo);
    }

    /** The groups that have started by {@code ticksSinceGo}. */
    static int groupsGone(int cars, int ticksSinceGo) {
        return Math.min(groups(cars), ticksSinceGo / LAUNCH_GAP_TICKS + 1);
    }

    /**
     * Holds the cars whose group has not started yet: no throttle, brake or
     * steering, the handbrake on. Called once a tick before the cars drive;
     * a held computer car's own driving is not worked out until it goes (see
     * {@link #held}), so it sets off as a car does at the start.
     */
    static void holdWaiting(Control[] drivers, int cars, int ticksSinceGo) {
        for (int car = 0; car < cars; car++) {
            if (held(car, cars, ticksSinceGo)) {
                Control c = drivers[car];
                c.up = false;
                c.down = false;
                c.left = false;
                c.right = false;
                c.handb = true;
            }
        }
    }
}
