package nfm.lit;

import fallk.logmaster.HLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's races, kept as replays in the {@code replays} folder next to
 * the game. Each is a {@link RaceLog} named for when it was played, its
 * stage and, when the player finished it, in how many ticks:
 * {@code race-20261116-221500-123-stage08-t01234.nfmr}. The name alone says
 * which to keep, so tidying never opens a file: the
 * {@link #KEEP_LATEST} newest, and on each stage the fastest finish.
 */
final class Replays {

    /** How many of the newest races are kept, whatever they were. */
    static final int KEEP_LATEST = 50;

    private static final Pattern NAME = Pattern.compile("race-(\\d{8}-\\d{6}-\\d{3})-stage(\\d+)(?:-t(\\d+))?\\.nfmr");

    private Replays() {
    }

    /** The folder the replays are kept in: {@code replays} in the game's folder, or {@code -Dnfm.replays}. */
    static File folder() {
        return new File(System.getProperty("nfm.replays", "replays"));
    }

    /** The file name of a race played at {@code time} on {@code stage}, finished in {@code finishTick} (or -1). */
    static String fileName(String time, int stage, int finishTick) {
        return String.format(Locale.ROOT, "race-%s-stage%02d%s.nfmr", time, stage,
                finishTick >= 0 ? String.format(Locale.ROOT, "-t%05d", finishTick) : "");
    }

    /** A kept race, as its name describes it. */
    record Entry(File file, String time, int stage, int finishTick) {
        boolean finished() {
            return finishTick >= 0;
        }
    }

    /** The replays in {@code folder}, newest first. */
    static List<Entry> list(File folder) {
        List<Entry> out = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            Matcher m = NAME.matcher(f.getName());
            if (f.isFile() && m.matches()) {
                out.add(new Entry(f, m.group(1), Integer.parseInt(m.group(2)),
                        m.group(3) != null ? Integer.parseInt(m.group(3)) : -1));
            }
        }
        out.sort((a, b) -> b.time().compareTo(a.time()));
        return out;
    }

    /** The fastest finish on each stage, newest first among equals. */
    static Map<Integer, Entry> bestPerStage(List<Entry> entries) {
        Map<Integer, Entry> best = new HashMap<>();
        for (Entry e : entries) {
            if (e.finished()) {
                Entry b = best.get(e.stage());
                if (b == null || e.finishTick() < b.finishTick()) {
                    best.put(e.stage(), e);
                }
            }
        }
        return best;
    }

    /** The fastest finish kept for {@code stage}, or null. */
    static Entry best(int stage) {
        return bestPerStage(list(folder())).get(stage);
    }

    /** Deletes the replays that are neither among the newest nor a stage's fastest finish. */
    static void tidy(File folder) {
        List<Entry> entries = list(folder);
        Map<Integer, Entry> best = bestPerStage(entries);
        for (int i = KEEP_LATEST; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (best.get(e.stage()) != e && !e.file().delete()) {
                HLogger.warn("Could not delete the old replay " + e.file());
            }
        }
    }

    static String describe(Entry e) {
        return e.file().getName() + (e.finished() ? " (finished in " + seconds(e.finishTick()) + ")" : "");
    }

    /** A race time of {@code ticks} race ticks as m:ss.cc; a tick is 53 ms, as in the original. */
    static String seconds(int ticks) {
        long ms = ticks * 53L;
        return String.format(Locale.ROOT, "%d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100);
    }
}
