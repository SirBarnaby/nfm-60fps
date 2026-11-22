package nfm.lit.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;


/**
 * Turns a {@link SceneList} into triangles for the graphics card, keeping the
 * game's painter's order.
 * <p>
 * Polygons, rectangles and outlines are plain triangles and are gathered
 * into as few draw calls as possible: the card draws the triangles of one
 * call in order, so later ones still cover earlier ones. A concave polygon is
 * cut into triangles that fill what Java2D's even-odd rule does, one crossing
 * itself in bands between its corners and crossings; in a kept scene placed
 * on the card, whose corners move after it is cut, the points where bands
 * meet edges move along with them. Only a polygon with very many corners, or
 * one placed on the card that is flat when it is cut, uses the stencil
 * buffer: its fan of triangles flips the stencil, and a second pass colours
 * where it ended up odd.
 * <p>
 * Outlines are drawn as parallelograms one pixel thick across their major
 * axis, which lights one pixel per column (or row), as a one-pixel line does.
 */
final class SceneGeometry {

    static final int PLAIN = 0;
    static final int EVEN_ODD = 1;

    /**
     * Java2D fills a polygon without antialiasing as if its corners were a
     * quarter pixel up and left of where the graphics card would put them
     * (it samples pixels at x + 0.25, y + 0.25), so fills move by that much.
     * Measured: then 133 of 1.6 million pixels of random triangles differ.
     */
    static final float FILL_SHIFT = 0.25F;

    /**
     * Bytes per vertex: x and y as floats, the colour as RGBA bytes, then for
     * antialiased lines where across the line the vertex is (-1 to 1) and
     * the line's half width in target pixels, from which the shader works
     * out how much of each pixel the line covers (0 and 1 for everything
     * else, which covers it fully).
     */
    static final int STRIDE = 20;

    /** Ints per vertex. */
    private static final int INTS = STRIDE / 4;

    /*
     * The vertices as their float and colour bits, written here and copied to
     * the card's buffer in one go: putting each float into a direct buffer
     * checks the buffer every time and took a third of a frame.
     */
    private int[] vertices = new int[INTS * 65536];
    private int vertexCount;

    private int[] drawKind = new int[256];
    private int[] drawStart = new int[256];
    private int[] drawCount = new int[256];
    private int[] drawGlow = new int[256];
    /** The split-screen pane each draw is clipped to (its number in {@link #panes}), -1 for none. */
    private int[] drawPane = new int[256];
    private int draws;
    private int open;

    /* split-screen panes: x, y, width and height in the picture, four ints each */
    private int[] panes = new int[8];
    private int paneCount;
    /** The pane being built (see SceneList.PANE), -1 for none, and where its top left is in the picture. */
    private int pane;
    private float paneX;
    private float paneY;

    private float scaleX;
    private float scaleY;
    /** Target pixels per picture pixel: lines are drawn this thick. */
    private float pixelScale = 1F;
    /* the corners of the polygon being built */
    private float[] px = new float[16];
    private float[] py = new float[16];
    private int abgr;
    /** How much the polygons being built glow, see {@link SceneList#GLOW}. */
    private int glow;
    private int evenOddPolygons;
    private int antialiased;

    int vertexCount() {
        return vertexCount;
    }

    /** Copies the vertices to {@code to}, which holds {@link #vertexCount} of them. */
    void copyVertices(ByteBuffer to) {
        to.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(vertices, 0, vertexCount * INTS);
    }

    int draws() {
        return draws;
    }

    int kind(int draw) {
        return drawKind[draw];
    }

    int start(int draw) {
        return drawStart[draw];
    }

    int count(int draw) {
        return drawCount[draw];
    }

    /** How much the draw's polygons glow, 0 to 255. */
    int glow(int draw) {
        return drawGlow[draw];
    }

    /**
     * The split-screen pane the draw is clipped to, as x, y, width and height
     * in the picture's pixels into {@code into}; false if it is not in one.
     */
    boolean pane(int draw, int[] into) {
        int p = drawPane[draw];
        if (p < 0) {
            return false;
        }
        System.arraycopy(panes, p * 4, into, 0, 4);
        return true;
    }

    /** Polygons in the last frame that needed the stencil. */
    int evenOddPolygons() {
        return evenOddPolygons;
    }

    /** Polygons and outlines in the last frame drawn while Java2D antialiasing was on. */
    int antialiased() {
        return antialiased;
    }

