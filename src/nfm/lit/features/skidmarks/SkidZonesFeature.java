package nfm.lit.features.skidmarks;

import fallk.logmaster.HLogger;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import nfm.lit.features.Canvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.RaceView;

/**
 * Last race's skid zones (effects, "Last race's skid zones can be shown"):
 * while a race runs, a coarse grid of the stage (4 m squares) adds up how
 * much the wheels slid in each square, and when the race ends the grid is
 * saved in {@code cache\skid-heat}. The next race on that stage shows the
 * squares where most sliding happened as a faint darker tone on the road.
 * <p>
 * It slides its own wheels ({@link SkidSlip}), so it works with the
 * skidmarks off too, and writes nothing in test runs.
 */
public final class SkidZonesFeature implements Feature {

    public static final String ID = "skidzones";

    /** A square of the grid: 4 m. */
    static final float CELL = 240F;
    /** The grid's room: squares that saw sliding in one race. */
    static final int ROOM = 16384;
    /** At most this many squares show. */
    static final int SHOWN = 600;
    /** A square shows if it saw at least this share of the most-slid square's sliding. */
    static final float SHOW_FROM = 0.35F;
    private static final int MAGIC = 0x534b4831; // "SKH1"
    private static final int ITEM = 0;

    private final SkidSlip slip = new SkidSlip();
    /* the grid of this race: open addressing on the square's key */
    private final int[] keys = new int[ROOM];
    private final float[] heat = new float[ROOM];
    private final float[] height = new float[ROOM];
    private final boolean[] used = new boolean[ROOM];
    private int cells;

    /* last race's heaviest squares, shown */
    final int[] shownKey = new int[SHOWN];
    final float[] shownHeat = new float[SHOWN];
    final float[] shownY = new float[SHOWN];
    int shown;
    private float shownMax;

    private RaceView started;
    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void raceStarted(RaceView race) {
        started = race;
        slip.reset();
        Arrays.fill(used, false);
        cells = 0;
        shown = 0;
        shownMax = 0F;
        load(file(race));
    }

    @Override
    public void afterTick(RaceView race) {
        slip.update(race);
        int wheels = race.cars * RaceView.WHEELS;
        for (int at = 0; at < wheels; at++) {
            float s = slip.slide[at];
            if (race.contact[at] && s > SkidSlip.ON && slip.ratio[at] > 0.12F) {
                add(race.wheelX[at], race.wheelY[at], race.wheelZ[at], s);
            }
        }
    }

    @Override
    public void raceEnded(RaceView race) {
        RaceView r = race != null ? race : started;
        started = null;
        if (r == null || !r.keepsFiles || cells == 0) {
            return;
        }
        save(file(r));
    }

    /** The file this stage's grid is kept in. */
    static Path file(RaceView race) {
        String name = race.stageName == null ? "" : race.stageName.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return race.cacheDir.resolve("skid-heat").resolve("stage-" + race.stage + (name.isEmpty() ? "" : "-" + name)
                + ".bin");
    }

    static int key(float x, float z) {
        int gx = (int) Math.floor(x / CELL);
        int gz = (int) Math.floor(z / CELL);
        return (gx & 0xffff) << 16 | (gz & 0xffff);
    }

    static float centreX(int key) {
        return ((short) (key >>> 16) + 0.5F) * CELL;
    }

    static float centreZ(int key) {
        return ((short) key + 0.5F) * CELL;
    }

    private void add(float x, float y, float z, float amount) {
        int k = key(x, z);
        int i = slot(k);
        if (i < 0) {
            return; // full: a race slid in more than 16,000 squares; the rest goes uncounted
        }
        if (!used[i]) {
            used[i] = true;
            keys[i] = k;
            heat[i] = 0F;
            height[i] = y;
            cells++;
        }
        heat[i] += amount;
        height[i] = Math.min(height[i], y);
    }

    /** The slot of square {@code k}: where it is, or the free one it would take; -1 when full. */
    private int slot(int k) {
        int i = (k * 0x9E3779B1 >>> 18) & (ROOM - 1);
        for (int n = 0; n < ROOM; n++) {
            if (!used[i] || keys[i] == k) {
                return used[i] || cells < ROOM * 3 / 4 ? i : -1;
            }
            i = (i + 1) & (ROOM - 1);
        }
        return -1;
    }

    /* ---------------------------------------------------------- the file */

    private void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream o = Files.newOutputStream(temp);
                    DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(o))) {
                out.writeInt(MAGIC);
                out.writeInt(cells);
                for (int i = 0; i < ROOM; i++) {
                    if (used[i]) {
                        out.writeInt(keys[i]);
                        out.writeFloat(heat[i]);
                        out.writeFloat(height[i]);
                    }
                }
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            HLogger.warn("Skid zones: could not keep " + path + ": " + e);
        }
    }

    private void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream i = Files.newInputStream(path);
                DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(i))) {
            if (in.readInt() != MAGIC) {
                return;
            }
            int n = in.readInt();
            if (n < 0 || n > ROOM) {
                return;
            }
            for (int c = 0; c < n; c++) {
                offer(in.readInt(), in.readFloat(), in.readFloat());
            }
        } catch (IOException | RuntimeException e) {
            HLogger.warn("Skid zones: could not read " + path + ": " + e);
            shown = 0;
            return;
        }
        // keep the squares that saw at least a share of the most
        int kept = 0;
        for (int c = 0; c < shown; c++) {
            if (shownHeat[c] >= SHOW_FROM * shownMax) {
                shownKey[kept] = shownKey[c];
                shownHeat[kept] = shownHeat[c];
                shownY[kept++] = shownY[c];
            }
        }
        shown = kept;
    }

    /** A square of last race's grid: kept if it is among the {@link #SHOWN} heaviest so far. */
    private void offer(int k, float h, float y) {
        if (!(h > 0F) || Float.isNaN(y)) {
            return;
        }
        shownMax = Math.max(shownMax, h);
        int at;
        if (shown < SHOWN) {
            at = shown++;
        } else {
            at = 0;
            for (int c = 1; c < SHOWN; c++) {
                if (shownHeat[c] < shownHeat[at]) {
                    at = c;
                }
            }
            if (shownHeat[at] >= h) {
                return;
            }
        }
        shownKey[at] = k;
        shownHeat[at] = h;
        shownY[at] = y;
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        if (shown > 0) {
            queue.ground(ITEM);
        }
    }

    @Override
    public void draw(Canvas canvas, int item) {
        float half = CELL / 2F;
        for (int c = 0; c < shown; c++) {
            float x = centreX(shownKey[c]);
            float z = centreZ(shownKey[c]);
            float y = shownY[c] - 0.4F;
            canvas.color(canvas.fog(0x141414, x, y, z));
            canvas.dither(0.08F + 0.17F * shownHeat[c] / shownMax);
            cx[0] = x - half;
            cz[0] = z - half;
            cx[1] = x + half;
            cz[1] = z - half;
            cx[2] = x + half;
            cz[2] = z + half;
            cx[3] = x - half;
            cz[3] = z + half;
            cy[0] = cy[1] = cy[2] = cy[3] = y;
            canvas.world(cx, cy, cz, 4);
        }
    }

    /* ---------------------------------------------------------- for tests */

    /** Squares counted this race. */
    int cells() {
        return cells;
    }
}
