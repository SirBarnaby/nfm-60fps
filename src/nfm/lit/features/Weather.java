package nfm.lit.features;

/**
 * The weather of the race being played, for the extras whose look changes
 * with it (smoke sinks in rain, a landing throws snow): which it is, and
 * how deep the snow lies. The weather extra sets it; with that extra off it
 * is dry. Only a look and a sound: nothing in a race reads it.
 */
public final class Weather {

    public static final int DRY = 0;
    public static final int RAIN = 1;
    public static final int RAIN_FOG = 2;
    public static final int SNOW = 3;

    private static volatile int kind;
    private static volatile float snow;

    private Weather() {
    }

    /** {@link #DRY}, {@link #RAIN}, {@link #RAIN_FOG} or {@link #SNOW}. */
    public static int kind() {
        return kind;
    }

    public static boolean raining() {
        return kind == RAIN || kind == RAIN_FOG;
    }

    public static boolean snowing() {
        return kind == SNOW;
    }

    /** How deep the snow lies, 0 (none) to 1 (as deep as it gets); 0 unless it snows. */
    public static float snow() {
        return kind == SNOW ? snow : 0F;
    }

    /** The weather extra says what the weather is ({@code depth}: the snow's). */
    public static void set(int weather, float depth) {
        kind = weather;
        snow = depth;
    }
}
