package nfm.lit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a race tick reads and writes, captured at one tick so the race
 * can be rewound to it: the cars' physics, drivers and models (down to the
 * dents in every polygon), the standings, the replay buffer, every object on
 * the track and what drawing advanced on it, the camera, the sky and all of
 * the random streams.
 * <p>
 * This is what makes the world copyable. Rewinding is what a speculative
 * tick (drawing a frame from where the next tick will probably be) and online
 * play with rollback need, and the verifier's rollback self-test proves that
 * nothing the race depends on is left out.
 * <p>
 * A speculative tick captures and restores once per tick, so {@link #capture}
 * leaves out what a race never changes: the track's collision boxes, the
 * corners of every polygon that is not on a car, and the stage's ground,
 * cloud and mountain geometry. The objects on the track, their polygons and
 * the replay buffer, which hold most of the data, save and restore
 * themselves into one reusable {@link StateBuffer}; the cars' physics and
 * drivers, the standings, the HUD and the statics are copied by reflection.
 * A snapshot is reused: capturing again keeps that buffer, so a capture
 * every tick does not allocate it again. {@link #captureComplete} copies
 * everything reachable instead, and the self-test compares the world with it
 * after every rewind, so a value the smaller snapshot misses is reported by
 * name.
 */
final class RaceSnapshot {

    /** Classes whose objects are copied by reflection wherever the race reaches them. */
    private static final Set<Class<?>> FOLLOWED = Set.of(Madness.class, Stat.class, Control.class,
            CheckPoints.class, Wheels.class, RaceRandom.class);

    /** For the complete copy: everything, down to every polygon and the track's collision boxes. */
    private static final Set<Class<?>> FOLLOWED_COMPLETE = union(FOLLOWED,
            Set.of(Record.class, ContO.class, Plane.class, Trackers.class));

    /** The replay buffer's copies of the cars are never changed once made: kept by reference. */
    private static final Set<Field> REPLAY_CARS = Set.of(StateCopy.field(Record.class, "car"),
            StateCopy.field(Record.class, "starcar"), StateCopy.field(Record.class, "ocar"));

    /**
     * The HUD's plain fields and captions: the race flow (countdown, finish,
     * wasted) lives there, and a stunt finished in an undone tick must not
     * leave its caption behind.
     */
    private static final Field[] UI_FIELDS = scalarFields(xtGraphics.class);

    /** The HUD's own copy of who is wasted, and which engine loop it believes is playing. */
    private static final Field[] UI_ARRAYS = {StateCopy.field(xtGraphics.class, "dested"),
            StateCopy.field(xtGraphics.class, "pengs")};

    /**
     * Medium's statics a race can change: all but the stage's mountains,
     * ground polygons, clouds and star positions, which are built when it
     * loads, and the presentation pass's own save buffers.
     */
    private static final Field[] MEDIUM_FIELDS = staticFieldsExcept(Medium.class, "mrd", "nmv", "mtx", "mty",
            "mtz", "mtc", "ogpx", "ogpz", "cgpx", "cgpz", "pvr", "pmx", "pcv", "clx", "clz", "cmx", "clax", "clay",
            "claz", "clc", "stx", "stz", "bst", "savedFx", "savedLilo", "savedLightn", "savedNoelec", "savedCpflik",
            "savedElecr", "savedFlex", "savedLastmaf", "savedTwn", "savedStc");

    /** The track objects, their polygons and the replay buffer; null in a complete snapshot. */
    private final StateBuffer buffer;
    private StateCopy copy;
    private Record record;
    private ContO[] scene;
    private ContO[] sceneRefs;
    private int cars;

    /** An empty snapshot to {@link #capture} into. */
    RaceSnapshot() {
        buffer = new StateBuffer();
    }

    private RaceSnapshot(StateCopy copy, RaceState.View v, ContO[] sceneRefs) {
        buffer = null;
        hold(copy, v, sceneRefs);
    }

    private void hold(StateCopy copy, RaceState.View v, ContO[] sceneRefs) {
        this.copy = copy;
        this.record = v.record();
        this.scene = v.cars();
        this.sceneRefs = sceneRefs;
        this.cars = v.players();
    }

    /**
     * Captures what a race can change, replacing what this snapshot held.
     *
     * @param sceneCount how many of {@code v.cars()} are on the track: the cars,
     *                   then the stage's objects
     */
    void capture(RaceState.View v, int sceneCount) {
        if (buffer == null) {
            throw new IllegalStateException("a complete snapshot cannot be captured again");
        }
        StateCopy c = new StateCopy(FOLLOWED, REPLAY_CARS);
        ContO[] refs = captureObjects(c, v, sceneCount);
        c.captureFields(null, MEDIUM_FIELDS);
        captureOtherStatics(c);
        hold(c, v, refs);
        buffer.startSaving();
        sync();
        buffer.endSaving();
    }

    /**
     * Captures everything the race reaches, including what it never changes.
     * Slow: for checking {@link #capture} with {@link #differences()}.
     */
    static RaceSnapshot captureComplete(RaceState.View v, int sceneCount) {
        StateCopy copy = new StateCopy(FOLLOWED_COMPLETE, REPLAY_CARS);
        ContO[] sceneRefs = captureObjects(copy, v, sceneCount);
        for (ContO conto : sceneRefs) {
            copy.capture(conto);
        }
        copy.capture(v.record());
        copy.capture(v.track());
        copy.captureStatics(Medium.class);
        captureOtherStatics(copy);
        return new RaceSnapshot(copy, v, sceneRefs);
    }

    /** Puts the race back exactly as it was captured. */
    void restore() {
        copy.restore();
        System.arraycopy(sceneRefs, 0, scene, 0, sceneRefs.length);
        if (buffer != null) {
            buffer.startRestoring();
            sync();
            buffer.endRestoring();
        }
    }

    /**
     * What differs now from when this snapshot was captured, counted by field
     * ({@code "Plane.av" -> objects}). Empty if the race is exactly as captured.
     */
    Map<String, Integer> differences() {
        Map<String, Integer> out = copy.differences();
        for (int i = 0; i < sceneRefs.length; i++) {
            if (scene[i] != sceneRefs[i]) {
                out.merge("scene object", 1, Integer::sum);
            }
        }
        return out;
    }

    private static ContO[] captureObjects(StateCopy copy, RaceState.View v, int sceneCount) {
        for (int i = 0; i < v.players(); i++) {
            copy.capture(v.racers()[i]);
            copy.capture(v.drivers()[i]);
        }
        copy.capture(v.checkpoints());
        copy.captureFields(v.ui(), UI_FIELDS);
        for (Field f : UI_ARRAYS) {
            copy.captureArray(StateCopy.get(f, v.ui()));
        }
        return Arrays.copyOf(v.cars(), sceneCount);
    }

    private static void captureOtherStatics(StateCopy copy) {
        copy.captureStatics(Plane.class);
        copy.captureStatics(RadicalMath.class);
        copy.captureStatics(CheckPoints.class);
    }

    /** Saves or restores the objects on the track, their polygons and the replay buffer. */
    private void sync() {
        StateBuffer b = buffer;
        for (int i = 0; i < sceneRefs.length; i++) {
            ContO conto = sceneRefs[i];
            conto.syncRaceState(b);
            boolean onCar = i < cars; // the cars come first in the scene
            for (Plane plane : conto.p) {
                if (plane != null) {
                    plane.syncRaceState(b, onCar);
                }
            }
        }
        record.syncRaceState(b);
    }

    private static Field[] scalarFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m) || f.isSynthetic()) {
                continue;
            }
            if (f.getType().isPrimitive() || f.getType().isEnum() || f.getType() == String.class) {
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out.toArray(new Field[0]);
    }

    private static Field[] staticFieldsExcept(Class<?> type, String... names) {
        Set<String> skip = new HashSet<>(Arrays.asList(names));
        List<Field> out = new ArrayList<>();
        for (Field f : StateCopy.staticFields(type)) {
            if (!skip.remove(f.getName())) {
                out.add(f);
            }
        }
        if (!skip.isEmpty()) {
            throw new IllegalStateException(type.getSimpleName() + " has no static field " + skip);
        }
        return out.toArray(new Field[0]);
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> out = new HashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }
}