    /**
     * Builds the triangles for a picture of {@code width} x {@code height}
     * drawn into a target {@code pixelScale} times its size. Shapes scale
     * with the target, and one-pixel lines become pixelScale pixels thick.
     * Under the same {@code version} (0 or more) the list holds the same
     * polygons as last time, moved, see {@link #reuseCut}.
     */
    void build(SceneList list, int width, int height, float pixelScale, long version) {
        this.pixelScale = pixelScale;
        if (version < 0L || version != cutVersion) {
            cutVersion = version;
            cutUsed = 0;
            Arrays.fill(cutStart, -1);
        }
        polygon = 0;
        vertexCount = 0;
        draws = 0;
        evenOddPolygons = 0;
        antialiased = 0;
        scaleX = 2F / width;
        scaleY = 2F / height;
        abgr = 0xff000000;
        glow = 0;
        paneCount = 0;
        pane = -1;
        paneX = 0F;
        paneY = 0F;
        boolean antialias = false;
        int[] data = list.data();
        int size = list.size();
        for (int i = 0; i < size;) {
            switch (data[i]) {
                case SceneList.SET_COLOR -> {
                    int rgb = data[i + 1];
                    abgr = 0xff000000 | (rgb & 0xff) << 16 | (rgb & 0xff00) | (rgb >> 16 & 0xff);
                    i += 2;
                }
                case SceneList.ANTIALIAS -> {
                    antialias = data[i + 1] != 0;
                    i += 2;
                }
                case SceneList.TAG -> i += 2;
                case SceneList.GLOW -> {
                    glow = data[i + 1];
                    i += 2;
                }
                case SceneList.PANE -> {
                    if (projecting) {
                        // a kept scene has one camera: each split-screen pane is built on its own (ProjectedScene#build)
                        throw new IllegalStateException("a kept scene cannot hold split-screen panes");
                    }
                    startPane(data[i + 1], data[i + 2], data[i + 3], data[i + 4]);
                    i += 5;
                }
                case SceneList.FILL_RECT -> {
                    int x = data[i + 1];
                    int y = data[i + 2];
                    int w = data[i + 3];
                    int h = data[i + 4];
                    if (w > 0 && h > 0) {
                        begin(PLAIN);
                        if (projecting) {
                            constantQuad(x, y, x + w, y + h);
                        } else {
                            quad(x, y, x + w, y, x + w, y + h, x, y + h);
                        }
                    }
                    i += 5;
                }
                case SceneList.FILL_POLYGON, SceneList.DRAW_POLYGON,
                        SceneList.FILL_POLYGON_EXACT, SceneList.DRAW_POLYGON_EXACT -> {
                    int op = data[i];
                    int n = data[i + 1];
                    boolean exact = op == SceneList.FILL_POLYGON_EXACT || op == SceneList.DRAW_POLYGON_EXACT;
                    corners(data, i + 2, n, exact);
                    if (projecting) {
                        cornerPoints(exact ? i + 2 + 2 * n : -1, n);
                    }
                    if (op == SceneList.FILL_POLYGON || op == SceneList.FILL_POLYGON_EXACT) {
                        fillPolygon(n);
                    } else {
                        outline(n, antialias);
                    }
                    antialiased += antialias ? 1 : 0;
                    i += 2 + (exact ? 4 : 2) * n;
                }
                default -> throw new IllegalStateException("bad scene command " + data[i]);
            }
        }
    }

    /** The shapes from now on are pane {@code x, y, w, h}'s: placed from its top left and clipped to it. */
    private void startPane(int x, int y, int w, int h) {
        if (panes.length < (paneCount + 1) * 4) {
            panes = Arrays.copyOf(panes, panes.length * 2);
        }
        int at = paneCount * 4;
        panes[at] = x;
        panes[at + 1] = y;
        panes[at + 2] = w;
        panes[at + 3] = h;
        pane = paneCount++;
        paneX = x;
        paneY = y;
    }

    /** Loads a polygon's corners into {@link #px}/{@link #py}: the exact ones when it has them. */
    private void corners(int[] data, int at, int n, boolean exact) {
        if (px.length < n) {
            px = new float[n];
            py = new float[n];
        }
        int from = exact ? at + 2 * n : at;
        for (int k = 0; k < n; k++) {
            px[k] = exact ? Float.intBitsToFloat(data[from + k]) : data[from + k];
            py[k] = exact ? Float.intBitsToFloat(data[from + n + k]) : data[from + n + k];
        }
    }

    private void fillPolygon(int n) {
        int which = polygon++;
        if (n < 3) {
            return;
        }
        boolean convex = isConvex(px, py, n);
        if (projecting) {
            if (!convex) {
                if (scanEdges(n) && earClip(which, n) || bandFill(n)) {
                    return; // plain triangles the card places: no stencil, and in the same draw call as the rest
                }
                evenOddPolygons++;
            }
            begin(convex ? PLAIN : EVEN_ODD);
            for (int k = 1; k + 1 < n; k++) {
                cornerVertex(0);
                cornerVertex(k);
                cornerVertex(k + 1);
            }
            return;
        }
        if (!convex) {
            if (scanEdges(n) && (reuseCut(which, n) || earClip(which, n)) || bandFill(n)) {
                return; // plain triangles cover what even-odd would
            }
            evenOddPolygons++;
        }
        begin(convex ? PLAIN : EVEN_ODD);
        float x0 = px[0] + FILL_SHIFT;
        float y0 = py[0] + FILL_SHIFT;
        for (int k = 1; k + 1 < n; k++) {
            vertex(x0, y0);
            vertex(px[k] + FILL_SHIFT, py[k] + FILL_SHIFT);
            vertex(px[k + 1] + FILL_SHIFT, py[k + 1] + FILL_SHIFT);
        }
    }

    /* the polygon's corners still to be cut off, and the triangles cut off so far */
    private int[] ring = new int[16];
    private int[] cut = new int[48];

    /*
     * How the concave polygons of the scene kept under cutVersion were cut:
     * for the n-th filled polygon its n - 2 triangles from cutStart[n] in
     * cuts (as corner numbers), -1 if it was not.
     */
    private long cutVersion = -1L;
    private int polygon;
    private int[] cutStart = new int[1024];
    private int[] cuts = new int[3 * 4096];
    private int cutUsed;

    /**
     * Fills polygon number {@code which}, which does not cross or touch
     * itself ({@link #scanEdges}), with the triangles it was cut into in an
     * earlier frame of the same kept scene, if they still fit: while
     * the polygon does not cross itself and none of the triangles has turned
     * over, together they cover each point inside it exactly once (the
     * outline goes round each such point once, and that is the sum of the
     * triangles around it), so they fill what even-odd would. Checking that
     * takes a pass over the triangles where cutting takes one per corner.
     *
     * @return false if there are none or they no longer fit; nothing has been drawn then
     */
    private boolean reuseCut(int which, int n) {
        if (which >= cutStart.length || cutStart[which] < 0) {
            return false;
        }
        int sign = winding(n);
        if (sign == 0) {
            return false;
        }
        int from = cutStart[which];
        int to = from + 3 * (n - 2);
        for (int t = from; t < to; t += 3) {
            double turn = cross(cuts[t], cuts[t + 1], cuts[t + 2]);
            if (turn != 0D && (turn > 0D ? 1 : -1) != sign) {
                return false; // turned over
            }
        }
        triangles(cuts, from, to);
        return true;
    }

