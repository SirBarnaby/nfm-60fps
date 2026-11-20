package nfm.lit;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import nfm.lit.gpu.SceneList;

/**
 * Records the 3D scene of a frame instead of drawing it, so the graphics
 * card can draw it ({@link nfm.lit.gpu.GpuDisplay#presentScene}).
 * <p>
 * The world's drawing code only sets colours and antialiasing and fills or
 * outlines polygons and rectangles. Colour and antialiasing changes also go
 * to the real back buffer's context, so it is left in exactly the state the
 * software renderer would have left it in, and {@link #replay} can later
 * draw the same frame into the back buffer pixel for pixel. Any other kind
 * of drawing marks the frame unsupported, and the caller then draws it in
 * software.
 */
final class SceneRecorder extends NullGraphics {

    private final SceneList list = new SceneList();
    private Graphics2D target;
    private Color startColor;
    private Object startAntialias;
    private boolean unsupported;
    /* exact corners for the next polygon drawn at boundX/boundY, see bind */
    private int[] boundX;
    private int[] boundY;
    private int[] boundCopyX = new int[16];
    private int[] boundCopyY = new int[16];
    private float[] exactX = new float[16];
    private float[] exactY = new float[16];
    private int boundN = -1;
    /* while a scene is recorded for a whole tick, the scene its points go to */
    private RetainedScene retained;
    private float[] screenX = new float[16];
    private float[] screenY = new float[16];
    /** The glow of the polygons being recorded, see {@link #glow}. */
    private int glow;

    /** Starts recording a frame that would have been drawn into {@code target}. */
    SceneRecorder begin(Graphics2D target) {
        this.target = target;
        list.clear();
        unsupported = false;
        unbind();
        glow = 0;
        startColor = target.getColor();
        startAntialias = target.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        super.setColor(startColor);
        return this;
    }

    SceneList list() {
        return list;
    }

    /** True if the frame used drawing the graphics card path does not do. */
    boolean unsupported() {
        return unsupported;
    }

    /**
     * The drawing from now on is split-screen pane {@code area} of the
     * picture's, in the pane's own coordinates, as software draws a pane
     * (translated to its corner and clipped to it).
     */
    void pane(java.awt.Rectangle area) {
        list.pane(area.x, area.y, area.width, area.height);
    }

