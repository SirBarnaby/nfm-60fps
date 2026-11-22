package nfm.lit;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A deep copy of the plain state of an object graph, so it can be put back
 * exactly later.
 * <p>
 * Every primitive field and every primitive array (of any dimension) of a
 * captured object is copied. References into the classes in the followed set
 * are followed and those objects captured too; any other reference is kept as
 * it was, and put back as it was. Restoring writes the values back into the
 * same arrays and objects, and points every field back at the object it held,
 * so code that shares an array or an object sees the old state again too.
 * <p>
 * Final fields are never reassigned, only their contents restored.
 * <p>
 * Fields are read and written through {@link FieldSlot}s, never
 * {@link Field#get}: the run-ahead tick captures and restores with this
 * class every tick (see FieldSlot for what reflection cost there), and
 * primitive values are kept unboxed.
 */
final class StateCopy {

    private static final Map<Class<?>, FieldSlot[]> INSTANCE_SLOTS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, FieldSlot[]> STATIC_SLOTS = new ConcurrentHashMap<>();

    private final Set<Class<?>> followed;
    /** Fields whose objects are kept by reference only, even if their class is followed. */
    private final Set<Field> shallow;
    private final Map<Object, Saved> objects = new IdentityHashMap<>();
    /** The statics captured, and some fields of objects that are not captured whole. */
    private final List<Saved> parts = new ArrayList<>();
    private final List<ArrayCopy> arrays = new ArrayList<>();

    StateCopy(Set<Class<?>> followed, Set<Field> shallow) {
        this.followed = followed;
        this.shallow = shallow;
    }

    /**
     * The values of some fields of {@code target} (null for statics): a
     * primitive's raw bits in {@code bits}, a reference's saved form
     * ({@link Ref}, {@link ArrayCopy} or a boxed value) in {@code refs}.
     */
    private record Saved(Object target, FieldSlot[] slots, long[] bits, Object[] refs) {
    }

    /** Captures an object and everything it reaches in the followed classes. */
    void capture(Object root) {
        if (root == null || objects.containsKey(root)) {
            return;
        }
        if (root.getClass().isArray()) {
            throw new IllegalArgumentException("capture the object that holds the array");
        }
        FieldSlot[] slots = instanceSlots(root.getClass());
        Saved saved = new Saved(root, slots, new long[slots.length], new Object[slots.length]);
        objects.put(root, saved);
        save(saved, false);
    }

    /** Captures the static fields of a class, and what they reach. */
    void captureStatics(Class<?> type) {
        FieldSlot[] slots = staticSlots(type);
        Saved saved = new Saved(null, slots, new long[slots.length], new Object[slots.length]);
        parts.add(saved);
        save(saved, false);
    }

    /** Captures only the given fields of an object (null for statics), and what they reach. */
    void captureFields(Object target, FieldSlot[] slots) {
        Saved saved = new Saved(target, slots, new long[slots.length], new Object[slots.length]);
        parts.add(saved);
        save(saved, true);
    }

    /** Captures an array's contents, and what they reach. */
    void captureArray(Object array) {
        arrays.add(new ArrayCopy(array, copyArray(array, true)));
    }

    /** Saves what {@code saved}'s fields hold now; {@code followAll} follows even the shallow fields. */
    private void save(Saved saved, boolean followAll) {
        FieldSlot[] slots = saved.slots;
        Object target = saved.target;
        for (int i = 0; i < slots.length; i++) {
            FieldSlot s = slots[i];
            if (s.primitive()) {
                saved.bits[i] = s.bits(target);
            } else {
                saved.refs[i] = copyOf(s.ref(target), followAll || !shallow.contains(s.field));
            }
        }
    }

    /** Puts every captured value back. */
    void restore() {
        for (ArrayCopy a : arrays) {
            restoreArray(a);
        }
        for (Saved saved : parts) {
            put(saved);
        }
        for (Saved saved : objects.values()) {
            put(saved);
        }
    }

    private static void put(Saved saved) {
        FieldSlot[] slots = saved.slots;
        Object target = saved.target;
        for (int i = 0; i < slots.length; i++) {
            FieldSlot s = slots[i];
            if (s.primitive()) {
                if (!s.isFinal) {
                    s.putBits(target, saved.bits[i]);
                }
                continue;
            }
            Object value;
            Object kept = saved.refs[i];
            if (kept instanceof ArrayCopy copy) {
                restoreArray(copy);
                value = copy.array();
            } else if (kept instanceof Ref ref) {
                value = ref.value();
            } else {
                value = kept;
            }
            if (!s.isFinal) {
                s.putRef(target, value);
            }
        }
    }

    /**
     * Compares everything captured with the values held now and counts the
     * differences by field, as {@code "Plane.av" -> number of objects}. The
     * rollback self-test uses it to prove that a smaller snapshot puts back
     * everything a race changed.
     */
    Map<String, Integer> differences() {
        Map<String, Integer> out = new TreeMap<>();
        for (ArrayCopy a : arrays) {
            if (!sameContents(a)) {
                out.merge(a.array().getClass().getSimpleName(), 1, Integer::sum);
            }
        }
        for (Saved saved : parts) {
            compare(saved, out);
        }
        for (Saved saved : objects.values()) {
            compare(saved, out);
        }
        return out;
    }

    private static void compare(Saved saved, Map<String, Integer> out) {
        FieldSlot[] slots = saved.slots;
        for (int i = 0; i < slots.length; i++) {
            FieldSlot s = slots[i];
            boolean same = s.primitive() ? s.bits(saved.target) == saved.bits[i]
                    : same(s.ref(saved.target), saved.refs[i]);
            if (!same) {
                out.merge(s.name(), 1, Integer::sum);
            }
        }
    }

    private static boolean same(Object now, Object saved) {
        if (saved instanceof ArrayCopy copy) {
            return now == copy.array() && sameContents(copy);
        }
        if (saved instanceof Ref ref) {
            return now == ref.value();
        }
        return saved.equals(now);
    }

    private static boolean sameContents(ArrayCopy saved) {
        Object array = saved.array();
        Object contents = saved.contents();
        if (array.getClass().getComponentType().isPrimitive()) {
            return switch (array) {
                case int[] a -> Arrays.equals(a, (int[]) contents);
                case float[] a -> Arrays.equals(a, (float[]) contents);
                case boolean[] a -> Arrays.equals(a, (boolean[]) contents);
                case long[] a -> Arrays.equals(a, (long[]) contents);
                case double[] a -> Arrays.equals(a, (double[]) contents);
                case short[] a -> Arrays.equals(a, (short[]) contents);
                case byte[] a -> Arrays.equals(a, (byte[]) contents);
                case char[] a -> Arrays.equals(a, (char[]) contents);
                default -> throw new IllegalStateException(array.getClass().toString());
            };
        }
        Object[] elements = (Object[]) contents;
        Object[] target = (Object[]) array;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof ArrayCopy nested) {
                if (target[i] != nested.array() || !sameContents(nested)) {
                    return false;
                }
            } else if (target[i] != elements[i]) {
                return false;
            }
        }
        return true;
    }

    /* ---------------------------------------------------------------- values */

    /** A reference to an array, with a copy of what it held. */
    private record ArrayCopy(Object array, Object contents) {
    }

    /** A reference kept as it was. */
    private record Ref(Object value) {
    }

    private Object copyOf(Object value, boolean follow) {
        if (value == null) {
            return new Ref(null);
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return new ArrayCopy(value, copyArray(value, follow));
        }
        if (follow && followed.contains(type)) {
            capture(value);
        }
        return isBoxed(type) ? value : new Ref(value);
    }

    /**
     * Copies an array's contents: primitives by value, nested arrays as
     * {@link ArrayCopy}, objects by reference (captured if followed).
     */
    private Object copyArray(Object array, boolean follow) {
        Class<?> component = array.getClass().getComponentType();
        int n = Array.getLength(array);
        if (component.isPrimitive()) {
            Object copy = Array.newInstance(component, n);
            System.arraycopy(array, 0, copy, 0, n);
            return copy;
        }
        Object[] from = (Object[]) array;
        Object[] copy = new Object[n];
        for (int i = 0; i < n; i++) {
            Object element = from[i];
            if (element != null && element.getClass().isArray()) {
                copy[i] = new ArrayCopy(element, copyArray(element, follow));
            } else {
                if (element != null && follow && followed.contains(element.getClass())) {
                    capture(element);
                }
                copy[i] = element;
            }
        }
        return copy;
    }

    private static void restoreArray(ArrayCopy saved) {
        Object array = saved.array();
        Object contents = saved.contents();
        if (array.getClass().getComponentType().isPrimitive()) {
            System.arraycopy(contents, 0, array, 0, Array.getLength(contents));
            return;
        }
        Object[] elements = (Object[]) contents;
        Object[] target = (Object[]) array;
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof ArrayCopy nested) {
                restoreArray(nested);
                target[i] = nested.array();
            } else {
                target[i] = elements[i];
            }
        }
    }

    private static boolean isBoxed(Class<?> type) {
        return type == Integer.class || type == Float.class || type == Boolean.class || type == Long.class
                || type == Double.class || type == Short.class || type == Byte.class || type == Character.class;
    }

    /* ---------------------------------------------------------------- fields */

    private static FieldSlot[] instanceSlots(Class<?> type) {
        return INSTANCE_SLOTS.computeIfAbsent(type, t -> slots(fields(t, false)));
    }

    /** The static fields a capture of {@code type} covers. */
    static FieldSlot[] staticSlots(Class<?> type) {
        return STATIC_SLOTS.computeIfAbsent(type, t -> slots(fields(t, true)));
    }

    /** Slots for the given fields, for {@link #captureFields}. */
    static FieldSlot[] slots(Field[] fields) {
        FieldSlot[] out = new FieldSlot[fields.length];
        for (int i = 0; i < fields.length; i++) {
            out[i] = new FieldSlot(fields[i]);
        }
        return out;
    }

    private static Field[] fields(Class<?> type, boolean wantStatic) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                boolean isStatic = Modifier.isStatic(f.getModifiers());
                if (isStatic != wantStatic || f.isSynthetic()) {
                    continue;
                }
                if (isStatic && Modifier.isFinal(f.getModifiers()) && f.getType().isPrimitive()) {
                    continue;
                }
                f.setAccessible(true);
                out.add(f);
            }
            if (wantStatic) {
                break;
            }
        }
        return out.toArray(new Field[0]);
    }

    /** A field by name, for {@link #StateCopy}'s shallow set. */
    static Field field(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ex) {
            throw new IllegalStateException(type.getSimpleName() + "." + name, ex);
        }
    }
}
