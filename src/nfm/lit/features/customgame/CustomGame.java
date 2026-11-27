package nfm.lit.features.customgame;

import fallk.logmaster.HLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import nfm.lit.GameFacts;
import nfm.lit.GameUi;

/**
 * Custom game: a race with as many computer cars as the player likes, up to
 * {@link #MAX_RIVALS}, driving the cars of a lineup the player chooses:
 * Default (the cars the classic game brings to the stage), Novice (the cars
 * up to Drifter X), Pro (DR Monstaa first, then down the car list, the cars
 * after Drifter X only) or Custom (how many of each car, picked one by one).
 * <p>
 * This holds the choices, how they are saved and typed, and the cars each
 * lineup brings; {@link StaggeredStart} holds where the cars start and how
 * a big field sets off, and {@link CustomGamePage} the page the choices are
 * made on.
 * <p>
 * The classic game never turns it on: {@link #on} is false outside the custom
 * game's own screens and races, which only its switch in the extras
 * ({@link CustomGameFeature}) leads to, and every custom-game branch before
 * a race is behind it; a race's own staggered start is in the race state,
 * which is 0 groups in every other race. So the classic races, split screen
 * and every recorded race are as before.
 */
public final class CustomGame {

    /** The most computer cars a custom game has. */
    public static final int MAX_RIVALS = 32;
    /** The classic game's computer cars: the count a custom game starts with. */
    static final int CLASSIC_RIVALS = 6;

    /** The lineups: whose cars the computer cars drive. */
    public static final int LINEUP_DEFAULT = 0;
    public static final int LINEUP_NOVICE = 1;
    public static final int LINEUP_PRO = 2;
    public static final int LINEUP_CUSTOM = 3;
    static final int LINEUPS = 4;

    /** Drifter X: the last of the Novice cars. The Pro cars are the ones after it, up to DR Monstaa. */
    public static final int LAST_NOVICE = 8;

    private static volatile int rivals = CLASSIC_RIVALS;
    private static volatile int lineup = LINEUP_DEFAULT;
    /** How many of each car the Custom lineup brings, by car. */
    private static final int[] picks = new int[GameFacts.numberOfCars];

    /*
     * the choices the race being set up is made from, fixed with its field (fixField): the page or the console can
     * change the choices from then on, and that is for the next race, never for the cars, the recording or the check
     */
    private static int fieldRivals = CLASSIC_RIVALS;
    private static int fieldLineup = LINEUP_DEFAULT;
    private static final int[] fieldPicks = new int[GameFacts.numberOfCars];

    /* which choices were changed since they were last saved: only those are saved, so a choice given for one run is not */
    private static volatile boolean rivalsChanged;
    private static volatile boolean lineupChanged;
    private static volatile boolean picksChanged;

    /**
     * The custom game is on: picked from the main menu, through its page,
     * the car and stage select, the race and its results, until back at the
     * main menu; or set by a recorded race ({@link #replay}). Game thread.
     */
    public static boolean on;

    private CustomGame() {
    }

    /* ============================================================ choices */

    /** The computer cars chosen (the Custom lineup brings the cars picked instead, see {@link #rivalsNow}). */
    public static int rivals() {
        return rivals;
    }

    public static int lineup() {
        return lineup;
    }

    /** How many of {@code car} the Custom lineup brings. */
    public static int picks(int car) {
        return picks[car];
    }

    /** The computer cars the next race has: the count chosen, or every car picked for the Custom lineup. */
    public static int rivalsNow() {
        return lineup == LINEUP_CUSTOM ? picked() : rivals;
    }

    /** How many cars the Custom lineup has picked in all. */
    public static int picked() {
        int sum = 0;
        for (int p : picks) {
            sum += p;
        }
        return sum;
    }

    /** Sets the computer cars, none to {@link #MAX_RIVALS}. */
    public static void setRivals(int count) {
        rivals = count;
        rivalsChanged = true;
    }

    public static void setLineup(int chosen) {
        lineup = chosen;
        lineupChanged = true;
    }

    /**
     * Sets how many of {@code car} the Custom lineup brings, and makes the
     * lineup Custom. False if that would take the lineup past
     * {@link #MAX_RIVALS} cars (or below none), and nothing changes.
     */
    public static boolean setPick(int car, int count) {
        if (count < 0 || picked() - picks[car] + count > MAX_RIVALS) {
            return false;
        }
        picks[car] = count;
        picksChanged = true;
        setLineup(LINEUP_CUSTOM);
        return true;
    }