    /** Draws the triangles in {@code t[from..to)} (corner numbers), leaving out flat ones, which cover nothing. */
    private void triangles(int[] t, int from, int to) {
        begin(PLAIN);
        if (projecting) {
            // flat ones too: placed on the card the corners move, and a flat triangle may cover something then
            for (int k = from; k < to; k++) {
                cornerVertex(t[k]);
            }
            return;
        }
        for (int k = from; k < to; k += 3) {
            int a = t[k];
            int b = t[k + 1];
            int c = t[k + 2];
            if (cross(a, b, c) != 0D) {
                vertex(px[a] + FILL_SHIFT, py[a] + FILL_SHIFT);
                vertex(px[b] + FILL_SHIFT, py[b] + FILL_SHIFT);
                vertex(px[c] + FILL_SHIFT, py[c] + FILL_SHIFT);
            }
        }
    }

    /** 1 or -1 for the way the polygon in px/py winds, 0 if it covers nothing. */
    private int winding(int n) {
        double area = 0D;
        for (int k = 0, j = n - 1; k < n; j = k++) {
            area += (double) px[j] * py[k] - (double) px[k] * py[j];
        }
        return area > 0D ? 1 : area < 0D ? -1 : 0;
    }

    /** Keeps the n - 2 triangles in {@link #cut} as how polygon {@code which} was cut. */
    private void keepCut(int which, int n) {
        if (cutVersion < 0L) {
            return; // a scene drawn once
        }
        if (which >= cutStart.length) {
            int old = cutStart.length;
            cutStart = Arrays.copyOf(cutStart, Math.max(which + 1, old * 2));
            Arrays.fill(cutStart, old, cutStart.length, -1);
        }
        int count = 3 * (n - 2);
        if (cutUsed + count > cuts.length) {
            cuts = Arrays.copyOf(cuts, Math.max(cutUsed + count, cuts.length * 2));
        }
        System.arraycopy(cut, 0, cuts, cutUsed, count);
        cutStart[which] = cutUsed;
        cutUsed += count;
    }

    /**
     * Fills a polygon that does not cross or touch itself ({@link #scanEdges})
     * with plain triangles, cut off one corner (an "ear") at a time: what
     * they cover is exactly what the even-odd rule fills, without the stencil
     * and in the same draw call as the plain polygons around it.
     *
     * @return false if rounding left no clean ear; nothing has been drawn then
     */
    private boolean earClip(int which, int n) {
        int sign = winding(n);
        if (sign == 0) {
            return true; // covers nothing
        }
        if (ring.length < n) {
            ring = new int[n];
            cut = new int[3 * n];
        }
        for (int k = 0; k < n; k++) {
            ring[k] = k;
        }
        int m = n;
        int made = 0;
        int k = 0;
        int tried = 0;
        while (m > 3) {
            if (tried > m) {
                return false; // rounding left no clean ear
            }
            int a = ring[(k + m - 1) % m];
            int b = ring[k];
            int c = ring[(k + 1) % m];
            double turn = cross(a, b, c);
            boolean ear = turn == 0D;
            if (!ear && (turn > 0D ? 1 : -1) == sign) {
                ear = true;
                for (int q = 0; q < m && ear; q++) {
                    int v = ring[q];
                    if (v != a && v != b && v != c && inside(v, a, b, c, sign)) {
                        ear = false;
                    }
                }
            }
            if (!ear) {
                k = (k + 1) % m;
                tried++;
                continue;
            }
            // a straight corner is cut off too, as a flat triangle: it covers nothing now but may later
            cut[made++] = a;
            cut[made++] = b;
            cut[made++] = c;
            System.arraycopy(ring, k + 1, ring, k, m - k - 1);
            m--;
            k %= m;
            tried = 0;
        }
        cut[made++] = ring[0];
        cut[made++] = ring[1];
        cut[made++] = ring[2];
        keepCut(which, n);
        triangles(cut, 0, made);
        return true;
    }

    /** Polygons with more corners keep the stencil: cutting one in bands takes a pass over every pair of its edges. */
    private static final int MOST_BAND_CORNERS = 64;

    /*
     * the heights a polygon crossing itself is cut at (levelCount of them,
     * see scanEdges), and the edges across one band in order from the left
     */
    private double[] levels = new double[64];
    private int levelCount;
    private int[] spans = new int[16];
    private double[] spanX = new double[16];

    /**
     * Looks at every pair of the polygon's edges that do not share a corner,
     * once: true if none of them meet, so it has an inside and an outside
     * (as {@link #isSimple}). For a polygon {@link #bandFill} may cut, it
     * also puts the heights it is cut at into {@link #levels}: every
     * corner's, then every height where two edges cross (not just touch).
     * The ear clipper and the bands each took a pass over the pairs of their
     * own, and most polygons that reach this cross themselves.
     */
    private boolean scanEdges(int n) {
        if (n > MOST_BAND_CORNERS) {
            return isSimple(px, py, n); // stops at the first pair that meets
        }
        int most = n + n * (n - 3) / 2;
        if (levels.length < most) {
            levels = new double[most];
        }
        int count = 0;
        for (int k = 0; k < n; k++) {
            levels[count++] = py[k];
        }
        boolean simple = true;
        for (int i = 0; i < n; i++) {
            int i1 = i + 1 < n ? i + 1 : 0;
            for (int j = i + 2; j < n; j++) {
                int j1 = j + 1 < n ? j + 1 : 0;
                if (j1 == i) {
                    continue; // the edge before i: they share corner i
                }
                double sa = side(px[j], py[j], px[j1], py[j1], px[i], py[i]);
                double sb = side(px[j], py[j], px[j1], py[j1], px[i1], py[i1]);
                double sc = side(px[i], py[i], px[i1], py[i1], px[j], py[j]);
                double sd = side(px[i], py[i], px[i1], py[i1], px[j1], py[j1]);
                if (crosses(sa, sb, sc, sd)) {
                    // a cut at that height; edges that only touch meet at a corner, whose height is a cut anyway
                    simple = false;
                    levels[count++] = py[i] + sa / (sa - sb) * ((double) py[i1] - py[i]);
                } else if (simple && touches(sa, sb, sc, sd, px[i], py[i], px[i1], py[i1], px[j], py[j], px[j1], py[j1])) {
                    simple = false;
                }
            }
        }
        levelCount = count;
        return simple;
    }

