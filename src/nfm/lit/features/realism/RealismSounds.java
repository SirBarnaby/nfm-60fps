package nfm.lit.features.realism;

import static nfm.lit.features.Synth.decay;
import static nfm.lit.features.Synth.sine;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Synth;
import nfm.lit.features.Synth.Noise;

/**
 * The few sounds damage and heat add, each made once from a few lines of
 * synthesis (the same every run: its noise comes from a fixed seed) and
 * played at the car through the 3D sound, so Classic sound never hears
 * them: hot metal ticking as it cools, the thud and the scrape of a car
 * bottoming out, loose parts rattling. Impacts are decaying modes and the
 * scrape is filtered noise, after the modal synthesis papers (van den Doel)
 * and the Clatter and garjan libraries.
 */
final class RealismSounds {

    static final String TICK = "xtra-tick";
    static final String THUD = "xtra-thud";
    static final String SCRAPE = "xtra-scrape";
    static final String RATTLE = "xtra-rattle";

    static final int RATE = 22050;
    /** The clips' peak, a share of full scale: about -3 dB. */
    private static final float PEAK = 0.7F;

    private RealismSounds() {
    }

    /** Makes the sounds, once for the game. */
    static synchronized void make() {
        if (Sound3D.hasExtraClip(TICK)) {
            return;
        }
        Sound3D.extraClip(TICK, tick(), RATE);
        Sound3D.extraClip(THUD, thud(), RATE);
        Sound3D.extraClip(SCRAPE, scrape(), RATE);
        Sound3D.extraClip(RATTLE, rattle(), RATE);
    }

    /** A metal tick: a bright ring a few milliseconds long. */
    static short[] tick() {
        float[] out = new float[(int) (RATE * 0.03F)];
        Noise noise = new Noise(11);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float ring = sine(3150F, t) * 0.6F + sine(4870F, t) * 0.3F + sine(7420F, t) * 0.1F;
            out[i] = (ring * decay(t, 0.006F) + noise.next() * decay(t, 0.0008F) * 0.5F) * 0.8F;
        }
        return pcm(out);
    }

    /** A heavy thud: low modes and a short burst of dull noise. */
    static short[] thud() {
        float[] out = new float[(int) (RATE * 0.35F)];
        Noise noise = new Noise(23);
        float lowPass = 0F;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            lowPass += (noise.next() - lowPass) * 0.08F;
            float body = sine(52F, t) * decay(t, 0.09F) + sine(87F, t) * 0.6F * decay(t, 0.06F)
                    + sine(143F, t) * 0.3F * decay(t, 0.035F);
            out[i] = body * 0.9F + lowPass * 3F * decay(t, 0.02F);
        }
        return pcm(out);
    }

    /** Metal scraping on the road: noise through a band, with a few metallic modes riding on it. */
    static short[] scrape() {
        float[] out = new float[(int) (RATE * 0.4F)];
        Noise noise = new Noise(37);
        float lo = 0F;
        float hi = 0F;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float n = noise.next();
            hi += (n - hi) * 0.5F;
            lo += (hi - lo) * 0.15F;
            float band = hi - lo;
            float grit = 0.6F + 0.4F * noise.next();
            float modes = sine(1730F, t) * 0.25F + sine(2410F, t) * 0.18F + sine(3170F, t) * 0.12F;
            float envelope = Math.min(1F, t / 0.012F) * decay(Math.max(0F, t - 0.15F), 0.09F);
            out[i] = (band * 1.4F + modes * grit) * envelope * 0.7F;
        }
        return pcm(out);
    }

    /** A rattle: a few loose clicks close together. */
    static short[] rattle() {
        float[] out = new float[(int) (RATE * 0.09F)];
        Noise noise = new Noise(53);
        float[] at = {0F, 0.017F, 0.031F, 0.052F, 0.066F};
        for (int k = 0; k < at.length; k++) {
            float gain = 0.5F + 0.5F * Math.abs(noise.next());
            float pitch = 0.8F + 0.4F * Math.abs(noise.next());
            int start = (int) (at[k] * RATE);
            for (int i = start; i < out.length; i++) {
                float t = (i - start) / (float) RATE;
                float click = sine(910F * pitch, t) * 0.6F + sine(1630F * pitch, t) * 0.4F;
                out[i] += (click * decay(t, 0.004F) + noise.next() * decay(t, 0.0006F) * 0.4F) * gain * 0.6F;
            }
        }
        return pcm(out);
    }

    /** {@code out} as 16-bit samples, its peak at {@link #PEAK}. */
    private static short[] pcm(float[] out) {
        return Synth.pcm(out, PEAK);
    }
}
