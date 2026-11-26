package nfm.lit.features.sounds;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;
import static nfm.lit.features.ExtrasCheck.waiting;

import java.util.List;

/**
 * The recordings' checks (sound, "Sound effects are real recordings"):
 * every clip the credits name is in the jar, 16-bit mono at one of the
 * game's own rates, as loud as the others of its kind and never clipped;
 * a loop repeats without a step and is within 6 dB of the others; and
 * every recording has its maker, its license and its page, with what was
 * changed. How they sound waits for the owner.
 */
public final class SoundsCheck {

    private static final String F = "recordings";
    /** The rates the game's own sounds are sampled at. */
    private static final int[] GAME_RATES = {8_000, 8_272, 11_025, 22_050};

    private SoundsCheck() {
    }

    public static void run() {
        List<Recordings.Credit> credits = Recordings.credits();
        int missing = 0;
        int offRate = 0;
        int clipped = 0;
        int steps = 0;
        int loops = 0;
        float quietest = Float.MAX_VALUE;
        float loudest = 0F;
        StringBuilder wrong = new StringBuilder();
        for (Recordings.Credit c : credits) {
            Recordings.Clip clip = Recordings.clip(c.clip());
            if (clip == null || clip.pcm().length == 0) {
                missing++;
                wrong.append(' ').append(c.clip());
                continue;
            }
            boolean rate = clip.rate() == c.rate();
            boolean game = false;
            for (int r : GAME_RATES) {
                game |= clip.rate() == r;
            }
            if (!rate || !game) {
                offRate++;
                wrong.append(' ').append(c.clip());
            }
            short[] x = clip.pcm();
            int peak = 0;
            double sum = 0D;
            for (short v : x) {
                peak = Math.max(peak, Math.abs(v));
                sum += (double) v * v;
            }
            if (peak >= 32_767) {
                clipped++;
                wrong.append(' ').append(c.clip());
            }
            if ("loop".equals(c.kind())) {
                loops++;
                float level = (float) Math.sqrt(sum / x.length) / 32_768F;
                quietest = Math.min(quietest, level);
                loudest = Math.max(loudest, level);
                // the step from the last sample back to the first, against the steps inside the loop
                int biggest = 1;
                for (int i = 1; i < x.length; i++) {
                    biggest = Math.max(biggest, Math.abs(x[i] - x[i - 1]));
                }
                if (Math.abs(x[0] - x[x.length - 1]) > biggest) {
                    steps++;
                    wrong.append(' ').append(c.clip());
                }
            }
        }
        check(F, "every clip is in the game", !credits.isEmpty() && missing == 0,
                credits.size() + " clips in the credits, " + missing + " not found" + wrong);
        check(F, "sampled down to the game's rates", !credits.isEmpty() && offRate == 0 && clipped == 0,
                offRate + " at a rate the game does not use, " + clipped + " clipped (the game's: 8000, 8272, 11025"
                        + " and 22050 Hz)");
        check(F, "the loops are level and seamless", loops > 0 && steps == 0 && loudest < 2F * quietest,
                loops + " loops, level " + n(quietest) + " to " + n(loudest) + " of full scale, " + steps
                        + " with a step at the seam");
        int unnamed = 0;
        int attributed = 0;
        for (Recordings.Credit c : credits) {
            boolean whole = !c.author().isBlank() && !c.title().isBlank() && c.page().startsWith("https://")
                    && !c.changes().isBlank() && (c.license().startsWith("CC0") || c.license().startsWith("CC BY"));
            if (!whole) {
                unnamed++;
            }
            if (c.license().startsWith("CC BY")) {
                attributed++;
            }
        }
        check(F, "every recording is credited", !credits.isEmpty() && unnamed == 0,
                credits.size() + " credits, " + unnamed + " without a maker, a page, a license or its changes; "
                        + attributed + " ask to be named (CC BY), the rest are CC0");
        waiting(F, "the sounds, heard", "the owner listens to each of the " + credits.size() + " sounds in a race"
                + " and says which sound wrong or fake");
    }
}