    /**
     * Fills a polygon that crosses itself with plain triangles, exactly what
     * the even-odd rule fills: cut across at the height of every corner and
     * every crossing, no two edges cross inside a band, so its inside there
     * lies between the first and second edge from the left, the third and
     * fourth and so on, two triangles each. With the stencil each such
     * polygon was two draw calls of its own, and the few hundred a split-screen
     * frame has cost the card several times the rest of the scene. For the
     * card the bands are cut once a tick ({@link #bandsOnCard}): the
     * countdown's scenes had 140-200 such polygons (wheels, faces seen
     * edge-on), and their 400-600 draw calls held the presenter under 250
     * frames a second.
     *
     * @return false if it has too many corners, or, for the card, if it is
     *         flat (the stencil then fills it from its corners in each
     *         frame); nothing has been drawn then
     */
    private boolean bandFill(int n) {
        if (n > MOST_BAND_CORNERS) {
            return false;
        }
        if (spans.length < n) {
            spans = new int[n];
            spanX = new double[n];
        }
        int count = levelCount; // scanEdges put them there
        Arrays.sort(levels, 0, count);
        if (projecting && (float) levels[0] == (float) levels[count - 1]) {
            // flat now, it may not be by the next tick: the stencil fills what its corners cover in each frame
            return false;
        }
        begin(PLAIN);
        if (projecting) {
            bandsOnCard(n, count);
            return true;
        }
        double top = levels[0];
        for (int l = 1; l < count; l++) {
            double bottom = levels[l];
            if ((float) bottom != (float) top) { // a thinner band rounds to nothing
                band(n, top, bottom);
                top = bottom;
            }
        }
        return true;
    }

    /**
     * Fills the band of the polygon between heights {@code top} and
     * {@code bottom}. An edge is across it if it reaches both: no corner lies
     * between them, so every other edge stays out of it, and a line across
     * the band meets an even number of edges, as it meets any closed outline.
     */
    private void band(int n, double top, double bottom) {
        int m = edgesAcross(n, top, bottom);
        float topY = (float) top + FILL_SHIFT;
        float bottomY = (float) bottom + FILL_SHIFT;
        for (int e = 0; e + 1 < m; e += 2) {
            int left = spans[e];
            int right = spans[e + 1];
            int leftNext = left + 1 < n ? left + 1 : 0;
            int rightNext = right + 1 < n ? right + 1 : 0;
            float leftTop = (float) edgeX(left, leftNext, top) + FILL_SHIFT;
            float rightTop = (float) edgeX(right, rightNext, top) + FILL_SHIFT;
            float leftBottom = (float) edgeX(left, leftNext, bottom) + FILL_SHIFT;
            float rightBottom = (float) edgeX(right, rightNext, bottom) + FILL_SHIFT;
            if (leftTop != rightTop) {
                vertex(leftTop, topY);
                vertex(rightTop, topY);
                vertex(rightBottom, bottomY);
            }
            if (leftBottom != rightBottom) {
                vertex(leftTop, topY);
                vertex(rightBottom, bottomY);
                vertex(leftBottom, bottomY);
            }
        }
    }

    /**
     * Puts the edges across the band between {@code top} and {@code bottom}
     * into {@link #spans} in order from the left (edge k runs from corner k
     * to the next); returns how many there are.
     */
    private int edgesAcross(int n, double top, double bottom) {
        double middle = (top + bottom) / 2D;
        int m = 0;
        for (int k = 0; k < n; k++) {
            int next = k + 1 < n ? k + 1 : 0;
            if (Math.min(py[k], py[next]) <= top && Math.max(py[k], py[next]) >= bottom) {
                double x = edgeX(k, next, middle);
                int at = m++;
                for (; at > 0 && spanX[at - 1] > x; at--) {
                    spanX[at] = spanX[at - 1];
                    spans[at] = spans[at - 1];
                }
                spanX[at] = x;
                spans[at] = k;
            }
        }
        return m;
    }

    /*
     * The cuts of the polygon being cut in bands for the card: each cut's
     * height, and its points from the left (lineStart, lineCount) as point
     * codes, a corner's number or n + an edge's for where that edge crosses
     * the cut, with their x for the order and, for an edge's, the share of
     * the way along it. And one band piece's outline, as places in those.
     */
    private double[] lineY = new double[64];
    private int[] lineStart = new int[64];
    private int[] lineCount = new int[64];
    private int[] linePoint = new int[128];
    private double[] lineX = new double[128];
    private float[] lineAlong = new float[128];
    private int[] piece = new int[32];
    /* each edge's lowest and highest corner height */
    private float[] edgeLow = new float[16];
    private float[] edgeHigh = new float[16];

