package nfm.lit.gpu;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * A kept scene made ready for the graphics card to place, once per tick on
 * the game thread: its triangles, whose vertices name the points they sit
 * at, and its points, laid out as the shader reads them. A frame then sends
 * only its camera and cars ({@link #points()}'s constants); the scene itself
 * goes to the card once, the first time a frame of it is drawn.
 * <p>
 * Owned by one thread at a time, like the kept scene it belongs to.
 */
public final class ProjectedScene {

    /** Texels in a row of the points texture: three per point. */
    static final int POINTS_WIDTH = 3072;

    final SceneGeometry geometry = new SceneGeometry();
    private final ScenePoints points = new ScenePoints();
    private ByteBuffer vertices;
    private FloatBuffer texels;
    private int rows;
    private int pointCount;
    private boolean built;
    private long buildNanos;
    /** The split-screen pane it is drawn in, in the picture's pixels; null for the whole picture. */
    private Rectangle pane;

    /** The recording to build from, and the frame's camera and cars to place it with. */
    public ScenePoints points() {
        return points;
    }

    /** True once {@link #build} has made it ready, until {@link #clear}. */
    public boolean built() {
        return built;
    }

    /** Not ready for the card: the next frames place the scene's corners themselves. */
    public void clear() {
        built = false;
    }

    /**
     * Builds the scene for the card from {@code list}, whose corners have
     * been placed for one moment of it (how polygons are cut into triangles
     * is decided there), and the recording {@link #points()} was given; with
     * a {@code pane}, a split-screen pane's, recorded in its own coordinates,
     * drawn there and clipped to it.
     */
    public void build(SceneList list, Rectangle pane) {
        long start = System.nanoTime();
        this.pane = pane == null ? null : new Rectangle(pane);
        SceneGeometry g = geometry;
        // the size only scales vertices placed here; the card places these with its own
        g.buildProjected(list, points, 1, 1);
        int bytes = g.vertexCount() * SceneGeometry.PROJECTED_STRIDE;
        if (vertices == null || vertices.capacity() < bytes) {
            int size = Math.max(bytes, vertices == null ? 1 << 20 : vertices.capacity() * 2);
            MemoryUtil.memFree(vertices);
            vertices = MemoryUtil.memAlloc(size);
        }
        vertices.clear().limit(bytes);
        g.copyProjected(vertices);

        int recorded = points.pointCount;
        int extras = g.extraPointCount();
        pointCount = recorded + extras;
        rows = Math.max(1, (pointCount * 3 + POINTS_WIDTH - 1) / POINTS_WIDTH);
        int floats = rows * POINTS_WIDTH * 4;
        if (texels == null || texels.capacity() < floats) {
            int size = Math.max(floats, texels == null ? 0 : texels.capacity() * 2);
            MemoryUtil.memFree(texels);
            texels = MemoryUtil.memAllocFloat(size);
        }
        // per point three texels: its kind and eight values, then three floats nobody reads
        float[] row = new float[12];
        FloatBuffer f = texels.clear();
        int[] kinds = points.kinds;
        float[] values = points.points;
        int stride = ScenePoints.STRIDE;
        for (int id = 0; id < recorded; id++) {
            row[0] = kinds[id];
            System.arraycopy(values, id * stride, row, 1, stride);
            f.put(row);
        }
        float[] extra = g.extraPoints();
        row[0] = ScenePoints.CONSTANT;
        for (int k = 3; k < 12; k++) {
            row[k] = 0F;
        }
        for (int e = 0; e < extras; e++) {
            row[1] = extra[e * stride];
            row[2] = extra[e * stride + 1];
            f.put(row);
        }
        f.position(floats).flip();
        built = true;
        buildNanos = System.nanoTime() - start;
    }

    /** The split-screen pane it is drawn in, null for the whole picture. */
    Rectangle pane() {
        return pane;
    }

    /** The vertices, {@link SceneGeometry#PROJECTED_STRIDE} bytes each, from position 0 to the limit. */
    ByteBuffer vertices() {
        return vertices;
    }

    /** The points texture's rows in use, {@link #POINTS_WIDTH} RGBA32F texels each, from position 0 to the limit. */
    FloatBuffer texels() {
        return texels;
    }

    int rows() {
        return rows;
    }

    int pointCount() {
        return pointCount;
    }

    long buildNanos() {
        return buildNanos;
    }

    /** Frees the native buffers; the scene can be built again afterwards. */
    public void free() {
        MemoryUtil.memFree(vertices);
        MemoryUtil.memFree(texels);
        vertices = null;
        texels = null;
        built = false;
    }
}
