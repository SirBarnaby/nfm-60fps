package nfm.lit.features;

/**
 * What a stage's file says of its air, by the lines that say it: an extra
 * may change each as the stage loads ({@link Feature#air}). The colours are
 * red, green and blue, 0 to 255, as the file gives them, before the stage's
 * light ({@link #SNAP}) is laid over them.
 */
public final class StageAir {

    /** The stage begins to load: nothing read yet. {@code c} is not used. */
    public static final int BEGIN = 0;
    /** {@code snap}: the stage's light, percent added to red, green and blue of everything in it. */
    public static final int SNAP = 1;
    /** {@code sky}: the sky's colour. */
    public static final int SKY = 2;
    /** {@code ground}: the ground's colour. */
    public static final int GROUND = 3;
    /** {@code polys}: the colour of the ground's patches. */
    public static final int POLYS = 4;
    /** {@code fog}: the colour everything fades into with the distance. */
    public static final int FOG = 5;
    /** {@code clouds}: the clouds' colour; {@code c[3]} how much of the sky is mixed in (0 to 10), {@code c[4]} their height. */
    public static final int CLOUDS = 6;
    /** {@code texture}: the ground patches' tint; {@code c[3]} how much of the ground is mixed in. */
    public static final int TEXTURE = 7;
    /**
     * {@code fadefrom}: {@code c[0]} how far off the fog starts, units. A
     * change is seen by drawing only: the race keeps the stage's own.
     */
    public static final int FADE = 8;
    /** {@code density}: {@code c[0]} how thin the fog is (each band of distance keeps this many parts in this many and one); a change is seen by drawing only. */
    public static final int DENSITY = 9;
    /** The file has been read; {@code c[0]} is the fog's start and {@code c[1]} its density as they are drawn. */
    public static final int END = 10;

    private StageAir() {
    }
}