    /**
     * {@link #bandFill}'s bands for the card, which places the corners anew
     * each frame until the next tick. A point where a cut meets an edge is
     * placed there as a share of the way along the edge, so it stays on it.
     * Each band piece runs along its cuts through every point on them
     * between its two edges (the corners and edge crossings of the bands
     * either side), so a piece and its neighbours across a cut share the
     * same points in the same order and meet exactly however far the corners
     * move. Cut at just the two edges' points, a corner of the next band
     * lying between them came off the cut as the corners moved: a crack a
     * frame, a pixel high, across a road whose far corner was thousands of
     * pixels off the screen.
     */
    private void bandsOnCard(int n, int count) {
        if (lineY.length < count) {
            lineY = new double[count];
            lineStart = new int[count];
            lineCount = new int[count];
        }
        int cuts = 0;
        lineY[cuts++] = levels[0];
        for (int l = 1; l < count; l++) {
            if ((float) levels[l] != (float) lineY[cuts - 1]) { // a thinner band rounds to nothing, as on the processor
                lineY[cuts++] = levels[l];
            }
        }
        if (edgeLow.length < n) {
            edgeLow = new float[n];
            edgeHigh = new float[n];
        }
        for (int k = 0; k < n; k++) {
            int next = k + 1 < n ? k + 1 : 0;
            edgeLow[k] = Math.min(py[k], py[next]);
            edgeHigh[k] = Math.max(py[k], py[next]);
        }
        int points = 0;
        for (int c = 0; c < cuts; c++) {
            if (linePoint.length < points + n) { // a cut holds each edge's point, or its first corner's, at most
                linePoint = Arrays.copyOf(linePoint, 2 * (points + n));
                lineX = Arrays.copyOf(lineX, linePoint.length);
                lineAlong = Arrays.copyOf(lineAlong, linePoint.length);
            }
            double y = lineY[c];
            lineStart[c] = points;
            for (int k = 0; k < n; k++) {
                if (py[k] == y) {
                    points = addLinePoint(lineStart[c], points, k, px[k], 0F);
                }
                if (edgeLow[k] < y && edgeHigh[k] > y) {
                    int next = k + 1 < n ? k + 1 : 0;
                    // worked out from the cut's height alone, so every piece on the cut places it at the same spot
                    float along = (float) ((y - py[k]) / ((double) py[next] - py[k]));
                    points = addLinePoint(lineStart[c], points, n + k, edgeX(k, next, y), along);
                }
            }
            lineCount[c] = points - lineStart[c];
        }
        if (piece.length < 2 * n) { // up to n points on each of its two cuts
            piece = new int[2 * n];
        }
        for (int c = 0; c + 1 < cuts; c++) {
            double top = lineY[c];
            double bottom = lineY[c + 1];
            int m = edgesFromCut(n, c, top, bottom);
            for (int e = 0; e + 1 < m; e += 2) {
                int left = spans[e];
                int right = spans[e + 1];
                int size = 0;
                size = addRun(c, pointCode(n, left, top), pointCode(n, right, top), size);
                size = addRun(c + 1, pointCode(n, right, bottom), pointCode(n, left, bottom), size);
                // the piece is convex now (a band's part between two edges, with points along its top and bottom)
                for (int k = 1; k + 1 < size; k++) {
                    pointVertex(n, piece[0]);
                    pointVertex(n, piece[k]);
                    pointVertex(n, piece[k + 1]);
                }
            }
        }
    }

    /**
     * Puts point {@code code} at {@code x} ({@code along} its edge) into the
     * cut whose points start at {@code from}, in order; returns the new end.
     */
    private int addLinePoint(int from, int end, int code, double x, float along) {
        int at = end;
        // left to right, and points at the same place by their codes: one order for every piece on this cut
        for (; at > from && (lineX[at - 1] > x || lineX[at - 1] == x && linePoint[at - 1] > code); at--) {
            lineX[at] = lineX[at - 1];
            linePoint[at] = linePoint[at - 1];
            lineAlong[at] = lineAlong[at - 1];
        }
        lineX[at] = x;
        linePoint[at] = code;
        lineAlong[at] = along;
        return end + 1;
    }

    /**
     * {@link #edgesAcross} for the band below cut {@code c}, from the cut's
     * own points rather than every edge: an edge across the band reaches
     * from its top to its bottom, so it crosses the top cut or has a corner
     * on it. The same edges in the same order (by x halfway down, then by
     * number, as edgesAcross keeps them).
     */
    private int edgesFromCut(int n, int c, double top, double bottom) {
        double middle = (top + bottom) / 2D;
        int m = 0;
        for (int q = lineStart[c], end = q + lineCount[c]; q < end; q++) {
            int code = linePoint[q];
            if (code >= n) {
                m = addEdgeAcross(n, code - n, top, bottom, middle, m);
            } else {
                // a corner's two edges; one along the cut is not across the band, so none is added twice
                m = addEdgeAcross(n, code > 0 ? code - 1 : n - 1, top, bottom, middle, m);
                m = addEdgeAcross(n, code, top, bottom, middle, m);
            }
        }
        return m;
    }

    /** Adds edge k to {@link #spans} if it is across the band; returns how many are there. */
    private int addEdgeAcross(int n, int k, double top, double bottom, double middle, int m) {
        if (!(edgeLow[k] <= top && edgeHigh[k] >= bottom)) {
            return m; // the test edgesAcross makes
        }
        int next = k + 1 < n ? k + 1 : 0;
        double x = edgeX(k, next, middle);
        int at = m;
        for (; at > 0 && (spanX[at - 1] > x || spanX[at - 1] == x && spans[at - 1] > k); at--) {
            spanX[at] = spanX[at - 1];
            spans[at] = spans[at - 1];
        }
        spanX[at] = x;
        spans[at] = k;
        return m + 1;
    }

    /** Edge k's point at height {@code y}, which it reaches: its corner's number at a corner's height, else n + k. */
    private int pointCode(int n, int k, double y) {
        int next = k + 1 < n ? k + 1 : 0;
        return y == py[k] ? k : y == py[next] ? next : n + k;
    }

    /**
     * Adds cut {@code c}'s points from point {@code from} to point {@code to}
     * (both on it) to the piece's outline, as their places on the cut, which
     * holds {@code size}; returns how many it holds now.
     */
    private int addRun(int c, int from, int to, int size) {
        int start = lineStart[c];
        int end = start + lineCount[c];
        int i = start;
        int j = start;
        for (int q = start; q < end; q++) {
            if (linePoint[q] == from) {
                i = q;
            }
            if (linePoint[q] == to) {
                j = q;
            }
        }
        int step = i <= j ? 1 : -1;
        for (int q = i;; q += step) {
            piece[size++] = q;
            if (q == j) {
                return size;
            }
        }
    }

    /** The point at place {@code q} on a cut (see {@link #bandsOnCard}), placed on the card. */
    private void pointVertex(int n, int q) {
        int code = linePoint[q];
        if (code < n) {
            cornerVertex(code);
            return;
        }
        int a = code - n;
        int b = a + 1 < n ? a + 1 : 0;
        projectedVertex(lineAlong[q], 0F, refX[a], refY[a], refX[b], refY[b], ROLE_ALONG);
    }

