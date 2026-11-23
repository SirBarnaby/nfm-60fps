package nfm.lit.features.customgame;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import nfm.lit.Control;
import nfm.lit.GameFacts;

/**
 * What the hidden custom test ({@code -Dnfm.autotest.custom}) holds a custom
 * race to, from what the race shows rather than from the rules that made
 * it: the field the lineup should bring, the player last on the grid, and
 * each group setting off together, in turn, never before its start, at
 * most two seconds after the group before, the player with the last. Each
 * check gives what is wrong, or null.
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
        if (rivals != CustomGame.fieldCars()) {
            return rivals + " computer cars where " + CustomGame.fieldCars() + " were chosen";
        }
        int last = cars - 1;
        switch (CustomGame.fieldLineup()) {
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
                    if (count[car] != CustomGame.fieldPicks(car)) {
                        return "Custom brought " + count[car] + " of car " + car + " where " + CustomGame.fieldPicks(car)
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
     * Watches a staggered start on the game thread, tick by tick, in the real
     * ticks only: the controls each car drives with, read just before the
     * cars drive, where the physics takes them, and which cars are moving
     * after the tick. A car whose group has not gone is still a car on the
     * road, and one that is hit is pushed, so a start is read from the
     * controls, never from movement: every car, the player's included, must
     * drive with the hold ({@link StaggeredStart#holds}) on each tick before
     * its group's start and with controls of its own on that tick, so the
     * cars of a group go together and the player goes with the last group.
     * <p>
     * The run-ahead and the warm-up play race ticks too and put them back. A
     * tick's controls are only taken in when the real tick that read them
     * has run: {@link #afterRealTick} is called after the real tick alone, and
     * keeps what {@link #beforeDrive} saw only if the race's clock has moved
     * on from that tick by one.
     */
    public static final class Launches {
        private final int cars;
        private final int[] released;
        private final int[] groupMoved;
        private final boolean[] own;
        private int seenTick = -1;
        private int firstTick = -1;
        private int lastTick = -1;
        private int playerMoved = -1;
        private String problem;

        public Launches(int cars, int groups) {
            this.cars = cars;
            this.released = new int[cars];
            this.groupMoved = new int[groups];
            this.own = new boolean[cars];
            Arrays.fill(released, -1);
            Arrays.fill(groupMoved, -1);
        }

        /** Game thread, in every race tick from Go! on (real or not), just before the cars drive with {@code u}. */
        public void beforeDrive(int tick, Control[] u) {
            for (int car = 0; car < cars; car++) {
                own[car] = !StaggeredStart.holds(u[car]);
            }
            seenTick = tick;
        }

        /**
         * Game thread, after each real tick: takes in the controls
         * {@link #beforeDrive} saw, if they were this tick's ({@code ticksSinceGo}
         * one past theirs), with which cars are {@code moving} now.
         */
        public synchronized void afterRealTick(int ticksSinceGo, IntPredicate moving) {
            int tick = seenTick;
            seenTick = -1;
            if (tick < 0 || tick != ticksSinceGo - 1) {
                return; // no drive this tick (the countdown), or what was seen was a tick put back
            }
            if (firstTick < 0) {
                firstTick = tick;
            } else if (tick != lastTick + 1 && problem == null) {
                problem = "the watch missed ticks " + (lastTick + 1) + " to " + (tick - 1);
            }
            lastTick = tick;
            for (int car = 0; car < cars; car++) {
                int group = StaggeredStart.launchGroup(car, cars);
                int start = StaggeredStart.launchTick(group);
                if (problem == null && tick < start && own[car]) {
                    problem = who(car, group) + " drove tick " + tick + " with controls of its own, before its group's"
                            + " start at tick " + start;
                } else if (problem == null && tick == start && !own[car]) {
                    problem = who(car, group) + " was still held on tick " + start + ", its group's start";
                }
                if (own[car] && released[car] < 0) {
                    released[car] = tick;
                }
                if (own[car] && moving.test(car)) {
                    if (car == 0) {
                        playerMoved = playerMoved < 0 ? tick : playerMoved;
                    } else if (groupMoved[group] < 0) {
                        groupMoved[group] = tick;
                    }
                }
            }
        }

        private static String who(int car, int group) {
            return car == 0 ? "the player" : "car " + car + " of group " + (group + 1);
        }

        /** Each group's start as seen (the tick its cars first drove with their own controls), and when it first moved. */
        public synchronized String describe() {
            StringBuilder b = new StringBuilder();
            for (int g = 0; g < groupMoved.length; g++) {
                b.append(g == 0 ? "" : ", ").append(groupStart(g)).append(" (moving ").append(groupMoved[g]).append(")");
            }
            return b + "; the player " + released[0] + " (moving " + playerMoved + ")";
        }

        /** The tick group {@code g}'s cars first drove with their own controls, if they all did on the same one; else -1. */
        private int groupStart(int g) {
            int start = -1;
            for (int car = 1; car < cars; car++) {
                if (StaggeredStart.launchGroup(car, cars) == g) {
                    if (released[car] < 0 || start >= 0 && released[car] != start) {
                        return -1;
                    }
                    start = released[car];
                }
            }
            return start;
        }

        /** What is wrong with the start seen, or null; call once every group has gone. */
        public synchronized String problem() {
            if (problem != null) {
                return problem;
            }
            if (firstTick != 0) {
                return firstTick < 0 ? "the watch saw no tick of the race" : "the watch began at tick " + firstTick;
            }
            int last = StaggeredStart.launchTick(groupMoved.length - 1);
            if (lastTick < last) {
                return "the watch ended at tick " + lastTick + ", before the last group's start at tick " + last;
            }
            for (int g = 0; g < groupMoved.length; g++) {
                int start = groupStart(g);
                if (start < 0) {
                    return "the cars of group " + (g + 1) + " did not all set off on one tick";
                }
                if (g > 0 && start <= groupStart(g - 1)) {
                    return "group " + (g + 1) + " set off on tick " + start + ", not after group " + g;
                }
                if (g > 0 && start - groupStart(g - 1) > StaggeredStart.MOST_TICKS_APART) {
                    return "group " + (g + 1) + " set off " + (start - groupStart(g - 1)) + " ticks after group " + g
                            + ", more than 2 seconds";
                }
                if (groupMoved[g] < 0) {
                    return "no car of group " + (g + 1) + " ever moved";
                }
            }
            if (released[0] != groupStart(groupMoved.length - 1)) {
                return "the player set off on tick " + released[0] + ", the last group on tick "
                        + groupStart(groupMoved.length - 1);
            }
            if (playerMoved < 0) {
                return "the player never moved";
            }
            return null;
        }
    }
}
