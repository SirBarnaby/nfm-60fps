package nfm.lit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A recorded race: how it was set up, the player's input on every tick, and
 * what the race state looked like after every tick.
 * <p>
 * The file (gzip, {@code .nfmr}) holds everything the verifier needs to play
 * the race again without the player: the seed, stage, cars and the state the
 * race started from. Ticks count from the start of the countdown. Each tick
 * stores the input the race read, one hash per state group of the full state
 * and one hash of the canonical state (see {@link RaceState}); every
 * {@link #DUMP_INTERVAL} ticks the values themselves are stored as well, so a
 * mismatch can be traced to the exact fields. The full state depends on how
 * the classes are laid out, so {@link #layout} records that; the canonical
 * state is meant to stay comparable when the code is reorganised.
 */
final class RaceLog {

    private static final int MAGIC = 0x4E464D52; // "NFMR"
    private static final int VERSION = 2;
    /** Ticks between full state dumps: about two seconds of racing. */
    static final int DUMP_INTERVAL = 40;

    /* Input bits, one byte per tick. */
    static final int KEY_UP = 1;
    static final int KEY_DOWN = 2;
    static final int KEY_LEFT = 4;
    static final int KEY_RIGHT = 8;
    static final int KEY_HANDBRAKE = 16;
    static final int KEY_ENTER = 32;

    /** The player's held controls as input bits. */
    static int keyBits(Control u) {
        return (u.up ? KEY_UP : 0) | (u.down ? KEY_DOWN : 0) | (u.left ? KEY_LEFT : 0) | (u.right ? KEY_RIGHT : 0)
                | (u.handb ? KEY_HANDBRAKE : 0) | (u.enter ? KEY_ENTER : 0);
    }

    /** Sets the player's controls from input bits, the inverse of {@link #keyBits}. */
    static void applyKeys(Control u, int bits) {
        u.up = (bits & KEY_UP) != 0;
        u.down = (bits & KEY_DOWN) != 0;
        u.left = (bits & KEY_LEFT) != 0;
        u.right = (bits & KEY_RIGHT) != 0;
        u.handb = (bits & KEY_HANDBRAKE) != 0;
        u.enter = (bits & KEY_ENTER) != 0;
    }

    /** Something that happened between two ticks. */
    enum Event {
        /** The player watched an instant replay from the pause menu. */
        REPLAY
    }

    /** How the recording ended. */
    enum End {
        /** The race reached its end: someone won or every opponent was wasted. */
        FINISHED,
        /** The player left the race from the pause menu. */
        QUIT,
        /** The game was closed during the race. */
        CLOSED
    }

    /* Set-up. */
    String note = "";
    long seed;
    int stage;
    int nfmmode;
    int players;
    int unlocked;
    /** Car ids before the stage was set up: the player's pick and leftovers. */
    int[] carsBefore = new int[0];
    /** Car ids the stage set-up chose, checked by the verifier. */
    int[] carsAfter = new int[0];
    /** Fingerprint of the full state's field layout, see {@link RaceState#layout}. */
    int layout;
    int[] startState = new int[0];
    List<String> startNames = new ArrayList<>();
    int[] raceRandom = new int[0];
    /** The track's collision boxes, see {@link RaceState#trackShape}. */
    int[] trackShape = new int[0];
    /** What earlier stages left in the track pieces, see {@link RaceState#trackLeftovers}. */
    int[] trackLeftovers = new int[0];
    End end = End.CLOSED;

    /* Ticks. */
    private int ticks;
    private byte[] keys = new byte[4096];
    private byte[] lookback = new byte[4096];
    private byte[] view = new byte[4096];
    private byte[] spectate = new byte[4096];
    private int[] hashes = new int[0];
    private int[] canonical = new int[4096];
    private int groups;

    private final List<int[]> events = new ArrayList<>();
    private final List<int[]> dumps = new ArrayList<>();
    private final List<int[]> canonicalDumps = new ArrayList<>();
    private final List<Integer> dumpTicks = new ArrayList<>();

    void setGroups(int groups) {
        this.groups = groups;
        hashes = new int[4096 * groups];
    }

    int ticks() {
        return ticks;
    }

    void addTick(int keyBits, int look, int cameraView, int spectated, int[] tickHashes, int canonicalHash) {
        if (ticks == keys.length) {
            int n = ticks * 2;
            keys = Arrays.copyOf(keys, n);
            lookback = Arrays.copyOf(lookback, n);
            view = Arrays.copyOf(view, n);
            spectate = Arrays.copyOf(spectate, n);
            hashes = Arrays.copyOf(hashes, n * groups);
            canonical = Arrays.copyOf(canonical, n);
        }
        canonical[ticks] = canonicalHash;
        keys[ticks] = (byte) keyBits;
        lookback[ticks] = (byte) look;
        view[ticks] = (byte) cameraView;
        spectate[ticks] = (byte) spectated;
        System.arraycopy(tickHashes, 0, hashes, ticks * groups, groups);
        ticks++;
    }

    int keys(int tick) {
        return keys[tick];
    }

    int lookback(int tick) {
        return lookback[tick];
    }

    int view(int tick) {
        return view[tick];
    }

    int spectate(int tick) {
        return spectate[tick];
    }

    int hash(int tick, int group) {
        return hashes[tick * groups + group];
    }

    int canonicalHash(int tick) {
        return canonical[tick];
    }

    void addEvent(int afterTick, Event event) {
        events.add(new int[] {afterTick, event.ordinal()});
    }

    /** Number of events of this kind recorded after the given tick. */
    int countEvents(int afterTick, Event event) {
        int n = 0;
        for (int[] e : events) {
            if (e[0] == afterTick && e[1] == event.ordinal()) {
                n++;
            }
        }
        return n;
    }

    void addDump(int tick, int[] values, int[] canonicalValues) {
        dumpTicks.add(tick);
        dumps.add(values);
        canonicalDumps.add(canonicalValues);
    }

    /** The first full dump taken at or after the tick, or null. */
    int[] dumpAtOrAfter(int tick) {
        int i = dumpIndex(tick);
        return i < 0 ? null : dumps.get(i);
    }

    /** The first canonical dump taken at or after the tick, or null. */
    int[] canonicalDumpAtOrAfter(int tick) {
        int i = dumpIndex(tick);
        return i < 0 ? null : canonicalDumps.get(i);
    }

    private int dumpIndex(int tick) {
        for (int i = 0; i < dumpTicks.size(); i++) {
            if (dumpTicks.get(i) >= tick) {
                return i;
            }
        }
        return -1;
    }

    /** The tick of the first dump at or after the tick, or -1. */
    int dumpTickAtOrAfter(int tick) {
        int i = dumpIndex(tick);
        return i < 0 ? -1 : dumpTicks.get(i);
    }

    void write(File file) throws IOException {
        File tmp = new File(file.getPath() + ".part");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(note);
            out.writeLong(seed);
            out.writeInt(stage);
            out.writeInt(nfmmode);
            out.writeInt(players);
            out.writeInt(unlocked);
            writeInts(out, carsBefore);
            writeInts(out, carsAfter);
            out.writeInt(layout);
            writeInts(out, startState);
            out.writeInt(startNames.size());
            for (String name : startNames) {
                out.writeUTF(name);
            }
            writeInts(out, raceRandom);
            writeInts(out, trackShape);
            writeInts(out, trackLeftovers);
            out.writeUTF(end.name());

            out.writeInt(groups);
            out.writeInt(ticks);
            out.write(keys, 0, ticks);
            out.write(lookback, 0, ticks);
            out.write(view, 0, ticks);
            out.write(spectate, 0, ticks);
            for (int i = 0; i < ticks * groups; i++) {
                out.writeInt(hashes[i]);
            }
            for (int i = 0; i < ticks; i++) {
                out.writeInt(canonical[i]);
            }

            out.writeInt(events.size());
            for (int[] e : events) {
                out.writeInt(e[0]);
                out.writeInt(e[1]);
            }

            /*
             * Each dump is stored as the difference from the one before: most
             * values do not change in a second, so the file compresses well.
             */
            out.writeInt(dumps.size());
            int[] previous = new int[0];
            int[] previousCanonical = new int[0];
            for (int i = 0; i < dumps.size(); i++) {
                out.writeInt(dumpTicks.get(i));
                writeInts(out, xor(dumps.get(i), previous));
                writeInts(out, xor(canonicalDumps.get(i), previousCanonical));
                previous = dumps.get(i);
                previousCanonical = canonicalDumps.get(i);
            }
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static RaceLog read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            if (in.readInt() != MAGIC) {
                throw new IOException(file + " is not a recorded race");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException(file + " was recorded by format " + version + ", this build reads " + VERSION);
            }
            RaceLog log = new RaceLog();
            log.note = in.readUTF();
            log.seed = in.readLong();
            log.stage = in.readInt();
            log.nfmmode = in.readInt();
            log.players = in.readInt();
            log.unlocked = in.readInt();
            log.carsBefore = readInts(in);
            log.carsAfter = readInts(in);
            log.layout = in.readInt();
            log.startState = readInts(in);
            int names = in.readInt();
            for (int i = 0; i < names; i++) {
                log.startNames.add(in.readUTF());
            }
            log.raceRandom = readInts(in);
            log.trackShape = readInts(in);
            log.trackLeftovers = readInts(in);
            log.end = End.valueOf(in.readUTF());

            log.groups = in.readInt();
            int n = in.readInt();
            log.ticks = n;
            log.keys = new byte[n];
            log.lookback = new byte[n];
            log.view = new byte[n];
            log.spectate = new byte[n];
            in.readFully(log.keys, 0, n);
            in.readFully(log.lookback, 0, n);
            in.readFully(log.view, 0, n);
            in.readFully(log.spectate, 0, n);
            log.hashes = new int[n * log.groups];
            for (int i = 0; i < log.hashes.length; i++) {
                log.hashes[i] = in.readInt();
            }
            log.canonical = new int[n];
            for (int i = 0; i < n; i++) {
                log.canonical[i] = in.readInt();
            }

            int events = in.readInt();
            for (int i = 0; i < events; i++) {
                log.events.add(new int[] {in.readInt(), in.readInt()});
            }

            int dumps = in.readInt();
            int[] previous = new int[0];
            int[] previousCanonical = new int[0];
            for (int i = 0; i < dumps; i++) {
                log.dumpTicks.add(in.readInt());
                previous = xor(readInts(in), previous);
                previousCanonical = xor(readInts(in), previousCanonical);
                log.dumps.add(previous);
                log.canonicalDumps.add(previousCanonical);
            }
            return log;
        }
    }

    /**
     * XOR with the previous dump, when it has the same length. Applying it
     * again gives the original back, so it both encodes and decodes.
     */
    private static int[] xor(int[] values, int[] previous) {
        int[] out = values.clone();
        if (previous.length == values.length) {
            for (int i = 0; i < out.length; i++) {
                out[i] ^= previous[i];
            }
        }
        return out;
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values.length);
        for (int v : values) {
            out.writeInt(v);
        }
    }

    private static int[] readInts(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 50_000_000) {
            throw new IOException("corrupt race file");
        }
        int[] values = new int[n];
        for (int i = 0; i < n; i++) {
            values[i] = in.readInt();
        }
        return values;
    }
}
