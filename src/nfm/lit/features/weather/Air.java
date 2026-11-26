package nfm.lit.features.weather;

import nfm.lit.features.StageAir;
import nfm.lit.features.Weather;

/**
 * What the weather makes of a stage's air as its file is read: under rain
 * a grey, darker sky, duller light, darker ground and fog that starts
 * nearer (much nearer and thicker with "Rain and fog"); under snow a pale
 * sky, pale fog and ground that is part snow already. Every colour is
 * worked out from the stage's own, so a night stays a night and a red
 * evening keeps a little of its red.
 */
final class Air {

    /** Lying snow, before the stage's light. */
    static final int SNOW_WHITE = 0xeef1f6;
    /** The ground's patches under snow: a little bluer, so the ground is not one flat white. */
    static final int SNOW_SHADE = 0xdde4ee;
    /** How much of the ground's colour the thinnest snow hides, and how much more the deepest does. */
    static final float SNOW_HIDES = 0.5F;
    static final float SNOW_HIDES_MORE = 0.45F;

    /** How much of the ground's colour snow {@code depth} deep (0 to 1) hides. */
    static float hides(float depth) {
        return SNOW_HIDES + SNOW_HIDES_MORE * Math.max(0F, Math.min(1F, depth));
    }

    /** The stage file's own ground and patches (0xRRGGBB), -1 if it gave none. */
    int ground = -1;
    int patches = -1;
    /** What the last stage loaded was graded for. */
    int kind;

    void begin(int weather) {
        kind = weather;
        ground = -1;
        patches = -1;
    }

    /** Changes the values {@code c} of line {@code what} for weather {@code kind}. */
    void grade(int what, int[] c) {
        if (what == StageAir.GROUND) {
            ground = rgb(c);
        } else if (what == StageAir.POLYS) {
            patches = rgb(c);
        }
        if (kind == Weather.RAIN || kind == Weather.RAIN_FOG) {
            rain(what, c, kind == Weather.RAIN_FOG);
        } else if (kind == Weather.SNOW) {
            snow(what, c);
        }
    }

    private void rain(int what, int[] c, boolean fog) {
        switch (what) {
            case StageAir.SNAP -> {
                c[0] -= 16;
                c[1] -= 13;
                c[2] -= 8;
            }
            case StageAir.SKY -> grey(c, 0.72F, 0.80F);
            case StageAir.FOG -> grey(c, fog ? 0.84F : 0.80F, fog ? 0.85F : 0.7F);
            case StageAir.CLOUDS -> grey(c, 0.60F, 0.8F);
            case StageAir.GROUND, StageAir.POLYS -> {
                c[0] = Math.round(c[0] * 0.80F);
                c[1] = Math.round(c[1] * 0.80F);
                c[2] = Math.round(c[2] * 0.83F);
            }
            case StageAir.FADE -> c[0] = fog ? Math.max(1200, Math.min(1800, Math.round(c[0] * 0.25F)))
                    : Math.round(c[0] * 0.7F);
            case StageAir.DENSITY -> c[0] = fog ? 2 : Math.max(2, c[0] - 1);
            default -> {
            }
        }
    }

    private void snow(int what, int[] c) {
        switch (what) {
            case StageAir.SNAP -> {
                c[1] += 3;
                c[2] += 9;
            }
            case StageAir.SKY, StageAir.FOG -> pale(c, 0.8F);
            case StageAir.CLOUDS -> pale(c, 0.6F);
            case StageAir.GROUND -> put(c, mix(rgb(c), SNOW_WHITE, hides(WeatherFeature.SNOW_START)));
            case StageAir.POLYS -> put(c, mix(rgb(c), SNOW_SHADE, hides(WeatherFeature.SNOW_START)));
            case StageAir.FADE -> c[0] = Math.round(c[0] * 0.6F);
            case StageAir.DENSITY -> c[0] = Math.max(2, c[0] - 2);
            default -> {
            }
        }
    }

    /** The ground's colour under snow {@code depth} deep (0 to 1), as a stage file would give it; -1 if the file gave none. */
    int groundUnder(float depth) {
        return ground < 0 ? -1 : mix(ground, SNOW_WHITE, hides(depth));
    }

    /** The patches' colour under snow {@code depth} deep; -1 if the file gave none (they follow the ground). */
    int patchesUnder(float depth) {
        return patches < 0 ? -1 : mix(patches, SNOW_SHADE, hides(depth));
    }

    /** {@code c} mostly to a grey {@code level} times as bright as it was, a touch blue, {@code share} of the way. */
    private static void grey(int[] c, float level, float share) {
        float lum = (c[0] * 3 + c[1] * 6 + c[2]) / 10F * level;
        c[0] = clamp(c[0] + (lum * 0.97F - c[0]) * share);
        c[1] = clamp(c[1] + (lum - c[1]) * share);
        c[2] = clamp(c[2] + (lum * 1.05F - c[2]) * share);
    }

    /** {@code c} mostly to the pale grey of a snow sky, brighter where the stage's own was bright. */
    private static void pale(int[] c, float share) {
        float lum = 150F + (c[0] * 3 + c[1] * 6 + c[2]) / 10F * 0.38F;
        c[0] = clamp(c[0] + (lum * 0.95F - c[0]) * share);
        c[1] = clamp(c[1] + (lum * 0.97F - c[1]) * share);
        c[2] = clamp(c[2] + (lum * 1.01F - c[2]) * share);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    static int rgb(int[] c) {
        return clamp(c[0]) << 16 | clamp(c[1]) << 8 | clamp(c[2]);
    }

    private static void put(int[] c, int rgb) {
        c[0] = rgb >> 16 & 0xff;
        c[1] = rgb >> 8 & 0xff;
        c[2] = rgb & 0xff;
    }

    /** {@code a} with {@code share} of {@code b}. */
    static int mix(int a, int b, float share) {
        float s = Math.max(0F, Math.min(1F, share));
        int r = Math.round((a >> 16 & 0xff) + ((b >> 16 & 0xff) - (a >> 16 & 0xff)) * s);
        int g = Math.round((a >> 8 & 0xff) + ((b >> 8 & 0xff) - (a >> 8 & 0xff)) * s);
        int bl = Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * s);
        return r << 16 | g << 8 | bl;
    }
}