    /**
     * Fixes the next race's choices as they are now, when its field is made
     * ({@code GameUi.carspergame}), and gives its computer cars. Its cars,
     * its recording and the hidden test's check use these, whatever is
     * changed afterwards.
     */
    public static int fixField() {
        fieldRivals = rivals;
        fieldLineup = lineup;
        System.arraycopy(picks, 0, fieldPicks, 0, picks.length);
        return fieldCars();
    }

    /** The computer cars of the race being set up (see {@link #fixField}). */
    public static int fieldCars() {
        return fieldLineup == LINEUP_CUSTOM ? Arrays.stream(fieldPicks).sum() : fieldRivals;
    }

    /** The lineup of the race being set up (see {@link #fixField}). */
    public static int fieldLineup() {
        return fieldLineup;
    }

    /** How many of {@code car} the race being set up brings with the Custom lineup (see {@link #fixField}). */
    public static int fieldPicks(int car) {
        return fieldPicks[car];
    }

    /** Takes every car out of the Custom lineup. */
    public static void clearPicks() {
        Arrays.fill(picks, 0);
        picksChanged = true;
    }

    /**
     * For one hidden test run: these choices, as if saved, so the run never
     * saves them. {@code lineupName} as {@link #lineupNamed} takes it, or
     * null to keep the lineup.
     */
    public static void forTest(int count, String lineupName) {
        rivals = Math.clamp(count, 0, MAX_RIVALS);
        if (lineupName != null) {
            lineup = Math.max(0, lineupNamed(lineupName));
        }
    }

    /* ============================================================= saved */

    /* the saved choices; "custompicks" holds how many of each car the Custom lineup brings, comma separated */
    private static final String RIVALS_COOKIE = "customrivals";
    private static final String LINEUP_COOKIE = "customlineup";
    private static final String PICKS_COOKIE = "custompicks";

    /**
     * Reads the choices: each from its property for one run
     * ({@code -Dnfm.custom.rivals}, 0 to 32; {@code -Dnfm.custom.lineup},
     * default, novice, pro or custom; {@code -Dnfm.custom.picks}, a count
     * per car, comma separated), else as saved ({@code cookie}, -1 for none,
     * and {@code cookieText}, null for none), else as the classic game has
     * it. A property the game cannot read is logged and the saved choice
     * kept. Nothing read here is saved again unless it is changed.
     */
    public static void load(ToIntFunction<String> cookie, Function<String, String> cookieText) {
        String rivalsProperty = System.getProperty("nfm.custom.rivals");
        String lineupProperty = System.getProperty("nfm.custom.lineup");
        String picksProperty = System.getProperty("nfm.custom.picks");
        int savedRivals = cookie.applyAsInt(RIVALS_COOKIE);
        if (rivalsProperty != null) {
            try {
                savedRivals = Integer.parseInt(rivalsProperty.trim());
            } catch (NumberFormatException e) {
                HLogger.error("nfm.custom.rivals is not a number: " + rivalsProperty);
            }
        }
        int savedLineup = cookie.applyAsInt(LINEUP_COOKIE);
        if (lineupProperty != null) {
            if (lineupNamed(lineupProperty) >= 0) {
                savedLineup = lineupNamed(lineupProperty);
            } else {
                HLogger.error("nfm.custom.lineup is not default, novice, pro or custom: " + lineupProperty);
            }
        }
        String savedPicks = cookieText.apply(PICKS_COOKIE);
        if (picksProperty != null) {
            if (parsePicks(picksProperty) != null) {
                savedPicks = picksProperty;
            } else {
                HLogger.error("nfm.custom.picks wants " + GameFacts.numberOfCars
                        + " counts, one per car, comma separated, adding up to at most " + MAX_RIVALS + ": "
                        + picksProperty);
            }
        }
        set(savedRivals, savedLineup, savedPicks);
    }

    /** Saves each choice changed since it was last saved, with {@code save} (cookie name, value). */
    public static void saveIfChanged(BiConsumer<String, String> save) {
        if (rivalsChanged) {
            rivalsChanged = false;
            save.accept(RIVALS_COOKIE, String.valueOf(rivals));
        }
        if (lineupChanged) {
            lineupChanged = false;
            save.accept(LINEUP_COOKIE, String.valueOf(lineup));
        }
        if (picksChanged) {
            picksChanged = false;
            save.accept(PICKS_COOKIE, picksText());
        }
    }

