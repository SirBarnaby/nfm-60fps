package nfm.lit.features;

import java.util.Arrays;

/**
 * Where in the game's back-to-front drawing each thing a feature draws goes.
 * <p>
 * The game has no depth buffer: it sorts its objects by a distance from the
 * camera and draws the farthest first. Flat road pieces count as much
 * farther than anything else, so they are all drawn first, as a ground
 * layer. A feature queues each thing it draws as one of:
 * <ul>
 * <li>{@link #ground}: on the road, drawn right after the flat road pieces,
 * under every ramp, tree and car;
 * <li>{@link #at}: at a point in the world, drawn just before the first
 * object nearer than it, so what is in front covers it;
 * <li>{@link #after}: on a car, drawn right after that car ({@link #afterFirst}: before the others there).
 * </ul>
 * The game fills it once per frame drawn and merges it into its own order;
 * nothing is allocated once its arrays have grown.
 */
public final class DrawQueue {

    private static final int GROUND = 0;
    private static final int DEPTH = 1;
    private static final int AFTER = 2;
    private static final int AFTER_FIRST = 3;

    /** Draws item {@code item} of feature {@code feature}. */
    public interface Drawer {
        void draw(int feature, int item);
    }

    private int[] owner = new int[256];
    private int[] items = new int[256];
    private int[] kinds = new int[256];
    /** The depth of a DEPTH item, the car of an AFTER one. */
    private int[] where = new int[256];
    private int count;
    private long[] order = new long[256];
    private int depthCount;
    private int nextDepth;
    private boolean groundDrawn;
    private int afterCount;

    /** The feature queuing now (its place in the list). */
    private int current;

    /* the camera's eye this frame, as the game measures distances from it */
    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private int tick;
    private float share = 1F;

    /** Empties the queue for a frame seen from {@code (eyeX, eyeY, eyeZ)}, {@code tick} real ticks into the race. */
    public void begin(float eyeX, float eyeY, float eyeZ, int tick) {
        begin(eyeX, eyeY, eyeZ, tick, 1F);
    }

    /** As {@link #begin(float, float, float, int)}, the frame drawn {@code share} of the way between ticks ({@link #share}). */
    public void begin(float eyeX, float eyeY, float eyeZ, int tick, float share) {
        this.share = share;
        count = 0;
        depthCount = 0;
        afterCount = 0;
        groundDrawn = false;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.tick = tick;
        player = 0;
        yaw = 0F;
        pitch = 0F;
    }

    /* whose frame it is and where the camera looks, for what is drawn around the eye (the weather) */
    private int player;
    private float yaw;
    private float pitch;
    private float aspect = 16F / 9F;

    /**
     * After {@link #begin}: the frame is {@code player}'s (0, or 1 for
     * split screen's second), its camera turned {@code yaw} and tilted
     * {@code pitch} degrees as the game turns it, the picture
     * {@code aspect} times as wide as it is high.
     */
    public void view(int player, float yaw, float pitch, float aspect) {
        this.player = player;
        this.yaw = yaw;
        this.pitch = pitch;
        this.aspect = aspect;
    }

    /** The player whose frame this is. */
    public int player() {
        return player;
    }

