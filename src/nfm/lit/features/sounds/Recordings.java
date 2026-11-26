package nfm.lit.features.sounds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Features;

/**
 * The extras' sounds that are real recordings: each a 16-bit mono WAV next
 * to this class, cut from a recording by {@code sounds\make.py} and sampled
 * down to one of the game's own rates. {@code sounds\sources.tsv} names
 * every recording, who made it and its license; {@code sounds\clips.tsv}
 * what was cut from it. They go into {@code madness.jar} with the extras'
 * classes and are read as class-path resources, as the car sheets are.
 */
public final class Recordings {

    /** One clip: its samples and how many play a second. */
    public record Clip(short[] pcm, int rate) {

        /** How long it lasts, seconds. */
        public float seconds() {
            return pcm.length / (float) rate;
        }
    }

    private static final Clip MISSING = new Clip(new short[0], 11025);
    private static final Map<String, Clip> clips = new ConcurrentHashMap<>();

    private Recordings() {
    }

    /** Recording {@code name} (the file's name without {@code .wav}), read once; null if there is none. */
    public static Clip clip(String name) {
        Clip c = clips.computeIfAbsent(name, Recordings::read);
        return c == MISSING ? null : c;
    }

    /**
     * Gives the 3D sound recording {@code recording} as its clip
     * {@code as}, once; with {@code classic}, Classic sound too. False if
     * there is no such recording.
     */
    public static boolean give(String as, String recording, boolean classic) {
        if (Sound3D.hasExtraClip(as)) {
            return true;
        }
        Clip c = clip(recording);
        if (c == null) {
            return false;
        }
        Sound3D.extraClip(as, c.pcm, c.rate);
        if (classic) {
            Features.hooks().classicClip(as, c.pcm, c.rate);
        }
        return true;
    }

    /** One line of the credits: a clip, the recording it was cut from, who made that, its license and what was changed. */
    public record Credit(String clip, int rate, String kind, String title, String author, String license,
            String page, String changes) {
    }

    /**
     * The credits of every recording ({@code credits.txt}, which
     * {@code sounds\make.py} writes from its tables); none if the file is
     * not there.
     */
    public static java.util.List<Credit> credits() {
        java.util.List<Credit> all = new java.util.ArrayList<>();
        try (InputStream in = Recordings.class.getResourceAsStream("credits.txt")) {
            if (in == null) {
                return all;
            }
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\t");
                if (line.startsWith("#") || f.length < 8) {
                    continue;
                }
                all.add(new Credit(f[0], Integer.parseInt(f[1].trim()), f[2], f[3], f[4], f[5], f[6], f[7].trim()));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[sounds] credits.txt could not be read: " + e);
        }
        return all;
    }

    private static Clip read(String name) {
        try (InputStream in = Recordings.class.getResourceAsStream(name + ".wav")) {
            if (in == null) {
                return MISSING;
            }
            return parse(in.readAllBytes());
        } catch (IOException | RuntimeException e) {
            System.err.println("[sounds] " + name + ".wav could not be read: " + e);
            return MISSING;
        }
    }

    /** A 16-bit mono PCM WAV's samples and rate. */
    static Clip parse(byte[] file) {
        ByteBuffer b = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        int rate = 0;
        int at = 12; // after RIFF, the size and WAVE
        while (at + 8 <= file.length) {
            int id = b.getInt(at);
            int size = b.getInt(at + 4);
            if (id == 0x20746d66) { // "fmt "
                if (b.getShort(at + 8) != 1 || b.getShort(at + 10) != 1 || b.getShort(at + 22) != 16) {
                    throw new IllegalArgumentException("not 16-bit mono PCM");
                }
                rate = b.getInt(at + 12);
            } else if (id == 0x61746164) { // "data"
                int samples = Math.min(size, file.length - at - 8) / 2;
                short[] pcm = new short[samples];
                b.position(at + 8);
                b.asShortBuffer().get(pcm);
                if (rate == 0) {
                    throw new IllegalArgumentException("no format before the samples");
                }
                return new Clip(pcm, rate);
            }
            at += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("no samples");
    }
}
