package nfm.lit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Reads, hashes and restores everything a race depends on, for recorded races
 * and the headless verifier.
 * <p>
 * The decompiled classes keep their state in dozens of loose fields, many with
 * decompiler names, and fields come and go as the code is cleaned up. A
 * hand-written list would silently miss one, so this class takes every
 * primitive field (and primitive array) of the car physics ({@link Madness} and
 * its {@link Stat}), the drivers ({@link Control}) and the standings
 * ({@link CheckPoints}) by reflection. To those it adds the few fields of other
 * classes that the race reads back: the car models' pose and repair state, the
 * camera (what is on screen decides some outcomes), the race-flow fields of
 * {@link xtGraphics} and {@link Record}, and the random streams.
 * <p>
 * The values are walked in a fixed order, split into groups: one per car, then
 * the race, the camera and the random streams. A hash per group per tick tells
 * the verifier which car went wrong first; a full dump tells it which field.
 */
final class RaceState {

    /** Control's menu and HUD switches, which are not race state. */
    private static final Set<String> CONTROL_SKIP = Set.of("enter", "arrace");

    /** The parts of a car model the race reads or moves. */
    private static final List<String> CAR_FIELDS = List.of("x", "y", "z", "xz", "xy", "zy", "wxz", "wzy", "dist",
            "fix", "fcnt");

    /**
     * The camera, its follow and orbit state, and the projection and fog values
     * that decide whether a car counts as on screen.
     */
    private static final List<String> CAMERA_FIELDS = List.of("x", "y", "z", "xz", "zy", "bcxz", "vxz", "adv",
            "vert", "trns", "td", "flex", "focus_point", "trk", "crs", "cx", "cy", "cz", "w", "h", "fade");

    /** The race flow kept by the HUD: countdown, finish and the post-race hold. */
    private static final List<String> FLOW_FIELDS = List.of("starcnt", "holdit", "holdcnt", "winner", "wasted",
            "dested");

    /**
     * Which skid and crash clip plays next, and the gaps between them. The
     * rotation is never reset between races, so a race's sounds depend on the
     * races before it: recorded at the countdown like any other carry-over.
     * Not grrd or stopcnt: whether the wind sound starts draws from the
     * cosmetic stream, which drawing shares, so it may differ between runs.
     */
    private static final List<String> SOUND_FIELDS = List.of("crshturn", "crashup", "skflg", "dskflg", "skidup",
            "bfcrash", "bfskid", "pwait", "cntwis", "aird");

    /**
     * The HUD's blink phase: stunt captions and other HUD text alternate colour
     * on it every tick. The stage's loading screen flips it on its own thread
     * every 20 ms while its cartoon animates, so a race starts on a phase that
     * depends on how long the stage took to load: recorded at the countdown.
     */
    private static final List<String> HUD_FIELDS = List.of("aflk");

    private static final Field[] MADNESS = fieldsOf(Madness.class, Set.of(), true);
    private static final Field[] STAT = fieldsOf(Stat.class, Set.of(), true);
    private static final Field[] CONTROL = fieldsOf(Control.class, CONTROL_SKIP, true);
    private static final Field[] CHECKPOINTS = fieldsOf(CheckPoints.class, Set.of(), true);
    /** Only the scalars: the replay buffers are megabytes and the race never reads them. */
    private static final Field[] RECORD = fieldsOf(Record.class, Set.of(), false);
    private static final Field[] CAR = named(ContO.class, CAR_FIELDS);
    private static final Field[] CAMERA = named(Medium.class, CAMERA_FIELDS);
    private static final Field[] FLOW = named(xtGraphics.class, FLOW_FIELDS);
    private static final Field[] SOUND = named(xtGraphics.class, SOUND_FIELDS);
    private static final Field[] HUD = named(xtGraphics.class, HUD_FIELDS);

    /** The live objects of one race. */
    record View(World world, Madness[] racers, Control[] drivers, ContO[] cars, CheckPoints checkpoints,
            Record record, xtGraphics ui, Trackers track, int players) {
    }

    /** One named slice of the state: some fields of one object. */
    private record Group(String name, Object target, Field[] fields) {
    }

