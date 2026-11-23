package nfm.lit.features.customgame;

import java.util.Arrays;
import java.util.List;
import nfm.lit.GameFacts;

/**
 * What the hidden custom test ({@code -Dnfm.autotest.custom}) holds a custom
 * race to, from what the race shows rather than from the rules that made
 * it: the field the lineup should bring, the player last on the grid, and
 * each group seen setting off in turn, never before its start and at most
 * two seconds after the group before. Each check gives what is wrong, or
 * null.
 */
public final class CustomGameCheck {

    private CustomGameCheck() {
    }

    /**
     * What is wrong with a custom race's field of {@code cars} (the player's
     * included) driving {@code sc}, or null: the count chosen, and the cars
     * each lineup brings (Novice, cars 0 to 8, every one once before any
     * twice; Pro, 15 at the front then down to 9 and round again; Custom,
     * exactly the cars picked; Default above six, the stage's span of the car
     * list with its boss at the front).
     */
    public static String field(int[] sc, int cars, int stage, int unlocked) {
        int rivals = cars - 1;
        if (rivals != CustomGame.rivalsNow()) {
            return rivals + " computer cars where " + CustomGame.rivalsNow() + " were chosen";
        }
        int last = cars - 1;
        switch (CustomGame.lineup()) {
            case CustomGame.LINEUP_NOVICE -> {
                int[] count = new int[GameFacts.numberOfCars];
                for (int slot = 1; slot <= last; slot++) {
                    if (sc[slot] < 0 || sc[slot] > CustomGame.LAST_NOVICE) {
                        return "Novice brought car " + sc[slot] + " in slot " + slot;
                    }
                    count[sc[slot]]++;
                }
                int fewest = Integer.MAX_VALUE;
                int most = 0;
                for (int car = 0; car <= CustomGame.LAST_NOVICE; car++) {
                    fewest = Math.min(fewest, count[car]);
                    most = Math.max(most, count[car]);
                }
                if (rivals >= CustomGame.LAST_NOVICE + 1 && most - fewest > 1) {
                    return "Novice brought a car " + most + " times and another " + fewest + " times";
                }
            }
            case CustomGame.LINEUP_PRO -> {
                for (int i = 0; i < rivals; i++) {
                    if (sc[last - i] != CustomGame.proCar(i)) {
                        return "Pro has car " + sc[last - i] + " " + (i + 1) + " places from the front, not "
                                + CustomGame.proCar(i);
                    }
                }
            }
            case CustomGame.LINEUP_CUSTOM -> {
                int[] count = new int[GameFacts.numberOfCars];
                for (int slot = 1; slot <= last; slot++) {
                    count[sc[slot]]++;
                }
                for (int car = 0; car < GameFacts.numberOfCars; car++) {
                    if (count[car] != CustomGame.picks(car)) {
                        return "Custom brought " + count[car] + " of car " + car + " where " + CustomGame.picks(car)
                                + " were picked";
                    }
                }
            }
            default -> {
                if (CustomGame.classicPicks() || stage <= 0) {
                    return null; // the classic game's own choice, which the recorded races check
                }
                int boss = CustomGame.defaultBoss(stage);
                List<Integer> span = CustomGame.defaultSpan(stage, unlocked, sc[0]);
                for (int slot = 1; slot <= last; slot++) {
                    if (sc[slot] != boss && !span.contains(sc[slot]) && !span.isEmpty()) {
                        return "Default brought car " + sc[slot] + ", outside stage " + stage + "'s cars " + span;
                    }
                }
                if (sc[0] != boss && sc[last] != boss) {
                    return "Default has car " + sc[last] + " at the front, not the stage's boss " + boss;
                }
            }
        }
        return null;
    }

    /**
     * What is wrong with the grid, or null: the player ({@code z[0]}) must
     * stand behind every computer car, and the race must start in groups
     * exactly when it has more than {@link StaggeredStart#STAGGER_ABOVE}
     * computer cars, one group per {@link StaggeredStart#GROUP}.
     */
    public static String grid(float[] z, int cars, int startGroups) {
        for (int car = 1; car < cars; car++) {
            if (z[car] <= z[0]) {
                return "car " + car + " stands level with or behind the player";
            }
        }
        int rivals = cars - 1;
        int wanted = rivals > StaggeredStart.STAGGER_ABOVE ? (rivals + StaggeredStart.GROUP - 1) / StaggeredStart.GROUP
                : 0;
        if (startGroups != wanted) {
            return rivals + " computer cars start in " + startGroups + " groups, not " + wanted;
        }
        return null;
    }

    /**
     * Watches a staggered start from the test's samples: the tick each group,
     * and the player, was first seen moving. A sample's tick is read after
     * the cars' speeds, so a car is never seen moving on a tick before the
     * one it moved on.
     */
    public static final class Launches {
        private final int cars;
        private final int[] firstSeen;
        private int playerSeen = -1;
        private String early;

        public Launches(int cars, int groups) {
            this.cars = cars;
            this.firstSeen = new int[groups];
            Arrays.fill(firstSeen, -1);
        }

        /** Car {@code car} is moving (or not) in a sample taken {@code tick} ticks after Go!. */
        public void see(int car, boolean moving, int tick) {
            if (!moving) {
                return;
            }
            int group = StaggeredStart.launchGroup(car, cars);
            int start = StaggeredStart.launchTick(group);
            if (tick < start && early == null) {
                early = (car == 0 ? "the player" : "car " + car + " of group " + (group + 1)) + " moved by tick " + tick
                        + ", before its start at tick " + start;
            }
            if (car == 0) {
                if (playerSeen < 0) {
                    playerSeen = tick;
                }
            } else if (firstSeen[group] < 0) {
                firstSeen[group] = tick;
            }
        }

        /** The ticks each group was first seen moving, for the log. */
        public String describe() {
            return Arrays.toString(firstSeen) + ", the player " + playerSeen;
        }

        /** What is wrong with the start seen, or null; call once every group has gone. */
        public String problem() {
            if (early != null) {
                return early;
            }
            for (int g = 0; g < firstSeen.length; g++) {
                if (firstSeen[g] < 0) {
                    return "group " + (g + 1) + " was never seen moving";
                }
                if (g > 0 && firstSeen[g] < firstSeen[g - 1]) {
                    return "group " + (g + 1) + " set off before group " + g;
                }
                if (g > 0 && firstSeen[g] - firstSeen[g - 1] > StaggeredStart.MOST_TICKS_APART) {
                    return "group " + (g + 1) + " set off " + (firstSeen[g] - firstSeen[g - 1])
                            + " ticks after group " + g + ", more than 2 seconds";
                }
            }
            if (playerSeen < 0) {
                return "the player was never seen moving";
            }
            return null;
        }
    }
}
