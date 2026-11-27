package nfm.lit.features.weather;

import nfm.lit.features.GameHooks;
import nfm.lit.features.sounds.Recordings;

/**
 * The weather's sounds, each a real recording ({@code sounds\sources.tsv}
 * names them) looping around the listener through the 3D sound, so Classic
 * sound never hears them: rain falling all around, rain drumming on the
 * roof from inside a closed car, and the wind of a snowfall.
 */
final class WeatherSounds {

    static final String RAIN = "wx-rain";
    static final String ROOF = "wx-roof";
    static final String WIND = "wx-wind";
    /* later details' sounds, given with the rest: a splash, spray off a wet road, tyres on snow, a lump of snow landing */
    static final String SPLASH = "wx-splash";
    static final String SPRAY = "wx-spray";
    static final String ROLL = "wx-roll";
    static final String HIT = "wx-hit";

    /** The 3D sound's beds the weather uses. */
    static final int RAIN_BED = 0;
    static final int ROOF_BED = 1;
    static final int SPRAY_BED = 2;
    static final int WIND_BED = 3;
    static final int ROLL_BED = 4;

    private WeatherSounds() {
    }

    /** Gives the 3D sound the recordings, once for the game. */
    static synchronized void make() {
        Recordings.give(RAIN, "rain", false);
        Recordings.give(ROOF, "rainroof", false);
        Recordings.give(WIND, "snowwind", false);
        Recordings.give(SPLASH, "splash", false);
        Recordings.give(SPRAY, "spray", false);
        Recordings.give(ROLL, "snowroll", false);
        Recordings.give(HIT, "snowhit", false);
    }

    /**
     * What is heard all around this tick: {@code snow} or rain, the
     * listener at {@code speed} of their car's top speed (0 to 1) and, if
     * {@code inside}, under the roof of a closed car (the helmet cam);
     * {@code hiss} how much their tyres hiss on the wet road (0 to 1).
     */
    static void around(GameHooks hooks, boolean snow, float speed, boolean inside, float hiss) {
        if (!hooks.sound3d()) {
            return;
        }
        if (snow) {
            hooks.bed(RAIN_BED, null, 0F, 1F);
            hooks.bed(ROOF_BED, null, 0F, 1F);
            hooks.bed(SPRAY_BED, null, 0F, 1F);
            hooks.bed(WIND_BED, WIND, (0.3F + 0.3F * speed) * (inside ? 0.55F : 1F), 0.92F + 0.2F * speed);
        } else {
            hooks.bed(WIND_BED, null, 0F, 1F);
            hooks.bed(RAIN_BED, RAIN, inside ? 0.26F : 0.5F + 0.2F * speed, 1F);
            hooks.bed(ROOF_BED, inside ? ROOF : null, inside ? 0.6F : 0F, 1F);
            hooks.bed(SPRAY_BED, hiss > 0.02F ? SPRAY : null, 0.5F * hiss * (inside ? 0.6F : 1F), 0.85F + 0.3F * hiss);
        }
    }

    /** Nothing more is heard of the weather. */
    static void silence(GameHooks hooks) {
        for (int bed = RAIN_BED; bed <= ROLL_BED; bed++) {
            hooks.bed(bed, null, 0F, 1F);
        }
    }
}
