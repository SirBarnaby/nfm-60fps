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

    /** At most this many explosions are kept; the oldest make way. */
    public static final int BLASTS = 32;
    private static final float[] blastX = new float[BLASTS];
    private static final float[] blastGround = new float[BLASTS];
    private static final float[] blastZ = new float[BLASTS];
    private static final float[] blastSize = new float[BLASTS];
    private static final boolean[] blastCrater = new boolean[BLASTS];
    private static final int[] blastTick = new int[BLASTS];
    private static int blasts;

    private Weather() {
    }

    /** A race starts: no explosions yet. */
    public static void raceStarts() {
        blasts = 0;
        lumps = 0;
    }

    /**
     * An explosion's look says where it went off, for what the weather
     * does with it (rain fills a crater, a blast clears snow): over ground
     * at height {@code ground}, {@code size} 1 for a dynamite stick, leaving
     * a crater or not, {@code tick} ticks into the race.
     */
    public static void blast(float x, float ground, float z, float size, boolean crater, int tick) {
        int i = blasts % BLASTS;
        blastX[i] = x;
        blastGround[i] = ground;
        blastZ[i] = z;
        blastSize[i] = size;
        blastCrater[i] = crater;
        blastTick[i] = tick;
        blasts++;
    }

    /** Explosions since the race started; the last {@link #BLASTS} are kept, explosion {@code n} in slot {@code n % BLASTS}. */
    public static int blasts() {
        return blasts;
    }

    public static float blastX(int slot) {
        return blastX[slot];
    }

    public static float blastGround(int slot) {
        return blastGround[slot];
    }

    public static float blastZ(int slot) {
        return blastZ[slot];
    }

    public static float blastSize(int slot) {
        return blastSize[slot];
    }

    public static boolean blastCrater(int slot) {
        return blastCrater[slot];
    }

    public static int blastTick(int slot) {
        return blastTick[slot];
    }

    /* ------------------------------------------------------------- lumps */

    /** At most this many lumps of snow are kept; the oldest make way. */
    public static final int LUMPS = 64;
    private static final float[] lump = new float[LUMPS * 7];
    private static int lumps;

    /**
     * The snow's look says a lump of it was thrown (a heap run over, a
     * hard landing, a car ploughing): from {@code (x, y, z)}, moving
     * {@code (vx, vy, vz)} a tick, {@code size} units across. For an extra
     * that lets it tumble (PhysX); with none it is only the snow's own puffs.
     */
    public static void lump(float x, float y, float z, float vx, float vy, float vz, float size) {
        int at = lumps % LUMPS * 7;
        lump[at] = x;
        lump[at + 1] = y;
        lump[at + 2] = z;
        lump[at + 3] = vx;
        lump[at + 4] = vy;
        lump[at + 5] = vz;
        lump[at + 6] = size;
        lumps++;
    }

    /** Lumps thrown since the race started; the last {@link #LUMPS} are kept, lump {@code n} in slot {@code n % LUMPS}. */
    public static int lumps() {
        return lumps;
    }

    /** Value {@code k} of the lump in {@code slot}: 0 to 2 its place, 3 to 5 its move a tick, 6 its size. */
    public static float lump(int slot, int k) {
        return lump[slot * 7 + k];
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