    /** Receives every value in a fixed order. */
    interface Sink {
        /** A new group starts; every value until the next call belongs to it. */
        default void group(String name) {
        }

        void value(String owner, Field field, int index, int bits);
    }

    private RaceState() {
    }

    /** Groups in walking order. Each car is one group: physics, stats, driver and model. */
    private static List<List<Group>> groups(View v) {
        List<List<Group>> out = new ArrayList<>(v.players() + 2);
        for (int i = 0; i < v.players(); i++) {
            String car = "car" + i;
            out.add(List.of(new Group(car + ".physics", v.racers()[i], MADNESS),
                    new Group(car + ".stats", v.racers()[i].stat, STAT),
                    new Group(car + ".driver", v.drivers()[i], CONTROL),
                    new Group(car + ".model", v.cars()[i], CAR)));
        }
        out.add(List.of(new Group("race", v.checkpoints(), CHECKPOINTS),
                new Group("race.record", v.record(), RECORD),
                new Group("race.flow", v.ui(), FLOW),
                new Group("race.sound", v.ui(), SOUND),
                new Group("race.hud", v.ui(), HUD)));
        out.add(List.of(new Group("camera", null, CAMERA)));
        return out;
    }

    /** Number of per-tick hashes: one per car, the race, the camera and the random streams. */
    static int groupCount(int players) {
        return players + 3;
    }

    static String groupName(int group, int players) {
        if (group < players) {
            return "car " + group;
        }
        return switch (group - players) {
            case 0 -> "race";
            case 1 -> "camera";
            default -> "random";
        };
    }

    /** Walks every value: the restorable state, then the random streams' fingerprints. */
    static void visit(View v, Sink sink) {
        visitRestorable(v, sink);
        sink.group("random");
        long r = v.world().random.fingerprint();
        long cam = Medium.cameraRandom.fingerprint();
        sink.value("random.race", null, 0, (int) r);
        sink.value("random.race", null, 1, (int) (r >>> 32));
        sink.value("random.camera", null, 0, (int) cam);
        sink.value("random.camera", null, 1, (int) (cam >>> 32));
    }

    /** Walks the values {@link #restore} can put back, in the order it reads them. */
    static void visitRestorable(View v, Sink sink) {
        for (List<Group> slice : groups(v)) {
            sink.group(slice.get(0).name());
            for (Group g : slice) {
                visit(g, sink);
            }
        }
    }

    /** One hash per group, see {@link #groupCount(int)}. */
    static int[] hashes(View v) {
        GroupHasher hasher = new GroupHasher(groupCount(v.players()));
        visit(v, hasher);
        return hasher.result();
    }

    /** Every value, for the periodic dumps. */
    static int[] dump(View v) {
        return collect(v, RaceState::visit, false).values();
    }

    /** Names for the values {@link #dump} returns, built only when reporting a mismatch. */
    static List<String> names(View v) {
        return collect(v, RaceState::visit, true).names();
    }

    private static Collector collect(View v, BiConsumer<View, Sink> walk, boolean withNames) {
        Collector c = new Collector(withNames);
        walk.accept(v, c);
        return c;
    }

    /** For each value {@link #dump} returns, the group it belongs to (see {@link #groupName}). */
    static int[] groupOf(View v) {
        return collect(v, RaceState::visit, false).groups();
    }

    /** The state a race starts from. */
    static int[] startState(View v) {
        return collect(v, RaceState::visitRestorable, false).values();
    }

    static List<String> startStateNames(View v) {
        return collect(v, RaceState::visitRestorable, true).names();
    }

    /**
     * A fingerprint of the field layout: when the classes change, the full
     * hashes and dumps of older recordings no longer line up, and the verifier
     * falls back to the canonical state.
     */
    static int layout(View v) {
        int h = 17;
        for (String name : names(v)) {
            h = h * 31 + name.hashCode();
        }
        return h;
    }

