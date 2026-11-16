package nfm.lit;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * A graphics context that draws nothing.
 * <p>
 * Drawing the world has side effects the race depends on (see
 * {@code GameSparker.drawSimulationStep}), so every tick runs the drawing code
 * once without putting anything on screen. Through a real context clipped to
 * nothing, each polygon still went through Java2D's validation and span setup,
 * and every antialiasing switch forced the pipeline to revalidate. The world's
 * drawing code only ever writes to its context, so this one accepts every call
 * and does nothing. The few getters keep what was set, so a caller that reads a
 * value back gets what a real context would have given it.
 */
class NullGraphics extends Graphics2D {

    private Color color = Color.BLACK;
    private Color background = Color.BLACK;
    private Paint paint = Color.BLACK;
    private Font font = new Font(Font.DIALOG, Font.PLAIN, 12);
    private Stroke stroke = new BasicStroke();
    private Composite composite = AlphaComposite.SrcOver;
    private final RenderingHints hints = new RenderingHints(null);
    private AffineTransform transform = new AffineTransform();
    private Shape clip;

    @Override
    public Graphics create() {
        return new NullGraphics();
    }

    @Override
    public void dispose() {
    }

    /* ----------------------------------------------------- state, kept */

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public void setColor(Color c) {
        if (c != null) {
            color = c;
            paint = c;
        }
    }

    @Override
    public Paint getPaint() {
        return paint;
    }

    @Override
    public void setPaint(Paint p) {
        if (p != null) {
            paint = p;
            if (p instanceof Color c) {
                color = c;
            }
        }
    }

    @Override
    public Color getBackground() {
        return background;
    }

    @Override
    public void setBackground(Color c) {
        background = c;
    }

    @Override
    public Font getFont() {
        return font;
    }

    @Override
    public void setFont(Font f) {
        if (f != null) {
            font = f;
        }
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return new FontMetrics(f) {
        };
    }

    @Override
    public Stroke getStroke() {
        return stroke;
    }

    @Override
    public void setStroke(Stroke s) {
        stroke = s;
    }

    @Override
    public Composite getComposite() {
        return composite;
    }

    @Override
    public void setComposite(Composite c) {
        composite = c;
    }

    @Override
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        hints.put(key, value);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key key) {
        return hints.get(key);
    }

    @Override
    public void setRenderingHints(Map<?, ?> map) {
        hints.clear();
        hints.putAll(map);
    }

    @Override
    public void addRenderingHints(Map<?, ?> map) {
        hints.putAll(map);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return (RenderingHints) hints.clone();
    }

    @Override
    public AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    @Override
    public void setTransform(AffineTransform t) {
        transform = new AffineTransform(t);
    }

    @Override
    public void transform(AffineTransform t) {
        transform.concatenate(t);
    }

    @Override
    public void translate(int x, int y) {
        transform.translate(x, y);
    }

    @Override
    public void translate(double x, double y) {
        transform.translate(x, y);
    }

    @Override
    public void rotate(double theta) {
        transform.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        transform.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        transform.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        transform.shear(shx, shy);
    }

    @Override
    public Shape getClip() {
        return clip;
    }

    @Override
    public Rectangle getClipBounds() {
        return clip == null ? null : clip.getBounds();
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        clip = new Rectangle(x, y, width, height);
    }

    @Override
    public void setClip(Shape s) {
        clip = s;
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        clip = clip == null ? new Rectangle(x, y, width, height)
                : clip.getBounds().intersection(new Rectangle(x, y, width, height));
    }

    @Override
    public void clip(Shape s) {
        clip = s == null ? null : clip == null ? s : clip.getBounds().intersection(s.getBounds());
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return new FontRenderContext(null, false, false);
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return null;
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return false;
    }

    @Override
    public void setPaintMode() {
    }

    @Override
    public void setXORMode(Color c) {
    }

    /* ----------------------------------------------------- drawing, ignored */

    @Override
    public void draw(Shape s) {
    }

    @Override
    public void fill(Shape s) {
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
    }

    @Override
    public void fillRect(int x, int y, int width, int height) {
    }

    @Override
    public void clearRect(int x, int y, int width, int height) {
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
    }

    @Override
    public void drawString(String str, int x, int y) {
    }

    @Override
    public void drawString(String str, float x, float y) {
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return true;
    }

    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
    }

    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
    }

    @Override
    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            ImageObserver observer) {
        return true;
    }

    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
            Color bgcolor, ImageObserver observer) {
        return true;
    }
}