    /**
     * Sets the choices from saved or recorded values, as unchanged; anything
     * out of range leaves that choice as it is. {@code savedPicks} is a
     * count per car, comma separated, or null.
     */
    private static void set(int savedRivals, int savedLineup, String savedPicks) {
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
        rivalsChanged = false;
        lineupChanged = false;
        picksChanged = false;
    }

    /** The Custom lineup's counts as saved: one per car, comma separated. */
    static String picksText() {
        return countsText(picks);
    }

    private static String countsText(int[] counts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            b.append(i == 0 ? "" : ",").append(counts[i]);
        }
        return b.toString();
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

    /* ============================================================ words */

    /** A lineup by name, as the console and -Dnfm.custom.lineup take it, or -1. */
    public static int lineupNamed(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "default", "0" -> LINEUP_DEFAULT;
            case "novice", "1" -> LINEUP_NOVICE;
            case "pro", "2" -> LINEUP_PRO;
            case "custom", "3" -> LINEUP_CUSTOM;
            default -> -1;
        };
    }

    public static String lineupText(int lineup) {
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

    /** A custom game's result, where a classic race names the stage it completes: the player's place in the field. */
    public static String resultLine(int place, int cars) {
        return "You came " + GameUi.ordinal(place + 1) + " of " + cars + "!";
    }

    /* ========================================================== recorded */

    /**
     * The choices this race is played with, fixed with its field (see
     * {@link #fixField}), as a recorded race keeps them
     * ({@code rivals;lineup;picks}), or empty if it is not a custom game's.
     */
    public static String recorded() {
        return on ? fieldRivals + ";" + fieldLineup + ";" + countsText(fieldPicks) : "";
    }

    /**
     * Sets up the next race as recorded (see {@link #recorded}): a custom
     * game's with its choices, or, for an empty one, any other race.
     */
    public static void replay(String recorded) {
        on = !recorded.isEmpty();
        if (on) {
            String[] parts = recorded.split(";");
            set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
            fixField();
        }
    }

    /* =============================================================== cars */

    /**
     * True if the classic game's own choice of cars ({@code GameUi.sortcars})
     * picks this race's computer cars: the Default lineup with no more
     * computer cars than the classic game's {@link #CLASSIC_RIVALS}, so
     * Default with six is exactly the classic game.
     */
    public static boolean classicPicks() {
        return fieldLineup == LINEUP_DEFAULT && fieldRivals <= CLASSIC_RIVALS;
    }

    /**
     * Fills {@code sc[1]} to {@code sc[cars - 1]} with the computer cars'
     * cars, by the choices fixed with the field ({@link #fixField}), for any
     * lineup but the classic game's own (see
     * {@link #classicPicks}). The car in the highest slot starts at the
     * front of the grid, so the strongest go there: the Default lineup's
     * stage boss, DR Monstaa in Pro, the best car picked in Custom.
     *
     * @param stage    the stage (-1 for one loaded from a file)
     * @param unlocked the stages the player has unlocked
     * @param deck     shuffles: the race's own stream for the opponents, so a
     *                 race played again from its seed has the same cars
     */
    public static void pickCars(int[] sc, int cars, int stage, int unlocked, Random deck) {
        int last = cars - 1;
        switch (fieldLineup) {
            case LINEUP_NOVICE -> deal(sc, 1, last, range(0, LAST_NOVICE, -1), deck);
            case LINEUP_PRO -> {
                // DR Monstaa at the front, then down the line; past Sword of Justice, from DR Monstaa again
                for (int i = 0; i < last; i++) {
                    sc[last - i] = proCar(i);
                }
            }
            case LINEUP_CUSTOM -> {
                // the best car picked at the front, the rest behind it in the car list's order, downward
                int slot = last;
                for (int car = GameFacts.numberOfCars - 1; car >= 0; car--) {
                    for (int n = 0; n < fieldPicks[car] && slot >= 1; n++) {
                        sc[slot--] = car;
                    }
                }
            }
            default -> pickDefault(sc, last, stage, unlocked, deck);
        }
    }

    /** The Pro lineup's car {@code i} places from the front: DR Monstaa, then down to the first car after Drifter X, and round again. */
    static int proCar(int i) {
        int pros = GameFacts.numberOfCars - 1 - LAST_NOVICE;
        return GameFacts.numberOfCars - 1 - i % pros;
    }

    /**
     * The Default lineup in a race bigger than the classic one: the cars the
     * classic game draws for this stage with six computer cars (the same
     * span of the car list, and the stage's boss at the front), each shuffled
     * span dealt in turn until every computer car has one.
     */
    private static void pickDefault(int[] sc, int last, int stage, int unlocked, Random deck) {
        int dealTo = last;
        if (stage != 0) {
            int boss = defaultBoss(stage);
            if (stage != -1 && sc[0] != boss) {
                sc[last] = boss;
                dealTo--;
            }
            deal(sc, 1, dealTo, defaultSpan(stage, unlocked, sc[0]), deck);
        } else {
            deal(sc, 1, dealTo, range(0, CLASSIC_RIVALS, sc[0]), deck);
        }
    }

    /** The boss the classic game brings to {@code stage} with six computer cars. */
    static int defaultBoss(int stage) {
        return GameUi.stageBoss(stage, CLASSIC_RIVALS + 1);
    }

    /** The span of the car list the classic game draws {@code stage}'s computer cars from, without the player's car. */
    static List<Integer> defaultSpan(int stage, int unlocked, int playerCar) {
        int from = GameUi.carSpanStage(stage, unlocked) / 2;
        return range(from, Math.min(from + CLASSIC_RIVALS, GameFacts.numberOfCars - 1), playerCar);
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

    /* ============================================================ console */

    /** The custom command's line in the developer console's help. */
    public static final String CONSOLE_HELP = "The custom game's choices, as on its page (Custom Game in the main menu,"
            + " with the Custom game switch on in Settings, Extras): how many computer cars, up to 32, and their cars"
            + " (Default, Novice, Pro, or Custom: picked one by one). Usage: custom, custom cars <0-32>,"
            + " custom lineup <default|novice|pro|custom>, custom pick <car 1-16> <how many>, custom pick clear";

    /**
     * The developer console's custom command: shows the choices, or changes
     * one (saved as its page saves them). Cars are picked by their number in
     * the car select, 1 for the first.
     */
    public static String console(String[] args, String[] names) {
        String usage = "Usage: custom cars <0-" + MAX_RIVALS + ">, custom lineup <default|novice|pro|custom>,"
                + " custom pick <car 1-" + GameFacts.numberOfCars + "> <how many>, custom pick clear";
        String what = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        String to = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String said = null;
        switch (what) {
            case "cars" -> {
                int n = number(to);
                if (n >= 0 && n <= MAX_RIVALS) {
                    setRivals(n);
                    said = "Computer cars: " + rivalsText(n);
                }
            }
            case "lineup" -> {
                int l = lineupNamed(to);
                if (l >= 0) {
                    setLineup(l);
                    said = "Their cars: " + lineupText(l) + " (" + lineupHelp(l, names) + ")";
                }
            }
            case "pick" -> {
                if (to.equals("clear")) {
                    clearPicks();
                    said = "No cars picked";
                } else {
                    int car = number(to) - 1;
                    int count = args.length > 2 ? number(args[2]) : -1;
                    if (car >= 0 && car < GameFacts.numberOfCars && setPick(car, count)) {
                        said = names[car] + ": " + count + " (picked: " + picked() + " of " + MAX_RIVALS
                                + "; their cars: Custom)";
                    }
                }
            }
            default -> {
                return describe(names) + "\n" + usage;
            }
        }
        return said == null ? usage : said + " (saved).";
    }

    private static String describe(String[] names) {
        StringBuilder chosen = new StringBuilder();
        for (int car = 0; car < GameFacts.numberOfCars; car++) {
            if (picks[car] > 0) {
                chosen.append(chosen.isEmpty() ? "" : ", ").append(picks[car]).append(" ").append(names[car]);
            }
        }
        return "Custom game" + (CustomGameFeature.onMenu() ? "" : " (its switch is off: Settings, Extras)")
                + ": computer cars: " + rivalsText(rivalsNow()) + "; their cars: " + lineupText(lineup)
                + "; picked: " + (chosen.isEmpty() ? "none" : chosen) + ".";
    }

    /** A whole number typed in the console, or -1. */
    private static int number(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
