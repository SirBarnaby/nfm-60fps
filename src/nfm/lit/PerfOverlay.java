package nfm.lit;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The performance overlay: a small panel in a corner of the picture with
 * the readouts the player switches on in Settings, Advanced (or all of them
 * with the console's {@code overlay}): frames a second, frame time and the
 * graphics card's share of it, the frame times 90% and 99% of frames beat,
 * the game's memory, the graphics card's memory, the game's caches on disk,
 * and a graph of the last frames. F3 shows and hides it.
 * <p>
 * Every frame put on screen, by whichever thread shows it, is noted with
 * {@link #frameShown}; the readouts are worked out from those four times a
 * second, and the panel is drawn at the end of each game tick into the layer
 * that goes on top of the picture, so it never changes how a frame is drawn.
 */
final class PerfOverlay {

    /* the readouts, as bits of the saved switches */
    static final int FPS = 1;
    static final int FRAME_TIME = 2;
    static final int P90 = 4;
    static final int P99 = 8;
    static final int MEMORY = 16;
    static final int VIDEO_MEMORY = 32;
    static final int CACHE = 64;
    static final int GRAPH = 128;
    static final int ALL = 255;
    static final int DEFAULT_READOUTS = FPS | FRAME_TIME | P99;

    /* where the panel goes: off, or a corner */
    static final int OFF = 0;
    static final int TOP_LEFT = 1;
    static final int TOP_RIGHT = 2;
    static final int BOTTOM_LEFT = 3;
    static final int BOTTOM_RIGHT = 4;

    private static volatile int corner = OFF;
    /** The corner F3 brings the panel back to. */
    private static volatile int lastCorner = TOP_LEFT;
    private static volatile int readouts = DEFAULT_READOUTS;
    private static volatile boolean changed;

    /* the frames shown: when each ended, and the card's time for it (0 if it cannot say); under LOCK */
    private static final Object LOCK = new Object();
    private static final int KEPT = 8192;
    private static final long[] ENDS = new long[KEPT];
    private static final long[] GPU = new long[KEPT];
    private static long frames;

    /* the graphics card's memory, from its last frame: used, the most it has, textures, render targets */
    private static volatile long videoUsed = -1L;
    private static volatile long videoMost = -1L;
    private static volatile long videoTextures = -1L;
    private static volatile long videoTargets = -1L;
    private static volatile boolean onCard;

    /* the caches on disk, worked out off the game thread */
    private static volatile long cacheCode = -1L;
    private static volatile long cacheSounds = -1L;
    private static volatile long cacheMeasuredAt;
    private static volatile boolean measuringCache;

    /* worked out four times a second, game thread only */
    private static long updatedAt;
    private static final List<String> LINES = new ArrayList<>();
    private static final float[] GRAPH_MS = new float[180];
    private static int graphCount;
    private static long[] scratch = new long[KEPT];
    private static Font font;

    private PerfOverlay() {
    }

    /* ------------------------------------------------------------ settings */

    static int corner() {
        return corner;
    }

    static void setCorner(int where) {
        corner = Math.max(OFF, Math.min(BOTTOM_RIGHT, where));
        if (corner != OFF) {
            lastCorner = corner;
        }
        changed = true;
        updatedAt = 0L;
    }

    /** F3: hides the panel, or shows it where it was. */
    static void toggle() {
        setCorner(corner == OFF ? lastCorner : OFF);
    }

    static boolean shows(int readout) {
        return (readouts & readout) != 0;
    }

    static void setShows(int readout, boolean on) {
        readouts = on ? readouts | readout : readouts & ~readout;
        changed = true;
        updatedAt = 0L;
    }

    static int readouts() {
        return readouts;
    }

    static void setReadouts(int bits) {
        readouts = bits & ALL;
        changed = true;
        updatedAt = 0L;
    }

    /** The saved value: the corner, and the readouts above it. */
    static int saved() {
        return corner | readouts << 4 | lastCorner << 12;
    }

    /** Puts back a saved value; anything else (no value saved) leaves the defaults. */
    static void restore(int saved) {
        if (saved < 0) {
            return;
        }
        corner = Math.min(BOTTOM_RIGHT, saved & 0xF);
        readouts = saved >> 4 & ALL;
        int last = saved >> 12 & 0xF;
        lastCorner = last >= TOP_LEFT && last <= BOTTOM_RIGHT ? last : TOP_LEFT;
        changed = false;
    }

    /** True once after a change, for saving it. */
    static boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    static String describe() {
        if (corner == OFF) {
            return "off";
        }
        String[] corners = {"", "top left", "top right", "bottom left", "bottom right"};
        List<String> on = new ArrayList<>();
        String[] names = {"fps", "frame time", "90%", "99%", "memory", "video memory", "cache", "graph"};
        for (int i = 0; i < names.length; i++) {
            if ((readouts & 1 << i) != 0) {
                on.add(names[i]);
            }
        }
        return corners[corner] + (on.isEmpty() ? ", nothing on it" : ": " + String.join(", ", on));
    }

    /* --------------------------------------------------------- measurements */

    /** A frame went on screen at {@code nanos}; the card took {@code gpuNanos} on it (0 in software or unknown). */
    static void frameShown(long nanos, long gpuNanos) {
        synchronized (LOCK) {
            int at = (int) (frames % KEPT);
            ENDS[at] = nanos;
            GPU[at] = gpuNanos;
            frames++;
        }
    }

    /** Whether the card's memory is wanted, so the frame need not ask for it otherwise. */
    static boolean wantsVideoMemory() {
        return corner != OFF && (readouts & VIDEO_MEMORY) != 0;
    }

    /** The card's memory after a frame, in bytes; negative where it cannot say. */
    static void videoMemory(long used, long most, long textures, long targets) {
        videoUsed = used;
        videoMost = most;
        videoTextures = textures;
        videoTargets = targets;
    }

    /** Whether the graphics card draws (for the video memory readout). */
    static void drawingOnCard(boolean card) {
        onCard = card;
    }

    /* -------------------------------------------------------------- drawing */

    /**
     * Draws the panel, if it is on, onto {@code g}: the HUD {@code layer}
     * over a race, or the picture elsewhere. On the layer its background
     * replaces what is under it, so drawing it again onto a layer that was
     * not cleared looks the same; on the picture it darkens it.
     */
    static void draw(Graphics2D g, boolean layer) {
        if (corner == OFF) {
            return;
        }
        long now = System.nanoTime();
        if (now - updatedAt >= 250_000_000L) {
            update(now);
            updatedAt = now;
        }
        if (font == null) {
            font = new Font(Font.DIALOG, Font.BOLD, 12);
        }
        boolean graph = (readouts & GRAPH) != 0 && graphCount > 1;
        if (LINES.isEmpty() && !graph) {
            return;
        }
        Font oldFont = g.getFont();
        Color oldColor = g.getColor();
        Composite oldComposite = g.getComposite();
        Object textAntialias = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Object antialias = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        FontMetrics fm = g.getFontMetrics();
        int lineH = fm.getHeight();
        int pad = 7;
        int graphW = GRAPH_MS.length;
        int graphH = 44;
        int w = graph ? graphW : 0;
        for (String line : LINES) {
            w = Math.max(w, fm.stringWidth(line));
        }
        w += 2 * pad;
        int h = 2 * pad + LINES.size() * lineH + (graph ? graphH + (LINES.isEmpty() ? 0 : 6) : 0);
        int x = corner == TOP_LEFT || corner == BOTTOM_LEFT ? 8 : GameFacts.screenWidth - 8 - w;
        int y = corner == TOP_LEFT || corner == TOP_RIGHT ? 8 : GameFacts.screenHeight - 8 - h;

        g.setComposite(layer ? AlphaComposite.Src : AlphaComposite.SrcOver);
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRect(x, y, w, h);
        g.setComposite(AlphaComposite.SrcOver);
        int ty = y + pad + fm.getAscent();
        for (int i = 0; i < LINES.size(); i++) {
            String line = LINES.get(i);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(line, x + pad + 1, ty + 1);
            g.setColor(i == 0 && (readouts & FPS) != 0 ? new Color(0xffd84a) : new Color(0xf2f2f2));
            g.drawString(line, x + pad, ty);
            ty += lineH;
        }
        if (graph) {
            drawGraph(g, x + pad, y + h - pad - graphH, w - 2 * pad, graphH);
        }
        g.setFont(oldFont);
        g.setColor(oldColor);
        g.setComposite(oldComposite);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialias);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }

    /** The scales the graph can have, in milliseconds: the frame times of 240, 120, 60, 30, 15 and 7.5 fps. */
    private static final float[] SCALES = {4.17F, 8.33F, 16.7F, 33.3F, 66.7F, 133.3F};

    /**
     * The last frames' times as bars, on the smallest scale that holds all
     * but the slowest few; a frame slower than that reaches the top.
     */
    private static void drawGraph(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRect(x, y, w, h);
        float[] sorted = Arrays.copyOf(GRAPH_MS, graphCount);
        Arrays.sort(sorted);
        float typical = sorted[Math.max(0, (int) (graphCount * 0.98F) - 1)];
        float most = SCALES[SCALES.length - 1];
        for (float scale : SCALES) {
            if (scale >= typical * 1.25F) {
                most = scale;
                break;
            }
        }
        int bars = Math.min(graphCount, w);
        for (int i = 0; i < bars; i++) {
            float ms = GRAPH_MS[graphCount - bars + i];
            int bar = Math.max(1, Math.min(h, Math.round(ms / most * h)));
            g.setColor(ms > 33.4F ? new Color(0xff5a4a) : ms > 16.7F ? new Color(0xffc04a) : new Color(0x7fe07f));
            g.fillRect(x + w - bars + i, y + h - bar, 1, bar);
        }
        g.setColor(new Color(0xd0d0d0));
        g.drawString(String.format(Locale.ROOT, most < 10F ? "%.1f ms" : "%.0f ms", most), x + 3, y + 12);
    }

    /** Works out the readouts from the frames of the last two seconds. */
    private static void update(long now) {
        int n;
        long gpuSum = 0L;
        int gpuCount = 0;
        int inSecond = 0;
        synchronized (LOCK) {
            long first = Math.max(0L, frames - KEPT + 1);
            long from = frames;
            while (from > first && now - ENDS[(int) ((from - 1) % KEPT)] < 2_000_000_000L) {
                from--;
            }
            n = 0;
            for (long f = Math.max(from, first + 1); f < frames; f++) {
                long end = ENDS[(int) (f % KEPT)];
                scratch[n++] = end - ENDS[(int) ((f - 1) % KEPT)];
                if (now - end < 1_000_000_000L) {
                    inSecond++;
                    long gpu = GPU[(int) (f % KEPT)];
                    if (gpu > 0L && gpu < 1_000_000_000L) { // the card's clock can say nonsense as it starts
                        gpuSum += gpu;
                        gpuCount++;
                    }
                }
            }
        }
        graphCount = Math.min(n, GRAPH_MS.length);
        for (int i = 0; i < graphCount; i++) {
            GRAPH_MS[i] = scratch[n - graphCount + i] / 1e6F;
        }
        LINES.clear();
        int on = readouts;
        if ((on & FPS) != 0) {
            LINES.add(inSecond + " fps");
        }
        if ((on & FRAME_TIME) != 0) {
            double mean = 0D;
            int count = Math.min(n, inSecond);
            for (int i = n - count; i < n; i++) {
                mean += scratch[i];
            }
            mean = count == 0 ? 0D : mean / count / 1e6D;
            String gpu = gpuCount == 0 ? "" : String.format(Locale.ROOT, "  (card %.1f ms)", gpuSum / 1e6D / gpuCount);
            LINES.add(String.format(Locale.ROOT, "Frame %.1f ms", mean) + gpu);
        }
        if ((on & (P90 | P99)) != 0) {
            long[] sorted = Arrays.copyOf(scratch, n);
            Arrays.sort(sorted);
            StringBuilder line = new StringBuilder();
            if ((on & P90) != 0) {
                line.append(percentile(sorted, 0.90D, "90%"));
            }
            if ((on & P99) != 0) {
                line.append(line.length() == 0 ? "" : "    ").append(percentile(sorted, 0.99D, "99%"));
            }
            LINES.add(line.toString());
        }
        if ((on & MEMORY) != 0) {
            Runtime runtime = Runtime.getRuntime();
            long heap = runtime.totalMemory() - runtime.freeMemory();
            long process = processMemory();
            LINES.add((process > 0L ? "RAM " + mb(process) + "  (Java " : "RAM: Java ") + mb(heap) + " of "
                    + mb(runtime.maxMemory()) + (process > 0L ? ")" : ""));
        }
        if ((on & VIDEO_MEMORY) != 0) {
            if (!onCard) {
                LINES.add("VRAM: drawing in software");
            } else if (videoUsed < 0L) {
                LINES.add(videoTextures >= 0L ? "VRAM: textures " + mb(videoTextures) + ", targets " + mb(videoTargets)
                        : "VRAM: the card does not say");
            } else {
                LINES.add("VRAM " + mb(videoUsed) + (videoMost > 0L ? " of " + mb(videoMost) : "")
                        + (videoTextures >= 0L ? "  (textures " + mb(videoTextures + Math.max(0L, videoTargets)) + ")"
                                : ""));
            }
        }
        if ((on & CACHE) != 0) {
            measureCacheSoon(now);
            long code = cacheCode;
            long sounds = cacheSounds;
            LINES.add(code < 0L && sounds < 0L ? "Cache: measuring"
                    : "Cache " + mb(Math.max(0L, code) + Math.max(0L, sounds)) + "  (code " + mb(Math.max(0L, code))
                            + ", sound paths " + mb(Math.max(0L, sounds)) + ")");
        }
    }

    private static String percentile(long[] sorted, double share, String name) {
        if (sorted.length == 0) {
            return name + " -";
        }
        double ms = sorted[Math.min(sorted.length - 1, (int) Math.ceil(share * sorted.length) - 1)] / 1e6D;
        return String.format(Locale.ROOT, "%s %.1f ms (%.0f fps)", name, ms, ms > 0D ? 1000D / ms : 0D);
    }

    private static String mb(long bytes) {
        return bytes >= 10L << 30 ? String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30))
                : bytes / (1L << 20) + " MB";
    }

    /** Every ten seconds, on a thread of its own: the AOT code cache's size and the baked sound paths'. */
    private static void measureCacheSoon(long now) {
        if (measuringCache || cacheMeasuredAt != 0L && now - cacheMeasuredAt < 10_000_000_000L) {
            return;
        }
        measuringCache = true;
        Thread.ofVirtual().name("nfm-cache-size").start(() -> {
            try {
                File aot = new File("madness.aot");
                cacheCode = aot.isFile() ? aot.length() : 0L;
                cacheSounds = folderSize(new File("cache"));
            } finally {
                cacheMeasuredAt = System.nanoTime();
                measuringCache = false;
            }
        });
    }

    private static long folderSize(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return 0L;
        }
        long size = 0L;
        for (File f : files) {
            size += f.isDirectory() ? folderSize(f) : f.length();
        }
        return size;
    }

    /* the process's private memory, from Windows (K32GetProcessMemoryInfo) */
    private static MethodHandle memoryInfo;
    private static boolean memoryInfoUnavailable;
    private static MemorySegment counters;

    /** The memory Windows has committed to the game alone, in bytes; 0 if it cannot say. */
    private static long processMemory() {
        if (!GameSparker.IS_WINDOWS || memoryInfoUnavailable) {
            return 0L;
        }
        try {
            if (memoryInfo == null) {
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                memoryInfo = Linker.nativeLinker().downcallHandle(kernel32.findOrThrow("K32GetProcessMemoryInfo"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT));
                counters = Arena.global().allocate(72, 8); // PROCESS_MEMORY_COUNTERS
            }
            counters.set(ValueLayout.JAVA_INT, 0, 72);
            MemorySegment self = MemorySegment.ofAddress(-1L); // GetCurrentProcess()
            if ((int) memoryInfo.invokeExact(self, counters, 72) == 0) {
                return 0L;
            }
            return counters.get(ValueLayout.JAVA_LONG, 56); // PagefileUsage: private bytes
        } catch (Throwable ex) {
            memoryInfoUnavailable = true;
            return 0L;
        }
    }
}