    /**
     * The way the camera looks into {@code out[0..2]}, and the picture's
     * right and down from there into {@code out[3..5]} and
     * {@code out[6..8]}, each one unit long.
     */
    public void look(float[] out) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        float sy = (float) Math.sin(y);
        float cy = (float) Math.cos(y);
        float sp = (float) Math.sin(p);
        float cp = (float) Math.cos(p);
        out[0] = sy * cp;
        out[1] = sp;
        out[2] = cy * cp;
        out[3] = cy;
        out[4] = 0F;
        out[5] = -sy;
        out[6] = -sy * sp;
        out[7] = cp;
        out[8] = -cy * sp;
    }

    /** How many times as wide as it is high the picture is. */
    public float aspect() {
        return aspect;
    }

    /** The feature that queues next (for the game). */
    public void owner(int feature) {
        current = feature;
    }

    /** Where the camera's eye is this frame. */
    public float eyeX() {
        return eyeX;
    }

    public float eyeY() {
        return eyeY;
    }

    public float eyeZ() {
        return eyeZ;
    }

    /** Real ticks since the race started. */
    public int tick() {
        return tick;
    }

    /**
     * How far the frame is from the tick before the last real one (0) to the
     * last (1), as the cars are drawn; 1 while run-ahead's tick is drawn,
     * where the last real tick is the latest an extra knows.
     */
    public float share() {
        return share;
    }

    /** Item {@code item} lies on the road. */
    public void ground(int item) {
        add(GROUND, item, 0);
    }

    /** Item {@code item} is at world point {@code (x, y, z)}. */
    public void at(float x, float y, float z, int item) {
        add(DEPTH, item, depth(x, y, z));
        depthCount++;
    }

    /** Item {@code item} is on car {@code car}. */
    public void after(int car, int item) {
        add(AFTER, item, car);
        afterCount++;
    }

    /**
     * Item {@code item} is on car {@code car}, under what the extras draw
     * on it with {@link #after}: drawn right after the car, before those
     * (drops on a windscreen, under the dashboard drawn from the helmet).
     */
    public void afterFirst(int car, int item) {
        add(AFTER_FIRST, item, car);
        afterCount++;
    }

    /** The game's distance measure of world point {@code (x, y, z)}: the square root of how far it is from the eye. */
    public int depth(float x, float y, float z) {
        float dx = eyeX - x;
        float dy = eyeY - y;
        float dz = eyeZ - z;
        return (int) Math.sqrt((int) Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private void add(int kind, int item, int at) {
        if (count == items.length) {
            int size = count * 2;
            owner = Arrays.copyOf(owner, size);
            items = Arrays.copyOf(items, size);
            kinds = Arrays.copyOf(kinds, size);
            where = Arrays.copyOf(where, size);
        }
        owner[count] = current;
        items[count] = item;
        kinds[count] = kind;
        where[count++] = at;
    }

    /** True if nothing was queued. */
    public boolean isEmpty() {
        return count == 0;
    }

    /* ------------------------------------------------------ for the game */

    /** Sorts the queue before the game's objects are drawn. */
    public void sort() {
        if (order.length < count) {
            order = new long[items.length];
        }
        int d = 0;
        for (int i = 0; i < count; i++) {
            if (kinds[i] == DEPTH) {
                // ascending by depth, then by when queued; walked backwards
                order[d++] = ((long) where[i] << 32) | (count - 1 - i);
            }
        }
        Arrays.sort(order, 0, d);
        nextDepth = d - 1;
    }

    /**
     * Before the game draws an object at distance {@code dist}: everything
     * queued farther away. A flat road piece ({@code flat}) is part of the
     * ground layer; the first object that is not ends it.
     */
    public void before(int dist, boolean flat, Drawer drawer) {
        if (flat) {
            return;
        }
        if (!groundDrawn) {
            drawGround(drawer);
        }
        while (nextDepth >= 0 && (int) (order[nextDepth] >> 32) >= dist) {
            int i = count - 1 - (int) order[nextDepth--];
            drawer.draw(owner[i], items[i]);
        }
    }

    /** After the game drew car {@code car}: what is on it. */
    public void after(int car, Drawer drawer) {
        if (afterCount == 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            if (kinds[i] == AFTER_FIRST && where[i] == car) {
                drawer.draw(owner[i], items[i]);
            }
        }
        for (int i = 0; i < count; i++) {
            if (kinds[i] == AFTER && where[i] == car) {
                drawer.draw(owner[i], items[i]);
            }
        }
    }

    /** After the game's last object: whatever is left, nearest last. */
    public void end(Drawer drawer) {
        if (!groundDrawn) {
            drawGround(drawer);
        }
        while (nextDepth >= 0) {
            int i = count - 1 - (int) order[nextDepth--];
            drawer.draw(owner[i], items[i]);
        }
    }

    private void drawGround(Drawer drawer) {
        groundDrawn = true;
        for (int i = 0; i < count; i++) {
            if (kinds[i] == GROUND) {
                drawer.draw(owner[i], items[i]);
            }
        }
    }
}
