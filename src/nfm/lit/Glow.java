package nfm.lit;

/**
 * Marks what in the world gives off light, so an HDR display can show it
 * brighter than paper white: flames, lamps, the repair hoop's flash and
 * sparks, stars, and the sky in a lightning storm. Everything else stays at
 * the brightness Windows shows SDR white at, so the game looks as it always
 * did and only its lights stand out.
 * <p>
 * Only the scene recorded for the graphics card takes note; software
 * drawing, and so the race itself, is untouched.
 */
final class Glow {

    /** Fire, and the white flash of a car blowing up. */
    static final int FLAME = 255;
    /** The repair hoop's flash and the car's outline as it is fixed. */
    static final int REPAIR = 200;
    /** The sparks of an electrified car. */
    static final int ELECTRIC = 255;
    /** Headlights, tail lights and lamps after dark. */
    static final int LAMP = 150;
    /** Stars at night. */
    static final int STAR = 110;
    /** How much the sky glows per step its lightning flash is above the plain sky. */
    static final int LIGHTNING_STEP = 4;

    private Glow() {
    }

    /** The polygons drawn from now on glow by {@code level}, 0 to 255. */
    static void set(int level) {
        SceneRecorder recorder = Precise.recorder;
        if (recorder != null) {
            recorder.glow(level);
        }
    }

    /** The polygons drawn from now on do not glow. */
    static void off() {
        set(0);
    }
}
