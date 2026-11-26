package nfm.lit.features.powerups;

import static nfm.lit.features.Synth.decay;
import static nfm.lit.features.Synth.pcm;
import static nfm.lit.features.Synth.sine;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Features;
import nfm.lit.features.Synth.Noise;
import nfm.lit.features.sounds.Recordings;

/**
 * The powerups' sounds: the pickup, a hum for each powerup held (looped),
 * nitrous burning (four sounds), the dynamite's throw and the bomb's drop. Each is a real
 * recording ({@link Recordings}); one whose recording is missing is made
 * once from a few lines of synthesis instead (the same every run: the
 * noise comes from fixed seeds). They go to the 3D
 * sound as the extras' clips and to Classic sound as clips of its own,
 * never into the game's own list of sounds. An explosion's sound is
 * {@link nfm.lit.features.Explosions}'s.
 */
final class PowerupSounds {

    static final String PICKUP = "pwr-pickup";
    /** The hum of powerup {@code n} is {@code HUM + n}: 1 nitrous, 2 dynamite, 3 the bomb (Sound3D's HumVoice). */
    static final String HUM = "pwr-hum";
    /** Nitrous burning, for Classic sound: {@code NITROUS + kind}, the four loops of {@link #nitroLoop}. */
    static final String NITROUS = "pwr-nitrous";
    static final String THROW = "pwr-throw";
    static final String DROP = "pwr-drop";

    /** The recordings these sounds are (clips of {@code sounds\clips.tsv}): the checks ask that none is missing. */
    static final String[] RECORDINGS = {"bell", "bottle", "fuse", "clock", "nitro", "afterburner", "toss", "knock"};

    static final int RATE = 22050;
    /** A hum's loop, a second: every tone in it a whole number of hertz, so the loop meets itself. */
    private static final int LOOP = RATE;
    private static final int CROSSFADE = RATE / 10;

    private static boolean made;

    private PowerupSounds() {
    }

    /** Makes the sounds, once for the game. */
    static synchronized void make() {
        if (made) {
            return;
        }
        made = true;
        if (!Recordings.give(PICKUP, "bell", true)) {
            give(PICKUP, pickup());
        }
        if (!Recordings.give(HUM + PowerupRace.NITROUS, "bottle", true)) {
            give(HUM + PowerupRace.NITROUS, nitrousHum());
        }
        if (!Recordings.give(HUM + PowerupRace.DYNAMITE, "fuse", true)) {
            give(HUM + PowerupRace.DYNAMITE, fuseHum());
        }
        if (!Recordings.give(HUM + PowerupRace.BOMB, "clock", true)) {
            give(HUM + PowerupRace.BOMB, bombHum());
        }
        // nitrous burning: the two recordings the 3D sound mixes as it plays, and the four mixes for Classic sound
        Recordings.give(Sound3D.NITRO_HISS, "nitro", false);
        Recordings.give(Sound3D.NITRO_BURNER, "afterburner", false);
        Recordings.Clip hiss = Recordings.clip("nitro");
        for (int kind = 1; kind <= 4 && hiss != null; kind++) {
            short[] loop = nitroLoop(kind);
            if (loop != null) {
                Features.hooks().classicClip(NITROUS + kind, loop, hiss.rate());
            }
        }
        if (!Recordings.give(THROW, "toss", true)) {
            give(THROW, toss());
        }
        if (!Recordings.give(DROP, "knock", true)) {
            give(DROP, clunk());
        }
    }

    private static void give(String name, short[] pcm) {
        Sound3D.extraClip(name, pcm, RATE);
        Features.hooks().classicClip(name, pcm, RATE);
    }

    /** The pickup, if its recording is missing: a rising chime, three bell notes close together. */
    static short[] pickup() {
        float[] out = new float[(int) (RATE * 0.45F)];
        float[] notes = {1046.5F, 1318.5F, 1568F};
        for (int k = 0; k < notes.length; k++) {
            int start = (int) (k * 0.06F * RATE);
            for (int i = start; i < out.length; i++) {
                float t = (i - start) / (float) RATE;
                float bell = sine(notes[k], t) + 0.35F * sine(notes[k] * 2.76F, t) + 0.15F * sine(notes[k] * 5.4F, t);
                out[i] += bell * decay(t, 0.12F) * Math.min(1F, t / 0.003F);
            }
        }
        return pcm(out, 0.6F);
    }

    /** Nitrous held, if its recording is missing: a pressurised hiss over a low, slowly beating tone. */
    static short[] nitrousHum() {
        float[] noise = band(new Noise(71), LOOP + CROSSFADE, 0.6F, 0.12F);
        float[] out = new float[LOOP];
        for (int i = 0; i < LOOP; i++) {
            float t = i / (float) RATE;
            float tone = sine(110F, t) * 0.5F + sine(111F, t) * 0.5F + sine(220F, t) * 0.2F;
            out[i] = tone * 0.5F + loopNoise(noise, i) * 0.7F;
        }
        return pcm(out, 0.45F);
    }

    /** Dynamite held, if its recording is missing: a fuse fizzing and crackling. */
    static short[] fuseHum() {
        Noise noise = new Noise(83);
        float[] hiss = band(new Noise(89), LOOP + CROSSFADE, 0.9F, 0.35F);
        float[] out = new float[LOOP];
        for (int i = 0; i < LOOP; i++) {
            out[i] = loopNoise(hiss, i) * 0.5F * (0.75F + 0.25F * sine(7F, i / (float) RATE));
        }
        // crackles: short clicks at spots from the seed, kept clear of the loop's ends
        for (int k = 0; k < 38; k++) {
            int at = CROSSFADE + (int) (Math.abs(noise.next()) * (LOOP - 2 * CROSSFADE));
            float gain = 0.4F + 0.6F * Math.abs(noise.next());
            for (int i = 0; i < 160 && at + i < LOOP; i++) {
                float t = i / (float) RATE;
                out[at + i] += noise.next() * gain * decay(t, 0.0015F);
            }
        }
        return pcm(out, 0.4F);
    }

