package nfm.lit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 */
final class RaceSnapshot {

    /** Classes whose objects are copied whole wherever the race reaches them. */
    private static final Set<Class<?>> FOLLOWED = Set.of(Madness.class, Stat.class, Control.class,
            CheckPoints.class, Record.class, ContO.class, Plane.class, Wheels.class, RaceRandom.class,
            Trackers.class);

    /** The replay buffer's copies of the cars are never changed once made: kept by reference. */
    private static final Set<Field> SHALLOW = Set.of(StateCopy.field(Record.class, "car"),
            StateCopy.field(Record.class, "starcar"), StateCopy.field(Record.class, "ocar"));

    /** The HUD's plain fields: the race flow (countdown, finish, wasted) lives there. */
    private static final Field[] UI_FIELDS = scalarFields(xtGraphics.class);

    private final StateCopy copy;
    private final ContO[] scene;
    private final ContO[] sceneRefs;

    private RaceSnapshot(StateCopy copy, ContO[] scene, ContO[] sceneRefs) {
        this.copy = copy;
        this.scene = scene;
        this.sceneRefs = sceneRefs;
    }

    /**
     * Captures the race.
     *
     * @param sceneCount how many of {@code v.cars()} are on the track: the cars,
     *                   then the stage's objects
     */
    static RaceSnapshot capture(RaceState.View v, int sceneCount) {
        StateCopy copy = new StateCopy(FOLLOWED, SHALLOW);
        for (int i = 0; i < v.players(); i++) {
            copy.capture(v.racers()[i]);
            copy.capture(v.drivers()[i]);
        }
        ContO[] scene = v.cars();
        for (int i = 0; i < sceneCount; i++) {
            copy.capture(scene[i]);
        }
        copy.capture(v.checkpoints());
        copy.capture(v.record());
        copy.capture(v.track());
        copy.captureFields(v.ui(), UI_FIELDS);
        copy.captureStatics(Medium.class);
        copy.captureStatics(Plane.class);
        copy.captureStatics(RadicalMath.class);
        copy.captureStatics(CheckPoints.class);
        return new RaceSnapshot(copy, scene, Arrays.copyOf(scene, sceneCount));
    }

    /** Puts the race back exactly as it was captured. */
    void restore() {
        copy.restore();
        System.arraycopy(sceneRefs, 0, scene, 0, sceneRefs.length);
    }

    private static Field[] scalarFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m) || f.isSynthetic()) {
                continue;
            }
            if (f.getType().isPrimitive() || f.getType().isEnum()) {
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out.toArray(new Field[0]);
    }
}
