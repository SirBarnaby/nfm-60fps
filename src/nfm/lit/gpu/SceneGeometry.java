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
     * Bytes per vertex: x and y as floats, the colour as RGBA bytes, and the
     * distance from the middle of an antialiased line in pixels (0 for
     * everything else), from which the shader works out how much of the
     * pixel the line covers.
     */
    static final int STRIDE = 16;

    private ByteBuffer vertices = MemoryUtil.memAlloc(STRIDE * 65536).order(ByteOrder.LITTLE_ENDIAN);
    private int vertexCount;

    private int[] drawKind = new int[256];
    private int[] drawStart = new int[256];
    private int[] drawCount = new int[256];
    private int draws;
    private int open;

    private float scaleX;
    private float scaleY;
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

    /** Builds the triangles for a picture of {@code width} x {@code height}. */
    void build(SceneList list, int width, int height) {
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
                case SceneList.FILL_POLYGON -> {
                    int n = data[i + 1];
                    fillPolygon(data, i + 2, i + 2 + n, n);
                    antialiased += antialias ? 1 : 0;
                    i += 2 + 2 * n;
                }
                case SceneList.DRAW_POLYGON -> {
                    int n = data[i + 1];
                    outline(data, i + 2, i + 2 + n, n, antialias);
                    antialiased += antialias ? 1 : 0;
                    i += 2 + 2 * n;
                }
                default -> throw new IllegalStateException("bad scene command " + data[i]);
            }
        }
    }

    private void fillPolygon(int[] data, int xs, int ys, int n) {
        if (n < 3) {
            return;
        }
        boolean convex = isConvex(data, xs, ys, n);
        if (!convex) {
            evenOddPolygons++;
        }
        begin(convex ? PLAIN : EVEN_ODD);
        float x0 = data[xs] + FILL_SHIFT;
        float y0 = data[ys] + FILL_SHIFT;
        for (int k = 1; k + 1 < n; k++) {
            vertex(x0, y0);
            vertex(data[xs + k] + FILL_SHIFT, data[ys + k] + FILL_SHIFT);
            vertex(data[xs + k + 1] + FILL_SHIFT, data[ys + k + 1] + FILL_SHIFT);
        }
    }

    /**
     * True if the polygon is convex and winds once: every turn goes the same
     * way and its edges change direction at most twice in x and twice in y.
     * Straight runs and repeated points do not count as turns.
     */
    static boolean isConvex(int[] data, int xs, int ys, int n) {
        int sign = 0;
        int flipsX = 0;
        int flipsY = 0;
        int lastDx = 0;
        int lastDy = 0;
        int firstDx = 0;
        int firstDy = 0;
        int px = data[xs + n - 1];
        int py = data[ys + n - 1];
        int prevEdgeX = 0;
        int prevEdgeY = 0;
        // the last real edge before the first point, to measure the first turn
        for (int k = n - 1; k > 0 && prevEdgeX == 0 && prevEdgeY == 0; k--) {
            prevEdgeX = px - data[xs + k - 1];
            prevEdgeY = py - data[ys + k - 1];
        }
        for (int k = 0; k < n; k++) {
            int x = data[xs + k];
            int y = data[ys + k];
            int ex = x - px;
            int ey = y - py;
            px = x;
            py = y;
            if (ex == 0 && ey == 0) {
                continue;
            }
            long cross = (long) prevEdgeX * ey - (long) prevEdgeY * ex;
            if (cross != 0) {
                int s = cross > 0 ? 1 : -1;
                if (sign == 0) {
                    sign = s;
                } else if (s != sign) {
                    return false;
                }
            }
            int dx = Integer.signum(ex);
            int dy = Integer.signum(ey);
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

    private void outline(int[] data, int xs, int ys, int n, boolean antialias) {
        if (n <= 0) {
            return;
        }
        begin(PLAIN);
        for (int k = 0; k < n; k++) {
            int next = k + 1 < n ? k + 1 : 0;
            if (antialias) {
                smoothSegment(data[xs + k], data[ys + k], data[xs + next], data[ys + next]);
            } else {
                segment(data[xs + k], data[ys + k], data[xs + next], data[ys + next]);
            }
        }
    }

    /**
     * An antialiased one-pixel line as Java2D draws it: through the pixel
     * centres, half a pixel past each end, and fading over the pixel either
     * side, a pixel at distance d from its middle getting 1 - |d| of the colour.
     */
    private void smoothSegment(int x0, int y0, int x1, int y1) {
        float dx = (float) x1 - x0;
        float dy = (float) y1 - y0;
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
        float nx = -uy;
        float ny = ux;
        vertex(sx - nx, sy - ny, -1F);
        vertex(tx - nx, ty - ny, -1F);
        vertex(tx + nx, ty + ny, 1F);
        vertex(sx - nx, sy - ny, -1F);
        vertex(tx + nx, ty + ny, 1F);
        vertex(sx + nx, sy + ny, 1F);
    }

    /** A one-pixel line from pixel (x0, y0) to pixel (x1, y1), both ends included. */
    private void segment(int x0, int y0, int x1, int y1) {
        float dx = (float) x1 - x0;
        float dy = (float) y1 - y0;
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
        vertex(x, y, 0F);
    }

    private void vertex(float x, float y, float edge) {
        if ((vertexCount + 1) * STRIDE > vertices.capacity()) {
            vertices = MemoryUtil.memRealloc(vertices, vertices.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
        }
        int at = vertexCount * STRIDE;
        vertices.putFloat(at, x * scaleX - 1F);
        vertices.putFloat(at + 4, 1F - y * scaleY);
        vertices.putInt(at + 8, abgr);
        vertices.putFloat(at + 12, edge);
        vertexCount++;
        drawCount[open]++;
    }

    void free() {
        MemoryUtil.memFree(vertices);
    }
}
