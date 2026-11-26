package nfm.lit.features;

/**
 * A feature that changes how the cars' own polygons are drawn: their colour
 * (dirt, lights, broken glass, an open bonnet's bay) and, for a bent wheel,
 * its corners, and what is drawn on them (a wheel's openings). The game asks it for every polygon of a race car it draws, on
 * the processor and for the kept scene alike, before the fog is laid over.
 * At most one feature is asked ({@link Features#carLook}).
 * <p>
 * Called on the game thread only, many times a frame: nothing allocated.
 */
public interface CarLook {

    /** The colour car {@code car}'s polygon {@code plane} is drawn in, given the game's {@code rgb} (0xRRGGBB). */
    int color(int car, int plane, int rgb);

    /** After {@link #color}: -1 to light the polygon as the game would, 0 to keep it dark, 1 to light it (a lamp). */
    int lamp();

    /**
     * Car {@code car}'s polygon {@code plane}, corners {@code x, y, z}
     * ({@code n} of them, in the car's own numbers): if it is drawn
     * elsewhere (a bent wheel's polygon, tilted), its corners as drawn into
     * {@code outX, outY, outZ} and true; false to draw it as it is.
     */
    boolean bend(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ);

    /**
     * Right after the game drew car {@code car}'s polygon {@code plane}, in
     * {@code rgb} as shown (lit, an extra's look and the fog over it): what
     * the feature draws on that polygon, as polygons on the car through
     * {@code canvas} (a wheel's openings and the brake disc behind them).
     * They are drawn where the polygon is in the car's own back-to-front
     * order, so what covers the polygon covers them. Not asked for a
     * polygon the game left out (too far, off screen, breaking up).
     */
    default void onPlane(int car, int plane, int rgb, Canvas canvas) {
    }
}
