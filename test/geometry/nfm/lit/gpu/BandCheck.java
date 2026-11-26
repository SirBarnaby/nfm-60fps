package nfm.lit.gpu;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Checks the bands {@link SceneGeometry} cuts polygons that cross themselves in for the graphics card, away from the
 * game: each polygon is built as a kept scene is ({@code buildProjected}), its corners are moved, the vertices are
 * placed as {@code Shaders.PROJECTED_VS} places them, and what the triangles cover is compared with what the even-odd
 * rule fills for the moved corners. A polygon the build sends to the stencil is exact wherever its corners go.
 * <p>
 * The kept scene's probe (see {@link ScenePoints#probe}) gets the corners where the polygon is cut and where they move
 * to, as the game gives it the corners at both ends of the moments a kept scene's frames are placed for; with
 * {@code -Dnoprobe=true} it gets none, as before the check.
 *
 * <pre>
 * BandCheck random &lt;move&gt; &lt;mode&gt; [cases] [seed]
 *     random polygons that cross themselves, at samples every half pixel; mode "random" moves each corner by about
 *     move pixels, "one" one corner, "edge" one edge's two corners down (the blind review's check), "perspective"
 *     tilts the plane so the far side moves about move pixels
 * BandCheck motion [-Dreach=1.5] motion-*.bin...
 *     the game's own polygons from -Dnfm.motiontest scenes (49 moments between two ticks): each cut where it is
 *     halfway, as a kept scene is, and placed for every other moment it was drawn at, followed by continuity; the
 *     probe's far end is the last moment carried on to reach (1.5 in the game), at pixel centres
 * BandCheck time motion-*.bin...
 *     what the check costs: the same polygons built without the probe and with it, again and again
 * BandCheck stencil &lt;move&gt; [scenes] [seed]
 *     scenes of several polygons over each other, placed for another span than they were cut for (the frames between
 *     a tick's span and its scene): the draws for that, which fill band polygons with the stencil, against the
 *     even-odd rule polygon by polygon in order; the bands' own draws are counted beside them
 * </pre>
 */
public final class BandCheck {

    record Poly(String key, float[] x, float[] y) {
    }

    /** A polygon built for the card: its vertices, the points made for it, and whether it went to the stencil. */
    private record Built(int count, ByteBuffer bytes, float[] extra, boolean stencil) {
    }

    /**
     * Exits with 1 if a sample is wrong away from the outline (one on it, within 0.0001 px, is a tie between the
     * triangles' inclusive edges and the even-odd rule's half-open ones, not geometry), 2 for bad arguments.
     */
    public static void main(String[] args) throws IOException {
        long[] byDistance = new long[5]; // wrong samples by distance to the outline: <1e-4, <0.01, <0.1, <0.5, more
        switch (args.length == 0 ? "" : args[0]) {
            case "random" -> random(Double.parseDouble(args[1]), args[2], args.length > 3 ? Integer.parseInt(args[3]) : 2000,
                    args.length > 4 ? Long.parseLong(args[4]) : 1L, byDistance);
            case "motion" -> motion(List.of(args).subList(1, args.length), byDistance);
            case "time" -> {
                time(List.of(args).subList(1, args.length));
                return;
            }
            case "stencil" -> stencil(Double.parseDouble(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 2000,
                    args.length > 3 ? Long.parseLong(args[3]) : 1L, byDistance);
            default -> {
                System.out.println("BandCheck random <move> <mode> [cases] [seed] | motion <files> | time <files>"
                        + " | stencil <move> [scenes] [seed]");
                System.exit(2);
            }
        }
        long off = byDistance[1] + byDistance[2] + byDistance[3] + byDistance[4];
        System.out.printf("  wrong samples by distance to the outline: under 0.0001 px %d (ties), under 0.01 %d, under 0.1 %d,"
                + " under 0.5 %d, more %d: %s%n", byDistance[0], byDistance[1], byDistance[2], byDistance[3],
                byDistance[4], off == 0 ? "pass" : "FAIL");
        System.exit(off == 0 ? 0 : 1);
    }

    /* ------------------------------------------------------------------ building and placing */

    private static final boolean PROBE = !Boolean.getBoolean("noprobe");

    /** A polygon as the recorder hands it over: the list that draws it, and its corners as recorded points. */
    private record Input(SceneList list, ScenePoints points) {
    }

    /**
     * Builds polygon x, y for the card as a kept scene is, its corners recorded points; with {@code ends} (corners at
     * end 0, then end 1: x0, y0, x1, y1) as the probe.
     */
    private static Built build(SceneGeometry g, float[] x, float[] y, float[][] ends) {
        Input in = input(x, y);
        in.points().probe = probe(ends);
        g.buildProjected(in.list(), in.points(), 1, 1);
        boolean stencil = false;
        for (int d = 0; d < g.draws(); d++) {
            stencil |= g.kind(d) != SceneGeometry.PLAIN;
        }
        int count = g.vertexCount(); // the bands' own; the fans the build keeps for another span come after them
        ByteBuffer bytes = ByteBuffer.allocate(g.projectedVertexCount() * SceneGeometry.PROJECTED_STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN);
        g.copyProjected(bytes);
        return new Built(count, bytes, g.extraPoints(), stencil);
    }

    /** A probe that gives the corners in {@code ends} (x0, y0, x1, y1); null for none. */
    private static ScenePoints.Probe probe(float[][] ends) {
        return ends == null ? null : (end, id, xy) -> {
            xy[0] = ends[2 * end][id];
            xy[1] = ends[2 * end + 1][id];
        };
    }

    private static Input input(float[] x, float[] y) {
        int n = x.length;
        SceneList list = new SceneList();
        list.setColor(0xffffff);
        int at = list.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int k = 0; k < n; k++) {
            xs[k] = Math.round(x[k]);
            ys[k] = Math.round(y[k]);
        }
        list.polygon(SceneList.FILL_POLYGON, xs, ys, n, x, y);
        ScenePoints p = new ScenePoints();
        p.kinds = new int[n];
        p.points = new float[n * ScenePoints.STRIDE];
        p.pointCount = n;
        for (int k = 0; k < n; k++) {
            p.kinds[k] = ScenePoints.CONSTANT;
            p.points[k * ScenePoints.STRIDE] = x[k];
            p.points[k * ScenePoints.STRIDE + 1] = y[k];
        }
        p.polygons = new int[] { at + 2 + 2 * n, n, 0 };
        p.polygonCount = 1;
        p.codes = new int[2 * n];
        for (int k = 0; k < n; k++) {
            p.codes[k] = 2 * k;
            p.codes[n + k] = 2 * k + 1;
        }
        return new Input(list, p);
    }

    /** Places the vertices for corners mx, my as the projected vertex shader does (with its quarter-pixel shift). */
    private static void place(Built b, int n, float[] mx, float[] my, float[] vx, float[] vy) {
        float[] extra = b.extra();
        for (int v = 0; v < b.count(); v++) {
            int at = v * SceneGeometry.PROJECTED_STRIDE;
            ByteBuffer bytes = b.bytes();
            float posX = bytes.getFloat(at);
            float posY = bytes.getFloat(at + 4);
            int ax = (int) bytes.getFloat(at + 12);
            int ay = (int) bytes.getFloat(at + 16);
            int bx = (int) bytes.getFloat(at + 20);
            int by = (int) bytes.getFloat(at + 24);
            switch (Math.round(bytes.getFloat(at + 28))) {
                case SceneGeometry.ROLE_FILL -> {
                    vx[v] = coord(ax, n, mx, extra, 0) + 0.25F;
                    vy[v] = coord(ay, n, my, extra, 1) + 0.25F;
                }
                case SceneGeometry.ROLE_FIXED -> {
                    vx[v] = posX;
                    vy[v] = posY;
                }
                case SceneGeometry.ROLE_ALONG -> {
                    float x0 = coord(ax, n, mx, extra, 0);
                    float y0 = coord(ay, n, my, extra, 1);
                    vx[v] = x0 + (coord(bx, n, mx, extra, 0) - x0) * posX + 0.25F;
                    vy[v] = y0 + (coord(by, n, my, extra, 1) - y0) * posX + 0.25F;
                }
                case SceneGeometry.ROLE_CROSS -> {
                    // corners a, b, c, d in the four point numbers, each x and y from one point
                    float pax = coord(ax, n, mx, extra, 0);
                    float pay = coord(ax, n, my, extra, 1);
                    float abx = coord(ay, n, mx, extra, 0) - pax;
                    float aby = coord(ay, n, my, extra, 1) - pay;
                    float pcx = coord(bx, n, mx, extra, 0);
                    float pcy = coord(bx, n, my, extra, 1);
                    float cdx = coord(by, n, mx, extra, 0) - pcx;
                    float cdy = coord(by, n, my, extra, 1) - pcy;
                    float acx = pcx - pax;
                    float acy = pcy - pay;
                    float den = abx * cdy - aby * cdx;
                    float share = den != 0F ? Math.max(posX, Math.min(posY, (acx * cdy - acy * cdx) / den))
                            : (posX + posY) * 0.5F;
                    vx[v] = pax + abx * share + 0.25F;
                    vy[v] = pay + aby * share + 0.25F;
                }
                default -> throw new IllegalStateException("role " + bytes.getFloat(at + 28));
            }
        }
    }

    private static float coord(int id, int n, float[] corners, float[] extra, int axis) {
        return id < n ? corners[id] : extra[(id - n) * ScenePoints.STRIDE + axis];
    }

    /* ------------------------------------------------------------------ random polygons */

    private static final double STEP = 0.5;

    private static void random(double move, String mode, int cases, long seed, long[] byDistance) {
        Random r = new Random(seed);
        SceneGeometry g = new SceneGeometry();
        long wrongOut = 0;
        long wrongIn = 0;
        long inside = 0;
        double worst = 0;
        int casesWrong = 0;
        int stencil = 0;
        int done = 0;
        int size = (int) (100 / STEP);
        while (done < cases) {
            int n = 4 + r.nextInt(5);
            float[] x = new float[n];
            float[] y = new float[n];
            for (int k = 0; k < n; k++) {
                x[k] = 10F + r.nextFloat() * 80F;
                y[k] = 10F + r.nextFloat() * 80F;
            }
            if (SceneGeometry.isSimple(x, y, n)) {
                continue;
            }
            done++;
            float[] mx = new float[n];
            float[] my = new float[n];
            moveCorners(mode, move, r, x, y, mx, my);
            Built b = build(g, x, y, PROBE ? new float[][] { x, y, mx, my } : null);
            if (b.stencil()) {
                stencil++;
                continue; // the stencil fills what the moved corners cover
            }
            float[] vx = new float[b.count()];
            float[] vy = new float[b.count()];
            place(b, n, mx, my, vx, vy);
            boolean[] covered = new boolean[size * size];
            for (int v = 0; v + 2 < b.count(); v += 3) {
                cover(covered, 0, 0, size, size, STEP, vx[v], vy[v], vx[v + 1], vy[v + 1], vx[v + 2], vy[v + 2]);
            }
            long out = 0;
            long in = 0;
            long caseInside = 0;
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    double sx = (i + 0.5) * STEP - 0.25;
                    double sy = (j + 0.5) * STEP - 0.25;
                    boolean evenOdd = insideEvenOdd(mx, my, sx, sy);
                    boolean c = covered[j * size + i];
                    caseInside += evenOdd ? 1 : 0;
                    out += c && !evenOdd ? 1 : 0;
                    in += !c && evenOdd ? 1 : 0;
                    if (c != evenOdd) {
                        byDistance[distanceClass(mx, my, sx, sy)]++;
                    }
                }
            }
            wrongOut += out;
            wrongIn += in;
            inside += caseInside;
            casesWrong += out + in > 0 ? 1 : 0;
            worst = Math.max(worst, caseInside == 0 ? 0 : (double) (out + in) / caseInside);
        }
        double px2 = STEP * STEP;
        System.out.printf("move %.2f %s, probe %s: %d polygons, %d of them to the stencil; filled outside %.1f px^2, "
                + "missed inside %.1f px^2, of %.0f px^2 inside the rest (%.4f%%); %d polygons wrong somewhere, worst "
                + "%.2f%%%n", move, mode, PROBE, cases, stencil, wrongOut * px2, wrongIn * px2, inside * px2,
                100.0 * (wrongOut + wrongIn) / Math.max(1, inside), casesWrong, 100 * worst);
    }

    private static void moveCorners(String mode, double move, Random r, float[] x, float[] y, float[] mx, float[] my) {
        int n = x.length;
        System.arraycopy(x, 0, mx, 0, n);
        System.arraycopy(y, 0, my, 0, n);
        switch (mode) {
            case "edge" -> {
                int k = r.nextInt(n);
                my[k] += (float) move;
                my[(k + 1) % n] += (float) move;
            }
            case "one" -> {
                int k = r.nextInt(n);
                double angle = r.nextDouble() * 2 * Math.PI;
                mx[k] += (float) (move * Math.cos(angle));
                my[k] += (float) (move * Math.sin(angle));
            }
            case "perspective" -> {
                // x' = x / (1 + g x + h y) around the middle, g and h so the far side moves about move pixels
                double gx = (r.nextDouble() * 2 - 1) * move / 50.0 / 50.0;
                double hy = (r.nextDouble() * 2 - 1) * move / 50.0 / 50.0;
                for (int k = 0; k < n; k++) {
                    double u = x[k] - 50;
                    double v = y[k] - 50;
                    double w = 1 + gx * u + hy * v;
                    mx[k] = (float) (50 + u / w);
                    my[k] = (float) (50 + v / w);
                }
            }
            case "random" -> {
                for (int k = 0; k < n; k++) {
                    mx[k] = (float) (x[k] + r.nextGaussian() * move);
                    my[k] = (float) (y[k] + r.nextGaussian() * move);
                }
            }
            default -> throw new IllegalArgumentException("mode " + mode);
        }
    }

    /* ------------------------------------------------------------------ placed for another span */

    /**
     * Scenes of 3-8 polygons over each other, each its own grey. Those that cross themselves are cut in bands checked
     * for a tick's span (the probe: their corners where cut, and moved a tenth of a pixel), then placed for another
     * span, each of their corners moved about {@code move} pixels; the others move whole by as much, which keeps how
     * they were cut. Drawn in order as the card does, the draws for another span
     * ({@link SceneGeometry#stencilBands}) must show what the even-odd rule fills polygon by polygon; the bands' own
     * draws are counted beside them, wrong where a band no longer holds.
     */
    private static void stencil(double move, int scenes, long seed, long[] byDistance) {
        Random r = new Random(seed);
        SceneGeometry g = new SceneGeometry();
        int size = (int) (100 / STEP);
        long bandsWrong = 0;
        long stencilWrong = 0;
        long samples = 0;
        int crossing = 0;
        int all = 0;
        int bandScenes = 0;
        int shown = 0;
        for (int s = 0; s < scenes; s++) {
            int count = 3 + r.nextInt(6);
            float[][] cutX = new float[count][];
            float[][] cutY = new float[count][];
            float[][] movedX = new float[count][];
            float[][] movedY = new float[count][];
            float[][] endX = new float[count][];
            float[][] endY = new float[count][];
            for (int p = 0; p < count; p++) {
                int n = 3 + r.nextInt(6);
                float[] x = new float[n];
                float[] y = new float[n];
                for (int k = 0; k < n; k++) {
                    x[k] = 10F + r.nextFloat() * 80F;
                    y[k] = 10F + r.nextFloat() * 80F;
                }
                boolean crosses = !SceneGeometry.isSimple(x, y, n);
                crossing += crosses ? 1 : 0;
                float[] mx = new float[n];
                float[] my = new float[n];
                float[] ex = new float[n];
                float[] ey = new float[n];
                double dx = r.nextGaussian() * move;
                double dy = r.nextGaussian() * move;
                for (int k = 0; k < n; k++) {
                    mx[k] = (float) (x[k] + (crosses ? r.nextGaussian() * move : dx));
                    my[k] = (float) (y[k] + (crosses ? r.nextGaussian() * move : dy));
                    ex[k] = (float) (x[k] + r.nextGaussian() * 0.1);
                    ey[k] = (float) (y[k] + r.nextGaussian() * 0.1);
                }
                cutX[p] = x;
                cutY[p] = y;
                movedX[p] = mx;
                movedY[p] = my;
                endX[p] = ex;
                endY[p] = ey;
            }
            all += count;
            Input in = scene(cutX, cutY);
            in.points().probe = probe(new float[][] { flat(cutX), flat(cutY), flat(endX), flat(endY) });
            g.buildProjected(in.list(), in.points(), 1, 1);
            int total = g.projectedVertexCount();
            ByteBuffer bytes = ByteBuffer.allocate(total * SceneGeometry.PROJECTED_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
            g.copyProjected(bytes);
            Built b = new Built(total, bytes, g.extraPoints(), false);
            float[] mx = flat(movedX);
            float[] my = flat(movedY);
            float[] vx = new float[total];
            float[] vy = new float[total];
            place(b, mx.length, mx, my, vx, vy);
            int[] want = new int[size * size];
            for (int p = 0; p < count; p++) {
                for (int j = 0; j < size; j++) {
                    for (int i = 0; i < size; i++) {
                        if (insideEvenOdd(movedX[p], movedY[p], (i + 0.5) * STEP - 0.25, (j + 0.5) * STEP - 0.25)) {
                            want[j * size + i] = p + 1;
                        }
                    }
                }
            }
            samples += want.length;
            g.stencilBands(false);
            int[] bands = draw(g, bytes, vx, vy, size);
            g.stencilBands(true);
            bandScenes += g.draws() != drawCount(g, false) ? 1 : 0;
            int[] stencil = draw(g, bytes, vx, vy, size);
            for (int q = 0; q < want.length; q++) {
                bandsWrong += bands[q] != want[q] ? 1 : 0;
                if (stencil[q] != want[q]) {
                    stencilWrong++;
                    double sx = (q % size + 0.5) * STEP - 0.25;
                    double sy = (q / size + 0.5) * STEP - 0.25;
                    double d = Double.MAX_VALUE;
                    for (int p = 0; p < count; p++) {
                        d = Math.min(d, outlineDistance(movedX[p], movedY[p], sx, sy));
                    }
                    byDistance[d < 1e-4 ? 0 : d < 1e-2 ? 1 : d < 0.1 ? 2 : d < 0.5 ? 3 : 4]++;
                    if (d >= 1e-4 && shown++ < 5) {
                        System.out.printf("  scene %d at %.2f, %.2f (%.4f px from an outline): polygon %d shows, %d"
                                + " should%n", s, sx, sy, d, stencil[q] - 1, want[q] - 1);
                        for (int p = 0; p < count; p++) {
                            System.out.printf("    polygon %d: %s%n    moved %s%n", p, corners(cutX[p], cutY[p]),
                                    corners(movedX[p], movedY[p]));
                        }
                    }
                }
            }
        }
        double px2 = STEP * STEP;
        System.out.printf("another span, moved %.2f: %d scenes, %d polygons, %d of them cross themselves, %d scenes with"
                + " bands; wrong with the bands' draws %.1f px^2, with the draws for another span %.1f px^2, of %.0f px^2"
                + "%n", move, scenes, all, crossing, bandScenes, bandsWrong * px2, stencilWrong * px2, samples * px2);
    }

    /** The draws {@code g} has with {@link SceneGeometry#stencilBands} at {@code on}; leaves it on. */
    private static int drawCount(SceneGeometry g, boolean on) {
        g.stencilBands(on);
        int draws = g.draws();
        g.stencilBands(true);
        return draws;
    }

    /**
     * Draws {@code g}'s draws in order, as the card does, at samples every {@link #STEP}: a plain draw's triangles
     * colour what they cover, an even-odd one's flip the stencil and colour where it ended odd. Each sample holds the
     * grey of the last polygon over it (the polygon's number plus one), 0 for none.
     */
    private static int[] draw(SceneGeometry g, ByteBuffer bytes, float[] vx, float[] vy, int size) {
        int[] picture = new int[size * size];
        boolean[] odd = new boolean[size * size];
        for (int d = 0; d < g.draws(); d++) {
            int start = g.start(d);
            int end = start + g.count(d);
            if (end == start) {
                continue;
            }
            // a plain draw holds the triangles of many polygons, each in its own colour; an even-odd one, one polygon's
            int grey = bytes.getInt(start * SceneGeometry.PROJECTED_STRIDE + 8) & 0xff;
            boolean plain = g.kind(d) == SceneGeometry.PLAIN;
            if (!plain) {
                java.util.Arrays.fill(odd, false);
            }
            for (int v = start; v + 2 < end; v += 3) {
                int own = bytes.getInt(v * SceneGeometry.PROJECTED_STRIDE + 8) & 0xff;
                if (plain) {
                    covering(size, vx[v], vy[v], vx[v + 1], vy[v + 1], vx[v + 2], vy[v + 2], q -> picture[q] = own);
                } else {
                    flipping(size, vx[v], vy[v], vx[v + 1], vy[v + 1], vx[v + 2], vy[v + 2], q -> odd[q] = !odd[q]);
                }
            }
            if (!plain) {
                for (int q = 0; q < odd.length; q++) {
                    if (odd[q]) {
                        picture[q] = grey;
                    }
                }
            }
        }
        return picture;
    }

    /** Polygons x[p], y[p] in one list, each in grey p + 1, their corners recorded points numbered in order. */
    private static Input scene(float[][] x, float[][] y) {
        SceneList list = new SceneList();
        int total = 0;
        for (float[] corners : x) {
            total += corners.length;
        }
        ScenePoints p = new ScenePoints();
        p.kinds = new int[total];
        p.points = new float[total * ScenePoints.STRIDE];
        p.pointCount = total;
        p.polygons = new int[3 * x.length];
        p.polygonCount = x.length;
        p.codes = new int[2 * total];
        int id = 0;
        for (int q = 0; q < x.length; q++) {
            int n = x[q].length;
            list.setColor((q + 1) * 0x010101);
            int at = list.size();
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int k = 0; k < n; k++) {
                xs[k] = Math.round(x[q][k]);
                ys[k] = Math.round(y[q][k]);
            }
            list.polygon(SceneList.FILL_POLYGON, xs, ys, n, x[q], y[q]);
            p.polygons[3 * q] = at + 2 + 2 * n;
            p.polygons[3 * q + 1] = n;
            p.polygons[3 * q + 2] = 2 * id;
            for (int k = 0; k < n; k++) {
                p.kinds[id + k] = ScenePoints.CONSTANT;
                p.points[(id + k) * ScenePoints.STRIDE] = x[q][k];
                p.points[(id + k) * ScenePoints.STRIDE + 1] = y[q][k];
                p.codes[2 * id + k] = 2 * (id + k);
                p.codes[2 * id + n + k] = 2 * (id + k) + 1;
            }
            id += n;
        }
        return new Input(list, p);
    }

    private static String corners(float[] x, float[] y) {
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < x.length; k++) {
            out.append(String.format("%s(%.3f, %.3f)", k == 0 ? "" : " ", x[k], y[k]));
        }
        return out.toString();
    }

    private static float[] flat(float[][] parts) {
        int total = 0;
        for (float[] part : parts) {
            total += part.length;
        }
        float[] out = new float[total];
        int at = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /**
     * The samples (every {@link #STEP} from 0, size by size) triangle 0-1-2 covers, each handed to {@code at}, edges
     * included. Each edge is worked out from its upper end whichever way it runs, so two triangles that share it get
     * exactly opposite values and a sample on it is not lost between them to rounding (the diagonal of two ear
     * triangles, for one).
     */
    private static void covering(int size, float x0, float y0, float x1, float y1, float x2, float y2,
            java.util.function.IntConsumer at) {
        samples(size, x0, y0, x1, y1, x2, y2, false, at);
    }

    /**
     * As {@link #covering}, for the stencil: a sample on an edge two triangles of a fan share is handed over once, as
     * the card's fill rule has it, not by both (which would flip it back). The edge is worked out the same way in both
     * triangles, from its upper end, so the two get exactly opposite values; one exactly on it goes to the triangle on
     * the edge's positive side.
     */
    private static void flipping(int size, float x0, float y0, float x1, float y1, float x2, float y2,
            java.util.function.IntConsumer at) {
        samples(size, x0, y0, x1, y1, x2, y2, true, at);
    }

    /** {@link #covering} ({@code once} false) and {@link #flipping} (true). */
    private static void samples(int size, float x0, float y0, float x1, float y1, float x2, float y2, boolean once,
            java.util.function.IntConsumer at) {
        double area = (x1 - x0) * (double) (y2 - y0) - (y1 - y0) * (double) (x2 - x0);
        if (area == 0) {
            return;
        }
        double sign = Math.signum(area);
        int a0 = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2)) / STEP) - 1);
        int a1 = Math.min(size - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2)) / STEP) + 1);
        int b0 = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2)) / STEP) - 1);
        int b1 = Math.min(size - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2)) / STEP) + 1);
        for (int j = b0; j <= b1; j++) {
            double sy = (j + 0.5) * STEP;
            for (int i = a0; i <= a1; i++) {
                double sx = (i + 0.5) * STEP;
                if (onSide(x0, y0, x1, y1, sx, sy, sign, once) && onSide(x1, y1, x2, y2, sx, sy, sign, once)
                        && onSide(x2, y2, x0, y0, sx, sy, sign, once)) {
                    at.accept(j * size + i);
                }
            }
        }
    }

    /**
     * True if sample sx, sy is on the inside of edge a-b of a triangle turning {@code sign}; one exactly on it is, unless
     * {@code once}, then only for the triangle on the edge's positive side (see {@link #flipping}).
     */
    private static boolean onSide(float ax, float ay, float bx, float by, double sx, double sy, double sign,
            boolean once) {
        boolean flip = ay > by || ay == by && ax > bx;
        double px = flip ? bx : ax;
        double py = flip ? by : ay;
        double qx = flip ? ax : bx;
        double qy = flip ? ay : by;
        double e = (qx - px) * (sy - py) - (qy - py) * (sx - px);
        double side = flip ? -sign : sign;
        return e * side > 0 || e == 0 && (!once || side > 0);
    }

    /* ------------------------------------------------------------------ the game's polygons */

    private static void motion(List<String> files, long[] byDistance) throws IOException {
        float reach = Float.parseFloat(System.getProperty("reach", "1.5"));
        SceneGeometry g = new SceneGeometry();
        long allWrong = 0;
        long allInside = 0;
        long polysAll = 0;
        long placements = 0;
        int stencilAll = 0;
        int worst = 0;
        String worstAt = "";
        for (String file : files) {
            List<List<Poly>> frames = read(Path.of(file));
            int mid = frames.size() / 2;
            int polys = 0;
            int stencil = 0;
            for (Poly p : frames.get(mid)) {
                int n = p.x().length;
                if (n < 3 || n > 64 || SceneGeometry.isSimple(p.x(), p.y(), n)) {
                    continue;
                }
                polys++;
                Poly[] track = track(frames, mid, p);
                Built b = build(g, p.x(), p.y(), PROBE ? ends(track, mid, p, reach) : null);
                if (b.stencil()) {
                    stencil++;
                    continue;
                }
                for (int m = 0; m < frames.size(); m++) {
                    Poly q = track[m];
                    if (m == mid || q == null) {
                        continue;
                    }
                    float[] vx = new float[b.count()];
                    float[] vy = new float[b.count()];
                    place(b, n, q.x(), q.y(), vx, vy);
                    long[] result = compare(q.x(), q.y(), vx, vy, b.count(), byDistance);
                    allWrong += result[0];
                    allInside += result[1];
                    placements++;
                    if (result[0] > worst) {
                        worst = (int) result[0];
                        worstAt = Path.of(file).getFileName() + " " + p.key() + " moment " + m;
                    }
                }
            }
            polysAll += polys;
            stencilAll += stencil;
            System.out.printf("%s: %d polygons cross themselves, %d of them to the stencil%n", Path.of(file).getFileName(),
                    polys, stencil);
        }
        System.out.printf("probe %s (reach %.2f): %d polygons, %d of them to the stencil; the rest placed %d times: %d "
                + "pixels wrong of %d inside (%.4f%%), the worst placement %d (%s)%n", PROBE, reach, polysAll, stencilAll,
                placements, allWrong, allInside, 100.0 * allWrong / Math.max(1, allInside), worst, worstAt);
    }

    /** The probe's ends for a tracked polygon: its first moment, and its last carried on in a line to reach. */
    private static float[][] ends(Poly[] track, int mid, Poly p, float reach) {
        int first = 0;
        while (track[first] == null) {
            first++;
        }
        int last = track.length - 1;
        while (track[last] == null) {
            last--;
        }
        int n = p.x().length;
        float scale = (reach - 0.5F) / ((float) (last - mid) / (track.length - 1));
        float[] ex = new float[n];
        float[] ey = new float[n];
        for (int k = 0; k < n; k++) {
            ex[k] = p.x()[k] + (track[last].x()[k] - p.x()[k]) * scale;
            ey[k] = p.y()[k] + (track[last].y()[k] - p.y()[k]) * scale;
        }
        return new float[][] { track[first].x(), track[first].y(), ex, ey };
    }

    private static void time(List<String> files) throws IOException {
        List<Input> inputs = new ArrayList<>();
        List<ScenePoints.Probe> probes = new ArrayList<>();
        for (String file : files) {
            List<List<Poly>> frames = read(Path.of(file));
            int mid = frames.size() / 2;
            for (Poly p : frames.get(mid)) {
                int n = p.x().length;
                if (n >= 3 && n <= 64 && !SceneGeometry.isSimple(p.x(), p.y(), n)) {
                    inputs.add(input(p.x(), p.y()));
                    probes.add(probe(ends(track(frames, mid, p), mid, p, 1.5F)));
                }
            }
        }
        SceneGeometry g = new SceneGeometry();
        long sink = 0;
        for (int round = 0; round < 6; round++) {
            long[] nanos = new long[2];
            for (int mode = 0; mode < 2; mode++) {
                for (int q = 0; q < inputs.size(); q++) {
                    inputs.get(q).points().probe = mode == 0 ? null : probes.get(q);
                }
                long start = System.nanoTime();
                for (int pass = 0; pass < 50; pass++) {
                    for (Input in : inputs) {
                        g.buildProjected(in.list(), in.points(), 1, 1);
                        sink += g.vertexCount();
                    }
                }
                nanos[mode] = System.nanoTime() - start;
            }
            double builds = 50.0 * inputs.size();
            System.out.printf("round %d: %d polygons from %d scenes, built %.2f us each without the probe, %.2f us with "
                    + "it: the check %.3f ms a scene%n", round, inputs.size(), files.size(), nanos[0] / builds / 1e3,
                    nanos[1] / builds / 1e3, (nanos[1] - nanos[0]) / 50.0 / files.size() / 1e6);
        }
        System.out.println(sink == 42 ? "" : "(the later rounds are the warm ones)");
    }

    /** Moves a frame to the next at most this far (each corner), or it is another polygon. */
    private static final float STEP_LIMIT = 6F;

    /**
     * p's polygon at every moment, followed from the middle out moment by moment, each corner moving at most
     * STEP_LIMIT a moment; null where it cannot be told. (Matching by kind and order instead compares other polygons
     * once a scenery polygon comes or goes.)
     */
    static Poly[] track(List<List<Poly>> frames, int mid, Poly p) {
        Poly[] track = new Poly[frames.size()];
        track[mid] = p;
        String base = p.key().substring(0, p.key().lastIndexOf('|'));
        for (int dir = -1; dir <= 1; dir += 2) {
            Poly last = p;
            for (int m = mid + dir; m >= 0 && m < frames.size(); m += dir) {
                Poly best = null;
                float bestDistance = STEP_LIMIT;
                for (Poly q : frames.get(m)) {
                    if (q.x().length != p.x().length || !q.key().startsWith(base + "|")) {
                        continue;
                    }
                    float distance = 0F;
                    for (int k = 0; k < q.x().length; k++) {
                        distance = Math.max(distance,
                                Math.max(Math.abs(q.x()[k] - last.x()[k]), Math.abs(q.y()[k] - last.y()[k])));
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = q;
                    }
                }
                if (best == null) {
                    break;
                }
                track[m] = best;
                last = best;
            }
        }
        return track;
    }

    /** {wrong pixel centres, centres inside}, within the picture (2560 x 1440 at most). */
    private static long[] compare(float[] x, float[] y, float[] vx, float[] vy, int count, long[] byDistance) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int k = 0; k < x.length; k++) {
            minX = Math.min(minX, x[k]);
            maxX = Math.max(maxX, x[k]);
            minY = Math.min(minY, y[k]);
            maxY = Math.max(maxY, y[k]);
        }
        for (int v = 0; v < count; v++) {
            minX = Math.min(minX, vx[v]);
            maxX = Math.max(maxX, vx[v]);
            minY = Math.min(minY, vy[v]);
            maxY = Math.max(maxY, vy[v]);
        }
        int i0 = Math.max(0, (int) Math.floor(minX) - 1);
        int i1 = Math.min(2559, (int) Math.ceil(maxX) + 1);
        int j0 = Math.max(0, (int) Math.floor(minY) - 1);
        int j1 = Math.min(1439, (int) Math.ceil(maxY) + 1);
        if (i1 < i0 || j1 < j0) {
            return new long[] { 0, 0 };
        }
        int w = i1 - i0 + 1;
        int h = j1 - j0 + 1;
        boolean[] covered = new boolean[w * h];
        for (int v = 0; v + 2 < count; v += 3) {
            cover(covered, i0, j0, w, h, 1.0, vx[v], vy[v], vx[v + 1], vy[v + 1], vx[v + 2], vy[v + 2]);
        }
        long wrong = 0;
        long inside = 0;
        for (int j = 0; j < h; j++) {
            double sy = j0 + j + 0.5 - 0.25;
            for (int i = 0; i < w; i++) {
                double sx = i0 + i + 0.5 - 0.25;
                boolean in = insideEvenOdd(x, y, sx, sy);
                inside += in ? 1 : 0;
                if (in != covered[j * w + i]) {
                    wrong++;
                    byDistance[distanceClass(x, y, sx, sy)]++;
                }
            }
        }
        return new long[] { wrong, inside };
    }

    /* ------------------------------------------------------------------ coverage */

    /** Marks the samples (every {@code step}, from i0, j0, w by h of them) triangle 0-1-2 covers, edges included. */
    private static void cover(boolean[] covered, int i0, int j0, int w, int h, double step, float x0, float y0,
            float x1, float y1, float x2, float y2) {
        double area = (x1 - x0) * (double) (y2 - y0) - (y1 - y0) * (double) (x2 - x0);
        if (area == 0) {
            return;
        }
        double sign = Math.signum(area);
        int a0 = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2)) / step) - 1 - i0);
        int a1 = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2)) / step) + 1 - i0);
        int b0 = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2)) / step) - 1 - j0);
        int b1 = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2)) / step) + 1 - j0);
        for (int j = b0; j <= b1; j++) {
            double sy = (j0 + j + 0.5) * step;
            for (int i = a0; i <= a1; i++) {
                double sx = (i0 + i + 0.5) * step;
                double e0 = ((x1 - x0) * (sy - y0) - (y1 - y0) * (sx - x0)) * sign;
                double e1 = ((x2 - x1) * (sy - y1) - (y2 - y1) * (sx - x1)) * sign;
                double e2 = ((x0 - x2) * (sy - y2) - (y0 - y2) * (sx - x2)) * sign;
                if (e0 >= 0 && e1 >= 0 && e2 >= 0) {
                    covered[j * w + i] = true;
                }
            }
        }
    }

    private static boolean insideEvenOdd(float[] x, float[] y, double sx, double sy) {
        boolean in = false;
        int n = x.length;
        for (int k = 0, prev = n - 1; k < n; prev = k++) {
            if ((y[k] > sy) != (y[prev] > sy)) {
                double cx = x[prev] + (sy - y[prev]) * (x[k] - x[prev]) / (y[k] - y[prev]);
                if (sx < cx) {
                    in = !in;
                }
            }
        }
        return in;
    }

    /** How far sample sx, sy is from the outline: 0 under 0.0001 px, 1 under 0.01, 2 under 0.1, 3 under 0.5, else 4. */
    private static int distanceClass(float[] x, float[] y, double sx, double sy) {
        double d = outlineDistance(x, y, sx, sy);
        return d < 1e-4 ? 0 : d < 1e-2 ? 1 : d < 0.1 ? 2 : d < 0.5 ? 3 : 4;
    }

    private static double outlineDistance(float[] x, float[] y, double sx, double sy) {
        double best = Double.MAX_VALUE;
        int n = x.length;
        for (int k = 0, prev = n - 1; k < n; prev = k++) {
            double ax = x[prev];
            double ay = y[prev];
            double dx = x[k] - ax;
            double dy = y[k] - ay;
            double len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((sx - ax) * dx + (sy - ay) * dy) / len));
            best = Math.min(best, Math.hypot(sx - ax - t * dx, sy - ay - t * dy));
        }
        return best;
    }

    /* ------------------------------------------------------------------ -Dnfm.motiontest scenes */

    /** The exact filled polygons of each list in the file, keyed as test/motion.py keys them. */
    static List<List<Poly>> read(Path file) throws IOException {
        List<List<Poly>> frames = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            while (in.available() > 0) {
                int size = in.readInt();
                int[] data = new int[size];
                for (int i = 0; i < size; i++) {
                    data[i] = in.readInt();
                }
                frames.add(polygons(data));
            }
        }
        return frames;
    }

    private static List<Poly> polygons(int[] data) {
        List<Poly> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        Integer tag = null;
        int pane = -1;
        int i = 0;
        while (i < data.length) {
            int op = data[i];
            switch (op) {
                case 8 -> {
                    tag = data[i + 1];
                    i += 2;
                }
                case 1, 5, 9, 11 -> i += 2;
                case 4 -> i += 5;
                case 10 -> {
                    pane++;
                    i += 5;
                }
                case 2, 3, 6, 7 -> {
                    int n = data[i + 1];
                    boolean exact = op == 6 || op == 7;
                    String base = (tag == null || tag < 0 ? "scenery" : tag.toString()) + "|" + (op == 2 || op == 6)
                            + "|" + n + "|" + pane;
                    int occurrence = seen.merge(base, 1, Integer::sum);
                    if (op == 6) {
                        float[] x = new float[n];
                        float[] y = new float[n];
                        for (int k = 0; k < n; k++) {
                            x[k] = Float.intBitsToFloat(data[i + 2 + 2 * n + k]);
                            y[k] = Float.intBitsToFloat(data[i + 2 + 3 * n + k]);
                        }
                        out.add(new Poly(base + "|" + occurrence, x, y));
                    }
                    i += exact ? 2 + 4 * n : 2 + 2 * n;
                }
                default -> throw new IllegalStateException("op " + op + " at " + i);
            }
        }
        return out;
    }
}
