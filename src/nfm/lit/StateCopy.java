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
 */
final class StateCopy {

    private static final Map<Class<?>, Field[]> INSTANCE_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> STATIC_FIELDS = new ConcurrentHashMap<>();

    private final Set<Class<?>> followed;
    /** Fields whose objects are kept by reference only, even if their class is followed. */
    private final Set<Field> shallow;
    private final Map<Object, Object[]> objects = new IdentityHashMap<>();
    private final List<Class<?>> classes = new ArrayList<>();
    private final List<Object[]> statics = new ArrayList<>();
    /** Some fields of objects that are not captured whole. */
    private final List<Object> partialTargets = new ArrayList<>();
    private final List<Field[]> partialFields = new ArrayList<>();
    private final List<Object[]> partialValues = new ArrayList<>();
    private final List<ArrayCopy> arrays = new ArrayList<>();

    StateCopy(Set<Class<?>> followed, Set<Field> shallow) {
        this.followed = followed;
        this.shallow = shallow;
    }

    /** Captures an object and everything it reaches in the followed classes. */
    void capture(Object root) {
        if (root == null || objects.containsKey(root)) {
            return;
        }
        if (root.getClass().isArray()) {
            throw new IllegalArgumentException("capture the object that holds the array");
        }
        Field[] fields = instanceFields(root.getClass());
        Object[] values = new Object[fields.length];
        objects.put(root, values);
        for (int i = 0; i < fields.length; i++) {
            values[i] = copyOf(get(fields[i], root), !shallow.contains(fields[i]));
        }
    }

    /** Captures the static fields of a class, and what they reach. */
    void captureStatics(Class<?> type) {
        Field[] fields = staticFields(type);
        Object[] values = new Object[fields.length];
        classes.add(type);
        statics.add(values);
        for (int i = 0; i < fields.length; i++) {
            values[i] = copyOf(get(fields[i], null), !shallow.contains(fields[i]));
        }
    }

    /** Captures only the given fields of an object, and what they reach. */
    void captureFields(Object target, Field[] fields) {
        Object[] values = new Object[fields.length];
        partialTargets.add(target);
        partialFields.add(fields);
        partialValues.add(values);
        for (int i = 0; i < fields.length; i++) {
            values[i] = copyOf(get(fields[i], target), true);
        }
    }

    /** Captures an array's contents, and what they reach. */
    void captureArray(Object array) {
        arrays.add(new ArrayCopy(array, copyArray(array, true)));
    }

    /** Puts every captured value back. */
    void restore() {
        for (ArrayCopy a : arrays) {
            restoreArray(a);
        }
        for (int p = 0; p < partialTargets.size(); p++) {
            Field[] fields = partialFields.get(p);
            Object[] values = partialValues.get(p);
            for (int i = 0; i < fields.length; i++) {
                put(fields[i], partialTargets.get(p), values[i]);
            }
        }
        for (Map.Entry<Object, Object[]> e : objects.entrySet()) {
            Object target = e.getKey();
            Field[] fields = instanceFields(target.getClass());
            Object[] values = e.getValue();
            for (int i = 0; i < fields.length; i++) {
                put(fields[i], target, values[i]);
            }
        }
        for (int c = 0; c < classes.size(); c++) {
            Field[] fields = staticFields(classes.get(c));
            Object[] values = statics.get(c);
            for (int i = 0; i < fields.length; i++) {
                put(fields[i], null, values[i]);
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
        for (int p = 0; p < partialTargets.size(); p++) {
            compare(partialTargets.get(p), partialFields.get(p), partialValues.get(p), out);
        }
        for (Map.Entry<Object, Object[]> e : objects.entrySet()) {
            compare(e.getKey(), instanceFields(e.getKey().getClass()), e.getValue(), out);
        }
        for (int c = 0; c < classes.size(); c++) {
            compare(null, staticFields(classes.get(c)), statics.get(c), out);
        }
        return out;
    }

    private static void compare(Object target, Field[] fields, Object[] values, Map<String, Integer> out) {
        for (int i = 0; i < fields.length; i++) {
            if (!same(get(fields[i], target), values[i])) {
                out.merge(fields[i].getDeclaringClass().getSimpleName() + "." + fields[i].getName(), 1, Integer::sum);
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
        Object[] copy = new Object[n];
        for (int i = 0; i < n; i++) {
            Object element = Array.get(array, i);
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

    private static void put(Field field, Object target, Object saved) {
        Object value;
        if (saved instanceof ArrayCopy copy) {
            restoreArray(copy);
            value = copy.array();
        } else if (saved instanceof Ref ref) {
            value = ref.value();
        } else {
            value = saved;
        }
        if (Modifier.isFinal(field.getModifiers())) {
            return;
        }
        try {
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(field.toString(), ex);
        }
    }

    private static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(field.toString(), ex);
        }
    }

    private static boolean isBoxed(Class<?> type) {
        return type == Integer.class || type == Float.class || type == Boolean.class || type == Long.class
                || type == Double.class || type == Short.class || type == Byte.class || type == Character.class;
    }

    /* ---------------------------------------------------------------- fields */

    private static Field[] instanceFields(Class<?> type) {
        return INSTANCE_FIELDS.computeIfAbsent(type, t -> fields(t, false));
    }

    /** The static fields a capture of {@code type} covers. */
    static Field[] staticFields(Class<?> type) {
        return STATIC_FIELDS.computeIfAbsent(type, t -> fields(t, true));
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
