package nfm.lit.features;

import java.util.Arrays;

/**
 * A race extra's state as plain values, saved and handed back in one order,
 * so a single method both saves and restores it: {@code x = s.value(x)}
 * stores {@code x} while saving and returns the stored value while
 * restoring, and {@code s.contents(array)} does the same for an array's
 * elements. Saving and restoring cannot drift out of step when a value is
 * added. Visiting hands each value to a {@link Visitor} instead, for the
 * race's hashes and dumps.
 * <p>
 * Run-ahead saves and restores every tick, so nothing is allocated once the
 * buffer has grown to size.
 */
public final class RaceInts {

    /** Receives each value while visiting, numbered from 0. */
    public interface Visitor {
        void value(int index, int bits);
    }

    private static final int SAVING = 0;
    private static final int RESTORING = 1;
    private static final int VISITING = 2;

    private int[] data = new int[512];
    private int pos;
    private int saved;
    private int mode = SAVING;
    private Visitor visitor;

    /** Starts saving from the beginning, replacing what was saved before. */
    public void startSaving() {
        pos = 0;
        mode = SAVING;
    }

    public void endSaving() {
        saved = pos;
    }

    /** Starts handing the saved values back, from the first. */
    public void startRestoring() {
        pos = 0;
        mode = RESTORING;
    }

    /** Ends restoring; every saved value must have been read back. */
    public void endRestoring() {
        if (pos != saved) {
            throw new IllegalStateException("restored " + pos + " values of the race extras, saved " + saved);
        }
        mode = SAVING;
    }

    /** Starts handing every value to {@code v}, changing nothing. */
    public void startVisiting(Visitor v) {
        pos = 0;
        mode = VISITING;
        visitor = v;
    }

    public void endVisiting() {
        visitor = null;
        mode = SAVING;
    }

    /** How many values were saved. */
    public int size() {
        return saved;
    }

    /** True if {@code other} saved exactly the values this one did. */
    public boolean sameAs(RaceInts other) {
        return saved == other.saved && Arrays.equals(data, 0, saved, other.data, 0, saved);
    }

    public int value(int v) {
        switch (mode) {
            case RESTORING -> {
                return data[pos++];
            }
            case VISITING -> {
                visitor.value(pos++, v);
                return v;
            }
            default -> {
                if (pos == data.length) {
                    data = Arrays.copyOf(data, data.length * 2);
                }
                data[pos++] = v;
                return v;
            }
        }
    }

    public float value(float v) {
        return Float.intBitsToFloat(value(Float.floatToRawIntBits(v)));
    }

    public boolean value(boolean v) {
        return value(v ? 1 : 0) != 0;
    }

    /** A 64-bit value, as two. */
    public long value(long v) {
        long low = value((int) v) & 0xFFFFFFFFL;
        long high = value((int) (v >>> 32));
        return low | high << 32;
    }

    public void contents(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(a[i]);
        }
    }

    public void contents(float[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(a[i]);
        }
    }

    public void contents(boolean[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(a[i]);
        }
    }
}