    /**
     * The canonical race: what a race visibly is, read by name rather than by
     * reflection, so it stays comparable when the code is reorganised. When a
     * field below moves or is renamed, this method is updated to read the same
     * quantity from its new place, and every recorded race keeps verifying.
     */
    static void visitCanonical(View v, Sink sink) {
        for (int i = 0; i < v.players(); i++) {
            String car = "car" + i + ".";
            ContO o = v.cars()[i];
            Madness m = v.racers()[i];
            Control c = v.drivers()[i];
            sink.group(car);
            put(sink, car + "x", o.x);
            put(sink, car + "y", o.y);
            put(sink, car + "z", o.z);
            put(sink, car + "xz", o.xz);
            put(sink, car + "xy", o.xy);
            put(sink, car + "zy", o.zy);
            put(sink, car + "wheelSteer", o.wxz);
            put(sink, car + "wheelRoll", o.wzy);
            put(sink, car + "onScreen", o.dist);
            put(sink, car + "repairing", o.fix ? 1 : 0);
            put(sink, car + "repairStep", o.fcnt);
            put(sink, car + "model", m.cn);
            put(sink, car + "speed", Float.floatToRawIntBits(m.speed));
            put(sink, car + "power", Float.floatToRawIntBits(m.power));
            put(sink, car + "stuntPower", Float.floatToRawIntBits(m.powerup));
            for (int w = 0; w < 4; w++) {
                put(sink, car + "wheel" + w + ".vx", Float.floatToRawIntBits(m.scx[w]));
                put(sink, car + "wheel" + w + ".vy", Float.floatToRawIntBits(m.scy[w]));
                put(sink, car + "wheel" + w + ".vz", Float.floatToRawIntBits(m.scz[w]));
            }
            put(sink, car + "grounded", m.mtouch ? 1 : 0);
            put(sink, car + "wheelsGrounded", m.wtouch ? 1 : 0);
            put(sink, car + "capsized", m.capsized ? 1 : 0);
            put(sink, car + "damage", m.hitmag);
            put(sink, car + "squash", m.squash);
            put(sink, car + "wrecking", m.cntdest);
            put(sink, car + "wrecked", m.dest ? 1 : 0);
            put(sink, car + "checkpoints", m.clear);
            put(sink, car + "laps", m.nlaps);
            put(sink, car + "nextPoint", m.point);
            put(sink, car + "heading", m.mxz);
            put(sink, car + "cameraHeading", m.cxz);
            put(sink, car + "loop", m.loop);
            put(sink, car + "input", RaceLog.keyBits(c) & ~RaceLog.KEY_ENTER);
        }
        CheckPoints cp = v.checkpoints();
        sink.group("race");
        for (int i = 0; i < v.players(); i++) {
            put(sink, "race.position" + i, cp.pos[i]);
            put(sink, "race.cleared" + i, cp.clear[i]);
            put(sink, "race.wasted" + i, cp.dested[i]);
        }
        put(sink, "race.wastedCount", cp.wasted);
        put(sink, "race.halted", cp.haltall ? 1 : 0);
        put(sink, "race.countdown", v.ui().starcnt);
        put(sink, "race.over", raceOver(v.ui()) ? 1 : 0);
        put(sink, "race.won", v.ui().winner ? 1 : 0);
        sink.group("camera");
        put(sink, "camera.x", Medium.x);
        put(sink, "camera.y", Medium.y);
        put(sink, "camera.z", Medium.z);
        put(sink, "camera.xz", Medium.xz);
        put(sink, "camera.zy", Medium.zy);
        sink.group("random");
        long r = v.world().random.fingerprint();
        put(sink, "random.race.low", (int) r);
        put(sink, "random.race.high", (int) (r >>> 32));
    }