    /**
     * Where edge a-b is at height y, which it reaches: its corner at a
     * corner's height, so the bands either side of a cut share their corners.
     */
    private double edgeX(int a, int b, double y) {
        if (y == py[a]) {
            return px[a];
        }
        if (y == py[b]) {
            return px[b];
        }
        return px[a] + (y - py[a]) * ((double) px[b] - px[a]) / ((double) py[b] - py[a]);
    }

    /** Which way the path a, b, c turns at b: positive, negative or 0 for straight on. */
    private double cross(int a, int b, int c) {
        return ((double) px[b] - px[a]) * ((double) py[c] - py[b]) - ((double) py[b] - py[a]) * ((double) px[c] - px[b]);
    }

    /** True if corner v lies in or on the triangle a, b, c that winds the way {@code sign} says. */
    private boolean inside(int v, int a, int b, int c, int sign) {
        if (px[v] == px[a] && py[v] == py[a] || px[v] == px[b] && py[v] == py[b]
                || px[v] == px[c] && py[v] == py[c]) {
            return false; // the same point again: not in the way
        }
        return cross(a, b, v) * sign >= 0D && cross(b, c, v) * sign >= 0D && cross(c, a, v) * sign >= 0D;
    }

    /** True if no two edges that do not share a corner meet, so the polygon has an inside and an outside. */
    static boolean isSimple(float[] xs, float[] ys, int n) {
        for (int i = 0; i < n; i++) {
            int i1 = i + 1 < n ? i + 1 : 0;
            for (int j = i + 2; j < n; j++) {
                int j1 = j + 1 < n ? j + 1 : 0;
                if (j1 == i) {
                    continue; // the edge before i: they share corner i
                }
                if (meet(xs[i], ys[i], xs[i1], ys[i1], xs[j], ys[j], xs[j1], ys[j1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if segments a-b and c-d have a point in common. */
    private static boolean meet(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        double d1 = side(cx, cy, dx, dy, ax, ay);
        double d2 = side(cx, cy, dx, dy, bx, by);
        double d3 = side(ax, ay, bx, by, cx, cy);
        double d4 = side(ax, ay, bx, by, dx, dy);
        return crosses(d1, d2, d3, d4) || touches(d1, d2, d3, d4, ax, ay, bx, by, cx, cy, dx, dy);
    }

    /**
     * True if two segments cross, given which side of the second the first's
     * ends are on ({@code d1}, {@code d2}) and the second's of the first
     * ({@code d3}, {@code d4}): each pair strictly either side.
     */
    private static boolean crosses(double d1, double d2, double d3, double d4) {
        return (d1 > 0D && d2 < 0D || d1 < 0D && d2 > 0D) && (d3 > 0D && d4 < 0D || d3 < 0D && d4 > 0D);
    }

    /** True if an end of segment a-b lies on segment c-d or one of c-d on a-b (sides as for {@link #crosses}). */
    private static boolean touches(double d1, double d2, double d3, double d4, float ax, float ay, float bx, float by,
            float cx, float cy, float dx, float dy) {
        return d1 == 0D && within(cx, cy, dx, dy, ax, ay) || d2 == 0D && within(cx, cy, dx, dy, bx, by)
                || d3 == 0D && within(ax, ay, bx, by, cx, cy) || d4 == 0D && within(ax, ay, bx, by, dx, dy);
    }

    private static double side(float ax, float ay, float bx, float by, float px, float py) {
        return ((double) bx - ax) * ((double) py - ay) - ((double) by - ay) * ((double) px - ax);
    }

    /** For p on the line through a and b: true if it is between them. */
    private static boolean within(float ax, float ay, float bx, float by, float px, float py) {
        return Math.min(ax, bx) <= px && px <= Math.max(ax, bx) && Math.min(ay, by) <= py && py <= Math.max(ay, by);
    }

    /**
     * True if the polygon is convex and winds once: every turn goes the same
     * way and its edges change direction at most twice in x and twice in y.
     * Straight runs and repeated points do not count as turns.
     */
    static boolean isConvex(float[] xs, float[] ys, int n) {
        int sign = 0;
        int flipsX = 0;
        int flipsY = 0;
        int lastDx = 0;
        int lastDy = 0;
        int firstDx = 0;
        int firstDy = 0;
        float px = xs[n - 1];
        float py = ys[n - 1];
        float prevEdgeX = 0F;
        float prevEdgeY = 0F;
        // the last real edge before the first point, to measure the first turn
        for (int k = n - 1; k > 0 && prevEdgeX == 0F && prevEdgeY == 0F; k--) {
            prevEdgeX = px - xs[k - 1];
            prevEdgeY = py - ys[k - 1];
        }
        for (int k = 0; k < n; k++) {
            float x = xs[k];
            float y = ys[k];
            float ex = x - px;
            float ey = y - py;
            px = x;
            py = y;
            if (ex == 0F && ey == 0F) {
                continue;
            }
            double cross = (double) prevEdgeX * ey - (double) prevEdgeY * ex;
            if (cross != 0D) {
                int s = cross > 0D ? 1 : -1;
                if (sign == 0) {
                    sign = s;
                } else if (s != sign) {
                    return false;
                }
            }
            int dx = (int) Math.signum(ex);
            int dy = (int) Math.signum(ey);
            if (dx != 0) {
                if (lastDx != 0 && dx != lastDx) {
                    flipsX++;
                }
                if (firstDx == 0) {
                    firstDx = dx;
                }
                lastDx = dx;
            }
            if (dy != 0) {
                if (lastDy != 0 && dy != lastDy) {
                    flipsY++;
                }
                if (firstDy == 0) {
                    firstDy = dy;
                }
                lastDy = dy;
            }
            prevEdgeX = ex;
            prevEdgeY = ey;
        }
        // close the direction changes around the end
        if (lastDx != 0 && firstDx != 0 && lastDx != firstDx) {
            flipsX++;
        }
        if (lastDy != 0 && firstDy != 0 && lastDy != firstDy) {
            flipsY++;
        }
        return flipsX <= 2 && flipsY <= 2;
    }

    private void outline(int n, boolean antialias) {
        if (n <= 0) {
            return;
        }
        begin(PLAIN);
        for (int k = 0; k < n; k++) {
            int next = k + 1 < n ? k + 1 : 0;
            if (projecting) {
                segmentVertices(k, next, antialias ? ROLE_SMOOTH : ROLE_SEGMENT);
                continue;
            }
            if (antialias) {
                smoothSegment(px[k], py[k], px[next], py[next]);
            } else {
                segment(px[k], py[k], px[next], py[next]);
            }
        }
    }

    /**
     * An antialiased one-pixel line as Java2D draws it: through the pixel
     * centres, half a pixel past each end, and fading over the pixel either
     * side, a pixel at distance d from its middle getting 1 - |d| of the colour.
     */
    private void smoothSegment(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0F) {
            segment(x0, y0, x1, y1);
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float sx = x0 + 0.5F - ux * 0.5F;
        float sy = y0 + 0.5F - uy * 0.5F;
        float tx = x1 + 0.5F + ux * 0.5F;
        float ty = y1 + 0.5F + uy * 0.5F;
        // a line pixelScale target pixels wide, fading over one more target pixel on each side
        float halfWidth = pixelScale / 2F + 0.5F;
        float reach = halfWidth / pixelScale;
        float nx = -uy * reach;
        float ny = ux * reach;
        vertex(sx - nx, sy - ny, -1F, halfWidth);
        vertex(tx - nx, ty - ny, -1F, halfWidth);
        vertex(tx + nx, ty + ny, 1F, halfWidth);
        vertex(sx - nx, sy - ny, -1F, halfWidth);
        vertex(tx + nx, ty + ny, 1F, halfWidth);
        vertex(sx + nx, sy + ny, 1F, halfWidth);
    }

    /** A one-pixel line from pixel (x0, y0) to pixel (x1, y1), both ends included. */
    private void segment(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float ax = x0 + 0.5F;
        float ay = y0 + 0.5F;
        float bx = x1 + 0.5F;
        float by = y1 + 0.5F;
        if (dx == 0F && dy == 0F) {
            quad(x0, y0, x0 + 1F, y0, x0 + 1F, y0 + 1F, x0, y0 + 1F);
        } else if (Math.abs(dx) >= Math.abs(dy)) {
            float e = 0.5F / Math.abs(dx);
            float sx = ax - dx * e;
            float sy = ay - dy * e;
            float tx = bx + dx * e;
            float ty = by + dy * e;
            quad(sx, sy - 0.5F, tx, ty - 0.5F, tx, ty + 0.5F, sx, sy + 0.5F);
        } else {
            float e = 0.5F / Math.abs(dy);
            float sx = ax - dx * e;
            float sy = ay - dy * e;
            float tx = bx + dx * e;
            float ty = by + dy * e;
            quad(sx - 0.5F, sy, tx - 0.5F, ty, tx + 0.5F, ty, sx + 0.5F, sy);
        }
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        vertex(x0, y0);
        vertex(x1, y1);
        vertex(x2, y2);
        vertex(x0, y0);
        vertex(x2, y2);
        vertex(x3, y3);
    }

    /**
     * Continues the current draw call if it is plain and so is this, else
     * starts one; every even-odd polygon is a draw call of its own.
     */
    private void begin(int kind) {
        if (kind == PLAIN && draws > 0 && drawKind[draws - 1] == PLAIN && drawGlow[draws - 1] == glow
                && drawPane[draws - 1] == pane) {
            open = draws - 1;
            return;
        }
        if (draws == drawKind.length) {
            drawKind = Arrays.copyOf(drawKind, draws * 2);
            drawStart = Arrays.copyOf(drawStart, draws * 2);
            drawCount = Arrays.copyOf(drawCount, draws * 2);
            drawGlow = Arrays.copyOf(drawGlow, draws * 2);
            drawPane = Arrays.copyOf(drawPane, draws * 2);
        }
        drawKind[draws] = kind;
        drawGlow[draws] = glow;
        drawPane[draws] = pane;
        drawStart[draws] = vertexCount;
        drawCount[draws] = 0;
        open = draws++;
    }

    private void vertex(float x, float y) {
        vertex(x, y, 0F, 1F);
    }

    private void vertex(float x, float y, float across, float halfWidth) {
        int at = vertexCount * INTS;
        if (at + INTS > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        int[] v = vertices;
        v[at] = Float.floatToRawIntBits((x + paneX) * scaleX - 1F);
        v[at + 1] = Float.floatToRawIntBits(1F - (y + paneY) * scaleY);
        v[at + 2] = abgr;
        v[at + 3] = Float.floatToRawIntBits(across);
        v[at + 4] = Float.floatToRawIntBits(halfWidth);
        vertexCount++;
        drawCount[open]++;
    }

    void free() {
        vertices = new int[0];
        projected = new int[0];
    }

    /* ------------------------------------------------ placed on the card */

    /*
     * Roles of a vertex placed on the card (see Shaders.PROJECTED_VS): a
     * polygon's corner, a fixed position, a point a share of the way along
     * the edge between two corners (the share in the position's x), or one
     * of the four corners of a one-pixel line's parallelogram (ROLE_SEGMENT +
     * 0..3), plain or antialiased, which the shader works out from the line's
     * two ends.
     */
    static final int ROLE_FILL = 0;
    static final int ROLE_FIXED = 1;
    static final int ROLE_ALONG = 2;
    static final int ROLE_SEGMENT = 10;
    static final int ROLE_SMOOTH = 20;

    /** Bytes per vertex placed on the card: the fixed position, the colour, four point numbers and the role. */
    static final int PROJECTED_STRIDE = 32;
    private static final int PROJECTED_INTS = PROJECTED_STRIDE / 4;

    /** True while building for the card: vertices name points instead of holding positions. */
    private boolean projecting;
    private ScenePoints scenePoints;
    private int[] projected = new int[0];
    private int listPolygon;
    /* the points the corners of the polygon being built take x and y from */
    private int[] refX = new int[16];
    private int[] refY = new int[16];
    /* points made for values the game worked out itself, after the recording's own */
    private float[] extra = new float[ScenePoints.STRIDE * 1024];
    private int extraCount;

    /**
     * Builds a kept scene's triangles once for all its frames, for the card
     * to place: each vertex names the points its position comes from, and
     * the shader places them for each frame's camera and cars. Polygons are
     * cut into triangles where {@code list}'s corners are now (placed for
     * the frame being drawn): a flat polygon seen from in front keeps how it
     * was cut while it moves, as {@link #reuseCut} relies on too, and one
     * crossing itself is cut in bands whose cuts the card places along the
     * edges ({@link #bandsOnCard}); only a flat one, or one with very many
     * corners, is filled with the stencil, wherever its corners go.
     */
    void buildProjected(SceneList list, ScenePoints points, int width, int height) {
        projecting = true;
        scenePoints = points;
        listPolygon = 0;
        extraCount = 0;
        if (projected.length < PROJECTED_INTS * 65536) {
            projected = new int[PROJECTED_INTS * 65536];
        }
        try {
            build(list, width, height, 1F, -1L);
        } finally {
            projecting = false;
            scenePoints = null;
        }
    }

    /** The points made for fixed values by the last {@link #buildProjected}, ScenePoints.STRIDE floats each. */
    float[] extraPoints() {
        return extra;
    }

    int extraPointCount() {
        return extraCount;
    }

    /** Copies the vertices built for the card to {@code to}, which holds {@link #vertexCount} of them. */
    void copyProjected(ByteBuffer to) {
        to.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(projected, 0, vertexCount * PROJECTED_INTS);
    }

    /**
     * The points the corners of the polygon whose exact corners start at
     * {@code exactAt} in the list (-1 if it has none) come from, into
     * {@link #refX}/{@link #refY}: the recording's, or points made here for
     * corners the game worked out itself.
     */
    private void cornerPoints(int exactAt, int n) {
        if (refX.length < n) {
            refX = new int[n];
            refY = new int[n];
        }
        ScenePoints p = scenePoints;
        int code = -1;
        if (exactAt >= 0) {
            // the recording's polygons are the list's, in order; one drawn while nothing was recorded has none
            for (int q = listPolygon; q < p.polygonCount; q++) {
                int at = p.polygons[q * 3];
                if (at > exactAt) {
                    break;
                }
                if (at == exactAt && p.polygons[q * 3 + 1] == n) {
                    code = p.polygons[q * 3 + 2];
                    listPolygon = q + 1;
                    break;
                }
            }
        }
        for (int k = 0; k < n; k++) {
            int cx = code < 0 ? -1 : p.codes[code + k];
            int cy = code < 0 ? -1 : p.codes[code + n + k];
            if (cx >= 0 && cy >= 0) {
                refX[k] = cx >> 1;
                refY[k] = cy >> 1;
            } else {
                int fixed = fixedPoint(px[k], py[k]);
                refX[k] = cx >= 0 ? cx >> 1 : fixed;
                refY[k] = cy >= 0 ? cy >> 1 : fixed;
            }
        }
    }

    /** A point of its own for a position the game worked out on screen: the number it is drawn by. */
    private int fixedPoint(float x, float y) {
        int at = extraCount * ScenePoints.STRIDE;
        if (at + ScenePoints.STRIDE > extra.length) {
            extra = Arrays.copyOf(extra, extra.length * 2);
        }
        extra[at] = x;
        extra[at + 1] = y;
        return scenePoints.pointCount + extraCount++;
    }

    /** A filled polygon's corner {@code k}, placed on the card. */
    private void cornerVertex(int k) {
        projectedVertex(0F, 0F, refX[k], refY[k], refX[k], refY[k], ROLE_FILL);
    }

    /** The six vertices of the line from corner {@code k} to corner {@code next}, worked out on the card. */
    private void segmentVertices(int k, int next, int role) {
        int ax = refX[k];
        int ay = refY[k];
        int bx = refX[next];
        int by = refY[next];
        projectedVertex(0F, 0F, ax, ay, bx, by, role);
        projectedVertex(0F, 0F, ax, ay, bx, by, role + 1);
        projectedVertex(0F, 0F, ax, ay, bx, by, role + 2);
        projectedVertex(0F, 0F, ax, ay, bx, by, role);
        projectedVertex(0F, 0F, ax, ay, bx, by, role + 2);
        projectedVertex(0F, 0F, ax, ay, bx, by, role + 3);
    }

    /** A rectangle at fixed picture coordinates. */
    private void constantQuad(float x0, float y0, float x1, float y1) {
        projectedVertex(x0, y0, 0, 0, 0, 0, ROLE_FIXED);
        projectedVertex(x1, y0, 0, 0, 0, 0, ROLE_FIXED);
        projectedVertex(x1, y1, 0, 0, 0, 0, ROLE_FIXED);
        projectedVertex(x0, y0, 0, 0, 0, 0, ROLE_FIXED);
        projectedVertex(x1, y1, 0, 0, 0, 0, ROLE_FIXED);
        projectedVertex(x0, y1, 0, 0, 0, 0, ROLE_FIXED);
    }

    private void projectedVertex(float x, float y, int ax, int ay, int bx, int by, int role) {
        int at = vertexCount * PROJECTED_INTS;
        if (at + PROJECTED_INTS > projected.length) {
            projected = Arrays.copyOf(projected, projected.length * 2);
        }
        int[] v = projected;
        v[at] = Float.floatToRawIntBits(x);
        v[at + 1] = Float.floatToRawIntBits(y);
        v[at + 2] = abgr;
        // point numbers as floats: exact up to 16 million
        v[at + 3] = Float.floatToRawIntBits(ax);
        v[at + 4] = Float.floatToRawIntBits(ay);
        v[at + 5] = Float.floatToRawIntBits(bx);
        v[at + 6] = Float.floatToRawIntBits(by);
        v[at + 7] = Float.floatToRawIntBits(role);
        vertexCount++;
        drawCount[open]++;
    }
}
