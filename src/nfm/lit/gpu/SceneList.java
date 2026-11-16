package nfm.lit.gpu;

import java.util.Arrays;

/**
 * The drawing of one frame's 3D scene as a list of commands: colours,
 * filled and outlined polygons, rectangles and antialiasing switches, in the
 * order the game issued them. The list can be drawn by the graphics card
 * ({@link GpuDisplay#presentScene}) or replayed into Java2D unchanged.
 * <p>
 * Stored flat in one int array, each command an opcode followed by its
 * operands, so recording a frame allocates nothing once the array has grown.
 */
public final class SceneList {

    /** rgb (0xRRGGBB, always opaque) */
    public static final int SET_COLOR = 1;
    /** n, x[0..n), y[0..n): filled with the even-odd rule, as Graphics.fillPolygon */
    public static final int FILL_POLYGON = 2;
    /** n, x[0..n), y[0..n): closed one-pixel outline, as Graphics.drawPolygon */
    public static final int DRAW_POLYGON = 3;
    /** x, y, width, height */
    public static final int FILL_RECT = 4;
    /** 1 = antialiasing on, 0 = off */
    public static final int ANTIALIAS = 5;
    /** As FILL_POLYGON, followed by the exact corners as float bits, x then y. */
    public static final int FILL_POLYGON_EXACT = 6;
    /** As DRAW_POLYGON, followed by the exact corners as float bits, x then y. */
    public static final int DRAW_POLYGON_EXACT = 7;
    /** Names what the next polygons belong to; only written for tests, drawn as nothing. */
    public static final int TAG = 8;
    /**
     * level 0-255: how much the next polygons glow, as a flame, a lamp or
     * the sky in a lightning flash does; drawn brighter than white on an HDR
     * display, and as nothing anywhere else.
     */
    public static final int GLOW = 9;

    private int[] data = new int[1 << 16];
    private int size;

    /** Empties the list for a new frame. */
    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    /** The commands, valid up to {@link #size()}. */
    public int[] data() {
        return data;
    }

    public void setColor(int rgb) {
        ensure(2);
        data[size++] = SET_COLOR;
        data[size++] = rgb & 0xffffff;
    }

    public void antialias(boolean on) {
        ensure(2);
        data[size++] = ANTIALIAS;
        data[size++] = on ? 1 : 0;
    }

    public void polygon(int op, int[] xs, int[] ys, int n) {
        n = Math.max(0, Math.min(n, Math.min(xs.length, ys.length)));
        ensure(2 + 2 * n);
        data[size++] = op;
        data[size++] = n;
        System.arraycopy(xs, 0, data, size, n);
        size += n;
        System.arraycopy(ys, 0, data, size, n);
        size += n;
    }

    /**
     * A polygon drawn at whole pixels {@code xs/ys} in software, with the
     * exact corners {@code ex/ey} for the graphics card.
     */
    public void polygon(int op, int[] xs, int[] ys, int n, float[] ex, float[] ey) {
        n = Math.max(0, Math.min(n, Math.min(xs.length, ys.length)));
        ensure(2 + 4 * n);
        data[size++] = op == FILL_POLYGON ? FILL_POLYGON_EXACT : DRAW_POLYGON_EXACT;
        data[size++] = n;
        System.arraycopy(xs, 0, data, size, n);
        size += n;
        System.arraycopy(ys, 0, data, size, n);
        size += n;
        for (int k = 0; k < n; k++) {
            data[size++] = Float.floatToRawIntBits(ex[k]);
        }
        for (int k = 0; k < n; k++) {
            data[size++] = Float.floatToRawIntBits(ey[k]);
        }
    }

    public void tag(int value) {
        ensure(2);
        data[size++] = TAG;
        data[size++] = value;
    }

    public void glow(int level) {
        ensure(2);
        data[size++] = GLOW;
        data[size++] = Math.max(0, Math.min(255, level));
    }

    public void fillRect(int x, int y, int width, int height) {
        ensure(5);
        data[size++] = FILL_RECT;
        data[size++] = x;
        data[size++] = y;
        data[size++] = width;
        data[size++] = height;
    }

    private void ensure(int more) {
        if (size + more > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length * 2, size + more));
        }
    }
}