    /** Draws the recorded frame into {@code g}, exactly as the game would have. */
    void replay(Graphics2D g) {
        if (startColor != null) {
            g.setColor(startColor);
        }
        if (startAntialias != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, startAntialias);
        }
        AffineTransform base = g.getTransform();
        Shape baseClip = g.getClip();
        try {
            replayCommands(g, base);
        } finally {
            g.setTransform(base);
            g.setClip(baseClip);
        }
    }

    private void replayCommands(Graphics2D g, AffineTransform base) {
        int[] data = list.data();
        int size = list.size();
        int[] xs = new int[16];
        int[] ys = new int[16];
        for (int i = 0; i < size;) {
            switch (data[i]) {
                case SceneList.PANE -> {
                    g.setTransform(base);
                    g.translate(data[i + 1], data[i + 2]);
                    g.setClip(0, 0, data[i + 3], data[i + 4]);
                    i += 5;
                }
                case SceneList.SET_COLOR -> {
                    g.setColor(new Color(data[i + 1]));
                    i += 2;
                }
                case SceneList.ANTIALIAS -> {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, data[i + 1] != 0
                            ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                    i += 2;
                }
                case SceneList.FILL_RECT -> {
                    g.fillRect(data[i + 1], data[i + 2], data[i + 3], data[i + 4]);
                    i += 5;
                }
                case SceneList.TAG, SceneList.GLOW -> i += 2;
                case SceneList.FILL_POLYGON, SceneList.DRAW_POLYGON,
                        SceneList.FILL_POLYGON_EXACT, SceneList.DRAW_POLYGON_EXACT -> {
                    int op = data[i];
                    int n = data[i + 1];
                    if (xs.length < n) {
                        xs = new int[n];
                        ys = new int[n];
                    }
                    System.arraycopy(data, i + 2, xs, 0, n);
                    System.arraycopy(data, i + 2 + n, ys, 0, n);
                    if (op == SceneList.FILL_POLYGON || op == SceneList.FILL_POLYGON_EXACT) {
                        g.fillPolygon(xs, ys, n);
                    } else {
                        g.drawPolygon(xs, ys, n);
                    }
                    // software draws the whole pixels; the exact corners are for the graphics card
                    boolean exact = op == SceneList.FILL_POLYGON_EXACT || op == SceneList.DRAW_POLYGON_EXACT;
                    i += 2 + (exact ? 4 : 2) * n;
                }
                default -> throw new IllegalStateException("bad scene command " + data[i]);
            }
        }
    }

    /* ---------------------------------------------------- what is recorded */

    @Override
    public void setColor(Color c) {
        if (c != null) {
            super.setColor(c);
            target.setColor(c);
            list.setColor(c.getRGB());
            if (c.getAlpha() != 255) {
                unsupported = true; // the scene is opaque; translucency would need blending
            }
        }
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        super.setRenderingHint(key, value);
        target.setRenderingHint(key, value);
        if (key == RenderingHints.KEY_ANTIALIASING) {
            list.antialias(value == RenderingHints.VALUE_ANTIALIAS_ON);
        }
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return target.getRenderingHint(key);
    }

    /**
     * Gives the exact corners {@code ex/ey} of the polygon the game is about
     * to draw at whole pixels {@code xs/ys}, which already hold them. Every
     * fill or outline of that same polygon, the same arrays holding the
     * same corners, is then recorded with them, until {@link #unbind}.
     */
    void bind(int[] xs, int[] ys, float[] ex, float[] ey, int n) {
        if (boundCopyX.length < n) {
            boundCopyX = new int[n];
            boundCopyY = new int[n];
            exactX = new float[n];
            exactY = new float[n];
        }
        boundX = xs;
        boundY = ys;
        boundN = n;
        System.arraycopy(ex, 0, exactX, 0, n);
        System.arraycopy(ey, 0, exactY, 0, n);
        System.arraycopy(xs, 0, boundCopyX, 0, n);
        System.arraycopy(ys, 0, boundCopyY, 0, n);
    }

    /**
     * Records the polygons from now on for a scene kept for a whole tick:
     * every corner as a point of {@code scene} (see {@link Precise}), null
     * to stop.
     */
    void retain(RetainedScene scene) {
        retained = scene;
    }

    /** Set by tests that follow each polygon from frame to frame, see {@link #tag}. */
    static boolean tagging;

    /** When {@link #tagging}, marks the next polygons as drawn by {@code owner} in pass {@code kind}. */
    void tag(Object owner, int kind) {
        if (tagging) {
            list.tag(System.identityHashCode(owner) * 4 + kind);
        }
    }

    /** When {@link #tagging}, marks the next polygons as scenery number {@code id}. */
    void tag(int id) {
        if (tagging) {
            list.tag(-1 - id);
        }
    }

    /**
     * How much the polygons from now on glow, 0 (not at all) to 255; only
     * an HDR display shows it, software draws them as before.
     */
    void glow(int level) {
        if (level != glow) {
            glow = level;
            list.glow(level);
        }
    }

    /** Forgets the exact corners: the next polygons are recorded at whole pixels. */
    void unbind() {
        boundX = null;
        boundY = null;
        boundN = -1;
    }

    /** True if {@code xs/ys} is the bound polygon, still holding the corners it had when bound. */
    private boolean isBound(int[] xs, int[] ys, int n) {
        if (xs != boundX || ys != boundY || n != boundN) {
            return false;
        }
        return java.util.Arrays.equals(xs, 0, n, boundCopyX, 0, n)
                && java.util.Arrays.equals(ys, 0, n, boundCopyY, 0, n);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        polygon(SceneList.FILL_POLYGON, xPoints, yPoints, nPoints);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        polygon(SceneList.DRAW_POLYGON, xPoints, yPoints, nPoints);
    }

    private void polygon(int op, int[] xPoints, int[] yPoints, int nPoints) {
        if (retained != null) {
            retainedPolygon(op, xPoints, yPoints, nPoints);
            return;
        }
        if (isBound(xPoints, yPoints, nPoints)) {
            list.polygon(op, xPoints, yPoints, nPoints, exactX, exactY);
        } else {
            list.polygon(op, xPoints, yPoints, nPoints);
        }
    }

    private void retainedPolygon(int op, int[] xPoints, int[] yPoints, int nPoints) {
        int n = Math.max(0, Math.min(nPoints, Math.min(xPoints.length, yPoints.length)));
        float[] ex = exactX;
        float[] ey = exactY;
        if (!isBound(xPoints, yPoints, nPoints)) {
            if (screenX.length < n) {
                screenX = new float[n];
                screenY = new float[n];
            }
            Precise.screen(xPoints, yPoints, n, screenX, screenY);
            ex = screenX;
            ey = screenY;
        }
        int at = list.size() + 2 + 2 * n;
        list.polygon(op, xPoints, yPoints, n, ex, ey);
        retained.polygon(at, n, ex, ey);
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
        list.fillRect(x, y, width, height);
    }

    /* ------------------------------- anything else: draw this frame in software */

    @Override
    public void draw(Shape s) {
        unsupported = true;
    }

    @Override
    public void fill(Shape s) {
        unsupported = true;
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        unsupported = true;
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        unsupported = true;
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
        unsupported = true;
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        unsupported = true;
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        unsupported = true;
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        unsupported = true;
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        unsupported = true;
    }

    @Override
    public void drawString(String str, int x, int y) {
        unsupported = true;
    }

    @Override
    public void drawString(String str, float x, float y) {
        unsupported = true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        unsupported = true;
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        unsupported = true;
        return true;
    }

    @Override
    public void translate(int x, int y) {
        unsupported = true;
    }

    @Override
    public void setTransform(AffineTransform t) {
        unsupported = true;
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        unsupported = true;
    }

    @Override
    public void setClip(Shape s) {
        unsupported = true;
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        unsupported = true;
    }
}
