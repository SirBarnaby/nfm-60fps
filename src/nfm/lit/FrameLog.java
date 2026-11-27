package nfm.lit;

import fallk.logmaster.HLogger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Every frame the game puts on screen, in named parts (the main menu, the
 * race, the pause menu...): frames a second, frame times, the late and the
 * slowest frames, the graphics card's time and how frames were drawn. When a
 * part of a few seconds or more ends, one line in the game's log sums it up,
 * so a session on the player's own screen says how it went. Test runs also
 * keep every part, for the live test's report ({@link #write}). The game
 * thread names the parts ({@link #follow}); any thread that shows a frame
 * notes it ({@link #shown}).
 */
final class FrameLog {

    /** How a frame was drawn: from a scene kept for the tick, recorded for the card, the picture, software. */
    static final String KEPT = "kept scene";
    static final String CARD = "recorded scene";
    static final String PICTURE = "picture";
    static final String SOFTWARE = "software";

    /** The most frame times a part keeps: its last 2 minutes at 1,000 frames a second. */
    private static final int KEPT_GAPS = 1 << 17;
    /** A part shorter than this, or with fewer frames, is not summed up in the log. */
    private static final double LOGGED_SECONDS = 2D;
    private static final long LOGGED_FRAMES = 20L;
    /** Handing a frame to the card for this long or longer is the card holding it up. */
    private static final long CARD_HELD_NANOS = 100_000_000L;
    /** The most holds a part keeps the details of. */
    private static final int KEPT_HOLDS = 20;
    /** {@link #shown}'s motion for a frame that shows no race moving: a menu, or a race's first frame. */
    static final long NO_MOTION = Long.MIN_VALUE;
    /**
     * Even progress around a frame is taken from the frames shown this long
     * before and after it: the motion's own pace, which a moment's stutter
     * does not move.
     */
    private static final long MOTION_WINDOW_NANOS = 500_000_000L;
    /**
     * A frame whose motion is this far off even progress shows a jump or a
     * stop: a quarter of a 60 Hz frame of the race's movement.
     */
    static final long MOTION_STRAY_NANOS = 4_000_000L;
    /** Frames this close to a part's last motion frame are not judged (see {@link Motion}). */
    private static final long MOTION_END_NANOS = 100_000_000L;
    /*
     * Sums parts up off the game thread, one after another so the log keeps
     * their order: a long part's sums (its frame times and motion sorted) take
     * a while, which the game thread must not spend as one part becomes the
     * next.
     */
    private static final ExecutorService SUMMING = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nfm-framelog");
        t.setDaemon(true);
        return t;
    });
    /** -Dnfm.framelog.csv=true: every race part's frames, one line each, next to frames.json (for a closer look). */
    private static final boolean CSV = Boolean.getBoolean("nfm.framelog.csv");
    /*
     * With CSV, every tick of a part too, in ticks-NN-<part>.csv: when it was
     * due and began, how long the race's own step and the whole tick took,
     * then (for a race the presenter draws) the run-ahead tick, when the
     * frames' poses went to the presenter, recording the kept scene, making it
     * ready for the card and when the hand-over was done. A race that stands
     * still while frames come is the game thread late with these.
     */
    private static final int TICK_FIELDS = 9;
    private static final int KEPT_TICKS = 1 << 14;
    private static final int T_DUE = 0;
    private static final int T_BEGAN = 1;
    private static final int T_RACE = 2;
    private static final int T_ENDED = 3;
    private static final int T_AHEAD = 4;
    private static final int T_HANDED = 5;
    private static final int T_KEPT = 6;
    private static final int T_CARD = 7;
    private static final int T_DONE = 8;

    private static volatile boolean on;
    private static boolean keepParts;
    private static final Object LOCK = new Object();
    private static final List<Part> PARTS = new ArrayList<>();
    private static volatile Part current;
    private static long lastShown;
    /** The last frame shown was a test run's screenshot. */
    private static boolean afterScreenshot;
    /** A test run's name for what it does now (switching fullscreen, say), over the game's own; null for none. */
    private static volatile String label;

    private FrameLog() {
    }

    /** One named part of the session. */
    private static final class Part {
        final String name;
        final long startedAt;
        long endedAt;
        /* the frame times kept, and when each ended: the latest KEPT_GAPS of them, as a ring once full */
        long[] gaps = new long[1024];
        long[] ends = new long[1024];
        int kept;
        long frames;
        long sum;
        long gpuSum;
        int gpuCount;
        /** Frames of a test run's screenshots, and the ones after them, left out of the times. */
        int screenshotFrames;
        /** Frames the card held up (see {@link #cardTook}), the longest it held one, and the first few. */
        int cardHeld;
        long cardHeldLongest;
        final List<Hold> holds = new ArrayList<>();
        final Map<String, Integer> paths = new LinkedHashMap<>();
        final Map<String, String> notes = new LinkedHashMap<>();
        /*
         * Frames that showed the race moving: when each went on screen, the
         * moment of the race it showed and when that moment was chosen, the
         * latest KEPT_GAPS of them, as a ring once full
         */
        long[] motionShown = new long[1024];
        long[] motionAt = new long[1024];
        long[] motionChosen = new long[1024];
        int motionKept;
        long motionFrames;
        /* with CSV: each tick's times on the game thread, TICK_FIELDS a tick (see {@link #tick}) */
        long[] ticks = new long[0];
        int tickCount;

        Part(String name, long startedAt) {
            this.name = name;
            this.startedAt = startedAt;
        }

        /**
         * Where a new tick's row starts in {@link #ticks}, all zeros (rows are
         * only ever added, and the array grows with zeros); -1 once
         * {@link #KEPT_TICKS} are kept.
         */
        int newTickRow() {
            if (tickCount >= KEPT_TICKS) {
                return -1;
            }
            if ((tickCount + 1) * TICK_FIELDS > ticks.length) {
                ticks = Arrays.copyOf(ticks, Math.max(64, tickCount * 2) * TICK_FIELDS);
            }
            return tickCount++ * TICK_FIELDS;
        }

        void add(long gap, long end) {
            if (kept == gaps.length && kept < KEPT_GAPS) {
                gaps = Arrays.copyOf(gaps, kept * 2);
                ends = Arrays.copyOf(ends, kept * 2);
            }
            int i = kept < gaps.length ? kept++ : (int) (frames % gaps.length);
            gaps[i] = gap;
            ends[i] = end;
            frames++;
            sum += gap;
        }

        void addMotion(long shown, long motion, long chosen) {
            if (motionKept == motionShown.length && motionKept < KEPT_GAPS) {
                motionShown = Arrays.copyOf(motionShown, motionKept * 2);
                motionAt = Arrays.copyOf(motionAt, motionKept * 2);
                motionChosen = Arrays.copyOf(motionChosen, motionKept * 2);
            }
            int i = motionKept < motionShown.length ? motionKept++ : (int) (motionFrames % motionShown.length);
            motionShown[i] = shown;
            motionAt[i] = motion;
            motionChosen[i] = chosen;
            motionFrames++;
        }

        /** Index {@code k} (0 = the oldest kept) of the motion ring. */
        int motionIndex(int k) {
            return motionFrames <= motionShown.length ? k : (int) ((motionFrames + k) % motionShown.length);
        }
    }

    /** Starts noting frames; {@code keep} keeps every part for {@link #write} (test runs). */
    static void start(boolean keep) {
        synchronized (LOCK) {
            on = true;
            keepParts = keep;
            PARTS.clear();
            current = null;
            lastShown = 0L;
        }
    }

    static boolean on() {
        return on;
    }

    /** A test run names what it does now, over the game's own part; null hands back to the game. */
    static void label(String name) {
        label = name;
    }

    /**
     * Game thread, every frame: the game is in the part named {@code name}
     * (or the test run's label). A new name ends the part before it.
     *
     * @return true if a new part started
     */
    static boolean follow(String name) {
        if (!on) {
            return false;
        }
        String now = label != null ? label : name;
        Part p = current;
        if (p != null && p.name.equals(now)) {
            return false;
        }
        part(now);
        return true;
    }

    /** The game is closing: the part being noted is summed up in the log, and no more frames are noted. */
    static void end() {
        if (on) {
            part(null, true);
            on = false;
        }
    }

    private static void part(String name) {
        part(name, false);
    }

    /**
     * Ends the part being noted, summing it up in the log ({@code waited}:
     * before this returns, otherwise on {@link #SUMMING}), and starts one
     * named {@code name} (none for null).
     */
    private static void part(String name, boolean waited) {
        Part ended;
        synchronized (LOCK) {
            long now = System.nanoTime();
            ended = current;
            if (ended != null) {
                ended.endedAt = now;
                if (keepParts) {
                    PARTS.add(ended);
                }
            }
            current = name != null ? new Part(name, now) : null;
            lastShown = 0L; // the gap across the change is the change's, not a frame's
            afterScreenshot = false;
        }
        if (CSV && name != null) {
            // the CSVs count from here: the wall clock lines them up with Java's own logs (-Xlog with time)
            System.out.println("[framelog] " + name + " began at " + java.time.OffsetDateTime.now());
        }
        // summed up outside the lock, so frames go on being shown meanwhile: nothing notes into it any more
        if (ended == null) {
            return;
        }
        Runnable sum = () -> {
            String summary = summary(ended);
            if (summary != null) {
                HLogger.info(summary);
            }
        };
        if (!waited) {
            SUMMING.execute(sum);
            return;
        }
        // after any still being summed, so the log keeps its order, and before the game closes
        try {
            SUMMING.submit(sum).get(5L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            System.out.println("[framelog] the last part was not summed up: " + ex);
        }
    }

    /** Notes something about the part being noted: the picture's size, say. */
    static void note(String key, String value) {
        if (!on) {
            return;
        }
        synchronized (LOCK) {
            if (current != null) {
                current.notes.put(key, value);
            }
        }
    }

    /**
     * A frame went on screen at {@code nanos}, drawn {@code path}; the card
     * took {@code gpuNanos} (0 if unknown). A test run's {@code screenshot}
     * frame is drawn the long way round (in software as well, then read back
     * from the card), so its time, and the next frame's while the drawing
     * gets going again, are counted apart rather than as the game's.
     */
    static void shown(long nanos, long gpuNanos, String path, boolean screenshot) {
        shown(nanos, NO_MOTION, 0L, gpuNanos, path, screenshot);
    }

    /**
     * As {@link #shown(long, long, String, boolean)}, for a frame of a race:
     * it shows the race as it was at {@code motionNanos} (on the
     * System.nanoTime clock the race's ticks are due by: a tick's moment,
     * and as far between it and the next as the frame is placed; NO_MOTION
     * for none), a moment the drawing chose at {@code chosenNanos}. Frames
     * that show the race moving evenly show moments as far apart as the
     * frames themselves are (see {@link Motion}).
     */
    static void shown(long nanos, long motionNanos, long chosenNanos, long gpuNanos, String path, boolean screenshot) {
        if (!on) {
            return;
        }
        synchronized (LOCK) {
            Part p = current;
            if (p == null) {
                return;
            }
            if (lastShown != 0L) {
                if (screenshot || afterScreenshot) {
                    p.screenshotFrames++;
                } else {
                    p.add(nanos - lastShown, nanos);
                }
            }
            if (motionNanos != NO_MOTION && !screenshot && !afterScreenshot) {
                p.addMotion(nanos, motionNanos, chosenNanos);
            }
            afterScreenshot = screenshot;
            lastShown = nanos;
            if (gpuNanos > 0L && gpuNanos < 1_000_000_000L) {
                p.gpuSum += gpuNanos;
                p.gpuCount++;
            }
            p.paths.merge(path, 1, Integer::sum);
        }
    }

    /**
     * True if ticks are kept: for the log's line of a race part (how late
     * the game thread handed the presenter its frames, and what took the
     * time), and with CSV for the file. A few clock readings a tick.
     */
    static boolean tracingTicks() {
        return on;
    }

    /**
     * Game thread, with {@link #tracingTicks}: a tick due at {@code due}
     * began at {@code began}, its race step took {@code raceNanos} (0 for
     * none) and it ended at {@code ended}.
     */
    static void tick(long due, long began, long raceNanos, long ended) {
        synchronized (LOCK) {
            Part p = current;
            int at = p == null ? -1 : p.newTickRow();
            if (at < 0) {
                return;
            }
            p.ticks[at + T_DUE] = due;
            p.ticks[at + T_BEGAN] = began;
            p.ticks[at + T_RACE] = raceNanos;
            p.ticks[at + T_ENDED] = ended;
        }
    }

    /**
     * Game thread, with {@link #tracingTicks}: the presenter was handed the
     * frames to come, after the last {@link #tick} (or, with no tick since,
     * after the keys changed): the run-ahead tick took {@code aheadNanos},
     * the poses went over at {@code handed}, recording the kept scene took
     * {@code keptNanos}, making it ready for the card {@code cardNanos}, and
     * it was all done at {@code done}.
     */
    static void handedOver(long aheadNanos, long handed, long keptNanos, long cardNanos, long done) {
        synchronized (LOCK) {
            Part p = current;
            if (p == null) {
                return;
            }
            int last = (p.tickCount - 1) * TICK_FIELDS;
            if (p.tickCount == 0 || p.ticks[last + T_DONE] != 0L) {
                last = p.newTickRow(); // a run-ahead for changed keys, no tick of its own
                if (last < 0) {
                    return;
                }
            }
            p.ticks[last + T_AHEAD] = aheadNanos;
            p.ticks[last + T_HANDED] = handed;
            p.ticks[last + T_KEPT] = keptNanos;
            p.ticks[last + T_CARD] = cardNanos;
            p.ticks[last + T_DONE] = done;
        }
    }

    /** A frame the card held up: for how long, when (since the part started) and what changed on the card before. */
    private record Hold(long nanos, long at, String after) {
    }

    /**
     * Handing the frame just shown to the graphics card took {@code nanos}:
     * from a tenth of a second up, the card (or its driver, or Windows) held
     * the frame up rather than the game's own drawing. The part counts it
     * apart and the log names it, with what changed on the card just before
     * ({@code after}, empty for nothing), so a stall in the player's session
     * can be told for what it is.
     */
    static void cardTook(long nanos, String after) {
        if (nanos < CARD_HELD_NANOS) {
            return;
        }
        String part = null;
        synchronized (LOCK) {
            Part p = on ? current : null; // logged even before the frame log starts, as the game loads
            if (p != null) {
                part = p.name;
                p.cardHeld++;
                p.cardHeldLongest = Math.max(p.cardHeldLongest, nanos);
                if (p.holds.size() < KEPT_HOLDS) {
                    p.holds.add(new Hold(nanos, System.nanoTime() - p.startedAt, after));
                }
            }
        }
        HLogger.info(String.format(Locale.ROOT, "The graphics card held a frame up %d ms%s%s", nanos / 1_000_000L,
                part == null ? "" : " in " + part, after.isEmpty() ? "" : " (after " + after + ")"));
    }

    /** Writes the parts kept as JSON to {@code file}, the one being noted last. */
    static void write(File file) {
        String json;
        synchronized (LOCK) {
            List<Part> parts = new ArrayList<>(PARTS);
            if (current != null) {
                current.endedAt = System.nanoTime();
                parts.add(current);
            }
            StringBuilder out = new StringBuilder("{\n  \"parts\": [");
            for (int i = 0; i < parts.size(); i++) {
                Part p = parts.get(i);
                Motion motion = new Motion(p);
                out.append(i == 0 ? "\n" : ",\n").append(describe(new Times(p), motion));
                if (CSV && motion.frames > 0) {
                    File dir = file.getAbsoluteFile().getParentFile();
                    String slug = String.format(Locale.ROOT, "%02d-%s.csv", i, p.name.replaceAll("[^A-Za-z0-9]+", "-"));
                    writeCsv(new File(dir, "frames-" + slug), p, motion);
                    if (p.tickCount > 0) {
                        writeTicks(new File(dir, "ticks-" + slug), p);
                    }
                }
            }
            json = out.append("\n  ]\n}\n").toString();
        }
        writeText(file, json);
    }

    private static void writeText(File file, String text) {
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println("[framelog] cannot write " + file + ": " + ex);
        }
    }

    /** A part's race frames, one line each: when shown, the race's moment, how far off even progress. */
    private static void writeCsv(File file, Part p, Motion m) {
        StringBuilder s = new StringBuilder("shown_ms,motion_ms,chosen_ms,lag_ms,off_ms\n");
        for (int k = 0; k < m.frames; k++) {
            int i = p.motionIndex(k);
            s.append(String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f,%.3f%n", (p.motionShown[i] - p.startedAt) / 1e6D,
                    (p.motionAt[i] - p.startedAt) / 1e6D, (p.motionChosen[i] - p.startedAt) / 1e6D,
                    (p.motionShown[i] - p.motionAt[i]) / 1e6D, m.off[k] / 1e6D));
        }
        writeText(file, s.toString());
    }

    /**
     * A part's ticks, one line each (see {@link #tick}): when due and how late
     * it began, in ms from the part's start; how long the race's step and the
     * whole tick took; the run-ahead tick; how long after it was due the
     * presenter had the poses; recording the kept scene; making it ready for
     * the card; how long after it was due the hand-over was done. Blank where
     * the step did not happen.
     */
    private static void writeTicks(File file, Part p) {
        StringBuilder s = new StringBuilder("due_ms,late_ms,race_ms,tick_ms,ahead_ms,handed_ms,kept_ms,card_ms,done_ms\n");
        for (int k = 0; k < p.tickCount; k++) {
            int at = k * TICK_FIELDS;
            long[] t = p.ticks;
            long due = t[at + T_DUE];
            boolean ticked = due != 0L;
            boolean handed = t[at + T_DONE] != 0L;
            s.append(ticked ? ms((due - p.startedAt) / 1e6D) : "").append(',')
                    .append(ticked ? ms((t[at + T_BEGAN] - due) / 1e6D) : "").append(',')
                    .append(ticked && t[at + T_RACE] != 0L ? ms(t[at + T_RACE] / 1e6D) : "").append(',')
                    .append(ticked ? ms((t[at + T_ENDED] - t[at + T_BEGAN]) / 1e6D) : "").append(',')
                    .append(handed && t[at + T_AHEAD] != 0L ? ms(t[at + T_AHEAD] / 1e6D) : "").append(',')
                    .append(handed && ticked ? ms((t[at + T_HANDED] - due) / 1e6D) : "").append(',')
                    .append(handed ? ms(t[at + T_KEPT] / 1e6D) : "").append(',')
                    .append(handed && t[at + T_CARD] != 0L ? ms(t[at + T_CARD] / 1e6D) : "").append(',')
                    .append(handed && ticked ? ms((t[at + T_DONE] - due) / 1e6D) : "").append('\n');
        }
        writeText(file, s.toString());
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /**
     * How evenly a part's frames showed the race moving: what the player
     * sees, which frame times alone cannot tell (frames can come evenly while
     * what they show jumps or stops).
     * <p>
     * Each frame shows the race as it was at some moment and goes on screen
     * a little later: its lag. Moving evenly, the lag stays the same from one
     * frame to the next, the race moving on by as much as the time between
     * the two. A frame whose lag is off the lag around it (the median over
     * the frames shown {@link #MOTION_WINDOW_NANOS} before and after it)
     * shows the race that much ahead (a jump) or behind (a stop).
     * <p>
     * What it comes to is judged on every frame from the part's first on,
     * but not on those within {@link #MOTION_END_NANOS} of its last: before
     * the race pauses or cuts to the replay, the last tick is held still for
     * the cut, which is not a stutter. Its start is judged, a resume's frame
     * far off the lag after it too: a race standing still or jumping as it
     * starts or resumes is the first thing the player sees of it. All of them
     * go in the CSV.
     */
    private static final class Motion {
        final int frames;
        /** How far each frame was off even progress, in time order; positive is behind. */
        final long[] off;
        final double lagMs;
        /** The frames judged: all but those near the part's end. */
        final int judged;
        final double p99Ms;
        final double maxMs;
        final double maxAt;
        final int strays;
        /** The longest time frames in a row were off even progress: the race stood still or jumped about. */
        final double stretchMs;
        final double stretchAt;

        Motion(Part p) {
            int n = p.motionKept;
            frames = n;
            off = new long[n];
            long[] shown = new long[n];
            long[] lag = new long[n];
            for (int k = 0; k < n; k++) {
                int i = p.motionIndex(k);
                shown[k] = p.motionShown[i];
                lag[k] = p.motionShown[i] - p.motionAt[i];
            }
            // the window's lags, kept sorted as it slides
            long[] window = new long[Math.max(1, n)];
            int size = 0;
            int lo = 0;
            int hi = 0;
            for (int k = 0; k < n; k++) {
                while (hi < n && shown[hi] - shown[k] <= MOTION_WINDOW_NANOS) {
                    int at = Arrays.binarySearch(window, 0, size, lag[hi]);
                    at = at < 0 ? -at - 1 : at;
                    System.arraycopy(window, at, window, at + 1, size - at);
                    window[at] = lag[hi++];
                    size++;
                }
                while (shown[k] - shown[lo] > MOTION_WINDOW_NANOS) {
                    int at = Arrays.binarySearch(window, 0, size, lag[lo++]);
                    System.arraycopy(window, at + 1, window, at, size - at - 1);
                    size--;
                }
                off[k] = lag[k] - window[size / 2];
            }
            long[] sorted = Arrays.copyOf(lag, n);
            Arrays.sort(sorted);
            lagMs = n == 0 ? 0D : sorted[n / 2] / 1e6D;
            long[] sizes = new long[n];
            int count = 0;
            int worst = -1;
            int over = 0;
            long stretch = 0L;
            long stretchFrom = 0L;
            long from = -1L; // when the frames off in a row began (the frame before the first one off), -1 for none
            for (int k = 0; k < n; k++) {
                long away = Math.abs(off[k]);
                boolean inside = shown[n - 1] - shown[k] >= MOTION_END_NANOS;
                if (!inside || away <= MOTION_STRAY_NANOS) {
                    from = -1L;
                    if (!inside) {
                        continue;
                    }
                } else {
                    over++;
                    if (from < 0L) {
                        from = shown[k > 0 ? k - 1 : k];
                    }
                    if (shown[k] - from > stretch) {
                        stretch = shown[k] - from;
                        stretchFrom = from;
                    }
                }
                sizes[count++] = away;
                if (worst < 0 || away > Math.abs(off[worst])) {
                    worst = k;
                }
            }
            judged = count;
            strays = over;
            maxMs = worst < 0 ? 0D : Math.abs(off[worst]) / 1e6D;
            maxAt = worst < 0 ? 0D : (shown[worst] - p.startedAt) / 1e9D;
            stretchMs = stretch / 1e6D;
            stretchAt = stretch == 0L ? 0D : (stretchFrom - p.startedAt) / 1e9D;
            Arrays.sort(sizes, 0, count);
            p99Ms = count == 0 ? 0D
                    : sizes[Math.min(count - 1, Math.max(0, (int) Math.ceil(0.99D * count) - 1))] / 1e6D;
        }
    }

    /** A part's frame times, sorted, and what they come to. */
    private static final class Times {
        final Part part;
        final long[] sorted;
        final double seconds;
        final double fps;
        final long median;
        final int late;

        Times(Part p) {
            part = p;
            sorted = Arrays.copyOf(p.gaps, p.kept);
            Arrays.sort(sorted);
            seconds = (p.endedAt - p.startedAt) / 1e9D;
            fps = p.sum == 0L ? 0D : p.frames / (p.sum / 1e9D);
            median = sorted.length == 0 ? 0L : sorted[sorted.length / 2];
            late = late(sorted);
        }

        double ms(double share) {
            return sorted.length == 0 ? 0D
                    : sorted[Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(share * sorted.length) - 1))] / 1e6D;
        }

        double longest() {
            return sorted.length == 0 ? 0D : sorted[sorted.length - 1] / 1e6D;
        }

        double gpuMs() {
            return part.gpuCount == 0 ? 0D : part.gpuSum / 1e6D / part.gpuCount;
        }
    }

    /**
     * How many of these frame times, sorted, came late: over half as long
     * again as the median and 2 ms more. Every report of frame times (this
     * log and the profile's [draw] and [pace] lines) counts them this one way.
     */
    static int late(long[] sorted) {
        if (sorted.length == 0) {
            return 0;
        }
        long limit = sorted[sorted.length / 2] * 3 / 2 + 2_000_000L;
        int late = 0;
        for (int i = sorted.length - 1; i >= 0 && sorted[i] > limit; i--) {
            late++;
        }
        return late;
    }

    /** The log's line for a part, or null for one too short to tell anything. */
    private static String summary(Part p) {
        if ((p.endedAt - p.startedAt) / 1e9D < LOGGED_SECONDS || p.frames < LOGGED_FRAMES) {
            return null;
        }
        Times t = new Times(p);
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT,
                "Frames in %s: %.0f a second over %.1f s, half within %.1f ms, 99%% within %.1f ms, longest %.0f ms",
                p.name, t.fps, t.seconds, t.median / 1e6D, t.ms(0.99D), t.longest()));
        if (t.late > 0) {
            s.append(String.format(Locale.ROOT, ", %d late", t.late));
        }
        if (p.gpuCount > 0) {
            s.append(String.format(Locale.ROOT, ", card %.1f ms", t.gpuMs()));
        }
        if (p.cardHeld > 0) {
            s.append(String.format(Locale.ROOT, ", the card held %d up (longest %.0f ms)", p.cardHeld,
                    p.cardHeldLongest / 1e6D));
        }
        if (p.screenshotFrames > 0) {
            s.append(", ").append(p.screenshotFrames).append(" screenshot frames left out");
        }
        Motion m = new Motion(p);
        if (m.frames > 0) {
            // what the player sees move: even frames can still show the race jump or stop
            s.append(String.format(Locale.ROOT, ", motion: 99%% within %.1f ms of even, %d frames off by over %d ms"
                    + " (worst %.1f ms, longest %.0f ms in a row)", m.p99Ms, m.strays, MOTION_STRAY_NANOS / 1_000_000L,
                    m.maxMs, m.stretchMs));
        }
        int total = p.paths.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String, Integer> e : p.paths.entrySet()) {
            double share = 100D * e.getValue() / total;
            s.append(", ").append(e.getKey()).append(share < 1D ? " under 1%" : String.format(Locale.ROOT, " %.0f%%", share));
        }
        if (m.frames > 0) {
            s.append(ticks(p));
        }
        return s.toString();
    }

    /** A hand-over this long after its tick was due leaves the presenter a moment without the race's next step. */
    private static final long TICK_LATE_NANOS = 15_000_000L;

    /**
     * The game thread's side of a race part, for the log: how long after a
     * tick was due the presenter had its frames (the whole of the tick, the
     * tick played ahead, the extras, recording the kept scene and making it
     * ready for the card), and the longest of each. Uneven motion with
     * these all short is the presenter's or the card's; with these long it
     * is the game thread's.
     */
    private static String ticks(Part p) {
        int n = 0;
        int late = 0;
        long worst = 0L;
        long sum = 0L;
        long tick = 0L;
        long ahead = 0L;
        long kept = 0L;
        long card = 0L;
        for (int k = 0; k < p.tickCount; k++) {
            int at = k * TICK_FIELDS;
            long[] t = p.ticks;
            if (t[at + T_DUE] == 0L || t[at + T_DONE] == 0L) {
                continue;
            }
            long done = t[at + T_DONE] - t[at + T_DUE];
            n++;
            sum += done;
            worst = Math.max(worst, done);
            if (done > TICK_LATE_NANOS) {
                late++;
            }
            tick = Math.max(tick, t[at + T_ENDED] - t[at + T_BEGAN]);
            ahead = Math.max(ahead, t[at + T_AHEAD]);
            kept = Math.max(kept, t[at + T_KEPT]);
            card = Math.max(card, t[at + T_CARD]);
        }
        if (n == 0) {
            return "";
        }
        return String.format(Locale.ROOT, ", ticks: handed over %.1f ms after due (worst %.0f ms, %d of %d over %d ms;"
                + " longest tick %.1f ms, tick ahead %.1f ms, kept scene %.1f ms, for the card %.1f ms)",
                sum / 1e6D / n, worst / 1e6D, late, n, TICK_LATE_NANOS / 1_000_000L, tick / 1e6D, ahead / 1e6D,
                kept / 1e6D, card / 1e6D);
    }

    private static String describe(Times t, Motion m) {
        Part p = t.part;
        StringBuilder s = new StringBuilder("    {");
        s.append("\"name\": ").append(quote(p.name));
        s.append(String.format(Locale.ROOT, ", \"seconds\": %.2f, \"frames\": %d, \"fps\": %.1f", t.seconds, p.frames,
                t.fps));
        s.append(String.format(Locale.ROOT,
                ", \"ms\": {\"mean\": %.2f, \"median\": %.2f, \"p95\": %.2f, \"p99\": %.2f, \"max\": %.2f}",
                p.frames == 0L ? 0D : p.sum / 1e6D / p.frames, t.median / 1e6D, t.ms(0.95D), t.ms(0.99D),
                t.longest()));
        s.append(", \"late\": ").append(t.late);
        s.append(", \"screenshotFrames\": ").append(p.screenshotFrames);
        s.append(String.format(Locale.ROOT, ", \"cardHeld\": {\"frames\": %d, \"longestMs\": %.1f, \"holds\": [",
                p.cardHeld, p.cardHeldLongest / 1e6D));
        for (int i = 0; i < p.holds.size(); i++) {
            Hold h = p.holds.get(i);
            s.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "{\"ms\": %.1f, \"at\": %.2f, \"after\": ",
                    h.nanos() / 1e6D, h.at() / 1e9D)).append(quote(h.after())).append('}');
        }
        s.append("]}");
        s.append(", \"slowest\": [").append(slowest(p)).append(']');
        s.append(String.format(Locale.ROOT, ", \"gpuMs\": %.2f", t.gpuMs()));
        s.append(String.format(Locale.ROOT, ", \"motion\": {\"frames\": %d, \"judged\": %d, \"lagMs\": %.2f,"
                + " \"p99Ms\": %.2f, \"maxMs\": %.2f, \"maxAt\": %.2f, \"strays\": %d, \"strayMs\": %d,"
                + " \"stretchMs\": %.3f, \"stretchAt\": %.2f}", m.frames, m.judged, m.lagMs, m.p99Ms, m.maxMs,
                m.maxAt, m.strays, MOTION_STRAY_NANOS / 1_000_000L, m.stretchMs, m.stretchAt));
        s.append(", \"drawn\": {");
        int k = 0;
        for (Map.Entry<String, Integer> e : p.paths.entrySet()) {
            s.append(k++ == 0 ? "" : ", ").append(quote(e.getKey())).append(": ").append(e.getValue());
        }
        s.append("}, \"notes\": {");
        k = 0;
        for (Map.Entry<String, String> e : p.notes.entrySet()) {
            s.append(k++ == 0 ? "" : ", ").append(quote(e.getKey())).append(": ").append(quote(e.getValue()));
        }
        return s.append("}}").toString();
    }

    /** The five longest gaps, longest first, each with the second of the part it ended in. */
    private static String slowest(Part p) {
        int[] top = new int[Math.min(5, p.kept)]; // the longest so far, longest first, the earlier of equals first
        int n = 0;
        for (int i = 0; i < p.kept; i++) {
            int at = n;
            while (at > 0 && p.gaps[top[at - 1]] < p.gaps[i]) {
                at--;
            }
            if (at < top.length) {
                System.arraycopy(top, at, top, at + 1, Math.min(n, top.length - 1) - at);
                top[at] = i;
                n = Math.min(n + 1, top.length);
            }
        }
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int i = top[k];
            s.append(k == 0 ? "" : ", ").append(String.format(Locale.ROOT, "{\"ms\": %.1f, \"at\": %.2f}",
                    p.gaps[i] / 1e6D, (p.ends[i] - p.startedAt) / 1e9D));
        }
        return s.toString();
    }

    private static String quote(String text) {
        StringBuilder s = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        s.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        s.append(c);
                    }
                }
            }
        }
        return s.append('"').toString();
    }
}
