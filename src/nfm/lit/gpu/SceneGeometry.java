package nfm.lit.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.lwjgl.system.MemoryUtil;

/**
 * Turns a {@link SceneList} into triangles for the graphics card, keeping the
 * game's painter's order.
 * <p>
 * Convex polygons, rectangles and outlines are plain triangles and are
 * gathered into as few draw calls as possible: the card draws the triangles
 * of one call in order, so later ones still cover earlier ones. Any other
 * polygon (concave or crossing itself) is filled like Java2D's even-odd rule
 * with the stencil buffer: its fan of triangles flips the stencil, and a
 * second pass colours where it ended up odd.
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

    private ByteBuffer vertices = MemoryUtil.memAlloc(STRIDE * 65536).order(ByteOrder.LITTLE_ENDIAN);
    private int vertexCount;

    private int[] drawKind = new int[256];
    private int[] drawStart = new int[256];
    private int[] drawCount = new int[256];
    private int draws;
    private int open;

    private float scaleX;
    private float scaleY;
    /** Target pixels per picture pixel: lines are drawn this thick. */
    private float pixelScale = 1F;
    /* the corners of the polygon being built */
    private float[] px = new float[16];
    private float[] py = new float[16];
    private int abgr;
    private int evenOddPolygons;
    private int antialiased;

    int vertexCount() {
        return vertexCount;
    }

    ByteBuffer vertices() {
        return vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(0).limit(vertexCount * STRIDE);
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
     */
    void build(SceneList list, int width, int height, float pixelScale) {
        this.pixelScale = pixelScale;
        vertexCount = 0;
        draws = 0;
        evenOddPolygons = 0;
        antialiased = 0;
        scaleX = 2F / width;
        scaleY = 2F / height;
        abgr = 0xff000000;
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
                case SceneList.FILL_RECT -> {
                    int x = data[i + 1];
                    int y = data[i + 2];
                    int w = data[i + 3];
                    int h = data[i + 4];
                    if (w > 0 && h > 0) {
                        begin(PLAIN);
                        quad(x, y, x + w, y, x + w, y + h, x, y + h);
                    }
                    i += 5;
                }
                case SceneList.FILL_POLYGON, SceneList.DRAW_POLYGON,
                        SceneList.FILL_POLYGON_EXACT, SceneList.DRAW_POLYGON_EXACT -> {
                    int op = data[i];
                    int n = data[i + 1];
                    boolean exact = op == SceneList.FILL_POLYGON_EXACT || op == SceneList.DRAW_POLYGON_EXACT;
                    corners(data, i + 2, n, exact);
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
        if (n < 3) {
            return;
        }
        boolean convex = isConvex(px, py, n);
        if (!convex && earClip(n)) {
            return; // concave but not crossing itself: plain triangles cover what even-odd would
        }
        if (!convex) {
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

    /**
     * Fills a polygon that does not cross itself with plain triangles, cut
     * off one corner (an "ear") at a time: what they cover is exactly what
     * the even-odd rule fills, without the stencil and in the same draw
     * call as the plain polygons around it.
     *
     * @return false if the polygon crosses or touches itself, or no ear was
     *         found; nothing has been drawn then
     */
    private boolean earClip(int n) {
        if (!isSimple(px, py, n)) {
            return false;
        }
        double area = 0D;
        for (int k = 0, j = n - 1; k < n; j = k++) {
            area += (double) px[j] * py[k] - (double) px[k] * py[j];
        }
        if (area == 0D) {
            return true; // covers nothing
        }
        int sign = area > 0D ? 1 : -1;
        if (ring.length < n) {
            ring = new int[n];
            cut = new int[3 * n];
        }
        for (int k = 0; k < n; k++) {
            ring[k] = k;
        }
        int m = n;
        int cuts = 0;
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
            if (turn != 0D) { // a straight corner is just dropped
                cut[cuts++] = a;
                cut[cuts++] = b;
                cut[cuts++] = c;
            }
            System.arraycopy(ring, k + 1, ring, k, m - k - 1);
            m--;
            k %= m;
            tried = 0;
        }
        cut[cuts++] = ring[0];
        cut[cuts++] = ring[1];
        cut[cuts++] = ring[2];
        begin(PLAIN);
        for (int t = 0; t < cuts; t++) {
            vertex(px[cut[t]] + FILL_SHIFT, py[cut[t]] + FILL_SHIFT);
        }
        return true;
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
        if ((d1 > 0D && d2 < 0D || d1 < 0D && d2 > 0D) && (d3 > 0D && d4 < 0D || d3 < 0D && d4 > 0D)) {
            return true;
        }
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
        if (kind == PLAIN && draws > 0 && drawKind[draws - 1] == PLAIN) {
            open = draws - 1;
            return;
        }
        if (draws == drawKind.length) {
            drawKind = Arrays.copyOf(drawKind, draws * 2);
            drawStart = Arrays.copyOf(drawStart, draws * 2);
            drawCount = Arrays.copyOf(drawCount, draws * 2);
        }
        drawKind[draws] = kind;
        drawStart[draws] = vertexCount;
        drawCount[draws] = 0;
        open = draws++;
    }

    private void vertex(float x, float y) {
        vertex(x, y, 0F, 1F);
    }

    private void vertex(float x, float y, float across, float halfWidth) {
        if ((vertexCount + 1) * STRIDE > vertices.capacity()) {
            vertices = MemoryUtil.memRealloc(vertices, vertices.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
        }
        int at = vertexCount * STRIDE;
        vertices.putFloat(at, x * scaleX - 1F);
        vertices.putFloat(at + 4, 1F - y * scaleY);
        vertices.putInt(at + 8, abgr);
        vertices.putFloat(at + 12, across);
        vertices.putFloat(at + 16, halfWidth);
        vertexCount++;
        drawCount[open]++;
    }

    void free() {
        MemoryUtil.memFree(vertices);
    }
}