    /** xtGraphics keeps "the race is over and the result is on screen" private. */
    private static boolean raceOver(xtGraphics ui) {
        try {
            return FLOW[FLOW_FIELDS.indexOf("holdit")].getBoolean(ui);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void put(Sink sink, String name, int value) {
        sink.value(name, null, -1, value);
    }

    static int canonicalHash(View v) {
        GroupHasher hasher = new GroupHasher(groupCount(v.players()));
        visitCanonical(v, hasher);
        int h = 0;
        for (int g : hasher.result()) {
            h = h * 31 + g;
        }
        return h;
    }

    static int[] canonical(View v) {
        return collect(v, RaceState::visitCanonical, false).values();
    }

    static List<String> canonicalNames(View v) {
        return collect(v, RaceState::visitCanonical, true).names();
    }

    /**
     * Puts a recorded starting state back into the live objects. The verifier
     * does this when the countdown begins, so a race that was recorded after
     * other races starts from exactly the state it had then, including anything
     * the earlier races left behind.
     */
    static void restore(View v, int[] values) {
        int at = 0;
        for (List<Group> slice : groups(v)) {
            for (Group g : slice) {
                at = restore(g, values, at);
            }
        }
        if (at != values.length) {
            throw new IllegalStateException("start state has " + values.length + " values, expected " + at);
        }
    }

    /**
     * Restores a start state recorded with a different field layout: every
     * value whose name still exists is put back, the rest keep their current
     * value. Returns the names that no longer exist.
     */
    static List<String> restoreByName(View v, List<String> recordedNames, int[] recordedValues) {
        List<String> liveNames = startStateNames(v);
        int[] live = startState(v);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < liveNames.size(); i++) {
            index.put(liveNames.get(i), i);
        }
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < recordedNames.size(); i++) {
            Integer at = index.get(recordedNames.get(i));
            if (at == null) {
                missing.add(recordedNames.get(i));
            } else {
                live[at] = recordedValues[i];
            }
        }
        restore(v, live);
        return missing;
    }

    /** Values per track piece in {@link #trackShape}. */
    private static final int SHAPE_FIELDS = 9;
    /** Values per track piece in {@link #trackLeftovers}. */
    private static final int LEFTOVER_FIELDS = 6;

    /**
     * The track's collision boxes as stage loading builds them: the number of
     * pieces, then each piece's position, angles, size and damage factor. The
     * race never changes them, so they are recorded once, at the start; if they
     * differ later, the stage now loads differently.
     */
    static int[] trackShape(Trackers t) {
        int[] out = new int[1 + t.nt * SHAPE_FIELDS];
        out[0] = t.nt;
        int at = 1;
        for (int i = 0; i < t.nt; i++) {
            out[at++] = t.x[i];
            out[at++] = t.y[i];
            out[at++] = t.z[i];
            out[at++] = t.xy[i];
            out[at++] = t.zy[i];
            out[at++] = t.radx[i];
            out[at++] = t.rady[i];
            out[at++] = t.radz[i];
            out[at++] = t.dam[i];
        }
        return out;
    }

    /**
     * What earlier stages left in the track arrays that this race can read.
     * The original game behaves this way and it is kept, so these values are
     * recorded and the verifier puts them back:
     * <ul>
     * <li>A stage's boundary walls never set their surface (grip, skids and the
     * dust it throws up), wall flag or dust colour. A boundary wall keeps
     * whatever the piece in the same slot had in the stage loaded before, which
     * is zero in a freshly started game.</li>
     * <li>An AI driver remembers the last wall it hit, and that memory survives
     * into the next race. Until it hits a wall again it reads that slot's
     * damage and surface, which may lie past the end of the new stage's own
     * pieces and still hold an earlier stage's values.</li>
     * </ul>
     * Layout: the number of slots covered, then per slot the surface, wall flag,
     * dust colour and damage factor.
     */
    static int[] trackLeftovers(View v) {
        Trackers t = v.track();
        int extent = trackExtent(v);
        int[] out = new int[1 + extent * LEFTOVER_FIELDS];
        out[0] = extent;
        int at = 1;
        for (int i = 0; i < extent; i++) {
            out[at++] = t.skd[i];
            out[at++] = t.notwall[i] ? 1 : 0;
            out[at++] = t.c[i][0];
            out[at++] = t.c[i][1];
            out[at++] = t.c[i][2];
            out[at++] = t.dam[i];
        }
        return out;
    }

    /** The stage's own pieces, and every slot past them that a driver still remembers as its last wall. */
    private static int trackExtent(View v) {
        int extent = v.track().nt;
        for (int i = 0; i < v.players(); i++) {
            extent = Math.max(extent, v.drivers()[i].wall + 1);
        }
        return Math.min(extent, v.track().dam.length);
    }

