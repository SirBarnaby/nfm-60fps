package nfm.lit.features.customgame;

import nfm.lit.GameFacts;
import nfm.lit.features.Page;

/**
 * The custom game's page, opened from Custom Game on the main menu and again
 * after each of its races: how many computer cars race and whose cars they
 * drive, with Race! to go on to the car select.
 */
public final class CustomGamePage {

    /** The page's title, over its box. */
    public static final String TITLE = "Custom Game";

    private CustomGamePage() {
    }

    /**
     * The page's rows: Race!, the computer cars and their lineup on the first
     * tab; how many of each car the Custom lineup brings on the second.
     * Picking a car there makes the lineup Custom.
     *
     * @param carNames the cars' names, by car
     * @param race     Race! was chosen
     */
    public static void rows(Page page, String[] carNames, Runnable race) {
        int[] counts = new int[CustomGame.MAX_RIVALS + 1];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = i;
        }
        page.tab("Race", "How many computer cars race you, and whose cars they drive.");
        page.group("Race");
        page.button("Race!", "Go", "Off to the car select, then the stage select, with the computer cars set"
                + " below. After the race you are back here.", race);
        page.choice("Computer cars",
                "How many computer cars race you, up to " + CustomGame.MAX_RIVALS + ". Over "
                        + StaggeredStart.STAGGER_ABOVE + " they start in groups of " + StaggeredStart.GROUP
                        + ", one group a second, the front first. You always start last.",
                () -> CustomGame.rivalsNow() > StaggeredStart.STAGGER_ABOVE
                        ? "Now: " + StaggeredStart.groups(CustomGame.rivalsNow() + 1)
                                + " groups, and you go with the last."
                        : "Now: everyone starts at once.",
                counts, CustomGame::rivalsText, CustomGame::rivalsNow, CustomGame::setRivals);
        page.onlyWhen(() -> CustomGame.lineup() != CustomGame.LINEUP_CUSTOM,
                "With the Custom lineup, the cars picked on the Pick cars tab are the computer cars.");
        page.choice("Their cars",
                "Default: the cars the stage brings in the classic game. Novice: " + carNames[0] + " up to "
                        + carNames[CustomGame.LAST_NOVICE] + ". Pro: the pro cars only, "
                        + carNames[GameFacts.numberOfCars - 1] + " first, then down from there. Custom: you pick"
                        + " them, on the Pick cars tab.",
                () -> CustomGame.lineupHelp(CustomGame.lineup(), carNames) + ".",
                new int[] {CustomGame.LINEUP_DEFAULT, CustomGame.LINEUP_NOVICE, CustomGame.LINEUP_PRO,
                    CustomGame.LINEUP_CUSTOM},
                CustomGame::lineupText, CustomGame::lineup, CustomGame::setLineup);

        page.tab("Pick cars", "The Custom lineup: how many of each car race you, up to " + CustomGame.MAX_RIVALS
                + " in all.");
        page.group("Cars");
        for (int c = 0; c < GameFacts.numberOfCars; c++) {
            final int car = c;
            page.choice(carNames[car],
                    "How many " + carNames[car] + "s race you. Picking a car makes the lineup Custom (Race tab);"
                            + " up to " + CustomGame.MAX_RIVALS + " cars in all.",
                    () -> "Picked: " + CustomGame.picked() + " of " + CustomGame.MAX_RIVALS + ".",
                    counts, String::valueOf, () -> CustomGame.picks(car), v -> CustomGame.setPick(car, v));
        }
        page.button("Clear the picks", "Clear", "Takes every car out of the Custom lineup.", CustomGame::clearPicks);
        page.onlyWhen(() -> CustomGame.picked() > 0, "No car is picked.");
    }
}
