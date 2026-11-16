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

    /** Starts recording a frame that would have been drawn into {@code target}. */
    SceneRecorder begin(Graphics2D target) {
        this.target = target;
        list.clear();
        unsupported = false;
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

    /** Draws the recorded frame into {@code g}, exactly as the game would have. */
    void replay(Graphics2D g) {
        if (startColor != null) {
            g.setColor(startColor);
        }
        if (startAntialias != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, startAntialias);
        }
        int[] data = list.data();
        int size = list.size();
        int[] xs = new int[16];
        int[] ys = new int[16];
        for (int i = 0; i < size;) {
            switch (data[i]) {
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
                case SceneList.FILL_POLYGON, SceneList.DRAW_POLYGON -> {
                    int n = data[i + 1];
                    if (xs.length < n) {
                        xs = new int[n];
                        ys = new int[n];
                    }
                    System.arraycopy(data, i + 2, xs, 0, n);
                    System.arraycopy(data, i + 2 + n, ys, 0, n);
                    if (data[i] == SceneList.FILL_POLYGON) {
                        g.fillPolygon(xs, ys, n);
                    } else {
                        g.drawPolygon(xs, ys, n);
                    }
                    i += 2 + 2 * n;
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

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        list.polygon(SceneList.FILL_POLYGON, xPoints, yPoints, nPoints);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        list.polygon(SceneList.DRAW_POLYGON, xPoints, yPoints, nPoints);
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
