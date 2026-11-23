package nfm.lit.gpu;

/**
 * A kept scene's points, for placing them on the graphics card: what
 * {@code RetainedScene} records once a tick (each corner the game drew, as
 * a point on a car, in the world or on screen), and what changes with each
 * frame's moment (the camera and the cars), as the shader's constants.
 * <p>
 * The arrays are the recording's own, not copies: a kept scene is not
 * written again while it is drawn. Filled on the thread that draws.
 */
public final class ScenePoints {

    /** Point kinds, as RetainedScene numbers them; CONSTANT is a value the game worked out itself. */
    public static final int CAMERA = 0;
    public static final int CAR = 1;
    public static final int SHADOW = 2;
    public static final int HORIZON = 3;
    public static final int SCREEN = 4;
    public static final int SCREEN_TRAIL = 5;
    public static final int CONSTANT = 6;
    /** An extra's particle, moving through the world at a steady speed. */
    public static final int MOVING = 7;
    /** Floats per point in {@link #points}. */
    public static final int STRIDE = 8;
    /** Car slots the shader has constants for: as many as a race can have. */
    public static final int CAR_SLOTS = 51;
    /** float4s of camera constants, and of constants per car. */
    static final int CAMERA_VEC4S = 5;
    static final int CAR_VEC4S = 4;

    /* the recording */
    public int[] kinds;
    public float[] points;
    public int pointCount;
    /** Per polygon in the scene's list: where its exact x corners start, its corner count, its first code. */
    public int[] polygons;
    public int polygonCount;
    /** Per corner coordinate (x corners, then y): the point times 2 (+ 1 for y), or -1 for the value recorded. */
    public int[] codes;

    /** The frame's camera: see the projected vertex shader. */
    public final float[] camera = new float[4 * CAMERA_VEC4S];
    /** The frame's cars, CAR_VEC4S float4s each. */
    public final float[] cars = new float[4 * CAR_VEC4S * CAR_SLOTS];
    /** Car slots in use this frame. */
    public int carCount;
}
