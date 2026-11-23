package nfm.lit.features;

import fallk.logmaster.HLogger;

/**
 * The extras the game knows, which are on in the race being played, and the
 * calls the game makes into them. A new feature is one line in {@link #ALL}.
 * <p>
 * A look or sound feature follows its switch at once, even during a race;
 * a race feature keeps what its switch said when the race started. With
 * every feature off each call here is one check of a field. A feature that
 * throws is switched off for the rest of the race and logged, so a bug in an
 * extra never stops a race.
 * <p>
 * Called on the game thread only.
 */
public final class Features {

    /** Every feature, one line each. */
    private static final Feature[] ALL = {
        new nfm.lit.features.skidmarks.SkidmarksFeature(),
        new nfm.lit.features.skidmarks.SkidZonesFeature(),
        new nfm.lit.features.wingtrail.WingTrailFeature(),
        new nfm.lit.features.realism.RealismFeature(),
        nfm.lit.features.helmetcam.HelmetCam.FEATURE,
    };

    private static final boolean[] active = new boolean[ALL.length];
    /** Thrown during this race: off until the next. */
    private static final boolean[] broken = new boolean[ALL.length];
    private static int activeCount;
    private static boolean racing;
    private static int seenVersion = -1;
    private static RaceView lastView;
    /** The feature that recolours and reshapes the cars' polygons, and its place in {@link #ALL}; null and -1 if none is on. */
    private static CarLook look;
    private static int lookOwner = -1;

    private Features() {
    }

    /** True if feature {@code id} exists (its switch is shown). */
    public static boolean registered(String id) {
        for (Feature f : ALL) {
            if (f.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** True if some feature is on in the race being played: the game then fills the draw queue. */
    public static boolean anyActive() {
        return activeCount > 0;
    }

    private static int switchedVersion = -1;
    private static boolean switchedOn;

    /** True if some feature's switch is on (it runs in the next race, or now if it only changes the look). */
    public static boolean anySwitchedOn() {
        int v = FeatureSettings.version();
        if (v != switchedVersion) {
            switchedVersion = v;
            switchedOn = false;
            for (Feature f : ALL) {
                switchedOn |= FeatureSettings.on(f.id());
            }
        }
        return switchedOn;
    }

    /** True if feature {@code id} is on in the race being played. */
    public static boolean active(String id) {
        for (int f = 0; f < ALL.length; f++) {
            if (active[f] && ALL[f].id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** A race starts: the features whose switches are on start with it. */
    public static void raceStarted(RaceView race) {
        if (racing) {
            raceEnded(race);
        }
        racing = true;
        lastView = race;
        java.util.Arrays.fill(broken, false);
        seenVersion = FeatureSettings.version();
        activeCount = 0;
        for (int f = 0; f < ALL.length; f++) {
            active[f] = FeatureSettings.on(ALL[f].id());
            if (active[f]) {
                activeCount++;
                start(f, race);
            }
        }
        relook();
    }

    /** A real race tick has ended. */
    public static void afterTick(RaceView race) {
        if (!racing) {
            return;
        }
        lastView = race;
        if (seenVersion != FeatureSettings.version()) {
            follow(race);
        }
        if (activeCount == 0) {
            return;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                try {
                    ALL[f].afterTick(race);
                } catch (RuntimeException e) {
                    fail(f, e);
                }
            }
        }
    }

    /** A look or sound switch turned during the race: start or stop that feature now. */
    private static void follow(RaceView race) {
        seenVersion = FeatureSettings.version();
        for (int f = 0; f < ALL.length; f++) {
            Feature feature = ALL[f];
            if (feature.changesRaces() || broken[f]) {
                continue;
            }
            boolean on = FeatureSettings.on(feature.id());
            if (on && !active[f]) {
                active[f] = true;
                activeCount++;
                start(f, race);
            } else if (!on && active[f]) {
                stop(f, race);
            }
        }
        relook();
    }

    /** The race has ended or been left. */
    public static void raceEnded(RaceView race) {
        if (!racing) {
            return;
        }
        racing = false;
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                stop(f, race != null ? race : lastView);
            }
        }
        activeCount = 0;
        relook();
    }

    /** The race in play ended (the game left it), if one was. */
    public static void leftRace() {
        raceEnded(lastView);
    }

    /** Asks every feature that is on what it draws in this frame. */
    public static void queue(DrawQueue queue) {
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                queue.owner(f);
                try {
                    ALL[f].queue(queue);
                } catch (RuntimeException e) {
                    fail(f, e);
                }
            }
        }
    }

    /** Draws item {@code item} of feature {@code feature}. */
    public static void draw(int feature, int item, Canvas canvas) {
        if (!active[feature]) {
            return;
        }
        try {
            ALL[feature].draw(canvas, item);
        } catch (RuntimeException e) {
            fail(feature, e);
        }
    }

    /** The feature that recolours the cars' polygons in the race being played, or null: the game asks it for each. */
    public static CarLook carLook() {
        return look;
    }

    /** {@link CarLook#color} of the feature that recolours the cars; a feature that throws is switched off. */
    public static int carColor(int car, int plane, int rgb) {
        CarLook l = look;
        if (l == null) {
            return rgb;
        }
        try {
            return l.color(car, plane, rgb);
        } catch (RuntimeException e) {
            fail(lookOwner, e);
            return rgb;
        }
    }

    /** {@link CarLook#lamp} after {@link #carColor}; -1 when no feature recolours the cars. */
    public static int carLamp() {
        CarLook l = look;
        return l == null ? -1 : l.lamp();
    }

    /** {@link CarLook#bend}; false when no feature reshapes the cars (or it threw, and is off). */
    public static boolean carBend(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY,
            int[] outZ) {
        CarLook l = look;
        if (l == null) {
            return false;
        }
        try {
            return l.bend(car, plane, x, y, z, n, outX, outY, outZ);
        } catch (RuntimeException e) {
            fail(lookOwner, e);
            return false;
        }
    }

    /** Finds the feature on now that recolours the cars, if any. */
    private static void relook() {
        look = null;
        lookOwner = -1;
        for (int f = 0; f < ALL.length; f++) {
            if (active[f] && ALL[f] instanceof CarLook l) {
                look = l;
                lookOwner = f;
                return;
            }
        }
    }

    private static void start(int f, RaceView race) {
        try {
            ALL[f].raceStarted(race);
        } catch (RuntimeException e) {
            fail(f, e);
        }
    }

    private static void stop(int f, RaceView race) {
        active[f] = false;
        activeCount--;
        try {
            ALL[f].raceEnded(race);
        } catch (RuntimeException e) {
            HLogger.error("[extras] " + ALL[f].id() + " failed ending the race: " + e);
        }
    }

    private static void fail(int f, RuntimeException e) {
        HLogger.error("[extras] " + ALL[f].id() + " failed and is off for this race: " + e);
        e.printStackTrace();
        broken[f] = true;
        if (active[f]) {
            active[f] = false;
            activeCount--;
        }
        relook();
    }
}
