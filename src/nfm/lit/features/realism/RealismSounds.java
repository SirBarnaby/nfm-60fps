package nfm.lit.features.realism;

import nfm.lit.audio.EngineLayers;
import nfm.lit.features.sounds.Recordings;

/**
 * The few sounds damage and heat add, each a real recording
 * ({@code sounds\sources.tsv} names them) sampled down to one of the game's
 * rates and played at the car through the 3D sound, so Classic sound never
 * hears them: hot metal ticking as it cools, the thud and the scrape of a
 * car bottoming out, loose parts rattling, the ring of Sword of Justice's
 * spikes on a car it rams, and the exhaust's pops, heard with each flame
 * it throws.
 */
final class RealismSounds {

    static final String TICK = "xtra-tick";
    static final String THUD = "xtra-thud";
    static final String SCRAPE = "xtra-scrape";
    static final String RATTLE = "xtra-rattle";
    static final String GLINT = "xtra-glint";
    /** An exhaust pop and a loud backfire: the 3D sound plays them from the exhaust ({@code Sound3D.carPop}). */
    static final String POP = "xtra-pop";
    static final String BACKFIRE = "xtra-backfire";

    private RealismSounds() {
    }

    /** Gives the 3D sound the recordings, once for the game. */
    static synchronized void make() {
        Recordings.give(TICK, "tick", false);
        Recordings.give(THUD, "thud", false);
        Recordings.give(SCRAPE, "scrape", false);
        Recordings.give(RATTLE, "rattle", false);
        Recordings.give(GLINT, "glint", false);
        Recordings.give(EngineLayers.BACKFIRE_CLIP, "backfire", false);
        for (int i = 0; i < EngineLayers.POP_CLIPS.length; i++) {
            Recordings.give(EngineLayers.POP_CLIPS[i], "pop" + (i + 1), false);
        }
    }
}
