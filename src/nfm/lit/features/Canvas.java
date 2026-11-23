package nfm.lit.features;

/**
 * How a feature draws into the race's scene: flat polygons, as the game's
 * own models are made of, given in the world, moving through it or on a
 * car. The polygons go into the scene the graphics card draws (the kept
 * scene, placed again for every frame), so they are smooth between ticks
 * like everything else; in software they are drawn straight away.
 * <p>
 * Only valid inside {@link Feature#draw}. Corner arrays are read, never
 * kept; polygons have at most {@link #MAX_CORNERS} corners.
 */
public interface Canvas {

    int MAX_CORNERS = 16;

    /** The colour of the next polygons, 0xRRGGBB. */
    void color(int rgb);

    /**
     * {@code rgb} as the stage shows it at world point {@code (x, y, z)}: in
     * its light, faded into its fog with the distance, as the game shows the
     * road and the cars.
     */
    int fog(int rgb, float x, float y, float z);

    /**
     * How much of each pixel the next polygons cover, 0 to 1: below 1 they
     * are see-through, drawn in a fine fixed pattern of pixels (dithered).
     * Back to 1 for every item.
     */
    void dither(float coverage);

    /** How much the next polygons glow (flames, hot brakes), 0 to 1; only an HDR display shows it. */
    void glow(float level);

    /** A polygon fixed in the world. */
    void world(float[] x, float[] y, float[] z, int n);

    /**
     * A polygon moving through the world: its corners after the last real
     * tick, and how far it moves in a tick. Each frame shows it where it is
     * at that moment between the ticks.
     */
    void moving(float[] x, float[] y, float[] z, float vx, float vy, float vz, int n);

    /**
     * A polygon whose corners each move their own way: corner {@code v}
     * after the last real tick and how far it moves in a tick (zero for a
     * corner fixed in the world), so a trail can start at a moving car and
     * end where it was left in the air.
     */
    void moving(float[] x, float[] y, float[] z, float[] vx, float[] vy, float[] vz, int n);

    /** A polygon on car {@code car}, in the car's own frame (as its model's corners are): it moves with the car. */
    void onCar(int car, float[] x, float[] y, float[] z, int n);
}
