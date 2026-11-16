package nfm.lit;

import java.util.Arrays;

/**
 * Plain values saved in order and handed back in the same order, for state
 * that one method both saves and restores: {@code x = buffer.value(x)} stores
 * {@code x} while saving and returns the stored value while restoring, and
 * {@code buffer.contents(array)} does the same for an array's elements. With
 * a single method for both directions, saving and restoring cannot drift out
 * of step when a field is added.
 * <p>
 * References are kept as they are, so restoring points each field back at
 * the object it held. The buffer is reused: saving again overwrites what it
 * held, without allocating once it has grown to size.
 */
final class StateBuffer {

    private int[] data = new int[8192];
    private int pos;
    private int saved;
    private Object[] refs = new Object[256];
    private int refPos;
    private int refsSaved;
    private boolean restoring;

    /** Starts saving from the beginning, replacing what was saved before. */
    void startSaving() {
        pos = 0;
        refPos = 0;
        restoring = false;
    }

    /** Ends saving. */
    void endSaving() {
        saved = pos;
        refsSaved = refPos;
        Arrays.fill(refs, refPos, refs.length, null);
    }

    /** Starts handing the saved values back, from the first. */
    void startRestoring() {
        pos = 0;
        refPos = 0;
        restoring = true;
    }

    /** Ends restoring; every saved value must have been read back. */
    void endRestoring() {
        if (pos != saved || refPos != refsSaved) {
            throw new IllegalStateException("restored " + pos + " values and " + refPos + " references, saved "
                    + saved + " and " + refsSaved);
        }
    }

    int value(int v) {
        if (restoring) {
            return data[pos++];
        }
        ensure(1);
        data[pos++] = v;
        return v;
    }

    float value(float v) {
        return Float.intBitsToFloat(value(Float.floatToRawIntBits(v)));
    }

    boolean value(boolean v) {
        return value(v ? 1 : 0) != 0;
    }

    @SuppressWarnings("unchecked")
    <T> T ref(T v) {
        if (restoring) {
            return (T) refs[refPos++];
        }
        if (refPos == refs.length) {
            refs = Arrays.copyOf(refs, refs.length * 2);
        }
        refs[refPos++] = v;
        return v;
    }

    void contents(Object[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = ref(a[i]);
        }
    }

    void contents(int[][] a) {
        for (int[] row : a) {
            contents(row);
        }
    }

    void contents(int[][][] a) {
        for (int[][] plane : a) {
            contents(plane);
        }
    }

    void contents(float[][][] a) {
        for (float[][] plane : a) {
            for (float[] row : plane) {
                contents(row);
            }
        }
    }

    void contents(boolean[][] a) {
        for (boolean[] row : a) {
            contents(row);
        }
    }

    void contents(boolean[][][] a) {
        for (boolean[][] plane : a) {
            contents(plane);
        }
    }

    void contents(int[] a) {
        if (restoring) {
            System.arraycopy(data, pos, a, 0, a.length);
        } else {
            ensure(a.length);
            System.arraycopy(a, 0, data, pos, a.length);
        }
        pos += a.length;
    }

    void contents(float[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(a[i]);
        }
    }

    void contents(boolean[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = value(a[i]);
        }
    }

    private void ensure(int n) {
        if (pos + n > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length * 2, pos + n));
        }
    }
}