    /** A bomb held, if its recording is missing: a clock ticking, four ticks a second, each a short click with a little ring. */
    static short[] bombHum() {
        float[] out = new float[LOOP];
        Noise noise = new Noise(113);
        for (int k = 0; k < 4; k++) {
            int at = k * LOOP / 4 + 200;
            float pitch = k % 2 == 0 ? 2100F : 1750F; // tick, tock
            for (int i = 0; i < RATE / 12 && at + i < LOOP; i++) {
                float t = i / (float) RATE;
                out[at + i] += noise.next() * decay(t, 0.0012F) * 0.8F + sine(pitch, t) * decay(t, 0.012F) * 0.6F
                        + sine(pitch * 2.4F, t) * decay(t, 0.006F) * 0.3F;
            }
        }
        return pcm(out, 0.5F);
    }

    /**
     * Nitrous sound {@code kind} (1 on the ground, 2 in the air, 3 two at
     * once on the ground, 4 two at once in the air) mixed into one loop for
     * Classic sound, from the two recordings as the 3D sound mixes them
     * while it plays ({@link Sound3D#nitroMix}); null if a recording is
     * missing. The loop is as long as it takes both parts to come round
     * together.
     */
    static short[] nitroLoop(int kind) {
        Recordings.Clip hiss = Recordings.clip("nitro");
        Recordings.Clip burner = Recordings.clip("afterburner");
        if (hiss == null || burner == null || hiss.rate() != burner.rate()) {
            return null;
        }
        float[] mix = Sound3D.nitroMix(kind);
        float hissLoop = mix[0] > 0F ? hiss.pcm().length / mix[1] : 0F;
        float burnerLoop = mix[2] > 0F ? burner.pcm().length / mix[3] : 0F;
        int n = Math.round(Math.max(hissLoop, burnerLoop));
        if (hissLoop > 0F && burnerLoop > 0F) {
            // both come round together after a whole number of each: try up to eight of the longer
            for (int k = 1; k <= 8; k++) {
                float longer = Math.max(hissLoop, burnerLoop) * k;
                float times = longer / Math.min(hissLoop, burnerLoop);
                n = Math.round(longer);
                if (Math.abs(times - Math.round(times)) < 0.002F) {
                    break;
                }
            }
        }
        float[] out = new float[n];
        add(out, hiss.pcm(), mix[0], mix[1]);
        add(out, burner.pcm(), mix[2], mix[3]);
        float peak = 1F;
        for (float v : out) {
            peak = Math.max(peak, Math.abs(v));
        }
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            pcm[i] = (short) Math.round(out[i] / peak * 32767F);
        }
        return pcm;
    }

    /** Adds looping {@code clip} to {@code out} at {@code gain} (1: as recorded), played {@code pitch} times as fast. */
    private static void add(float[] out, short[] clip, float gain, float pitch) {
        if (gain <= 0F || clip.length == 0) {
            return;
        }
        for (int i = 0; i < out.length; i++) {
            double at = (i * (double) pitch) % clip.length;
            int a = (int) at;
            int b = (a + 1) % clip.length;
            float t = (float) (at - a);
            out[i] += (clip[a] + (clip[b] - clip[a]) * t) / 32768F * gain;
        }
    }

    /** The bomb put down, if its recording is missing: a metal clank and a thump. */
    static short[] clunk() {
        float[] out = new float[(int) (RATE * 0.5F)];
        Noise noise = new Noise(101);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float metal = sine(420F, t) * decay(t, 0.12F) + 0.6F * sine(1130F, t) * decay(t, 0.07F)
                    + 0.3F * sine(2210F, t) * decay(t, 0.04F);
            float thump = sine(70F, t) * decay(t, 0.05F);
            out[i] = metal * 0.6F + thump + noise.next() * decay(t, 0.004F) * 0.5F;
        }
        return pcm(out, 0.65F);
    }

    /** The stick thrown, if its recording is missing: a short swish. */
    static short[] toss() {
        float[] out = new float[(int) (RATE * 0.3F)];
        Noise noise = new Noise(103);
        float lo = 0F;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            lo += (noise.next() - lo) * 0.25F;
            float envelope = (float) Math.sin(Math.PI * Math.min(1F, t / 0.3F));
            out[i] = lo * envelope;
        }
        return pcm(out, 0.5F);
    }

    /* ----------------------------------------------------------- helpers */

    /** Noise through a simple band (two one-pole filters), {@code n} samples. */
    private static float[] band(Noise noise, int n, float high, float low) {
        float[] out = new float[n];
        float hi = 0F;
        float lo = 0F;
        for (int i = 0; i < n; i++) {
            hi += (noise.next() - hi) * high;
            lo += (hi - lo) * low;
            out[i] = hi - lo;
        }
        return out;
    }

    /** Sample {@code i} of a loop cut from {@code noise}: its start is faded into what follows its end, so it loops. */
    private static float loopNoise(float[] noise, int i) {
        if (i >= CROSSFADE) {
            return noise[i];
        }
        float w = i / (float) CROSSFADE;
        return noise[i] * w + noise[LOOP + i] * (1F - w);
    }
}
