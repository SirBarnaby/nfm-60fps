package nfm.lit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * One field read and written by name rather than by code: the race state
 * {@link StateCopy} saves and puts back, the values {@link RaceState} records
 * and checks, the HUD fields {@link PlayerHud} swaps. Each goes through a
 * {@link VarHandle} called with the field's exact type.
 * <p>
 * Why not {@link Field#get}: since Java 18 every reflected field reads and
 * writes through a method handle of its own, and after 127 uses Java rebuilds
 * each such handle as a class of its own ("customizing" it). A run-ahead tick
 * captures and restores about 1,600 fields, so all of them reached that count
 * together, about a second into the first race, and rebuilding them stood the
 * race still for 60 ms and more; a recorded race reads as many every tick. A
 * VarHandle called with the field's exact type goes straight to the field and
 * is never rebuilt; primitive values also stay unboxed ({@link #bits}).
 */
final class FieldSlot {

    /** {@link #kind} for a field that holds a reference. */
    static final char REFERENCE = 'L';

    final Field field;
    /** What the field holds: one of the primitive type letters of the JVM, or {@link #REFERENCE}. */
    final char kind;
    /* a final field is only read: the put calls leave it as it is (its VarHandle cannot write it) */
    private final boolean isFinal;
    private final boolean isStatic;
    private final VarHandle handle;

    FieldSlot(Field field) {
        this.field = field;
        int modifiers = field.getModifiers();
        isStatic = Modifier.isStatic(modifiers);
        isFinal = Modifier.isFinal(modifiers);
        Class<?> type = field.getType();
        kind = type.isPrimitive() ? type.descriptorString().charAt(0) : REFERENCE;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup());
            handle = lookup.unreflectVarHandle(field);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(field.toString(), ex);
        }
    }

    boolean primitive() {
        return kind != REFERENCE;
    }

    /** A primitive field's value as raw bits (floats and doubles as their bit patterns); {@code target} null for a static. */
    long bits(Object target) {
        return isStatic ? staticBits() : instanceBits(target);
    }

    private long instanceBits(Object o) {
        return switch (kind) {
            case 'I' -> (int) handle.get(o);
            case 'F' -> Float.floatToRawIntBits((float) handle.get(o));
            case 'Z' -> (boolean) handle.get(o) ? 1L : 0L;
            case 'J' -> (long) handle.get(o);
            case 'D' -> Double.doubleToRawLongBits((double) handle.get(o));
            case 'S' -> (short) handle.get(o);
            case 'B' -> (byte) handle.get(o);
            case 'C' -> (char) handle.get(o);
            default -> throw new IllegalStateException(field + " holds a reference");
        };
    }

    private long staticBits() {
        return switch (kind) {
            case 'I' -> (int) handle.get();
            case 'F' -> Float.floatToRawIntBits((float) handle.get());
            case 'Z' -> (boolean) handle.get() ? 1L : 0L;
            case 'J' -> (long) handle.get();
            case 'D' -> Double.doubleToRawLongBits((double) handle.get());
            case 'S' -> (short) handle.get();
            case 'B' -> (byte) handle.get();
            case 'C' -> (char) handle.get();
            default -> throw new IllegalStateException(field + " holds a reference");
        };
    }

    /** Puts a primitive field's value back from {@link #bits}; a final field keeps its own. */
    void putBits(Object target, long bits) {
        if (isFinal) {
            return;
        }
        if (isStatic) {
            putStaticBits(bits);
        } else {
            putInstanceBits(target, bits);
        }
    }

    private void putInstanceBits(Object o, long v) {
        switch (kind) {
            case 'I' -> handle.set(o, (int) v);
            case 'F' -> handle.set(o, Float.intBitsToFloat((int) v));
            case 'Z' -> handle.set(o, v != 0L);
            case 'J' -> handle.set(o, v);
            case 'D' -> handle.set(o, Double.longBitsToDouble(v));
            case 'S' -> handle.set(o, (short) v);
            case 'B' -> handle.set(o, (byte) v);
            case 'C' -> handle.set(o, (char) v);
            default -> throw new IllegalStateException(field + " holds a reference");
        }
    }

    private void putStaticBits(long v) {
        switch (kind) {
            case 'I' -> handle.set((int) v);
            case 'F' -> handle.set(Float.intBitsToFloat((int) v));
            case 'Z' -> handle.set(v != 0L);
            case 'J' -> handle.set(v);
            case 'D' -> handle.set(Double.longBitsToDouble(v));
            case 'S' -> handle.set((short) v);
            case 'B' -> handle.set((byte) v);
            case 'C' -> handle.set((char) v);
            default -> throw new IllegalStateException(field + " holds a reference");
        }
    }

    /** A reference field's value; {@code target} null for a static. */
    Object ref(Object target) {
        return isStatic ? (Object) handle.get() : (Object) handle.get(target);
    }

    /** Points a reference field at {@code value}; a final field keeps its own. */
    void putRef(Object target, Object value) {
        if (isFinal) {
            return;
        }
        if (isStatic) {
            handle.set(value);
        } else {
            handle.set(target, value);
        }
    }

    /** A name for reports: {@code "Plane.av"}. */
    String name() {
        return field.getDeclaringClass().getSimpleName() + "." + field.getName();
    }
}
