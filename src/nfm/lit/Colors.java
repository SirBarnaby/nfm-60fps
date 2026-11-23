package nfm.lit;

import java.awt.Color;

/**
 * Colours for the drawing code, without making a new one each time.
 * <p>
 * The 3D code works out a colour for every polygon it draws, every frame, and
 * the original made a new {@link Color} for each: some hundreds of thousands
 * a second while racing, all garbage a moment later, which made the game's
 * memory climb between collections. A Color never changes once made, so the
 * same one can be handed out again: these keep the last few thousand made, by
 * their red, green and blue, and give the one already made where it is there.
 * <p>
 * The colour drawn is the same either way, and a value out of range throws
 * just as {@code new Color} does. Safe from any thread: a slot holds a whole
 * Color or another one, and every Color is checked before it is given out.
 */
public final class Colors {

    /** Slots, a power of two: more than the colours one frame of a stage uses. */
    private static final int SLOTS = 1 << 13;

    private static final Color[] MADE = new Color[SLOTS];

    private Colors() {
    }

    /** As {@code new Color(red, green, blue)}, each 0 to 255. */
    public static Color of(int red, int green, int blue) {
        if (((red | green | blue) & ~0xff) != 0) {
            return new Color(red, green, blue); // throws, as it would have
        }
        return ofRgb((red << 16) | (green << 8) | blue);
    }

    /** As {@code new Color(rgb)}: the low 24 bits are red, green and blue; opaque. */
    public static Color ofRgb(int rgb) {
        int argb = 0xff000000 | rgb;
        int slot = (argb * 0x9E3779B1) >>> (32 - Integer.numberOfTrailingZeros(SLOTS));
        Color color = MADE[slot];
        if (color == null || color.getRGB() != argb) {
            color = new Color(argb);
            MADE[slot] = color;
        }
        return color;
    }

    /** As {@code new Color(argb, true)}: alpha in the top 8 bits (the extras' see-through colours in software). */
    public static Color ofArgb(int argb) {
        int slot = (argb * 0x9E3779B1) >>> (32 - Integer.numberOfTrailingZeros(SLOTS));
        Color color = MADE[slot];
        if (color == null || color.getRGB() != argb) {
            color = new Color(argb, true);
            MADE[slot] = color;
        }
        return color;
    }

    /** As {@link Color#getHSBColor}, the same colour, from hue, saturation and brightness. */
    public static Color ofHsb(float hue, float saturation, float brightness) {
        return ofRgb(Color.HSBtoRGB(hue, saturation, brightness));
    }
}