    /**
     * Puts recorded {@link #trackLeftovers} back and returns how many values
     * differed. The stage's own damage factors are checked by
     * {@link #trackShape} first, so restoring them changes nothing.
     */
    static int restoreTrackLeftovers(View v, int[] values) {
        Trackers t = v.track();
        int extent = values.length == 0 ? -1 : values[0];
        if (extent < 0 || extent > t.dam.length || values.length != 1 + extent * LEFTOVER_FIELDS) {
            throw new IllegalStateException("the recorded track leftovers are malformed");
        }
        int differed = 0;
        int at = 1;
        for (int i = 0; i < extent; i++) {
            int[] live = {t.skd[i], t.notwall[i] ? 1 : 0, t.c[i][0], t.c[i][1], t.c[i][2], t.dam[i]};
            for (int k = 0; k < LEFTOVER_FIELDS; k++) {
                if (live[k] != values[at + k]) {
                    differed++;
                }
            }
            t.skd[i] = values[at++];
            t.notwall[i] = values[at++] != 0;
            t.c[i][0] = values[at++];
            t.c[i][1] = values[at++];
            t.c[i][2] = values[at++];
            t.dam[i] = values[at++];
        }
        return differed;
    }

    private static Field[] fieldsOf(Class<?> type, Set<String> skip, boolean arrays) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            Class<?> t = f.getType();
            if (Modifier.isStatic(f.getModifiers()) || skip.contains(f.getName()) || !isPrimitiveData(t)
                    || (!arrays && t.isArray())) {
                continue;
            }
            f.setAccessible(true);
            out.add(f);
        }
        out.sort(Comparator.comparing(Field::getName));
        return out.toArray(new Field[0]);
    }

    private static Field[] named(Class<?> type, List<String> names) {
        Field[] out = new Field[names.size()];
        for (int i = 0; i < out.length; i++) {
            try {
                out[i] = type.getDeclaredField(names.get(i));
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException(type.getSimpleName() + "." + names.get(i) + " is gone", ex);
            }
            out[i].setAccessible(true);
        }
        return out;
    }

    private static boolean isPrimitiveData(Class<?> t) {
        while (t.isArray()) {
            t = t.getComponentType();
        }
        return t.isPrimitive();
    }

    private static void visit(Group g, Sink sink) {
        try {
            for (Field f : g.fields()) {
                if (f.getType().isArray()) {
                    visitArray(g.name(), f, f.get(g.target()), sink);
                } else {
                    sink.value(g.name(), f, -1, scalar(f, g.target()));
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int scalar(Field f, Object target) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (t == int.class) {
            return f.getInt(target);
        }
        if (t == float.class) {
            return Float.floatToRawIntBits(f.getFloat(target));
        }
        if (t == boolean.class) {
            return f.getBoolean(target) ? 1 : 0;
        }
        if (t == long.class) {
            return fold(f.getLong(target));
        }
        if (t == double.class) {
            return fold(Double.doubleToRawLongBits(f.getDouble(target)));
        }
        if (t == byte.class) {
            return f.getByte(target);
        }
        if (t == short.class) {
            return f.getShort(target);
        }
        return f.getChar(target);
    }

    /** Both halves of a 64-bit value in one int (raw bits for doubles, so NaNs keep theirs). */
    private static int fold(long v) {
        return Long.hashCode(v);
    }

    /** Arrays are walked element by element; nested arrays row by row. */
    private static void visitArray(String owner, Field f, Object array, Sink sink) {
        int[] index = {0};
        walkArray(array, bits -> sink.value(owner, f, index[0]++, bits));
    }

    private interface IntSink {
        void accept(int bits);
    }

    private static void walkArray(Object array, IntSink out) {
        if (array == null) {
            out.accept(0);
        } else if (array instanceof int[] a) {
            for (int v : a) {
                out.accept(v);
            }
        } else if (array instanceof float[] a) {
            for (float v : a) {
                out.accept(Float.floatToRawIntBits(v));
            }
        } else if (array instanceof boolean[] a) {
            for (boolean v : a) {
                out.accept(v ? 1 : 0);
            }
        } else if (array instanceof long[] a) {
            for (long v : a) {
                out.accept(fold(v));
            }
        } else if (array instanceof double[] a) {
            for (double v : a) {
                out.accept(fold(Double.doubleToRawLongBits(v)));
            }
        } else if (array instanceof byte[] a) {
            for (byte v : a) {
                out.accept(v);
            }
        } else if (array instanceof short[] a) {
            for (short v : a) {
                out.accept(v);
            }
        } else if (array instanceof char[] a) {
            for (char v : a) {
                out.accept(v);
            }
        } else if (array instanceof Object[] nested) {
            for (Object inner : nested) {
                walkArray(inner, out);
            }
        }
    }

    private static int restore(Group g, int[] values, int at) {
        try {
            for (Field f : g.fields()) {
                if (f.getType().isArray()) {
                    at = restoreArray(f.get(g.target()), values, at);
                } else {
                    restoreScalar(f, g.target(), values[at++]);
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        return at;
    }

    private static void restoreScalar(Field f, Object target, int bits) throws IllegalAccessException {
        if (Modifier.isFinal(f.getModifiers())) {
            return;
        }
        Class<?> t = f.getType();
        if (t == int.class) {
            f.setInt(target, bits);
        } else if (t == float.class) {
            f.setFloat(target, Float.intBitsToFloat(bits));
        } else if (t == boolean.class) {
            f.setBoolean(target, bits != 0);
        } else if (t == byte.class) {
            f.setByte(target, (byte) bits);
        } else if (t == short.class) {
            f.setShort(target, (short) bits);
        } else if (t == char.class) {
            f.setChar(target, (char) bits);
        }
        // long and double values are stored folded and stay as they are; the race has none
    }

    private static int restoreArray(Object array, int[] values, int at) {
        if (array == null) {
            return at + 1;
        }
        if (array instanceof int[] a) {
            System.arraycopy(values, at, a, 0, a.length);
            return at + a.length;
        }
        if (array instanceof float[] a) {
            for (int i = 0; i < a.length; i++) {
                a[i] = Float.intBitsToFloat(values[at++]);
            }
            return at;
        }
        if (array instanceof boolean[] a) {
            for (int i = 0; i < a.length; i++) {
                a[i] = values[at++] != 0;
            }
            return at;
        }
        if (array instanceof Object[] nested) {
            for (Object inner : nested) {
                at = restoreArray(inner, values, at);
            }
            return at;
        }
        int[] count = {0};
        walkArray(array, bits -> count[0]++);
        return at + count[0];
    }

    /** One running hash per group. */
    private static final class GroupHasher implements Sink {
        private final long[] h;
        private int current = -1;

        GroupHasher(int groups) {
            h = new long[groups];
        }

        @Override
        public void group(String name) {
            current++;
            h[current] = 0x6A09E667F3BCC908L + current;
        }

        @Override
        public void value(String owner, Field field, int index, int bits) {
            long x = h[current] ^ (bits & 0xFFFFFFFFL);
            x *= 0x100000001B3L;
            h[current] = x ^ (x >>> 29);
        }

        int[] result() {
            int[] out = new int[h.length];
            for (int i = 0; i < h.length; i++) {
                out[i] = fold(RaceRandom.mix64(h[i]));
            }
            return out;
        }
    }

    /** Collects values and the group of each, and their names when asked. */
    private static final class Collector implements Sink {
        private final List<String> names;
        private int[] values = new int[8192];
        private int[] groups = new int[8192];
        private int size;
        private int group = -1;

        Collector(boolean withNames) {
            names = withNames ? new ArrayList<>() : null;
        }

        @Override
        public void group(String name) {
            group++;
        }

        @Override
        public void value(String owner, Field field, int index, int bits) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
                groups = Arrays.copyOf(groups, size * 2);
            }
            groups[size] = group;
            values[size++] = bits;
            if (names != null) {
                String suffix = index >= 0 ? "[" + index + "]" : "";
                String name = field == null ? owner + suffix : owner + "." + field.getName() + suffix;
                names.add(name);
            }
        }

        int[] values() {
            return Arrays.copyOf(values, size);
        }

        int[] groups() {
            return Arrays.copyOf(groups, size);
        }

        List<String> names() {
            return names;
        }
    }
}
